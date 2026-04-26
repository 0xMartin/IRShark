package com.vex.irshark.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Undo
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
import com.vex.irshark.util.transmitIrCode
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

// ── Main screen ──────────────────────────────────────────────────────────────

@Composable
fun IrFinderScreen(
    dbIndex: FlipperDbIndex,
    onTransmit: () -> Unit = {},
    onNavStateChange: (breadcrumb: String?, onBack: (() -> Unit)?, onUndo: (() -> Unit)?) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val violet  = MaterialTheme.colorScheme.primary

    // Wizard state
    var step            by remember { mutableStateOf<FinderStep>(FinderStep.PickCategory) }
    var selectedCategory by remember { mutableStateOf("") }
    var selectedBrand    by remember { mutableStateOf("") }

    // Finder button state — loaded for the chosen category+brand combination
    val finderButtons = remember { mutableStateListOf<FinderButton>() }
    var selectedButtonIdx by remember { mutableIntStateOf(-1) }
    var isLoadingCodes   by remember { mutableStateOf(false) }
    // Cache: profilePath → set of all IR code strings in that profile (for accurate matching)
    var profileCodesCache by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }

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
            FinderStep.PickCategory -> onNavStateChange(null, null, null)
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
        val buttons = defaultButtonsForCategory(selectedCategory)
        finderButtons.clear()
        finderButtons.addAll(buttons.map { FinderButton(label = it) })
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
            // For each button, gather all unique code options from all profiles
            val buttonMap = mutableMapOf<String, MutableList<DbIrCodeOption>>()
            val cacheBuilder = mutableMapOf<String, MutableSet<String>>()
            buttons.forEach { btn -> buttonMap[btn] = mutableListOf() }

            for (profile in profiles) {
                val opts = loadDbIrCodeOptions(context, profile.path)
                // Build the per-profile code cache (used for accurate matching)
                cacheBuilder[profile.path] = opts.mapTo(mutableSetOf()) { it.code }
                for (opt in opts) {
                    // Match to one of our button slots by name similarity
                    val matchKey = buttons.firstOrNull { btn ->
                        opt.label.replace('_', ' ').equals(btn.replace('_', ' '), ignoreCase = true) ||
                        opt.label.contains(btn.replace('_', ' '), ignoreCase = true) ||
                        btn.replace('_', ' ').contains(opt.label.replace('_', ' '), ignoreCase = true)
                    }
                    if (matchKey != null) {
                        if (buttonMap[matchKey]!!.none { it.code == opt.code }) {
                            buttonMap[matchKey]!!.add(opt)
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                val updated = finderButtons.mapIndexed { i, btn ->
                    btn.copy(codeOptions = buttonMap[btn.label].orEmpty())
                }
                finderButtons.clear()
                finderButtons.addAll(updated)
                profileCodesCache = cacheBuilder
                isLoadingCodes = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        when (step) {
            FinderStep.PickCategory -> PickStep(
                question = "What type of device?",
                options = categories.map { prettyName(it) to it },
                onSelect = { raw ->
                    selectedCategory = raw
                    step = FinderStep.PickBrand
                }
            )

            FinderStep.PickBrand -> PickStep(
                question = "What brand?",
                options = brandsForCategory.map { prettyName(it) to it },
                onSelect = { raw ->
                    selectedBrand = raw
                    step = FinderStep.TestButtons
                }
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
                matchingProfiles = matchingProfiles.map { it.name to prettyPath(it.parentPath) },
                onSelectButton = { idx -> selectedButtonIdx = idx },
                onSend = { idx ->
                    val btn = finderButtons.getOrNull(idx) ?: return@TestButtonsStep
                    val code = btn.currentCode ?: return@TestButtonsStep
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
                }
            )
        }
    }
}

// ── Pick step (category / brand) ─────────────────────────────────────────────

@Composable
private fun PickStep(
    question: String,
    options: List<Pair<String, String>>,   // display → raw
    onSelect: (String) -> Unit
) {
    val violet = MaterialTheme.colorScheme.primary
    var searchQuery by remember { mutableStateOf("") }
    val filtered = if (searchQuery.isBlank()) options
        else options.filter { (display, _) -> display.contains(searchQuery, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxWidth()) {
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
                .heightIn(max = 420.dp)
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
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = display,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
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
    matchingProfiles: List<Pair<String, String>>,
    onSelectButton: (Int) -> Unit,
    onSend: (Int) -> Unit,
    onWorks: (Int) -> Unit,
    onNextCode: (Int) -> Unit,
    onResetButton: (Int) -> Unit
) {
    val violet = MaterialTheme.colorScheme.primary
    val green  = Color(0xFF3EB47C)
    val selectedBtn = buttons.getOrNull(selectedIndex)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Test buttons",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Select a button, then send codes until one works. Tap ✓ Works when the device responds.",
            color = Color(0xFF8A8899),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Button grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height((buttons.size / 2 * 38 + 46).dp.coerceAtMost(192.dp))
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
                        .clickable { if (!btn.isConfirmed) onSelectButton(idx) },
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
                    // Progress text
                    Text(
                        text = "${prettyButtonLabel(selectedBtn.label)}  —  Code ${selectedBtn.progress} / ${selectedBtn.total}",
                        color = Color(0xFF8A8899),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
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
                            onClick = { onSend(selectedIndex) },
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
                        .height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(matchingProfiles) { (name, path) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0E0B1A))
                                .border(1.dp, Color(0xFF2A2540), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = path,
                                color = Color(0xFF8A8899),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
