package com.m4r71n.irshark.ir.protocols

import com.m4r71n.irshark.ir.IrEncodeResult
import com.m4r71n.irshark.ir.IrProtocolEncoder
import com.m4r71n.irshark.ir.IrProtocolUtils

class RcaProtocol : IrProtocolEncoder {
    override val protocolId: String = "rca"
    override val displayName: String = "RCA"

    override fun encode(params: Map<String, Any>): IrEncodeResult {
        val address = IrProtocolUtils.parseHexValue(
            params["address"] as? String ?: throw IllegalArgumentException("address is required"),
            minDigits = 1,
            maxDigits = 2
        )
        val command = IrProtocolUtils.parseHexValue(
            params["command"] as? String ?: throw IllegalArgumentException("command is required"),
            minDigits = 1,
            maxDigits = 2
        )

        val addr4 = address and 0x0F
        val cmd8 = command and 0xFF
        val addrInv4 = addr4.inv() and 0x0F
        val cmdInv8 = cmd8.inv() and 0xFF
        val data24 = addr4 or (cmd8 shl 4) or (addrInv4 shl 12) or (cmdInv8 shl 16)

        val bits = mutableListOf<Int>()
        bits += 4000
        bits += 4000
        for (i in 0 until 24) {
            bits += 500
            bits += if (((data24 ushr i) and 1) == 1) 2000 else 1000
        }
        bits += 500

        return IrEncodeResult(38000, bits.toIntArray())
    }
}
