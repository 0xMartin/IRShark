package com.m4r71n.irshark.ir.protocols

import com.m4r71n.irshark.ir.IrEncodeResult
import com.m4r71n.irshark.ir.IrProtocolEncoder
import com.m4r71n.irshark.ir.IrProtocolUtils

class Nec42Protocol : IrProtocolEncoder {
    override val protocolId: String = "nec42"
    override val displayName: String = "NEC42"

    override fun encode(params: Map<String, Any>): IrEncodeResult {
        val address = IrProtocolUtils.parseHexValue(
            params["address"] as? String ?: throw IllegalArgumentException("address is required"),
            minDigits = 1,
            maxDigits = 8
        )
        val command = IrProtocolUtils.parseHexValue(
            params["command"] as? String ?: throw IllegalArgumentException("command is required"),
            minDigits = 1,
            maxDigits = 8
        )

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

        return IrEncodeResult(38000, bits.toIntArray())
    }
}
