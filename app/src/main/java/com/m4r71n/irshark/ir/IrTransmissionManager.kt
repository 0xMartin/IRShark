package com.m4r71n.irshark.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "IrTransmissionManager"
private const val PREFS_NAME = "irshark_prefs"
private const val KEY_TX_MODE = "tx_mode"
private const val KEY_BRIDGE_ENDPOINT = "bridge_endpoint"
private const val DEFAULT_BRIDGE_TIMEOUT_MS = 3500

enum class IrTxMode(val rawValue: String) {
    AUTO("AUTO"),
    LOCAL("LOCAL"),
    BRIDGE_HTTP("BRIDGE_HTTP");

    companion object {
        fun fromRaw(raw: String?): IrTxMode {
            return entries.firstOrNull { it.rawValue.equals(raw?.trim(), ignoreCase = true) } ?: AUTO
        }
    }
}

/**
 * Extracts the protocol name from a payload string.
 * Payload format: "protocol=RC6; address=...; command=..." etc.
 * Returns the protocol name (e.g., "RC6", "NEC") or "RAW" for raw format, or null if not found.
 */
fun extractProtocolFromPayload(payload: String): String? {
    val trimmed = payload.trim()
    // Check if it's a raw payload (space-separated integers)
    val isRaw = trimmed.split(Regex("\\s+")).all { it.toIntOrNull() != null }
    if (isRaw) return "RAW"

    val lower = payload.lowercase()
    val explicitRawType = Regex("""type\s*=\s*raw(?:\s*;|\s*$)""", RegexOption.IGNORE_CASE).containsMatchIn(payload)
    val hasRawData = Regex("""(?:^|;)\s*data\s*=""", RegexOption.IGNORE_CASE).containsMatchIn(payload)
    if (explicitRawType || hasRawData || lower.startsWith("raw|")) return "RAW"
    
    val match = Regex("""protocol\s*=\s*([^;\s]+)""", RegexOption.IGNORE_CASE).find(payload)
    return match?.groupValues?.get(1)?.uppercase()?.trim(';')
}

private data class BridgeTxResult(
    val success: Boolean,
    val reachable: Boolean
)

/**
 * Manages IR transmission through multiple paths:
 * - Internal (device IR emitter via ConsumerIrManager)
 * - Bridge HTTP (remote IR bridge)
 */
