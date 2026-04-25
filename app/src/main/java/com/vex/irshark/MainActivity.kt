package com.vex.irshark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.vex.irshark.data.FlipperDbIndex
import com.vex.irshark.data.SavedRemote
import com.vex.irshark.data.UniversalCommandItem
import com.vex.irshark.data.countProfilesForCommand
import com.vex.irshark.data.dbRootPath
import com.vex.irshark.data.loadAppSettings
import com.vex.irshark.data.loadFlipperDbIndex
import com.vex.irshark.data.loadSavedRemotes
import com.vex.irshark.data.parentPath
import com.vex.irshark.data.prettyPath
import com.vex.irshark.data.saveAppSettings
import com.vex.irshark.data.saveSavedRemotes
import com.vex.irshark.ui.components.AppHeader
import com.vex.irshark.ui.components.AppToastController
import com.vex.irshark.ui.components.AppToastHost
import com.vex.irshark.ui.screens.HomeScreen
import com.vex.irshark.ui.screens.RemoteControlScreen
import com.vex.irshark.ui.screens.RemotesListScreen
import com.vex.irshark.ui.screens.SettingsScreen
import com.vex.irshark.ui.screens.UniversalRemoteScreen
import com.vex.irshark.ui.theme.IRSharkTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IRSharkTheme(darkTheme = true, dynamicColor = false) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    IRSharkApp(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

// ── Navigation state ──────────────────────────────────────────────────────────

private enum class Screen {
    HOME, UNIVERSAL, MY_REMOTES, REMOTE_DB, REMOTE_CONTROL, SETTINGS
}

private enum class ControlSource { MY_REMOTES, REMOTE_DB }

// ── Root composable with navigation ──────────────────────────────────────────

@Composable
fun IRSharkApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var dbIndex by remember { mutableStateOf(FlipperDbIndex()) }
    var savedRemotes by remember { mutableStateOf(listOf<SavedRemote>()) }

    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }

    // Universal Remote state
    var universalPath by rememberSaveable { mutableStateOf(dbRootPath()) }
    var universalCommand by rememberSaveable { mutableStateOf<UniversalCommandItem?>(null) }
    var universalCodeStep by rememberSaveable { mutableIntStateOf(0) }
    var universalAutoSend by rememberSaveable { mutableStateOf(false) }
    var universalIntervalMs by rememberSaveable { mutableStateOf(250f) }
    var autoStopAtEnd by rememberSaveable { mutableStateOf(true) }
    var showTxLed by rememberSaveable { mutableStateOf(true) }
    var txPulseActive by remember { mutableStateOf(false) }
    var settingsDirty by remember { mutableStateOf(false) }
    var settingsToastPending by remember { mutableStateOf(false) }
    var settingsLoaded by remember { mutableStateOf(false) }
    var remotesLoaded by remember { mutableStateOf(false) }
    var txPulseJob by remember { mutableStateOf<Job?>(null) }
    val toastController = remember { AppToastController() }
    val scope = rememberCoroutineScope()

    fun emitTxPulse(durationMs: Long = 180L) {
        txPulseJob?.cancel()
        txPulseJob = scope.launch {
            txPulseActive = true
            delay(durationMs)
            txPulseActive = false
        }
    }

    // List search queries
    var myRemotesQuery by rememberSaveable { mutableStateOf("") }
    var remoteDbQuery by rememberSaveable { mutableStateOf("") }

    // Remote Control state
    var controlProfilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var controlName by rememberSaveable { mutableStateOf<String?>(null) }
    var controlCommandsCsv by rememberSaveable { mutableStateOf("") }
    var controlSource by rememberSaveable { mutableStateOf(ControlSource.REMOTE_DB) }
    var controlSelectedCommand by rememberSaveable { mutableStateOf<String?>(null) }
    var controlTxCount by rememberSaveable { mutableIntStateOf(0) }

    // Load data
    LaunchedEffect(Unit) {
        dbIndex = loadFlipperDbIndex(context)
        savedRemotes = loadSavedRemotes(context)
        remotesLoaded = true
        val settings = loadAppSettings(context)
        universalIntervalMs = settings.globalIntervalMs
        autoStopAtEnd = settings.autoStopAtEnd
        showTxLed = settings.showTxLed
        settingsLoaded = true
    }

    LaunchedEffect(savedRemotes) {
        if (!remotesLoaded) return@LaunchedEffect
        saveSavedRemotes(context, savedRemotes)
    }

    LaunchedEffect(settingsDirty, universalIntervalMs, autoStopAtEnd, showTxLed) {
        if (!settingsLoaded || !settingsDirty) return@LaunchedEffect
        saveAppSettings(
            context,
            com.vex.irshark.data.AppSettings(
                globalIntervalMs = universalIntervalMs,
                autoStopAtEnd = autoStopAtEnd,
                showTxLed = showTxLed
            )
        )
        settingsDirty = false
        settingsToastPending = true
    }

    LaunchedEffect(settingsToastPending) {
        if (!settingsToastPending) return@LaunchedEffect
        delay(700)
        settingsToastPending = false
        toastController.show("Settings saved")
    }

    // Universal auto-send loop
    val universalCoverage = universalCommand?.let {
        countProfilesForCommand(dbIndex, universalPath, it.actualCommand)
    } ?: 0

    LaunchedEffect(universalAutoSend, universalCommand, universalCoverage, universalIntervalMs) {
        if (!universalAutoSend || universalCommand == null || universalCoverage <= 0) return@LaunchedEffect
        try {
            while (universalAutoSend) {
                txPulseActive = true
                delay((universalIntervalMs.roundToInt().toLong() / 2).coerceAtLeast(70L))
                txPulseActive = false
                delay((universalIntervalMs.roundToInt().toLong() / 2).coerceAtLeast(70L))
                if (universalCodeStep >= universalCoverage) {
                    if (autoStopAtEnd) {
                        universalAutoSend = false
                        universalCommand = null
                        universalCodeStep = 0
                        break
                    }
                    universalCodeStep = 1
                } else {
                    universalCodeStep += 1
                }
            }
        } finally {
            txPulseActive = false
        }
    }

    // Control auto-send loop
    val controlCommands = controlCommandsCsv.split(";;").map { it.trim() }.filter { it.isNotBlank() }

    Box(
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF08060F), Color(0xFF0D0A18), Color(0xFF08060F))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            AppHeader(
                status = dbIndex.status,
                txActive = txPulseActive || universalAutoSend,
                showTxLed = showTxLed,
                fastBlink = universalAutoSend
            )
            if (screen != Screen.UNIVERSAL) {
                Spacer(modifier = Modifier.height(12.dp))
            }

            Box(modifier = Modifier.padding(horizontal = if (screen == Screen.UNIVERSAL) 0.dp else 14.dp)) {
                when (screen) {
                    Screen.HOME -> {
                        HomeScreen(
                            onUniversal = { screen = Screen.UNIVERSAL },
                            onMyRemotes = { screen = Screen.MY_REMOTES },
                            onRemoteDb = { screen = Screen.REMOTE_DB },
                            onSettings = { screen = Screen.SETTINGS }
                        )
                    }

                    Screen.UNIVERSAL -> {
                        UniversalRemoteScreen(
                            dbIndex = dbIndex,
                            currentPath = universalPath,
                            activeItem = universalCommand,
                            codeStep = universalCodeStep,
                            activeCoverage = universalCoverage,
                            autoSend = universalAutoSend,
                            intervalMs = universalIntervalMs,
                            onHome = {
                                universalAutoSend = false
                                screen = Screen.HOME
                            },
                            onBackPath = {
                                parentPath(universalPath)?.let {
                                    universalPath = it
                                    universalCommand = null
                                    universalCodeStep = 0
                                    universalAutoSend = false
                                }
                            },
                            onOpenFolder = { next ->
                                universalPath = next
                                universalCommand = null
                                universalCodeStep = 0
                                universalAutoSend = false
                            },
                            onCommandClick = { item ->
                                val coverage = countProfilesForCommand(dbIndex, universalPath, item.actualCommand)
                                if (coverage <= 1) {
                                    universalCommand = null
                                    universalCodeStep = 0
                                    universalAutoSend = false
                                    emitTxPulse()
                                } else {
                                    universalCommand = item
                                    universalAutoSend = true
                                    universalCodeStep = 1
                                }
                            },
                            onToggleAutoSend = {
                                if (universalAutoSend) {
                                    universalAutoSend = false
                                    universalCommand = null
                                    universalCodeStep = 0
                                    txPulseActive = false
                                } else {
                                    universalAutoSend = true
                                }
                            },
                            onIntervalChange = { universalIntervalMs = it }
                        )
                    }

                    Screen.MY_REMOTES -> {
                        val filtered = savedRemotes.filter {
                            myRemotesQuery.isBlank() ||
                                it.name.contains(myRemotesQuery, ignoreCase = true) ||
                                prettyPath(it.profilePath).contains(myRemotesQuery, ignoreCase = true)
                        }
                        RemotesListScreen(
                            title = "MY REMOTES",
                            query = myRemotesQuery,
                            queryLabel = "Search saved remotes",
                            onQueryChange = { myRemotesQuery = it },
                            onHome = { screen = Screen.HOME },
                            emptyText = "No saved remotes.",
                            items = filtered.map { it.name to prettyPath(it.profilePath) },
                            onOpen = { index ->
                                val remote = filtered[index]
                                controlProfilePath = remote.profilePath
                                controlName = remote.name
                                controlCommandsCsv = remote.commands.joinToString(";;")
                                controlSource = ControlSource.MY_REMOTES
                                controlSelectedCommand = null
                                controlTxCount = 0
                                screen = Screen.REMOTE_CONTROL
                            },
                            onSecondaryAction = { index ->
                                val remote = filtered[index]
                                savedRemotes = savedRemotes.filterNot { it.profilePath == remote.profilePath }
                                toastController.show("Removed from My Remotes")
                            },
                            secondaryActionLabel = "Delete"
                        )
                    }

                    Screen.REMOTE_DB -> {
                        val filtered = dbIndex.profiles.filter {
                            remoteDbQuery.isBlank() ||
                                it.name.contains(remoteDbQuery, ignoreCase = true) ||
                                prettyPath(it.parentPath).contains(remoteDbQuery, ignoreCase = true)
                        }.take(300)
                        RemotesListScreen(
                            title = "REMOTE DB",
                            query = remoteDbQuery,
                            queryLabel = "Search all database remotes",
                            onQueryChange = { remoteDbQuery = it },
                            onHome = { screen = Screen.HOME },
                            emptyText = "No matching remotes in database.",
                            items = filtered.map { it.name to prettyPath(it.parentPath) },
                            onOpen = { index ->
                                val profile = filtered[index]
                                controlProfilePath = profile.path
                                controlName = profile.name
                                controlCommandsCsv = profile.commands.joinToString(";;")
                                controlSource = ControlSource.REMOTE_DB
                                controlSelectedCommand = null
                                controlTxCount = 0
                                screen = Screen.REMOTE_CONTROL
                            },
                            onSecondaryAction = { index ->
                                val profile = filtered[index]
                                if (savedRemotes.none { it.profilePath == profile.path }) {
                                    savedRemotes = savedRemotes + SavedRemote(
                                        name = profile.name,
                                        profilePath = profile.path,
                                        commands = profile.commands
                                    )
                                    toastController.show("Added to My Remotes")
                                }
                            },
                            secondaryActionLabel = "Add",
                            secondaryActionLabelForItem = { idx ->
                                val path = filtered[idx].path
                                if (savedRemotes.any { it.profilePath == path }) "Added" else "Add"
                            },
                            secondaryActionEnabledForItem = { idx ->
                                val path = filtered[idx].path
                                savedRemotes.none { it.profilePath == path }
                            }
                        )
                    }

                    Screen.REMOTE_CONTROL -> {
                        val profilePath = controlProfilePath.orEmpty()
                        val currentProfile = dbIndex.profiles.firstOrNull { it.path == profilePath }
                        val commands = if (controlCommands.isNotEmpty()) controlCommands else currentProfile?.commands.orEmpty()
                        val title = controlName ?: currentProfile?.name ?: "Remote"
                        val subtitle = currentProfile?.let { prettyPath(it.parentPath) } ?: prettyPath(profilePath)

                        RemoteControlScreen(
                            title = title,
                            subtitle = subtitle,
                            commands = commands,
                            selectedCommand = controlSelectedCommand,
                            txCount = controlTxCount,
                            onBack = {
                                screen = if (controlSource == ControlSource.MY_REMOTES) Screen.MY_REMOTES else Screen.REMOTE_DB
                            },
                            onCommandClick = { _ ->
                                controlSelectedCommand = null
                                controlTxCount += 1
                                emitTxPulse()
                            },
                            onSave = {
                                if (profilePath.isNotBlank() && savedRemotes.none { it.profilePath == profilePath }) {
                                    savedRemotes = savedRemotes + SavedRemote(
                                        name = title,
                                        profilePath = profilePath,
                                        commands = commands
                                    )
                                }
                            },
                            showSaveButton = controlSource == ControlSource.REMOTE_DB
                        )
                    }

                    Screen.SETTINGS -> {
                        SettingsScreen(
                            intervalMs = universalIntervalMs,
                            autoStopAtEnd = autoStopAtEnd,
                            showTxLed = showTxLed,
                            onBack = { screen = Screen.HOME },
                            onIntervalChange = {
                                universalIntervalMs = it
                                settingsDirty = true
                            },
                            onAutoStopAtEndChange = {
                                autoStopAtEnd = it
                                settingsDirty = true
                            },
                            onShowTxLedChange = {
                                showTxLed = it
                                settingsDirty = true
                            },
                            onIntervalPresetSelect = {
                                universalIntervalMs = it
                                settingsDirty = true
                            },
                            onResetDefaults = {
                                universalIntervalMs = 250f
                                autoStopAtEnd = true
                                showTxLed = true
                                settingsDirty = true
                            }
                        )
                    }
                }
            }

        }

        AppToastHost(
            controller = toastController,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun IRSharkPreview() {
    IRSharkTheme(darkTheme = true, dynamicColor = false) {
        IRSharkApp(modifier = Modifier.fillMaxSize())
    }
}
