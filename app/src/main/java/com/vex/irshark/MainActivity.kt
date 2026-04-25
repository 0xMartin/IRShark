package com.vex.irshark

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vex.irshark.ui.theme.IRSharkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val DB_ROOT = "flipper_irdb"
private const val PREFS_NAME = "irshark_prefs"
private const val KEY_SAVED_REMOTES = "saved_remotes"
private const val REMOTE_DELIMITER = "||"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IRSharkTheme(darkTheme = true, dynamicColor = false) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    IRSharkApp(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun IRSharkApp(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val neon = MaterialTheme.colorScheme.primary

    var dbIndex by remember { mutableStateOf(FlipperDbIndex()) }
    var activeTab by rememberSaveable { mutableStateOf(TopTab.UNIVERSAL_REMOTE) }
    var currentPath by rememberSaveable { mutableStateOf(DB_ROOT) }
    var activeCommand by rememberSaveable { mutableStateOf<String?>(null) }
    var currentCode by rememberSaveable { mutableIntStateOf(0) }
    var autoSending by rememberSaveable { mutableStateOf(false) }
    var sendIntervalMs by rememberSaveable { mutableStateOf(250f) }
    var query by rememberSaveable { mutableStateOf("") }
    var savedRemotes by remember { mutableStateOf(listOf<SavedRemote>()) }
    var selectedRemoteInLibrary by remember { mutableStateOf<SavedRemote?>(null) }

    val activeCommandCount = remember(dbIndex, currentPath, activeCommand) {
        val command = activeCommand
        if (command.isNullOrBlank()) 0 else countProfilesForCommand(dbIndex, currentPath, command)
    }

    LaunchedEffect(Unit) {
        dbIndex = loadFlipperDbIndex(context)
        savedRemotes = loadSavedRemotes(context)
    }

    LaunchedEffect(savedRemotes) {
        saveSavedRemotes(context, savedRemotes)
    }

    LaunchedEffect(autoSending, currentPath, activeCommand, sendIntervalMs, activeCommandCount) {
        if (!autoSending || activeCommand.isNullOrBlank() || activeCommandCount <= 0) {
            return@LaunchedEffect
        }

        while (autoSending) {
            delay(sendIntervalMs.roundToInt().toLong())
            currentCode = if (currentCode >= activeCommandCount) 1 else currentCode + 1
        }
    }

    Box(
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF050805), Color(0xFF0A100A), Color(0xFF040604))
            )
        )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 100.dp, end = 22.dp)
                .size(170.dp)
                .clip(CircleShape)
                .background(neon.copy(alpha = 0.08f))
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 80.dp, start = 18.dp)
                .size(130.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.03f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            HeaderBar(status = dbIndex.status)

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF121712))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TabChip(
                    label = "UNIVERSAL REMOTE",
                    active = activeTab == TopTab.UNIVERSAL_REMOTE,
                    onClick = { activeTab = TopTab.UNIVERSAL_REMOTE }
                )
                TabChip(
                    label = "MY REMOTES",
                    active = activeTab == TopTab.MY_REMOTES,
                    onClick = {
                        activeTab = TopTab.MY_REMOTES
                        autoSending = false
                        activeCommand = null
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (activeTab) {
                TopTab.UNIVERSAL_REMOTE -> {
                    UniversalRemotePanel(
                        dbIndex = dbIndex,
                        currentPath = currentPath,
                        activeCommand = activeCommand,
                        currentCode = currentCode,
                        activeCommandCount = activeCommandCount,
                        autoSending = autoSending,
                        sendIntervalMs = sendIntervalMs,
                        onOpenFolder = { nextPath ->
                            currentPath = nextPath
                            activeCommand = null
                            currentCode = 0
                            autoSending = false
                        },
                        onBack = {
                            parentPath(currentPath)?.let {
                                currentPath = it
                                activeCommand = null
                                currentCode = 0
                                autoSending = false
                            }
                        },
                        onCommandClick = { command ->
                            activeCommand = command
                            if (activeCommandCount > 0) {
                                currentCode = if (currentCode >= activeCommandCount) 1 else currentCode + 1
                            } else {
                                currentCode = 1
                            }
                        },
                        onToggleAutoSend = {
                            autoSending = !autoSending
                        },
                        onIntervalChange = { sendIntervalMs = it }
                    )
                }

                TopTab.MY_REMOTES -> {
                    MyRemotesPanel(
                        query = query,
                        onQueryChange = { query = it },
                        savedRemotes = savedRemotes,
                        dbProfiles = dbIndex.profiles,
                        selectedRemote = selectedRemoteInLibrary,
                        onOpenRemote = { selectedRemoteInLibrary = it },
                        onCloseRemote = { selectedRemoteInLibrary = null },
                        onAddRemote = { remote ->
                            if (savedRemotes.none { it.profilePath == remote.profilePath }) {
                                savedRemotes = savedRemotes + remote
                            }
                        },
                        onDeleteRemote = { remote ->
                            savedRemotes = savedRemotes.filterNot { it.profilePath == remote.profilePath }
                            if (selectedRemoteInLibrary?.profilePath == remote.profilePath) {
                                selectedRemoteInLibrary = null
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderBar(status: String) {
    val neon = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(neon.copy(alpha = 0.22f))
                    .border(1.dp, neon, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("IR", color = neon, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Text(
                text = "IRShark",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 23.sp
            )
        }

        Text(
            text = status,
            color = neon,
            fontSize = 10.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(170.dp)
        )
    }
}

@Composable
private fun AppCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    val neon = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0A0D0A))
            .border(1.dp, neon.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = Color(0xFFA4B2A4),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(10.dp))
        }
        content()
    }
}

@Composable
private fun UniversalRemotePanel(
    dbIndex: FlipperDbIndex,
    currentPath: String,
    activeCommand: String?,
    currentCode: Int,
    activeCommandCount: Int,
    autoSending: Boolean,
    sendIntervalMs: Float,
    onOpenFolder: (String) -> Unit,
    onBack: () -> Unit,
    onCommandClick: (String) -> Unit,
    onToggleAutoSend: () -> Unit,
    onIntervalChange: (Float) -> Unit
) {
    val scroll = rememberScrollState()
    val childFolders = dbIndex.folders[currentPath].orEmpty().sortedBy { prettyName(it) }
    val commandStats = remember(dbIndex, currentPath) { commandStatsForPath(dbIndex, currentPath) }
    val commands = commandStats.keys.sorted()
    val profilesInPath = profilesUnderPath(dbIndex, currentPath)
    val progress = if (activeCommandCount > 0) currentCode.toFloat() / activeCommandCount.toFloat() else 0f

    AppCard(
        title = "UNIVERSAL REMOTE",
        subtitle = "Path: ${prettyPath(currentPath)}"
    ) {
        Column(modifier = Modifier.verticalScroll(scroll)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF121812))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                    .clickable(enabled = currentPath != DB_ROOT, onClick = onBack)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (currentPath == DB_ROOT) "•" else "←",
                    color = if (currentPath == DB_ROOT) Color(0xFF899989) else MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (currentPath == DB_ROOT) "Root category level" else "Back to parent folder",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Folders: ${childFolders.size}  |  Profiles: ${profilesInPath.size}",
                color = Color(0xFF95A795),
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (childFolders.isNotEmpty()) {
                Text(
                    text = "Browse folders",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                childFolders.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { folderPath ->
                            FolderTile(
                                title = prettyName(folderPath),
                                onClick = { onOpenFolder(folderPath) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(2 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Text(
                text = "Universal command test",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (autoSending) Color(0xFF3A130F) else Color(0xFF103016))
                        .border(
                            1.dp,
                            if (autoSending) Color(0xFFFF7B6D) else MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable(onClick = onToggleAutoSend),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (autoSending) "STOP" else "START AUTO SEND",
                        color = if (autoSending) Color(0xFFFFB7AF) else MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .width(118.dp)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF111711))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${sendIntervalMs.roundToInt()} ms",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }

            Slider(
                value = sendIntervalMs,
                onValueChange = onIntervalChange,
                valueRange = 80f..1200f,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                IrPhoneIcon()
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (commands.isEmpty()) {
                Text(
                    text = "No command names found under this folder path.",
                    color = Color(0xFF9EB19E),
                    fontSize = 11.sp
                )
            } else {
                commands.chunked(2).forEachIndexed { rowIndex, rowButtons ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowButtons.forEach { command ->
                            val isActive = command == activeCommand
                            val codeCount = commandStats[command] ?: 0
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else Color(0xFF0F140F))
                                    .border(
                                        1.dp,
                                        if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.16f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onCommandClick(command) }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$command  ($codeCount)",
                                    color = if (isActive) MaterialTheme.colorScheme.primary else Color.White,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                        repeat(2 - rowButtons.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    if (rowIndex < (commands.size - 1) / 2) Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val statusText = when {
                activeCommand.isNullOrBlank() -> "Select a command to begin testing sequence."
                activeCommandCount <= 0 -> "No matching command blocks found for \"$activeCommand\"."
                currentCode <= 0 -> "Ready to send \"$activeCommand\" across $activeCommandCount profiles."
                autoSending -> "Auto sending \"$activeCommand\" code $currentCode / $activeCommandCount (${sendIntervalMs.roundToInt()} ms)..."
                else -> "Manual sending \"$activeCommand\" code $currentCode / $activeCommandCount."
            }

            Text(text = statusText, color = Color.White, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(20.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color(0xFF1A241A)
            )
        }
    }
}

@Composable
private fun FolderTile(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0E130E))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.34f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            DeviceCategoryIcon(title)
            Text(
                text = title,
                color = Color.White,
                fontSize = 11.sp,
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
private fun MyRemotesPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    savedRemotes: List<SavedRemote>,
    dbProfiles: List<FlipperProfile>,
    selectedRemote: SavedRemote?,
    onOpenRemote: (SavedRemote) -> Unit,
    onCloseRemote: () -> Unit,
    onAddRemote: (SavedRemote) -> Unit,
    onDeleteRemote: (SavedRemote) -> Unit
) {
    val neon = MaterialTheme.colorScheme.primary
    val q = query.trim()

    val filteredSaved = if (q.isBlank()) {
        savedRemotes
    } else {
        savedRemotes.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.profilePath.contains(q, ignoreCase = true)
        }
    }

    val addCandidates = dbProfiles
        .asSequence()
        .filterNot { profile -> savedRemotes.any { it.profilePath == profile.path } }
        .filter {
            q.isBlank() ||
                it.name.contains(q, ignoreCase = true) ||
                prettyPath(it.parentPath).contains(q, ignoreCase = true)
        }
        .take(40)
        .toList()

    AppCard(title = "LIBRARY", subtitle = "MY REMOTES") {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search device or path") },
            placeholder = { Text("TV, CCTV, Samsung...") }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Saved Remotes (${savedRemotes.size})",
            color = neon,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        if (filteredSaved.isEmpty()) {
            EmptyHint("No saved remotes yet.")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filteredSaved) { remote ->
                    SavedRemoteRow(
                        remote = remote,
                        onOpen = { onOpenRemote(remote) },
                        onDelete = { onDeleteRemote(remote) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = neon.copy(alpha = 0.25f))
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Add From Flipper-IRDB",
            color = neon,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        if (addCandidates.isEmpty()) {
            EmptyHint("No matching profiles in database.")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(addCandidates) { profile ->
                    val remote = SavedRemote(
                        name = profile.name,
                        profilePath = profile.path,
                        commands = profile.commands
                    )
                    DbCandidateRow(
                        profile = profile,
                        onOpen = { onOpenRemote(remote) },
                        onAdd = { onAddRemote(remote) }
                    )
                }
            }
        }

        if (selectedRemote != null) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = neon.copy(alpha = 0.25f))
            Spacer(modifier = Modifier.height(10.dp))
            RemotePreviewCard(remote = selectedRemote, onClose = onCloseRemote)
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF111511))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(
            text = text,
            color = Color(0xFF9EB19E),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun RowScope.TabChip(label: String, active: Boolean, onClick: () -> Unit) {
    val neon = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) neon.copy(alpha = 0.25f) else Color(0xFF101510))
            .border(
                1.dp,
                if (active) neon else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(9.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) neon else Color(0xFFD4DDD4),
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SavedRemoteRow(remote: SavedRemote, onOpen: () -> Unit, onDelete: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(onClick = onOpen)
                    .padding(vertical = 4.dp)
            ) {
                PowerIcon(Modifier.size(16.dp))
                Column {
                    Text(text = remote.name, color = Color.White, fontSize = 12.sp)
                    Text(
                        text = prettyPath(remote.profilePath),
                        color = Color(0xFF94A694),
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(onClick = onDelete)
                    .padding(5.dp)
            ) {
                TrashIcon(Modifier.fillMaxSize())
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    }
}

@Composable
private fun DbCandidateRow(profile: FlipperProfile, onOpen: () -> Unit, onAdd: () -> Unit) {
    val neon = MaterialTheme.colorScheme.primary
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(onClick = onOpen)
                    .padding(vertical = 4.dp)
            ) {
                Text(text = profile.name, color = Color.White, fontSize = 11.sp)
                Text(
                    text = prettyPath(profile.parentPath),
                    color = Color(0xFF94A694),
                    fontSize = 10.sp
                )
            }
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(neon.copy(alpha = 0.20f))
                    .border(1.dp, neon, RoundedCornerShape(8.dp))
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Add", color = neon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    }
}

@Composable
private fun RemotePreviewCard(remote: SavedRemote, onClose: () -> Unit) {
    val neon = MaterialTheme.colorScheme.primary
    val commands = if (remote.commands.isEmpty()) listOf("POWER") else remote.commands

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF101510))
            .border(1.dp, neon.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = remote.name,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Text(
                    text = prettyPath(remote.profilePath),
                    color = Color(0xFF95A795),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }

            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF121812))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Close", color = Color.White, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val rows = commands.chunked(3)
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0D120D))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = label, color = Color.White, fontSize = 10.sp)
                    }
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            if (index < rows.lastIndex) Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun DeviceCategoryIcon(label: String) {
    val neon = MaterialTheme.colorScheme.primary
    val normalized = label.lowercase()

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF172317))
            .border(1.dp, neon.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height

            when {
                "tv" in normalized || "monitor" in normalized || "display" in normalized || "screen" in normalized -> {
                    drawRoundRect(
                        color = neon,
                        topLeft = Offset(w * 0.10f, h * 0.18f),
                        size = Size(w * 0.80f, h * 0.52f),
                        cornerRadius = CornerRadius(3f, 3f),
                        style = Stroke(width = 2f)
                    )
                    drawLine(neon, Offset(w * 0.35f, h * 0.80f), Offset(w * 0.65f, h * 0.80f), 2f)
                }

                "ac" in normalized || "air" in normalized || "heater" in normalized || "humid" in normalized -> {
                    drawRoundRect(
                        color = neon,
                        topLeft = Offset(w * 0.10f, h * 0.22f),
                        size = Size(w * 0.80f, h * 0.28f),
                        cornerRadius = CornerRadius(3f, 3f),
                        style = Stroke(width = 2f)
                    )
                    drawLine(neon, Offset(w * 0.20f, h * 0.62f), Offset(w * 0.35f, h * 0.76f), 2f)
                    drawLine(neon, Offset(w * 0.48f, h * 0.62f), Offset(w * 0.48f, h * 0.80f), 2f)
                    drawLine(neon, Offset(w * 0.76f, h * 0.62f), Offset(w * 0.61f, h * 0.76f), 2f)
                }

                "fan" in normalized -> {
                    drawCircle(neon, radius = w * 0.07f, center = Offset(w * 0.50f, h * 0.50f))
                    drawCircle(neon, radius = w * 0.24f, center = Offset(w * 0.50f, h * 0.50f), style = Stroke(width = 2f))
                    drawLine(neon, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.78f, h * 0.42f), 2f)
                    drawLine(neon, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.38f, h * 0.78f), 2f)
                    drawLine(neon, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.30f, h * 0.34f), 2f)
                }

                "led" in normalized || "light" in normalized -> {
                    drawCircle(neon, radius = w * 0.18f, center = Offset(w * 0.50f, h * 0.38f), style = Stroke(width = 2f))
                    drawLine(neon, Offset(w * 0.50f, h * 0.56f), Offset(w * 0.50f, h * 0.84f), 2f)
                }

                "audio" in normalized || "speaker" in normalized || "receiver" in normalized || "sound" in normalized -> {
                    drawRoundRect(
                        color = neon,
                        topLeft = Offset(w * 0.12f, h * 0.18f),
                        size = Size(w * 0.76f, h * 0.62f),
                        cornerRadius = CornerRadius(3f, 3f),
                        style = Stroke(width = 2f)
                    )
                    drawCircle(neon, radius = w * 0.08f, center = Offset(w * 0.34f, h * 0.49f), style = Stroke(width = 2f))
                    drawCircle(neon, radius = w * 0.08f, center = Offset(w * 0.66f, h * 0.49f), style = Stroke(width = 2f))
                }

                "camera" in normalized || "cctv" in normalized -> {
                    drawRoundRect(
                        color = neon,
                        topLeft = Offset(w * 0.10f, h * 0.30f),
                        size = Size(w * 0.72f, h * 0.32f),
                        cornerRadius = CornerRadius(3f, 3f),
                        style = Stroke(width = 2f)
                    )
                    drawCircle(neon, radius = w * 0.09f, center = Offset(w * 0.76f, h * 0.46f), style = Stroke(width = 2f))
                }

                else -> {
                    drawRoundRect(
                        color = neon,
                        topLeft = Offset(w * 0.14f, h * 0.18f),
                        size = Size(w * 0.72f, h * 0.64f),
                        cornerRadius = CornerRadius(3f, 3f),
                        style = Stroke(width = 2f)
                    )
                    drawLine(neon, Offset(w * 0.35f, h * 0.36f), Offset(w * 0.65f, h * 0.36f), 2f)
                    drawLine(neon, Offset(w * 0.35f, h * 0.52f), Offset(w * 0.65f, h * 0.52f), 2f)
                    drawLine(neon, Offset(w * 0.35f, h * 0.68f), Offset(w * 0.65f, h * 0.68f), 2f)
                }
            }
        }
    }
}

