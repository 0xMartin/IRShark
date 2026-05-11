package com.m4r71n.irshark.ir.protocols

import com.m4r71n.irshark.ir.IrEncodeResult
import com.m4r71n.irshark.ir.IrProtocolEncoder
import com.m4r71n.irshark.ir.IrProtocolUtils

class Samsung36Protocol : IrProtocolEncoder {
    override val protocolId: String = "samsung36"
    override val displayName: String = "Samsung36"

    override fun encode(params: Map<String, Any>): IrEncodeResult {
        val address = IrProtocolUtils.parseHexValue(
            params["address"] as? String ?: throw IllegalArgumentException("address is required"),
            minDigits = 1,
            maxDigits = 4
        ) and 0xFFFF
        val command = IrProtocolUtils.parseHexValue(
            params["command"] as? String ?: throw IllegalArgumentException("command is required"),
            minDigits = 1,
            maxDigits = 5
        ) and 0xFFFFF

        val bits = mutableListOf<Int>()
        bits += 4500
        bits += 4500

        fun appendBit(bit: Int) {
            bits += 560
            bits += if (bit == 1) 1690 else 560
        }

        for (i in 0 until 16) appendBit((address shr i) and 1)
        for (i in 0 until 20) appendBit((command shr i) and 1)
        bits += 560

        return IrEncodeResult(38000, bits.toIntArray())
    }
}