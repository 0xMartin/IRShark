package com.m4r71n.irshark.ir.protocols

import com.m4r71n.irshark.ir.IrEncodeResult
import com.m4r71n.irshark.ir.IrProtocolEncoder
import com.m4r71n.irshark.ir.IrProtocolUtils

private fun encodePulseDistanceBytes(
    bytes: List<Int>,
    headerMarkUs: Int,
    headerSpaceUs: Int,
    bitMarkUs: Int,
    zeroSpaceUs: Int,
    oneSpaceUs: Int,
    trailerMarkUs: Int,
    lsbFirst: Boolean
): IntArray {
    val bits = mutableListOf<Int>()
    bits += headerMarkUs
    bits += headerSpaceUs
    for (byte in bytes) {
        val range = if (lsbFirst) 0 until 8 else 7 downTo 0
        for (i in range) {
            val bit = (byte shr i) and 1
            bits += bitMarkUs
            bits += if (bit == 1) oneSpaceUs else zeroSpaceUs
        }
    }
    bits += trailerMarkUs
    return bits.toIntArray()
}

class KaseikyoProtocol : IrProtocolEncoder {
    override val protocolId: String = "kaseikyo"
    override val displayName: String = "Kaseikyo"

    override fun encode(params: Map<String, Any>): IrEncodeResult {
        val address = IrProtocolUtils.parseHexValue(
            params["address"] as? String ?: throw IllegalArgumentException("address is required"),
            minDigits = 1,
            maxDigits = 7
        )
        val command = IrProtocolUtils.parseHexValue(
            params["command"] as? String ?: throw IllegalArgumentException("command is required"),
            minDigits = 1,
            maxDigits = 3
        )

        val id = (address ushr 24) and 0x03
        val vendorId = (address ushr 8) and 0xFFFF
        val genre1 = (address ushr 4) and 0x0F
        val genre2 = address and 0x0F
        val cmd10 = command and 0x03FF

        val payload = MutableList(6) { 0 }
        payload[0] = vendorId and 0xFF
        payload[1] = (vendorId ushr 8) and 0xFF

        var vendorParity = payload[0] xor payload[1]
        vendorParity = (vendorParity and 0x0F) xor (vendorParity ushr 4)

        payload[2] = (vendorParity and 0x0F) or ((genre1 and 0x0F) shl 4)
        payload[3] = (genre2 and 0x0F) or ((cmd10 and 0x0F) shl 4)
        payload[4] = ((id and 0x03) shl 6) or ((cmd10 ushr 4) and 0x3F)
        payload[5] = payload[2] xor payload[3] xor payload[4]

        val pattern = encodePulseDistanceBytes(
            bytes = payload,
            headerMarkUs = 3456,
            headerSpaceUs = 1728,
            bitMarkUs = 432,
            zeroSpaceUs = 432,
            oneSpaceUs = 1296,
            trailerMarkUs = 432,
            lsbFirst = true
        )
        return IrEncodeResult(37000, pattern)
    }
}