@Composable
private fun IrPhoneIcon() {
    val neon = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(width = 42.dp, height = 32.dp)) {
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(0f, size.height * 0.22f),
            size = Size(size.width * 0.42f, size.height * 0.62f),
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 3f)
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.16f, size.height * 0.29f),
            end = Offset(size.width * 0.24f, size.height * 0.29f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
        drawArc(
            color = neon,
            startAngle = -35f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(size.width * 0.30f, size.height * 0.02f),
            size = Size(size.width * 0.55f, size.height * 0.55f),
            style = Stroke(width = 3f)
        )
        drawArc(
            color = neon.copy(alpha = 0.6f),
            startAngle = -30f,
            sweepAngle = 60f,
            useCenter = false,
            topLeft = Offset(size.width * 0.45f, size.height * 0.10f),
            size = Size(size.width * 0.40f, size.height * 0.40f),
            style = Stroke(width = 2f)
        )
    }
}

@Composable
private fun PowerIcon(modifier: Modifier = Modifier) {
    val neon = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        drawCircle(color = neon, style = Stroke(width = 2.4f))
        drawLine(
            color = neon,
            start = Offset(size.width / 2f, size.height * 0.15f),
            end = Offset(size.width / 2f, size.height * 0.52f),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun TrashIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val white = Color.White.copy(alpha = 0.88f)
        drawRoundRect(
            color = white,
            topLeft = Offset(size.width * 0.20f, size.height * 0.28f),
            size = Size(size.width * 0.60f, size.height * 0.56f),
            cornerRadius = CornerRadius(3f, 3f),
            style = Stroke(width = 2.2f)
        )
        drawLine(
            color = white,
            start = Offset(size.width * 0.15f, size.height * 0.25f),
            end = Offset(size.width * 0.85f, size.height * 0.25f),
            strokeWidth = 2.2f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = white,
            start = Offset(size.width * 0.40f, size.height * 0.12f),
            end = Offset(size.width * 0.60f, size.height * 0.12f),
            strokeWidth = 2.2f,
            cap = StrokeCap.Round
        )
    }
}

