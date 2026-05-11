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

class PioneerProtocol : IrProtocolEncoder {
    override val protocolId: String = "pioneer"
    override val displayName: String = "Pioneer"

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

class Nec42Protocol : IrProtocolEncoder {
    override val protocolId: String = "nec42"
    override val displayName: String = "NEC42"

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
