package com.vex.irshark.ui.screens

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vex.irshark.data.DbIrCodeOption
import com.vex.irshark.data.FlipperDbIndex
import com.vex.irshark.data.loadDbIrCodeOptions
import com.vex.irshark.data.prettyName
import com.vex.irshark.data.prettyPath
import com.vex.irshark.data.profilesUnderPath
import com.vex.irshark.ui.components.CategorySvgIcon
import com.vex.irshark.util.transmitIrCode
import com.vex.irshark.util.extractProtocolFromPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Typical buttons per device category ──────────────────────────────────────

private val CATEGORY_BUTTONS = mapOf(
    "TVs" to listOf("Power", "Volume_Up", "Volume_Down", "Mute", "Channel_Up", "Channel_Down",
        "Input", "Menu", "Ok", "Back"),
    "ACs" to listOf("Power", "Mode", "Temp_Up", "Temp_Down", "Fan_Speed", "Sleep", "Timer"),
    "Projectors" to listOf("Power", "Volume_Up", "Volume_Down", "Mute", "Menu", "Ok", "Back",
        "Source", "Zoom_In", "Zoom_Out"),
    "Audio_and_Video_Receivers" to listOf("Power", "Volume_Up", "Volume_Down", "Mute", "Input",
        "Source", "Menu", "Ok", "Back"),
    "DVD_Players" to listOf("Power", "Play", "Pause", "Stop", "Prev", "Next", "Menu", "Ok", "Back"),
    "Fans" to listOf("Power", "Speed_Up", "Speed_Down", "Oscillation", "Timer", "Mode"),
    "Cameras" to listOf("Power", "Shutter", "Zoom_In", "Zoom_Out", "Menu", "Ok", "Back"),
    "Consoles" to listOf("Power", "Play", "Pause", "Stop", "Menu", "Ok", "Back"),
)

private fun defaultButtonsForCategory(category: String): List<String> {
    return CATEGORY_BUTTONS[category]
        ?: listOf("Power", "Volume_Up", "Volume_Down", "Mute", "Menu", "Ok", "Back")
}

private fun prettyButtonLabel(raw: String): String =
    raw.replace('_', ' ').split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

private fun normalizeFinderLabel(raw: String): String {
    return raw.uppercase().replace(Regex("[^A-Z0-9]+"), " ").trim()
}

private fun compactFinderLabel(raw: String): String = normalizeFinderLabel(raw).replace(" ", "")

// ── Common abbreviations / key parts for robust matching ──
private val COMMON_ABBREVIATIONS = mapOf(
    "VOL" to "VOLUME",
    "DN" to "DOWN",
    "UP" to "UP",
    "DOWN" to "DOWN",
    "CH" to "CHANNEL",
    "PROG" to "PROGRAM",
    "PRG" to "PROGRAM",
    "PRV" to "PREV",
    "PREVIOUS" to "PREV",
    "NXT" to "NEXT",
    "CUR" to "CURSOR",
    "NAV" to "NAV",
    "TMP" to "TEMP",
    "TEMP" to "TEMP",
    "FAN" to "FAN",
    "SPD" to "SPEED",
    "INC" to "UP",
    "DEC" to "DOWN",
    "PLUS" to "UP",
    "MINUS" to "DOWN",
    "FFWD" to "FASTFORWARD",
    "RWD" to "REWIND"
)

private fun expandAbbreviations(compact: String): Set<String> {
    // Expand recursively so labels like CHPREV/VOLDN are normalized in more than one step.
    val variations = mutableSetOf(compact)
    var changed = true
    var guard = 0
    while (changed && guard < 6) {
        changed = false
        guard++
        val snapshot = variations.toList()
        for (candidate in snapshot) {
            for ((abbr, expanded) in COMMON_ABBREVIATIONS) {
                if (candidate.contains(abbr)) {
                    val updated = candidate.replace(abbr, expanded)
                    if (variations.add(updated)) {
                        changed = true
                    }
                }
            }
        }
    }
    return variations
}

