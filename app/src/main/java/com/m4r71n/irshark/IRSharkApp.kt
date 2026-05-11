package com.m4r71n.irshark

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.m4r71n.irshark.data.FlipperDbIndex
import com.m4r71n.irshark.data.DbLoadProgress
import com.m4r71n.irshark.data.FlipperProfile
import com.m4r71n.irshark.data.RemoteHistoryEntry
import com.m4r71n.irshark.data.SavedRemote
import com.m4r71n.irshark.data.SavedRemoteButton
import com.m4r71n.irshark.data.UniversalCommandItem
import com.m4r71n.irshark.data.getUniquePayloadsForCommand
import com.m4r71n.irshark.data.categorySeedFromPath
import com.m4r71n.irshark.data.bundledDbVersionLabel
import com.m4r71n.irshark.data.dbRootPath
import com.m4r71n.irshark.data.exportRemotesToJson
import com.m4r71n.irshark.data.exportMacrosToJson
import com.m4r71n.irshark.data.importRemotesFromJson
import com.m4r71n.irshark.data.importMacrosFromJson
import com.m4r71n.irshark.data.isDownloadedDbAvailable
import com.m4r71n.irshark.data.loadAppSettings
import com.m4r71n.irshark.data.loadRemoteHistory
import com.m4r71n.irshark.data.loadSavedMacros
import com.m4r71n.irshark.data.saveSavedMacros
import com.m4r71n.irshark.data.SavedMacro
import com.m4r71n.irshark.data.recordRemoteHistory
import com.m4r71n.irshark.data.resolveEffectiveDbSource
import com.m4r71n.irshark.ir.transmitIrCode
import com.m4r71n.irshark.ir.IrTransmitStatus
import com.m4r71n.irshark.ir.transmitIrCodeResult
import com.m4r71n.irshark.ir.IrTransmissionManager
import com.m4r71n.irshark.ir.getIrCompatibilityReport
import com.m4r71n.irshark.data.loadDbIrCodeOptions
import com.m4r71n.irshark.data.loadFlipperDbIndex
import com.m4r71n.irshark.data.loadSavedRemotes
import com.m4r71n.irshark.data.parentPath
import com.m4r71n.irshark.data.prettyName
import com.m4r71n.irshark.data.prettyPath
import com.m4r71n.irshark.data.prettyPathWithChevron
import com.m4r71n.irshark.data.saveAppSettings
import com.m4r71n.irshark.data.saveRemoteHistory
import com.m4r71n.irshark.data.saveSavedRemotes
import com.m4r71n.irshark.data.importFlipperDatabaseFromZip
import com.m4r71n.irshark.ui.components.AppHeader
import com.m4r71n.irshark.ui.components.AppToastController
import com.m4r71n.irshark.ui.components.AppToastHost
import com.m4r71n.irshark.ui.components.RemoteControlNavBar
import com.m4r71n.irshark.ui.components.SectionNavBar
import com.m4r71n.irshark.ui.screens.HomeScreen
import com.m4r71n.irshark.ui.screens.MacroEditorScreen
import com.m4r71n.irshark.ui.screens.MacroListScreen
import com.m4r71n.irshark.ui.screens.MacroDoneScreen
import com.m4r71n.irshark.ui.screens.MacroRunScreen
import com.m4r71n.irshark.ui.screens.RemoteButtonEditorScreen
import com.m4r71n.irshark.ui.screens.RemoteControlScreen
import com.m4r71n.irshark.ui.screens.RemoteEditorScreen
import com.m4r71n.irshark.ui.screens.RemotesListScreen
import com.m4r71n.irshark.ui.screens.SettingsScreen
import com.m4r71n.irshark.ui.screens.IrFinderScreen
import com.m4r71n.irshark.ui.screens.SplashScreen
import com.m4r71n.irshark.ui.screens.UniversalRemoteScreen
import com.m4r71n.irshark.ui.theme.IRSharkTheme
import com.m4r71n.irshark.macro.MacroEngine
import com.m4r71n.irshark.macro.MacroRunState
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


// ── Navigation state ──────────────────────────────────────────────────────────

private enum class Screen {
    HOME,
    UNIVERSAL,
    MY_REMOTES,
    REMOTE_DB,
    REMOTE_CONTROL,
    REMOTE_EDITOR,
    REMOTE_BUTTON_EDITOR,
    SETTINGS,
    MACROS,
    MACRO_EDITOR,
    MACRO_RUN,
    IR_FINDER
}

private enum class RemoteDbFilterType {
    MANUFACTURER,
    CATEGORY,
    PROTOCOL
}

private val REMOTE_DB_PROTOCOL_FILTER_OPTIONS = listOf(
    "DENON",
    "JVC",
    "KASEIKYO",
    "NEC",
    "NEC16",
    "NEC42",
    "NECEXT",
    "PIONEER",
    "RAW",
    "RCA",
    "RC5",
    "RC5X",
    "RC6",
    "SAMSUNG",
    "SAMSUNG32",
    "SAMSUNG36",
    "SIRC",
    "SIRC15",
    "SIRC20"
)

private enum class ControlSource { MY_REMOTES, REMOTE_DB, HISTORY }

private const val REMOTE_DB_RESULT_LIMIT = 150

// ── Search & utility helpers ────────────────────────────────────────────────

private fun estimateUniversalRemainingMs(
    processedCount: Int,
    totalCount: Int,
    startedAtMs: Long,
    fallbackPerCodeMs: Long
): Long {
    if (totalCount <= 0) return 0L
    val safeProcessed = processedCount.coerceAtLeast(0)
    val remaining = (totalCount - safeProcessed).coerceAtLeast(0)
    if (remaining == 0) return 0L
    if (safeProcessed <= 0 || startedAtMs <= 0L) {
        return remaining * fallbackPerCodeMs.coerceAtLeast(1L)
    }

    val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
    val averagePerCodeMs = elapsedMs.toDouble() / safeProcessed.toDouble()
    return (remaining * averagePerCodeMs).toLong().coerceAtLeast(0L)
}

