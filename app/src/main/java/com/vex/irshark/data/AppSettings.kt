package com.vex.irshark.data

import android.content.Context

private const val PREFS_NAME = "irshark_prefs"
private const val KEY_GLOBAL_INTERVAL_MS = "global_interval_ms"
private const val KEY_AUTO_STOP_AT_END = "auto_stop_at_end"
private const val KEY_SHOW_TX_LED = "show_tx_led"
private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
private const val KEY_IR_FINDER_LAST_TESTED = "ir_finder_last_tested"
private const val KEY_PREFER_DOWNLOADED_DB = "prefer_downloaded_db"
private const val KEY_DOWNLOADED_DB_TAG = "downloaded_db_tag"
private const val KEY_TX_MODE = "tx_mode"
private const val KEY_BRIDGE_ENDPOINT = "bridge_endpoint"

data class AppSettings(
    val globalIntervalMs: Float = 150f,
    val autoStopAtEnd: Boolean = true,
    val showTxLed: Boolean = true,
    val hapticFeedback: Boolean = true,
    val irFinderLastTested: String? = null,
    val preferDownloadedDb: Boolean = false,
    val downloadedDbTag: String? = null,
    val txMode: String = "AUTO",
    val bridgeEndpoint: String = ""
)

fun loadAppSettings(context: Context): AppSettings {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return AppSettings(
        globalIntervalMs    = prefs.getFloat(KEY_GLOBAL_INTERVAL_MS, 150f),
        autoStopAtEnd       = prefs.getBoolean(KEY_AUTO_STOP_AT_END, true),
        showTxLed           = prefs.getBoolean(KEY_SHOW_TX_LED, true),
        hapticFeedback      = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true),
        irFinderLastTested  = prefs.getString(KEY_IR_FINDER_LAST_TESTED, null),
        preferDownloadedDb  = prefs.getBoolean(KEY_PREFER_DOWNLOADED_DB, false),
        downloadedDbTag     = prefs.getString(KEY_DOWNLOADED_DB_TAG, null),
        txMode              = prefs.getString(KEY_TX_MODE, "AUTO") ?: "AUTO",
        bridgeEndpoint      = prefs.getString(KEY_BRIDGE_ENDPOINT, "") ?: ""
    )
}

fun saveAppSettings(context: Context, settings: AppSettings) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
        .putFloat(KEY_GLOBAL_INTERVAL_MS, settings.globalIntervalMs)
        .putBoolean(KEY_AUTO_STOP_AT_END, settings.autoStopAtEnd)
        .putBoolean(KEY_SHOW_TX_LED, settings.showTxLed)
        .putBoolean(KEY_HAPTIC_FEEDBACK, settings.hapticFeedback)
        .putString(KEY_IR_FINDER_LAST_TESTED, settings.irFinderLastTested)
        .putBoolean(KEY_PREFER_DOWNLOADED_DB, settings.preferDownloadedDb)
        .putString(KEY_DOWNLOADED_DB_TAG, settings.downloadedDbTag)
        .putString(KEY_TX_MODE, settings.txMode)
        .putString(KEY_BRIDGE_ENDPOINT, settings.bridgeEndpoint)
        .apply()
}
