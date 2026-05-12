package com.m4r71n.irshark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.m4r71n.irshark.R
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF100D1C))
            .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                icon()
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                textAlign = if (icon != null) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

private fun categoryIconRes(name: String): Int {
    val lname = name.lowercase()
    return when {
        "tv" in lname || "television" in lname -> R.drawable.ic_cat_tvs
        "ac" in lname || "air" in lname || "condition" in lname -> R.drawable.ic_cat_acs
        "projector" in lname -> R.drawable.ic_cat_projectors
        "dvd" in lname || "blu" in lname || "disc" in lname -> R.drawable.ic_cat_dvd_players
        "fan" in lname -> R.drawable.ic_cat_fans
        "camera" in lname || "cctv" in lname -> R.drawable.ic_cat_cameras
        "console" in lname || "game" in lname || "xbox" in lname || "playstation" in lname || "nintendo" in lname -> R.drawable.ic_cat_consoles
        "audio" in lname || "receiver" in lname || "av" in lname -> R.drawable.ic_cat_av_receivers
        "set_top" in lname || "set top" in lname || "stb" in lname || "cable" in lname || "box" in lname -> R.drawable.ic_cat_set_top_boxes
        "light" in lname || "lamp" in lname || "led" in lname -> R.drawable.ic_cat_lights
        "micro" in lname || "oven" in lname -> R.drawable.ic_cat_microwaves
        else -> R.drawable.ic_cat_other
    }
}

@Composable
fun CategorySvgIcon(
    name: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    Image(
        painter = painterResource(id = categoryIconRes(name)),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size)
    )
}

// ── Empty state card ─────────────────────────────────────────────────────────

@Composable
fun EmptyCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0D0B18))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(vertical = 20.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            color = Color(0xFF6E6B82),
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 17.sp
        )
    }
}

// ── Shared remote command button (Universal + Remote Control) ───────────────

