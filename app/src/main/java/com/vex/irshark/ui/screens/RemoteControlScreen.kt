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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.vex.irshark.ui.components.Badge

@Composable
fun RemoteControlScreen(
    title: String,
    subtitle: String,
    commands: List<String>,
    selectedCommand: String?,
    txCount: Int,
    onBack: () -> Unit,
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
                    Text("Add", color = violet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Device info header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F0D1A))
                .border(1.dp, violet.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Badge(subtitle)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text("Controls", color = violet, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))

        // Command buttons grid
        commands.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { cmd ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F0D1A))
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onCommandClick(cmd) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cmd,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
                repeat(2 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
