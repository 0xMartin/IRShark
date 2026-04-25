package com.vex.irshark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    onUniversal: () -> Unit,
    onMyRemotes: () -> Unit,
    onRemoteDb: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Professional IR control platform built on Flipper-IRDB",
            color = Color(0xFF8A8899),
            fontSize = 12.sp
        )

        HomeEntryButton("UNIVERSAL REMOTE", "Pick device category and auto test commands", onUniversal)
        HomeEntryButton("MY REMOTES", "Your saved and reusable remotes", onMyRemotes)
        HomeEntryButton("REMOTE DB", "Browse all remotes from Flipper-IRDB", onRemoteDb)
        HomeEntryButton("SETTINGS", "Global speed, TX LED and behavior", onSettings)
    }
}

@Composable
private fun HomeEntryButton(title: String, subtitle: String, onClick: () -> Unit) {
    val violet = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF100D1C))
            .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = Color(0xFF8A8899), fontSize = 11.sp)
        }
    }
}
