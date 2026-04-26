package com.vex.irshark

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.core.content.FileProvider
import com.vex.irshark.data.FlipperDbIndex
import com.vex.irshark.data.DbLoadProgress
import com.vex.irshark.data.SavedRemote
import com.vex.irshark.data.SavedRemoteButton
import com.vex.irshark.data.UniversalCommandItem
import com.vex.irshark.data.countProfilesForCommand
import com.vex.irshark.data.categorySeedFromPath
import com.vex.irshark.data.dbRootPath
import com.vex.irshark.data.exportRemotesToJson
import com.vex.irshark.data.exportMacrosToJson
import com.vex.irshark.data.importRemotesFromJson
import com.vex.irshark.data.importMacrosFromJson
import com.vex.irshark.data.loadAppSettings
import com.vex.irshark.data.loadSavedMacros
import com.vex.irshark.data.saveSavedMacros
import com.vex.irshark.data.SavedMacro
import com.vex.irshark.util.transmitIrCode
import com.vex.irshark.data.loadDbIrCodeOptions
import com.vex.irshark.data.loadFlipperDbIndex
import com.vex.irshark.data.loadSavedRemotes
import com.vex.irshark.data.parentPath
import com.vex.irshark.data.prettyPath
import com.vex.irshark.data.saveAppSettings
import com.vex.irshark.data.saveSavedRemotes
import com.vex.irshark.ui.components.AppHeader
import com.vex.irshark.ui.components.RemoteEditorDialog
import com.vex.irshark.ui.components.AppToastController
import com.vex.irshark.ui.components.AppToastHost
import com.vex.irshark.ui.components.SectionNavBar
import com.vex.irshark.ui.screens.HomeScreen
import com.vex.irshark.ui.screens.MacroEditorScreen
import com.vex.irshark.ui.screens.MacroListScreen
import com.vex.irshark.ui.screens.MacroDoneScreen
import com.vex.irshark.ui.screens.MacroRunScreen
import com.vex.irshark.ui.screens.RemoteControlScreen
import com.vex.irshark.ui.screens.RemotesListScreen
import com.vex.irshark.ui.screens.SettingsScreen
import com.vex.irshark.ui.screens.IrFinderScreen
import com.vex.irshark.ui.screens.SplashScreen
import com.vex.irshark.ui.screens.UniversalRemoteScreen
import com.vex.irshark.ui.theme.IRSharkTheme
import com.vex.irshark.macro.MacroEngine
import com.vex.irshark.macro.MacroRunState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
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
    HOME, UNIVERSAL, MY_REMOTES, REMOTE_DB, REMOTE_CONTROL, SETTINGS, MACROS, MACRO_EDITOR, MACRO_RUN, IR_FINDER
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
    var hapticFeedback by rememberSaveable { mutableStateOf(true) }
    var irFinderLastTested by rememberSaveable { mutableStateOf<String?>(null) }
    var txPulseActive by remember { mutableStateOf(false) }
    var settingsDirty by remember { mutableStateOf(false) }
    var settingsToastPending by remember { mutableStateOf(false) }
    var settingsLoaded by remember { mutableStateOf(false) }
    var dbLoaded by remember { mutableStateOf(false) }
    var dbLoadProgress by remember { mutableStateOf(DbLoadProgress()) }
    var remotesLoaded by remember { mutableStateOf(false) }
    var txPulseJob by remember { mutableStateOf<Job?>(null) }
    val toastController = remember { AppToastController() }
    val scope = rememberCoroutineScope()

    // Macros
    var savedMacros by remember { mutableStateOf(listOf<SavedMacro>()) }
    var macrosLoaded by remember { mutableStateOf(false) }
    var editingMacroId by remember { mutableStateOf<String?>(null) }
    var macrosQuery by remember { mutableStateOf("") }
    var pendingDeleteMacroId by remember { mutableStateOf<String?>(null) }
    val macroEngine = remember { MacroEngine(context) }
    val macroState by macroEngine.state.collectAsState()
    LaunchedEffect(hapticFeedback) { macroEngine.hapticEnabled = hapticFeedback }

    fun shareJsonFile(fileStem: String, subject: String, chooserTitle: String, json: String) {
        scope.launch {
            runCatching {
                val uri = withContext(Dispatchers.IO) {
                    val safeStem = fileStem
                        .trim()
                        .ifBlank { "irshark_export" }
                        .replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val file = File(context.cacheDir, "${safeStem}_${System.currentTimeMillis()}.json")
                    file.writeText(json, Charsets.UTF_8)
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("IRShark JSON", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, chooserTitle))
            }.onFailure {
                toastController.show("Failed to prepare share file")
            }
        }
    }

    fun countImportEntries(json: String, wrapperKey: String): Int {
        val trimmed = json.trim()
        if (trimmed.isBlank()) return 0
        return runCatching {
            when {
                trimmed.startsWith("[") -> JSONArray(trimmed).length()
                trimmed.startsWith("{") -> {
                    val root = JSONObject(trimmed)
                    root.optJSONArray(wrapperKey)?.length() ?: 1
                }
                else -> 0
            }
        }.getOrDefault(0)
    }

    // ── Import / Export launchers ─────────────────────────────────────────────
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            scope.launch {
                val json = exportRemotesToJson(savedRemotes)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream -> stream.write(json.toByteArray()) }
                }
                toastController.show("Exported ${savedRemotes.size} remotes")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            scope.launch {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
                }
                if (json != null) {
                    val imported = importRemotesFromJson(json)
                    val totalEntries = countImportEntries(json, "remotes")
                    val invalidEntries = (totalEntries - imported.size).coerceAtLeast(0)
                    val existingNames = savedRemotes.map { it.name.lowercase() }.toSet()
                    val newOnes = imported.filter { it.name.lowercase() !in existingNames }
                    val duplicateEntries = imported.size - newOnes.size

                    if (newOnes.isNotEmpty()) {
                        savedRemotes = savedRemotes + newOnes
                    }

                    val health = "Remote import: +${newOnes.size}, dup $duplicateEntries, invalid $invalidEntries"
                    if (newOnes.isEmpty()) {
                        toastController.show("No remotes imported. $health")
                    } else {
                        toastController.show(health)
                    }
                }
            }
        }
    }
    val macroExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            scope.launch {
                val json = exportMacrosToJson(savedMacros)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream -> stream.write(json.toByteArray()) }
                }
                toastController.show("Exported ${savedMacros.size} macros")
            }
        }
    }
    val macroImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            scope.launch {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
                }
                if (json != null) {
                    val imported = importMacrosFromJson(json)
                    val totalEntries = countImportEntries(json, "macros")
                    val invalidEntries = (totalEntries - imported.size).coerceAtLeast(0)
                    if (imported.isEmpty()) {
                        toastController.show("No valid macros in file (invalid $invalidEntries)")
                    } else {
                        val takenNames = savedMacros.map { it.name.lowercase() }.toMutableSet()
                        val takenIds = savedMacros.map { it.id }.toMutableSet()
                        var renamedCount = 0
                        var regeneratedIdCount = 0

                        fun uniqueMacroName(baseName: String): String {
                            val base = baseName.trim().ifBlank { "Macro" }
                            if (base.lowercase() !in takenNames) return base
                            var suffix = 2
                            while ("$base $suffix".lowercase() in takenNames) {
                                suffix += 1
                            }
                            return "$base $suffix"
                        }

                        val normalized = imported.map { macro ->
                            val resolvedName = uniqueMacroName(macro.name)
                            if (!resolvedName.equals(macro.name, ignoreCase = false)) {
                                renamedCount += 1
                            }
                            takenNames += resolvedName.lowercase()

                            val resolvedId = if (macro.id.isBlank() || macro.id in takenIds) {
                                regeneratedIdCount += 1
                                UUID.randomUUID().toString()
                            } else {
                                macro.id
                            }
                            takenIds += resolvedId

                            macro.copy(id = resolvedId, name = resolvedName)
                        }

                        savedMacros = savedMacros + normalized
                        toastController.show(
                            "Macro import: +${normalized.size}, renamed $renamedCount, invalid $invalidEntries, id-fix $regeneratedIdCount"
                        )
                    }
                }
            }
        }
    }

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
    var controlRemoteIndex by rememberSaveable { mutableIntStateOf(-1) }
    var controlButtons by remember { mutableStateOf(listOf<SavedRemoteButton>()) }
    var controlSource by rememberSaveable { mutableStateOf(ControlSource.REMOTE_DB) }
    var controlSelectedCommand by rememberSaveable { mutableStateOf<String?>(null) }
    var controlTxCount by rememberSaveable { mutableIntStateOf(0) }

    // Editor state for custom/editable remotes
    var showRemoteEditor by remember { mutableStateOf(false) }
    var editingRemoteIndex by remember { mutableStateOf<Int?>(null) }
    var pendingDeleteRemoteIndex by remember { mutableStateOf<Int?>(null) }

    // IR Finder nav state (lifted from IrFinderScreen)
    var irFinderBreadcrumb by remember { mutableStateOf<String?>(null) }
    var irFinderOnBack by remember { mutableStateOf<(() -> Unit)?>(null) }
    var irFinderOnUndo by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Clear IR Finder nav state when navigating away
    LaunchedEffect(screen) {
        if (screen != Screen.IR_FINDER) {
            irFinderBreadcrumb = null
            irFinderOnBack = null
            irFinderOnUndo = null
        }
    }

    // Blink TX LED whenever the macro engine transmits an IR command
    LaunchedEffect(macroEngine) {
        macroEngine.irTransmitEvent.collect { emitTxPulse() }
    }

    fun uniqueRemoteName(baseName: String, excludeIndex: Int? = null): String {
        val taken = savedRemotes
            .filterIndexed { idx, _ -> excludeIndex == null || idx != excludeIndex }
            .map { it.name.lowercase() }
            .toSet()

        val base = baseName.trim().ifBlank { "Custom Remote" }
        if (!taken.contains(base.lowercase())) return base

        var suffix = 2
        while (taken.contains("$base $suffix".lowercase())) {
            suffix += 1
        }
        return "$base $suffix"
    }

    fun hydrateMissingCodesFromDb(
        buttons: List<SavedRemoteButton>,
        dbCodes: List<com.vex.irshark.data.DbIrCodeOption>
    ): List<SavedRemoteButton> {
        if (buttons.isEmpty() || dbCodes.isEmpty()) return buttons
        val byLabel = dbCodes.associateBy { it.label.trim().uppercase() }
        return buttons.map { button ->
            if (button.code.isNotBlank()) {
                button
            } else {
                val hit = byLabel[button.label.trim().uppercase()]
                if (hit != null) {
                    button.copy(code = hit.code)
                } else {
                    button
                }
            }
        }
    }

    // Load data
    LaunchedEffect(Unit) {
        dbIndex = loadFlipperDbIndex(context) { progress ->
            dbLoadProgress = progress
        }
        dbLoaded = true
        savedRemotes = loadSavedRemotes(context)
        remotesLoaded = true
        savedMacros = loadSavedMacros(context)
        macrosLoaded = true
        val settings = loadAppSettings(context)
        universalIntervalMs = settings.globalIntervalMs
        autoStopAtEnd = settings.autoStopAtEnd
        showTxLed = settings.showTxLed
        hapticFeedback = settings.hapticFeedback
        irFinderLastTested = settings.irFinderLastTested
        settingsLoaded = true
    }

    LaunchedEffect(savedRemotes) {
        if (!remotesLoaded) return@LaunchedEffect
        saveSavedRemotes(context, savedRemotes)
    }

    LaunchedEffect(savedMacros) {
        if (!macrosLoaded) return@LaunchedEffect
        saveSavedMacros(context, savedMacros)
    }

    LaunchedEffect(settingsDirty, universalIntervalMs, autoStopAtEnd, showTxLed, hapticFeedback) {
        if (!settingsLoaded || !settingsDirty) return@LaunchedEffect
        saveAppSettings(
            context,
            com.vex.irshark.data.AppSettings(
                globalIntervalMs = universalIntervalMs,
                autoStopAtEnd = autoStopAtEnd,
                showTxLed = showTxLed,
                hapticFeedback = hapticFeedback,
                irFinderLastTested = irFinderLastTested
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

    // Control command labels
    val controlCommands = controlButtons.map { it.label }.filter { it.isNotBlank() }
    // Show splash while loading
    if (!settingsLoaded || !dbLoaded) {
        SplashScreen(
            loadedFiles = dbLoadProgress.loadedFiles,
            totalFiles = dbLoadProgress.totalFiles
        )
        return
    }
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
            val screenTitle = when (screen) {
                Screen.HOME, Screen.REMOTE_CONTROL -> "IRShark"
                Screen.UNIVERSAL -> "Universal Remote"
                Screen.MY_REMOTES -> "My Remotes"
                Screen.REMOTE_DB -> "Remote DB"
                Screen.SETTINGS -> "Settings"
                Screen.MACROS -> "Macros"
                Screen.MACRO_EDITOR -> "Macro Editor"
                Screen.MACRO_RUN -> "Running Macro"
                Screen.IR_FINDER -> "IR Finder"
            }
            AppHeader(
                txActive = txPulseActive || universalAutoSend,
                showTxLed = showTxLed,
                fastBlink = universalAutoSend,
                screenTitle = screenTitle
            )
            if (screen in listOf(Screen.MY_REMOTES, Screen.REMOTE_DB, Screen.SETTINGS, Screen.MACROS, Screen.IR_FINDER)) {
                SectionNavBar(
                    onHome = { screen = Screen.HOME },
                    breadcrumb = if (screen == Screen.IR_FINDER) (irFinderBreadcrumb ?: "Root") else null,
                    onBack = if (screen == Screen.IR_FINDER) irFinderOnBack else null,
                    extraActions = if (screen == Screen.IR_FINDER && irFinderOnUndo != null)
                        listOf(Icons.Filled.RestartAlt to irFinderOnUndo!!)
                    else emptyList(),
                    searchQuery = when (screen) {
                        Screen.MACROS -> macrosQuery
                        Screen.MY_REMOTES -> myRemotesQuery
                        Screen.REMOTE_DB -> remoteDbQuery
                        else -> null
                    },
                    searchPlaceholder = when (screen) {
                        Screen.MY_REMOTES -> "Search saved remotes"
                        Screen.REMOTE_DB -> "Search all database remotes"
                        else -> "Search macros"
                    },
                    onSearchQuery = when (screen) {
                        Screen.MACROS -> ({ macrosQuery = it })
                        Screen.MY_REMOTES -> ({ myRemotesQuery = it })
                        Screen.REMOTE_DB -> ({ remoteDbQuery = it })
                        else -> null
                    },
                    actions = when (screen) {
                        Screen.MY_REMOTES -> listOf(
                            Icons.Filled.Add to {
                                editingRemoteIndex = null
                                showRemoteEditor = true
                            },
                            Icons.Filled.Download to {
                                importLauncher.launch(arrayOf("application/json", "text/plain"))
                            },
                            Icons.Filled.Upload to {
                                exportLauncher.launch("irshark_remotes.json")
                            }
                        )
                        Screen.MACROS -> listOf(
                            Icons.Filled.Download to {
                                macroImportLauncher.launch(arrayOf("application/json", "text/plain"))
                            },
                            Icons.Filled.Upload to {
                                macroExportLauncher.launch("irshark_macros.json")
                            },
                            Icons.Filled.Add to {
                                editingMacroId = null
                                screen = Screen.MACRO_EDITOR
                            }
                        )
                        else -> emptyList()
                    }
                )
            }
            if (screen !in listOf(Screen.UNIVERSAL, Screen.MACRO_EDITOR)) {
                Spacer(modifier = Modifier.height(12.dp))
            }

            Box(modifier = Modifier
                .weight(1f)
                .padding(horizontal = if (screen in listOf(Screen.UNIVERSAL, Screen.MACRO_EDITOR)) 0.dp else 14.dp)) {
                when (screen) {
                    Screen.HOME -> {
                        HomeScreen(
                            onUniversal = { screen = Screen.UNIVERSAL },
                            onMyRemotes = { screen = Screen.MY_REMOTES },
                            onRemoteDb  = { screen = Screen.REMOTE_DB },
                            onSettings  = { screen = Screen.SETTINGS },
                            onMacros    = { screen = Screen.MACROS },
                            onIrFinder  = { screen = Screen.IR_FINDER }
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
                        val indexedFiltered = savedRemotes.withIndex().filter {
                            myRemotesQuery.isBlank() ||
                                it.value.name.contains(myRemotesQuery, ignoreCase = true) ||
                                prettyPath(it.value.profilePath).contains(myRemotesQuery, ignoreCase = true)
                        }.sortedByDescending { it.value.favorite }
                        RemotesListScreen(
                            emptyText = "No saved remotes.",
                            items = indexedFiltered.map { (_, remote) ->
                                val subtitle = when {
                                    remote.sourceProfilePath != null -> "From DB: ${prettyPath(remote.sourceProfilePath)}"
                                    remote.profilePath.isNotBlank() -> prettyPath(remote.profilePath)
                                    else -> "Custom remote"
                                }
                                remote.name to subtitle
                            },
                            iconNameForItem = { idx ->
                                val remote = indexedFiltered[idx].value
                                remote.iconName ?: categorySeedFromPath(remote.sourceProfilePath ?: remote.profilePath)
                            },
                            isFavoriteForItem = { idx -> indexedFiltered[idx].value.favorite },
                            onFavoriteToggleForItem = { idx ->
                                val originalIndex = indexedFiltered[idx].index
                                val remote = indexedFiltered[idx].value
                                savedRemotes = savedRemotes.toMutableList().also {
                                    it[originalIndex] = remote.copy(favorite = !remote.favorite)
                                }
                            },
                            onDuplicateForItem = { idx ->
                                val remote = indexedFiltered[idx].value
                                val newName = uniqueRemoteName(remote.name + " copy")
                                savedRemotes = savedRemotes + remote.copy(name = newName, favorite = false)
                                toastController.show("Duplicated as \"$newName\"")
                            },
                            onOpen = { index ->
                                val originalIndex = indexedFiltered[index].index
                                val remote = indexedFiltered[index].value
                                controlProfilePath = remote.profilePath
                                controlName = remote.name
                                controlButtons = if (remote.buttons.isNotEmpty()) {
                                    remote.buttons
                                } else {
                                    remote.commands.map { SavedRemoteButton(label = it, code = "") }
                                }
                                controlRemoteIndex = originalIndex
                                controlSource = ControlSource.MY_REMOTES
                                controlSelectedCommand = null
                                controlTxCount = 0

                                // Backfill missing DB codes for previously imported remotes.
                                if (remote.sourceProfilePath != null && controlButtons.any { it.code.isBlank() }) {
                                    scope.launch {
                                        val dbCodes = loadDbIrCodeOptions(context, remote.sourceProfilePath)
                                        val hydrated = hydrateMissingCodesFromDb(controlButtons, dbCodes)
                                        controlButtons = hydrated
                                        savedRemotes = savedRemotes.toMutableList().also {
                                            val prev = it.getOrNull(originalIndex)
                                            if (prev != null) {
                                                it[originalIndex] = prev.copy(
                                                    buttons = hydrated,
                                                    commands = hydrated.map { b -> b.label }
                                                )
                                            }
                                        }
                                    }
                                }

                                screen = Screen.REMOTE_CONTROL
                            },
                            onSecondaryAction = { index ->
                                val originalIndex = indexedFiltered[index].index
                                pendingDeleteRemoteIndex = originalIndex
                            },
                            secondaryActionLabel = "Delete",
                            secondaryActionIcon = Icons.Filled.Delete
                        )
                    }

                    Screen.REMOTE_DB -> {
                        val filtered = dbIndex.profiles.filter {
                            remoteDbQuery.isBlank() ||
                                it.name.contains(remoteDbQuery, ignoreCase = true) ||
                                prettyPath(it.parentPath).contains(remoteDbQuery, ignoreCase = true)
                        }.take(300)
                        RemotesListScreen(
                            emptyText = "No matching remotes in database.",
                            items = filtered.map { it.name to prettyPath(it.parentPath) },
                            iconNameForItem = { idx ->
                                categorySeedFromPath(filtered[idx].parentPath)
                            },
                            onOpen = { index ->
                                val profile = filtered[index]
                                controlProfilePath = profile.path
                                controlName = profile.name
                                controlButtons = profile.commands.map { cmd ->
                                    SavedRemoteButton(label = cmd, code = "")
                                }
                                controlRemoteIndex = -1
                                controlSource = ControlSource.REMOTE_DB
                                controlSelectedCommand = null
                                controlTxCount = 0

                                scope.launch {
                                    val dbCodes = loadDbIrCodeOptions(context, profile.path)
                                    controlButtons = hydrateMissingCodesFromDb(controlButtons, dbCodes)
                                }

                                screen = Screen.REMOTE_CONTROL
                            },
                            onSecondaryAction = { index ->
                                val profile = filtered[index]
                                if (savedRemotes.none { it.sourceProfilePath == profile.path }) {
                                    scope.launch {
                                        val dbCodes = loadDbIrCodeOptions(context, profile.path)
                                        val seededButtons = profile.commands.map { cmd ->
                                            SavedRemoteButton(
                                                label = cmd,
                                                code = ""
                                            )
                                        }
                                        val hydratedButtons = hydrateMissingCodesFromDb(seededButtons, dbCodes)
                                        val resolvedName = uniqueRemoteName(profile.name)
                                        savedRemotes = savedRemotes + SavedRemote(
                                            name = resolvedName,
                                            profilePath = profile.path,
                                            commands = hydratedButtons.map { it.label },
                                            buttons = hydratedButtons,
                                            iconName = categorySeedFromPath(profile.parentPath),
                                            sourceProfilePath = profile.path
                                        )
                                        toastController.show("Added to My Remotes")
                                    }
                                }
                            },
                            secondaryActionLabel = "Add",
                            secondaryActionLabelForItem = { idx ->
                                val path = filtered[idx].path
                                if (savedRemotes.any { it.sourceProfilePath == path }) "Added" else "Add"
                            },
                            secondaryActionEnabledForItem = { idx ->
                                val path = filtered[idx].path
                                savedRemotes.none { it.sourceProfilePath == path }
                            }
                        )
                    }

                    Screen.REMOTE_CONTROL -> {
                        val profilePath = controlProfilePath.orEmpty()
                        val currentProfile = dbIndex.profiles.firstOrNull { it.path == profilePath }
                        val commands = if (controlCommands.isNotEmpty()) controlCommands else currentProfile?.commands.orEmpty()
                        val title = controlName ?: currentProfile?.name ?: "Remote"
                        val activeSavedRemote = if (controlSource == ControlSource.MY_REMOTES && controlRemoteIndex in savedRemotes.indices) {
                            savedRemotes[controlRemoteIndex]
                        } else {
                            null
                        }

                        val typeBadge = when {
                            activeSavedRemote?.sourceProfilePath == null && controlSource == ControlSource.MY_REMOTES -> "Custom"
                            activeSavedRemote?.sourceProfilePath != null -> {
                                val parent = dbIndex.profiles
                                    .firstOrNull { it.path == activeSavedRemote.sourceProfilePath }
                                    ?.parentPath
                                if (parent.isNullOrBlank()) "From DB" else prettyPath(parent)
                            }
                            currentProfile != null -> prettyPath(currentProfile.parentPath)
                            profilePath.isNotBlank() -> prettyPath(profilePath)
                            else -> "Custom"
                        }
                        val countBadge = "${commands.size} buttons"

                        RemoteControlScreen(
                            title = title,
                            deviceIconName = (activeSavedRemote?.sourceProfilePath
                                ?: currentProfile?.parentPath
                                ?: profilePath).let { path ->
                                    activeSavedRemote?.iconName ?: categorySeedFromPath(path)
                                },
                            typeBadge = typeBadge,
                            countBadge = countBadge,
                            buttons = controlButtons,
                            selectedCommand = controlSelectedCommand,
                            txCount = controlTxCount,
                            hapticEnabled = hapticFeedback,
                            onBack = {
                                controlRemoteIndex = -1
                                screen = if (controlSource == ControlSource.MY_REMOTES) Screen.MY_REMOTES else Screen.REMOTE_DB
                            },
                            onCommandClick = { cmdLabel ->
                                controlSelectedCommand = null
                                controlTxCount += 1
                                emitTxPulse()
                                // Find the IR code for this button and transmit
                                val button = controlButtons.firstOrNull { it.label.equals(cmdLabel, ignoreCase = true) }
                                if (button != null && button.code.isNotBlank()) {
                                    scope.launch(Dispatchers.IO) {
                                        transmitIrCode(context, button.code)
                                    }
                                }
                            },
                            onEdit = {
                                if (controlSource == ControlSource.MY_REMOTES && controlRemoteIndex in savedRemotes.indices) {
                                    editingRemoteIndex = controlRemoteIndex
                                    showRemoteEditor = true
                                }
                            },
                            onSave = {
                                if (profilePath.isNotBlank() && savedRemotes.none { it.sourceProfilePath == profilePath }) {
                                    val resolvedName = uniqueRemoteName(title)
                                    savedRemotes = savedRemotes + SavedRemote(
                                        name = resolvedName,
                                        profilePath = profilePath,
                                        commands = commands,
                                        buttons = controlButtons,
                                        sourceProfilePath = profilePath
                                    )
                                }
                            },
                            showSaveButton = controlSource == ControlSource.REMOTE_DB,
                            showEditButton = controlSource == ControlSource.MY_REMOTES,
                            onShare = if (controlSource == ControlSource.MY_REMOTES && controlRemoteIndex in savedRemotes.indices) {
                                {
                                    val remote = savedRemotes[controlRemoteIndex]
                                    val json = exportRemotesToJson(listOf(remote))
                                    shareJsonFile(
                                        fileStem = "remote_${remote.name}",
                                        subject = "IRShark Remote: ${remote.name}",
                                        chooserTitle = "Share \"${remote.name}\"",
                                        json = json
                                    )
                                }
                            } else null
                        )
                    }

                    Screen.SETTINGS -> {
                        SettingsScreen(
                            intervalMs = universalIntervalMs,
                            autoStopAtEnd = autoStopAtEnd,
                            showTxLed = showTxLed,
                            hapticFeedback = hapticFeedback,
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
                            onHapticFeedbackChange = {
                                hapticFeedback = it
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
                                hapticFeedback = true
                                settingsDirty = true
                            }
                        )
                    }

                    Screen.MACROS -> {
                        val macroFiltered = savedMacros.filter {
                            macrosQuery.isBlank() || it.name.contains(macrosQuery, ignoreCase = true)
                        }
                        MacroListScreen(
                            macros   = macroFiltered,
                            onPlay   = { macro ->
                                macroEngine.launch(macro, scope)
                                screen = Screen.MACRO_RUN
                            },
                            onEdit   = { macro ->
                                editingMacroId = macro.id
                                screen = Screen.MACRO_EDITOR
                            },
                            onDelete = { macro -> pendingDeleteMacroId = macro.id },
                            onShare = { macro ->
                                val json = exportMacrosToJson(listOf(macro))
                                shareJsonFile(
                                    fileStem = "macro_${macro.name}",
                                    subject = "IRShark Macro: ${macro.name}",
                                    chooserTitle = "Share \"${macro.name}\"",
                                    json = json
                                )
                            }
                        )
                    }

                    Screen.MACRO_EDITOR -> {
                        val initialMacro = editingMacroId?.let { id -> savedMacros.find { it.id == id } }
                        MacroEditorScreen(
                            dbProfiles    = dbIndex.profiles,
                            savedRemotes  = savedRemotes,
                            initialMacro  = initialMacro,
                            existingNames = savedMacros.filter { it.id != initialMacro?.id }.map { it.name }.toSet(),
                            onSave        = { macro ->
                                savedMacros = if (savedMacros.any { it.id == macro.id }) {
                                    savedMacros.map { if (it.id == macro.id) macro else it }
                                } else {
                                    savedMacros + macro
                                }
                                editingMacroId = null
                                screen = Screen.MACROS
                            },
                            onDismiss     = {
                                editingMacroId = null
                                screen = Screen.MACROS
                            }
                        )
                    }

                    Screen.IR_FINDER -> {
                        IrFinderScreen(
                            dbIndex = dbIndex,
                            onTransmit = { emitTxPulse() },
                            addedProfilePaths = savedRemotes.mapNotNull { it.sourceProfilePath }.toSet(),
                            hapticEnabled = hapticFeedback,
                            lastTested = irFinderLastTested,
                            onUpdateLastTested = { tested ->
                                irFinderLastTested = tested
                                scope.launch(Dispatchers.IO) {
                                    saveAppSettings(
                                        context,
                                        com.vex.irshark.data.AppSettings(
                                            globalIntervalMs = universalIntervalMs,
                                            autoStopAtEnd = autoStopAtEnd,
                                            showTxLed = showTxLed,
                                            hapticFeedback = hapticFeedback,
                                            irFinderLastTested = tested
                                        )
                                    )
                                }
                            },
                            onAddRemote = { profilePath, profileName, commands ->
                                if (savedRemotes.none { it.sourceProfilePath == profilePath }) {
                                    scope.launch {
                                        val dbCodes = loadDbIrCodeOptions(context, profilePath)
                                        val seededButtons = commands.map { cmd ->
                                            SavedRemoteButton(
                                                label = cmd,
                                                code = ""
                                            )
                                        }
                                        val hydratedButtons = hydrateMissingCodesFromDb(seededButtons, dbCodes)
                                        val resolvedName = uniqueRemoteName(profileName)
                                        savedRemotes = savedRemotes + SavedRemote(
                                            name = resolvedName,
                                            profilePath = profilePath,
                                            commands = hydratedButtons.map { it.label },
                                            buttons = hydratedButtons,
                                            iconName = categorySeedFromPath(profilePath),
                                            sourceProfilePath = profilePath
                                        )
                                        toastController.show("Added to My Remotes")
                                    }
                                }
                            },
                            onNavStateChange = { breadcrumb, onBack, onUndo ->
                                irFinderBreadcrumb = breadcrumb
                                irFinderOnBack = onBack
                                irFinderOnUndo = onUndo
                            }
                        )
                    }

                    Screen.MACRO_RUN -> {
                        val running = macroState as? MacroRunState.Running
                        if (running != null) {
                            MacroRunScreen(
                                state     = running,
                                onStop    = { macroEngine.stop(); screen = Screen.MACROS },
                                onYes     = { macroEngine.respondYes() },
                                onNo      = { macroEngine.respondNo() },
                                onSwitch  = { macroEngine.respondSwitch(it) }
                            )
                        } else {
                            val snap      = macroState
                            val finished  = snap is MacroRunState.Finished
                            val irLog     = when (snap) {
                                is MacroRunState.Finished  -> snap.irLog
                                is MacroRunState.Cancelled -> snap.irLog
                                else                       -> emptyList()
                            }
                            MacroDoneScreen(
                                finished  = finished,
                                macroName = when (snap) {
                                    is MacroRunState.Finished  -> snap.macroName
                                    is MacroRunState.Cancelled -> snap.macroName
                                    else                       -> ""
                                },
                                irLog   = irLog,
                                onDone  = { screen = Screen.MACROS }
                            )
                        }
                    }
                }
            }

        }

        if (showRemoteEditor) {
            val editIndex = editingRemoteIndex
            val existing = editIndex?.let { savedRemotes.getOrNull(it) }
            RemoteEditorDialog(
                initialName = existing?.name.orEmpty(),
                initialButtons = existing?.buttons.orEmpty(),
                initialIconName = existing?.iconName,
                existingNames = savedRemotes.map { it.name }.toSet(),
                originalName = existing?.name,
                dbProfiles = dbIndex.profiles,
                onDismiss = {
                    showRemoteEditor = false
                    editingRemoteIndex = null
                },
                onSave = { rawName, buttons, iconName ->
                    val normalizedButtons = buttons.map {
                        it.copy(
                            label = it.label.trim(),
                            code = it.code.trim()
                        )
                    }.filter { it.label.isNotBlank() }

                    val resolvedName = uniqueRemoteName(rawName, excludeIndex = editIndex)

                    if (editIndex != null && editIndex in savedRemotes.indices) {
                        val previous = savedRemotes[editIndex]
                        val changed = previous.name != resolvedName || previous.buttons != normalizedButtons
                        val detachFromDb = previous.sourceProfilePath != null && changed

                        val updated = previous.copy(
                            name = resolvedName,
                            profilePath = if (detachFromDb) "" else previous.profilePath,
                            commands = normalizedButtons.map { it.label },
                            buttons = normalizedButtons,
                            iconName = iconName,
                            sourceProfilePath = if (detachFromDb) null else previous.sourceProfilePath
                        )

                        savedRemotes = savedRemotes.toMutableList().also { it[editIndex] = updated }

                        if (controlSource == ControlSource.MY_REMOTES && controlRemoteIndex == editIndex) {
                            controlName = updated.name
                            controlButtons = updated.buttons
                            controlProfilePath = updated.profilePath
                        }
                        toastController.show("Remote updated")
                    } else {
                        savedRemotes = savedRemotes + SavedRemote(
                            name = resolvedName,
                            profilePath = "",
                            commands = normalizedButtons.map { it.label },
                            buttons = normalizedButtons,
                            iconName = iconName,
                            sourceProfilePath = null
                        )
                        toastController.show("Remote created")
                    }

                    showRemoteEditor = false
                    editingRemoteIndex = null
                }
            )
        }

        if (pendingDeleteRemoteIndex != null) {
            val deleteIndex = pendingDeleteRemoteIndex ?: -1
            val deleteTarget = savedRemotes.getOrNull(deleteIndex)
            if (deleteTarget != null) {
                AlertDialog(
                    onDismissRequest = { pendingDeleteRemoteIndex = null },
                    title = { Text("Delete remote") },
                    text = { Text("Delete '${deleteTarget.name}' from My Remotes?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                savedRemotes = savedRemotes.toMutableList().also { it.removeAt(deleteIndex) }
                                pendingDeleteRemoteIndex = null
                                toastController.show("Removed from My Remotes")
                            }
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteRemoteIndex = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        if (pendingDeleteMacroId != null) {
            val target = savedMacros.find { it.id == pendingDeleteMacroId }
            if (target != null) {
                AlertDialog(
                    onDismissRequest = { pendingDeleteMacroId = null },
                    title = { Text("Delete macro") },
                    text = { Text("Delete '${target.name}'?") },
                    confirmButton = {
                        TextButton(onClick = {
                            savedMacros = savedMacros.filter { it.id != target.id }
                            pendingDeleteMacroId = null
                            toastController.show("Macro deleted")
                        }) { Text("Delete") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteMacroId = null }) { Text("Cancel") }
                    }
                )
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
