package com.m4r71n.irshark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.view.HapticFeedbackConstants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m4r71n.irshark.data.SavedRemoteButton
import com.m4r71n.irshark.ui.components.Badge
import com.m4r71n.irshark.ui.components.CategorySvgIcon
import com.m4r71n.irshark.ui.components.RemoteCommandButton
import com.m4r71n.irshark.ir.extractProtocolFromPayload

private data class ButtonGroup(
    val category: String,
    val buttons: List<SavedRemoteButton>
)

private fun normalizeCommandToken(raw: String): String {
    return raw.trim()
        .lowercase()
        .replace(' ', '_')
        .replace('/', '_')
        .replace(Regex("_+"), "_")
        .trim('_')
}

private data class CategoryRule(
    val category: String,
    val matchers: List<(String) -> Boolean>
)

private fun groupButtonsByCategory(buttons: List<SavedRemoteButton>): List<ButtonGroup> {
    // Rules are aligned with Flipper IRDB naming guidance + .fff-ir-lint.json groups.
    val categoryRules = listOf(
        CategoryRule(
            category = "Power",
            matchers = listOf(
                { t -> Regex("^((power|pwr)_*)?(toggle)?$").matches(t) },
                { t -> Regex("^((power|pwr)_*)?((on_off)|(off_on)|toggle)$").matches(t) },
                { t -> Regex("^(turn_*)?((on_off)|(off_on))$").matches(t) },
                { t -> Regex("^((power|pwr)_*)?off$").matches(t) },
                { t -> Regex("^(turn_*)?off$").matches(t) },
                { t -> Regex("^((power|pwr)_*)?on$").matches(t) },
                { t -> Regex("^(turn_*)?on$").matches(t) },
                { t -> t == "standby" }
            )
        ),
        CategoryRule(
            category = "Volume",
            matchers = listOf(
                { t -> Regex("^vol(ume)?(_*(up|[\\^+]))?$").matches(t) },
                { t -> Regex("^vol(ume)?(_*(d(o?w)?n|[\\-]))?$").matches(t) },
                { t -> t == "mute" || t == "mte" || t.startsWith("mute") }
            )
        ),
        CategoryRule(
            category = "Channel",
            matchers = listOf(
                { t -> Regex("^ch(an(nel)?)?(_*(up|[\\^+]|next))?$").matches(t) },
                { t -> Regex("^ch(an(nel)?)?(_*(d(o?w)?n|[\\-]|prev(ious)?))?$").matches(t) },
                { t -> t == "ch_next" || t == "ch_prev" }
            )
        ),
        CategoryRule(
            category = "Navigation",
            matchers = listOf(
                { t -> t in setOf("ok", "enter", "up", "down", "left", "right", "back", "menu", "guide", "home", "exit") }
            )
        ),
        CategoryRule(
            category = "Playback",
            matchers = listOf(
                { t -> t in setOf("play", "pause", "play_pause", "stop", "next", "prev", "previous", "rewind", "forward", "sleep") }
            )
        ),
        CategoryRule(
            category = "Input",
            matchers = listOf(
                { t -> t.contains("input") || t.contains("source") || t.startsWith("hdmi") || t == "av" }
            )
        ),
        CategoryRule(
            category = "Climate",
            matchers = listOf(
                { t -> t in setOf("off", "dh", "cool_hi", "cool_lo", "heat_hi", "heat_lo") || t.startsWith("temp") || t.startsWith("fan") }
            )
        ),
        CategoryRule(
            category = "Lighting",
            matchers = listOf(
                { t -> t in setOf("power_off", "power_on", "brightness_up", "brightness_dn", "red", "green", "blue", "white", "yellow") },
                { t -> t.contains("brightness") || t in setOf("r", "g", "b", "w") }
            )
        ),
        CategoryRule(
            category = "Apps",
            matchers = listOf(
                { t -> t.contains("netflix") || t.contains("youtube") || t.contains("prime") || t.contains("app") }
            )
        ),
        CategoryRule(
            category = "Numbers",
            matchers = listOf(
                { t -> Regex("^(num_*)?[0-9]+$").matches(t) }
            )
        )
    )

    val grouped = mutableMapOf<String, MutableList<SavedRemoteButton>>()
    val uncategorized = mutableListOf<SavedRemoteButton>()

    buttons.forEach { btn ->
        val token = normalizeCommandToken(btn.label)
        var found = false
        for (rule in categoryRules) {
            if (rule.matchers.any { it(token) }) {
                grouped.getOrPut(rule.category) { mutableListOf() }.add(btn)
                found = true
                break
            }
        }
        if (!found) uncategorized.add(btn)
    }

    val preferredOrder = listOf(
        "Power",
        "Volume",
        "Channel",
        "Navigation",
        "Playback",
        "Input",
        "Climate",
        "Lighting",
        "Apps",
        "Numbers",
        "Other"
    )

    return (grouped.toList() + Pair("Other", uncategorized))
        .filter { it.second.isNotEmpty() }
        .sortedWith(
            compareBy<Pair<String, List<SavedRemoteButton>>>(
                { preferredOrder.indexOf(it.first).let { idx -> if (idx >= 0) idx else Int.MAX_VALUE } },
                { it.first }
            )
        )
        .map { (cat, btns) -> ButtonGroup(cat, btns) }
}

