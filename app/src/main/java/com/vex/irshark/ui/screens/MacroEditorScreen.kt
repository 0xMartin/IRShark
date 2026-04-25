package com.vex.irshark.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vex.irshark.data.FlipperProfile
import com.vex.irshark.data.MacroStep
import com.vex.irshark.data.SavedMacro
import com.vex.irshark.data.SavedRemote
import com.vex.irshark.data.loadDbIrCodeOptions
import kotlinx.coroutines.launch
import java.util.UUID

private enum class StepType(val label: String, val color: Color) {
    IR_SEND     ("IR Send",          Color(0xFF7B4DDF)),
    DELAY       ("Delay",            Color(0xFF2E7ADB)),
    SHOW_TEXT   ("Show Text",        Color(0xFF1E8A5E)),
    WAIT_CONFIRM("Wait for OK",      Color(0xFFC27C1A)),
    REPEAT      ("Repeat N times",   Color(0xFFA07800)),
    LOOP        ("Loop until stop",  Color(0xFFB84000)),
    IF_CONFIRM  ("If user confirms", Color(0xFF1E8AA8))
}

@Composable
fun MacroEditorScreen(
    dbProfiles:    List<FlipperProfile>,
    savedRemotes:  List<SavedRemote>,
    initialMacro:  SavedMacro?,
    existingNames: Set<String>,
    onSave:        (SavedMacro) -> Unit,
    onDismiss:     () -> Unit
) {
    var macroName by remember { mutableStateOf(initialMacro?.name ?: "") }
    var steps     by remember { mutableStateOf(initialMacro?.steps?.toMutableList() ?: mutableListOf<MacroStep>()) }
    var nameError by remember { mutableStateOf(false) }

    var showTypePickerDialog by remember { mutableStateOf(false) }
    var pendingStepType      by remember { mutableStateOf<StepType?>(null) }
    var editingIndex         by remember { mutableStateOf<Int?>(null) }
    var showIrPicker         by remember { mutableStateOf(false) }
    var irPickCallback       by remember { mutableStateOf<((String, String, String) -> Unit)?>(null) }
    var pendingDeleteIndex   by remember { mutableStateOf<Int?>(null) }

    val violet = MaterialTheme.colorScheme.primary

    if (showIrPicker) {
        IrPickerDialog(
            savedRemotes = savedRemotes,
            dbProfiles   = dbProfiles,
            onDismiss    = { showIrPicker = false; irPickCallback = null; pendingStepType = null; editingIndex = null },
            onPick       = { remoteName, buttonLabel, irCode ->
                irPickCallback?.invoke(remoteName, buttonLabel, irCode)
                showIrPicker  = false
                irPickCallback = null
            }
        )
    }

    pendingDeleteIndex?.let { delIdx ->
        AlertDialog(
            onDismissRequest = { pendingDeleteIndex = null },
            title = { Text("Remove step") },
            text  = { Text("Remove this step?") },
            confirmButton = {
                TextButton(onClick = {
                    steps = steps.toMutableList().also { it.removeAt(delIdx) }
                    pendingDeleteIndex = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIndex = null }) { Text("Cancel") }
            }
        )
    }

    if (showTypePickerDialog) {
        StepTypePickerDialog(
            onDismiss = { showTypePickerDialog = false },
            onPick    = { type ->
                showTypePickerDialog = false
                editingIndex = null
                if (type == StepType.IR_SEND) {
                    showIrPicker = true
                    irPickCallback = { remoteName, buttonLabel, irCode ->
                        steps = steps.toMutableList().also {
                            it.add(MacroStep.IrSend(
                                displayLabel = "$remoteName / $buttonLabel",
                                remoteName   = remoteName,
                                buttonLabel  = buttonLabel,
                                irCode       = irCode
                            ))
                        }
                    }
                } else {
                    pendingStepType = type
                }
            }
        )
    }

    pendingStepType?.let { type ->
        StepConfigDialog(
            type        = type,
            editingStep = editingIndex?.let { steps.getOrNull(it) },
            onDismiss   = { pendingStepType = null; editingIndex = null },
            onSaveStep  = { newStep ->
                val idx = editingIndex
                steps = steps.toMutableList().also {
                    if (idx != null && idx in it.indices) it[idx] = newStep
                    else it.add(newStep)
                }
                pendingStepType = null
                editingIndex    = null
            }
        )
    }

    Column(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value          = macroName,
            onValueChange  = { macroName = it; nameError = false },
            modifier       = Modifier.fillMaxWidth(),
            singleLine     = true,
            isError        = nameError,
            label          = { Text("Macro name") },
            supportingText = if (nameError) ({ Text("Name is required") }) else null
        )

        if (steps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF100D1C))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("No steps yet. Tap + to add a step.", color = Color(0xFF8A8899), fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(steps) { index, step ->
                    StepRow(
                        index      = index,
                        step       = step,
                        isFirst    = index == 0,
                        isLast     = index == steps.lastIndex,
                        onMoveUp   = {
                            steps = steps.toMutableList().also {
                                val tmp = it[index - 1]; it[index - 1] = it[index]; it[index] = tmp
                            }
                        },
                        onMoveDown = {
                            steps = steps.toMutableList().also {
                                val tmp = it[index + 1]; it[index + 1] = it[index]; it[index] = tmp
                            }
                        },
                        onEdit = {
                            editingIndex = index
                            if (step is MacroStep.IrSend) {
                                showIrPicker = true
                                irPickCallback = { remoteName, buttonLabel, irCode ->
                                    steps = steps.toMutableList().also {
                                        it[index] = MacroStep.IrSend(
                                            displayLabel = "$remoteName / $buttonLabel",
                                            remoteName   = remoteName,
                                            buttonLabel  = buttonLabel,
                                            irCode       = irCode
                                        )
                                    }
                                    editingIndex = null
                                }
                            } else {
                                pendingStepType = stepToType(step)
                            }
                        },
                        onDelete = { pendingDeleteIndex = index }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(violet.copy(alpha = 0.14f))
                .border(1.dp, violet.copy(alpha = 0.50f), RoundedCornerShape(10.dp))
                .clickable { showTypePickerDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, null, tint = violet, modifier = Modifier.size(16.dp))
                Text("Add step", color = violet, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(modifier = Modifier.weight(1f), onClick = onDismiss) { Text("Cancel") }
            Box(
                modifier = Modifier
                    .weight(2f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(violet.copy(alpha = 0.28f))
                    .border(1.dp, violet, RoundedCornerShape(10.dp))
                    .clickable {
                        if (macroName.isBlank()) { nameError = true; return@clickable }
                        onSave(SavedMacro(
                            id    = initialMacro?.id ?: UUID.randomUUID().toString(),
                            name  = macroName.trim(),
                            steps = steps
                        ))
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = violet, modifier = Modifier.size(16.dp))
                    Text("Save macro", color = violet, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun StepRow(
    index: Int, step: MacroStep,
    isFirst: Boolean, isLast: Boolean,
    onMoveUp: () -> Unit, onMoveDown: () -> Unit,
    onEdit: () -> Unit, onDelete: () -> Unit
) {
    val typeColor = stepColor(step)
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(typeColor.copy(alpha = 0.20f))
                .border(1.dp, typeColor.copy(alpha = 0.50f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("${index + 1}", color = typeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF100D1C))
                .border(1.dp, typeColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .clickable(onClick = onEdit)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(stepEmoji(step) + "  " + stepDescription(step), color = Color.White, fontSize = 12.sp, maxLines = 2)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SmallIconBtn(Icons.Filled.KeyboardArrowUp,   Color(0xFF8A8899), enabled = !isFirst) { onMoveUp() }
            SmallIconBtn(Icons.Filled.KeyboardArrowDown, Color(0xFF8A8899), enabled = !isLast)  { onMoveDown() }
        }
        SmallIconBtn(Icons.Filled.Delete, Color(0xFFFF7B9D)) { onDelete() }
    }
}

@Composable
private fun SmallIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color, enabled: Boolean = true, onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1A1726))
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.14f else 0.05f), RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (enabled) tint else tint.copy(alpha = 0.25f), modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun StepTypePickerDialog(onDismiss: () -> Unit, onPick: (StepType) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF121024))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Add step", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            StepType.entries.forEach { type ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(type.color.copy(alpha = 0.12f))
                        .border(1.dp, type.color.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
                        .clickable { onPick(type) }
                        .padding(12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(type.color)
                    )
                    Text(type.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            TextButton(modifier = Modifier.align(Alignment.End), onClick = onDismiss) { Text("Cancel") }
        }
    }
}

@Composable
private fun StepConfigDialog(
    type:        StepType,
    editingStep: MacroStep?,
    onDismiss:   () -> Unit,
    onSaveStep:  (MacroStep) -> Unit
) {
    var delayMs   by remember { mutableStateOf(((editingStep as? MacroStep.Delay)?.ms ?: 500L).toString()) }
    var showText  by remember { mutableStateOf((editingStep as? MacroStep.ShowText)?.text ?: "") }
    var waitMsg   by remember { mutableStateOf((editingStep as? MacroStep.WaitConfirm)?.message ?: "Press OK to continue") }
    var repeatCnt by remember { mutableStateOf(((editingStep as? MacroStep.RepeatBlock)?.count ?: 3).toString()) }
    var ifMsg     by remember { mutableStateOf((editingStep as? MacroStep.IfConfirm)?.message ?: "Continue?") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF121024))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(type.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            when (type) {
                StepType.DELAY -> {
                    OutlinedTextField(
                        value = delayMs, onValueChange = { delayMs = it },
                        label = { Text("Duration (ms)") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Text("500 = 0.5 sec, 2000 = 2 sec", color = Color(0xFF8A8899), fontSize = 11.sp)
                }
                StepType.SHOW_TEXT -> {
                    OutlinedTextField(
                        value = showText, onValueChange = { showText = it },
                        label = { Text("Message to display") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
                StepType.WAIT_CONFIRM -> {
                    OutlinedTextField(
                        value = waitMsg, onValueChange = { waitMsg = it },
                        label = { Text("Prompt message") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
                StepType.REPEAT -> {
                    OutlinedTextField(
                        value = repeatCnt, onValueChange = { repeatCnt = it },
                        label = { Text("Repeat count") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                StepType.LOOP -> {
                    Text("Loops indefinitely until user stops the macro.", color = Color(0xFF8A8899), fontSize = 12.sp)
                }
                StepType.IF_CONFIRM -> {
                    OutlinedTextField(
                        value = ifMsg, onValueChange = { ifMsg = it },
                        label = { Text("Question for user") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
                StepType.IR_SEND -> {}
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = {
                    val step: MacroStep = when (type) {
                        StepType.DELAY        -> MacroStep.Delay(delayMs.toLongOrNull()?.coerceAtLeast(1) ?: 500L)
                        StepType.SHOW_TEXT    -> MacroStep.ShowText(showText.ifBlank { "Hello!" })
                        StepType.WAIT_CONFIRM -> MacroStep.WaitConfirm(waitMsg.ifBlank { "Press OK to continue" })
                        StepType.REPEAT       -> MacroStep.RepeatBlock(
                            count = repeatCnt.toIntOrNull()?.coerceAtLeast(1) ?: 3,
                            steps = (editingStep as? MacroStep.RepeatBlock)?.steps ?: emptyList()
                        )
                        StepType.LOOP         -> MacroStep.LoopUntilStop(
                            steps = (editingStep as? MacroStep.LoopUntilStop)?.steps ?: emptyList()
                        )
                        StepType.IF_CONFIRM   -> MacroStep.IfConfirm(
                            message  = ifMsg.ifBlank { "Continue?" },
                            yesSteps = (editingStep as? MacroStep.IfConfirm)?.yesSteps ?: emptyList(),
                            noSteps  = (editingStep as? MacroStep.IfConfirm)?.noSteps  ?: emptyList()
                        )
                        StepType.IR_SEND -> return@TextButton
                    }
                    onSaveStep(step)
                }) { Text(if (editingStep == null) "Add" else "Save") }
            }
        }
    }
}

@Composable
internal fun IrPickerDialog(
    savedRemotes: List<SavedRemote>,
    dbProfiles:   List<FlipperProfile>,
    onDismiss:    () -> Unit,
    onPick:       (remoteName: String, buttonLabel: String, irCode: String) -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val violet  = MaterialTheme.colorScheme.primary

    var tab          by remember { mutableIntStateOf(0) }
    var query        by remember { mutableStateOf("") }
    var myRemoteIdx  by remember { mutableIntStateOf(-1) }
    var dbProfileIdx by remember { mutableIntStateOf(-1) }
    var dbButtons    by remember { mutableStateOf(listOf<com.vex.irshark.data.DbIrCodeOption>()) }
    var loadingDb    by remember { mutableStateOf(false) }

    LaunchedEffect(tab, dbProfileIdx) {
        if (tab == 1 && dbProfileIdx >= 0) {
            val profile = dbProfiles.getOrNull(dbProfileIdx) ?: return@LaunchedEffect
            loadingDb = true
            dbButtons = loadDbIrCodeOptions(context, profile.path)
            loadingDb = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF121024))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Pick IR Button", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)

            TabRow(selectedTabIndex = tab, containerColor = Color(0xFF1A1530), contentColor = violet) {
                Tab(selected = tab == 0, onClick = { tab = 0; query = ""; myRemoteIdx = -1 },
                    text = { Text("My Remotes", fontSize = 12.sp) })
                Tab(selected = tab == 1, onClick = { tab = 1; query = ""; dbProfileIdx = -1; dbButtons = emptyList() },
                    text = { Text("Remote DB", fontSize = 12.sp) })
            }

            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(if (tab == 0) "Search remotes" else "Search profiles") }
            )

            if (tab == 0) {
                val filtered = savedRemotes.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                if (myRemoteIdx < 0) {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(filtered) { _, remote ->
                            PickerRow(remote.name, "${remote.buttons.size} buttons") {
                                myRemoteIdx = savedRemotes.indexOf(remote)
                            }
                        }
                    }
                } else {
                    val remote = savedRemotes.getOrNull(myRemoteIdx)
                    if (remote != null) {
                        Text("<- ${remote.name}", color = violet, fontSize = 12.sp,
                            modifier = Modifier.clickable { myRemoteIdx = -1 })
                        val filteredBtns = remote.buttons.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsIndexed(filteredBtns) { _, btn ->
                                val hasCode = btn.code.isNotBlank()
                                PickerRow(btn.label, if (hasCode) btn.code.take(50) else "No IR code", enabled = hasCode) {
                                    if (hasCode) onPick(remote.name, btn.label, btn.code)
                                }
                            }
                        }
                    }
                }
            } else {
                val filteredProfiles = dbProfiles.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }.take(200)
                if (dbProfileIdx < 0) {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(filteredProfiles) { _, profile ->
                            PickerRow(profile.name, profile.parentPath) { dbProfileIdx = dbProfiles.indexOf(profile) }
                        }
                    }
                } else {
                    val profile = dbProfiles.getOrNull(dbProfileIdx)
                    if (profile != null) {
                        Text("<- ${profile.name}", color = violet, fontSize = 12.sp,
                            modifier = Modifier.clickable { dbProfileIdx = -1; dbButtons = emptyList() })
                        if (loadingDb) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text("Loading...", color = Color(0xFF8A8899), fontSize = 12.sp)
                            }
                        } else {
                            val filteredBtns = dbButtons.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                itemsIndexed(filteredBtns) { _, opt ->
                                    PickerRow(opt.label, opt.code.take(60)) { onPick(profile.name, opt.label, opt.code) }
                                }
                            }
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun PickerRow(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) Color(0xFF181327) else Color(0xFF120F20))
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.10f else 0.05f), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(10.dp)
    ) {
        Column {
            Text(title,    color = if (enabled) Color.White else Color(0xFF5A5870), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color(0xFF8A8899), fontSize = 10.sp, maxLines = 1)
        }
    }
}

private fun stepToType(step: MacroStep): StepType = when (step) {
    is MacroStep.IrSend        -> StepType.IR_SEND
    is MacroStep.Delay         -> StepType.DELAY
    is MacroStep.ShowText      -> StepType.SHOW_TEXT
    is MacroStep.WaitConfirm   -> StepType.WAIT_CONFIRM
    is MacroStep.RepeatBlock   -> StepType.REPEAT
    is MacroStep.LoopUntilStop -> StepType.LOOP
    is MacroStep.IfConfirm     -> StepType.IF_CONFIRM
}

private fun stepColor(step: MacroStep): Color = when (step) {
    is MacroStep.IrSend        -> Color(0xFF7B4DDF)
    is MacroStep.Delay         -> Color(0xFF2E7ADB)
    is MacroStep.ShowText      -> Color(0xFF1E8A5E)
    is MacroStep.WaitConfirm   -> Color(0xFFC27C1A)
    is MacroStep.RepeatBlock   -> Color(0xFFA07800)
    is MacroStep.LoopUntilStop -> Color(0xFFB84000)
    is MacroStep.IfConfirm     -> Color(0xFF1E8AA8)
}

private fun stepEmoji(step: MacroStep): String = when (step) {
    is MacroStep.IrSend        -> "[IR]"
    is MacroStep.Delay         -> "[Wait]"
    is MacroStep.ShowText      -> "[Msg]"
    is MacroStep.WaitConfirm   -> "[OK?]"
    is MacroStep.RepeatBlock   -> "[Rpt]"
    is MacroStep.LoopUntilStop -> "[Loop]"
    is MacroStep.IfConfirm     -> "[If]"
}

private fun stepDescription(step: MacroStep): String = when (step) {
    is MacroStep.IrSend        -> "Send IR: ${step.displayLabel}"
    is MacroStep.Delay         -> "Wait ${step.ms} ms"
    is MacroStep.ShowText      -> "Show: \"${step.text}\""
    is MacroStep.WaitConfirm   -> "Wait OK: \"${step.message}\""
    is MacroStep.RepeatBlock   -> "Repeat ${step.count}x (${step.steps.size} inner)"
    is MacroStep.LoopUntilStop -> "Loop forever (${step.steps.size} inner)"
    is MacroStep.IfConfirm     -> "If: \"${step.message}\""
}
