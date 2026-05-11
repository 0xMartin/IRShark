package com.m4r71n.irshark.ir

import com.m4r71n.irshark.ir.protocols.NecProtocol
import com.m4r71n.irshark.ir.protocols.SonyProtocol
import com.m4r71n.irshark.ir.protocols.Rc5Protocol
import com.m4r71n.irshark.ir.protocols.Rc6Protocol
import com.m4r71n.irshark.ir.protocols.RawSignalProtocol
import com.m4r71n.irshark.ir.protocols.Samsung32Protocol
import com.m4r71n.irshark.ir.protocols.SamsungProtocol
import com.m4r71n.irshark.ir.protocols.Samsung36Protocol
import com.m4r71n.irshark.ir.protocols.KaseikyoProtocol
import com.m4r71n.irshark.ir.protocols.RcaProtocol
import com.m4r71n.irshark.ir.protocols.PioneerProtocol
import com.m4r71n.irshark.ir.protocols.Nec42Protocol
import com.m4r71n.irshark.ir.protocols.Nec16Protocol
import com.m4r71n.irshark.ir.protocols.DenonProtocol
import com.m4r71n.irshark.ir.protocols.JvcProtocol

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

    // NEC and Samsung variants
    IrProtocolRegistry.register(SamsungProtocol())
    IrProtocolRegistry.register(Samsung32Protocol())
    IrProtocolRegistry.register(Samsung36Protocol())
    IrProtocolRegistry.register(Nec16Protocol())
    IrProtocolRegistry.register(Nec42Protocol())

    // Additional families
    IrProtocolRegistry.register(KaseikyoProtocol())
    IrProtocolRegistry.register(RcaProtocol())
    IrProtocolRegistry.register(PioneerProtocol())
    IrProtocolRegistry.register(DenonProtocol())
    IrProtocolRegistry.register(JvcProtocol())
}
