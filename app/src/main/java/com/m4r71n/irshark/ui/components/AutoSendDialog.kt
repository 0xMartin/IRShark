package com.m4r71n.irshark.ui.components

import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AutoSendProgressModal(
    commandName: String,
    currentIndex: Int,
    totalCount: Int,
    estimatedTimeRemainingMs: Long,
    hapticEnabled: Boolean = true,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val violet = MaterialTheme.colorScheme.primary
    val progress = if (totalCount > 0) currentIndex.toFloat() / totalCount.toFloat() else 0f
    val view = LocalView.current
    
    val seconds = (estimatedTimeRemainingMs / 1000).toInt()
    val minutes = seconds / 60
    val secs = seconds % 60
    val timeStr = if (minutes > 0) "${minutes}m ${secs}s" else "${secs}s"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = false) {}
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF13101E))
                .border(1.dp, violet.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Sending", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                
                Text(
                    commandName,
                    color = violet,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = violet,
                    trackColor = Color(0xFF1E1A30)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$currentIndex / $totalCount", color = Color(0xFF8A8899), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(timeStr, color = Color(0xFF8A8899), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2E1020))
                        .border(1.dp, Color(0xFFFF7B9D), RoundedCornerShape(12.dp))
                        .clickable {
                            if (hapticEnabled) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            }
                            onStop()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("STOP", color = Color(0xFFFFB7C8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
