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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vex.irshark.data.FlipperProfile
import com.vex.irshark.data.SavedMacro
import com.vex.irshark.data.SavedRemote
import com.vex.irshark.data.loadDbIrCodeOptions
import com.vex.irshark.ui.macro.BlockParams
import com.vex.irshark.ui.macro.MacroBlockType
import com.vex.irshark.ui.macro.MacroGraph
import com.vex.irshark.ui.macro.MacroGraphCanvas
import com.vex.irshark.ui.macro.MacroNode
import com.vex.irshark.ui.macro.blockLabel
import kotlinx.coroutines.launch
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Main editor screen
// ─────────────────────────────────────────────────────────────────────────────

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
    var nameError by remember { mutableStateOf(false) }

    // Build / restore graph
    val graph = remember {
        val g = if (!initialMacro?.blocklyXml.isNullOrBlank()) {
            MacroGraph.fromJson(initialMacro!!.blocklyXml)
        } else {
            MacroGraph.empty()
        }
        // Ensure start node always exists
        if (g.nodes.none { it.type == MacroBlockType.START }) {
            g.nodes.add(MacroNode(id = "start", type = MacroBlockType.START,
                pos = Offset(200f, 120f), params = BlockParams.None))
        }
        g
    }

    // Param-editor dialog
    var editingNode by remember { mutableStateOf<MacroNode?>(null) }

    // IR picker
    var showIrPicker   by remember { mutableStateOf(false) }
    var irPickNodeId   by remember { mutableStateOf("") }

    // Save / name dialog
    var showSaveDialog by remember { mutableStateOf(false) }
    var orphanWarning  by remember { mutableStateOf(0) }

    if (showIrPicker) {
        IrPickerDialog(
            savedRemotes = savedRemotes,
            dbProfiles   = dbProfiles,
            onDismiss    = { showIrPicker = false },
            onPick       = { remoteName, buttonLabel, irCode ->
                graph.updateParams(irPickNodeId, BlockParams.IrSend(
                    displayLabel = "$remoteName / $buttonLabel",
                    remoteName   = remoteName,
                    buttonLabel  = buttonLabel,
                    irCode       = irCode
                ))
                showIrPicker = false
            }
        )
    }

    editingNode?.let { node ->
        NodeParamDialog(
            node         = node,
            onDismiss    = { editingNode = null },
            onIrPick     = { editingNode = null; irPickNodeId = node.id; showIrPicker = true },
            onSave       = { params -> graph.updateParams(node.id, params); editingNode = null },
            onDelete     = {
                if (node.type != MacroBlockType.START) {
                    graph.removeNode(node.id)
                }
                editingNode = null
            }
        )
    }

    if (showSaveDialog) {
        SaveDialog(
            initialName    = macroName,
            orphanCount    = orphanWarning,
            existingNames  = existingNames,
            onDismiss      = { showSaveDialog = false },
            onSave         = { name ->
                macroName     = name
                showSaveDialog = false
                val result = graph.compile()
                onSave(SavedMacro(
                    id         = initialMacro?.id ?: UUID.randomUUID().toString(),
                    name       = name.trim(),
                    steps      = result.steps,
                    blocklyXml = graph.toJson()
                ))
            }
        )
    }

    // ── Full-screen layout ────────────────────────────────────────────────
    Column(modifier = Modifier.fillMaxSize()) {

        // Top bar: name + save/cancel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0E0B1A))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value         = macroName,
                onValueChange = { macroName = it; nameError = false },
                modifier      = Modifier.weight(1f),
                singleLine    = true,
                isError       = nameError,
                placeholder   = { Text("Macro name", fontSize = 13.sp) }
            )
            // Save
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    .clickable {
                        if (macroName.isBlank()) { nameError = true; return@clickable }
                        val result = graph.compile()
                        orphanWarning = result.orphans
                        showSaveDialog = true
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Text("Save", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            // Cancel
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .width(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A1726))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, null, tint = Color(0xFF8A8899), modifier = Modifier.size(14.dp))
            }
        }

        // Graph canvas
        MacroGraphCanvas(
            graph            = graph,
            modifier         = Modifier.weight(1f),
            onNodeTap        = { node ->
                if (node.type == MacroBlockType.IR_SEND) {
                    irPickNodeId = node.id
                    showIrPicker = true
                } else if (node.type != MacroBlockType.START) {
                    editingNode = node
                }
            },
            onNodeLongPress  = { node ->
                val currentSelected = graph.nodes.filter { it.selected }.map { it.id }.toSet()
                val newSet = if (node.id in currentSelected) currentSelected - node.id
                             else currentSelected + node.id
                graph.setSelected(newSet)
            },
            onAddBlock       = { type, pos ->
                graph.addNode(MacroNode(type = type, pos = pos,
                    params = defaultParams(type)))
            },
            onDeleteSelected = {
                val ids = graph.nodes.filter { it.selected && it.type != MacroBlockType.START }
                    .map { it.id }.toSet()
                graph.removeNodes(ids)
            }
        )
    }
}

