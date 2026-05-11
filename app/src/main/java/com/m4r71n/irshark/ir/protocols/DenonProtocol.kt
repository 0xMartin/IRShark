package com.m4r71n.irshark.ir.protocols

import com.m4r71n.irshark.ir.IrEncodeResult
import com.m4r71n.irshark.ir.IrProtocolEncoder
import com.m4r71n.irshark.ir.IrProtocolUtils

class DenonProtocol : IrProtocolEncoder {
    override val protocolId: String = "denon"
    override val displayName: String = "Denon"

    override fun encode(params: Map<String, Any>): IrEncodeResult {
        val address = IrProtocolUtils.parseHexValue(
            params["address"] as? String ?: throw IllegalArgumentException("address is required"),
            minDigits = 1,
            maxDigits = 2
        )
        val command = IrProtocolUtils.parseHexValue(
            params["command"] as? String ?: throw IllegalArgumentException("command is required"),
            minDigits = 1,
            maxDigits = 3
        )

        val markUs = 263
        val zeroSpaceUs = 790
        val oneSpaceUs = 1895
        val gapUs = 43000

        fun buildFrame(addr5: Int, cmd10: Int): List<Int> {
            val out = mutableListOf<Int>()
            for (i in 4 downTo 0) {
                out += markUs
                out += if (((addr5 shr i) and 1) == 1) oneSpaceUs else zeroSpaceUs
            }
            for (i in 9 downTo 0) {
                out += markUs
                out += if (((cmd10 shr i) and 1) == 1) oneSpaceUs else zeroSpaceUs
            }
            out += markUs
            return out
        }

        val addr5 = address and 0x1F
        val cmd10 = command and 0x3FF
        val invCmd10 = cmd10.inv() and 0x3FF

        val pattern = (buildFrame(addr5, cmd10) + gapUs + buildFrame(addr5, invCmd10)).toIntArray()
        return IrEncodeResult(38000, pattern)
    }
}