private fun detectDirectionalIntent(compact: String): String? {
    val upHints = listOf("UP", "NEXT", "MORE", "PLUS", "FORWARD", "HIGHER", "INC")
    val downHints = listOf("DOWN", "PREV", "LESS", "MINUS", "BACK", "LOWER", "DEC")

    val hasChannelContext = compact.contains("CHANNEL") || compact.contains("CH") ||
        compact.contains("PROGRAM") || compact.contains("PROG")
    if (hasChannelContext) {
        if (upHints.any { compact.contains(it) }) return "Channel_Up"
        if (downHints.any { compact.contains(it) }) return "Channel_Down"
    }

    val hasVolumeContext = compact.contains("VOLUME") || compact.contains("VOL") ||
        compact.contains("AUDIO") || compact.contains("SOUND")
    if (hasVolumeContext) {
        if (upHints.any { compact.contains(it) }) return "Volume_Up"
        if (downHints.any { compact.contains(it) }) return "Volume_Down"
    }

    val hasTempContext = compact.contains("TEMP") || compact.contains("TEMPERATURE") ||
        compact.contains("HEAT") || compact.contains("COOL")
    if (hasTempContext) {
        if (upHints.any { compact.contains(it) } || compact.contains("HOT")) return "Temp_Up"
        if (downHints.any { compact.contains(it) } || compact.contains("COLD")) return "Temp_Down"
    }

    val hasSpeedContext = compact.contains("SPEED") || compact.contains("FAN")
    if (hasSpeedContext) {
        if (upHints.any { compact.contains(it) }) return "Speed_Up"
        if (downHints.any { compact.contains(it) }) return "Speed_Down"
    }

    val hasMediaContext = compact.contains("TRACK") || compact.contains("CHAPTER") ||
        compact.contains("SONG") || compact.contains("SKIP")
    if (hasMediaContext) {
        if (compact.contains("NEXT") || compact.contains("FORWARD")) return "Next"
        if (compact.contains("PREV") || compact.contains("BACK") || compact.contains("REWIND")) return "Prev"
    }

    return null
}

private fun levenshteinDistance(s1: String, s2: String): Int {
    if (s1.length < s2.length) return levenshteinDistance(s2, s1)
    if (s2.isEmpty()) return s1.length
    
    val previous = IntArray(s2.length + 1) { it }
    val current = IntArray(s2.length + 1)
    
    for (i in 1..s1.length) {
        current[0] = i
        for (j in 1..s2.length) {
            val insertions = current[j - 1] + 1
            val deletions = previous[j] + 1
            val substitutions = previous[j - 1] + if (s1[i - 1] != s2[j - 1]) 1 else 0
            current[j] = minOf(insertions, deletions, substitutions)
        }
        previous.indices.forEach { previous[it] = current[it] }
    }
    return current[s2.length]
}

private fun similarityRatio(s1: String, s2: String): Double {
    val distance = levenshteinDistance(s1, s2)
    val maxLen = maxOf(s1.length, s2.length)
    if (maxLen == 0) return 1.0
    return 1.0 - (distance.toDouble() / maxLen)
}

