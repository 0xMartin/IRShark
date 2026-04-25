package com.vex.irshark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Top bar back/home arrow button ───────────────────────────────────────────

@Composable
fun BackIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val violet = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) Color(0xFF13101E) else Color(0xFF0F0D18))
            .border(
                1.dp,
                if (enabled) violet.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = if (enabled) violet else Color(0xFF6A6880),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Folder tile used in Universal Remote ─────────────────────────────────────

@Composable
fun FolderButton(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: (@Composable () -> Unit)? = null) {
    val violet = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .height(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF100D1C))
            .border(1.dp, violet.copy(alpha = 0.34f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        if (icon != null) {
            Box(modifier = Modifier.align(Alignment.TopStart)) { icon() }
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

// ── Empty state card ─────────────────────────────────────────────────────────

@Composable
fun EmptyCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF13101E))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(message, color = Color(0xFF8A8899), fontSize = 11.sp)
    }
}

// ── List row (My Remotes / Remote DB) ────────────────────────────────────────

@Composable
fun ListRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onOpen: () -> Unit,
    onAction: () -> Unit
) {
    val violet = MaterialTheme.colorScheme.primary
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpen)
                    .padding(6.dp)
            ) {
                Text(title, color = Color.White, fontSize = 12.sp)
                Text(subtitle, color = Color(0xFF8A8899), fontSize = 10.sp, maxLines = 1)
            }
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(violet.copy(alpha = 0.18f))
                    .border(1.dp, violet, RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(actionLabel, color = violet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
    }
}

// ── App header bar ───────────────────────────────────────────────────────────

@Composable
fun AppHeader(status: String) {
    val violet = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(violet.copy(alpha = 0.20f))
                    .border(1.dp, violet, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "IR", color = violet, fontWeight = FontWeight.ExtraBold)
            }
            Text(
                text = "IRShark",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Text(
            text = status,
            color = violet,
            fontSize = 10.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(175.dp)
        )
    }
}
