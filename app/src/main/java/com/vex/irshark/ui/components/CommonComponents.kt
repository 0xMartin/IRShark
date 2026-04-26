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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.vex.irshark.R
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

@Composable
fun HomeIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp)
) {
    val violet = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(Color(0xFF13101E))
            .border(1.dp, violet.copy(alpha = 0.40f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Home,
            contentDescription = "Home",
            tint = violet,
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
    actionEnabled: Boolean = true,
    actionIcon: ImageVector? = null,
    onOpen: () -> Unit,
    onAction: () -> Unit,
    isFavorite: Boolean = false,
    onFavoriteToggle: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null
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
            if (onFavoriteToggle != null) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isFavorite) "Unpin" else "Pin",
                    tint = if (isFavorite) Color(0xFFFFD54F) else Color(0xFF5A5870),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onFavoriteToggle)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpen)
                    .padding(6.dp)
            ) {
                Text(title, color = Color.White, fontSize = 14.sp)
                Text(subtitle, color = Color(0xFF8A8899), fontSize = 11.sp, maxLines = 1)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDuplicate != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A1726))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                            .clickable(onClick = onDuplicate),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Duplicate",
                            tint = Color(0xFF8A8899),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (actionEnabled) violet.copy(alpha = 0.18f) else Color(0xFF1A1726))
                        .border(
                            1.dp,
                            if (actionEnabled) violet else Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(enabled = actionEnabled, onClick = onAction),
                    contentAlignment = Alignment.Center
                ) {
                    if (actionIcon != null) {
                        Icon(
                            imageVector = actionIcon,
                            contentDescription = actionLabel,
                            tint = if (actionEnabled) violet else Color(0xFF8A8899),
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            actionLabel,
                            color = if (actionEnabled) violet else Color(0xFF8A8899),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
    }
}

// ── App header bar ───────────────────────────────────────────────────────────

@Composable
fun AppHeader(txActive: Boolean, showTxLed: Boolean, fastBlink: Boolean, screenTitle: String = "IRShark") {
    val violet = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0814))
            .border(1.dp, violet.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(
                    painter = painterResource(R.drawable.app_icon),
                    contentDescription = "IRShark",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Text(
                    text = screenTitle,
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (showTxLed) {
                    TxLedIndicator(active = txActive, fastBlink = fastBlink)
                }
            }
        }
    }
}

@Composable
private fun TxLedIndicator(active: Boolean, fastBlink: Boolean) {
    val pulse = rememberInfiniteTransition(label = "tx-led")
    val alpha = if (fastBlink) {
        // Sharp on/off blink: full on for half the period, full off for other half
        pulse.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 220
                    1f at 0
                    1f at 99
                    0f at 100
                    0f at 219
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "tx-led-alpha"
        )
    } else {
        pulse.animateFloat(
            initialValue = if (active) 0.45f else 0.18f,
            targetValue = if (active) 1f else 0.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "tx-led-alpha"
        )
    }
    val base = if (active) Color(0xFF5BFF9A) else Color(0xFF4A5568)
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(base.copy(alpha = alpha.value))
            .border(1.dp, base.copy(alpha = 0.85f), RoundedCornerShape(999.dp))
    )
}

// ── Badge (compact info display) ──────────────────────────────────────────────

@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    val violet = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(violet.copy(alpha = 0.15f))
            .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = violet,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── Section nav bar (Panel2 for My Remotes, Remote DB, Settings) ───────────

@Composable
fun SectionNavBar(
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    // Each action: Pair<icon, onClick>. Rendered left-to-right as bordered icon boxes.
    actions: List<Pair<ImageVector, () -> Unit>> = emptyList(),
    // Optional search field between home button and actions
    searchQuery: String? = null,
    searchPlaceholder: String = "Search",
    onSearchQuery: ((String) -> Unit)? = null
) {
    val violet = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
            .background(Color(0xFF121024))
            .border(1.dp, violet.copy(alpha = 0.12f), RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
            .padding(horizontal = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeIconButton(onClick = onHome, modifier = Modifier.size(40.dp))
            // Search field (if provided)
            if (searchQuery != null && onSearchQuery != null) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQuery,
                    singleLine = true,
                    cursorBrush = SolidColor(Color.White),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 13.sp
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0E0B1A))
                                .border(1.dp, violet.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(searchPlaceholder, color = Color(0xFF8A8899), fontSize = 13.sp)
                            }
                            inner()
                        }
                    }
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            actions.forEach { (icon, onClick) ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(violet.copy(alpha = 0.14f))
                        .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = violet,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── Universal Remote path header (single row design) ────────────────────────

@Composable
fun UniversalRemoteHeader(
    currentPath: String,
    count: Int,
    onHome: () -> Unit,
    onBack: () -> Unit,
    canGoBack: Boolean = true,
    modifier: Modifier = Modifier
) {
    val violet = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
            .background(Color(0xFF121024))
            .border(1.dp, violet.copy(alpha = 0.12f), RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
            .padding(horizontal = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeIconButton(
                    onClick = onHome,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(10.dp, 0.dp, 0.dp, 10.dp)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(0.dp, 20.dp, 20.dp, 0.dp))
                        .background(violet.copy(alpha = 0.08f))
                        .border(1.dp, violet.copy(alpha = 0.2f), RoundedCornerShape(0.dp, 20.dp, 20.dp, 0.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = currentPath,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(violet.copy(alpha = 0.15f))
                    .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Count: $count",
                    color = violet,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (canGoBack) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF13101E))
                        .border(1.dp, violet.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = violet,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
