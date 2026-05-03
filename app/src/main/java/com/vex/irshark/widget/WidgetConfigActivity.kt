package com.vex.irshark.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.vex.irshark.data.SavedRemote
import com.vex.irshark.data.SavedRemoteButton
import com.vex.irshark.data.loadSavedRemotes
import com.vex.irshark.ui.theme.IRSharkTheme
import com.vex.irshark.R
import kotlinx.coroutines.launch

data class WidgetSize(val columns: Int, val rows: Int, val labelRes: Int)

val WIDGET_SIZES = listOf(
    WidgetSize(1, 1, R.string.widget_size_1x1),
    WidgetSize(2, 1, R.string.widget_size_2x1),
    WidgetSize(2, 2, R.string.widget_size_2x2),
    WidgetSize(2, 3, R.string.widget_size_2x3)
)

data class ButtonConfig(val remoteName: String, val label: String, val code: String)

sealed class WizardScreen {
    object SizePicker : WizardScreen()
    data class RemotePicker(val slotIndex: Int, val totalSlots: Int) : WizardScreen()
    data class ButtonPicker(
        val slotIndex: Int,
        val totalSlots: Int,
        val selectedRemote: SavedRemote
    ) : WizardScreen()
}

class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Return CANCELED if the user backs out without selecting
        setResult(RESULT_CANCELED, Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        })

        val remotes = loadSavedRemotes(this)

        setContent {
            IRSharkTheme {
                WidgetConfigWizard(
                    remotes = remotes,
                    onComplete = { columns, rows, buttons ->
                        saveAndFinish(appWidgetId, columns, rows, buttons)
                    }
                )
            }
        }
    }

    private fun saveAndFinish(
        appWidgetId: Int,
        columns: Int,
        rows: Int,
        buttons: List<ButtonConfig>
    ) {
        lifecycleScope.launch {
            val context = this@WidgetConfigActivity
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[intPreferencesKey("columns")] = columns
                    this[intPreferencesKey("rows")] = rows
                    buttons.forEachIndexed { i, btn ->
                        this[stringPreferencesKey("button_remote_$i")] = btn.remoteName
                        this[stringPreferencesKey("button_label_$i")] = btn.label
                        this[stringPreferencesKey("button_code_$i")] = btn.code
                    }
                }
            }
            IrSharkWidget().update(context, glanceId)
            setResult(RESULT_OK, Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            })
            finish()
        }
    }
}

@Composable
private fun WidgetConfigWizard(
    remotes: List<SavedRemote>,
    onComplete: (Int, Int, List<ButtonConfig>) -> Unit
) {
    var screen by remember { mutableStateOf<WizardScreen>(WizardScreen.SizePicker) }
    var selectedSize by remember { mutableStateOf<WidgetSize?>(null) }
    val configuredButtons = remember { mutableStateListOf<ButtonConfig>() }

    when (val s = screen) {
        is WizardScreen.SizePicker -> {
            SizePickerScreen(
                onSizeSelected = { size ->
                    selectedSize = size
                    configuredButtons.clear()
                    screen = WizardScreen.RemotePicker(
                        slotIndex = 0,
                        totalSlots = size.columns * size.rows
                    )
                }
            )
        }
        is WizardScreen.RemotePicker -> {
            RemotePickerScreen(
                slotIndex = s.slotIndex,
                totalSlots = s.totalSlots,
                remotes = remotes,
                onBack = {
                    if (s.slotIndex == 0) {
                        screen = WizardScreen.SizePicker
                    } else {
                        if (configuredButtons.isNotEmpty()) configuredButtons.removeLastOrNull()
                        screen = WizardScreen.RemotePicker(s.slotIndex - 1, s.totalSlots)
                    }
                },
                onRemoteSelected = { remote ->
                    screen = WizardScreen.ButtonPicker(s.slotIndex, s.totalSlots, remote)
                }
            )
        }
        is WizardScreen.ButtonPicker -> {
            ButtonPickerScreen(
                slotIndex = s.slotIndex,
                totalSlots = s.totalSlots,
                remote = s.selectedRemote,
                onBack = {
                    screen = WizardScreen.RemotePicker(s.slotIndex, s.totalSlots)
                },
                onButtonSelected = { button ->
                    configuredButtons.add(
                        ButtonConfig(s.selectedRemote.name, button.label, button.code)
                    )
                    val nextIndex = s.slotIndex + 1
                    if (nextIndex >= s.totalSlots) {
                        onComplete(
                            selectedSize!!.columns,
                            selectedSize!!.rows,
                            configuredButtons.toList()
                        )
                    } else {
                        screen = WizardScreen.RemotePicker(nextIndex, s.totalSlots)
                    }
                }
            )
        }
    }
}

@Composable
private fun SizePickerScreen(onSizeSelected: (WidgetSize) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF120722))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.widget_size_picker_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }
        HorizontalDivider(color = Color(0xFF3A2A5E))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(WIDGET_SIZES) { size ->
                SizeRow(size = size, onClick = { onSizeSelected(size) })
            }
        }
    }
}

@Composable
private fun SizeRow(size: WidgetSize, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(size.labelRes),
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge
        )
    }
    HorizontalDivider(color = Color(0xFF2A1A4E), modifier = Modifier.padding(start = 20.dp))
}

@Composable
private fun RemotePickerScreen(
    slotIndex: Int,
    totalSlots: Int,
    remotes: List<SavedRemote>,
    onBack: () -> Unit,
    onRemoteSelected: (SavedRemote) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF120722))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.widget_back),
                    tint = Color.White
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.widget_button_progress, slotIndex + 1, totalSlots),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFBBAAFF)
                )
                Text(
                    text = stringResource(R.string.widget_select_remote),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }
        HorizontalDivider(color = Color(0xFF3A2A5E))
        if (remotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.widget_no_remotes), color = Color(0xFFAA88FF))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(remotes) { remote ->
                    RemoteRow(remote = remote, onClick = { onRemoteSelected(remote) })
                }
            }
        }
    }
}

@Composable
private fun ButtonPickerScreen(
    slotIndex: Int,
    totalSlots: Int,
    remote: SavedRemote,
    onBack: () -> Unit,
    onButtonSelected: (SavedRemoteButton) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF120722))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.widget_back),
                    tint = Color.White
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.widget_button_progress_remote, slotIndex + 1, totalSlots, remote.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFBBAAFF)
                )
                Text(
                    text = stringResource(R.string.widget_select_button),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }
        HorizontalDivider(color = Color(0xFF3A2A5E))
        val buttons = remote.buttons
        if (buttons.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.widget_no_buttons), color = Color(0xFFAA88FF))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(buttons) { button ->
                    ButtonRow(button = button, onClick = { onButtonSelected(button) })
                }
            }
        }
    }
}

@Composable
private fun RemoteRow(remote: SavedRemote, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = remote.name,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.widget_buttons_count, remote.buttons.size),
            color = Color(0xFFAA88FF),
            style = MaterialTheme.typography.bodySmall
        )
    }
    HorizontalDivider(
        color = Color(0xFF2A1A4E),
        modifier = Modifier.padding(start = 20.dp)
    )
}

@Composable
private fun ButtonRow(button: SavedRemoteButton, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = button.label,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge
        )
    }
    HorizontalDivider(
        color = Color(0xFF2A1A4E),
        modifier = Modifier.padding(start = 20.dp)
    )
}
