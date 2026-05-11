package com.m4r71n.irshark.widget

import android.appwidget.AppWidgetManager
import android.content.Context.AUDIO_SERVICE
import android.content.Context.VIBRATOR_SERVICE
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.LocalContext
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.m4r71n.irshark.R
import com.m4r71n.irshark.ir.transmitIrCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal val KEY_COLUMNS = intPreferencesKey("columns")
internal val KEY_ROWS = intPreferencesKey("rows")
internal val KEY_STYLE = stringPreferencesKey("widget_style")
internal val KEY_FEEDBACK_ENABLED = booleanPreferencesKey("feedback_enabled")
internal val KEY_ACTIVE_INDEX = intPreferencesKey("active_button_index")
internal fun keyButtonLabel(index: Int) = stringPreferencesKey("button_label_$index")
internal fun keyButtonCode(index: Int) = stringPreferencesKey("button_code_$index")
internal fun keyButtonRemote(index: Int) = stringPreferencesKey("button_remote_$index")
internal val KEY_BUTTON_INDEX = ActionParameters.Key<Int>("button_index")

internal enum class WidgetStyle(
    val code: String,
    val background: Color,
    val setupText: Color,
    val buttonBackground: Color,
    val indicatorIdle: Color,
    val remoteText: Color,
    val labelText: Color
) {
    DEFAULT(
        code = "default",
        background = Color(0xFF0D0618),
        setupText = Color(0x88BBAAFF),
        buttonBackground = Color(0xFF2C1558),
        indicatorIdle = Color(0xFF53327F),
        remoteText = Color(0xFF9977CC),
        labelText = Color.White
    ),
    DARK(
        code = "dark",
        background = Color(0xFF121212),
        setupText = Color(0xFF9E9E9E),
        buttonBackground = Color(0xFF2A2A2A),
        indicatorIdle = Color(0xFF555555),
        remoteText = Color(0xFFB0B0B0),
        labelText = Color(0xFFF2F2F2)
    ),
    LIGHT(
        code = "light",
        background = Color(0xFFF3F5F8),
        setupText = Color(0xFF5D6778),
        buttonBackground = Color(0xFFFFFFFF),
        indicatorIdle = Color(0xFFD5DCE8),
        remoteText = Color(0xFF4E5D75),
        labelText = Color(0xFF1E2430)
    ),
    SUNSET(
        code = "sunset",
        background = Color(0xFF2A1220),
        setupText = Color(0xFFF8B08A),
        buttonBackground = Color(0xFFC64F7A),
        indicatorIdle = Color(0xFFE07E9F),
        remoteText = Color(0xFFFFD1B5),
        labelText = Color(0xFFFFF5EE)
    ),
    OCEAN(
        code = "ocean",
        background = Color(0xFF0A1F30),
        setupText = Color(0xFF89D2E8),
        buttonBackground = Color(0xFF145374),
        indicatorIdle = Color(0xFF2E789F),
        remoteText = Color(0xFF9BD8E8),
        labelText = Color(0xFFE8F7FF)
    );

    companion object {
        fun fromCode(raw: String?): WidgetStyle {
            return entries.firstOrNull { it.code == raw } ?: DEFAULT
        }
    }
}

private const val PRESSED_INDICATOR_MS = 180L

class IrSharkWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent()
        }
    }
}

