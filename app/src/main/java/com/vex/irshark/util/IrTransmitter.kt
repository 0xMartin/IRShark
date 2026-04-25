package com.vex.irshark.util

import android.content.Context
import android.hardware.ConsumerIrManager

/**
 * Transmits an IR code payload via ConsumerIrManager.
 * Supports two formats:
 *  - Parsed: "protocol=NEC; address=0x00FF; command=0x20DF"
 *  - Raw:    space-separated integer durations (microseconds, alternating mark/space)
 *
 * Returns true if transmission was attempted, false if IR hardware unavailable.
 */
fun transmitIrCode(context: Context, codePayload: String): Boolean {
    val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        ?: return false
    if (!irManager.hasIrEmitter()) return false

    val payload = codePayload.trim()

    // Try parsed format: extract frequency from protocol mapping + encode NEC/etc
    val parsedMatch = Regex("""(?i)protocol\s*=\s*(\S+).*address\s*=\s*(\S+).*command\s*=\s*(\S+)""")
        .find(payload)
    if (parsedMatch != null) {
        val protocol = parsedMatch.groupValues[1].uppercase()
        val address = parsedMatch.groupValues[2].removePrefix("0x").removePrefix("0X")
            .toLongOrNull(16) ?: return false
        val command = parsedMatch.groupValues[3].removePrefix("0x").removePrefix("0X")
            .toLongOrNull(16) ?: return false

        // Encode as NEC protocol (38kHz carrier, standard timing)
        val frequency = when (protocol) {
            "NEC"     -> 38000
            "RC5"     -> 36000
            "RC6"     -> 36000
            "SAMSUNG" -> 38000
            "SONY"    -> 40000
            else      -> 38000
        }
        val pattern = encodeNec(address.toInt(), command.toInt())
        if (pattern.isNotEmpty()) {
            irManager.transmit(frequency, pattern)
            return true
        }
        return false
    }

    // Try raw format: space-separated integers
    val parts = payload.split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
    if (parts.size >= 4) {
        irManager.transmit(38000, parts.toIntArray())
        return true
    }

    return false
}

private fun encodeNec(address: Int, command: Int): IntArray {
    // NEC protocol: 9ms lead + 4.5ms space, then 32 bits LSB first, then stop
    val lead = intArrayOf(9000, 4500)
    val one  = intArrayOf(560, 1690)
    val zero = intArrayOf(560, 560)
    val stop = intArrayOf(560)

    val bits = mutableListOf<Int>()
    bits.addAll(lead.toList())

    // 8 bits address, 8 bits ~address, 8 bits command, 8 bits ~command
    val data = listOf(
        address and 0xFF,
        (address and 0xFF).inv() and 0xFF,
        command and 0xFF,
        (command and 0xFF).inv() and 0xFF
    )
    for (byte in data) {
        for (i in 0 until 8) {
            if ((byte shr i) and 1 == 1) bits.addAll(one.toList())
            else bits.addAll(zero.toList())
        }
    }
    bits.addAll(stop.toList())
    return bits.toIntArray()
}