private data class FlipperDbIndex(
    val totalProfiles: Int = 0,
    val folders: Map<String, List<String>> = emptyMap(),
    val profilesByFolder: Map<String, List<FlipperProfile>> = emptyMap(),
    val profiles: List<FlipperProfile> = emptyList(),
    val status: String = "Loading Flipper-IRDB..."
)

private data class FlipperProfile(
    val path: String,
    val parentPath: String,
    val name: String,
    val commands: List<String>
)

private data class SavedRemote(
    val name: String,
    val profilePath: String,
    val commands: List<String>
)

private enum class TopTab {
    UNIVERSAL_REMOTE,
    MY_REMOTES
}

private suspend fun loadFlipperDbIndex(context: Context): FlipperDbIndex {
    return withContext(Dispatchers.IO) {
        runCatching {
            val folders = mutableMapOf<String, MutableList<String>>()
            val profilesByFolder = mutableMapOf<String, MutableList<FlipperProfile>>()
            val allProfiles = mutableListOf<FlipperProfile>()

            fun walk(path: String) {
                val children = context.assets.list(path)?.sorted().orEmpty()
                folders.putIfAbsent(path, mutableListOf())
                profilesByFolder.putIfAbsent(path, mutableListOf())

                children.forEach { child ->
                    val childPath = "$path/$child"
                    if (child.endsWith(".ir", ignoreCase = true)) {
                        val commands = parseIrCommands(context, childPath)
                        val profile = FlipperProfile(
                            path = childPath,
                            parentPath = path,
                            name = child.removeSuffix(".ir").replace('_', ' ').trim(),
                            commands = commands
                        )
                        profilesByFolder[path]?.add(profile)
                        allProfiles += profile
                    } else {
                        val nested = context.assets.list(childPath).orEmpty()
                        if (nested.isNotEmpty()) {
                            folders[path]?.add(childPath)
                            walk(childPath)
                        }
                    }
                }
            }

            walk(DB_ROOT)

            FlipperDbIndex(
                totalProfiles = allProfiles.size,
                folders = folders,
                profilesByFolder = profilesByFolder,
                profiles = allProfiles,
                status = "Flipper-IRDB loaded: ${allProfiles.size} profiles"
            )
        }.getOrElse {
            FlipperDbIndex(status = "Flipper-IRDB unavailable")
        }
    }
}

