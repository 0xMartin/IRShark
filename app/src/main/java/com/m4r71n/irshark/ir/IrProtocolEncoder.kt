package com.m4r71n.irshark.ir

/**
 * Base interface for IR protocol encoders.
 * Each protocol implementation should encode a payload into IR mark/space pattern.
 */
interface IrProtocolEncoder {
    /**
     * Protocol identifier (e.g., "nec", "rc5", "sirc12").
     */
    val protocolId: String

    /**
     * Human-readable protocol name (e.g., "NEC", "RC5", "Sony SIRC 12-bit").
     */
    val displayName: String

    /**
     * Encode protocol-specific parameters into IR mark/space pattern.
     * 
     * @param params Protocol-specific parameters (e.g., address, command for NEC)
     * @return IrEncodeResult containing frequency and pattern
     * @throws IllegalArgumentException if parameters are invalid
     */
    fun encode(params: Map<String, Any>): IrEncodeResult
}
