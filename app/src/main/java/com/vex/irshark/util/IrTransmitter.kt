package com.vex.irshark.util

import android.content.Context
import android.hardware.ConsumerIrManager

private const val RC5_CARRIER_HZ = 36000
private const val RC5_HALF_BIT_US = 889
private const val RC5_REPEAT_GAP_US = 88900

private const val RC6_CARRIER_HZ = 36000
private const val RC6_T_US = 444

private val rc6Lock = Any()
private var rc6ToggleBit = false
private val rc5Lock = Any()
private var rc5ToggleBit = false

/**
 * Transmits an IR code payload via ConsumerIrManager.
 * Supports two formats:
 *  - Parsed: "protocol=NEC; address=0x00FF; command=0x20DF"
 *  - Raw:    space-separated integer durations (microseconds, alternating mark/space)
 *
 * Returns true if transmission was attempted, false if IR hardware unavailable.
 */
fun transmitIrCode(context: Context, codePayload: String): Boolean {
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
            if (parts.size >= 4) {
                irManager.transmit(frequency, parts)
                return true
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
                protocol == "SAMSUNG" || protocol == "SAMSUNG32" -> {
                    encodeNec(address.value, command.value, extendedAddress = true)
                }
                protocol == "RC6" -> encodeRc6(address.bytes, command.bytes)
                protocol == "RC5" -> encodeRc5(address.value, command.value, extended = false)
                protocol == "RC5X" -> encodeRc5(address.value, command.value, extended = true)
                else -> return false
            }

            if (pattern.isNotEmpty()) {
                val carrier = when (protocol) {
                    "RC5", "RC5X" -> RC5_CARRIER_HZ
                    "RC6" -> RC6_CARRIER_HZ
                    else -> 38000
                }
                irManager.transmit(carrier, pattern)
                return true
            }
            return false
        }
    }

    // Backward compatibility for old saved payloads containing only raw timing numbers.
    val parts = parseIntPattern(payload)
    if (parts.size >= 4) {
        irManager.transmit(38000, parts)
        return true
    }

    return false
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
            if (out.size % 2 == 0) {
                out += gapUs
            } else {
                out[out.lastIndex] = out.last() + gapUs
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
