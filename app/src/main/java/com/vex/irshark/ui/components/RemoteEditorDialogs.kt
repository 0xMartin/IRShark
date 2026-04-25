package com.vex.irshark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vex.irshark.data.DbIrCodeOption
import com.vex.irshark.data.FlipperProfile
import com.vex.irshark.data.SavedRemoteButton
import com.vex.irshark.data.loadDbIrCodeOptions

@Composable
fun RemoteEditorDialog(
    initialName: String,
    initialButtons: List<SavedRemoteButton>,
    existingNames: Set<String>,
    originalName: String?,
    dbProfiles: List<FlipperProfile>,
    onDismiss: () -> Unit,
    onSave: (String, List<SavedRemoteButton>) -> Unit
) {
    var remoteName by remember { mutableStateOf(initialName) }
    var buttons by remember { mutableStateOf(initialButtons) }
    var editingButtonIndex by remember { mutableIntStateOf(-1) }
    var showButtonDialog by remember { mutableStateOf(false) }

    val normalizedName = remoteName.trim()
    val originalLower = originalName?.trim()?.lowercase().orEmpty()
    val duplicateName = normalizedName.isNotBlank() &&
        existingNames.any { it.lowercase() == normalizedName.lowercase() && it.lowercase() != originalLower }
    val canSave = normalizedName.isNotBlank() && !duplicateName && buttons.isNotEmpty()

    if (showButtonDialog) {
        val existing = if (editingButtonIndex in buttons.indices) buttons[editingButtonIndex] else null
        IrButtonEditorDialog(
            initialButton = existing,
            dbProfiles = dbProfiles,
            onDismiss = { showButtonDialog = false },
            onSave = { updated ->
                buttons = if (editingButtonIndex in buttons.indices) {
                    buttons.toMutableList().also { it[editingButtonIndex] = updated }
                } else {
                    buttons + updated
                }
                showButtonDialog = false
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF121024))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Remote Editor", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = remoteName,
                    onValueChange = { remoteName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Remote name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )

                if (duplicateName) {
                    Text(
                        text = "Name already exists in My Remotes.",
                        color = Color(0xFFFF8A80),
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = "Buttons (${buttons.size})",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (buttons.isEmpty()) {
                    EmptyCard("Add at least one button.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 230.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(buttons.indices.toList()) { index ->
                            val button = buttons[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF181327))
                                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(button.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    val codePreview = if (button.code.isBlank()) "No code" else button.code.take(42)
                                    Text(codePreview, color = Color(0xFF8A8899), fontSize = 10.sp, maxLines = 1)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Edit",
                                        tint = Color(0xFF9B6DFF),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                editingButtonIndex = index
                                                showButtonDialog = true
                                            }
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        tint = Color(0xFFFF7B9D),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                buttons = buttons.toMutableList().also { it.removeAt(index) }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        editingButtonIndex = -1
                        showButtonDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1E4A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.LibraryAdd, contentDescription = null, tint = Color(0xFFB699FF))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Add button", color = Color(0xFFE4D7FF))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            onSave(normalizedName, buttons)
                        }
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun IrButtonEditorDialog(
    initialButton: SavedRemoteButton?,
    dbProfiles: List<FlipperProfile>,
    onDismiss: () -> Unit,
    onSave: (SavedRemoteButton) -> Unit
) {
    val context = LocalContext.current
    val defaultCode = "protocol=NEC; address=0x00FF; command=0x20DF"

    var label by remember { mutableStateOf(initialButton?.label ?: "POWER") }
    var code by remember { mutableStateOf(initialButton?.code ?: defaultCode) }

    var profileSearch by remember { mutableStateOf("") }
    var selectedProfilePath by remember { mutableStateOf<String?>(null) }
    var selectedCodeIdx by remember { mutableIntStateOf(-1) }
    var dbCodes by remember { mutableStateOf(listOf<DbIrCodeOption>()) }
    var loadingCodes by remember { mutableStateOf(false) }

    val filteredProfiles = remember(profileSearch, dbProfiles) {
        dbProfiles.filter {
            profileSearch.isBlank() ||
                it.name.contains(profileSearch, ignoreCase = true) ||
                it.parentPath.contains(profileSearch, ignoreCase = true)
        }.take(30)
    }

    LaunchedEffect(selectedProfilePath) {
        val path = selectedProfilePath
        if (path.isNullOrBlank()) {
            dbCodes = emptyList()
            selectedCodeIdx = -1
            return@LaunchedEffect
        }
        loadingCodes = true
        dbCodes = loadDbIrCodeOptions(context, path)
        selectedCodeIdx = -1
        loadingCodes = false
    }

    val canSave = label.trim().isNotBlank() && code.trim().isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF121024))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Button & IR Code", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Button name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp),
                    label = { Text("IR code payload") }
                )

                Text("Import code from database", color = Color(0xFFB699FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = profileSearch,
                    onValueChange = { profileSearch = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search remote profile") }
                )

                if (filteredProfiles.isEmpty()) {
                    EmptyCard("No profile matched your query.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredProfiles) { profile ->
                            val selected = selectedProfilePath == profile.path
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) Color(0xFF2A1E4A) else Color(0xFF181327))
                                    .border(1.dp, Color.White.copy(alpha = if (selected) 0.24f else 0.10f), RoundedCornerShape(10.dp))
                                    .clickable { selectedProfilePath = profile.path }
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
                    loadingCodes -> {
                        Text("Loading IR codes...", color = Color(0xFF8A8899), fontSize = 11.sp)
                    }
                    selectedProfilePath != null && dbCodes.isEmpty() -> {
                        EmptyCard("No IR entries found in selected profile.")
                    }
                    dbCodes.isNotEmpty() -> {
                        Text("Select button code", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(dbCodes.indices.toList()) { idx ->
                                val item = dbCodes[idx]
                                val selected = selectedCodeIdx == idx
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) Color(0xFF2A1E4A) else Color(0xFF181327))
                                        .border(1.dp, Color.White.copy(alpha = if (selected) 0.24f else 0.10f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedCodeIdx = idx
                                            label = item.label
                                            code = item.code
                                        }
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text(item.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        Text(item.code.take(70), color = Color(0xFF8A8899), fontSize = 10.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            onSave(
                                SavedRemoteButton(
                                    label = label.trim(),
                                    code = code.trim()
                                )
                            )
                        }
                    ) { Text("Apply") }
                }
            }
        }
    }
}
