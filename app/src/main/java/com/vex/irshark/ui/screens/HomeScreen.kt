package com.vex.irshark.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vex.irshark.R

@Composable
fun HomeScreen(
    onUniversal: () -> Unit,
    onMyRemotes: () -> Unit,
    onRemoteDb:  () -> Unit,
    onSettings:  () -> Unit,
    onMacros:    () -> Unit = {},
    onIrFinder: () -> Unit = {}
) {
    val sharkColor = Color(0xFF9B6DFF)
    Box(modifier = Modifier.fillMaxSize()) {
        // Decorative shark textures
        Image(
            painter = painterResource(id = R.drawable.shark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(sharkColor, BlendMode.SrcIn),
            modifier = Modifier
                .size(252.dp)
                .align(Alignment.BottomCenter)
                .offset(y = (-62).dp)
                .rotate(-20f)
                .alpha(0.07f)
        )
        Image(
            painter = painterResource(id = R.drawable.shark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(sharkColor, BlendMode.SrcIn),
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopStart)
                .rotate(165f)
                .alpha(0.04f)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeGridCard("Universal Remote", Icons.Filled.SettingsRemote, onUniversal, Modifier.weight(1f))
                HomeGridCard("My Remotes", Icons.Filled.Folder, onMyRemotes, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeGridCard("Remote DB", Icons.Filled.Storage, onRemoteDb, Modifier.weight(1f))
                HomeGridCard("Macros", Icons.Filled.AutoAwesome, onMacros, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeGridCard("IR Finder", Icons.Filled.FindInPage, onIrFinder, Modifier.weight(1f))
                HomeGridCard("Settings", Icons.Filled.Settings, onSettings, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HomeGridCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val violet = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .height(130.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF100D1C))
            .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(violet.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = violet,
                    modifier = Modifier.size(39.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
