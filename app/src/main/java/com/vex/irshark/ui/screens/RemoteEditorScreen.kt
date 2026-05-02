package com.vex.irshark.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vex.irshark.data.DbIrCodeOption
import com.vex.irshark.data.FlipperProfile
import com.vex.irshark.data.SavedRemoteButton
import com.vex.irshark.ui.components.CategorySvgIcon
import com.vex.irshark.ui.components.EmptyCard

private val editorIconOptions = listOf(
    "TVs",
    "ACs",
    "Projectors",
    "DVD_Players",
    "Fans",
    "Cameras",
    "Consoles",
    "Audio_and_Video_Receivers",
    "Set_Top_Boxes",
    "Lights",
    "Microwaves",
    "Other"
)

@Composable
fun RemoteEditorScreen(
    remoteName: String,
    iconName: String?,
    buttons: List<SavedRemoteButton>,
    duplicateName: Boolean,
    onNameChange: (String) -> Unit,
    onIconChange: (String?) -> Unit,
    onAddButton: () -> Unit,
    onEditButton: (Int) -> Unit,
    onMoveButtonUp: (Int) -> Unit,
    onMoveButtonDown: (Int) -> Unit,
    onDeleteButton: (Int) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean
) {
    val violet = MaterialTheme.colorScheme.primary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F0D1A))
                    .border(1.dp, violet.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Remote", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = remoteName,
                        onValueChange = onNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Remote name") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                    )
                    if (duplicateName) {
                        Text("Name already exists in My Remotes.", color = Color(0xFFFF8A80), fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F0D1A))
                    .border(1.dp, violet.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Icon", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(editorIconOptions.size) { idx ->
                            val option = editorIconOptions[idx]
                            val selected = iconName == option
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) Color(0xFF2A1E4A) else Color(0xFF181327))
                                    .border(
                                        1.dp,
                                        if (selected) Color(0xFF9B6DFF) else Color.White.copy(alpha = 0.10f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onIconChange(option) }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CategorySvgIcon(name = option, tint = Color(0xFF9B6DFF), size = 18.dp)
                                    Text(
                                        text = option.replace('_', ' '),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F0D1A))
                    .border(1.dp, violet.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Buttons (${buttons.size})", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Button(
                            onClick = onAddButton,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1E4A))
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Color(0xFFB699FF))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Add", color = Color(0xFFE4D7FF))
                        }
                    }

                    if (buttons.isEmpty()) {
                        EmptyCard("Add at least one button.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            buttons.forEachIndexed { index, button ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF181327))
                                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(button.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Text(
                                            if (button.code.isBlank()) "No code" else button.code.take(56),
                                            color = Color(0xFF8A8899),
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Edit",
                                            tint = Color(0xFF9B6DFF),
                                            modifier = Modifier.size(18.dp).clickable { onEditButton(index) }
                                        )
                                        if (index > 0) {
                                            Icon(
                                                imageVector = Icons.Filled.KeyboardArrowUp,
                                                contentDescription = "Move up",
                                                tint = Color(0xFF8A8899),
                                                modifier = Modifier.size(18.dp).clickable { onMoveButtonUp(index) }
                                            )
                                        }
                                        if (index < buttons.lastIndex) {
                                            Icon(
                                                imageVector = Icons.Filled.KeyboardArrowDown,
                                                contentDescription = "Move down",
                                                tint = Color(0xFF8A8899),
                                                modifier = Modifier.size(18.dp).clickable { onMoveButtonDown(index) }
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete",
                                            tint = Color(0xFFFF7B9D),
                                            modifier = Modifier.size(18.dp).clickable { onDeleteButton(index) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF232033))
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun RemoteButtonEditorScreen(
    buttonLabel: String,
    buttonCode: String,
    profileSearch: String,
    selectedProfilePath: String?,
    selectedCodeIdx: Int,
    dbCodes: List<DbIrCodeOption>,
    loadingCodes: Boolean,
    filteredProfiles: List<FlipperProfile>,
    codeError: String?,
    onLabelChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onProfileSearchChange: (String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onSelectDbCode: (Int) -> Unit,
    onBack: () -> Unit,
    onApply: () -> Unit,
    canApply: Boolean
) {
    val violet = MaterialTheme.colorScheme.primary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F0D1A))
                    .border(1.dp, violet.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = buttonLabel,
                        onValueChange = onLabelChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Button name") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                    )

                    OutlinedTextField(
                        value = buttonCode,
                        onValueChange = onCodeChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        label = { Text("IR code payload") },
                        isError = codeError != null
                    )
                    if (codeError != null) {
                        Text(codeError!!, color = Color(0xFFFF8A80), fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F0D1A))
                    .border(1.dp, violet.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Import from database", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

                    OutlinedTextField(
                        value = profileSearch,
                        onValueChange = onProfileSearchChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search remote profile") }
                    )

                    if (filteredProfiles.isEmpty()) {
                        EmptyCard("No profile matched your query.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            filteredProfiles.forEach { profile ->
                                val selected = selectedProfilePath == profile.path
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) Color(0xFF2A1E4A) else Color(0xFF181327))
                                        .border(1.dp, Color.White.copy(alpha = if (selected) 0.24f else 0.10f), RoundedCornerShape(10.dp))
                                        .clickable { onSelectProfile(profile.path) }
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text(profile.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        Text(profile.parentPath, color = Color(0xFF8A8899), fontSize = 10.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }

                    when {
                        loadingCodes -> Text("Loading IR codes...", color = Color(0xFF8A8899), fontSize = 11.sp)
                        selectedProfilePath != null && dbCodes.isEmpty() -> EmptyCard("No IR entries found in selected profile.")
                        dbCodes.isNotEmpty() -> {
                            Text("Select button code", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                dbCodes.forEachIndexed { idx, item ->
                                    val selected = selectedCodeIdx == idx
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selected) Color(0xFF2A1E4A) else Color(0xFF181327))
                                            .border(1.dp, Color.White.copy(alpha = if (selected) 0.24f else 0.10f), RoundedCornerShape(10.dp))
                                            .clickable {
                                                onSelectDbCode(idx)
                                            }
                                            .padding(8.dp)
                                    ) {
                                        Column {
                                            Text(item.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                            Text(item.code.take(74), color = Color(0xFF8A8899), fontSize = 10.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF232033))
                ) {
                    Text("Back")
                }
                Button(
                    onClick = onApply,
                    enabled = canApply,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
