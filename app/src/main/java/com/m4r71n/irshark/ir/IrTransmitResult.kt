package com.m4r71n.irshark.ir

enum class IrTransmitStatus {
    SUCCESS,
    NO_OUTPUT_AVAILABLE,
    FAILED
}

data class IrTransmitResult(
    val status: IrTransmitStatus,
    val message: String = ""
) {
    val success: Boolean get() = status == IrTransmitStatus.SUCCESS
}
