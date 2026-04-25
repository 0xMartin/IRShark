package com.vex.irshark.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vex.irshark.data.SavedMacro
import com.vex.irshark.data.countMacroSteps
import com.vex.irshark.ui.components.EmptyCard

@Composable
fun MacroListScreen(
    macros:   List<SavedMacro>,
    onPlay:   (SavedMacro) -> Unit,
    onEdit:   (SavedMacro) -> Unit,
    onDelete: (SavedMacro) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (macros.isEmpty()) {
            EmptyCard("No macros yet. Tap + to create one.")
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxWidth().height(500.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(macros) { macro ->
                    MacroRow(macro = macro, onPlay = onPlay, onEdit = onEdit, onDelete = onDelete)
                }
            }
        }
    }
}

@Composable
private fun MacroRow(
    macro:    SavedMacro,
    onPlay:   (SavedMacro) -> Unit,
    onEdit:   (SavedMacro) -> Unit,
    onDelete: (SavedMacro) -> Unit
) {
    val violet   = MaterialTheme.colorScheme.primary
    val stepCount = countMacroSteps(macro.steps)

    Column {
        Row(
            modifier            = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment   = Alignment.CenterVertically
        ) {
            // Title + subtitle
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .padding(6.dp)
            ) {
                Text(macro.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(
                    "$stepCount step${if (stepCount != 1) "s" else ""}",
                    color = Color(0xFF8A8899), fontSize = 10.sp
                )
            }

            // Play button  (green accent)
            MacroActionBox(icon = Icons.Filled.PlayArrow, tint = Color(0xFF5BFF9A),
                bg = Color(0x225BFF9A), border = Color(0xFF5BFF9A)) { onPlay(macro) }

            // Edit button
            MacroActionBox(icon = Icons.Filled.Edit, tint = violet,
                bg = violet.copy(alpha = 0.18f), border = violet) { onEdit(macro) }

            // Delete button
            MacroActionBox(icon = Icons.Filled.Delete, tint = Color(0xFFFF7B9D),
                bg = Color(0xFF1A1726), border = Color.White.copy(alpha = 0.18f)) { onDelete(macro) }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
    }
}

@Composable
private fun MacroActionBox(
    icon:   ImageVector,
    tint:   Color,
    bg:     Color,
    border: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
    }
}
