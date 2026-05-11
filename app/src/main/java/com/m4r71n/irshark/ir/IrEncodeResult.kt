package com.m4r71n.irshark.ir

/**
 * Result of encoding an IR protocol payload into mark/space pattern.
 */
data class IrEncodeResult(
    val frequencyHz: Int,
    val pattern: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IrEncodeResult) return false

        if (frequencyHz != other.frequencyHz) return false
        if (!pattern.contentEquals(other.pattern)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = frequencyHz
        result = 31 * result + pattern.contentHashCode()
        return result
    }
}