@Composable
fun RemoteCommandButton(
    label: String,
    protocol: String = "",
    isActive: Boolean,
    onClick: () -> Unit,
    onLongPressRepeat: (() -> Unit)? = null,
    onLongPressRepeatStateChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val violet = MaterialTheme.colorScheme.primary
    val stripeColor = if (isActive) Color(0xFFE57373) else violet.copy(alpha = 0.65f)
    val secondaryLabelColor = Color(0xFF7A7A96)

    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF16122A), Color(0xFF0D0B1A))
                )
            )
            .border(
                1.dp,
                if (isActive) Color(0xFFE57373).copy(alpha = 0.50f) else violet.copy(alpha = 0.18f),
                RoundedCornerShape(14.dp)
            )
            .then(
                if (onLongPressRepeat != null) {
                    // Press behavior for Remote UI:
                    // 1) Send one IR signal only after a short stable hold.
                    // 2) After another 1000 ms of holding, start continuous repeat.
                    // 3) If the finger movement looks like scroll, cancel everything.
                    Modifier.pointerInput(onClick, onLongPressRepeat) {
                        coroutineScope {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val shortPressDelayMs = 140L
                                val repeatStartDelayMs = 1_000L
                                val repeatIntervalMs = 100L
                                val touchSlopPx = viewConfiguration.touchSlop
                                val startPos = down.position

                                var pointerIsDown = true
                                var canceledByScroll = false
                                var singleSent = false
                                var repeating = false

                                val singleJob = launch {
                                    delay(shortPressDelayMs)
                                    if (pointerIsDown && !canceledByScroll) {
                                        onClick()
                                        singleSent = true
                                    }
                                }

                                val repeatJob = launch {
                                    delay(shortPressDelayMs + repeatStartDelayMs)
                                    if (pointerIsDown && !canceledByScroll) {
                                        repeating = true
                                        onLongPressRepeatStateChange?.invoke(true)
                                        while (pointerIsDown && !canceledByScroll) {
                                            onLongPressRepeat()
                                            delay(repeatIntervalMs)
                                        }
                                    }
                                }

                                while (pointerIsDown) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                        ?: break

                                    val movedTooMuch =
                                        (change.position - startPos).getDistance() > touchSlopPx
                                    if (movedTooMuch) {
                                        canceledByScroll = true
                                        pointerIsDown = false
                                        break
                                    }

                                    if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                                        pointerIsDown = false
                                        break
                                    }
                                }

                                singleJob.cancel()
                                repeatJob.cancel()

                                if (repeating) {
                                    onLongPressRepeatStateChange?.invoke(false)
                                }

                                // Quick tap should still trigger one send (unless it became scroll).
                                if (!canceledByScroll && !singleSent) {
                                    onClick()
                                }
                            }
                        }
                    }
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 22.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(stripeColor)
            )

            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (protocol.isNotBlank()) {
                Text(
                    text = protocol,
                    color = secondaryLabelColor,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── List row (My Remotes / Remote DB) ────────────────────────────────────────

@Composable
fun ListRow(
    title: String,
    subtitle: String,
    badgeTexts: List<String> = emptyList(),
    actionLabel: String,
    actionEnabled: Boolean = true,
    actionIcon: ImageVector? = null,
    onOpen: () -> Unit,
    onAction: () -> Unit,
    leadingIconName: String? = null,
    isFavorite: Boolean = false,
    onFavoriteToggle: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null
) {
    val violet = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF100D1C))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onFavoriteToggle != null) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isFavorite) "Unpin" else "Pin",
                    tint = if (isFavorite) Color(0xFFFFD54F) else Color(0xFF3D3A52),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onFavoriteToggle)
                )
                Spacer(Modifier.size(8.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen)
                    .padding(end = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!leadingIconName.isNullOrBlank()) {
                        CategorySvgIcon(
                            name = leadingIconName,
                            tint = violet,
                            size = 18.dp
                        )
                    }
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (badgeTexts.isNotEmpty()) {
                    val primary = badgeTexts.firstOrNull()?.trim().orEmpty()
                    val secondary = badgeTexts.drop(1).map { it.trim() }.filter { it.isNotBlank() }
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        val maxPrimaryWidth = maxWidth * 0.62f
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (primary.isNotBlank()) {
                                Badge(
                                    text = primary,
                                    marquee = true,
                                    modifier = Modifier.widthIn(max = maxPrimaryWidth)
                                )
                            }
                            secondary.forEach { badgeText ->
                                Badge(text = badgeText)
                            }
                        }
                    }
                } else {
                    Text(subtitle, color = Color(0xFF6E6B82), fontSize = 11.sp, maxLines = 1)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDuplicate != null) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF181327))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                            .clickable(onClick = onDuplicate),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Duplicate",
                            tint = Color(0xFF6E6B82),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (actionEnabled) violet.copy(alpha = 0.15f) else Color(0xFF181327))
                        .border(
                            1.dp,
                            if (actionEnabled) violet.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(enabled = actionEnabled, onClick = onAction),
                    contentAlignment = Alignment.Center
                ) {
                    if (actionIcon != null) {
                        Icon(
                            imageVector = actionIcon,
                            contentDescription = actionLabel,
                            tint = if (actionEnabled) violet else Color(0xFF6E6B82),
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            actionLabel,
                            color = if (actionEnabled) violet else Color(0xFF6E6B82),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ── Remote grid tile (2-column card view for My Remotes) ────────────────────

@Composable
fun RemoteGridItem(
    title: String,
    iconName: String? = null,
    isFavorite: Boolean = false,
    onFavoriteToggle: (() -> Unit)? = null,
    onOpen: () -> Unit,
    onAction: () -> Unit,
    actionIcon: ImageVector? = null,
    onDuplicate: (() -> Unit)? = null
) {
    val violet = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF100D1C))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon area — centred, takes remaining vertical space
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (!iconName.isNullOrBlank()) {
                    CategorySvgIcon(name = iconName, tint = violet, size = 46.dp)
                } else {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = null,
                        tint = violet.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Name (marquee-scrolls when too long)
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee()
                    .padding(vertical = 6.dp),
                textAlign = TextAlign.Center
            )

            // Bottom action row: star  ·  [duplicate]  [action]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onFavoriteToggle != null) {
                    Box(
                        modifier = Modifier
                            .size(39.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF181327))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            .clickable(onClick = onFavoriteToggle),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (isFavorite) "Unpin" else "Pin",
                            tint = if (isFavorite) Color(0xFFFFD54F) else Color(0xFF3D3A52),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(39.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (onDuplicate != null) {
                        Box(
                            modifier = Modifier
                                .size(39.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF181327))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                .clickable(onClick = onDuplicate),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Duplicate",
                                tint = Color(0xFF6E6B82),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(39.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1A0F0F))
                            .border(1.dp, Color(0xFF3D1A1A), RoundedCornerShape(10.dp))
                            .clickable(onClick = onAction),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = actionIcon ?: Icons.Filled.Delete,
                            contentDescription = null,
                            tint = Color(0xFFAA5555),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
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
            .drawBehind {
                val strokeWidthPx = 4.dp.toPx()
                val y = size.height - strokeWidthPx / 2

                drawLine(
                    color = lerp(violet, Color.Black, 0.6f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidthPx
                )
            }
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
    val pulse = if (fastBlink) rememberInfiniteTransition(label = "tx-led") else null
    val blinkAlpha = if (fastBlink && pulse != null) {
        // Sharp on/off blink only while rapid TX mode is active
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
        ).value
    } else {
        0f
    }
    val idleAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0.22f,
        animationSpec = tween(durationMillis = 180, easing = LinearEasing),
        label = "tx-led-idle-alpha"
    )
    val alpha = if (fastBlink) blinkAlpha else idleAlpha
    val base = if (active) Color(0xFFFF0000) else Color(0xFF5A2A2A)
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(base.copy(alpha = alpha))
            .border(1.5.dp, base.copy(alpha = 0.90f), RoundedCornerShape(999.dp))
    )
}