private fun parseIrCommands(context: Context, assetPath: String): List<String> {
    return runCatching {
        val commands = LinkedHashSet<String>()
        context.assets.open(assetPath).bufferedReader().useLines { lines ->
            lines.forEach { rawLine ->
                val line = rawLine.trim()
                if (line.startsWith("name:", ignoreCase = true)) {
                    val command = line.substringAfter(':').trim()
                        .replace('_', ' ')
                        .replace('-', ' ')
                        .uppercase()
                    if (command.isNotBlank()) {
                        commands += command
                    }
                }
            }
        }
        commands.toList()
    }.getOrElse { emptyList() }
}

private fun profilesUnderPath(dbIndex: FlipperDbIndex, folderPath: String): List<FlipperProfile> {
    val prefix = "$folderPath/"
    return dbIndex.profiles.filter { it.parentPath == folderPath || it.path.startsWith(prefix) }
}

private fun commandStatsForPath(dbIndex: FlipperDbIndex, folderPath: String): Map<String, Int> {
    val stats = linkedMapOf<String, Int>()
    profilesUnderPath(dbIndex, folderPath).forEach { profile ->
        profile.commands.forEach { command ->
            stats[command] = (stats[command] ?: 0) + 1
        }
    }
    return stats
}

private fun countProfilesForCommand(dbIndex: FlipperDbIndex, folderPath: String, command: String): Int {
    return profilesUnderPath(dbIndex, folderPath).count { profile ->
        profile.commands.any { it.equals(command, ignoreCase = true) }
    }
}

