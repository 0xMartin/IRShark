package com.m4r71n.irshark.ir.protocols

import com.m4r71n.irshark.ir.IrEncodeResult
import com.m4r71n.irshark.ir.IrProtocolEncoder
import com.m4r71n.irshark.ir.IrProtocolUtils

/**
 * Sony SIRC protocol encoder.
 * 
 * Supports 12-bit, 15-bit, and 20-bit variants.
 * 
 * Frame structure:
 * - Header: 2400µs mark + 600µs space
 * - Bits: 1200µs mark (1) or 600µs mark (0), all followed by 600µs space
 * - Last space removed, gap padded to 45000µs
 * - Frame duplicated (2x)
 */
class SonyProtocol(private val totalBits: Int = 12) : IrProtocolEncoder {
    override val protocolId: String = when (totalBits) {
        12 -> "sirc12"
        15 -> "sirc15"
        20 -> "sirc20"
        else -> throw IllegalArgumentException("Unsupported SIRC variant: $totalBits bits")
    }

    override val displayName: String = when (totalBits) {
        12 -> "Sony SIRC 12-bit"
        15 -> "Sony SIRC 15-bit"
        20 -> "Sony SIRC 20-bit"
        else -> "Sony SIRC"
    }

    companion object {
        private const val CARRIER_HZ = 40000
        private const val HEADER_MARK = 2400
        private const val HEADER_SPACE = 600
        private const val BIT_MARK_1 = 1200
        private const val BIT_MARK_0 = 600
        private const val BIT_SPACE = 600
        private const val FRAME_TOTAL_US = 45000
    }

    override fun encode(params: Map<String, Any>): IrEncodeResult {
        val addressStr = params["address"] as? String
            ?: throw IllegalArgumentException("address is required")
        val commandStr = params["command"] as? String
            ?: throw IllegalArgumentException("command is required")

        val address = IrProtocolUtils.parseHexValue(addressStr, minDigits = 1, maxDigits = 8)
        val command = IrProtocolUtils.parseHexValue(commandStr, minDigits = 1, maxDigits = 8)

        // Mask address and command based on total bits
        val (addrMask, cmdMask) = when (totalBits) {
            12 -> Pair(0x1F, 0x7F)    // cmd(7) + addr(5)
            15 -> Pair(0xFF, 0x7F)    // cmd(7) + addr(8)
            20 -> Pair(0x1FFF, 0x7F)  // cmd(7) + addr(13)
            else -> throw IllegalArgumentException("Unsupported bit count: $totalBits")
        }

        val maskedAddr = address and addrMask
        val maskedCmd = command and cmdMask
        val data = (maskedCmd and 0x7F) or ((maskedAddr and addrMask) shl 7)

        fun buildFrame(): List<Int> {
            val seq = mutableListOf<Int>()
            seq.add(HEADER_MARK)
            seq.add(HEADER_SPACE)

            for (i in 0 until totalBits) {
                val bit = (data shr i) and 1
                seq.add(if (bit == 1) BIT_MARK_1 else BIT_MARK_0)
                seq.add(BIT_SPACE)
            }

            if (seq.isNotEmpty()) seq.removeAt(seq.lastIndex)
            val used = seq.fold(0) { acc, v -> acc + v }
            val remaining = (FRAME_TOTAL_US - used).coerceAtLeast(0)
            seq.add(remaining)

            return seq
        }

        val frame = buildFrame()
        val out = mutableListOf<Int>()
        out.addAll(frame)
        out.addAll(frame)

        return IrEncodeResult(CARRIER_HZ, out.toIntArray())
    }
}
