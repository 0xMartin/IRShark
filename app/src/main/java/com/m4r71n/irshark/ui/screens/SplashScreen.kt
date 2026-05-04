package com.m4r71n.irshark.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m4r71n.irshark.R
import kotlin.math.roundToInt

@Composable
fun SplashScreen(
    loadedFiles: Int = 0,
    totalFiles: Int = 0
) {
    val violet = MaterialTheme.colorScheme.primary
    val progress = remember(loadedFiles, totalFiles) {
        if (totalFiles > 0) {
            (loadedFiles.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    val pulse = rememberInfiniteTransition(label = "logo-pulse")
    val logoAlpha = pulse.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo-alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08060F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Image(
                painter = painterResource(R.drawable.app_icon),
                contentDescription = "IRShark",
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .graphicsLayer { alpha = logoAlpha.value }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "IRShark",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Loading IR database…",
                color = Color(0xFF8A8899),
                fontSize = 12.sp
            )

            if (totalFiles > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$loadedFiles / $totalFiles files",
                    color = Color(0xFF8A8899),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .width(200.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = violet,
                trackColor = violet.copy(alpha = 0.18f)
            )

            if (totalFiles > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${(progress * 100f).roundToInt()}%",
                    color = Color(0xFF8A8899),
                    fontSize = 11.sp
                )
            }
        }
    }
}
