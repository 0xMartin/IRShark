package com.m4r71n.irshark.ir.protocols

import com.m4r71n.irshark.ir.IrEncodeResult
import com.m4r71n.irshark.ir.IrProtocolEncoder
import com.m4r71n.irshark.ir.IrProtocolUtils

/**
 * RC6 protocol encoder (Mode 0).
 * 
 * Manchester bi-phase encoding with mode bits and toggle.
 * 
 * Frame structure:
 * - Leader: 6T mark + 2T space (where T = 444µs)
 * - Start bit: 1
 * - Mode bits: 000 (Mode 0)
 * - Toggle bit: double-width, flips on encode
 * - Payload: 16 bits (address + command, MSB-first)
 * - 6T trailing silence
 */
class Rc6Protocol : IrProtocolEncoder {
    override val protocolId: String = "rc6"
    override val displayName: String = "RC6"

    companion object {
        private const val CARRIER_HZ = 36000
        private const val T_US = 444
        private val rc6Lock = Any()
        private var rc6ToggleBit = false
    }

    override fun encode(params: Map<String, Any>): IrEncodeResult {
        val addressStr = params["address"] as? String
            ?: throw IllegalArgumentException("address is required")
        val commandStr = params["command"] as? String
            ?: throw IllegalArgumentException("command is required")

        val addressBytes = parseHexBytes(addressStr)
        val commandBytes = parseHexBytes(commandStr)

        val address = littleEndianValue(addressBytes)
        val command = littleEndianValue(commandBytes)

        val useLong = address > 0xFF || command > 0xFF
        val addrBits = if (useLong) 16 else 8
        val cmdBits = if (useLong) 16 else 8
        val modeBits = if (useLong) intArrayOf(1, 1, 0) else intArrayOf(0, 0, 0)

        val toggle = synchronized(rc6Lock) {
            val current = rc6ToggleBit
            rc6ToggleBit = !rc6ToggleBit
            current
        }

        val halves = mutableListOf<Pair<Boolean, Int>>()

        fun appendHalf(isMark: Boolean, duration: Int) {
            if (duration <= 0) return
            val last = halves.lastOrNull()
            if (last != null && last.first == isMark) {
                halves[halves.lastIndex] = isMark to (last.second + duration)
            } else {
                halves.add(isMark to duration)
            }
        }

        fun appendBit(bit: Int, doubleWidth: Boolean = false) {
            val unit = if (doubleWidth) 2 * T_US else T_US
            // RC6 Manchester: 1 => mark then space, 0 => space then mark
            if (bit == 1) {
                appendHalf(isMark = true, duration = unit)
                appendHalf(isMark = false, duration = unit)
            } else {
                appendHalf(isMark = false, duration = unit)
                appendHalf(isMark = true, duration = unit)
            }
        }

        // Leader: 6T mark + 2T space
        appendHalf(isMark = true, duration = 6 * T_US)
        appendHalf(isMark = false, duration = 2 * T_US)

        // Start bit (1), mode bits (000), trailer/toggle bit (double width)
        appendBit(1)
        for (bit in modeBits) appendBit(bit)
        appendBit(if (toggle) 1 else 0, doubleWidth = true)

        for (i in (addrBits - 1) downTo 0) appendBit((address shr i) and 1)
        for (i in (cmdBits - 1) downTo 0) appendBit((command shr i) and 1)

        val pattern = halves.map { it.second }.toIntArray()
        return IrEncodeResult(CARRIER_HZ, pattern)
    }

    private fun parseHexBytes(hex: String): List<Int> {
        val cleaned = hex.trim()
            .replace(" ", "")
            .removePrefix("0x")
            .removePrefix("0X")
            .uppercase()
        val bytes = mutableListOf<Int>()
        for (i in cleaned.indices step 2) {
            val twoChar = if (i + 1 < cleaned.length) {
                cleaned.substring(i, i + 2)
            } else {
                cleaned.substring(i)
            }
            bytes.add(twoChar.toInt(16))
        }
        return bytes.take(4)
    }

    private fun littleEndianValue(bytes: List<Int>): Int {
        var value = 0
        for (i in bytes.indices) {
            value = value or ((bytes[i] and 0xFF) shl (8 * i))
        }
        return value
    }
}
