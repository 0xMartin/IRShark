package com.m4r71n.irshark.ir.protocols

import com.m4r71n.irshark.ir.IrEncodeResult
import com.m4r71n.irshark.ir.IrProtocolEncoder
import com.m4r71n.irshark.ir.IrProtocolUtils

class Samsung32Protocol : IrProtocolEncoder {
    override val protocolId: String = "samsung32"
    override val displayName: String = "Samsung32"

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

        val lead = intArrayOf(4500, 4500)
        val one = intArrayOf(550, 1650)
        val zero = intArrayOf(550, 550)
        val stop = intArrayOf(550)

        val addr = address and 0xFF
        val cmd = command and 0xFF
        val data = listOf(addr, addr, cmd, cmd.inv() and 0xFF)

        val bits = mutableListOf<Int>()
        bits.addAll(lead.toList())
        for (byte in data) {
            for (i in 0 until 8) {
                bits.addAll(if (((byte shr i) and 1) == 1) one.toList() else zero.toList())
            }
        }
        bits.addAll(stop.toList())

        return IrEncodeResult(38000, bits.toIntArray())
    }
}