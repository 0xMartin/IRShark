package com.vex.irshark.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
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
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.LocalContext
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.vex.irshark.R
import com.vex.irshark.util.transmitIrCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val KEY_COLUMNS = intPreferencesKey("columns")
internal val KEY_ROWS = intPreferencesKey("rows")
internal fun keyButtonLabel(index: Int) = stringPreferencesKey("button_label_$index")
internal fun keyButtonCode(index: Int) = stringPreferencesKey("button_code_$index")
internal fun keyButtonRemote(index: Int) = stringPreferencesKey("button_remote_$index")
internal val KEY_BUTTON_INDEX = ActionParameters.Key<Int>("button_index")

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
    val isConfigured = columns > 0 && rows > 0 && prefs[keyButtonLabel(0)]?.isNotBlank() == true

    if (!isConfigured) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF0D0618)))
                .clickable(actionRunCallback<OpenConfigAction>()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = LocalContext.current.getString(R.string.widget_setup_prompt),
                style = TextStyle(
                    color = ColorProvider(Color(0x88BBAAFF)),
                    fontSize = 12.sp
                )
            )
        }
        return
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF0D0618)))
            .padding(3.dp)
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
                                .background(ColorProvider(Color(0xFF2C1558)))
                                .cornerRadius(14.dp)
                            .clickable(
                                actionRunCallback<SendIrAction>(
                                    actionParametersOf(KEY_BUTTON_INDEX to index)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = GlanceModifier.padding(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            if (remoteName.isNotBlank()) {
                                Text(
                                    text = remoteName,
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = ColorProvider(Color(0xFF9977CC)),
                                        fontSize = 10.sp
                                    )
                                )
                            }
                            Text(
                                text = buttonLabel.ifBlank { "—" },
                                maxLines = 1,
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
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


class SendIrAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val index = parameters[KEY_BUTTON_INDEX] ?: 0
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val buttonCode = prefs[keyButtonCode(index)]
        if (buttonCode.isNullOrBlank()) return
        withContext(Dispatchers.IO) {
            transmitIrCode(context, buttonCode)
        }
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
