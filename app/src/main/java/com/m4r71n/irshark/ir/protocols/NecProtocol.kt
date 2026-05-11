package com.m4r71n.irshark.ir.protocols

import com.m4r71n.irshark.ir.IrEncodeResult
import com.m4r71n.irshark.ir.IrProtocolEncoder
import com.m4r71n.irshark.ir.IrProtocolUtils

/**
 * NEC protocol encoder.
 * 
 * Standard NEC: 8b addr + ~addr + cmd + ~cmd = 32 bits
 * NECext/Samsung: 16b addr + cmd + ~cmd = 32 bits
 * 
 * Frame structure:
 * - Preamble: 9ms mark + 4.5ms space
 * - 32 bits (LSB-first): mark=562µs, space=562µs (0) or 1687µs (1)
 * - Trailer: 562µs mark
 */
class NecProtocol(val extendedAddress: Boolean = false) : IrProtocolEncoder {
    override val protocolId: String = if (extendedAddress) "necext" else "nec"
    override val displayName: String = if (extendedAddress) "NEC Extended" else "NEC"

    companion object {
        private const val CARRIER_HZ = 38000
        private const val LEAD_MARK = 9000
        private const val LEAD_SPACE = 4500
        private const val BIT_MARK = 562
        private const val BIT_SPACE_0 = 562
        private const val BIT_SPACE_1 = 1687
        private const val TRAILER_MARK = 562
    }

    override fun encode(params: Map<String, Any>): IrEncodeResult {
        val addressStr = params["address"] as? String
            ?: throw IllegalArgumentException("address is required")
        val commandStr = params["command"] as? String
            ?: throw IllegalArgumentException("command is required")

        val address = IrProtocolUtils.parseHexValue(addressStr, minDigits = 1, maxDigits = 4)
        val command = IrProtocolUtils.parseHexValue(commandStr, minDigits = 1, maxDigits = 2)

        val bits = mutableListOf<Int>()
        bits.add(LEAD_MARK)
        bits.add(LEAD_SPACE)

        val data = if (extendedAddress) {
            // NECext: 16b addr + cmd + ~cmd
            listOf(
                address and 0xFF,
                (address shr 8) and 0xFF,
                command and 0xFF,
                (command and 0xFF).inv() and 0xFF
            )
        } else {
            // Standard NEC: 8b addr + ~addr + cmd + ~cmd
            listOf(
                address and 0xFF,
                (address and 0xFF).inv() and 0xFF,
                command and 0xFF,
                (command and 0xFF).inv() and 0xFF
            )
        }

        for (byte in data) {
            for (i in 0 until 8) {
                val bit = (byte shr i) and 1
                bits.add(BIT_MARK)
                bits.add(if (bit == 1) BIT_SPACE_1 else BIT_SPACE_0)
            }
        }
        bits.add(TRAILER_MARK)

        return IrEncodeResult(CARRIER_HZ, bits.toIntArray())
    }
}
