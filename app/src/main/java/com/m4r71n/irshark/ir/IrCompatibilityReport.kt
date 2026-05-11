package com.m4r71n.irshark.ir

data class IrCompatibilityReport(
    val hasIrEmitter: Boolean,
    val selectedMode: IrTxMode,
    val effectiveRoute: String,
    val canTransmit: Boolean,
    val message: String
)
