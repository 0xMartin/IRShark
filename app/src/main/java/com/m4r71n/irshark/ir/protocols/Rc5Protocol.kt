package com.m4r71n.irshark.ir.protocols

import com.m4r71n.irshark.ir.IrEncodeResult
import com.m4r71n.irshark.ir.IrProtocolEncoder
import com.m4r71n.irshark.ir.IrProtocolUtils

/**
 * RC5 and RC5x protocol encoder.
 * 
 * Bi-phase Manchester encoding with toggle bit.
 * 
 * Frame structure:
 * - Start bits: 11 (fixed)
 * - Toggle bit: flips on each encode
 * - Address: 5 bits (MSB-first)
 * - Command: 6 bits (MSB-first)
 * - Unit: ~889µs per half-bit
 * - Carrier: 36 kHz
 * - Frame padded to 114000µs with repeats
 */
class Rc5Protocol(private val extended: Boolean = false) : IrProtocolEncoder {
    override val protocolId: String = if (extended) "rc5x" else "rc5"
    override val displayName: String = if (extended) "RC5x" else "RC5"

    companion object {
        private const val CARRIER_HZ = 36000
        private const val HALF_BIT_US = 889
        private const val REPEAT_GAP_US = 88900
        private val rc5Lock = Any()
        private var rc5ToggleBit = false
    }

    override fun encode(params: Map<String, Any>): IrEncodeResult {
        val addressStr = params["address"] as? String
            ?: throw IllegalArgumentException("address is required")
        val commandStr = params["command"] as? String
            ?: throw IllegalArgumentException("command is required")

        val address = IrProtocolUtils.parseHexValue(addressStr, minDigits = 1, maxDigits = 2) and 0x1F
        val command = IrProtocolUtils.parseHexValue(commandStr, minDigits = 1, maxDigits = 2) and 0x7F

        val toggle = synchronized(rc5Lock) {
            val current = rc5ToggleBit
            rc5ToggleBit = !rc5ToggleBit
            current
        }

        val bits = mutableListOf<Int>()
        // RC5: start1=1, start2=1, toggle, address(5), command(6)
        // RC5X: start1=1, field bit = inverted command bit 6, toggle, address(5), command(6)
        val cMsb = (command shr 6) and 1
        val start2 = if (extended) {
            if (cMsb == 1) 0 else 1
        } else {
            1
        }
        bits.add(1)
        bits.add(start2)
        bits.add(if (toggle) 1 else 0)
        for (i in 4 downTo 0) bits.add((address shr i) and 1)
        val command6 = command and 0x3F
        for (i in 5 downTo 0) bits.add((command6 shr i) and 1)

        val halves = mutableListOf<Pair<Boolean, Int>>()

        fun appendHalf(isMark: Boolean, duration: Int) {
            val last = halves.lastOrNull()
            if (last != null && last.first == isMark) {
                halves[halves.lastIndex] = isMark to (last.second + duration)
            } else {
                halves.add(isMark to duration)
            }
        }

        // Manchester: 1 => mark+space, 0 => space+mark
        for (bit in bits) {
            if (bit == 1) {
                appendHalf(isMark = true, duration = HALF_BIT_US)
                appendHalf(isMark = false, duration = HALF_BIT_US)
            } else {
                appendHalf(isMark = false, duration = HALF_BIT_US)
                appendHalf(isMark = true, duration = HALF_BIT_US)
            }
        }

        val singleFrame = halves.map { it.second }.toIntArray()
        val pattern = IrProtocolUtils.repeatFrame(singleFrame, REPEAT_GAP_US, repeats = 3)

        return IrEncodeResult(CARRIER_HZ, pattern)
    }
}
