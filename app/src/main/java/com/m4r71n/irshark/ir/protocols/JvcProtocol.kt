package com.m4r71n.irshark.ir.protocols

import com.m4r71n.irshark.ir.IrEncodeResult
import com.m4r71n.irshark.ir.IrProtocolEncoder
import com.m4r71n.irshark.ir.IrProtocolUtils

class JvcProtocol : IrProtocolEncoder {
    override val protocolId: String = "jvc"
    override val displayName: String = "JVC"

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
        bits += 8400
        bits += 4200

        fun appendBit(bit: Int) {
            bits += 525
            bits += if (bit == 1) 1575 else 525
        }

        for (i in 0 until 8) appendBit((address shr i) and 1)
        for (i in 0 until 8) appendBit((command shr i) and 1)
        bits += 525

        return IrEncodeResult(38000, bits.toIntArray())
    }
}
