package com.vex.irshark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.view.HapticFeedbackConstants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vex.irshark.ui.components.BackIconButton
import com.vex.irshark.ui.components.Badge
import com.vex.irshark.ui.components.CategorySvgIcon
import com.vex.irshark.ui.components.RemoteCommandButton

@Composable
fun RemoteControlScreen(
    title: String,
    deviceIconName: String?,
    typeBadge: String,
    countBadge: String,
    commands: List<String>,
    selectedCommand: String?,
    txCount: Int,
    onBack: () -> Unit,
    onCommandClick: (String) -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    showSaveButton: Boolean,
    showEditButton: Boolean,
    hapticEnabled: Boolean = true,
    onShare: (() -> Unit)? = null
) {
    val violet = MaterialTheme.colorScheme.primary
    var flashedCommand by remember { mutableStateOf<String?>(null) }
    var columnCount by rememberSaveable { mutableIntStateOf(2) }
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top navigation row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BackIconButton(onClick = onBack, modifier = Modifier.size(40.dp))
            if (showSaveButton) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(violet.copy(alpha = 0.15f))
                        .border(1.dp, violet.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onSave),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Add", color = violet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Device info header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F0D1A))
                .border(1.dp, violet.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!deviceIconName.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(violet.copy(alpha = 0.12f))
                                .border(1.dp, violet.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            CategorySvgIcon(
                                name = deviceIconName,
                                tint = violet,
                                size = 20.dp
                            )
                        }
                    }
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (showEditButton) {
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(violet.copy(alpha = 0.14f))
                                .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .clickable(onClick = onEdit)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Edit", color = violet, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (onShare != null) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(violet.copy(alpha = 0.14f))
                                .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .clickable(onClick = onShare),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = "Share",
                                tint = violet,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge(typeBadge)
                    Badge(countBadge)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Controls",
                    color = violet,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(1 to Icons.Filled.ViewList, 2 to Icons.Filled.GridView).forEach { (cols, icon) ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (columnCount == cols) violet.copy(alpha = 0.18f) else Color.Transparent)
                                .clickable { columnCount = cols },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (columnCount == cols) violet else Color(0xFF8A8899),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Command buttons grid
            commands.chunked(columnCount).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { cmd ->
                        val isFlashed = flashedCommand == cmd
                        RemoteCommandButton(
                            label = cmd,
                            countLabel = "x1",
                            isActive = isFlashed,
                            onClick = {
                                if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                flashedCommand = cmd
                                scope.launch { delay(220); flashedCommand = null }
                                onCommandClick(cmd)
                            },
                            modifier = Modifier
                                .weight(1f)
                        )
                    }
                    repeat(columnCount - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