private fun parentPath(path: String): String? {
    if (path == DB_ROOT) return null
    val slash = path.lastIndexOf('/')
    if (slash <= 0) return DB_ROOT
    return path.substring(0, slash)
}

private fun prettyName(path: String): String {
    val name = path.substringAfterLast('/').ifBlank { path }
    return name.replace('_', ' ')
}

private fun prettyPath(path: String): String {
    return path
        .removePrefix("$DB_ROOT/")
        .removePrefix(DB_ROOT)
        .ifBlank { "Root" }
        .replace('/', ' ')
        .replace('_', ' ')
}

private fun loadSavedRemotes(context: Context): List<SavedRemote> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_SAVED_REMOTES, "").orEmpty()
    if (raw.isBlank()) {
        return emptyList()
    }

    return raw.split(REMOTE_DELIMITER)
        .mapNotNull { token ->
            val trimmed = token.trim()
            if (trimmed.isBlank()) {
                return@mapNotNull null
            }

            val parts = trimmed.split("::")
            return@mapNotNull when {
                parts.size >= 3 -> {
                    SavedRemote(
                        name = parts[0].trim(),
                        profilePath = parts[1].trim(),
                        commands = parts[2].split(";;").map { it.trim() }.filter { it.isNotBlank() }
                    )
                }
                parts.size == 2 -> {
                    // Compatibility with previous version where second field was category.
                    SavedRemote(
                        name = parts[0].trim(),
                        profilePath = parts[1].trim(),
                        commands = emptyList()
                    )
                }
                else -> {
                    SavedRemote(name = trimmed, profilePath = "", commands = emptyList())
                }
            }
        }
}

private fun saveSavedRemotes(context: Context, remotes: List<SavedRemote>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val serialized = remotes.joinToString(REMOTE_DELIMITER) {
        "${it.name}::${it.profilePath}::${it.commands.joinToString(";;")}" 
    }
    prefs.edit().putString(KEY_SAVED_REMOTES, serialized).apply()
}

@Preview(showBackground = true)
@Composable
fun IRSharkPreview() {
    IRSharkTheme(darkTheme = true, dynamicColor = false) {
        IRSharkApp(modifier = Modifier.fillMaxSize())
    }
}
