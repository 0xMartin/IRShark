package com.m4r71n.irshark.ir

/**
 * Registry of all supported IR protocol encoders.
 * Centralizes protocol availability and lookup.
 */
object IrProtocolRegistry {
    private val encoders = mutableMapOf<String, IrProtocolEncoder>()

    /**
     * Register a new protocol encoder.
     */
    fun register(encoder: IrProtocolEncoder) {
        encoders[encoder.protocolId.lowercase()] = encoder
    }

    /**
     * Get encoder by protocol ID.
     * @return encoder or null if not found
     */
    fun getEncoder(protocolId: String): IrProtocolEncoder? {
        return encoders[protocolId.lowercase()]
    }

    /**
     * Check if protocol is supported.
     */
    fun isSupported(protocolId: String): Boolean {
        return encoders.containsKey(protocolId.lowercase())
    }

    /**
     * Get all registered protocol IDs.
     */
    fun allProtocolIds(): List<String> {
        return encoders.keys.sorted()
    }

    /**
     * Get all registered protocol encoders.
     */
    fun allEncoders(): List<IrProtocolEncoder> {
        return encoders.values.sortedBy { it.protocolId }
    }

    /**
     * Clear all registered encoders (useful for testing).
     */
    fun clear() {
        encoders.clear()
    }
}
