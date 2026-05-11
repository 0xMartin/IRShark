package com.m4r71n.irshark.ir

import android.content.Context
import android.util.Log

private const val TAG = "IrCompatLayer"

/**
 * Compatibility layer - přemostění starého API na nový IrTransmissionManager.
 * Toto umožňuje postupnou migraci bez přerušení kódu.
 */

// Wrapper pro kompatibilitu - vrátí report o dostupnosti IR
fun getIrCompatibilityReport(context: Context, modeRaw: String, bridgeEndpointRaw: String): IrCompatibilityReport {
    val manager = IrTransmissionManager(context)
    return manager.getCompatibilityReport(modeRaw, bridgeEndpointRaw)
}

// Starý API - nyní deleguje na nový IrTransmissionManager
fun transmitIrCode(context: Context, codePayload: String): Boolean {
    val prefs = context.getSharedPreferences("irshark_prefs", Context.MODE_PRIVATE)
    val modeRaw = prefs.getString("tx_mode", "AUTO").orEmpty()
    val bridgeEndpoint = prefs.getString("bridge_endpoint", "").orEmpty()
    return transmitIrCodeResult(context, codePayload, modeRaw = modeRaw, bridgeEndpointRaw = bridgeEndpoint).success
}

fun transmitIrCode(context: Context, codePayload: String, modeRaw: String, bridgeEndpointRaw: String): Boolean {
    return transmitIrCodeResult(context, codePayload, modeRaw, bridgeEndpointRaw).success
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

private fun toProtocolId(protocolRaw: String): String {
    return when (protocolRaw.uppercase()) {
        "NEC" -> "nec"
        "NECEXT" -> "necext"
        "SAMSUNG" -> "samsung"
        "SAMSUNG32" -> "samsung32"
        "SAMSUNG36" -> "samsung36"
        "SIRC" -> "sirc"
        "SIRC15" -> "sirc15"
        "SIRC20" -> "sirc20"
        "KASEIKYO" -> "kaseikyo"
        "RCA" -> "rca"
        "PIONEER" -> "pioneer"
        "NEC42" -> "nec42"
        "RC6" -> "rc6"
        "RC5" -> "rc5"
        "RC5X" -> "rc5x"
        "NEC16" -> "nec16"
        "DENON" -> "denon"
        "JVC" -> "jvc"
        "RAW" -> "raw"
        else -> protocolRaw.lowercase()
    }
}

private fun isRawNumericPayload(payload: String): Boolean {
    val tokens = payload.trim().split(Regex("\\s+|,|;"))
        .filter { it.isNotBlank() }
    if (tokens.isEmpty()) return false
    return tokens.all { token ->
        token.toIntOrNull() != null ||
            (token.startsWith("0x") || token.startsWith("0X"))
    }
}

fun transmitIrCodeResult(context: Context, codePayload: String, modeRaw: String, bridgeEndpointRaw: String): IrTransmitResult {
    val manager = IrTransmissionManager(context)

    val payload = codePayload.trim()
    Log.d(TAG, "transmitIrCodeResult: payload='$payload'")
    val fields = parsePayloadFields(payload)
    Log.d(TAG, "Parsed fields: $fields")

    if (fields.isNotEmpty()) {
        val explicitType = fields["type"]?.lowercase().orEmpty()
        val rawData = fields["data"].orEmpty()
        val protocol = fields["protocol"].orEmpty()
        if (explicitType == "parsed" || protocol.isNotBlank()) {
            val address = fields["address"].orEmpty()
            val command = fields["command"].orEmpty()
            Log.d(TAG, "Using protocol=$protocol, address=$address, command=$command")
            if (address.isBlank() || command.isBlank()) {
                Log.d(TAG, "Missing address or command")
                return IrTransmitResult(IrTransmitStatus.FAILED, "Parsed payload requires address and command")
            }

            val protocolId = toProtocolId(protocol)
            Log.d(TAG, "Mapped protocol '$protocol' to protocolId '$protocolId'")
            return manager.transmitCode(
                protocolId = protocolId,
                params = mapOf(
                    "address" to address,
                    "command" to command
                ),
                modeRaw = modeRaw,
                bridgeEndpointRaw = bridgeEndpointRaw
            )
        }

        if (explicitType == "raw" || rawData.isNotBlank()) {
            Log.d(TAG, "Using RAW protocol")
            val rawParams = mutableMapOf<String, Any>(
                "pattern" to rawData
            )
            fields["frequency"]?.takeIf { it.isNotBlank() }?.let {
                rawParams["frequency"] = it
            }
            return manager.transmitCode(
                protocolId = "raw",
                params = rawParams,
                modeRaw = modeRaw,
                bridgeEndpointRaw = bridgeEndpointRaw
            )
        }
    }

    if (isRawNumericPayload(payload)) {
        Log.d(TAG, "Payload looks like raw numeric")
        return manager.transmitCode(
            protocolId = "raw",
            params = mapOf("pattern" to payload),
            modeRaw = modeRaw,
            bridgeEndpointRaw = bridgeEndpointRaw
        )
    }

    Log.d(TAG, "Unsupported payload format: $payload")
    return IrTransmitResult(IrTransmitStatus.FAILED, "Unsupported payload format")
}
