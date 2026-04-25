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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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

@Composable
fun SettingsScreen(
    intervalMs: Float,
    autoStopAtEnd: Boolean,
    showTxLed: Boolean,
    onBack: () -> Unit,
    onIntervalChange: (Float) -> Unit,
    onAutoStopAtEndChange: (Boolean) -> Unit,
    onShowTxLedChange: (Boolean) -> Unit,
    onIntervalPresetSelect: (Float) -> Unit,
    onResetDefaults: () -> Unit
) {
    val violet = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BackIconButton(onClick = onBack, modifier = Modifier.size(40.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F0D1A))
                    .border(1.dp, violet.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Settings",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F0D1A))
                .border(1.dp, violet.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Global IR speed", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${intervalMs.toInt()} ms between codes", color = Color(0xFF8A8899), fontSize = 11.sp)
                Slider(
                    value = intervalMs,
                    onValueChange = onIntervalChange,
                    valueRange = 80f..1500f
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpeedPresetButton("Fast", onClick = { onIntervalPresetSelect(120f) })
                    SpeedPresetButton("Balanced", onClick = { onIntervalPresetSelect(250f) })
                    SpeedPresetButton("Safe", onClick = { onIntervalPresetSelect(500f) })
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        SettingToggleCard(
            title = "Auto stop at end",
            subtitle = "Stop universal sending automatically when last profile is reached",
            checked = autoStopAtEnd,
            onCheckedChange = onAutoStopAtEndChange
        )

        Spacer(modifier = Modifier.height(10.dp))

        SettingToggleCard(
            title = "Show TX LED",
            subtitle = "Animated indicator in header when IR transmission is active",
            checked = showTxLed,
            onCheckedChange = onShowTxLedChange
        )

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F0D1A))
                .border(1.dp, violet.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                .clickable(onClick = onResetDefaults)
                .padding(12.dp)
        ) {
            Text(
                text = "Reset settings to default",
                color = violet,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Changes are saved automatically.",
            color = Color(0xFF8A8899),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SpeedPresetButton(label: String, onClick: () -> Unit) {
    val violet = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(violet.copy(alpha = 0.14f))
            .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = violet, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val violet = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F0D1A))
            .border(1.dp, violet.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Color(0xFF8A8899), fontSize = 11.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