private val FINDER_BUTTON_ALIASES = linkedMapOf(
    "Power" to setOf("POWER", "ONOFF", "POWERTOGGLE", "STANDBY", "ON", "OFF", "PWRBUTTON", "PWR", "POWERKEY", "KEYPOWER", "MAINPOWER", "SYSTEMPOWER"),
    "Volume_Up" to setOf("VOLUMEUP", "VOLUP", "VOLUMEPLUS", "VOLPLUS", "AUDIOUP", "SOUNDUP", "VOLMOREUP", "MOREVOL", "VOLU", "VOLINC", "VOLUMEINC", "VOLHIGHER", "VOLNEXT"),
    "Volume_Down" to setOf("VOLUMEDOWN", "VOLDOWN", "VOLUMEMINUS", "VOLMINUS", "AUDIODOWN", "SOUNDDOWN", "VOLMOREDOWN", "LESSVOL", "VOLDN", "VOLD", "VOLDEC", "VOLUMEDEC", "VOLLOWER", "VOLPREV"),
    "Mute" to setOf("MUTE", "VOLUMEMUTE", "AUDIOMUTE", "MUTEON", "MUTEOFF", "MUTEKEY", "SOUNDMUTE"),
    "Channel_Up" to setOf("CHANNELUP", "CHUP", "PROGRAMUP", "PROGUP", "CHMORE", "CHU", "CHNEXT", "CHANNELNEXT", "PROGRAMNEXT", "CHPLUS", "CHANNELPLUS", "PRGNEXT"),
    "Channel_Down" to setOf("CHANNELDOWN", "CHDOWN", "PROGRAMDOWN", "PROGDOWN", "CHLESS", "CHDN", "CHD", "CHPREV", "CHANNELPREV", "PROGRAMPREV", "CHMINUS", "CHANNELMINUS", "PRGPREV"),
    "Input" to setOf("INPUT", "TVAV", "AV", "HDMI", "INP", "INPUTSELECT", "INPUTSEL", "TVVIDEO", "VIDEOINPUT"),
    "Source" to setOf("SOURCE", "SRC", "SCART", "SOURCENEXT", "SOURCESELECT"),
    "Menu" to setOf("MENU", "SETUP", "SETTINGS", "OPTIONS", "HOME", "MAINMENU", "SYSTEMMENU", "OSD"),
    "Ok" to setOf("OK", "ENTER", "SELECT", "CONFIRM", "OKAY", "CENTRE", "CENTER"),
    "Back" to setOf("BACK", "RETURN", "EXIT", "ESCAPE", "ESC", "BKSP", "GOBACK"),
    "Up" to setOf("UP", "CURSORUP", "NAVUP", "PAGEUP", "ARROWUP"),
    "Down" to setOf("DOWN", "CURSORDOWN", "NAVDOWN", "PAGEDOWN", "ARROWDOWN"),
    "Left" to setOf("LEFT", "CURSORLEFT", "NAVLEFT", "PAGELEFT", "ARROWLEFT"),
    "Right" to setOf("RIGHT", "CURSORRIGHT", "NAVRIGHT", "PAGERIGHT", "ARROWRIGHT"),
    "Play" to setOf("PLAY", "PLAYSTART", "PLAYPAUSEPLAY", "START"),
    "Pause" to setOf("PAUSE", "PAUSED", "HOLD"),
    "Stop" to setOf("STOP", "STOPSTOPPED", "STOPPLAY"),
    "Prev" to setOf("PREV", "PREVIOUS", "BACKWARD", "REWIND", "SKIPBACK", "TRACKPREV", "SONGPREV", "CHAPTERPREV"),
    "Next" to setOf("NEXT", "FORWARD", "FASTFORWARD", "SKIPFORWARD", "FF", "TRACKNEXT", "SONGNEXT", "CHAPTERNEXT"),
    "Mode" to setOf("MODE", "FUNCTION", "FUNC"),
    "Temp_Up" to setOf("TEMPUP", "TEMPERATUREUP", "TEMPU", "TEMPPLUS", "TEMPINC", "HEATUP"),
    "Temp_Down" to setOf("TEMPDOWN", "TEMPERATUREDOWN", "TEMPD", "TEMPMINUS", "TEMPDEC", "COOLDOWN"),
    "Fan_Speed" to setOf("FANSPEED", "FAN", "SPEED", "FANSPEEDMORE", "FANMORE", "FSPEED", "FANLEVEL", "BLOWER", "AIRFLOW"),
    "Sleep" to setOf("SLEEP", "SLEEPTIMER", "NIGHT"),
    "Timer" to setOf("TIMER", "CLOCKTIMER", "PROGRAMTIMER"),
    "Speed_Up" to setOf("SPEEDUP", "SPDUP", "SPEEDINC", "SPEEDPLUS"),
    "Speed_Down" to setOf("SPEEDDOWN", "SPDDN", "SPEEDDEC", "SPEEDMINUS"),
    "Oscillation" to setOf("OSCILLATION", "SWING", "OSC", "SWINGMODE"),
    "Shutter" to setOf("SHUTTER", "SNAP", "CAPTURE", "TAKEPICTURE"),
    "Zoom_In" to setOf("ZOOMIN", "ZOOM", "TELE", "TELEPHOTO"),
    "Zoom_Out" to setOf("ZOOMOUT", "WIDE", "WIDEANGLE")
)

private fun canonicalFinderButtonLabel(rawLabel: String): String? {
    val compact = compactFinderLabel(rawLabel)
    if (compact.isBlank()) return null

    // Directional labels frequently encode intent in compact form (e.g. CHNEXT, VOLDN).
    detectDirectionalIntent(compact)?.let { return it }
    
    // Stage 1: Try exact match first
    FINDER_BUTTON_ALIASES.entries.firstOrNull { compact in it.value }?.let { return it.key }
    
    // Stage 2: Try abbreviation expansion
    val expanded = expandAbbreviations(compact)
    for (variation in expanded) {
        detectDirectionalIntent(variation)?.let { return it }
        FINDER_BUTTON_ALIASES.entries.firstOrNull { variation in it.value }?.let { return it.key }
    }
    
    // Stage 3: Try Levenshtein distance (fuzzy match) - highest similarity
    val bestMatch = FINDER_BUTTON_ALIASES.entries
        .mapNotNull { (canonical, aliases) ->
            val bestAliasSimilarity = aliases.maxOfOrNull { alias ->
                maxOf(
                    similarityRatio(compact, alias),
                    expanded.maxOfOrNull { variation -> similarityRatio(variation, alias) } ?: 0.0
                )
            } ?: 0.0
            if (bestAliasSimilarity > 0.0) canonical to bestAliasSimilarity else null
        }
        .maxByOrNull { it.second }
    
    // Return match only if similarity is >65% (allows "VOLDN" -> "VOLUMEDOWN", etc)
    if (bestMatch != null && bestMatch.second > 0.65) {
        return bestMatch.first
    }
    
    return null
}

