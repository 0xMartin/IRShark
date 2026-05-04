package com.m4r71n.irshark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.AlertDialog
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
import com.m4r71n.irshark.data.DbIrCodeOption
import com.m4r71n.irshark.data.FlipperProfile
import com.m4r71n.irshark.data.SavedRemoteButton
import com.m4r71n.irshark.data.loadDbIrCodeOptions

@Composable
fun RemoteEditorDialog(
    initialName: String,
    initialButtons: List<SavedRemoteButton>,
    initialIconName: String?,
    existingNames: Set<String>,
    originalName: String?,
    dbProfiles: List<FlipperProfile>,
    onDismiss: () -> Unit,
    onSave: (String, List<SavedRemoteButton>, String?) -> Unit
) {
    var remoteName by remember { mutableStateOf(initialName) }
    var buttons by remember { mutableStateOf(initialButtons) }
    var iconName by remember { mutableStateOf(initialIconName) }
    var editingButtonIndex by remember { mutableIntStateOf(-1) }
    var showButtonDialog by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val isDirty = remoteName != initialName || buttons != initialButtons || iconName != initialIconName

    fun requestDismiss() {
        if (isDirty) showDiscardConfirm = true else onDismiss()
    }

    val normalizedName = remoteName.trim()
    val originalLower = originalName?.trim()?.lowercase().orEmpty()
    val duplicateName = normalizedName.isNotBlank() &&
        existingNames.any { it.lowercase() == normalizedName.lowercase() && it.lowercase() != originalLower }
    val canSave = normalizedName.isNotBlank() && !duplicateName && buttons.isNotEmpty()
    val iconOptions = listOf(
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

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes. Discard them?") },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; onDismiss() }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("Keep editing") }
            }
        )
    }

    Dialog(onDismissRequest = { requestDismiss() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF121024))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 592.dp)) {
                // --- Fixed header ---
                Text("Remote Editor", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                // --- Scrollable content ---
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

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
                    text = "Icon",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(iconOptions) { option ->
                        val selected = iconName == option
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Color(0xFF2A1E4A) else Color(0xFF181327))
                                .border(
                                    1.dp,
                                    if (selected) Color(0xFF9B6DFF) else Color.White.copy(alpha = 0.10f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { iconName = option }
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
                                    if (index > 0) {
                                        Icon(
                                            imageVector = Icons.Filled.KeyboardArrowUp,
                                            contentDescription = "Move up",
                                            tint = Color(0xFF8A8899),
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable {
                                                    buttons = buttons.toMutableList().also {
                                                        val tmp = it[index - 1]; it[index - 1] = it[index]; it[index] = tmp
                                                    }
                                                }
                                        )
                                    }
                                    if (index < buttons.lastIndex) {
                                        Icon(
                                            imageVector = Icons.Filled.KeyboardArrowDown,
                                            contentDescription = "Move down",
                                            tint = Color(0xFF8A8899),
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable {
                                                    buttons = buttons.toMutableList().also {
                                                        val tmp = it[index + 1]; it[index + 1] = it[index]; it[index] = tmp
                                                    }
                                                }
                                        )
                                    }
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
                } // end scrollable content column

                // --- Fixed footer — always visible ---
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { requestDismiss() }) { Text("Cancel") }
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            onSave(normalizedName, buttons, iconName)
                        }
                    ) { Text("Save") }
                }
            } // end outer column
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
    var codeError by remember { mutableStateOf<String?>(null) }

    // Validates IR payload formats used by the app:
    // 1) key=value pairs (semicolon/newline separated), including DB-generated
    //    type=raw; frequency=...; data=... and type=parsed; protocol=...; ...
    // 2) raw space-separated integers (legacy quick paste)
    fun validateIrCode(raw: String): String? {
        val s = raw.trim()
        if (s.isBlank()) return "Code cannot be empty"

        // raw: space-separated integers (positive+negative), at least 4 values
        val rawPattern = Regex("""^-?\d+(\s+-?\d+){3,}$""")
        if (rawPattern.matches(s)) return null

        // Parse semicolon/newline-separated key=value payloads.
        val pairs = s
            .split(';', '\n')
            .mapNotNull { segment ->
                val token = segment.trim()
                if (token.isEmpty()) return@mapNotNull null
                val idx = token.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                token.substring(0, idx).trim().lowercase() to token.substring(idx + 1).trim()
            }

        if (pairs.isEmpty()) {
            return "Invalid IR code format. Use key=value pairs (e.g. type=raw; data=...) or raw integers."
        }

        val fields = pairs.toMap()
        when (fields["type"]?.lowercase()) {
            "raw" -> {
                if (fields["data"].isNullOrBlank()) {
                    return "RAW code must contain data=..."
                }
            }
            "parsed" -> {
                if (fields["protocol"].isNullOrBlank()) {
                    return "Parsed code must contain protocol=..."
                }
                // address/command can be empty for some profiles; allow save.
            }
        }

        return null
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF121024))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 592.dp)
            ) {
                // --- Fixed header ---
                Text("Button & IR Code", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                // --- Scrollable content ---
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

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
                    onValueChange = { code = it; codeError = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp),
                    label = { Text("IR code payload") },
                    isError = codeError != null
                )
                if (codeError != null) {
                    Text(
                        text = codeError!!,
                        color = Color(0xFFFF8A80),
                        fontSize = 11.sp
                    )
                }

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
                            .heightIn(min = 80.dp, max = 160.dp),
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
                                .heightIn(min = 80.dp, max = 160.dp),
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

                } // end scrollable content column

                // --- Fixed footer ---
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            val err = validateIrCode(code)
                            if (err != null) {
                                codeError = err
                            } else {
                                onSave(
                                    SavedRemoteButton(
                                        label = label.trim(),
                                        code = code.trim()
                                    )
                                )
                            }
                        }
                    ) { Text("Apply") }
                }
            }
        }
    }
}
