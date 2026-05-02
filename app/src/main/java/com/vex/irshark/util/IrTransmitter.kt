package com.vex.irshark.util

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

private const val RC5_CARRIER_HZ = 36000
private const val RC5_HALF_BIT_US = 889
private const val RC5_REPEAT_GAP_US = 88900

private const val RC6_CARRIER_HZ = 36000
private const val RC6_T_US = 444
private const val SIRC_CARRIER_HZ = 40000
private const val KASEIKYO_CARRIER_HZ = 38000
private const val PIONEER_CARRIER_HZ = 40000
private const val MAX_TRANSMIT_PATTERN_US = 2_000_000L
private const val TAG = "IrTransmitter"
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

data class IrCompatibilityReport(
    val hasIrEmitter: Boolean,
    val selectedMode: IrTxMode,
    val effectiveRoute: String,
    val canTransmit: Boolean,
    val message: String
)

enum class IrTransmitStatus {
    SUCCESS,
    NO_OUTPUT_AVAILABLE,
    FAILED
}

data class IrTransmitResult(
    val status: IrTransmitStatus,
    val message: String = ""
) {
    val success: Boolean get() = status == IrTransmitStatus.SUCCESS
}

private data class BridgeTxResult(
    val success: Boolean,
    val reachable: Boolean
)

private val rc6Lock = Any()
private var rc6ToggleBit = false
private val rc5Lock = Any()
private var rc5ToggleBit = false

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
    
    val match = Regex("""protocol\s*=\s*([^;\s]+)""", RegexOption.IGNORE_CASE).find(payload)
    return match?.groupValues?.get(1)?.uppercase()?.trim(';')
}

fun getIrCompatibilityReport(context: Context, modeRaw: String, bridgeEndpointRaw: String): IrCompatibilityReport {
    val hasEmitter = hasIrEmitter(context)
    val mode = IrTxMode.fromRaw(modeRaw)
    val normalizedBridge = normalizeBridgeEndpoint(bridgeEndpointRaw)
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
 * Transmits an IR code payload via ConsumerIrManager.
 * Supports two formats:
 *  - Parsed: "protocol=NEC; address=0x00FF; command=0x20DF"
 *  - Raw:    space-separated integer durations (microseconds, alternating mark/space)
 *
 * Returns true if transmission was attempted, false if IR hardware unavailable.
 */
fun transmitIrCode(context: Context, codePayload: String): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val modeRaw = prefs.getString(KEY_TX_MODE, IrTxMode.AUTO.rawValue)
    val bridgeEndpoint = prefs.getString(KEY_BRIDGE_ENDPOINT, "").orEmpty()
    return transmitIrCodeResult(context, codePayload, modeRaw = modeRaw.orEmpty(), bridgeEndpointRaw = bridgeEndpoint).success
}

fun transmitIrCode(context: Context, codePayload: String, modeRaw: String, bridgeEndpointRaw: String): Boolean {
    return transmitIrCodeResult(context, codePayload, modeRaw, bridgeEndpointRaw).success
}

fun transmitIrCodeResult(context: Context, codePayload: String, modeRaw: String, bridgeEndpointRaw: String): IrTransmitResult {
    val mode = IrTxMode.fromRaw(modeRaw)
    val normalizedBridgeEndpoint = normalizeBridgeEndpoint(bridgeEndpointRaw)
    val canUseLocal = hasIrEmitter(context)

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
        IrTxMode.LOCAL -> {
            val ok = transmitLocalIrCode(context, codePayload)
            if (ok) {
                IrTransmitResult(IrTransmitStatus.SUCCESS)
            } else if (!canUseLocal) {
                IrTransmitResult(
                    IrTransmitStatus.NO_OUTPUT_AVAILABLE,
                    "No internal IR blaster found."
                )
            } else {
                IrTransmitResult(IrTransmitStatus.FAILED, "IR transmission failed.")
            }
        }

        IrTxMode.BRIDGE_HTTP -> {
            val bridge = transmitViaBridge(normalizedBridgeEndpoint, codePayload)
            if (bridge.success) {
                IrTransmitResult(IrTransmitStatus.SUCCESS)
            } else if (!canUseLocal && !bridge.reachable) {
                IrTransmitResult(
                    IrTransmitStatus.NO_OUTPUT_AVAILABLE,
                    "No internal IR and IR bridge is not reachable."
                )
            } else {
                IrTransmitResult(IrTransmitStatus.FAILED, "Bridge transmission failed.")
            }
        }

        IrTxMode.AUTO -> {
            // AUTO resolves above to LOCAL or BRIDGE_HTTP, so this branch should not be hit.
            IrTransmitResult(IrTransmitStatus.FAILED, "Invalid auto routing state.")
        }
    }
}

