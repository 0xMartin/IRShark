package com.vex.irshark.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.vex.irshark.data.FlipperDbIndex
import com.vex.irshark.data.UniversalCommandItem
import com.vex.irshark.data.dbRootPath
import com.vex.irshark.data.prettyName
import com.vex.irshark.data.prettyPathWithChevron
import com.vex.irshark.data.profilesUnderPath
import com.vex.irshark.data.resolveUniversalCommandsForPath
import com.vex.irshark.data.resolveUniversalCommandsWithDedup
import com.vex.irshark.ui.components.AutoSendProgressModal
import com.vex.irshark.ui.components.CategorySvgIcon
import com.vex.irshark.ui.components.EmptyCard
import com.vex.irshark.ui.components.FolderButton
import com.vex.irshark.ui.components.RemoteCommandButton
import com.vex.irshark.ui.components.UniversalRemoteHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class UniversalTab { Commands, Categories }

@Composable
fun UniversalRemoteScreen(
    dbIndex: FlipperDbIndex,
    currentPath: String,
    activeItem: UniversalCommandItem?,
    codeStep: Int,
    activeCoverage: Int,
    autoSend: Boolean,
    intervalMs: Float,
    hapticEnabled: Boolean = true,
    onHome: () -> Unit,
    onBackPath: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onCommandClick: (UniversalCommandItem) -> Unit,
    onToggleAutoSend: () -> Unit,
    onIntervalChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val root = dbRootPath()
    val folders = dbIndex.folders[currentPath].orEmpty().sortedBy { prettyName(it) }
    // Initially show raw profile counts; replaced by deduplicated counts once IO is done.
    val resolvedCommands = resolveUniversalCommandsForPath(dbIndex, currentPath)
    var dedupedCommands by remember(currentPath) { mutableStateOf<List<UniversalCommandItem>?>(null) }
    LaunchedEffect(currentPath) {
        dedupedCommands = withContext(Dispatchers.IO) {
            resolveUniversalCommandsWithDedup(context, dbIndex, currentPath)
        }
    }
    val displayCommands = dedupedCommands ?: resolvedCommands
    val isDedupLoading = dedupedCommands == null && resolvedCommands.isNotEmpty()
    val violet = MaterialTheme.colorScheme.primary

    var folderSearchQuery by remember { mutableStateOf("") }
    var flashedCommand by remember { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(UniversalTab.Categories) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val displayCoverage = when {
        activeCoverage > 0 -> activeCoverage
        activeItem != null -> activeItem.profileCoverage
        else -> 0
    }

    val estimatedTimeMs = if (autoSend && activeItem != null && displayCoverage > 0) {
        ((displayCoverage - codeStep.coerceAtLeast(1)) * intervalMs.roundToInt()).toLong()
    } else 0L
    
    val filteredFolders = if (folderSearchQuery.isBlank()) {
        folders
    } else {
        folders.filter { prettyName(it).contains(folderSearchQuery, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top header bar
            UniversalRemoteHeader(
                currentPath = prettyPathWithChevron(currentPath),
                onHome = {
                    if (autoSend) onToggleAutoSend()
                    onHome()
                },
                onBack = onBackPath,
                canGoBack = currentPath != root
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(UniversalTab.Commands to "Commands", UniversalTab.Categories to "Categories").forEach { (tab, label) ->
                        val selected = selectedTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Color(0xFF171327) else Color(0xFF100D1C))
                                .border(1.dp, if (selected) violet.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .clickable { selectedTab = tab },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    label,
                                    color = if (selected) Color.White else Color(0xFF8A8899),
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                                Box(
                                    modifier = Modifier
                                        .height(3.dp)
                                        .fillMaxWidth(0.42f)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (selected) violet else Color.Transparent)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (selectedTab) {
                    UniversalTab.Categories -> {
                        OutlinedTextField(
                            value = folderSearchQuery,
                            onValueChange = { folderSearchQuery = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Search categories") },
                            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 12.sp),
                            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = violet.copy(alpha = 0.45f),
                                unfocusedBorderColor = violet.copy(alpha = 0.2f),
                                focusedContainerColor = Color(0xFF13101E),
                                unfocusedContainerColor = Color(0xFF13101E)
                            )
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                                .border(
                                    1.dp,
                                    violet.copy(alpha = 0.15f),
                                    RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
                                ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(10.dp)
                        ) {
                            items(filteredFolders) { path ->
                                val name = prettyName(path)
                                val iconSourceName = if (currentPath == root) name else prettyName(currentPath)
                                FolderButton(
                                    title = name,
                                    onClick = {
                                        folderSearchQuery = ""
                                        onOpenFolder(path)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    icon = { CategorySvgIcon(name = iconSourceName, tint = violet, size = 24.dp) }
                                )
                            }
                        }
                    }

                    UniversalTab.Commands -> {
                        if (isDedupLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = violet,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(30.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Filtering unique IR codes...",
                                        color = Color(0xFFB7B3CC),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        } else if (displayCommands.isEmpty()) {
                            EmptyCard("No commands found in this category.")
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(displayCommands) { item ->
                                    val isFlashed = flashedCommand == item.actualCommand
                                    RemoteCommandButton(
                                        label = item.displayLabel,
                                        protocol = "x${item.profileCoverage}",
                                        isActive = isFlashed,
                                        onClick = {
                                            if (hapticEnabled) {
                                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                            }
                                            flashedCommand = item.actualCommand
                                            scope.launch { delay(220); flashedCommand = null }
                                            onCommandClick(item)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Auto-send modal
        if (autoSend && activeItem != null && displayCoverage > 1) {
            AutoSendProgressModal(
                commandName = activeItem.displayLabel,
                currentIndex = codeStep,
                totalCount = displayCoverage,
                estimatedTimeRemainingMs = estimatedTimeMs,
                hapticEnabled = hapticEnabled,
                onStop = onToggleAutoSend
            )
        }
    }
}
