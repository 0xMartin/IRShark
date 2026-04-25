package com.vex.irshark.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vex.irshark.data.FlipperDbIndex
import com.vex.irshark.data.UniversalCommandItem
import com.vex.irshark.data.dbRootPath
import com.vex.irshark.data.prettyName
import com.vex.irshark.data.prettyPath
import com.vex.irshark.data.profilesUnderPath
import com.vex.irshark.data.resolveUniversalCommandsForPath
import com.vex.irshark.ui.components.AutoSendProgressModal
import com.vex.irshark.ui.components.EmptyCard
import com.vex.irshark.ui.components.FolderButton
import com.vex.irshark.ui.components.UniversalRemoteHeader
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
    
    val estimatedTimeMs = if (autoSend && activeItem != null && activeCoverage > 0) {
        ((activeCoverage - codeStep) * intervalMs.roundToInt()).toLong()
    } else 0L
    
    val filteredFolders = if (folderSearchQuery.isBlank()) {
        folders
    } else {
        folders.filter { prettyName(it).contains(folderSearchQuery, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Top header bar
            UniversalRemoteHeader(
                currentPath = prettyPath(currentPath),
                count = profilesUnderPath(dbIndex, currentPath).size,
                onHome = {
                    if (autoSend) onToggleAutoSend()
                    onHome()
                },
                onBack = onBackPath,
                canGoBack = currentPath != root
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
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

                    // Scrollable list (max 6 items = 2x3 grid)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                            .border(
                                1.dp,
                                violet.copy(alpha = 0.15f),
                                RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
                            ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp)
                    ) {
                        items(filteredFolders.chunked(2)) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                row.forEach { path ->
                                    val name = prettyName(path)
                                    FolderButton(
                                        title = name,
                                        onClick = {
                                            folderSearchQuery = ""
                                            onOpenFolder(path)
                                        },
                                        modifier = Modifier.weight(1f),
                                        icon = { CategoryIcon(name = name, tint = violet) }
                                    )
                                }
                                repeat(2 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
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
                                val selected = activeItem?.actualCommand == item.actualCommand
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) violet.copy(alpha = 0.28f) else Color(0xFF0F0D1A))
                                        .border(
                                            1.dp,
                                            if (selected) violet else Color.White.copy(alpha = 0.12f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onCommandClick(item) }
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = item.displayLabel,
                                            color = if (selected) violet else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )
                                        if (item.profileCoverage > 1) {
                                            Text(
                                                text = "${item.profileCoverage}x",
                                                color = if (selected) violet else Color(0xFF8A8899),
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
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

// ── Category icons drawn with Canvas ─────────────────────────────────────────

@Composable
private fun CategoryIcon(name: String, tint: Color) {
    val lname = name.lowercase()
    val iconColor = tint.copy(alpha = 0.75f)
    Canvas(modifier = Modifier.size(28.dp)) {
        when {
            "tv" in lname || "television" in lname || "projector" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                drawRoundRect(color = iconColor, topLeft = Offset(2f, 4f), size = Size(size.width - 4f, size.height * 0.65f), cornerRadius = CornerRadius(3f), style = sw)
                drawLine(iconColor, Offset(size.width / 2f, size.height * 0.7f), Offset(size.width / 2f, size.height - 4f), strokeWidth = 2.2f)
                drawLine(iconColor, Offset(size.width * 0.3f, size.height - 4f), Offset(size.width * 0.7f, size.height - 4f), strokeWidth = 2.2f)
            }
            "ac" in lname || "air" in lname || "condition" in lname || "purif" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                drawRoundRect(color = iconColor, topLeft = Offset(2f, 6f), size = Size(size.width - 4f, size.height * 0.55f), cornerRadius = CornerRadius(4f), style = sw)
                for (i in 0..3) {
                    val x = 6f + i * 5f
                    drawLine(iconColor, Offset(x, size.height * 0.55f + 4f), Offset(x, size.height - 5f), strokeWidth = 1.8f)
                }
            }
            "audio" in lname || "receiver" in lname || "speaker" in lname || "sound" in lname || "amplif" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                drawRoundRect(color = iconColor, topLeft = Offset(2f, 8f), size = Size(size.width - 4f, size.height * 0.45f), cornerRadius = CornerRadius(3f), style = sw)
                for (i in 0..2) {
                    drawCircle(color = iconColor, radius = 1.5f, center = Offset(8f + i * 5f, size.height * 0.3f + 8f))
                }
            }
            "blu" in lname || "dvd" in lname || "cd" in lname || "disc" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                drawCircle(color = iconColor, radius = size.minDimension / 2f - 3f, style = sw)
                drawCircle(color = iconColor, radius = 4f, style = sw)
                drawCircle(color = iconColor, radius = 1.5f)
            }
            "camera" in lname || "cctv" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                drawRoundRect(color = iconColor, topLeft = Offset(2f, 7f), size = Size(size.width * 0.65f, size.height * 0.5f), cornerRadius = CornerRadius(3f), style = sw)
                drawCircle(color = iconColor, radius = 4f, center = Offset(size.width * 0.35f, size.height * 0.315f + 7f), style = sw)
                val path = Path().apply {
                    moveTo(size.width * 0.68f, size.height * 0.22f)
                    lineTo(size.width - 3f, size.height * 0.15f)
                    lineTo(size.width - 3f, size.height * 0.65f)
                    lineTo(size.width * 0.68f, size.height * 0.58f)
                    close()
                }
                drawPath(path, iconColor, style = sw)
            }
            "cable" in lname || "box" in lname || "stb" in lname || "set" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                drawRoundRect(color = iconColor, topLeft = Offset(2f, 8f), size = Size(size.width - 4f, size.height * 0.48f), cornerRadius = CornerRadius(3f), style = sw)
                drawCircle(color = iconColor, radius = 2f, center = Offset(8f, size.height * 0.38f))
                drawRoundRect(color = iconColor, topLeft = Offset(12f, size.height * 0.28f + 1f), size = Size(8f, 3.5f), cornerRadius = CornerRadius(2f))
            }
            "clock" in lname || "watch" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                drawCircle(color = iconColor, radius = size.minDimension / 2f - 3f, style = sw)
                drawLine(iconColor, center, Offset(center.x, center.y - size.minDimension * 0.25f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(iconColor, center, Offset(center.x + size.minDimension * 0.2f, center.y + size.minDimension * 0.1f), strokeWidth = 1.6f, cap = StrokeCap.Round)
            }
            "game" in lname || "console" in lname || "nintendo" in lname || "playstation" in lname || "xbox" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                val cx = size.width * 0.35f
                val cy = size.height * 0.5f
                for (angle in listOf(0f, 90f, 180f, 270f)) {
                    val rad = Math.toRadians(angle.toDouble())
                    val dx = (Math.cos(rad) * 7f).toFloat()
                    val dy = (Math.sin(rad) * 7f).toFloat()
                    drawLine(iconColor, Offset(cx, cy), Offset(cx + dx, cy + dy), strokeWidth = 2.2f, cap = StrokeCap.Round)
                }
                drawCircle(color = iconColor, radius = 2.5f, center = Offset(size.width * 0.75f, size.height * 0.38f), style = sw)
                drawCircle(color = iconColor, radius = 2.5f, center = Offset(size.width * 0.75f, size.height * 0.62f), style = sw)
            }
            "fan" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                drawCircle(color = iconColor, radius = size.minDimension / 2f - 3f, style = sw)
                drawCircle(color = iconColor, radius = 4f, style = sw)
                for (angle in 0..2) {
                    val rad = Math.toRadians(angle * 120.0)
                    val dx = (Math.cos(rad) * 7f).toFloat()
                    val dy = (Math.sin(rad) * 7f).toFloat()
                    drawLine(iconColor, center, Offset(center.x + dx, center.y + dy), strokeWidth = 2.2f, cap = StrokeCap.Round)
                }
            }
            "light" in lname || "lamp" in lname || "led" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                drawCircle(color = iconColor, radius = 8f, center = Offset(center.x, center.y - 2f), style = sw)
                drawLine(iconColor, Offset(center.x - 4f, center.y + 7f), Offset(center.x + 4f, center.y + 7f), strokeWidth = 2f)
                drawLine(iconColor, Offset(center.x - 3f, center.y + 10f), Offset(center.x + 3f, center.y + 10f), strokeWidth = 2f)
            }
            "micro" in lname || "oven" in lname || "kitchen" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                drawRoundRect(color = iconColor, topLeft = Offset(2f, 5f), size = Size(size.width - 4f, size.height - 10f), cornerRadius = CornerRadius(3f), style = sw)
                drawRoundRect(color = iconColor, topLeft = Offset(5f, 8f), size = Size(size.width * 0.55f, size.height - 16f), cornerRadius = CornerRadius(2f), style = sw)
                drawLine(iconColor, Offset(size.width - 6f, 9f), Offset(size.width - 6f, size.height - 6f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            }
            else -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                drawRoundRect(color = iconColor, topLeft = Offset(7f, 2f), size = Size(size.width - 14f, size.height - 4f), cornerRadius = CornerRadius(6f), style = sw)
                drawCircle(color = iconColor, radius = 3f, center = Offset(center.x, 9f), style = sw)
                val cx2 = center.x; val cy2 = size.height * 0.56f
                drawLine(iconColor, Offset(cx2 - 5f, cy2), Offset(cx2 + 5f, cy2), strokeWidth = 1.8f, cap = StrokeCap.Round)
                drawLine(iconColor, Offset(cx2, cy2 - 5f), Offset(cx2, cy2 + 5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            }
        }
    }
}
