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
 * - Start bits: 11 (first start bit is encoded by the preamble, field bit is the second)
 * - Toggle bit: flips on each encode
 * - Address: 5 bits (MSB-first)
 * - Command: 6 bits (MSB-first), command bit 6 is encoded as inverted field bit for RC5X
 * - Unit: ~889us per half-bit
 * - Carrier: 36 kHz
 */
class Rc5Protocol(private val extended: Boolean = false) : IrProtocolEncoder {
    override val protocolId: String = if (extended) "rc5x" else "rc5"
    override val displayName: String = if (extended) "RC5x" else "RC5"

    companion object {
        private const val CARRIER_HZ = 36000
        private const val HALF_BIT_US = 889
        private val rc5Lock = Any()
        private var rc5ToggleBit = false
    }

    override fun encode(params: Map<String, Any>): IrEncodeResult {
        val addressStr = params["address"] as? String
            ?: throw IllegalArgumentException("address is required")
        val commandStr = params["command"] as? String
            ?: throw IllegalArgumentException("command is required")

        val address = IrProtocolUtils.parseHexValue(addressStr, minDigits = 1, maxDigits = 8) and 0x1F
        val command7 = IrProtocolUtils.parseHexValue(commandStr, minDigits = 1, maxDigits = 8) and 0x7F

        val toggle = synchronized(rc5Lock) {
            val current = rc5ToggleBit
            rc5ToggleBit = !rc5ToggleBit
            current
        }

        // RC5 frame payload (13 bits after implicit first start bit):
        // field(start2), toggle, address(5), command(6)
        // For RC5X, field bit is inverted command bit 6.
        val isRc5x = extended || command7 >= 0x40
        val fieldBit = if (isRc5x && ((command7 and 0x40) != 0)) 0 else 1
        val command6 = command7 and 0x3F
        val data13 =
            (fieldBit shl 12) or
                ((if (toggle) 1 else 0) shl 11) or
                ((address and 0x1F) shl 6) or
                command6

        val pattern = mutableListOf<Int>()

        fun appendMark(duration: Int) {
            if (pattern.isNotEmpty() && pattern.size % 2 == 1) {
                pattern[pattern.lastIndex] = pattern.last() + duration
            } else {
                pattern.add(duration)
            }
        }

        fun appendSpace(duration: Int) {
            if (pattern.isNotEmpty() && pattern.size % 2 == 0) {
                pattern[pattern.lastIndex] = pattern.last() + duration
            } else {
                pattern.add(duration)
            }
        }

        // Start preamble for RC5 (equivalent to first start bit handling).
        appendMark(HALF_BIT_US)
        appendSpace(HALF_BIT_US)
        appendMark(HALF_BIT_US)

        // RC5 biphase: logical 1 => space+mark, logical 0 => mark+space
        for (i in 12 downTo 0) {
            val bit = (data13 shr i) and 1
            if (bit == 1) {
                appendSpace(HALF_BIT_US)
                appendMark(HALF_BIT_US)
            } else {
                appendMark(HALF_BIT_US)
                appendSpace(HALF_BIT_US)
            }
        }

        return IrEncodeResult(CARRIER_HZ, pattern.toIntArray())
    }
}
