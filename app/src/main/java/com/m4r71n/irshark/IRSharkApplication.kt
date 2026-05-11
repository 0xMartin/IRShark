package com.m4r71n.irshark

import android.app.Application
import com.m4r71n.irshark.ir.initializeIrProtocolRegistry

/**
 * Application class for IRShark.
 * Initializes protocol registry when the app process starts,
 * ensuring it's available for widgets, services, and activities.
 */
class IRSharkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize IR protocol encoders early in app process lifecycle.
        // This ensures they're available for widgets and services, not just MainActivity.
        initializeIrProtocolRegistry()
    }
}