private fun normalizeSearchText(value: String): String {
    return value
        .lowercase()
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun tokenizeSearchQuery(query: String): List<String> {
    val normalized = normalizeSearchText(query)
    if (normalized.isBlank()) return emptyList()
    return normalized.split(' ').filter { it.isNotBlank() }
}

private fun matchesRemoteDbQuery(profile: FlipperProfile, query: String): Boolean {
    val tokens = tokenizeSearchQuery(query)
    if (tokens.isEmpty()) return true

    val searchable = normalizeSearchText("${profile.name} ${prettyPath(profile.parentPath)}")
    return tokens.all { token -> searchable.contains(token) }
}

private fun remoteDbPathSegments(parentPath: String): List<String> {
    val normalized = parentPath
        .removePrefix("${dbRootPath()}/")
        .removePrefix(dbRootPath())
        .trim('/').trim()
    if (normalized.isBlank()) return emptyList()
    return normalized.split('/').filter { it.isNotBlank() }
}

private fun remoteDbCategory(profile: FlipperProfile): String {
    val first = remoteDbPathSegments(profile.parentPath).firstOrNull().orEmpty()
    return if (first.isBlank()) "Root" else prettyName(first)
}

private fun remoteDbManufacturer(profile: FlipperProfile): String {
    val segments = remoteDbPathSegments(profile.parentPath)
    val manufacturerSegment = when {
        segments.size >= 2 -> segments[1]
        segments.isNotEmpty() -> segments.last()
        else -> profile.parentPath.substringAfterLast('/').ifBlank { profile.name }
    }
    return prettyName(manufacturerSegment)
}

private fun extractProtocolFromCodePayload(code: String): String? {
    val match = Regex("""protocol\s*=\s*([^;\s]+)""", RegexOption.IGNORE_CASE).find(code)
    return match?.groupValues?.getOrNull(1)?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
}

private suspend fun loadProfileProtocolsSummary(context: android.content.Context, profilePath: String): Pair<Set<String>, List<String>> {
    val options = loadDbIrCodeOptions(context, profilePath)
    val counts = linkedMapOf<String, Int>()

    options.forEach { option ->
        val protocol = extractProtocolFromCodePayload(option.code)
            ?: if (option.code.contains("type=raw", ignoreCase = true)) "RAW" else null
        if (!protocol.isNullOrBlank()) {
            counts[protocol] = (counts[protocol] ?: 0) + 1
        }
    }

    val sorted = counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    val all = sorted.map { it.key }.toSet()
    val top3 = sorted.take(3).map { it.key }
    return all to top3
}

// ── Root composable with navigation ──────────────────────────────────────────

@Composable
fun IRSharkApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current

    var dbIndex by remember { mutableStateOf(FlipperDbIndex()) }
    var savedRemotes by remember { mutableStateOf(listOf<SavedRemote>()) }

    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }

    // Universal Remote state
    var universalPath by rememberSaveable { mutableStateOf(dbRootPath()) }
    var universalCommand by rememberSaveable { mutableStateOf<UniversalCommandItem?>(null) }
    var universalCodeStep by rememberSaveable { mutableIntStateOf(0) }
    var universalProcessedCount by rememberSaveable { mutableIntStateOf(0) }
    var universalStartedAtMs by remember { mutableLongStateOf(0L) }
    var universalIncludeUnsorted by rememberSaveable { mutableStateOf(false) }
    var universalAutoSend by rememberSaveable { mutableStateOf(false) }
    var universalIntervalMs by rememberSaveable { mutableStateOf(250f) }

    // ── Settings & persistent flags ──────────────────────────────────────────────
    var autoStopAtEnd by rememberSaveable { mutableStateOf(true) }
    var showTxLed by rememberSaveable { mutableStateOf(true) }
    var hapticFeedback by rememberSaveable { mutableStateOf(true) }
    var txModeRaw by rememberSaveable { mutableStateOf("AUTO") }
    var bridgeEndpoint by rememberSaveable { mutableStateOf("") }
    var preferDownloadedDb by rememberSaveable { mutableStateOf(false) }
    var downloadedDbTag by rememberSaveable { mutableStateOf<String?>(null) }
    var downloadedDbAvailable by remember { mutableStateOf(false) }
    var effectiveDbSourceLabel by remember { mutableStateOf("Default") }
    var irFinderLastTested by rememberSaveable { mutableStateOf<String?>(null) }

    // ── Loading & runtime state ───────────────────────────────────────────────────
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

    LaunchedEffect(universalIncludeUnsorted, universalPath) {
        if (!universalIncludeUnsorted && universalPath.startsWith("${dbRootPath()}/Other")) {
            universalPath = dbRootPath()
        }
    }

    // Macros
    var savedMacros by remember { mutableStateOf(listOf<SavedMacro>()) }
    var macrosLoaded by remember { mutableStateOf(false) }
    var editingMacroId by remember { mutableStateOf<String?>(null) }
    var macrosQuery by remember { mutableStateOf("") }
    var pendingDeleteMacroId by remember { mutableStateOf<String?>(null) }
    val macroEngine = remember { MacroEngine(context) }
    val macroState by macroEngine.state.collectAsState()
    LaunchedEffect(hapticFeedback) { macroEngine.hapticEnabled = hapticFeedback }
    LaunchedEffect(txModeRaw, bridgeEndpoint) {
        macroEngine.txModeRaw = txModeRaw
        macroEngine.bridgeEndpoint = bridgeEndpoint
    }

    // ── Utility functions ─────────────────────────────────────────────────────────
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

    // ── IR transmit & database reload ────────────────────────────────────────────
    fun emitTxPulse(durationMs: Long = 180L) {
        txPulseJob?.cancel()
        txPulseJob = scope.launch {
            txPulseActive = true
            delay(durationMs)
            txPulseActive = false
        }
    }

    fun reloadDbIndex() {
        scope.launch {
            dbLoaded = false
            dbLoadProgress = DbLoadProgress()
            dbIndex = loadFlipperDbIndex(context) { progress ->
                dbLoadProgress = progress
            }
            downloadedDbAvailable = isDownloadedDbAvailable(context)
            effectiveDbSourceLabel = if (resolveEffectiveDbSource(context).name == "DOWNLOADED") {
                "Downloaded"
            } else {
                "Default"
            }
            dbLoaded = true
        }
    }

    // ── Database ZIP import ──────────────────────────────────────────────────────
    val zipPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext com.m4r71n.irshark.data.FlipperDbUpdateResult(
                        success = false,
                        updated = false,
                        latestTag = null,
                        message = "Cannot open selected file"
                    )
                inputStream.use { importFlipperDatabaseFromZip(context, it) }
            }
            if (result.success) {
                downloadedDbTag = result.latestTag
                downloadedDbAvailable = isDownloadedDbAvailable(context)
                settingsDirty = true
                if (preferDownloadedDb) {
                    reloadDbIndex()
                } else {
                    effectiveDbSourceLabel = if (resolveEffectiveDbSource(context).name == "DOWNLOADED") {
                        "Downloaded"
                    } else {
                        "Default"
                    }
                }
            }
            toastController.show(result.message)
        }
    }

    // ── List search & filtering ─────────────────────────────────────────────────
    var myRemotesQuery by rememberSaveable { mutableStateOf("") }
    var remoteDbQuery by rememberSaveable { mutableStateOf("") }
    var remoteDbManufacturerFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var remoteDbCategoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var remoteDbProtocolFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var remoteDbShowFilterTypeDialog by remember { mutableStateOf(false) }
    var remoteDbFilterPickerType by remember { mutableStateOf<RemoteDbFilterType?>(null) }
    var remoteDbFilterSearchQuery by rememberSaveable { mutableStateOf("") }
    val remoteDbProtocolsByPath = remember { mutableStateMapOf<String, Set<String>>() }
    val remoteDbTopProtocolsByPath = remember { mutableStateMapOf<String, List<String>>() }

    val remoteDbCategoryOptions = remember(dbIndex.profiles) {
        dbIndex.profiles
            .map(::remoteDbCategory)
            .distinct()
            .sorted()
    }
    val remoteDbManufacturerOptions = remember(dbIndex.profiles) {
        dbIndex.profiles
            .map(::remoteDbManufacturer)
            .distinct()
            .sorted()
    }
    val discoveredProtocolOptions = remoteDbProtocolsByPath
        .values
        .flatten()
        .distinct()
        .sorted()
    val remoteDbProtocolOptions = (REMOTE_DB_PROTOCOL_FILTER_OPTIONS + discoveredProtocolOptions)
        .distinct()
        .sorted()
    
    // Remote DB filtering on background thread to avoid UI lag
    var remoteDbFilteredProfiles by remember { mutableStateOf<List<FlipperProfile>>(emptyList()) }
    var remoteDbMatchCount by remember { mutableStateOf(0) }
    LaunchedEffect(
        remoteDbQuery,
        dbIndex.profiles,
        remoteDbManufacturerFilter,
        remoteDbCategoryFilter,
        remoteDbProtocolFilter
    ) {
        val baseFiltered = withContext(Dispatchers.Default) {
            dbIndex.profiles.filter { profile ->
                matchesRemoteDbQuery(profile, remoteDbQuery) &&
                    (remoteDbCategoryFilter.isNullOrBlank() || remoteDbCategory(profile).equals(remoteDbCategoryFilter, ignoreCase = true)) &&
                    (remoteDbManufacturerFilter.isNullOrBlank() || remoteDbManufacturer(profile).equals(remoteDbManufacturerFilter, ignoreCase = true))
            }
        }

        val protocolFilter = remoteDbProtocolFilter?.takeIf { it.isNotBlank() }
        val filtered = if (protocolFilter == null) {
            baseFiltered
        } else {
            val protocolKey = protocolFilter.uppercase()

            // Immediately refresh UI so old results do not stay visible while indexing.
            remoteDbMatchCount = 0
            remoteDbFilteredProfiles = emptyList()

            val missing = baseFiltered.filter { it.path !in remoteDbProtocolsByPath }
            if (missing.isNotEmpty()) {
                val chunkSize = 96
                missing.chunked(chunkSize).forEach { chunk ->
                    val loaded = withContext(Dispatchers.IO) {
                        chunk.associate { profile ->
                            val (allProtocols, top3) = loadProfileProtocolsSummary(context, profile.path)
                            profile.path to (allProtocols to top3)
                        }
                    }
                    loaded.forEach { (path, value) ->
                        remoteDbProtocolsByPath[path] = value.first
                        remoteDbTopProtocolsByPath[path] = value.second
                    }

                    // Progressive refresh while metadata loads.
                    val partial = baseFiltered.filter { profile ->
                        remoteDbProtocolsByPath[profile.path]?.contains(protocolKey) == true
                    }
                    remoteDbMatchCount = partial.size
                    remoteDbFilteredProfiles = partial.take(REMOTE_DB_RESULT_LIMIT).toList()
                }
            }

            baseFiltered.filter { profile ->
                remoteDbProtocolsByPath[profile.path]?.contains(protocolKey) == true
            }
        }

        remoteDbMatchCount = filtered.size
        remoteDbFilteredProfiles = filtered.take(REMOTE_DB_RESULT_LIMIT).toList()
    }

    LaunchedEffect(remoteDbFilteredProfiles) {
        val missing = remoteDbFilteredProfiles
            .take(80)
            .filter { it.path !in remoteDbTopProtocolsByPath }
        if (missing.isEmpty()) return@LaunchedEffect

        val loaded = withContext(Dispatchers.IO) {
            missing.associate { profile ->
                val (allProtocols, top3) = loadProfileProtocolsSummary(context, profile.path)
                profile.path to (allProtocols to top3)
            }
        }
        loaded.forEach { (path, value) ->
            remoteDbProtocolsByPath[path] = value.first
            remoteDbTopProtocolsByPath[path] = value.second
        }
    }

    // ── Remote Control state ──────────────────────────────────────────────────────
    var controlProfilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var controlName by rememberSaveable { mutableStateOf<String?>(null) }
    var controlRemoteIndex by rememberSaveable { mutableIntStateOf(-1) }
    var controlButtons by remember { mutableStateOf(listOf<SavedRemoteButton>()) }
    var controlSource by rememberSaveable { mutableStateOf(ControlSource.REMOTE_DB) }
    var controlSelectedCommand by rememberSaveable { mutableStateOf<String?>(null) }
    var controlTxCount by rememberSaveable { mutableIntStateOf(0) }
    var controlRepeatSending by remember { mutableStateOf(false) }
    var controlReturnScreen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var controlHistoryEntry by remember { mutableStateOf<RemoteHistoryEntry?>(null) }
    var controlColumnCount by rememberSaveable { mutableIntStateOf(2) }
    var controlGroupByCategory by rememberSaveable { mutableStateOf(true) }
    var remoteHistory by remember { mutableStateOf(listOf<RemoteHistoryEntry>()) }
    var historyLoaded by remember { mutableStateOf(false) }

    // ── Remote editor state ───────────────────────────────────────────────────────
    var editingRemoteIndex by remember { mutableStateOf<Int?>(null) }
    var remoteEditorReturnScreen by remember { mutableStateOf(Screen.MY_REMOTES) }
    var editorRemoteName by remember { mutableStateOf("") }
    var editorRemoteButtons by remember { mutableStateOf(listOf<SavedRemoteButton>()) }
    var editorRemoteIconName by remember { mutableStateOf<String?>(null) }
    var editorRemoteColumnCount by remember { mutableIntStateOf(2) }
    var editorOriginalName by remember { mutableStateOf<String?>(null) }
    var editorOriginalButtons by remember { mutableStateOf(listOf<SavedRemoteButton>()) }
    var editorOriginalIconName by remember { mutableStateOf<String?>(null) }
    var editorOriginalColumnCount by remember { mutableIntStateOf(2) }
    var editingButtonIndex by remember { mutableStateOf(-1) }
    var editorButtonLabel by remember { mutableStateOf("") }
    var editorButtonCode by remember { mutableStateOf("") }
    var editorButtonOriginalLabel by remember { mutableStateOf("") }
    var editorButtonOriginalCode by remember { mutableStateOf("") }
    var editorButtonProfileSearch by remember { mutableStateOf("") }
    var editorButtonSelectedProfilePath by remember { mutableStateOf<String?>(null) }
    var editorButtonSelectedCodeIdx by remember { mutableIntStateOf(-1) }
    var editorButtonDbCodes by remember { mutableStateOf(listOf<com.m4r71n.irshark.data.DbIrCodeOption>()) }
    var editorButtonLoadingCodes by remember { mutableStateOf(false) }
    var editorButtonCodeError by remember { mutableStateOf<String?>(null) }
    var showEditorDiscardDialog by remember { mutableStateOf(false) }
    var editorDiscardTarget by remember { mutableStateOf<Screen?>(null) }
    var pendingDeleteRemoteIndex by remember { mutableStateOf<Int?>(null) }

    val editorFilteredProfiles = remember(editorButtonProfileSearch, dbIndex.profiles) {
        dbIndex.profiles.filter {
            editorButtonProfileSearch.isBlank() ||
                it.name.contains(editorButtonProfileSearch, ignoreCase = true) ||
                it.parentPath.contains(editorButtonProfileSearch, ignoreCase = true)
        }.take(40)
    }

    LaunchedEffect(screen, editorButtonSelectedProfilePath) {
        if (screen != Screen.REMOTE_BUTTON_EDITOR) return@LaunchedEffect
        val path = editorButtonSelectedProfilePath
        if (path.isNullOrBlank()) {
            editorButtonDbCodes = emptyList()
            editorButtonSelectedCodeIdx = -1
            return@LaunchedEffect
        }
        editorButtonLoadingCodes = true
        editorButtonDbCodes = loadDbIrCodeOptions(context, path)
        editorButtonSelectedCodeIdx = -1
        editorButtonLoadingCodes = false
    }

    // ── IR Finder state ───────────────────────────────────────────────────────────
    // State is lifted here so it survives recomposition and can be reset on navigation.
    var irFinderBreadcrumb by remember { mutableStateOf<String?>(null) }
    var irFinderOnBack by remember { mutableStateOf<(() -> Unit)?>(null) }
    var irFinderOnUndo by remember { mutableStateOf<(() -> Unit)?>(null) }
    var irFinderCategory by rememberSaveable { mutableStateOf("") }  // Preserve category/brand across navigation
    var irFinderBrand by rememberSaveable { mutableStateOf("") }
    var irFinderInTestButtons by rememberSaveable { mutableStateOf(false) }  // Track if we're in final step
    var irFinderSelectedButtonIdx by rememberSaveable { mutableIntStateOf(-1) }  // Persist selected button
    // Serialize finder buttons state for persistence across app lifecycle
    var irFinderButtonsSerialized by rememberSaveable { mutableStateOf("") }
    var showConfirmNavDialog by remember { mutableStateOf(false) }
    var confirmNavReason by remember { mutableStateOf("") }
    // Clear IR Finder nav state when navigating away
    LaunchedEffect(screen) {
        if (screen != Screen.IR_FINDER) {
            irFinderBreadcrumb = null
            irFinderOnBack = null
            irFinderOnUndo = null
        }
        if (screen != Screen.REMOTE_CONTROL) {
            controlRepeatSending = false
        }
    }

    // Blink TX LED whenever the macro engine transmits an IR command
    LaunchedEffect(macroEngine) {
        macroEngine.irTransmitEvent.collect { emitTxPulse() }
    }

    // ── Remote editor helpers ─────────────────────────────────────────────────────
    // Name deduplication, opening the editor, tracking dirty state, and starting
    // the per-button sub-editor.
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

    fun startRemoteEditor(remoteIndex: Int?, returnScreen: Screen) {
        val existing = remoteIndex?.let { savedRemotes.getOrNull(it) }
        editingRemoteIndex = remoteIndex
        remoteEditorReturnScreen = returnScreen
        editorRemoteName = existing?.name.orEmpty()
        editorRemoteButtons = existing?.buttons.orEmpty()
        editorRemoteIconName = existing?.iconName
        editorRemoteColumnCount = existing?.columnCount ?: 2
        editorOriginalName = existing?.name
        editorOriginalButtons = existing?.buttons.orEmpty()
        editorOriginalIconName = existing?.iconName
        editorOriginalColumnCount = existing?.columnCount ?: 2
        editingButtonIndex = -1
        screen = Screen.REMOTE_EDITOR
    }

    fun closeRemoteEditor() {
        editingRemoteIndex = null
        editingButtonIndex = -1
        screen = remoteEditorReturnScreen
    }

    fun isRemoteEditorDirty(): Boolean {
        return editorRemoteName != editorOriginalName.orEmpty() ||
            editorRemoteButtons != editorOriginalButtons ||
            editorRemoteIconName != editorOriginalIconName ||
            editorRemoteColumnCount != editorOriginalColumnCount
    }

    fun isButtonEditorDirty(): Boolean {
        return editorButtonLabel != editorButtonOriginalLabel || editorButtonCode != editorButtonOriginalCode
    }

    fun startButtonEditor(index: Int) {
        val defaultCode = "protocol=NEC; address=0x00FF; command=0x20DF"
        val existing = editorRemoteButtons.getOrNull(index)
        editingButtonIndex = index
        editorButtonLabel = existing?.label ?: "POWER"
        editorButtonCode = existing?.code ?: defaultCode
        editorButtonOriginalLabel = editorButtonLabel
        editorButtonOriginalCode = editorButtonCode
        editorButtonProfileSearch = ""
        editorButtonSelectedProfilePath = null
        editorButtonSelectedCodeIdx = -1
        editorButtonDbCodes = emptyList()
        editorButtonLoadingCodes = false
        editorButtonCodeError = null
        screen = Screen.REMOTE_BUTTON_EDITOR
    }

    // ── Remote editor actions ─────────────────────────────────────────────────────
    // Validation, saving button/remote changes, exit-with-dirty-check logic.
    fun validateEditorButtonCode(raw: String): String? {
        val s = raw.trim()
        if (s.isBlank()) return "Code cannot be empty"

        val rawPattern = Regex("""^-?\d+(\s+-?\d+){3,}$""")
        if (rawPattern.matches(s)) return null

        val pairs = s
            .split(';', '\n')
            .mapNotNull { segment ->
                val token = segment.trim()
                if (token.isEmpty()) return@mapNotNull null
                val idx = token.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                token.substring(0, idx).trim().lowercase() to token.substring(idx + 1).trim()
            }

        if (pairs.isEmpty()) return "Invalid IR code format. Use key=value pairs or raw integers."

        val fields = pairs.toMap()
        when (fields["type"]?.lowercase()) {
            "raw" -> if (fields["data"].isNullOrBlank()) return "RAW code must contain data=..."
            "parsed" -> if (fields["protocol"].isNullOrBlank()) return "Parsed code must contain protocol=..."
        }
        return null
    }

    fun saveButtonEditor() {
        val err = validateEditorButtonCode(editorButtonCode)
        if (err != null) {
            editorButtonCodeError = err
            return
        }
        val updatedButton = SavedRemoteButton(
            label = editorButtonLabel.trim(),
            code = editorButtonCode.trim()
        )
        editorRemoteButtons = if (editingButtonIndex in editorRemoteButtons.indices) {
            editorRemoteButtons.toMutableList().also { it[editingButtonIndex] = updatedButton }
        } else {
            editorRemoteButtons + updatedButton
        }
        editingButtonIndex = -1
        screen = Screen.REMOTE_EDITOR
    }

    fun performEditorExit(target: Screen) {
        when (screen) {
            Screen.REMOTE_EDITOR -> {
                if (target == Screen.HOME) {
                    editingRemoteIndex = null
                    editingButtonIndex = -1
                    screen = Screen.HOME
                } else {
                    closeRemoteEditor()
                }
            }
            Screen.REMOTE_BUTTON_EDITOR -> {
                if (target == Screen.HOME) {
                    editingRemoteIndex = null
                    editingButtonIndex = -1
                    screen = Screen.HOME
                } else {
                    editingButtonIndex = -1
                    screen = Screen.REMOTE_EDITOR
                }
            }
            else -> {
                screen = target
            }
        }
    }

    fun requestEditorExit(target: Screen) {
        val dirty = when (screen) {
            Screen.REMOTE_EDITOR -> isRemoteEditorDirty()
            Screen.REMOTE_BUTTON_EDITOR -> isButtonEditorDirty()
            else -> false
        }
        if (dirty) {
            editorDiscardTarget = target
            showEditorDiscardDialog = true
        } else {
            performEditorExit(target)
        }
    }

    fun saveRemoteEditor() {
        val normalizedName = editorRemoteName.trim()
        val duplicateName = normalizedName.isNotBlank() &&
            savedRemotes.withIndex().any { (idx, remote) ->
                idx != editingRemoteIndex && remote.name.equals(normalizedName, ignoreCase = true)
            }
        if (normalizedName.isBlank() || duplicateName || editorRemoteButtons.isEmpty()) {
            return
        }

        val normalizedButtons = editorRemoteButtons.map {
            it.copy(label = it.label.trim(), code = it.code.trim())
        }.filter { it.label.isNotBlank() }

        val resolvedName = uniqueRemoteName(normalizedName, excludeIndex = editingRemoteIndex)

        if (editingRemoteIndex != null && editingRemoteIndex in savedRemotes.indices) {
            val previous = savedRemotes[editingRemoteIndex!!]
            val changed = previous.name != resolvedName || previous.buttons != normalizedButtons
            val detachFromDb = previous.sourceProfilePath != null && changed

            val updated = previous.copy(
                name = resolvedName,
                profilePath = if (detachFromDb) "" else previous.profilePath,
                commands = normalizedButtons.map { it.label },
                buttons = normalizedButtons,
                iconName = editorRemoteIconName,
                sourceProfilePath = if (detachFromDb) null else previous.sourceProfilePath,
                columnCount = editorRemoteColumnCount
            )

            savedRemotes = savedRemotes.toMutableList().also { it[editingRemoteIndex!!] = updated }

            if (controlSource == ControlSource.MY_REMOTES && controlRemoteIndex == editingRemoteIndex) {
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
                iconName = editorRemoteIconName,
                sourceProfilePath = null,
                columnCount = editorRemoteColumnCount
            )
            toastController.show("Remote created")
        }

        closeRemoteEditor()
    }

    // ── Remote control helpers ────────────────────────────────────────────────────
    // Code hydration from DB, history recording, and opening a remote control screen
    // from various sources (My Remotes, Remote DB, history).
    fun hydrateMissingCodesFromDb(
        buttons: List<SavedRemoteButton>,
        dbCodes: List<com.m4r71n.irshark.data.DbIrCodeOption>
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

    fun rememberRemoteOpen(
        name: String,
        profilePath: String,
        sourceProfilePath: String?,
        iconName: String?,
        snapshotButtons: List<SavedRemoteButton>
    ) {
        remoteHistory = recordRemoteHistory(
            history = remoteHistory,
            entry = RemoteHistoryEntry(
                name = name,
                profilePath = profilePath,
                sourceProfilePath = sourceProfilePath,
                iconName = iconName,
                openedAtEpochMs = System.currentTimeMillis(),
                buttons = snapshotButtons
            )
        )
    }

    fun openRemoteControl(
        name: String,
        profilePath: String,
        buttons: List<SavedRemoteButton>,
        source: ControlSource,
        returnScreen: Screen,
        remoteIndex: Int = -1,
        iconName: String? = null,
        sourceProfilePath: String? = null,
        historySnapshotButtons: List<SavedRemoteButton> = emptyList()
    ) {
        controlProfilePath = profilePath
        controlName = name
        controlButtons = buttons
        controlRemoteIndex = remoteIndex
        controlSource = source
        controlSelectedCommand = null
        controlTxCount = 0
        controlReturnScreen = returnScreen
        controlHistoryEntry = if (source == ControlSource.HISTORY) {
            RemoteHistoryEntry(
                name = name,
                profilePath = profilePath,
                sourceProfilePath = sourceProfilePath,
                iconName = iconName,
                openedAtEpochMs = System.currentTimeMillis(),
                buttons = historySnapshotButtons
            )
        } else {
            null
        }
        rememberRemoteOpen(
            name = name,
            profilePath = profilePath,
            sourceProfilePath = sourceProfilePath,
            iconName = iconName,
            snapshotButtons = historySnapshotButtons
        )
        screen = Screen.REMOTE_CONTROL
    }

    fun openDatabaseRemote(
        profile: FlipperProfile,
        source: ControlSource,
        returnScreen: Screen,
        historyName: String = profile.name,
        iconNameOverride: String? = null
    ) {
        val seededButtons = profile.commands.map { cmd ->
            SavedRemoteButton(label = cmd, code = "")
        }
        val iconName = iconNameOverride ?: categorySeedFromPath(profile.parentPath)
        openRemoteControl(
            name = historyName,
            profilePath = profile.path,
            buttons = seededButtons,
            source = source,
            returnScreen = returnScreen,
            iconName = iconName,
            sourceProfilePath = profile.path,
            historySnapshotButtons = emptyList()
        )

        scope.launch {
            val dbCodes = loadDbIrCodeOptions(context, profile.path)
            val hydrated = hydrateMissingCodesFromDb(seededButtons, dbCodes)
            if (controlProfilePath == profile.path && controlName == historyName) {
                controlButtons = hydrated
            }
        }
    }

    fun openHistoryRemote(entry: RemoteHistoryEntry) {
        val sourceProfilePath = entry.sourceProfilePath
        if (!sourceProfilePath.isNullOrBlank()) {
            val profile = dbIndex.profiles.firstOrNull { it.path == sourceProfilePath }
            if (profile != null) {
                openDatabaseRemote(
                    profile = profile,
                    source = ControlSource.HISTORY,
                    returnScreen = Screen.SETTINGS,
                    historyName = entry.name,
                    iconNameOverride = entry.iconName
                )
            }
        } else if (entry.buttons.isNotEmpty()) {
            openRemoteControl(
                name = entry.name,
                profilePath = entry.profilePath,
                buttons = entry.buttons,
                source = ControlSource.HISTORY,
                returnScreen = Screen.SETTINGS,
                iconName = entry.iconName,
                sourceProfilePath = null,
                historySnapshotButtons = entry.buttons
            )
        }
    }

    // ── Data loading ──────────────────────────────────────────────────────────────
    // Load all persisted data once on startup: settings, DB index, remotes, history, macros.
    LaunchedEffect(Unit) {
        val settings = loadAppSettings(context)
        universalIntervalMs = settings.globalIntervalMs
        autoStopAtEnd = settings.autoStopAtEnd
        showTxLed = settings.showTxLed
        hapticFeedback = settings.hapticFeedback
        txModeRaw = settings.txMode
        bridgeEndpoint = settings.bridgeEndpoint
        irFinderLastTested = settings.irFinderLastTested
        preferDownloadedDb = settings.preferDownloadedDb
        downloadedDbTag = settings.downloadedDbTag
        settingsLoaded = true

        dbIndex = loadFlipperDbIndex(context) { progress ->
            dbLoadProgress = progress
        }
        downloadedDbAvailable = isDownloadedDbAvailable(context)
        effectiveDbSourceLabel = if (resolveEffectiveDbSource(context).name == "DOWNLOADED") {
            "Downloaded"
        } else {
            "Default"
        }
        dbLoaded = true
        savedRemotes = loadSavedRemotes(context)
        remotesLoaded = true
        remoteHistory = loadRemoteHistory(context)
        historyLoaded = true
        savedMacros = loadSavedMacros(context)
        macrosLoaded = true
    }

    // ── Data persistence ──────────────────────────────────────────────────────────
    // Auto-save whenever corresponding state changes (guarded by the loaded flag
    // to avoid overwriting persisted data before it has been read).
    LaunchedEffect(savedRemotes) {
        if (!remotesLoaded) return@LaunchedEffect
        saveSavedRemotes(context, savedRemotes)
    }

    LaunchedEffect(remoteHistory) {
        if (!historyLoaded) return@LaunchedEffect
        saveRemoteHistory(context, remoteHistory)
    }

    LaunchedEffect(savedMacros) {
        if (!macrosLoaded) return@LaunchedEffect
        saveSavedMacros(context, savedMacros)
    }

    LaunchedEffect(settingsDirty, universalIntervalMs, autoStopAtEnd, showTxLed, hapticFeedback, txModeRaw, bridgeEndpoint, preferDownloadedDb, downloadedDbTag) {
        if (!settingsLoaded || !settingsDirty) return@LaunchedEffect
        saveAppSettings(
            context,
            com.m4r71n.irshark.data.AppSettings(
                globalIntervalMs = universalIntervalMs,
                autoStopAtEnd = autoStopAtEnd,
                showTxLed = showTxLed,
                hapticFeedback = hapticFeedback,
                txMode = txModeRaw,
                bridgeEndpoint = bridgeEndpoint,
                irFinderLastTested = irFinderLastTested,
                preferDownloadedDb = preferDownloadedDb,
                downloadedDbTag = downloadedDbTag
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

    // ── Universal auto-send ───────────────────────────────────────────────────────
    // Computes deduplicated payloads for the active command and drives the
    // timed auto-transmit loop when universalAutoSend is enabled.
    var universalUniquePayloads by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(universalCommand, universalPath, universalIncludeUnsorted) {
        val cmd = universalCommand
        if (cmd == null) {
            universalUniquePayloads = emptyList()
            return@LaunchedEffect
        }
        universalUniquePayloads = withContext(Dispatchers.IO) {
            getUniquePayloadsForCommand(
                context = context,
                dbIndex = dbIndex,
                folderPath = universalPath,
                command = cmd.actualCommand,
                includeConverted = universalIncludeUnsorted
            ).shuffled()
        }
    }
    val universalCoverage = universalUniquePayloads.size
    val universalEstimatedRemainingMs = estimateUniversalRemainingMs(
        processedCount = universalProcessedCount,
        totalCount = universalCoverage.takeIf { it > 0 } ?: (universalCommand?.profileCoverage ?: 0),
        startedAtMs = universalStartedAtMs,
        fallbackPerCodeMs = universalIntervalMs.roundToInt().toLong()
    )

    LaunchedEffect(universalAutoSend, universalCommand, universalCoverage, universalIntervalMs) {
        if (!universalAutoSend || universalCommand == null || universalCoverage <= 0) return@LaunchedEffect
        // Snapshot the deduplicated payload list so the loop is stable.
        val payloads = universalUniquePayloads
        if (payloads.isEmpty()) return@LaunchedEffect
        try {
            while (universalAutoSend) {
                // Transmit the unique IR payload for the current step (1-based index).
                val idx = (universalCodeStep - 1).coerceIn(0, payloads.size - 1)
                val payload = payloads[idx]
                val txResult = withContext(Dispatchers.IO) {
                    transmitIrCodeResult(context, payload, modeRaw = txModeRaw, bridgeEndpointRaw = bridgeEndpoint)
                }
                val transmitOk = txResult.success
                universalProcessedCount = (universalProcessedCount + 1).coerceAtMost(universalCoverage)
                if (txResult.status == IrTransmitStatus.NO_OUTPUT_AVAILABLE) {
                    toastController.show("No IR output found. Internal IR or live bridge not available.")
                }
                if (!transmitOk) {
                    if (universalCodeStep >= universalCoverage) {
                        if (autoStopAtEnd) {
                            universalAutoSend = false
                            universalCommand = null
                            universalCodeStep = 0
                            universalProcessedCount = 0
                            universalStartedAtMs = 0L
                            break
                        }
                        universalCodeStep = 1
                        universalProcessedCount = 0
                        universalStartedAtMs = System.currentTimeMillis()
                    } else {
                        universalCodeStep += 1
                    }
                    continue
                }
                txPulseActive = true
                delay((universalIntervalMs.roundToInt().toLong() / 2).coerceAtLeast(70L))
                txPulseActive = false
                delay((universalIntervalMs.roundToInt().toLong() / 2).coerceAtLeast(70L))
                if (universalCodeStep >= universalCoverage) {
                    if (autoStopAtEnd) {
                        universalAutoSend = false
                        universalCommand = null
                        universalCodeStep = 0
                        universalProcessedCount = 0
                        universalStartedAtMs = 0L
                        break
                    }
                    universalCodeStep = 1
                    universalProcessedCount = 0
                    universalStartedAtMs = System.currentTimeMillis()
                } else {
                    universalCodeStep += 1
                }
            }
        } finally {
            txPulseActive = false
        }
    }

    // ── Screen wake lock ───────────────────────────────────────────────────────────
    // Keep the display awake while universal auto-send is running.
    DisposableEffect(view, universalAutoSend) {
        view.keepScreenOn = universalAutoSend
        onDispose {
            view.keepScreenOn = false
        }
    }

    // ── Screen rendering ───────────────────────────────────────────────────────────
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
                Screen.HOME -> "IRShark"
                Screen.REMOTE_CONTROL -> "Remote"
                Screen.REMOTE_EDITOR -> "Remote Editor"
                Screen.REMOTE_BUTTON_EDITOR -> "Button Editor"
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
                txActive = txPulseActive || universalAutoSend || controlRepeatSending,
                showTxLed = showTxLed,
                fastBlink = universalAutoSend || controlRepeatSending,
                screenTitle = screenTitle
            )
            if (screen == Screen.REMOTE_CONTROL) {
                val profilePath = controlProfilePath.orEmpty()
                val currentProfile = dbIndex.profiles.firstOrNull { it.path == profilePath }
                val title = controlName ?: currentProfile?.name ?: "Remote"
                val activeSavedRemote = if (controlSource == ControlSource.MY_REMOTES && controlRemoteIndex in savedRemotes.indices) {
                    savedRemotes[controlRemoteIndex]
                } else {
                    null
                }
                val iconName = (activeSavedRemote?.sourceProfilePath
                    ?: currentProfile?.parentPath
                    ?: profilePath).let { path ->
                        activeSavedRemote?.iconName ?: categorySeedFromPath(path)
                    }
                RemoteControlNavBar(
                    title = title,
                    iconName = iconName,
                    onHome = { screen = Screen.HOME },
                    onBack = {
                        controlRemoteIndex = -1
                        screen = controlReturnScreen
                    }
                )
            }
            if (screen in listOf(Screen.MY_REMOTES, Screen.REMOTE_DB, Screen.SETTINGS, Screen.MACROS, Screen.IR_FINDER, Screen.REMOTE_EDITOR, Screen.REMOTE_BUTTON_EDITOR)) {
                val remoteDbShownCount = if (screen == Screen.REMOTE_DB) minOf(remoteDbMatchCount, REMOTE_DB_RESULT_LIMIT) else 0
                val remoteEditorCanSave = editorRemoteName.trim().isNotBlank() &&
                    editorRemoteButtons.isNotEmpty() &&
                    savedRemotes.withIndex().none { (idx, remote) ->
                        idx != editingRemoteIndex && remote.name.equals(editorRemoteName.trim(), ignoreCase = true)
                    }
                val buttonEditorCanSave = editorButtonLabel.trim().isNotBlank() && editorButtonCode.trim().isNotBlank()
                SectionNavBar(
                    onHome = {
                        if (screen == Screen.REMOTE_EDITOR || screen == Screen.REMOTE_BUTTON_EDITOR) {
                            requestEditorExit(Screen.HOME)
                        } else {
                            screen = Screen.HOME
                        }
                    },
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
                    searchResultCount = if (screen == Screen.REMOTE_DB) remoteDbShownCount else null,
                    searchTotalCount = if (screen == Screen.REMOTE_DB) remoteDbMatchCount else null,
                    actionsRound = screen == Screen.REMOTE_EDITOR || screen == Screen.REMOTE_BUTTON_EDITOR,
                    actions = when (screen) {
                        Screen.REMOTE_EDITOR -> listOf(
                            Icons.Filled.Close to { requestEditorExit(remoteEditorReturnScreen) },
                            Icons.Filled.Save to { if (remoteEditorCanSave) saveRemoteEditor() }
                        )
                        Screen.REMOTE_BUTTON_EDITOR -> listOf(
                            Icons.Filled.Close to { requestEditorExit(Screen.REMOTE_EDITOR) },
                            Icons.Filled.Save to { if (buttonEditorCanSave) saveButtonEditor() }
                        )
                        Screen.MY_REMOTES -> listOf(
                            Icons.Filled.Add to {
                                startRemoteEditor(remoteIndex = null, returnScreen = Screen.MY_REMOTES)
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

            // Confirmation dialog for navigating away from sensitive screens
            if (showConfirmNavDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmNavDialog = false },
                    title = { Text("Confirm") },
                    text = { Text(confirmNavReason) },
                    confirmButton = {
                        TextButton(onClick = {
                            showConfirmNavDialog = false
                            when {
                                irFinderInTestButtons -> {
                                    irFinderCategory = ""
                                    irFinderBrand = ""
                                    irFinderInTestButtons = false
                                }
                                screen == Screen.REMOTE_CONTROL -> {
                                    controlRemoteIndex = -1
                                }
                            }
                            screen = Screen.HOME
                        }) {
                            Text("Leave")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmNavDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showEditorDiscardDialog) {
                AlertDialog(
                    onDismissRequest = { showEditorDiscardDialog = false },
                    title = { Text("Discard changes?") },
                    text = { Text("You have unsaved changes. Leave without saving?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showEditorDiscardDialog = false
                            val target = editorDiscardTarget
                            editorDiscardTarget = null
                            if (target != null) performEditorExit(target)
                        }) {
                            Text("Leave")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showEditorDiscardDialog = false
                            editorDiscardTarget = null
                        }) {
                            Text("Stay")
                        }
                    }
                )
            }

            if (remoteDbShowFilterTypeDialog) {
                AlertDialog(
                    onDismissRequest = { remoteDbShowFilterTypeDialog = false },
                    title = { Text("Add filter") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                remoteDbFilterPickerType = RemoteDbFilterType.MANUFACTURER
                                remoteDbFilterSearchQuery = ""
                                remoteDbShowFilterTypeDialog = false
                            }) {
                                Text("Manufacturer")
                            }
                            TextButton(onClick = {
                                remoteDbFilterPickerType = RemoteDbFilterType.CATEGORY
                                remoteDbFilterSearchQuery = ""
                                remoteDbShowFilterTypeDialog = false
                            }) {
                                Text("Device category")
                            }
                            TextButton(onClick = {
                                remoteDbFilterPickerType = RemoteDbFilterType.PROTOCOL
                                remoteDbFilterSearchQuery = ""
                                remoteDbShowFilterTypeDialog = false
                            }) {
                                Text("Protocol")
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { remoteDbShowFilterTypeDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            remoteDbFilterPickerType?.let { filterType ->
                val options = when (filterType) {
                    RemoteDbFilterType.MANUFACTURER -> remoteDbManufacturerOptions
                    RemoteDbFilterType.CATEGORY -> remoteDbCategoryOptions
                    RemoteDbFilterType.PROTOCOL -> remoteDbProtocolOptions
                }
                val filteredOptions = options.filter {
                    remoteDbFilterSearchQuery.isBlank() ||
                        it.contains(remoteDbFilterSearchQuery, ignoreCase = true)
                }
                val currentValue = when (filterType) {
                    RemoteDbFilterType.MANUFACTURER -> remoteDbManufacturerFilter
                    RemoteDbFilterType.CATEGORY -> remoteDbCategoryFilter
                    RemoteDbFilterType.PROTOCOL -> remoteDbProtocolFilter
                }

                AlertDialog(
                    onDismissRequest = { remoteDbFilterPickerType = null },
                    title = {
                        Text(
                            when (filterType) {
                                RemoteDbFilterType.MANUFACTURER -> "Select manufacturer"
                                RemoteDbFilterType.CATEGORY -> "Select category"
                                RemoteDbFilterType.PROTOCOL -> "Select protocol"
                            }
                        )
                    },
                    text = {
                        val scroll = rememberScrollState()
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = remoteDbFilterSearchQuery,
                                onValueChange = { remoteDbFilterSearchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Search") }
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .verticalScroll(scroll),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                filteredOptions.forEach { option ->
                                    TextButton(
                                        onClick = {
                                            when (filterType) {
                                                RemoteDbFilterType.MANUFACTURER -> remoteDbManufacturerFilter = option
                                                RemoteDbFilterType.CATEGORY -> remoteDbCategoryFilter = option
                                                RemoteDbFilterType.PROTOCOL -> remoteDbProtocolFilter = option
                                            }
                                            remoteDbFilterSearchQuery = ""
                                            remoteDbFilterPickerType = null
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val marker = if (option == currentValue) "* " else ""
                                        Text("$marker$option")
                                    }
                                }
                                if (filteredOptions.isEmpty()) {
                                    Text("No matching options.")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            when (filterType) {
                                RemoteDbFilterType.MANUFACTURER -> remoteDbManufacturerFilter = null
                                RemoteDbFilterType.CATEGORY -> remoteDbCategoryFilter = null
                                RemoteDbFilterType.PROTOCOL -> remoteDbProtocolFilter = null
                            }
                            remoteDbFilterSearchQuery = ""
                            remoteDbFilterPickerType = null
                        }) {
                            Text("Clear")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            remoteDbFilterSearchQuery = ""
                            remoteDbFilterPickerType = null
                        }) {
                            Text("Close")
                        }
                    }
                )
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
                        val universalOtherPath = "${dbRootPath()}/Other"
                        UniversalRemoteScreen(
                            dbIndex = dbIndex,
                            currentPath = universalPath,
                            activeItem = universalCommand,
                            codeStep = universalProcessedCount,
                            activeCoverage = universalCoverage,
                            autoSend = universalAutoSend,
                            estimatedTimeRemainingMs = universalEstimatedRemainingMs,
                            includeUnsortedRemotes = universalIncludeUnsorted,
                            hapticEnabled = hapticFeedback,
                            onHome = {
                                universalAutoSend = false
                                universalProcessedCount = 0
                                universalStartedAtMs = 0L
                                screen = Screen.HOME
                            },
                            onBackPath = {
                                parentPath(universalPath)?.let {
                                    universalPath = it
                                    universalCommand = null
                                    universalCodeStep = 0
                                    universalProcessedCount = 0
                                    universalStartedAtMs = 0L
                                    universalAutoSend = false
                                }
                            },
                            onOpenFolder = { next ->
                                universalPath = next
                                universalCommand = null
                                universalCodeStep = 0
                                universalProcessedCount = 0
                                universalStartedAtMs = 0L
                                universalAutoSend = false
                            },
                            onIncludeUnsortedRemotesChange = { enabled ->
                                universalIncludeUnsorted = enabled
                                universalCommand = null
                                universalCodeStep = 0
                                universalProcessedCount = 0
                                universalStartedAtMs = 0L
                                universalAutoSend = false
                                if (!enabled && universalPath.startsWith("$universalOtherPath/")) {
                                    universalPath = dbRootPath()
                                }
                                if (!enabled && universalPath == universalOtherPath) {
                                    universalPath = dbRootPath()
                                }
                            },
                            onCommandClick = { item ->
                                // item.profileCoverage reflects the deduplicated unique-code count
                                // (set by the screen's async dedup LaunchedEffect).
                                if (item.profileCoverage <= 1) {
                                    universalCommand = null
                                    universalCodeStep = 0
                                    universalProcessedCount = 0
                                    universalStartedAtMs = 0L
                                    universalAutoSend = false
                                    emitTxPulse()
                                } else {
                                    universalCommand = item
                                    universalProcessedCount = 0
                                    universalStartedAtMs = System.currentTimeMillis()
                                    universalAutoSend = true
                                    universalCodeStep = 1
                                }
                            },
                            onToggleAutoSend = {
                                if (universalAutoSend) {
                                    universalAutoSend = false
                                    universalCommand = null
                                    universalCodeStep = 0
                                    universalProcessedCount = 0
                                    universalStartedAtMs = 0L
                                    txPulseActive = false
                                } else {
                                    universalProcessedCount = 0
                                    universalStartedAtMs = System.currentTimeMillis()
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
                                val remoteButtons = if (remote.buttons.isNotEmpty()) {
                                    remote.buttons
                                } else {
                                    remote.commands.map { SavedRemoteButton(label = it, code = "") }
                                }
                                openRemoteControl(
                                    name = remote.name,
                                    profilePath = remote.profilePath,
                                    buttons = remoteButtons,
                                    source = ControlSource.MY_REMOTES,
                                    returnScreen = Screen.MY_REMOTES,
                                    remoteIndex = originalIndex,
                                    iconName = remote.iconName ?: categorySeedFromPath(remote.sourceProfilePath ?: remote.profilePath),
                                    sourceProfilePath = remote.sourceProfilePath,
                                    historySnapshotButtons = if (remote.sourceProfilePath == null) remoteButtons else emptyList()
                                )

                                // Backfill missing DB codes for previously imported remotes.
                                if (remote.sourceProfilePath != null && remoteButtons.any { it.code.isBlank() }) {
                                    scope.launch {
                                        val dbCodes = loadDbIrCodeOptions(context, remote.sourceProfilePath)
                                        val hydrated = hydrateMissingCodesFromDb(remoteButtons, dbCodes)
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
                        val filtered = remoteDbFilteredProfiles
                        val activeFilters = listOfNotNull(
                            remoteDbManufacturerFilter?.let { "Manufacturer: $it" to { remoteDbManufacturerFilter = null } },
                            remoteDbCategoryFilter?.let { "Category: $it" to { remoteDbCategoryFilter = null } },
                            remoteDbProtocolFilter?.let { "Protocol: $it" to { remoteDbProtocolFilter = null } }
                        )

                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TextButton(onClick = { remoteDbShowFilterTypeDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.FilterList,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(" Add filter")
                                }
                                activeFilters.forEach { (label, clearFilter) ->
                                    TextButton(onClick = clearFilter) {
                                        Text("x $label")
                                    }
                                }
                            }

                            RemotesListScreen(
                                emptyText = "No matching remotes in database.",
                                items = filtered.map { profile ->
                                    val categoryLabel = prettyPathWithChevron(profile.parentPath)
                                    val topProtocols = remoteDbTopProtocolsByPath[profile.path].orEmpty()
                                    val protocolSummary = if (topProtocols.isEmpty()) {
                                        ""
                                    } else {
                                        " | ${topProtocols.joinToString(", ")}"
                                    }
                                    profile.name to "$categoryLabel$protocolSummary"
                                },
                                iconNameForItem = { idx ->
                                    categorySeedFromPath(filtered[idx].parentPath)
                                },
                                onOpen = { index ->
                                    val profile = filtered[index]
                                    openDatabaseRemote(
                                        profile = profile,
                                        source = ControlSource.REMOTE_DB,
                                        returnScreen = Screen.REMOTE_DB
                                    )
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
                        val historyEntry = if (controlSource == ControlSource.HISTORY) controlHistoryEntry else null
                        val effectiveSourceProfilePath = activeSavedRemote?.sourceProfilePath ?: historyEntry?.sourceProfilePath ?: profilePath.takeIf { currentProfile != null }

                        val typeBadge = when {
                            activeSavedRemote?.sourceProfilePath == null && controlSource == ControlSource.MY_REMOTES -> "Custom"
                            activeSavedRemote?.sourceProfilePath != null -> {
                                val parent = dbIndex.profiles
                                    .firstOrNull { it.path == activeSavedRemote.sourceProfilePath }
                                    ?.parentPath
                                if (parent.isNullOrBlank()) "From DB" else prettyPath(parent)
                            }
                            historyEntry?.sourceProfilePath != null -> {
                                if (currentProfile != null) prettyPath(currentProfile.parentPath) else "From DB"
                            }
                            historyEntry?.buttons?.isNotEmpty() == true -> "Custom"
                            currentProfile != null -> prettyPath(currentProfile.parentPath)
                            profilePath.isNotBlank() -> prettyPath(profilePath)
                            else -> "Custom"
                        }
                        val countBadge = "${commands.size} buttons"

                        val isRemoteAlreadyAdded = !effectiveSourceProfilePath.isNullOrBlank() && savedRemotes.any { it.sourceProfilePath == effectiveSourceProfilePath }
                        
                        RemoteControlScreen(
                            title = title,
                            deviceIconName = (activeSavedRemote?.sourceProfilePath
                                ?: historyEntry?.iconName
                                ?: currentProfile?.parentPath
                                ?: profilePath).let { path ->
                                    activeSavedRemote?.iconName ?: historyEntry?.iconName ?: categorySeedFromPath(path)
                                },
                            typeBadge = typeBadge,
                            countBadge = countBadge,
                            buttons = controlButtons,
                            selectedCommand = controlSelectedCommand,
                            txCount = controlTxCount,
                            hapticEnabled = hapticFeedback,
                            onBack = {
                                controlRepeatSending = false
                                controlRemoteIndex = -1
                                screen = controlReturnScreen
                            },
                            onCommandClick = { cmdLabel ->
                                controlRepeatSending = false
                                controlSelectedCommand = null
                                controlTxCount += 1
                                emitTxPulse()
                                // Find the IR code for this button and transmit
                                val button = controlButtons.firstOrNull { it.label.equals(cmdLabel, ignoreCase = true) }
                                if (button != null && button.code.isNotBlank()) {
                                    scope.launch(Dispatchers.IO) {
                                        val txResult = transmitIrCodeResult(
                                            context,
                                            button.code,
                                            modeRaw = txModeRaw,
                                            bridgeEndpointRaw = bridgeEndpoint
                                        )
                                        if (txResult.status == IrTransmitStatus.NO_OUTPUT_AVAILABLE) {
                                            withContext(Dispatchers.Main) {
                                                toastController.show("No IR output found. Internal IR or live bridge not available.")
                                            }
                                        }
                                    }
                                }
                            },
                            onRepeatCommandClick = { cmdLabel ->
                                controlSelectedCommand = null
                                controlTxCount += 1
                                val button = controlButtons.firstOrNull { it.label.equals(cmdLabel, ignoreCase = true) }
                                if (button != null && button.code.isNotBlank()) {
                                    scope.launch(Dispatchers.IO) {
                                        val txResult = transmitIrCodeResult(
                                            context,
                                            button.code,
                                            modeRaw = txModeRaw,
                                            bridgeEndpointRaw = bridgeEndpoint
                                        )
                                        if (txResult.status == IrTransmitStatus.NO_OUTPUT_AVAILABLE) {
                                            withContext(Dispatchers.Main) {
                                                toastController.show("No IR output found. Internal IR or live bridge not available.")
                                            }
                                        }
                                    }
                                }
                            },
                            onRepeatStateChange = { repeating ->
                                controlRepeatSending = repeating
                            },
                            onEdit = {
                                if (controlSource == ControlSource.MY_REMOTES && controlRemoteIndex in savedRemotes.indices) {
                                    startRemoteEditor(remoteIndex = controlRemoteIndex, returnScreen = Screen.REMOTE_CONTROL)
                                }
                            },
                            onSave = {
                                val addProfilePath = effectiveSourceProfilePath
                                if (!addProfilePath.isNullOrBlank() && savedRemotes.none { it.sourceProfilePath == addProfilePath }) {
                                    val resolvedName = uniqueRemoteName(title)
                                    savedRemotes = savedRemotes + SavedRemote(
                                        name = resolvedName,
                                        profilePath = addProfilePath,
                                        commands = commands,
                                        buttons = controlButtons,
                                        iconName = activeSavedRemote?.iconName ?: historyEntry?.iconName ?: categorySeedFromPath(currentProfile?.parentPath ?: addProfilePath),
                                        sourceProfilePath = addProfilePath,
                                        groupByCategory = controlGroupByCategory
                                    )
                                }
                            },
                            showSaveButton = controlSource != ControlSource.MY_REMOTES && !effectiveSourceProfilePath.isNullOrBlank(),
                            showEditButton = controlSource == ControlSource.MY_REMOTES,
                            saveButtonLabel = if (isRemoteAlreadyAdded) "Added" else "Add",
                            saveButtonEnabled = !isRemoteAlreadyAdded,
                            columnCount = activeSavedRemote?.columnCount ?: controlColumnCount,
                            groupByCategory = activeSavedRemote?.groupByCategory ?: controlGroupByCategory,
                            onColumnCountChange = { cols ->
                                if (controlSource == ControlSource.MY_REMOTES && controlRemoteIndex in savedRemotes.indices) {
                                    savedRemotes = savedRemotes.toMutableList().also { list ->
                                        list[controlRemoteIndex] = list[controlRemoteIndex].copy(columnCount = cols)
                                    }
                                } else {
                                    controlColumnCount = cols
                                }
                            },
                            onGroupByCategoryChange = { grouped ->
                                if (controlSource == ControlSource.MY_REMOTES && controlRemoteIndex in savedRemotes.indices) {
                                    savedRemotes = savedRemotes.toMutableList().also { list ->
                                        list[controlRemoteIndex] = list[controlRemoteIndex].copy(groupByCategory = grouped)
                                    }
                                } else {
                                    controlGroupByCategory = grouped
                                }
                            },
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

                    Screen.REMOTE_EDITOR -> {
                        val normalizedName = editorRemoteName.trim()
                        val duplicateName = normalizedName.isNotBlank() &&
                            savedRemotes.withIndex().any { (idx, remote) ->
                                idx != editingRemoteIndex && remote.name.equals(normalizedName, ignoreCase = true)
                            }
                        val canSaveRemote = normalizedName.isNotBlank() && !duplicateName && editorRemoteButtons.isNotEmpty()

                        RemoteEditorScreen(
                            remoteName = editorRemoteName,
                            iconName = editorRemoteIconName,
                            buttons = editorRemoteButtons,
                            columnCount = editorRemoteColumnCount,
                            duplicateName = duplicateName,
                            onNameChange = { editorRemoteName = it },
                            onIconChange = { editorRemoteIconName = it },
                            onColumnCountChange = { editorRemoteColumnCount = it },
                            onAddButton = {
                                startButtonEditor(-1)
                            },
                            onEditButton = { idx ->
                                startButtonEditor(idx)
                            },
                            onMoveButton = { from, to ->
                                if (from in editorRemoteButtons.indices && to in editorRemoteButtons.indices) {
                                    editorRemoteButtons = editorRemoteButtons.toMutableList().also { list ->
                                        val item = list.removeAt(from)
                                        list.add(to, item)
                                    }
                                }
                            },
                            onDeleteButton = { idx ->
                                if (idx in editorRemoteButtons.indices) {
                                    editorRemoteButtons = editorRemoteButtons.toMutableList().also { it.removeAt(idx) }
                                }
                            },
                            onBack = {
                                requestEditorExit(remoteEditorReturnScreen)
                            },
                            onSave = { saveRemoteEditor() },
                            canSave = canSaveRemote
                        )
                    }

                    Screen.REMOTE_BUTTON_EDITOR -> {
                        RemoteButtonEditorScreen(
                            buttonLabel = editorButtonLabel,
                            buttonCode = editorButtonCode,
                            profileSearch = editorButtonProfileSearch,
                            selectedProfilePath = editorButtonSelectedProfilePath,
                            selectedCodeIdx = editorButtonSelectedCodeIdx,
                            dbCodes = editorButtonDbCodes,
                            loadingCodes = editorButtonLoadingCodes,
                            filteredProfiles = editorFilteredProfiles,
                            codeError = editorButtonCodeError,
                            onLabelChange = {
                                editorButtonLabel = it
                            },
                            onCodeChange = {
                                editorButtonCode = it
                                editorButtonCodeError = null
                            },
                            onProfileSearchChange = {
                                editorButtonProfileSearch = it
                            },
                            onSelectProfile = {
                                editorButtonSelectedProfilePath = it
                            },
                            onSelectDbCode = { idx ->
                                val hit = editorButtonDbCodes.getOrNull(idx) ?: return@RemoteButtonEditorScreen
                                editorButtonSelectedCodeIdx = idx
                                editorButtonLabel = hit.label
                                editorButtonCode = hit.code
                                editorButtonCodeError = null
                            },
                            onBack = { requestEditorExit(Screen.REMOTE_EDITOR) },
                            onApply = { saveButtonEditor() },
                            canApply = editorButtonLabel.trim().isNotBlank() && editorButtonCode.trim().isNotBlank()
                        )
                    }

                    Screen.SETTINGS -> {
                        SettingsScreen(
                            intervalMs = universalIntervalMs,
                            autoStopAtEnd = autoStopAtEnd,
                            showTxLed = showTxLed,
                            hapticFeedback = hapticFeedback,
                            txModeRaw = txModeRaw,
                            bridgeEndpoint = bridgeEndpoint,
                            compatibilityReport = getIrCompatibilityReport(context, txModeRaw, bridgeEndpoint),
                            useDownloadedDb = preferDownloadedDb,
                            downloadedDbAvailable = downloadedDbAvailable,
                            bundledDbVersion = bundledDbVersionLabel(),
                            downloadedDbVersion = downloadedDbTag,
                            effectiveDbSourceLabel = effectiveDbSourceLabel,
                            historyEntries = remoteHistory,
                            onOpenHistoryItem = { openHistoryRemote(it) },
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
                            onTxModeChange = {
                                txModeRaw = it
                                settingsDirty = true
                            },
                            onBridgeEndpointChange = {
                                bridgeEndpoint = it
                                settingsDirty = true
                            },
                            onUseDefaultDb = {
                                preferDownloadedDb = false
                                settingsDirty = true
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        saveAppSettings(
                                            context,
                                            com.m4r71n.irshark.data.AppSettings(
                                                globalIntervalMs = universalIntervalMs,
                                                autoStopAtEnd = autoStopAtEnd,
                                                showTxLed = showTxLed,
                                                hapticFeedback = hapticFeedback,
                                                txMode = txModeRaw,
                                                bridgeEndpoint = bridgeEndpoint,
                                                irFinderLastTested = irFinderLastTested,
                                                preferDownloadedDb = false,
                                                downloadedDbTag = downloadedDbTag
                                            )
                                        )
                                    }
                                    reloadDbIndex()
                                }
                            },
                            onUseDownloadedDb = {
                                preferDownloadedDb = true
                                settingsDirty = true
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        saveAppSettings(
                                            context,
                                            com.m4r71n.irshark.data.AppSettings(
                                                globalIntervalMs = universalIntervalMs,
                                                autoStopAtEnd = autoStopAtEnd,
                                                showTxLed = showTxLed,
                                                hapticFeedback = hapticFeedback,
                                                txMode = txModeRaw,
                                                bridgeEndpoint = bridgeEndpoint,
                                                irFinderLastTested = irFinderLastTested,
                                                preferDownloadedDb = true,
                                                downloadedDbTag = downloadedDbTag
                                            )
                                        )
                                    }
                                    reloadDbIndex()
                                }
                            },
                            onImportDatabaseZip = {
                                zipPickerLauncher.launch(arrayOf("application/zip"))
                            },
                            onIntervalPresetSelect = {
                                universalIntervalMs = it
                                settingsDirty = true
                            },
                            onResetDefaults = {
                                universalIntervalMs = 150f
                                autoStopAtEnd = true
                                showTxLed = true
                                hapticFeedback = true
                                txModeRaw = "AUTO"
                                bridgeEndpoint = ""
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
                            },
                            onTransmit    = { emitTxPulse() },
                            txModeRaw = txModeRaw,
                            bridgeEndpoint = bridgeEndpoint
                        )
                    }

                    Screen.IR_FINDER -> {
                        IrFinderScreen(
                            dbIndex = dbIndex,
                            initialCategory = irFinderCategory,
                            initialBrand = irFinderBrand,
                            initialFinderButtonsState = irFinderButtonsSerialized,
                            initialSelectedButtonIdx = irFinderSelectedButtonIdx,
                            onTransmit = { emitTxPulse() },
                            addedProfilePaths = savedRemotes.mapNotNull { it.sourceProfilePath }.toSet(),
                            hapticEnabled = hapticFeedback,
                            txModeRaw = txModeRaw,
                            bridgeEndpoint = bridgeEndpoint,
                            lastTested = irFinderLastTested,
                            onUpdateLastTested = { tested ->
                                irFinderLastTested = tested
                                scope.launch(Dispatchers.IO) {
                                    saveAppSettings(
                                        context,
                                        com.m4r71n.irshark.data.AppSettings(
                                            globalIntervalMs = universalIntervalMs,
                                            autoStopAtEnd = autoStopAtEnd,
                                            showTxLed = showTxLed,
                                            hapticFeedback = hapticFeedback,
                                            txMode = txModeRaw,
                                            bridgeEndpoint = bridgeEndpoint,
                                            irFinderLastTested = tested,
                                            preferDownloadedDb = preferDownloadedDb,
                                            downloadedDbTag = downloadedDbTag
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
                            onOpenRemote = { profile ->
                                openDatabaseRemote(
                                    profile = profile,
                                    source = ControlSource.HISTORY,
                                    returnScreen = Screen.IR_FINDER
                                )
                            },
                            onNavStateChange = { breadcrumb, onBack, onUndo ->
                                irFinderBreadcrumb = breadcrumb
                                irFinderOnBack = onBack
                                irFinderOnUndo = onUndo
                            },
                            onStateChange = { category, brand, inTestButtons ->
                                irFinderCategory = category
                                irFinderBrand = brand
                                irFinderInTestButtons = inTestButtons
                            },
                            onFinderStateChange = { buttons, selectedIdx ->
                                // Serialize button state for persistence
                                irFinderButtonsSerialized = com.m4r71n.irshark.ui.screens.serializeFinderButtons(buttons)
                                irFinderSelectedButtonIdx = selectedIdx
                            },
                            onHome = {
                                if (irFinderInTestButtons) {
                                    showConfirmNavDialog = true
                                    confirmNavReason = "You're still testing buttons. Your progress will be lost. Leave anyway?"
                                } else {
                                    screen = Screen.HOME
                                }
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

        // ── Overlay dialogs ───────────────────────────────────────────────────────────
        // Delete confirmations shown as overlays on top of the current screen.
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