private fun chooseFinderButtonsForCategory(
    category: String,
    commandFrequency: Map<String, Int>
): List<String> {
    val defaults = defaultButtonsForCategory(category)
    if (commandFrequency.isEmpty()) return defaults

    val defaultsPresent = defaults.filter { (commandFrequency[it] ?: 0) > 0 }
    val remaining = commandFrequency.entries
        .filter { it.key !in defaultsPresent }
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }

    val targetSize = defaults.size.coerceAtLeast(8)
    val selected = (defaultsPresent + remaining).distinct().take(targetSize)
    return if (selected.isNotEmpty()) selected else defaults
}

// ── Data ─────────────────────────────────────────────────────────────────────

/** A button slot in the finder: carries its loaded code options and which one is confirmed. */
data class FinderButton(
    val label: String,
    val codeOptions: List<DbIrCodeOption> = emptyList(),
    val confirmedCode: String? = null,   // not null = user confirmed this code works
    val codeIndex: Int = 0               // which code in codeOptions we're currently at
) {
    val isConfirmed: Boolean get() = confirmedCode != null
    val hasOptions: Boolean get() = codeOptions.isNotEmpty()
    val currentCode: String? get() = codeOptions.getOrNull(codeIndex)?.code
    val total: Int get() = codeOptions.size
    val progress: Int get() = if (total == 0) 0 else (codeIndex + 1).coerceAtMost(total)
}

// ── Steps ─────────────────────────────────────────────────────────────────────

private sealed class FinderStep {
    object PickCategory : FinderStep()
    object PickBrand    : FinderStep()
    object TestButtons  : FinderStep()
}

// ── Helper functions for finder state persistence ──────────────────────────

internal fun serializeFinderButtons(buttons: List<FinderButton>): String {
    return buttons.joinToString("|") { btn ->
        "${btn.label}:${btn.confirmedCode ?: "\u0000"}:${btn.codeIndex}"
    }
}

private fun deserializeFinderButtons(serialized: String, buttonsList: List<FinderButton>): List<FinderButton> {
    if (serialized.isBlank()) return buttonsList
    
    val stateMap = mutableMapOf<String, Pair<String?, Int>>()
    try {
        serialized.split("|").forEach { part ->
            val segments = part.split(":")
            if (segments.size >= 3) {
                val label = segments[0]
                val code = segments[1].takeIf { it != "\u0000" }
                val idx = segments[2].toIntOrNull() ?: 0
                if (label.isNotEmpty()) {
                    stateMap[label] = code to idx
                }
            }
        }
    } catch (e: Exception) {
        // Ignore parsing errors
    }
    
    return buttonsList.map { btn ->
        val (code, idx) = stateMap[btn.label] ?: (null to 0)
        btn.copy(confirmedCode = code, codeIndex = idx)
    }
}

// ── Main screen ──────────────────────────────────────────────────────────────

