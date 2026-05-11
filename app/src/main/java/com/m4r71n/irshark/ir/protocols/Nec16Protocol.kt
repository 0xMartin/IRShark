package com.m4r71n.irshark.ir.protocols

import com.m4r71n.irshark.ir.IrEncodeResult
import com.m4r71n.irshark.ir.IrProtocolEncoder
import com.m4r71n.irshark.ir.IrProtocolUtils

class Nec16Protocol : IrProtocolEncoder {
    override val protocolId: String = "nec16"
    override val displayName: String = "NEC16"

    override fun encode(params: Map<String, Any>): IrEncodeResult {
        val address = IrProtocolUtils.parseHexValue(
            params["address"] as? String ?: throw IllegalArgumentException("address is required"),
            minDigits = 1,
            maxDigits = 2
        ) and 0xFF
        val command = IrProtocolUtils.parseHexValue(
            params["command"] as? String ?: throw IllegalArgumentException("command is required"),
            minDigits = 1,
            maxDigits = 2
        ) and 0xFF

        val bits = mutableListOf<Int>()
        bits += 9000
        bits += 4500

        fun appendBit(bit: Int) {
            bits += 563
            bits += if (bit == 1) 1688 else 563
        }

        for (i in 0 until 8) appendBit((address shr i) and 1)
        for (i in 0 until 8) appendBit((command shr i) and 1)
        bits += 563

        return IrEncodeResult(38000, bits.toIntArray())
    }
}
