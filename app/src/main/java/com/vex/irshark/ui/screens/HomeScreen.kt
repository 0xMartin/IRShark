package com.vex.irshark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

        HomeEntryButton("UNIVERSAL REMOTE", "Pick device category and auto test commands", Icons.Filled.Wifi, onUniversal)
        HomeEntryButton("MY REMOTES", "Your saved and reusable remotes", Icons.Filled.Folder, onMyRemotes)
        HomeEntryButton("REMOTE DB", "Browse all remotes from Flipper-IRDB", Icons.Filled.Search, onRemoteDb)
        HomeEntryButton("SETTINGS", "Global speed, TX LED and behavior", Icons.Filled.Settings, onSettings)
    }
}

@Composable
private fun HomeEntryButton(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    val violet = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF100D1C))
            .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .fillMaxHeight()
                    .background(violet.copy(alpha = 0.10f))
                    .border(
                        width = 0.dp,
                        color = Color.Transparent,
                        shape = RoundedCornerShape(0.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = violet,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(3.dp))
                Text(subtitle, color = Color(0xFF8A8899), fontSize = 11.sp)
            }
        }
    }
}