@Composable
fun IrFinderScreen(
    dbIndex: FlipperDbIndex,
    initialCategory: String = "",
    initialBrand: String = "",
    initialFinderButtons: List<FinderButton> = emptyList(),
    initialFinderButtonsState: String = "",
    initialSelectedButtonIdx: Int = -1,
    onTransmit: () -> Unit = {},
    addedProfilePaths: Set<String> = emptySet(),
    onAddRemote: (profilePath: String, profileName: String, commands: List<String>) -> Unit = { _, _, _ -> },
    onOpenRemote: (com.vex.irshark.data.FlipperProfile) -> Unit = {},
    onNavStateChange: (breadcrumb: String?, onBack: (() -> Unit)?, onUndo: (() -> Unit)?) -> Unit = { _, _, _ -> },
    onStateChange: (category: String, brand: String, inTestButtons: Boolean) -> Unit = { _, _, _ -> },
    onFinderStateChange: (buttons: List<FinderButton>, selectedIdx: Int) -> Unit = { _, _ -> },
    onHome: () -> Unit = {},
    lastTested: String? = null,
    onUpdateLastTested: (String) -> Unit = {},
    hapticEnabled: Boolean = true
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val violet  = MaterialTheme.colorScheme.primary

    // Wizard state
    var step            by remember { mutableStateOf<FinderStep>(FinderStep.PickCategory) }
    var selectedCategory by remember { mutableStateOf("") }
    var selectedBrand    by remember { mutableStateOf("") }

    // Initialize from caller-provided state if available
    LaunchedEffect(initialCategory, initialBrand) {
        if (initialCategory.isNotEmpty()) {
            selectedCategory = initialCategory
            if (initialBrand.isNotEmpty()) {
                selectedBrand = initialBrand
                step = FinderStep.TestButtons
            } else {
                step = FinderStep.PickBrand
            }
        }
    }

    // Notify parent when entering TestButtons so it can save lastTested
    LaunchedEffect(step, selectedBrand) {
        if (step == FinderStep.TestButtons && selectedBrand.isNotEmpty()) {
            onUpdateLastTested("${prettyName(selectedCategory)} › ${prettyName(selectedBrand)}")
        }
    }

    // Track state changes for parent (e.g., to know if we're in final testing step)
    LaunchedEffect(step, selectedCategory, selectedBrand) {
        onStateChange(selectedCategory, selectedBrand, step == FinderStep.TestButtons)
    }

    // Finder button state — loaded for the chosen category+brand combination
    val finderButtons = remember { 
        mutableStateListOf<FinderButton>().apply {
            if (initialFinderButtons.isNotEmpty()) addAll(initialFinderButtons)
        }
    }
    var selectedButtonIdx by remember { mutableIntStateOf(initialSelectedButtonIdx) }
    var isLoadingCodes   by remember { mutableStateOf(false) }
    // Cache: profilePath → set of all IR code strings in that profile (for accurate matching)
    var profileCodesCache by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }

    // Notify parent when finder buttons state changes (for persistence)
    // Use SideEffect to ensure state is saved on every recomposition, not just on step change
    SideEffect {
        if (step == FinderStep.TestButtons) {
            onFinderStateChange(finderButtons.toList(), selectedButtonIdx)
        }
    }

    // Matching profiles (narrowed as confirmations come in)
    val confirmedCodes = finderButtons.filter { it.isConfirmed }.associate { it.label to it.confirmedCode!! }
    val matchingProfiles = remember(confirmedCodes, selectedCategory, selectedBrand, profileCodesCache) {
        val basePath = if (selectedBrand.isNotEmpty())
            "flipper_irdb/$selectedCategory/$selectedBrand"
        else
            "flipper_irdb/$selectedCategory"
        val allProfiles = profilesUnderPath(dbIndex, basePath)
        if (confirmedCodes.isEmpty() || profileCodesCache.isEmpty()) {
            allProfiles
        } else {
            allProfiles.filter { profile ->
                val codesInProfile = profileCodesCache[profile.path] ?: return@filter false
                confirmedCodes.values.all { confirmedCode -> confirmedCode in codesInProfile }
            }
        }
    }

    // ── Notify parent of nav state on every recomposition (always-fresh lambdas) ──
    val lastConfirmedIdx = finderButtons.indexOfLast { it.isConfirmed }
    SideEffect {
        when (step) {
            FinderStep.PickCategory -> onNavStateChange("Root", null, null)
            FinderStep.PickBrand -> onNavStateChange(
                prettyName(selectedCategory),
                {
                    step = FinderStep.PickCategory
                    selectedCategory = ""
                },
                null
            )
            FinderStep.TestButtons -> onNavStateChange(
                "${prettyName(selectedCategory)} › $selectedBrand",
                {
                    step = FinderStep.PickBrand
                    selectedBrand = ""
                    finderButtons.clear()
                },
                if (lastConfirmedIdx >= 0) {
                    {
                        finderButtons[lastConfirmedIdx] = finderButtons[lastConfirmedIdx]
                            .copy(confirmedCode = null, codeIndex = 0)
                        selectedButtonIdx = lastConfirmedIdx
                    }
                } else null
            )
        }
    }

    // ── Category list from dbIndex ────────────────────────────────────────────
    val categories = remember(dbIndex) {
        dbIndex.folders["flipper_irdb"].orEmpty()
            .map { it.substringAfterLast('/') }
            .filter { !it.startsWith("_") }
            .sorted()
    }

    val brandsForCategory = remember(selectedCategory, dbIndex) {
        val folderPath = "flipper_irdb/$selectedCategory"
        dbIndex.folders[folderPath].orEmpty()
            .map { it.substringAfterLast('/') }
            .sorted()
    }

    // ── Load codes when brand is selected ─────────────────────────────────────
    LaunchedEffect(selectedBrand, selectedCategory) {
        if (selectedBrand.isEmpty() && selectedCategory.isEmpty()) return@LaunchedEffect
        val fallbackButtons = defaultButtonsForCategory(selectedCategory)
        finderButtons.clear()
        finderButtons.addAll(fallbackButtons.map { FinderButton(label = it) })
        selectedButtonIdx = -1
        isLoadingCodes = true
        profileCodesCache = emptyMap()

        // Load all IR code options for all brands in selected brand folder (or category if no brand)
        scope.launch(Dispatchers.IO) {
            val basePath = if (selectedBrand.isNotEmpty())
                "flipper_irdb/$selectedCategory/$selectedBrand"
            else
                "flipper_irdb/$selectedCategory"

            val profiles = profilesUnderPath(dbIndex, basePath)
            // For each canonical command button, gather all unique code options.
            val buttonMap = mutableMapOf<String, MutableList<DbIrCodeOption>>()
            val commandFrequency = mutableMapOf<String, Int>()
            val cacheBuilder = mutableMapOf<String, MutableSet<String>>()

            for (profile in profiles) {
                val opts = loadDbIrCodeOptions(context, profile.path)
                // Build the per-profile code cache (used for accurate matching)
                cacheBuilder[profile.path] = opts.mapTo(mutableSetOf()) { it.code }
                for (opt in opts) {
                    val canonical = canonicalFinderButtonLabel(opt.label) ?: continue
                    commandFrequency[canonical] = (commandFrequency[canonical] ?: 0) + 1
                    val bucket = buttonMap.getOrPut(canonical) { mutableListOf() }
                    if (bucket.none { it.code == opt.code }) {
                        bucket.add(opt)
                    }
                }
            }

            val selectedButtons = chooseFinderButtonsForCategory(selectedCategory, commandFrequency)

            withContext(Dispatchers.Main) {
                val updated = selectedButtons.map { label ->
                    FinderButton(label = label, codeOptions = buttonMap[label].orEmpty())
                }
                finderButtons.clear()
                // Apply persisted state if available (confirmed codes and index)
                val restoredButtons = if (initialFinderButtonsState.isNotEmpty()) {
                    deserializeFinderButtons(initialFinderButtonsState, updated)
                } else {
                    updated
                }
                finderButtons.addAll(restoredButtons)
                profileCodesCache = cacheBuilder
                isLoadingCodes = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when (step) {
        FinderStep.PickCategory -> Column(modifier = Modifier.fillMaxSize()) {
            PickStep(
                modifier = Modifier.weight(1f),
                question = "What type of device?",
                options = categories.map { prettyName(it) to it },
                showDeviceIcons = true,
                onSelect = { raw ->
                    selectedCategory = raw
                    step = FinderStep.PickBrand
                },
                onHome = onHome
            )
        }

            FinderStep.PickBrand -> PickStep(
                question = "What brand?",
                options = brandsForCategory.map { prettyName(it) to it },
                showDeviceIcons = true,
                iconNameOverride = selectedCategory,
                onSelect = { raw ->
                    selectedBrand = raw
                    step = FinderStep.TestButtons
                },
                onHome = onHome
            )

            FinderStep.TestButtons -> TestButtonsStep(
                buttons = finderButtons,
                selectedIndex = selectedButtonIdx,
                isLoading = isLoadingCodes,
                matchingCount = matchingProfiles.size,
                totalCount = if (selectedBrand.isNotEmpty()) {
                    profilesUnderPath(dbIndex, "flipper_irdb/$selectedCategory/$selectedBrand").size
                } else {
                    profilesUnderPath(dbIndex, "flipper_irdb/$selectedCategory").size
                },
                categoryIconName = selectedCategory,
                matchingProfiles = matchingProfiles,
                addedProfilePaths = addedProfilePaths,
                hapticEnabled = hapticEnabled,
                onSelectButton = { idx -> selectedButtonIdx = idx },
                onSend = { idx ->
                    val btn = finderButtons.getOrNull(idx) ?: return@TestButtonsStep
                    val code = btn.confirmedCode ?: btn.currentCode ?: return@TestButtonsStep
                    scope.launch(Dispatchers.IO) { transmitIrCode(context, code) }
                    onTransmit()
                },
                onWorks = { idx ->
                    val btn = finderButtons.getOrNull(idx) ?: return@TestButtonsStep
                    val code = btn.currentCode ?: return@TestButtonsStep
                    finderButtons[idx] = btn.copy(confirmedCode = code)

                    // Re-compute matching profiles with this new confirmation included,
                    // then trim codeOptions on all remaining buttons to codes that actually
                    // exist in at least one still-matching profile.
                    val newConfirmedCodes = finderButtons
                        .filter { it.isConfirmed }
                        .associate { it.label to it.confirmedCode!! }
                    val basePath = if (selectedBrand.isNotEmpty())
                        "flipper_irdb/$selectedCategory/$selectedBrand"
                    else
                        "flipper_irdb/$selectedCategory"
                    val nowMatching = profilesUnderPath(dbIndex, basePath).filter { profile ->
                        val codesInProfile = profileCodesCache[profile.path] ?: return@filter false
                        newConfirmedCodes.values.all { c -> c in codesInProfile }
                    }.map { it.path }.toSet()

                    // Build union of all codes available in still-matching profiles
                    val availableCodes: Set<String> = profileCodesCache
                        .filter { (path, _) -> path in nowMatching }
                        .values.flatten().toSet()

                    // Filter each non-confirmed button's options to only codes in availableCodes
                    finderButtons.forEachIndexed { i, b ->
                        if (!b.isConfirmed && b.codeOptions.isNotEmpty()) {
                            val filtered = b.codeOptions.filter { it.code in availableCodes }
                            if (filtered.size != b.codeOptions.size) {
                                finderButtons[i] = b.copy(codeOptions = filtered, codeIndex = 0)
                            }
                        }
                    }

                    // Advance to next unconfirmed button
                    val next = finderButtons.indexOfFirst { !it.isConfirmed }
                    selectedButtonIdx = if (next >= 0) next else -1
                },
                onNextCode = { idx ->
                    val btn = finderButtons.getOrNull(idx) ?: return@TestButtonsStep
                    if (btn.codeIndex + 1 < btn.total) {
                        finderButtons[idx] = btn.copy(codeIndex = btn.codeIndex + 1)
                    }
                },
                onResetButton = { idx ->
                    val btn = finderButtons.getOrNull(idx) ?: return@TestButtonsStep
                    finderButtons[idx] = btn.copy(confirmedCode = null, codeIndex = 0)
                },
                onOpenRemote = onOpenRemote,
                onAddRemote = { profile ->
                    onAddRemote(profile.path, profile.name, profile.commands)
                },
                onHome = onHome
            )
        }
    }
}

// ── Pick step (category / brand) ─────────────────────────────────────────────

@Composable
private fun PickStep(
    modifier: Modifier = Modifier.fillMaxSize(),
    question: String,
    options: List<Pair<String, String>>,
    showDeviceIcons: Boolean = false,
    iconNameOverride: String? = null,
    onSelect: (String) -> Unit,
    onHome: (() -> Unit)? = null
) {
    val violet = MaterialTheme.colorScheme.primary
    var searchQuery by remember { mutableStateOf("") }
    val filtered = if (searchQuery.isBlank()) options
        else options.filter { (display, _) -> display.contains(searchQuery, ignoreCase = true) }

    Column(modifier = modifier) {
        Text(
            text = question,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter…", color = Color(0xFF8A8899)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = violet,
                unfocusedBorderColor = Color(0xFF2A2540),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = violet
            )
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(filtered) { (display, raw) ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF100D1C))
                        .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .clickable { onSelect(raw) },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showDeviceIcons) {
                            val iconName = iconNameOverride ?: raw
                            CategorySvgIcon(name = iconName, tint = violet, size = 20.dp)
                        }
                        Text(
                            text = display,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            textAlign = if (showDeviceIcons) TextAlign.Start else TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        
        // Home button (visible only if provided)
        if (onHome != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, violet.copy(alpha = 0.3f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8A8899))
            ) {
                Text("Home", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ── Test buttons step ─────────────────────────────────────────────────────────

@Composable
private fun TestButtonsStep(
    buttons: List<FinderButton>,
    selectedIndex: Int,
    isLoading: Boolean,
    matchingCount: Int,
    totalCount: Int,
    categoryIconName: String,
    matchingProfiles: List<com.vex.irshark.data.FlipperProfile>,
    addedProfilePaths: Set<String>,
    hapticEnabled: Boolean = true,
    onSelectButton: (Int) -> Unit,
    onSend: (Int) -> Unit,
    onWorks: (Int) -> Unit,
    onNextCode: (Int) -> Unit,
    onResetButton: (Int) -> Unit,
    onOpenRemote: (com.vex.irshark.data.FlipperProfile) -> Unit,
    onAddRemote: (com.vex.irshark.data.FlipperProfile) -> Unit,
    onHome: (() -> Unit)? = null
) {
    val violet = MaterialTheme.colorScheme.primary
    val green  = Color(0xFF3EB47C)
    val selectedBtn = buttons.getOrNull(selectedIndex)
    val view = LocalView.current

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Test buttons",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        // Button grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height((buttons.size / 2 * 30 + 37).dp.coerceAtMost(166.dp))
        ) {
            items(buttons.size) { idx ->
                val btn = buttons[idx]
                val isSelected = idx == selectedIndex
                val borderColor = when {
                    btn.isConfirmed -> green
                    isSelected      -> violet
                    else            -> Color(0xFF2A2540)
                }
                val bgColor = when {
                    btn.isConfirmed -> green.copy(alpha = 0.12f)
                    isSelected      -> violet.copy(alpha = 0.15f)
                    else            -> Color(0xFF100D1C)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor)
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                        .clickable {
                            if (btn.isConfirmed) {
                                if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onSend(idx)
                            } else {
                                onSelectButton(idx)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (btn.isConfirmed) {
                            Icon(Icons.Filled.Check, contentDescription = null,
                                tint = green, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            prettyButtonLabel(btn.label),
                            color = if (btn.isConfirmed) green else Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Send panel (visible only when a button is selected)
        if (selectedIndex >= 0 && selectedBtn != null && !selectedBtn.isConfirmed) {
            if (isLoading) {
                Text("Loading codes…", color = Color(0xFF8A8899), fontSize = 13.sp)
            } else if (!selectedBtn.hasOptions) {
                Text(
                    "No codes found for ${prettyButtonLabel(selectedBtn.label)}.",
                    color = Color(0xFF8A8899), fontSize = 13.sp
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF100D1C))
                        .border(1.dp, violet.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    // Progress text with protocol
                    val currentCode = selectedBtn.currentCode
                    val currentProtocol = currentCode?.let { extractProtocolFromPayload(it) }.orEmpty()
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${prettyButtonLabel(selectedBtn.label)}  —  Code ${selectedBtn.progress} / ${selectedBtn.total}",
                            color = Color(0xFF8A8899),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        if (currentProtocol.isNotEmpty()) {
                            Text(
                                text = "Protocol: $currentProtocol",
                                color = Color(0xFFFFC14D),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                    LinearProgressIndicator(
                        progress = { if (selectedBtn.total > 0) selectedBtn.progress.toFloat() / selectedBtn.total else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = violet,
                        trackColor = Color(0xFF2A2540),
                        strokeCap = StrokeCap.Round
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Send code button
                        Button(
                            onClick = {
                                if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onSend(selectedIndex)
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = violet)
                        ) {
                            Text("Send code", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        // Works button
                        OutlinedButton(
                            onClick = { onWorks(selectedIndex) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, green),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = green)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Works!", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Next code / reset row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedBtn.codeIndex + 1 < selectedBtn.total) {
                            OutlinedButton(
                                onClick = { onNextCode(selectedIndex) },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, violet.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = violet)
                            ) {
                                Text("Try next code", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Text(
                                "No more codes",
                                color = Color(0xFF8A8899),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        IconButton(
                            onClick = { onResetButton(selectedIndex) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reset",
                                tint = Color(0xFF8A8899), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Matching devices section
        val confirmedCount = buttons.count { it.isConfirmed }
        if (confirmedCount > 0 || true) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Matching devices",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$matchingCount / $totalCount",
                    color = if (matchingCount < totalCount) violet else Color(0xFF8A8899),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (matchingProfiles.isEmpty()) {
                Text(
                    "No matches — try more buttons or different codes.",
                    color = Color(0xFF8A8899),
                    fontSize = 13.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(matchingProfiles) { profile ->
                        val isAdded = addedProfilePaths.contains(profile.path)
                        val iconName = categoryIconName.ifBlank { "Other" }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0E0B1A))
                                .border(1.dp, Color(0xFF2A2540), RoundedCornerShape(8.dp))
                                .clickable { onOpenRemote(profile) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(violet.copy(alpha = 0.12f))
                                    .border(1.dp, violet.copy(alpha = 0.30f), RoundedCornerShape(9.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                CategorySvgIcon(name = iconName, tint = violet, size = 18.dp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = prettyPath(profile.parentPath),
                                    color = Color(0xFF8A8899),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = { onAddRemote(profile) },
                                enabled = !isAdded,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, violet.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = violet,
                                    disabledContentColor = Color(0xFF8A8899)
                                ),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = if (isAdded) "Added" else "Add",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
        
    }
}
