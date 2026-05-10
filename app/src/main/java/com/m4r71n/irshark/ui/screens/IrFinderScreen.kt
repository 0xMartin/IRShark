package com.m4r71n.irshark.ui.screens

import android.view.HapticFeedbackConstants
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
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
import com.m4r71n.irshark.data.FlipperDbIndex
import com.m4r71n.irshark.data.loadDbIrCodeOptions
import com.m4r71n.irshark.data.prettyName
import com.m4r71n.irshark.data.prettyPath
import com.m4r71n.irshark.data.profilesUnderPath
import com.m4r71n.irshark.ui.components.CategorySvgIcon
import com.m4r71n.irshark.util.transmitIrCode
import com.m4r71n.irshark.util.extractProtocolFromPayload
import com.m4r71n.irshark.util.IrTransmitStatus
import com.m4r71n.irshark.util.transmitIrCodeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun prettyButtonLabel(raw: String): String =
    raw.replace('_', ' ').split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

private fun normalizeFinderLabel(raw: String): String {
    return raw.uppercase().replace(Regex("[^A-Z0-9]+"), " ").trim()
}

private fun parsePayloadFieldsForFingerprint(payload: String): Map<String, String> {
    return payload
        .split(';')
        .mapNotNull { segment ->
            val idx = segment.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = segment.substring(0, idx).trim().lowercase()
            val value = segment.substring(idx + 1).trim()
            if (key.isBlank() || value.isBlank()) null else key to value
        }
        .toMap()
}

private fun normalizeHexLike(value: String): String {
    val v = value.trim().uppercase()
    val clean = if (v.startsWith("0X")) v.substring(2) else v
    val parsed = clean.toLongOrNull(16)
    return if (parsed != null) parsed.toString(16).uppercase() else clean
}

private fun irFingerprint(codePayload: String): String {
    val fields = parsePayloadFieldsForFingerprint(codePayload)
    if (fields.isEmpty()) {
        // Fallback for legacy raw payloads
        return codePayload.trim().replace(Regex("\\s+"), " ")
    }

    val type = fields["type"].orEmpty().lowercase()
    return when {
        type == "raw" || fields.containsKey("data") -> {
            val freq = fields["frequency"].orEmpty().toIntOrNull()?.toString().orEmpty()
            val data = fields["data"].orEmpty().trim().replace(Regex("\\s+"), " ")
            "raw|f=$freq|d=$data"
        }
        else -> {
            val protocol = fields["protocol"].orEmpty().uppercase()
            val address = normalizeHexLike(fields["address"].orEmpty())
            val command = normalizeHexLike(fields["command"].orEmpty())
            "parsed|p=$protocol|a=$address|c=$command"
        }
    }
}


// ── Data ─────────────────────────────────────────────────────────────────────

/** A button slot in the finder: carries its loaded code options and which one is confirmed. */
data class FinderButton(
    val id: String,
    val label: String,
    val code: String,
    val profilePath: String,
    val profileName: String,
    val profileParentPath: String,
    val frequency: Int,
    val isConfirmed: Boolean = false,
    val isIgnored: Boolean = false,
    val isRejected: Boolean = false,
    val wasSentButNotConfirmed: Boolean = false
) {
    val labelKey: String get() = normalizeFinderLabel(label).lowercase()
}

// ── Steps ─────────────────────────────────────────────────────────────────────

private sealed class FinderStep {
    object PickCategory : FinderStep()
    object PickBrand    : FinderStep()
    object TestButtons  : FinderStep()
}

private sealed class ChipItem {
    data class ConfirmedChip(val btn: FinderButton) : ChipItem()
    data class IgnoredChip(val labelKey: String, val rawLabel: String) : ChipItem()
}

// ── Helper functions for finder state persistence ──────────────────────────

internal fun serializeFinderButtons(buttons: List<FinderButton>): String {
    return buttons.mapIndexed { index, btn ->
        "$index;${if (btn.isConfirmed) 1 else 0};${if (btn.wasSentButNotConfirmed) 1 else 0};${if (btn.isRejected) 1 else 0};${if (btn.isIgnored) 1 else 0}"
    }.joinToString("|")
}

private data class FinderButtonPersistedState(
    val isConfirmed: Boolean,
    val wasSentButNotConfirmed: Boolean,
    val isRejected: Boolean,
    val isIgnored: Boolean
)