/**
 * Legacy local transmitter implementation using ConsumerIrManager.
 */
private fun transmitLocalIrCode(context: Context, codePayload: String): Boolean {
    val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        ?: return false
    if (!irManager.hasIrEmitter()) return false

    val payload = codePayload.trim()

    // New canonical payload is key/value pairs separated by ';'.
    val fields = parsePayloadFields(payload)
    if (fields.isNotEmpty()) {
        val explicitType = fields["type"]?.lowercase().orEmpty()

        // Raw payload path (type=raw; frequency=...; data=...)
        val rawData = fields["data"].orEmpty()
        if (explicitType == "raw" || rawData.isNotBlank()) {
            val frequency = fields["frequency"]?.toIntOrNull()?.coerceIn(30000, 60000) ?: 38000
            val parts = parseIntPattern(rawData)
            if (isTransmitPatternSupported(parts)) {
                return tryTransmit(irManager, frequency, parts)
            }
            return false
        }

        // Parsed payload path (type=parsed; protocol=...; address=...; command=...)
        val protocol = fields["protocol"]?.uppercase().orEmpty()
        if (explicitType == "parsed" || protocol.isNotBlank()) {
            val address = parseHexValue(fields["address"].orEmpty()) ?: return false
            val command = parseHexValue(fields["command"].orEmpty()) ?: return false

            val pattern = when {
                protocol == "NEC" -> encodeNec(address.value, command.value, extendedAddress = false)
                protocol == "NECEXT" -> encodeNec(address.value, command.value, extendedAddress = true)
                protocol == "SAMSUNG" -> encodeNec(address.value, command.value, extendedAddress = true)
                protocol == "SAMSUNG32" -> encodeSamsung32(address.value, command.value)
                protocol == "SIRC" -> encodeSirc(address.value, command.value, totalBits = 12)
                protocol == "SIRC15" -> encodeSirc(address.value, command.value, totalBits = 15)
                protocol == "SIRC20" -> encodeSirc(address.value, command.value, totalBits = 20)
                protocol == "KASEIKYO" -> encodeKaseikyo(address.value, command.value)
                protocol == "RCA" -> encodeRca(address.value, command.value)
                protocol == "PIONEER" -> encodePioneer(address.value, command.value)
                protocol == "NEC42" -> encodeNec42(address.value, command.value)
                protocol == "RC6" -> encodeRc6(address.bytes, command.bytes)
                protocol == "RC5" -> encodeRc5(address.value, command.value, extended = false)
                protocol == "RC5X" -> encodeRc5(address.value, command.value, extended = true)
                else -> return false
            }

            if (pattern.isNotEmpty()) {
                val carrier = when (protocol) {
                    "SIRC", "SIRC15", "SIRC20" -> SIRC_CARRIER_HZ
                    "KASEIKYO" -> KASEIKYO_CARRIER_HZ
                    "PIONEER" -> PIONEER_CARRIER_HZ
                    "RC5", "RC5X" -> RC5_CARRIER_HZ
                    "RC6" -> RC6_CARRIER_HZ
                    else -> 38000
                }
                if (!isTransmitPatternSupported(pattern)) return false
                return tryTransmit(irManager, carrier, pattern)
            }
            return false
        }
    }

    // Backward compatibility for old saved payloads containing only raw timing numbers.
    val parts = parseIntPattern(payload)
    if (isTransmitPatternSupported(parts)) {
        return tryTransmit(irManager, 38000, parts)
    }

    return false
}

