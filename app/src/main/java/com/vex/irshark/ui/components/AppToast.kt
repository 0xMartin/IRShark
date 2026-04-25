package com.vex.irshark.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableSharedFlow

class AppToastController {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun show(message: String) {
        _events.tryEmit(message)
    }

    internal fun events(): MutableSharedFlow<String> = _events
}

@Composable
fun AppToastHost(
    controller: AppToastController,
    modifier: Modifier = Modifier,
    durationMs: Long = 1700
) {
    var message by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    val violet = MaterialTheme.colorScheme.primary

    LaunchedEffect(controller) {
        controller.events().collect { next ->
            message = next
            visible = true
            kotlinx.coroutines.delay(durationMs)
            visible = false
        }
    }

    Box(modifier = modifier.wrapContentSize(align = Alignment.BottomCenter)) {
        AnimatedVisibility(
            visible = visible && message != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF18142A))
                    .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.orEmpty(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