private fun decodeFinderButtonsState(serialized: String): Map<Int, FinderButtonPersistedState> {
    val stateMap = mutableMapOf<Int, FinderButtonPersistedState>()
    if (serialized.isBlank()) return stateMap

    serialized.split("|").forEach { part ->
        val segments = part.split(";")
        if (segments.size < 4) return@forEach
        val index = segments[0].toIntOrNull() ?: return@forEach
        stateMap[index] = FinderButtonPersistedState(
            isConfirmed = segments.getOrNull(1) == "1",
            wasSentButNotConfirmed = segments.getOrNull(2) == "1",
            isRejected = segments.getOrNull(3) == "1",
            isIgnored = segments.getOrNull(4) == "1"
        )
    }
    return stateMap
}

private fun deserializeFinderButtons(serialized: String, buttonsList: List<FinderButton>): List<FinderButton> {
    if (serialized.isBlank()) return buttonsList
    val stateByIndex = decodeFinderButtonsState(serialized)

    return buttonsList.mapIndexed { index, btn ->
        val state = stateByIndex[index]
        btn.copy(
            isConfirmed = state?.isConfirmed ?: false,
            wasSentButNotConfirmed = state?.wasSentButNotConfirmed ?: false,
            isRejected = state?.isRejected ?: false,
            isIgnored = state?.isIgnored ?: false
        )
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
    onOpenRemote: (com.m4r71n.irshark.data.FlipperProfile) -> Unit = {},
    onNavStateChange: (breadcrumb: String?, onBack: (() -> Unit)?, onUndo: (() -> Unit)?) -> Unit = { _, _, _ -> },
    onStateChange: (category: String, brand: String, inTestButtons: Boolean) -> Unit = { _, _, _ -> },
    onFinderStateChange: (buttons: List<FinderButton>, selectedIdx: Int) -> Unit = { _, _ -> },
    onHome: () -> Unit = {},
    lastTested: String? = null,
    onUpdateLastTested: (String) -> Unit = {},
    hapticEnabled: Boolean = true,
    txModeRaw: String = "AUTO",
    bridgeEndpoint: String = ""
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
    var selectedButtonId by remember { mutableStateOf<String?>(null) }
    var isLoadingCodes   by remember { mutableStateOf(false) }
    var pendingSendDialogButtonId by remember { mutableStateOf<String?>(null) }
    var deferredRejectedButtonIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var allowInitialFinderStateRestore by remember { mutableStateOf(true) }
    // Cache: profilePath → set of all IR code strings in that profile (for accurate matching)
    var profileCodesCache by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }

    fun applyDeferredRejectedButtons() {
        if (deferredRejectedButtonIds.isEmpty()) return
        finderButtons.forEachIndexed { idx, button ->
            if (button.id in deferredRejectedButtonIds && !button.isIgnored) {
                finderButtons[idx] = button.copy(isRejected = true, wasSentButNotConfirmed = false)
            }
        }
        deferredRejectedButtonIds = emptySet()
    }

    // Notify parent when finder buttons state changes (for persistence)
    // Use SideEffect to ensure state is saved on every recomposition, not just on step change
    SideEffect {
        if (step == FinderStep.TestButtons) {
            onFinderStateChange(finderButtons.toList(), selectedButtonIdx)
        }
    }

    // Matching profiles (narrowed as confirmations come in)
    val confirmedCodes = finderButtons.filter { it.isConfirmed }.map { it.code }.toSet()
    val confirmedLabelKeys = finderButtons.filter { it.isConfirmed }.map { it.labelKey }.toSet()
    val matchingProfiles = remember(confirmedCodes, selectedCategory, selectedBrand, profileCodesCache, dbIndex) {
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
                confirmedCodes.all { confirmedCode -> confirmedCode in codesInProfile }
            }
        }
    }
    val matchingProfilePaths = remember(matchingProfiles) { matchingProfiles.map { it.path }.toSet() }

    // Visible list: candidates from currently matching profiles, ordered by frequency.
    // Candidates already sent (but not confirmed) are deprioritized to the end.
    val visibleCandidateIndices = remember(finderButtons.toList(), matchingProfilePaths, confirmedCodes, confirmedLabelKeys) {
        finderButtons.indices
            .filter { idx ->
                val btn = finderButtons[idx]
                !btn.isConfirmed && !btn.isRejected && !btn.isIgnored &&
                    btn.labelKey !in confirmedLabelKeys &&
                    (matchingProfilePaths.isEmpty() || btn.profilePath in matchingProfilePaths)
            }
            .sortedWith(
                compareByDescending<Int> { idx -> finderButtons[idx].frequency }
                    .thenBy { idx -> finderButtons[idx].labelKey }
                    .thenBy { idx -> finderButtons[idx].profileName.lowercase() }
            )
    }

    fun buttonIdAtVisibleIndex(index: Int): String? {
        return visibleCandidateIndices.getOrNull(index)?.let { actualIdx -> finderButtons.getOrNull(actualIdx)?.id }
    }

    fun findNextUsableIndex(fromIndex: Int, step: Int, extraDeferredIds: Set<String> = emptySet()): Int {
        val blockedIds = deferredRejectedButtonIds + extraDeferredIds
        var cursor = fromIndex + step
        while (cursor in visibleCandidateIndices.indices) {
            val id = buttonIdAtVisibleIndex(cursor)
            if (id == null || id !in blockedIds) return cursor
            cursor += step
        }
        return -1
    }

    LaunchedEffect(visibleCandidateIndices, selectedButtonId, finderButtons.toList()) {
        if (visibleCandidateIndices.isEmpty()) {
            selectedButtonIdx = -1
            selectedButtonId = null
        } else {
            val selectedIdxById = selectedButtonId?.let { currentId ->
                visibleCandidateIndices.indexOfFirst { idx -> finderButtons.getOrNull(idx)?.id == currentId }
            } ?: -1
            when {
                selectedIdxById >= 0 -> selectedButtonIdx = selectedIdxById
                selectedButtonIdx in visibleCandidateIndices.indices -> {
                    selectedButtonId = finderButtons.getOrNull(visibleCandidateIndices[selectedButtonIdx])?.id
                }
                else -> {
                    selectedButtonIdx = 0
                    selectedButtonId = finderButtons.getOrNull(visibleCandidateIndices[0])?.id
                }
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
                    selectedButtonIdx = -1
                    selectedButtonId = null
                    pendingSendDialogButtonId = null
                    deferredRejectedButtonIds = emptySet()
                    allowInitialFinderStateRestore = false
                    onFinderStateChange(emptyList(), -1)
                },
                null
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
        finderButtons.clear()
        selectedButtonIdx = -1
        isLoadingCodes = true
        deferredRejectedButtonIds = emptySet()
        profileCodesCache = emptyMap()

        // Load all IR code options for all brands in selected brand folder (or category if no brand)
        scope.launch(Dispatchers.IO) {
            val basePath = if (selectedBrand.isNotEmpty())
                "flipper_irdb/$selectedCategory/$selectedBrand"
            else
                "flipper_irdb/$selectedCategory"

            val profiles = profilesUnderPath(dbIndex, basePath)
            val cacheBuilder = mutableMapOf<String, MutableSet<String>>()
            val rawCandidates = mutableListOf<FinderButton>()
            val labelFrequency = mutableMapOf<String, Int>()

            for (profile in profiles) {
                val opts = loadDbIrCodeOptions(context, profile.path)
                // Build the per-profile code cache (used for accurate matching)
                cacheBuilder[profile.path] = opts.mapTo(mutableSetOf()) { it.code }
                opts.forEachIndexed { idx, opt ->
                    val label = opt.label.trim()
                    if (label.isEmpty()) return@forEachIndexed
                    val labelKey = normalizeFinderLabel(label).lowercase()
                    if (labelKey.isEmpty()) return@forEachIndexed
                    labelFrequency[labelKey] = (labelFrequency[labelKey] ?: 0) + 1
                    rawCandidates.add(
                        FinderButton(
                            id = "${profile.path}#$idx#${irFingerprint(opt.code)}",
                            label = label,
                            code = opt.code,
                            profilePath = profile.path,
                            profileName = profile.name,
                            profileParentPath = profile.parentPath,
                            frequency = 0
                        )
                    )
                }
            }

            val rankedCandidates = rawCandidates
                .map { candidate ->
                    candidate.copy(frequency = labelFrequency[candidate.labelKey] ?: 0)
                }
                .sortedWith(
                    compareByDescending<FinderButton> { it.frequency }
                        .thenBy { it.labelKey }
                        .thenBy { it.profileName.lowercase() }
                )

            withContext(Dispatchers.Main) {
                finderButtons.clear()
                // Apply persisted state if available (confirmed codes and index)
                val restoredButtons = if (allowInitialFinderStateRestore && initialFinderButtonsState.isNotEmpty()) {
                    deserializeFinderButtons(initialFinderButtonsState, rankedCandidates)
                } else {
                    rankedCandidates
                }
                finderButtons.addAll(restoredButtons)
                profileCodesCache = cacheBuilder
                selectedButtonIdx = if (finderButtons.isNotEmpty()) 0 else -1
                selectedButtonId = finderButtons.firstOrNull { !it.isConfirmed }?.id
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

            FinderStep.TestButtons -> {
                TestButtonsStep(
                    allButtons = finderButtons,
                    visibleIndices = visibleCandidateIndices,
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
                    onSend = { idx ->
                        val actualIdx = visibleCandidateIndices.getOrNull(idx) ?: return@TestButtonsStep
                        val btn = finderButtons.getOrNull(actualIdx) ?: return@TestButtonsStep
                        selectedButtonId = btn.id
                        val code = btn.code
                        scope.launch(Dispatchers.IO) {
                            val txResult = transmitIrCodeResult(context, code, modeRaw = txModeRaw, bridgeEndpointRaw = bridgeEndpoint)
                            if (txResult.status == IrTransmitStatus.NO_OUTPUT_AVAILABLE) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "No IR output found. Internal IR or live bridge not available.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                        pendingSendDialogButtonId = btn.id
                        onTransmit()
                    },
                    onSendConfirmed = { buttonId ->
                        val actualIdx = finderButtons.indexOfFirst { it.id == buttonId }
                        val btn = finderButtons.getOrNull(actualIdx) ?: return@TestButtonsStep
                        scope.launch(Dispatchers.IO) {
                            val txResult = transmitIrCodeResult(context, btn.code, modeRaw = txModeRaw, bridgeEndpointRaw = bridgeEndpoint)
                            if (txResult.status == IrTransmitStatus.NO_OUTPUT_AVAILABLE) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "No IR output found. Internal IR or live bridge not available.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                        onTransmit()
                    },
                    onBack = {
                        if (visibleCandidateIndices.isEmpty()) return@TestButtonsStep
                        val previousIdx = findNextUsableIndex(selectedButtonIdx, -1)
                        if (previousIdx >= 0) {
                            selectedButtonIdx = previousIdx
                            selectedButtonId = buttonIdAtVisibleIndex(previousIdx)
                        }
                    },
                    onNext = {
                        if (visibleCandidateIndices.isEmpty()) return@TestButtonsStep
                        val nextIdx = findNextUsableIndex(selectedButtonIdx, 1)
                        if (nextIdx >= 0) {
                            selectedButtonIdx = nextIdx
                            selectedButtonId = buttonIdAtVisibleIndex(nextIdx)
                        }
                    },
                    onIgnoreCurrent = {
                        val selectedActualIdx = visibleCandidateIndices.getOrNull(selectedButtonIdx)
                            ?: return@TestButtonsStep
                        val selectedButton = finderButtons.getOrNull(selectedActualIdx)
                            ?: return@TestButtonsStep
                        val ignoredLabelKey = selectedButton.labelKey
                        finderButtons.forEachIndexed { idx, button ->
                            if (button.labelKey == ignoredLabelKey) {
                                finderButtons[idx] = button.copy(isIgnored = true, wasSentButNotConfirmed = false)
                            }
                        }
                        deferredRejectedButtonIds = deferredRejectedButtonIds.filterNot { rejectedId ->
                            finderButtons.firstOrNull { it.id == rejectedId }?.labelKey == ignoredLabelKey
                        }.toSet()
                        val nextIdx = findNextUsableIndex(selectedButtonIdx, 1)
                        if (nextIdx >= 0) {
                            selectedButtonIdx = nextIdx
                            selectedButtonId = buttonIdAtVisibleIndex(nextIdx)
                        } else {
                            selectedButtonIdx = -1
                            selectedButtonId = null
                        }
                    },
                    onUnignoreLabel = { labelKey ->
                        finderButtons.forEachIndexed { idx, button ->
                            if (button.labelKey == labelKey) {
                                finderButtons[idx] = button.copy(
                                    isIgnored = false,
                                    isRejected = false,
                                    wasSentButNotConfirmed = false
                                )
                            }
                        }
                        deferredRejectedButtonIds = deferredRejectedButtonIds.filterNot { rejectedId ->
                            finderButtons.firstOrNull { it.id == rejectedId }?.labelKey == labelKey
                        }.toSet()
                        if (selectedButtonIdx < 0) {
                            val nextIdx = findNextUsableIndex(-1, 1)
                            if (nextIdx >= 0) {
                                selectedButtonIdx = nextIdx
                                selectedButtonId = buttonIdAtVisibleIndex(nextIdx)
                            }
                        }
                    },
                    onRemoveConfirmed = { buttonId ->
                        val actualIdx = finderButtons.indexOfFirst { it.id == buttonId }
                        if (actualIdx >= 0) {
                            applyDeferredRejectedButtons()
                            finderButtons[actualIdx] = finderButtons[actualIdx].copy(isConfirmed = false)
                            selectedButtonId = null
                        }
                    },
                    onOpenRemote = onOpenRemote,
                    onAddRemote = { profile ->
                        onAddRemote(profile.path, profile.name, profile.commands)
                    },
                    onHome = onHome
                )

                val pendingDialogButton = pendingSendDialogButtonId?.let { pendingId ->
                    finderButtons.firstOrNull { it.id == pendingId }
                }
                if (pendingDialogButton != null) {
                    AlertDialog(
                        onDismissRequest = { pendingSendDialogButtonId = null },
                        title = {
                            Text(
                                text = prettyButtonLabel(pendingDialogButton.label),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text("Did this code work?")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    applyDeferredRejectedButtons()
                                    val actualIdx = finderButtons.indexOfFirst { it.id == pendingDialogButton.id }
                                    if (actualIdx >= 0) {
                                        finderButtons[actualIdx] = finderButtons[actualIdx]
                                            .copy(isConfirmed = true, wasSentButNotConfirmed = false)
                                        selectedButtonIdx = 0
                                        selectedButtonId = null
                                    }
                                    pendingSendDialogButtonId = null
                                }
                            ) {
                                Text("Works")
                            }
                        },
                        dismissButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val actualIdx = finderButtons.indexOfFirst { it.id == pendingDialogButton.id }
                                        val btn = finderButtons.getOrNull(actualIdx)
                                        if (btn != null) {
                                            scope.launch(Dispatchers.IO) {
                                                val txResult = transmitIrCodeResult(context, btn.code, modeRaw = txModeRaw, bridgeEndpointRaw = bridgeEndpoint)
                                                if (txResult.status == IrTransmitStatus.NO_OUTPUT_AVAILABLE) {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(
                                                            context,
                                                            "No IR output found. Internal IR or live bridge not available.",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            }
                                            onTransmit()
                                        }
                                    }
                                ) {
                                    Text("Resend")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val rejectedId = pendingDialogButton.id
                                        val nextIdx = findNextUsableIndex(
                                            fromIndex = selectedButtonIdx,
                                            step = 1,
                                            extraDeferredIds = setOf(rejectedId)
                                        )
                                        deferredRejectedButtonIds = deferredRejectedButtonIds + rejectedId
                                        if (nextIdx >= 0) {
                                            selectedButtonIdx = nextIdx
                                            selectedButtonId = buttonIdAtVisibleIndex(nextIdx)
                                        } else {
                                            selectedButtonIdx = -1
                                            selectedButtonId = null
                                        }
                                        pendingSendDialogButtonId = null
                                    }
                                ) {
                                    Text("No")
                                }
                            }
                        }
                    )
                }
            }
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
    allButtons: List<FinderButton>,
    visibleIndices: List<Int>,
    selectedIndex: Int,
    isLoading: Boolean,
    matchingCount: Int,
    totalCount: Int,
    categoryIconName: String,
    matchingProfiles: List<com.m4r71n.irshark.data.FlipperProfile>,
    addedProfilePaths: Set<String>,
    hapticEnabled: Boolean = true,
    onSend: (Int) -> Unit,
    onSendConfirmed: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onIgnoreCurrent: () -> Unit,
    onUnignoreLabel: (String) -> Unit,
    onRemoveConfirmed: (String) -> Unit,
    onOpenRemote: (com.m4r71n.irshark.data.FlipperProfile) -> Unit,
    onAddRemote: (com.m4r71n.irshark.data.FlipperProfile) -> Unit,
    onHome: (() -> Unit)? = null
) {
    val violet = MaterialTheme.colorScheme.primary
    val green  = Color(0xFF3EB47C)
    val visibleButtons = visibleIndices.mapNotNull { idx -> allButtons.getOrNull(idx) }
    val selectedBtn = visibleButtons.getOrNull(selectedIndex)
    val confirmedButtons = allButtons.filter { it.isConfirmed }
    val ignoredButtons = allButtons
        .filter { it.isIgnored }
        .distinctBy { it.labelKey }
        .map { it.labelKey to it.label }
    val view = LocalView.current
    var showAllChips by remember { mutableStateOf(false) }

    // Combine confirmed and ignored buttons for display
    val allChips = remember(confirmedButtons, ignoredButtons) {
        (confirmedButtons.map { ChipItem.ConfirmedChip(it) } + ignoredButtons.map { (labelKey, rawLabel) -> ChipItem.IgnoredChip(labelKey, rawLabel) })
    }
    val chipsToShow = if (showAllChips) allChips else allChips.take(6)
    val hasMoreChips = allChips.size > 6

    Column(modifier = Modifier.fillMaxSize()) {
        // Confirmed + ignored chips (expandable 3-row grid, default 9 items)
        if (allChips.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (showAllChips) (((allChips.size + 2) / 3).coerceAtMost(10) * 42).dp else 126.dp)
            ) {
                items(chipsToShow) { chip ->
                    when (chip) {
                        is ChipItem.ConfirmedChip -> {
                            val btn = chip.btn
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(green.copy(alpha = 0.16f))
                                    .border(1.dp, green.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        onSendConfirmed(btn.id)
                                    }
                                    .padding(start = 10.dp, end = 8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Send,
                                    contentDescription = null,
                                    tint = green,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = prettyButtonLabel(btn.label),
                                    color = green,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(green.copy(alpha = 0.25f))
                                        .clickable { onRemoveConfirmed(btn.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove",
                                        tint = green,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                        is ChipItem.IgnoredChip -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFFF8A65).copy(alpha = 0.16f))
                                    .border(1.dp, Color(0xFFFF8A65), RoundedCornerShape(10.dp))
                                    .padding(start = 10.dp, end = 8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFF8A65),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = prettyButtonLabel(chip.rawLabel),
                                    color = Color(0xFFFF8A65),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFFF8A65).copy(alpha = 0.25f))
                                        .clickable { onUnignoreLabel(chip.labelKey) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove",
                                        tint = Color(0xFFFF8A65),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // "View all" button if there are more chips
            if (hasMoreChips) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(violet.copy(alpha = 0.08f))
                        .border(1.dp, violet.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .clickable { showAllChips = !showAllChips }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (showAllChips) "Show less" else "Show all (${allChips.size})",
                        color = violet,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Send panel (visible only when a candidate is selected)
        if (selectedIndex >= 0 && selectedBtn != null) {
            if (isLoading) {
                Text("Loading codes…", color = Color(0xFF8A8899), fontSize = 13.sp)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF100D1C))
                        .border(1.dp, violet.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    val currentProtocol = extractProtocolFromPayload(selectedBtn.code).orEmpty()
                    
                    // Progress bar with position at top
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = {
                                if (visibleButtons.isNotEmpty()) {
                                    ((selectedIndex + 1).coerceIn(1, visibleButtons.size)).toFloat() / visibleButtons.size
                                } else 0f
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = violet,
                            trackColor = Color(0xFF2A2540),
                            strokeCap = StrokeCap.Round
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${(selectedIndex + 1).coerceAtLeast(1)} / ${visibleButtons.size}",
                            color = Color(0xFF8A8899),
                            fontSize = 11.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Top section: left info + right send button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = prettyButtonLabel(selectedBtn.label),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 24.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Text(
                                text = selectedBtn.profileName,
                                color = Color(0xFF8A8899),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                            if (currentProtocol.isNotEmpty()) {
                                Text(
                                    text = currentProtocol,
                                    color = Color(0xFFFFC14D),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        
                        // Send code button
                        Button(
                            onClick = {
                                if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onSend(selectedIndex)
                            },
                            modifier = Modifier
                                .size(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = violet),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Send", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Navigation row at bottom
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            enabled = selectedIndex > 0,
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, violet.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = violet),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text("Back", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = onNext,
                            enabled = selectedIndex < visibleButtons.lastIndex,
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, violet.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = violet),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text("Next", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = onIgnoreCurrent,
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF8A65)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A65)),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text("Ignore", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Matching devices section
        val confirmedCount = allButtons.count { it.isConfirmed }
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