class IrTransmissionManager(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Get compatibility report for current configuration.
     */
    fun getCompatibilityReport(modeRaw: String = "", bridgeEndpointRaw: String = ""): IrCompatibilityReport {
        val hasEmitter = hasIrEmitter()
        val mode = IrTxMode.fromRaw(modeRaw.ifEmpty { prefs.getString(KEY_TX_MODE, IrTxMode.AUTO.rawValue) })
        val normalizedBridge = normalizeBridgeEndpoint(
            bridgeEndpointRaw.ifEmpty { prefs.getString(KEY_BRIDGE_ENDPOINT, "").orEmpty() }
        )
        val bridgeReady = normalizedBridge.isNotBlank()

        val effective = when (mode) {
            IrTxMode.LOCAL -> if (hasEmitter) "LOCAL" else "UNAVAILABLE"
            IrTxMode.BRIDGE_HTTP -> if (bridgeReady) "BRIDGE_HTTP" else "UNAVAILABLE"
            IrTxMode.AUTO -> when {
                hasEmitter -> "LOCAL"
                bridgeReady -> "BRIDGE_HTTP"
                else -> "UNAVAILABLE"
            }
        }

        val canTransmit = effective != "UNAVAILABLE"
        val message = when {
            mode == IrTxMode.LOCAL && !hasEmitter -> "This phone has no IR blaster."
            mode == IrTxMode.BRIDGE_HTTP && !bridgeReady -> "Bridge mode selected, but endpoint is not configured."
            mode == IrTxMode.AUTO && !hasEmitter && !bridgeReady -> "No local IR and no bridge endpoint configured."
            effective == "LOCAL" -> "IR blaster is available."
            else -> "Bridge endpoint is ready."
        }

        return IrCompatibilityReport(
            hasIrEmitter = hasEmitter,
            selectedMode = mode,
            effectiveRoute = effective,
            canTransmit = canTransmit,
            message = message
        )
    }

    /**
     * Parse a text IR payload and transmit it.
     * Supports key=value format (type=parsed/raw) and legacy raw integer sequences.
     * If modeRaw/bridgeEndpointRaw are blank, values are read from shared preferences.
     */
    fun transmitPayload(
        codePayload: String,
        modeRaw: String = "",
        bridgeEndpointRaw: String = ""
    ): IrTransmitResult {
        val payload = codePayload.trim()
        val fields = parsePayloadFields(payload)

        if (fields.isNotEmpty()) {
            val explicitType = fields["type"]?.lowercase().orEmpty()
            val rawData = fields["data"].orEmpty()
            val protocol = fields["protocol"].orEmpty()

            if (explicitType == "parsed" || protocol.isNotBlank()) {
                val address = fields["address"].orEmpty()
                val command = fields["command"].orEmpty()
                if (address.isBlank() || command.isBlank()) {
                    return IrTransmitResult(IrTransmitStatus.FAILED, "Parsed payload requires address and command")
                }
                return transmitCode(
                    protocolId = protocol.lowercase(),
                    params = mapOf("address" to address, "command" to command),
                    modeRaw = modeRaw,
                    bridgeEndpointRaw = bridgeEndpointRaw
                )
            }

            if (explicitType == "raw" || rawData.isNotBlank()) {
                val rawParams = mutableMapOf<String, Any>("pattern" to rawData)
                fields["frequency"]?.takeIf { it.isNotBlank() }?.let { rawParams["frequency"] = it }
                return transmitCode(
                    protocolId = "raw",
                    params = rawParams,
                    modeRaw = modeRaw,
                    bridgeEndpointRaw = bridgeEndpointRaw
                )
            }
        }

        if (isRawNumericPayload(payload)) {
            return transmitCode(
                protocolId = "raw",
                params = mapOf("pattern" to payload),
                modeRaw = modeRaw,
                bridgeEndpointRaw = bridgeEndpointRaw
            )
        }

        return IrTransmitResult(IrTransmitStatus.FAILED, "Unsupported payload format")
    }

    /**
     * Transmit IR code using protocol encoder.
     * 
     * @param protocolId Protocol identifier (e.g., "nec", "sony12", "rc5")
     * @param params Protocol-specific parameters
     * @param modeRaw Optional transmission mode override
     * @param bridgeEndpointRaw Optional bridge endpoint override
     * @return transmission result
     */
    fun transmitCode(
        protocolId: String,
        params: Map<String, Any>,
        modeRaw: String = "",
        bridgeEndpointRaw: String = ""
    ): IrTransmitResult {
        val encoder = IrProtocolRegistry.getEncoder(protocolId)
            ?: return IrTransmitResult(
                IrTransmitStatus.FAILED,
                "Protocol not supported: $protocolId"
            )

        val encodeResult = try {
            encoder.encode(params)
        } catch (e: Exception) {
            Log.e(TAG, "Encoding failed: ${e.message}", e)
            return IrTransmitResult(
                IrTransmitStatus.FAILED,
                "Encoding failed: ${e.message}"
            )
        }

        val mode = IrTxMode.fromRaw(modeRaw.ifEmpty { prefs.getString(KEY_TX_MODE, IrTxMode.AUTO.rawValue) })
        val normalizedBridgeEndpoint = normalizeBridgeEndpoint(
            bridgeEndpointRaw.ifEmpty { prefs.getString(KEY_BRIDGE_ENDPOINT, "").orEmpty() }
        )
        val canUseLocal = hasIrEmitter()

        val effectiveMode = when (mode) {
            IrTxMode.LOCAL -> if (canUseLocal) IrTxMode.LOCAL else null
            IrTxMode.BRIDGE_HTTP -> if (normalizedBridgeEndpoint.isNotBlank()) IrTxMode.BRIDGE_HTTP else null
            IrTxMode.AUTO -> when {
                canUseLocal -> IrTxMode.LOCAL
                normalizedBridgeEndpoint.isNotBlank() -> IrTxMode.BRIDGE_HTTP
                else -> null
            }
        }

        if (effectiveMode == null) {
            return IrTransmitResult(
                status = IrTransmitStatus.NO_OUTPUT_AVAILABLE,
                message = "No IR output found. Enable internal IR or connect a live IR bridge."
            )
        }

        return when (effectiveMode) {
            IrTxMode.LOCAL -> transmitLocal(encodeResult)
            IrTxMode.BRIDGE_HTTP -> transmitViaBridge(normalizedBridgeEndpoint, encodeResult)
            IrTxMode.AUTO -> IrTransmitResult(IrTransmitStatus.FAILED, "Invalid auto routing state.")
        }
    }

    /**
     * Transmit raw IR pattern.
     */
    fun transmitRaw(pattern: IntArray, frequencyHz: Int): IrTransmitResult {
        val encodeResult = IrEncodeResult(frequencyHz, pattern)
        return transmitLocal(encodeResult)
    }

    private fun transmitLocal(encodeResult: IrEncodeResult): IrTransmitResult {
        val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
            ?: return IrTransmitResult(
                IrTransmitStatus.NO_OUTPUT_AVAILABLE,
                "ConsumerIrManager not available"
            )

        if (!irManager.hasIrEmitter()) {
            return IrTransmitResult(
                IrTransmitStatus.NO_OUTPUT_AVAILABLE,
                "No internal IR blaster found."
            )
        }

        // Ensure pattern ends with space (even-length)
        val finalPattern = if (encodeResult.pattern.size % 2 == 1) {
            encodeResult.pattern + intArrayOf(45000)
        } else {
            encodeResult.pattern
        }

        if (!IrProtocolUtils.isTransmitPatternSupported(finalPattern)) {
            return IrTransmitResult(
                IrTransmitStatus.FAILED,
                "Pattern validation failed (duration or size out of bounds)"
            )
        }

        return try {
            irManager.transmit(encodeResult.frequencyHz, finalPattern)
            IrTransmitResult(IrTransmitStatus.SUCCESS)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "IR transmit rejected by platform: ${error.message}")
            IrTransmitResult(IrTransmitStatus.FAILED, "IR transmission rejected by device")
        } catch (error: RuntimeException) {
            Log.w(TAG, "IR transmit failed: ${error.message}")
            IrTransmitResult(IrTransmitStatus.FAILED, "IR transmission failed")
        }
    }

    private fun transmitViaBridge(endpoint: String, encodeResult: IrEncodeResult): IrTransmitResult {
        if (endpoint.isBlank()) {
            return IrTransmitResult(IrTransmitStatus.NO_OUTPUT_AVAILABLE, "Bridge endpoint not configured")
        }

        val bridge = performBridgeTransmit(endpoint, encodeResult)
        return when {
            bridge.success -> IrTransmitResult(IrTransmitStatus.SUCCESS)
            !bridge.reachable -> IrTransmitResult(
                IrTransmitStatus.NO_OUTPUT_AVAILABLE,
                "IR bridge is not reachable"
            )
            else -> IrTransmitResult(IrTransmitStatus.FAILED, "Bridge transmission failed")
        }
    }

    private fun performBridgeTransmit(endpoint: String, encodeResult: IrEncodeResult): BridgeTxResult {
        return try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = DEFAULT_BRIDGE_TIMEOUT_MS
                readTimeout = DEFAULT_BRIDGE_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            val patternStr = encodeResult.pattern.joinToString(" ")
            val payload = """{"frequency":${encodeResult.frequencyHz},"pattern":"$patternStr"}"""
            
            conn.outputStream.use { out -> out.write(payload.toByteArray(Charsets.UTF_8)) }

            val ok = conn.responseCode in 200..299
            conn.disconnect()
            BridgeTxResult(success = ok, reachable = true)
        } catch (error: Exception) {
            Log.w(TAG, "Bridge transmit failed: ${error.message}")
            BridgeTxResult(success = false, reachable = false)
        }
    }

    private fun hasIrEmitter(): Boolean {
        val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
            ?: return false
        return irManager.hasIrEmitter()
    }

    private fun normalizeBridgeEndpoint(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    private fun parsePayloadFields(payload: String): Map<String, String> {
        return payload
            .split(';')
            .mapNotNull { segment ->
                val idx = segment.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val key = segment.substring(0, idx).trim().lowercase()
                val value = segment.substring(idx + 1).trim()
                if (key.isBlank() || value.isBlank()) null else key to value
            }
            .toMap()
    }

    private fun isRawNumericPayload(payload: String): Boolean {
        val tokens = payload.trim().split(Regex("\\s+|,|;")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        return tokens.all { token ->
            token.toIntOrNull() != null ||
                token.startsWith("0x") || token.startsWith("0X")
        }
    }
}