@Composable
private fun WidgetContent() {
    val prefs = currentState<Preferences>()
    val columns = prefs[KEY_COLUMNS] ?: 0
    val rows = prefs[KEY_ROWS] ?: 0
    val style = WidgetStyle.fromCode(prefs[KEY_STYLE])
    val activeIndex = prefs[KEY_ACTIVE_INDEX] ?: -1
    // Keep compact mode only for the smallest single-tile widget.
    // 2x1 should use regular spacing so horizontal gaps stay consistent.
    val compactMode = (columns == 1 && rows == 1)
    // Read the system font scale so we can compensate in sp calculations.
    @Suppress("LocalContextConfigurationRead")
    val fontScale = LocalContext.current.resources.configuration.fontScale.coerceAtLeast(1f)
    val isConfigured = columns > 0 && rows > 0 && prefs[keyButtonLabel(0)]?.isNotBlank() == true

    if (!isConfigured) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(style.background))
                .clickable(actionRunCallback<OpenConfigAction>()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = LocalContext.current.getString(R.string.widget_setup_prompt),
                style = TextStyle(
                    color = ColorProvider(style.setupText),
                    fontSize = 12.sp
                )
            )
        }
        return
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(style.background))
            .padding(6.dp)
    ) {
        repeat(rows) { row ->
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
            ) {
                repeat(columns) { col ->
                    val index = row * columns + col
                    val remoteName = prefs[keyButtonRemote(index)].orEmpty()
                    val buttonLabel = prefs[keyButtonLabel(index)].orEmpty()
                    val labelFontSize = calculateWidgetLabelFontSize(
                        label = buttonLabel,
                        columns = columns,
                        compactMode = compactMode,
                        fontScale = fontScale
                    )
                    val isPressed = activeIndex == index

                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .fillMaxSize()
                                .background(ColorProvider(Color(0x1AFFFFFF)))
                                .cornerRadius(16.dp)
                        ) {}
                        Box(
                            modifier = GlanceModifier
                                .fillMaxSize()
                                .padding(1.dp)
                                .background(ColorProvider(style.buttonBackground))
                                .cornerRadius(15.dp)
                                .clickable(
                                    actionRunCallback<SendIrAction>(
                                        actionParametersOf(KEY_BUTTON_INDEX to index)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = GlanceModifier.fillMaxSize()
                            ) {
                                Box(
                                    modifier = GlanceModifier
                                        .fillMaxSize()
                                        .padding(top = if (compactMode) 6.dp else 8.dp),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Box(
                                        modifier = GlanceModifier
                                            .width(if (compactMode) 20.dp else 28.dp)
                                            .height(if (compactMode) 4.dp else 5.dp)
                                            .cornerRadius(999.dp)
                                            .background(
                                                ColorProvider(
                                                    if (isPressed) Color(0xFFFF4D4D) else style.indicatorIdle
                                                )
                                            )
                                    ) {}
                                }
                                Column(
                                    modifier = GlanceModifier
                                        .fillMaxSize()
                                        .padding(horizontal = if (compactMode) 4.dp else 6.dp, vertical = if (compactMode) 6.dp else 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Text(
                                            text = buttonLabel.ifBlank { "—" },
                                            maxLines = 1,
                                            style = TextStyle(
                                                color = ColorProvider(style.labelText),
                                                fontSize = labelFontSize.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        if (remoteName.isNotBlank()) {
                                            Text(
                                                text = remoteName,
                                                maxLines = 1,
                                                style = TextStyle(
                                                    color = ColorProvider(style.remoteText),
                                                    fontSize = 10.sp
                                                )
                                            )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Calculates the label font size (in sp) for a widget button.
 *
 * Two adjustments are applied:
 * 1. Length-based reduction  — longer labels get a smaller font so they fit horizontally.
 * 2. FontScale compensation  — dividing by the system font scale keeps the physical
 *    rendered size stable even when the user has chosen a larger accessibility font.
 *    Without this, sp values grow proportionally with fontScale and overflow the button.
 */
private fun calculateWidgetLabelFontSize(
    label: String,
    columns: Int,
    compactMode: Boolean,
    fontScale: Float = 1f
): Float {
    val base = if (compactMode) 13f else 15f
    val min  = if (compactMode) 8f  else 9f
    val length = label.trim().length

    // Desired rendered size before fontScale correction.
    val targetSp = if (length <= 5) {
        base
    } else {
        // Reduce 1.5 sp for every 2 extra characters; more aggressive for wider grids.
        val reductionPer2Chars = if (columns >= 3) 1.5f else 1f
        val steps = (length - 5 + 1) / 2
        (base - steps * reductionPer2Chars).coerceAtLeast(min)
    }

    // Divide by fontScale so Android's scaling brings the result back to targetSp.
    // This prevents text from overflowing on devices with large system font size.
    return (targetSp / fontScale).coerceAtLeast(min / fontScale)
}


class SendIrAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val index = parameters[KEY_BUTTON_INDEX] ?: 0

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[KEY_ACTIVE_INDEX] = index
            }
        }
        IrSharkWidget().update(context, glanceId)

        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        if (prefs[KEY_FEEDBACK_ENABLED] == true) {
            triggerPressFeedback(context)
        }
        val buttonCode = prefs[keyButtonCode(index)]
        if (!buttonCode.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                transmitIrCode(context, buttonCode)
            }
        }

        delay(PRESSED_INDICATOR_MS)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[KEY_ACTIVE_INDEX] = -1
            }
        }
        IrSharkWidget().update(context, glanceId)
    }
}

private fun triggerPressFeedback(context: Context) {
    try {
        val vibrator = context.getSystemService(VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(25L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(25L)
        }
    } catch (_: Throwable) {
    }

    try {
        val audio = context.getSystemService(AUDIO_SERVICE) as? AudioManager
        if (audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            val tone = ToneGenerator(AudioManager.STREAM_SYSTEM, 50)
            try {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 30)
            } finally {
                tone.release()
            }
        }
    } catch (_: Throwable) {
    }
}

class OpenConfigAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val intent = Intent(context, WidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }
}
