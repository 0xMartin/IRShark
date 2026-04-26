package com.vex.irshark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vex.irshark.data.FlipperDbIndex
import com.vex.irshark.data.UniversalCommandItem
import com.vex.irshark.data.dbRootPath
import com.vex.irshark.data.prettyName
import com.vex.irshark.data.prettyPathWithChevron
import com.vex.irshark.data.profilesUnderPath
import com.vex.irshark.data.resolveUniversalCommandsForPath
import com.vex.irshark.ui.components.AutoSendProgressModal
import com.vex.irshark.ui.components.CategorySvgIcon
import com.vex.irshark.ui.components.EmptyCard
import com.vex.irshark.ui.components.FolderButton
import com.vex.irshark.ui.components.RemoteCommandButton
import com.vex.irshark.ui.components.UniversalRemoteHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun UniversalRemoteScreen(
    dbIndex: FlipperDbIndex,
    currentPath: String,
    activeItem: UniversalCommandItem?,
    codeStep: Int,
    activeCoverage: Int,
    autoSend: Boolean,
    intervalMs: Float,
    onHome: () -> Unit,
    onBackPath: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onCommandClick: (UniversalCommandItem) -> Unit,
    onToggleAutoSend: () -> Unit,
    onIntervalChange: (Float) -> Unit
) {
    val root = dbRootPath()
    val folders = dbIndex.folders[currentPath].orEmpty().sortedBy { prettyName(it) }
    val resolvedCommands = resolveUniversalCommandsForPath(dbIndex, currentPath)
    val violet = MaterialTheme.colorScheme.primary
    
    var folderSearchQuery by remember { mutableStateOf("") }
    var flashedCommand by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val estimatedTimeMs = if (autoSend && activeItem != null && activeCoverage > 0) {
        ((activeCoverage - codeStep) * intervalMs.roundToInt()).toLong()
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
                count = profilesUnderPath(dbIndex, currentPath).size,
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp)
            ) {
                // Device folder section
                if (folders.isNotEmpty()) {
                    Text("Categories", color = violet, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Search field
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

                    // Scrollable list (same style as IR Finder picker)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                            .border(
                                1.dp,
                                violet.copy(alpha = 0.15f),
                                RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
                            ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)
                    ) {
                        items(filteredFolders) { path ->
                            val name = prettyName(path)
                            val iconSourceName = if (currentPath == root) {
                                name
                            } else {
                                prettyName(currentPath)
                            }
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

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Commands section
                Text("Commands", color = violet, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))

                if (resolvedCommands.isEmpty()) {
                    EmptyCard("No commands found in this category.")
                } else {
                    resolvedCommands.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { item ->
                                val isFlashed = flashedCommand == item.actualCommand
                                RemoteCommandButton(
                                    label = item.displayLabel,
                                    countLabel = "x${item.profileCoverage}",
                                    isActive = isFlashed,
                                    onClick = {
                                        flashedCommand = item.actualCommand
                                        scope.launch { delay(220); flashedCommand = null }
                                        onCommandClick(item)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                )
                            }
                            repeat(2 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Auto-send modal
        if (autoSend && activeItem != null && activeCoverage > 1) {
            AutoSendProgressModal(
                commandName = activeItem.displayLabel,
                currentIndex = codeStep,
                totalCount = activeCoverage,
                estimatedTimeRemainingMs = estimatedTimeMs,
                onStop = onToggleAutoSend
            )
        }
    }
}
