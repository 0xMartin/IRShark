package com.m4r71n.irshark.ir

import android.util.Log

private const val TAG = "IrProtocolUtils"

/**
 * Helper functions for IR protocol encoding.
 */
object IrProtocolUtils {
    /**
     * Parse hexadecimal string to integer.
     * @param hex hex string (with or without 0x prefix)
     * @param minDigits minimum number of hex digits required
     * @param maxDigits maximum number of hex digits allowed
     * @return parsed value
     * @throws IllegalArgumentException if format is invalid
     */
    fun parseHexValue(hex: String, minDigits: Int = 1, maxDigits: Int = 8): Int {
        val cleaned = hex.trim().replace(" ", "").removePrefix("0x").removePrefix("0X").uppercase()
        
        if (cleaned.length < minDigits) {
            throw IllegalArgumentException("Hex value too short: '$hex' (need at least $minDigits digits)")
        }
        if (cleaned.length > maxDigits) {
            throw IllegalArgumentException("Hex value too long: '$hex' (max $maxDigits digits)")
        }
        
        return try {
            cleaned.toInt(16)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid hex format: '$hex'", e)
        }
    }

    /**
     * Parse hexadecimal string to byte (0-255).
     */
    fun parseHexByte(hex: String): Int {
        val value = parseHexValue(hex, minDigits = 1, maxDigits = 2)
        if (value > 255) {
            throw IllegalArgumentException("Byte value out of range: $value (0-255)")
        }
        return value
    }

    /**
     * Check if pattern is transmit-safe (valid durations, not exceeding max).
     */
    fun isTransmitPatternSupported(pattern: IntArray, maxDurationUs: Long = 2_000_000L): Boolean {
        if (pattern.size < 4) {
            Log.w(TAG, "Pattern too short: ${pattern.size} < 4")
            return false
        }
        if (pattern.any { it <= 0 }) {
            Log.w(TAG, "Pattern contains non-positive durations")
            return false
        }

        val totalDurationUs = pattern.fold(0L) { acc, part -> acc + part.toLong() }
        if (totalDurationUs > maxDurationUs) {
            Log.w(TAG, "Pattern duration $totalDurationUs us exceeds device limit $maxDurationUs us")
            return false
        }

        return true
    }

    /**
     * Get bit at position (0-indexed from LSB).
     */
    fun getBit(value: Int, bitPosition: Int): Int {
        return (value shr bitPosition) and 1
    }

    /**
     * Invert byte value.
     */
    fun invertByte(value: Int): Int {
        return (value.inv()) and 0xFF
    }

    /**
     * Repeat frame pattern with gap between repeats.
     */
    fun repeatFrame(frame: IntArray, gapUs: Int, repeats: Int): IntArray {
        if (frame.isEmpty() || repeats <= 1) return frame

        val out = ArrayList<Int>(frame.size * repeats + repeats)
        repeat(repeats) { index ->
            frame.forEach { out.add(it) }
            if (index != repeats - 1) {
                // Pattern starts with mark and alternates. If length is even,
                // we currently end on a space and should extend that last space.
                if (out.size % 2 == 0) {
                    out[out.lastIndex] = out.last() + gapUs
                } else {
                    out.add(gapUs)
                }
            }
        }
        return out.toIntArray()
    }

    /**
     * Sum all durations in pattern.
     */
    fun sumPattern(pattern: List<Int>): Int {
        return pattern.fold(0) { acc, v -> acc + v }
    }
}
