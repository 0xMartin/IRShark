package com.m4r71n.irshark.ir.protocols

import com.m4r71n.irshark.ir.IrEncodeResult
import com.m4r71n.irshark.ir.IrProtocolEncoder
import android.util.Log

/**
 * Raw signal protocol encoder.
 * 
 * Accepts raw mark/space durations as space-separated integers.
 * Input can be decimal or hexadecimal (0x prefix).
 */
class RawSignalProtocol : IrProtocolEncoder {
    override val protocolId: String = "raw"
    override val displayName: String = "Raw Signal"

    companion object {
        private const val TAG = "RawSignalProtocol"
        private const val DEFAULT_CARRIER_HZ = 38000
        private const val MIN_CARRIER_HZ = 10000
        private const val MAX_CARRIER_HZ = 100000
        private const val MAX_PATTERN_ENTRIES = 4096
        private const val MAX_TOTAL_DURATION_US = 2_000_000L
    }

    override fun encode(params: Map<String, Any>): IrEncodeResult {
        val patternStr = params["pattern"] as? String
            ?: throw IllegalArgumentException("pattern is required")
        val frequencyStr = params["frequency"] as? String

        val frequency = if (frequencyStr.isNullOrBlank()) {
            DEFAULT_CARRIER_HZ
        } else {
            try {
                frequencyStr.toInt().coerceIn(MIN_CARRIER_HZ, MAX_CARRIER_HZ)
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Invalid frequency: '$frequencyStr'", e)
            }
        }

        val pattern = parsePattern(patternStr)
        
        if (pattern.size > MAX_PATTERN_ENTRIES) {
            throw IllegalArgumentException("Pattern too long: ${pattern.size} > $MAX_PATTERN_ENTRIES")
        }

        if (pattern.any { it <= 0 }) {
            throw IllegalArgumentException("Pattern contains non-positive durations")
        }

        val totalDurationUs = pattern.fold(0L) { acc, part -> acc + part.toLong() }
        if (totalDurationUs > MAX_TOTAL_DURATION_US) {
            Log.w(TAG, "Pattern duration $totalDurationUs us exceeds device limit $MAX_TOTAL_DURATION_US us")
            throw IllegalArgumentException("Pattern duration exceeds maximum: $totalDurationUs > $MAX_TOTAL_DURATION_US")
        }

        // Auto-append 45ms trailing space if pattern has odd length (ends with mark)
        val finalPattern = if (pattern.size % 2 == 1) {
            pattern + 45000
        } else {
            pattern
        }

        return IrEncodeResult(frequency, finalPattern.toIntArray())
    }

    private fun parsePattern(patternStr: String): List<Int> {
        val tokens = patternStr.split(Regex("\\s+|,|;"))
        val pattern = mutableListOf<Int>()

        for (token in tokens) {
            val cleaned = token.trim()
            if (cleaned.isEmpty()) continue

            val value = try {
                when {
                    cleaned.startsWith("0x") || cleaned.startsWith("0X") -> {
                        cleaned.substring(2).toInt(16)
                    }
                    else -> cleaned.toInt()
                }
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Invalid pattern token: '$token'", e)
            }

            if (value <= 0) {
                throw IllegalArgumentException("Pattern durations must be positive: $token = $value")
            }

            pattern.add(value)
        }

        return pattern
    }
}
