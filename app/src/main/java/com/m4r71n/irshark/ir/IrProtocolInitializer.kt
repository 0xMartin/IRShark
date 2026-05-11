package com.m4r71n.irshark.ir

import com.m4r71n.irshark.ir.protocols.NecProtocol
import com.m4r71n.irshark.ir.protocols.SonyProtocol
import com.m4r71n.irshark.ir.protocols.Rc5Protocol
import com.m4r71n.irshark.ir.protocols.Rc6Protocol
import com.m4r71n.irshark.ir.protocols.RawSignalProtocol

/**
 * Initialize IR protocol registry with all supported encoders.
 * Call this once at application startup.
 */
fun initializeIrProtocolRegistry() {
    // Raw signal (must be available)
    IrProtocolRegistry.register(RawSignalProtocol())

    // NEC family
    IrProtocolRegistry.register(NecProtocol(extendedAddress = false))
    IrProtocolRegistry.register(NecProtocol(extendedAddress = true))

    // Sony SIRC
    IrProtocolRegistry.register(SonyProtocol(totalBits = 12))
    IrProtocolRegistry.register(SonyProtocol(totalBits = 15))
    IrProtocolRegistry.register(SonyProtocol(totalBits = 20))

    // RC5 / RC5x
    IrProtocolRegistry.register(Rc5Protocol(extended = false))
    IrProtocolRegistry.register(Rc5Protocol(extended = true))

    // RC6
    IrProtocolRegistry.register(Rc6Protocol())

    // TODO: Add remaining protocols as they are ported
    // - Samsung32, Samsung36
    // - SIRC, SIRC15, SIRC20 (if different from Sony)
    // - Kaseikyo, RCA, Pioneer, Denon, etc.
}
