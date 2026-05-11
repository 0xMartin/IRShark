package com.m4r71n.irshark.ir.protocols

import com.m4r71n.irshark.ir.IrEncodeResult
import com.m4r71n.irshark.ir.IrProtocolEncoder
import com.m4r71n.irshark.ir.IrProtocolUtils

class SamsungProtocol : IrProtocolEncoder {
    override val protocolId: String = "samsung"
    override val displayName: String = "Samsung"

    override fun encode(params: Map<String, Any>): IrEncodeResult {
        val address = IrProtocolUtils.parseHexValue(
            params["address"] as? String ?: throw IllegalArgumentException("address is required"),
            minDigits = 1,
            maxDigits = 4
        )
        val command = IrProtocolUtils.parseHexValue(
            params["command"] as? String ?: throw IllegalArgumentException("command is required"),
            minDigits = 1,
            maxDigits = 2
        )

        val lead = intArrayOf(9000, 4500)
        val one = intArrayOf(560, 1690)
        val zero = intArrayOf(560, 560)
        val stop = intArrayOf(560)

        val data = listOf(
            address and 0xFF,
            (address shr 8) and 0xFF,
            command and 0xFF,
            (command and 0xFF).inv() and 0xFF
        )

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