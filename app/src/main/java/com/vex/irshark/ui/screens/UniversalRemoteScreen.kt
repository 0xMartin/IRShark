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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.vex.irshark.ui.components.BackIconButton
import com.vex.irshark.ui.components.EmptyCard
import com.vex.irshark.ui.components.FolderButton
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
    val progress = if (activeCoverage > 0) codeStep.toFloat() / activeCoverage.toFloat() else 0f
    val violet = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Navigation row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BackIconButton(onClick = onHome, modifier = Modifier.size(40.dp))
            if (currentPath != root) {
                BackIconButton(onClick = onBackPath, modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Path: ${prettyPath(currentPath)}",
            color = Color(0xFF8A8899),
            fontSize = 11.sp
        )
        Text(
            text = "Folders: ${folders.size} | Profiles: ${profilesUnderPath(dbIndex, currentPath).size}",
            color = Color(0xFF8A8899),
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Device folder grid with icons
        if (folders.isNotEmpty()) {
            Text("Device folders", color = violet, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            folders.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { path ->
                        val name = prettyName(path)
                        FolderButton(
                            title = name,
                            onClick = { onOpenFolder(path) },
                            modifier = Modifier.weight(1f),
                            icon = { CategoryIcon(name = name, tint = violet) }
                        )
                    }
                    repeat(2 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Universal commands section
        Text("Universal commands", color = violet, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))

        // Auto-send row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (autoSend) Color(0xFF2E1020) else Color(0xFF18103A))
                    .border(
                        1.dp,
                        if (autoSend) Color(0xFFFF7B9D) else violet,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable(onClick = onToggleAutoSend),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (autoSend) "STOP" else "START AUTO SEND",
                    color = if (autoSend) Color(0xFFFFB7C8) else violet,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF13101E))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "${intervalMs.roundToInt()} ms", color = Color.White, fontSize = 12.sp)
            }
        }

        Slider(
            value = intervalMs,
            onValueChange = onIntervalChange,
            valueRange = 80f..1200f,
            modifier = Modifier.fillMaxWidth()
        )

        // Command buttons
        if (resolvedCommands.isEmpty()) {
            EmptyCard("No command names found in this folder path.")
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
                                .height(50.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) violet.copy(alpha = 0.28f) else Color(0xFF100D1C))
                                .border(
                                    1.dp,
                                    if (selected) violet else Color.White.copy(alpha = 0.14f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onCommandClick(item) }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${item.displayLabel} (${item.profileCoverage})",
                                color = if (selected) violet else Color.White,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                    repeat(2 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Status line
        val status = when {
            activeItem == null -> "Select a command to start testing."
            activeCoverage <= 0 -> "No matching profiles for selected command."
            autoSend -> "Auto sending ${activeItem.displayLabel}: $codeStep / $activeCoverage"
            else -> "Manual step ${activeItem.displayLabel}: $codeStep / $activeCoverage"
        }
        Text(status, color = Color.White, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(20.dp)),
            color = violet,
            trackColor = Color(0xFF1E1A30)
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── Category icons drawn with Canvas ─────────────────────────────────────────

@Composable
private fun CategoryIcon(name: String, tint: Color) {
    val lname = name.lowercase()
    val iconColor = tint.copy(alpha = 0.75f)
    Canvas(modifier = Modifier.size(28.dp)) {
        when {
            // TV / Televisions
            "tv" in lname || "television" in lname || "projector" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                // Screen rectangle
                drawRoundRect(color = iconColor, topLeft = Offset(2f, 4f), size = Size(size.width - 4f, size.height * 0.65f), cornerRadius = CornerRadius(3f), style = sw)
                // Stand
                drawLine(iconColor, Offset(size.width / 2f, size.height * 0.7f), Offset(size.width / 2f, size.height - 4f), strokeWidth = 2.2f)
                drawLine(iconColor, Offset(size.width * 0.3f, size.height - 4f), Offset(size.width * 0.7f, size.height - 4f), strokeWidth = 2.2f)
            }
            // AC / Air conditioner / air_purifier
            "ac" in lname || "air" in lname || "condition" in lname || "purif" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                drawRoundRect(color = iconColor, topLeft = Offset(2f, 6f), size = Size(size.width - 4f, size.height * 0.55f), cornerRadius = CornerRadius(4f), style = sw)
                // Fins
                for (i in 0..3) {
                    val x = 6f + i * 5f
                    drawLine(iconColor, Offset(x, size.height * 0.55f + 4f), Offset(x, size.height - 5f), strokeWidth = 1.8f)
                }
            }
            // Audio / Receiver / Speaker / Sound bar
            "audio" in lname || "receiver" in lname || "speaker" in lname || "sound" in lname || "amplif" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                drawRoundRect(color = iconColor, topLeft = Offset(2f, 8f), size = Size(size.width - 4f, size.height * 0.45f), cornerRadius = CornerRadius(3f), style = sw)
                // Speaker grill dots
                for (i in 0..2) {
                    drawCircle(color = iconColor, radius = 1.5f, center = Offset(8f + i * 5f, size.height * 0.3f + 8f))
                }
            }
            // Blu-ray / DVD / CD
            "blu" in lname || "dvd" in lname || "cd" in lname || "disc" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                drawCircle(color = iconColor, radius = size.minDimension / 2f - 3f, style = sw)
                drawCircle(color = iconColor, radius = 4f, style = sw)
                drawCircle(color = iconColor, radius = 1.5f)
            }
            // Camera
            "camera" in lname || "cctv" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                drawRoundRect(color = iconColor, topLeft = Offset(2f, 7f), size = Size(size.width * 0.65f, size.height * 0.5f), cornerRadius = CornerRadius(3f), style = sw)
                // Lens
                drawCircle(color = iconColor, radius = 4f, center = Offset(size.width * 0.35f, size.height * 0.315f + 7f), style = sw)
                // Viewfinder triangle
                val path = Path().apply {
                    moveTo(size.width * 0.68f, size.height * 0.22f)
                    lineTo(size.width - 3f, size.height * 0.15f)
                    lineTo(size.width - 3f, size.height * 0.65f)
                    lineTo(size.width * 0.68f, size.height * 0.58f)
                    close()
                }
                drawPath(path, iconColor, style = sw)
            }
            // Cable box / STB / set-top
            "cable" in lname || "box" in lname || "stb" in lname || "set" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                drawRoundRect(color = iconColor, topLeft = Offset(2f, 8f), size = Size(size.width - 4f, size.height * 0.48f), cornerRadius = CornerRadius(3f), style = sw)
                // Power dot + led
                drawCircle(color = iconColor, radius = 2f, center = Offset(8f, size.height * 0.38f))
                drawRoundRect(color = iconColor, topLeft = Offset(12f, size.height * 0.28f + 1f), size = Size(8f, 3.5f), cornerRadius = CornerRadius(2f))
            }
            // Clocks
            "clock" in lname || "watch" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                drawCircle(color = iconColor, radius = size.minDimension / 2f - 3f, style = sw)
                // Hour/minute hands
                drawLine(iconColor, center, Offset(center.x, center.y - size.minDimension * 0.25f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(iconColor, center, Offset(center.x + size.minDimension * 0.2f, center.y + size.minDimension * 0.1f), strokeWidth = 1.6f, cap = StrokeCap.Round)
            }
            // Game / console
            "game" in lname || "console" in lname || "nintendo" in lname || "playstation" in lname || "xbox" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                // D-pad
                val cx = size.width * 0.35f
                val cy = size.height * 0.5f
                for (angle in listOf(0f, 90f, 180f, 270f)) {
                    val rad = Math.toRadians(angle.toDouble())
                    val dx = (Math.cos(rad) * 7f).toFloat()
                    val dy = (Math.sin(rad) * 7f).toFloat()
                    drawLine(iconColor, Offset(cx, cy), Offset(cx + dx, cy + dy), strokeWidth = 2.2f, cap = StrokeCap.Round)
                }
                // Buttons
                drawCircle(color = iconColor, radius = 2.5f, center = Offset(size.width * 0.75f, size.height * 0.38f), style = sw)
                drawCircle(color = iconColor, radius = 2.5f, center = Offset(size.width * 0.75f, size.height * 0.62f), style = sw)
            }
            // Fan
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
            // Lights / lamp
            "light" in lname || "lamp" in lname || "led" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                // Bulb
                drawCircle(color = iconColor, radius = 8f, center = Offset(center.x, center.y - 2f), style = sw)
                drawLine(iconColor, Offset(center.x - 4f, center.y + 7f), Offset(center.x + 4f, center.y + 7f), strokeWidth = 2f)
                drawLine(iconColor, Offset(center.x - 3f, center.y + 10f), Offset(center.x + 3f, center.y + 10f), strokeWidth = 2f)
            }
            // Microwave / oven
            "micro" in lname || "oven" in lname || "kitchen" in lname -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                drawRoundRect(color = iconColor, topLeft = Offset(2f, 5f), size = Size(size.width - 4f, size.height - 10f), cornerRadius = CornerRadius(3f), style = sw)
                // Door window
                drawRoundRect(color = iconColor, topLeft = Offset(5f, 8f), size = Size(size.width * 0.55f, size.height - 16f), cornerRadius = CornerRadius(2f), style = sw)
                // Handle
                drawLine(iconColor, Offset(size.width - 6f, 9f), Offset(size.width - 6f, size.height - 6f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            }
            // Default: remote control icon
            else -> {
                val sw = Stroke(width = 2.2f, cap = StrokeCap.Round)
                // Remote body
                drawRoundRect(color = iconColor, topLeft = Offset(7f, 2f), size = Size(size.width - 14f, size.height - 4f), cornerRadius = CornerRadius(6f), style = sw)
                // Power button at top
                drawCircle(color = iconColor, radius = 3f, center = Offset(center.x, 9f), style = sw)
                // D-pad cross
                val cx2 = center.x; val cy2 = size.height * 0.56f
                drawLine(iconColor, Offset(cx2 - 5f, cy2), Offset(cx2 + 5f, cy2), strokeWidth = 1.8f, cap = StrokeCap.Round)
                drawLine(iconColor, Offset(cx2, cy2 - 5f), Offset(cx2, cy2 + 5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            }
        }
    }
}