private fun hasIrEmitter(context: Context): Boolean {
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

private fun transmitViaBridge(endpoint: String, codePayload: String): BridgeTxResult {
    if (endpoint.isBlank()) return BridgeTxResult(success = false, reachable = false)

    return try {
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = DEFAULT_BRIDGE_TIMEOUT_MS
            readTimeout = DEFAULT_BRIDGE_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        val body = "{\"payload\":\"${escapeJson(codePayload)}\"}"
        conn.outputStream.use { out -> out.write(body.toByteArray(Charsets.UTF_8)) }

        val ok = conn.responseCode in 200..299
        val reachable = true
        conn.disconnect()
        BridgeTxResult(success = ok, reachable = reachable)
    } catch (error: Exception) {
        Log.w(TAG, "Bridge transmit failed: ${error.message}")
        BridgeTxResult(success = false, reachable = false)
    }
}

private fun escapeJson(value: String): String {
    val sb = StringBuilder(value.length + 16)
    for (ch in value) {
        when (ch) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

private fun isTransmitPatternSupported(pattern: IntArray): Boolean {
    if (pattern.size < 4) return false
    if (pattern.any { it <= 0 }) return false

    val totalDurationUs = pattern.fold(0L) { acc, part -> acc + part.toLong() }
    if (totalDurationUs > MAX_TRANSMIT_PATTERN_US) {
        Log.w(TAG, "Skipping IR transmit: pattern duration $totalDurationUs us exceeds device limit $MAX_TRANSMIT_PATTERN_US us")
        return false
    }

    return true
}

private fun tryTransmit(irManager: ConsumerIrManager, carrierHz: Int, pattern: IntArray): Boolean {
    return try {
        irManager.transmit(carrierHz, pattern)
        true
    } catch (error: IllegalArgumentException) {
        Log.w(TAG, "IR transmit rejected by platform: ${error.message}")
        false
    } catch (error: RuntimeException) {
        Log.w(TAG, "IR transmit failed: ${error.message}")
        false
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

private fun parseIntPattern(raw: String): IntArray {
    return raw
        .split(Regex("\\s+"))
        .mapNotNull { it.toIntOrNull() }
        .toIntArray()
}

private data class ParsedHexValue(
    val value: Int,
    val bytes: List<Int>
)

private fun parseHexValue(raw: String): ParsedHexValue? {
    val cleaned = raw.trim()
    if (cleaned.isBlank()) return null

    val tokens = cleaned.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null

    val bytes = mutableListOf<Int>()
    for (token in tokens) {
        val hex = token.removePrefix("0x").removePrefix("0X")
        if (!hex.matches(Regex("(?i)[0-9a-f]+"))) continue

        if (hex.length <= 2) {
            bytes += (hex.toInt(16) and 0xFF)
        } else {
            // For wider tokens (e.g. 0x1B38), treat token as little-endian integer and split to bytes.
            val value = hex.toLong(16)
            var tmp = value
            while (tmp > 0) {
                bytes += (tmp.toInt() and 0xFF)
                tmp = tmp ushr 8
            }
            if (value == 0L) bytes += 0
        }
    }
    if (bytes.isEmpty()) return null

    val limited = bytes.take(4)
    var value = 0
    for (i in limited.indices) {
        value = value or ((limited[i] and 0xFF) shl (8 * i))
    }

    return ParsedHexValue(value = value, bytes = limited)
}

private fun encodeNec(address: Int, command: Int, extendedAddress: Boolean): IntArray {
    // NEC protocol: 9ms lead + 4.5ms space, then 32 bits LSB first, then stop
    val lead = intArrayOf(9000, 4500)
    val one  = intArrayOf(560, 1690)
    val zero = intArrayOf(560, 560)
    val stop = intArrayOf(560)

    val bits = mutableListOf<Int>()
    bits.addAll(lead.toList())

    // NEC: 8b addr + ~addr + cmd + ~cmd
    // NECext/Samsung style: 16b addr + cmd + ~cmd
    val data = if (extendedAddress) {
        listOf(
            address and 0xFF,
            (address shr 8) and 0xFF,
            command and 0xFF,
            (command and 0xFF).inv() and 0xFF
        )
    } else {
        listOf(
            address and 0xFF,
            (address and 0xFF).inv() and 0xFF,
            command and 0xFF,
            (command and 0xFF).inv() and 0xFF
        )
    }
    for (byte in data) {
        for (i in 0 until 8) {
            if ((byte shr i) and 1 == 1) bits.addAll(one.toList())
            else bits.addAll(zero.toList())
        }
    }
    bits.addAll(stop.toList())
    return bits.toIntArray()
}

private fun encodeSamsung32(address: Int, command: Int): IntArray {
    val lead = intArrayOf(4500, 4500)
    val one = intArrayOf(550, 1650)
    val zero = intArrayOf(550, 550)
    val stop = intArrayOf(550)
    val bits = mutableListOf<Int>()
    bits.addAll(lead.toList())

    val addr = address and 0xFF
    val cmd = command and 0xFF
    val data = listOf(
        addr,
        addr,
        cmd,
        cmd.inv() and 0xFF
    )
    for (byte in data) {
        for (i in 0 until 8) {
            bits.addAll(if (((byte shr i) and 1) == 1) one.toList() else zero.toList())
        }
    }
    bits.addAll(stop.toList())
    return bits.toIntArray()
}

private fun encodeSirc(address: Int, command: Int, totalBits: Int): IntArray {
    val frame = mutableListOf<Int>()
    val leader = intArrayOf(2400, 600)
    val one = intArrayOf(1200, 600)
    val zero = intArrayOf(600, 600)

    val frameBits = mutableListOf<Int>()
    for (i in 0 until 7) frameBits += (command shr i) and 1
    when (totalBits) {
        12 -> for (i in 0 until 5) frameBits += (address shr i) and 1
        15 -> for (i in 0 until 8) frameBits += (address shr i) and 1
        20 -> for (i in 0 until 13) frameBits += (address shr i) and 1
        else -> return intArrayOf()
    }

    frame.addAll(leader.toList())
    for (bit in frameBits) frame.addAll(if (bit == 1) one.toList() else zero.toList())

    // Sony SIRC repeats the whole frame at ~45 ms start-to-start.
    val frameDurationUs = frame.sum()
    val gapUs = (45000 - frameDurationUs).coerceAtLeast(10000)
    return repeatFrame(frame.toIntArray(), gapUs = gapUs, repeats = 3)
}

private fun encodeKaseikyo(address: Int, command: Int): IntArray {
    // Flipper mapping (address=26b, command=10b):
    // address = [id:2][vendor_id:16][genre1:4][genre2:4], command = 10 bits.
    val id = (address ushr 24) and 0x03
    val vendorId = (address ushr 8) and 0xFFFF
    val genre1 = (address ushr 4) and 0x0F
    val genre2 = address and 0x0F
    val cmd10 = command and 0x03FF

    val payload = MutableList(6) { 0 }
    payload[0] = vendorId and 0xFF
    payload[1] = (vendorId ushr 8) and 0xFF

    var vendorParity = payload[0] xor payload[1]
    vendorParity = (vendorParity and 0x0F) xor (vendorParity ushr 4)

    payload[2] = (vendorParity and 0x0F) or ((genre1 and 0x0F) shl 4)
    payload[3] = (genre2 and 0x0F) or ((cmd10 and 0x0F) shl 4)
    payload[4] = ((id and 0x03) shl 6) or ((cmd10 ushr 4) and 0x3F)
    payload[5] = payload[2] xor payload[3] xor payload[4]

    return encodePulseDistanceBytes(
        bytes = payload,
        headerMarkUs = 3456,
        headerSpaceUs = 1728,
        bitMarkUs = 432,
        zeroSpaceUs = 432,
        oneSpaceUs = 1296,
        trailerMarkUs = 432,
        lsbFirst = true
    )
}

private fun encodeRca(address: Int, command: Int): IntArray {
    val addr4 = address and 0x0F
    val cmd8 = command and 0xFF
    val addrInv4 = addr4.inv() and 0x0F
    val cmdInv8 = cmd8.inv() and 0xFF

    val data24 = addr4 or (cmd8 shl 4) or (addrInv4 shl 12) or (cmdInv8 shl 16)
    val bits = mutableListOf<Int>()
    bits += 4000
    bits += 4000

    for (i in 0 until 24) {
        bits += 500
        bits += if (((data24 ushr i) and 1) == 1) 2000 else 1000
    }
    bits += 500
    return bits.toIntArray()
}

private fun encodePioneer(address: Int, command: Int): IntArray {
    val addr = address and 0xFF
    val cmd = command and 0xFF
    val payload = listOf(
        addr,
        addr.inv() and 0xFF,
        cmd,
        cmd.inv() and 0xFF
    )

    val frame = mutableListOf<Int>()
    frame += 8500
    frame += 4225

    for (byte in payload) {
        for (i in 0 until 8) {
            frame += 500
            frame += if (((byte ushr i) and 1) == 1) 1500 else 500
        }
    }

    // Pioneer sends 33 bits where the last bit is a 0 stop bit.
    frame += 500
    frame += 500

    return repeatFrame(frame.toIntArray(), gapUs = 26000, repeats = 2)
}

private fun encodeNec42(address: Int, command: Int): IntArray {
    val bits = mutableListOf<Int>()
    bits += 9000
    bits += 4500

    fun appendBit(bit: Int) {
        bits += 560
        bits += if (bit == 1) 1690 else 560
    }

    val addr13 = address and 0x1FFF
    val invAddr13 = addr13.inv() and 0x1FFF
    val cmd8 = command and 0xFF
    val invCmd8 = cmd8.inv() and 0xFF

    for (i in 0 until 13) appendBit((addr13 shr i) and 1)
    for (i in 0 until 13) appendBit((invAddr13 shr i) and 1)
    for (i in 0 until 8) appendBit((cmd8 shr i) and 1)
    for (i in 0 until 8) appendBit((invCmd8 shr i) and 1)
    bits += 560
    return bits.toIntArray()
}

private fun encodePulseDistanceBytes(
    bytes: List<Int>,
    headerMarkUs: Int,
    headerSpaceUs: Int,
    bitMarkUs: Int,
    zeroSpaceUs: Int,
    oneSpaceUs: Int,
    trailerMarkUs: Int,
    lsbFirst: Boolean
): IntArray {
    val bits = mutableListOf<Int>()
    bits += headerMarkUs
    bits += headerSpaceUs
    for (byte in bytes) {
        val range = if (lsbFirst) 0 until 8 else 7 downTo 0
        for (i in range) {
            val bit = (byte shr i) and 1
            bits += bitMarkUs
            bits += if (bit == 1) oneSpaceUs else zeroSpaceUs
        }
    }
    bits += trailerMarkUs
    return bits.toIntArray()
}

private fun encodeRc6(addressBytes: List<Int>, commandBytes: List<Int>): IntArray {
    val address = littleEndianValue(addressBytes)
    val command = littleEndianValue(commandBytes)

    // Mode-0 for 8+8 payload, mode-6-like framing for larger values (16+16).
    val useLong = address > 0xFF || command > 0xFF
    val addrBits = if (useLong) 16 else 8
    val cmdBits = if (useLong) 16 else 8
    val modeBits = if (useLong) intArrayOf(1, 1, 0) else intArrayOf(0, 0, 0)

    val toggle = synchronized(rc6Lock) {
        val current = rc6ToggleBit
        rc6ToggleBit = !rc6ToggleBit
        current
    }

    val halves = mutableListOf<Pair<Boolean, Int>>() // true=mark, false=space

    fun appendHalf(isMark: Boolean, duration: Int) {
        if (duration <= 0) return
        val last = halves.lastOrNull()
        if (last != null && last.first == isMark) {
            halves[halves.lastIndex] = isMark to (last.second + duration)
        } else {
            halves += isMark to duration
        }
    }

    fun appendBit(bit: Int, doubleWidth: Boolean = false) {
        val unit = if (doubleWidth) 2 * RC6_T_US else RC6_T_US
        // RC6 Manchester: 1 => mark then space, 0 => space then mark.
        if (bit == 1) {
            appendHalf(isMark = true, duration = unit)
            appendHalf(isMark = false, duration = unit)
        } else {
            appendHalf(isMark = false, duration = unit)
            appendHalf(isMark = true, duration = unit)
        }
    }

    // Leader: 6T mark + 2T space.
    appendHalf(isMark = true, duration = 6 * RC6_T_US)
    appendHalf(isMark = false, duration = 2 * RC6_T_US)

    // Start bit (1), mode bits (000), trailer/toggle bit (double width).
    appendBit(1)
    for (bit in modeBits) appendBit(bit)
    appendBit(if (toggle) 1 else 0, doubleWidth = true)

    for (i in (addrBits - 1) downTo 0) appendBit((address shr i) and 1)
    for (i in (cmdBits - 1) downTo 0) appendBit((command shr i) and 1)

    return halves.map { it.second }.toIntArray()
}

private fun encodeRc5(address: Int, command: Int, extended: Boolean): IntArray {
    val a = address and 0x1F
    val c7 = command and 0x7F

    val toggle = synchronized(rc5Lock) {
        val current = rc5ToggleBit
        rc5ToggleBit = !rc5ToggleBit
        current
    }

    val bits = mutableListOf<Int>()
    // RC5: start1=1, start2=1, toggle, address(5), command(6)
    // RC5X: start1=1, field bit = inverted command bit 6, toggle, address(5), command(6)
    val cMsb = (c7 shr 6) and 1
    val start2 = if (extended) {
        if (cMsb == 1) 0 else 1
    } else {
        1
    }
    bits += 1
    bits += start2
    bits += if (toggle) 1 else 0
    for (i in 4 downTo 0) bits += ((a shr i) and 1)
    val command6 = c7 and 0x3F
    for (i in 5 downTo 0) bits += ((command6 shr i) and 1)

    val halves = mutableListOf<Pair<Boolean, Int>>()

    fun appendHalf(isMark: Boolean, duration: Int) {
        val last = halves.lastOrNull()
        if (last != null && last.first == isMark) {
            halves[halves.lastIndex] = isMark to (last.second + duration)
        } else {
            halves += isMark to duration
        }
    }

    // Manchester (same phase convention as RC6 path): 1 => mark+space, 0 => space+mark.
    for (bit in bits) {
        if (bit == 1) {
            appendHalf(isMark = true, duration = RC5_HALF_BIT_US)
            appendHalf(isMark = false, duration = RC5_HALF_BIT_US)
        } else {
            appendHalf(isMark = false, duration = RC5_HALF_BIT_US)
            appendHalf(isMark = true, duration = RC5_HALF_BIT_US)
        }
    }

    val singleFrame = halves.map { it.second }.toIntArray()
    return repeatFrame(singleFrame, RC5_REPEAT_GAP_US, repeats = 3)
}

private fun repeatFrame(frame: IntArray, gapUs: Int, repeats: Int): IntArray {
    if (frame.isEmpty() || repeats <= 1) return frame

    val out = ArrayList<Int>(frame.size * repeats + repeats)
    repeat(repeats) { index ->
        frame.forEach { out += it }
        if (index != repeats - 1) {
            // Pattern starts with mark and alternates. If length is even,
            // we currently end on a space and should extend that last space.
            if (out.size % 2 == 0) {
                out[out.lastIndex] = out.last() + gapUs
            } else {
                out += gapUs
            }
        }
    }
    return out.toIntArray()
}

private fun littleEndianValue(bytes: List<Int>): Int {
    var value = 0
    val limited = bytes.take(4)
    for (i in limited.indices) {
        value = value or ((limited[i] and 0xFF) shl (8 * i))
    }
    return value
}