@Composable
fun RemoteControlScreen(
    title: String,
    deviceIconName: String?,
    typeBadge: String,
    countBadge: String,
    buttons: List<SavedRemoteButton>,
    selectedCommand: String?,
    txCount: Int,
    onBack: () -> Unit,
    onCommandClick: (String) -> Unit,
    onRepeatCommandClick: ((String) -> Unit)? = null,
    onRepeatStateChange: ((Boolean) -> Unit)? = null,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    showSaveButton: Boolean,
    showEditButton: Boolean,
    hapticEnabled: Boolean = true,
    columnCount: Int = 2,
    onColumnCountChange: (Int) -> Unit = {},
    groupByCategory: Boolean = true,
    onGroupByCategoryChange: (Boolean) -> Unit = {},
    onShare: (() -> Unit)? = null,
    saveButtonLabel: String = "Add",
    saveButtonEnabled: Boolean = true
) {
    val violet = MaterialTheme.colorScheme.primary
    var flashedCommand by remember { mutableStateOf<String?>(null) }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val buttonGroups = if (groupByCategory) groupButtonsByCategory(buttons) else listOf(ButtonGroup("All", buttons))

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F0D1A))
                .border(1.dp, violet.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val columnSelectorPillWidth = 78.dp
                val dualActionButtonWidth = 35.dp
                val saveActionWidth = columnSelectorPillWidth
                val editActionWidth = if (showEditButton && onShare != null) dualActionButtonWidth else columnSelectorPillWidth
                val shareActionWidth = if (showEditButton && onShare != null) dualActionButtonWidth else columnSelectorPillWidth
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Badge(
                            text = typeBadge,
                            marquee = true,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .widthIn(min = 0.dp)
                        )
                        Badge(text = countBadge)
                    }

                    if (showSaveButton) {
                        Box(
                            modifier = Modifier
                                .width(saveActionWidth)
                                .height(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (saveButtonEnabled) violet.copy(alpha = 0.14f) else Color(0xFF2A2540))
                                .border(1.dp, if (saveButtonEnabled) violet.copy(alpha = 0.35f) else Color(0xFF2A2540), RoundedCornerShape(8.dp))
                                .clickable(enabled = saveButtonEnabled, onClick = onSave),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = saveButtonLabel,
                                color = if (saveButtonEnabled) violet else Color(0xFF8A8899),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (showEditButton) {
                        Box(
                            modifier = Modifier
                                .width(editActionWidth)
                                .height(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(violet.copy(alpha = 0.14f))
                                .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .clickable(onClick = onEdit),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Edit", color = violet, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (onShare != null) {
                        Box(
                            modifier = Modifier
                                .width(shareActionWidth)
                                .height(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(violet.copy(alpha = 0.14f))
                                .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .clickable(onClick = onShare),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = "Share",
                                tint = violet,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Auto Sort",
                        color = Color(0xFFB7B3CC),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Switch(
                        checked = groupByCategory,
                        onCheckedChange = onGroupByCategoryChange,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(violet.copy(alpha = 0.14f))
                            .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(1 to Icons.Filled.ViewList, 2 to Icons.Filled.GridView, 3 to Icons.Filled.ViewModule).forEach { (cols, icon) ->
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (columnCount == cols) violet.copy(alpha = 0.24f) else Color.Transparent)
                                        .clickable { onColumnCountChange(cols) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = if (columnCount == cols) violet else Color(0xFF8A8899),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Command buttons grouped by category
            buttonGroups.forEach { group ->
                if (groupByCategory && group.category != "All") {
                    Text(
                        text = group.category,
                        color = violet.copy(alpha = 0.7f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .padding(start = 4.dp, bottom = 6.dp, top = 4.dp)
                    )
                }
                group.buttons.chunked(columnCount).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { btn ->
                            val cmd = btn.label
                            val isFlashed = flashedCommand == cmd
                            val protocol = (extractProtocolFromPayload(btn.code) ?: "").ifBlank { "" }
                            RemoteCommandButton(
                                label = cmd,
                                protocol = protocol,
                                isActive = isFlashed,
                                onClick = {
                                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    flashedCommand = cmd
                                    scope.launch { delay(220); flashedCommand = null }
                                    onCommandClick(cmd)
                                },
                                onLongPressRepeat = {
                                    flashedCommand = cmd
                                    (onRepeatCommandClick ?: onCommandClick)(cmd)
                                },
                                onLongPressRepeatStateChange = { repeating ->
                                    if (repeating) {
                                        flashedCommand = cmd
                                    } else if (flashedCommand == cmd) {
                                        flashedCommand = null
                                    }
                                    onRepeatStateChange?.invoke(repeating)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(columnCount - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (groupByCategory && group.category != "All") {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
