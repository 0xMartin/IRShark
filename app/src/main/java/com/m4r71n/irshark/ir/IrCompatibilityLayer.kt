package com.m4r71n.irshark.ir

import android.content.Context

/**
 * Compatibility layer - přemostění starého API na nový IrTransmissionManager.
 * Toto umožňuje postupnou migraci bez přerušení kódu.
 */

// Wrapper pro kompatibilitu - vrátí report o dostupnosti IR
fun getIrCompatibilityReport(context: Context, modeRaw: String, bridgeEndpointRaw: String): IrCompatibilityReport {
    val manager = IrTransmissionManager(context)
    return manager.getCompatibilityReport(modeRaw, bridgeEndpointRaw)
}

// Starý API - nyní deleguje na nový IrTransmissionManager
fun transmitIrCode(context: Context, codePayload: String): Boolean {
    val prefs = context.getSharedPreferences("irshark_prefs", Context.MODE_PRIVATE)
    val modeRaw = prefs.getString("tx_mode", "AUTO").orEmpty()
    val bridgeEndpoint = prefs.getString("bridge_endpoint", "").orEmpty()
    return transmitIrCodeResult(context, codePayload, modeRaw = modeRaw, bridgeEndpointRaw = bridgeEndpoint).success
}

fun transmitIrCode(context: Context, codePayload: String, modeRaw: String, bridgeEndpointRaw: String): Boolean {
    return transmitIrCodeResult(context, codePayload, modeRaw, bridgeEndpointRaw).success
}

fun transmitIrCodeResult(context: Context, codePayload: String, modeRaw: String, bridgeEndpointRaw: String): IrTransmitResult {
    val manager = IrTransmissionManager(context)
    
    // Pokud je to raw payload (space-separated integers)
    val trimmedPayload = codePayload.trim()
    val isRaw = trimmedPayload.split(Regex("\\s+")).all { token ->
        token.toIntOrNull() != null || token.startsWith("0x") || token.startsWith("0X")
    }
    
    return if (isRaw) {
        // Transmit raw
        try {
            val parts = trimmedPayload.split(Regex("\\s+"))
                .mapNotNull { token ->
                    when {
                        token.startsWith("0x") || token.startsWith("0X") -> token.substring(2).toIntOrNull(16)
                        else -> token.toIntOrNull()
                    }
                }
                .toIntArray()
            manager.transmitRaw(parts, 38000)
        } catch (e: Exception) {
            IrTransmitResult(IrTransmitStatus.FAILED, "Raw signal parsing failed: ${e.message}")
        }
    } else {
        // Transmit via old-style payload (fallback)
        // Pro teď jen vrať FAILED, později toto bude parsovat protokol z payloadu
        IrTransmitResult(IrTransmitStatus.FAILED, "Legacy payload format - use new IR API")
    }
}