private fun defaultParams(type: MacroBlockType): BlockParams = when (type) {
    MacroBlockType.IR_SEND      -> BlockParams.IrSend()
    MacroBlockType.DELAY        -> BlockParams.Delay()
    MacroBlockType.SHOW_TEXT    -> BlockParams.ShowText()
    MacroBlockType.WAIT_CONFIRM -> BlockParams.WaitConfirm()
    MacroBlockType.IF_ELSE      -> BlockParams.IfElse()
    else                        -> BlockParams.None
}

// ─────────────────────────────────────────────────────────────────────────────
// Node param dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NodeParamDialog(
    node:      MacroNode,
    onDismiss: () -> Unit,
    onIrPick:  () -> Unit,
    onSave:    (BlockParams) -> Unit,
    onDelete:  () -> Unit
) {
    val violet = MaterialTheme.colorScheme.primary

    var delayMs  by remember { mutableStateOf(((node.params as? BlockParams.Delay)?.ms ?: 500L).toString()) }
    var showTxt  by remember { mutableStateOf((node.params as? BlockParams.ShowText)?.text ?: "") }
    var showDur   by remember { mutableStateOf(((node.params as? BlockParams.ShowText)?.durationMs ?: 3000L).toString()) }
    var showAsync by remember { mutableStateOf((node.params as? BlockParams.ShowText)?.async ?: false) }
    var waitMsg  by remember { mutableStateOf((node.params as? BlockParams.WaitConfirm)?.message ?: "Press OK to continue") }
    var ifMsg    by remember { mutableStateOf((node.params as? BlockParams.IfElse)?.message ?: "Continue?") }

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
            Text(blockLabel(node.type), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            when (node.type) {
                MacroBlockType.DELAY -> {
                    OutlinedTextField(value = delayMs, onValueChange = { delayMs = it },
                        label = { Text("Duration (ms)") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Text("500 = 0.5 sec, 2000 = 2 sec", color = Color(0xFF8A8899), fontSize = 11.sp)
                }
                MacroBlockType.SHOW_TEXT -> {
                    OutlinedTextField(value = showTxt, onValueChange = { showTxt = it },
                        label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = showDur, onValueChange = { showDur = it },
                        label = { Text("Duration (ms)") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Text("3000 = 3 sec", color = Color(0xFF8A8899), fontSize = 11.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Don't wait (async)", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                        Switch(checked = showAsync, onCheckedChange = { showAsync = it })
                    }
                }
                MacroBlockType.WAIT_CONFIRM -> {
                    OutlinedTextField(value = waitMsg, onValueChange = { waitMsg = it },
                        label = { Text("Prompt") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                MacroBlockType.IF_ELSE -> {
                    OutlinedTextField(value = ifMsg, onValueChange = { ifMsg = it },
                        label = { Text("Question") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("YES and NO branches connect from the bottom pins.", color = Color(0xFF8A8899), fontSize = 11.sp)
                }
                MacroBlockType.IR_SEND -> {
                    val p = node.params as? BlockParams.IrSend
                    Text(if (p?.displayLabel.isNullOrEmpty()) "No IR button selected" else p!!.displayLabel,
                        color = if (p?.displayLabel.isNullOrEmpty()) Color(0xFF8A8899) else Color.White,
                        fontSize = 12.sp)
                    TextButton(onClick = onIrPick, modifier = Modifier.fillMaxWidth()) {
                        Text("Pick IR button...")
                    }
                }
                MacroBlockType.END -> {
                    Text("Terminates the macro immediately.", color = Color(0xFF8A8899), fontSize = 12.sp)
                }
                else -> {}
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (node.type != MacroBlockType.START && node.type != MacroBlockType.END) {
                    TextButton(onClick = onDelete) { Text("Delete block", color = Color(0xFFFF7B9D)) }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = {
                    val params: BlockParams = when (node.type) {
                        MacroBlockType.DELAY        -> BlockParams.Delay(delayMs.toLongOrNull()?.coerceAtLeast(1) ?: 500L)
                        MacroBlockType.SHOW_TEXT    -> BlockParams.ShowText(showTxt.ifBlank { "Hello!" }, showDur.toLongOrNull()?.coerceAtLeast(100) ?: 3000L, showAsync)
                        MacroBlockType.WAIT_CONFIRM -> BlockParams.WaitConfirm(waitMsg.ifBlank { "Continue?" })
                        MacroBlockType.IF_ELSE      -> BlockParams.IfElse(ifMsg.ifBlank { "Continue?" })
                        MacroBlockType.IR_SEND, MacroBlockType.END, MacroBlockType.START -> node.params
                    }
                    onSave(params)
                }) { Text("OK") }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Save dialog (with optional orphan warning)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SaveDialog(
    initialName:   String,
    orphanCount:   Int,
    existingNames: Set<String>,
    onDismiss:     () -> Unit,
    onSave:        (String) -> Unit
) {
    var name  by remember { mutableStateOf(initialName) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save macro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it; error = "" },
                    label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = error.isNotEmpty(), supportingText = if (error.isNotEmpty()) ({ Text(error) }) else null)
                if (orphanCount > 0) {
                    Text("Warning: $orphanCount block(s) are not connected to the graph and will be ignored.",
                        color = Color(0xFFFFC14D), fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { error = "Name required"; return@TextButton }
                onSave(name.trim())
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// IR Picker dialog (reused)
// ─────────────────────────────────────────────────────────────────────────────

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
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, label = { Text(if (tab == 0) "Search remotes" else "Search profiles") })

            if (tab == 0) {
                val filtered = savedRemotes.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                if (myRemoteIdx < 0) {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(filtered) { _, remote ->
                            IrPickerRow(remote.name, "${remote.buttons.size} buttons") {
                                myRemoteIdx = savedRemotes.indexOf(remote)
                            }
                        }
                    }
                } else {
                    val remote = savedRemotes.getOrNull(myRemoteIdx)
                    if (remote != null) {
                        Text("<- ${remote.name}", color = violet, fontSize = 12.sp,
                            modifier = Modifier.clickable { myRemoteIdx = -1 })
                        val btns = remote.buttons.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsIndexed(btns) { _, btn ->
                                val ok = btn.code.isNotBlank()
                                IrPickerRow(btn.label, if (ok) btn.code.take(50) else "No IR code", enabled = ok) {
                                    if (ok) onPick(remote.name, btn.label, btn.code)
                                }
                            }
                        }
                    }
                }
            } else {
                val profs = dbProfiles.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }.take(200)
                if (dbProfileIdx < 0) {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(profs) { _, profile ->
                            IrPickerRow(profile.name, profile.parentPath) { dbProfileIdx = dbProfiles.indexOf(profile) }
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
                            val bts = dbButtons.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                itemsIndexed(bts) { _, opt ->
                                    IrPickerRow(opt.label, opt.code.take(60)) { onPick(profile.name, opt.label, opt.code) }
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
private fun IrPickerRow(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
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