// ── Badge (compact info display) ──────────────────────────────────────────────

@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    marquee: Boolean = false
) {
    val violet = MaterialTheme.colorScheme.primary
    val pillShape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .height(24.dp)
            .clip(pillShape)
            .background(violet.copy(alpha = 0.15f))
            .border(1.dp, violet.copy(alpha = 0.35f), pillShape)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = violet,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
            modifier = if (marquee) {
                Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
                    .basicMarquee(iterations = Int.MAX_VALUE)
            } else {
                Modifier.wrapContentHeight(Alignment.CenterVertically)
            }
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
    onSearchQuery: ((String) -> Unit)? = null,
    searchResultCount: Int? = null,
    searchTotalCount: Int? = null,
    actionsRound: Boolean = false,
    // Optional breadcrumb/back for sub-screens (e.g. IR Finder steps)
    breadcrumb: String? = null,
    onBack: (() -> Unit)? = null,
    extraActions: List<Pair<ImageVector, () -> Unit>> = emptyList()
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
            if (breadcrumb != null) {
                // Breadcrumb mode: connected home + path box, right-side circular actions + back
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
                            .border(1.dp, violet.copy(alpha = 0.18f), RoundedCornerShape(0.dp, 20.dp, 20.dp, 0.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val segments = breadcrumb.split(" > ")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            segments.forEachIndexed { i, seg ->
                                if (i > 0) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = violet.copy(alpha = 0.45f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Text(
                                    text = seg,
                                    color = if (i == segments.lastIndex) Color.White else Color(0xFF9B8EC4),
                                    fontSize = if (i == segments.lastIndex) 13.sp else 11.sp,
                                    fontWeight = if (i == segments.lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                extraActions.forEach { (icon, onClick) ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF13101E))
                            .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                            .clickable(onClick = onClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null,
                            tint = violet, modifier = Modifier.size(20.dp))
                    }
                }

                if (onBack != null) {
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
            } else {
                // Normal mode: home button + optional search + actions
                HomeIconButton(onClick = onHome, modifier = Modifier.size(40.dp))
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
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0E0B1A))
                                    .border(1.dp, violet.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = searchPlaceholder,
                                        color = Color(0xFF8A8899),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    if (searchResultCount != null) {
                        val resultLabel = if (searchTotalCount != null && searchTotalCount >= searchResultCount) {
                            "$searchResultCount/$searchTotalCount"
                        } else {
                            searchResultCount.toString()
                        }
                        Box(
                            modifier = Modifier
                                .height(30.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(violet.copy(alpha = 0.12f))
                                .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = resultLabel,
                                color = violet,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                actions.forEach { (icon, onClick) ->
                    val actionShape = if (actionsRound) RoundedCornerShape(999.dp) else RoundedCornerShape(10.dp)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(actionShape)
                            .background(violet.copy(alpha = 0.14f))
                            .border(1.dp, violet.copy(alpha = 0.35f), actionShape)
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
}

@Composable
fun RemoteControlNavBar(
    title: String,
    iconName: String?,
    onHome: () -> Unit,
    onBack: () -> Unit,
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
            HomeIconButton(onClick = onHome, modifier = Modifier.size(40.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(violet.copy(alpha = 0.08f))
                    .border(1.dp, violet.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!iconName.isNullOrBlank()) {
                    CategorySvgIcon(name = iconName, tint = violet, size = 18.dp)
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

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

// ── Universal Remote path header (single row design) ────────────────────────

@Composable
fun UniversalRemoteHeader(
    currentPath: String,
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
                        .border(1.dp, violet.copy(alpha = 0.18f), RoundedCornerShape(0.dp, 20.dp, 20.dp, 0.dp)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val segments = currentPath.split(" > ")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        segments.forEachIndexed { i, seg ->
                            if (i > 0) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = violet.copy(alpha = 0.45f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = seg,
                                color = if (i == segments.lastIndex) Color.White else Color(0xFF9B8EC4),
                                fontSize = if (i == segments.lastIndex) 13.sp else 11.sp,
                                fontWeight = if (i == segments.lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
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
