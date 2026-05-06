package com.m4r71n.irshark.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m4r71n.irshark.R

@Composable
fun HomeScreen(
    onUniversal: () -> Unit,
    onMyRemotes: () -> Unit,
    onRemoteDb:  () -> Unit,
    onSettings:  () -> Unit,
    onMacros:    () -> Unit = {},
    onIrFinder: () -> Unit = {}
) {
    val violet = MaterialTheme.colorScheme.primary
    val sharkColor = Color(0xFF9B6DFF)

    Box(modifier = Modifier.fillMaxSize()) {
        // Decorative shark textures
        Image(
            painter = painterResource(id = R.drawable.shark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(sharkColor, BlendMode.SrcIn),
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 40.dp, y = (-20).dp)
                .rotate(-35f)
                .alpha(0.06f)
        )
        Image(
            painter = painterResource(id = R.drawable.shark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(sharkColor, BlendMode.SrcIn),
            modifier = Modifier
                .size(110.dp)
                .align(Alignment.TopStart)
                .rotate(165f)
                .alpha(0.04f)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Hero card — Universal Remote ──────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            0f to Color(0xFF1E1535),
                            0.55f to Color(0xFF13102A),
                            1f to Color(0xFF0D0B1A)
                        )
                    )
                    .border(
                        1.8.dp,
                        Brush.linearGradient(
                            0f to violet.copy(alpha = 0.7f),
                            1f to violet.copy(alpha = 0.15f)
                        ),
                        RoundedCornerShape(22.dp)
                    )
                    .clickable(onClick = onUniversal),
            ) {
                // Glow blob behind icon
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.CenterEnd)
                        .offset(x = (-20).dp)
                        .background(
                            Brush.radialGradient(listOf(violet.copy(alpha = 0.18f), Color.Transparent)),
                            RoundedCornerShape(50)
                        )
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Universal Remote",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.3).sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Send IR signals to any device",
                            color = violet.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(violet.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SettingsRemote,
                            contentDescription = null,
                            tint = violet,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // ── 2×2 grid ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HomeGridCard("My Remotes",  Icons.Filled.Folder,    "Saved custom remotes",  onMyRemotes, Modifier.weight(1f))
                HomeGridCard("Remote DB",   Icons.Filled.Storage,   "Browse IR database",    onRemoteDb,  Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HomeGridCard("IR Finder",   Icons.Filled.FindInPage, "Find signal in DB",    onIrFinder,  Modifier.weight(1f))
                HomeGridCard("Macros",      Icons.Filled.AutoAwesome,"Automate IR sequences", onMacros,   Modifier.weight(1f))
            }

            // ── Settings — slim bar ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF100D1C))
                    .border(1.4.dp, violet.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                    .clickable(onClick = onSettings),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        tint = violet.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Settings",
                        color = Color(0xFFB7B3CC),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeGridCard(
    title: String,
    icon: ImageVector,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val violet = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .height(118.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    0f to Color(0xFF151124),
                    1f to Color(0xFF0E0B1A)
                )
            )
            .border(1.4.dp, violet.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(violet.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = violet,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF7A7490),
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

