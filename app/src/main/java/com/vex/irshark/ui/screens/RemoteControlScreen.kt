package com.vex.irshark.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vex.irshark.ui.components.BackIconButton
import kotlin.math.roundToInt

@Composable
fun RemoteControlScreen(
    title: String,
    subtitle: String,
    commands: List<String>,
    selectedCommand: String?,
    txCount: Int,
    autoSend: Boolean,
    intervalMs: Float,
    progress: Float,
    onBack: () -> Unit,
    onToggleAutoSend: () -> Unit,
    onIntervalChange: (Float) -> Unit,
    onCommandClick: (String) -> Unit,
    onSave: () -> Unit,
    showSaveButton: Boolean
) {
    val violet = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Top navigation row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BackIconButton(onClick = onBack, modifier = Modifier.size(40.dp))
            if (showSaveButton) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(violet.copy(alpha = 0.15f))
                        .border(1.dp, violet.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onSave),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Save", color = violet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(subtitle, color = Color(0xFF8A8899), fontSize = 11.sp)
        Spacer(modifier = Modifier.height(10.dp))

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

        // Command buttons grid
        commands.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { cmd ->
                    val selected = selectedCommand == cmd
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
                            .clickable { onCommandClick(cmd) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cmd,
                            color = if (selected) violet else Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
                repeat(2 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            text = if (selectedCommand == null) "Select command to transmit."
                   else "TX count for $selectedCommand: $txCount",
            color = Color.White,
            fontSize = 11.sp
        )

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
