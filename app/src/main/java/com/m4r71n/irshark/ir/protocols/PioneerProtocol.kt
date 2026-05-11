package com.m4r71n.irshark.ir.protocols

import com.m4r71n.irshark.ir.IrEncodeResult
import com.m4r71n.irshark.ir.IrProtocolEncoder
import com.m4r71n.irshark.ir.IrProtocolUtils

class PioneerProtocol : IrProtocolEncoder {
    override val protocolId: String = "pioneer"
    override val displayName: String = "Pioneer"

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

        val addr = address and 0xFF
        val cmd = command and 0xFF
        val payload = listOf(addr, addr.inv() and 0xFF, cmd, cmd.inv() and 0xFF)

        val frame = mutableListOf<Int>()
        frame += 8500
        frame += 4225
        for (byte in payload) {
            for (i in 0 until 8) {
                frame += 500
                frame += if (((byte ushr i) and 1) == 1) 1500 else 500
            }
        }
        frame += 500
        frame += 500

        val pattern = IrProtocolUtils.repeatFrame(frame.toIntArray(), gapUs = 26000, repeats = 2)
        return IrEncodeResult(40000, pattern)
    }
}
