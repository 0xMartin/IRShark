package com.m4r71n.irshark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import com.m4r71n.irshark.data.DbIrCodeOption
import com.m4r71n.irshark.data.FlipperProfile
import com.m4r71n.irshark.data.SavedRemoteButton
import com.m4r71n.irshark.ui.components.CategorySvgIcon
import com.m4r71n.irshark.ui.components.EmptyCard

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
    columnCount: Int,
    duplicateName: Boolean,
    onNameChange: (String) -> Unit,
    onIconChange: (String?) -> Unit,
    onColumnCountChange: (Int) -> Unit,
    onAddButton: () -> Unit,
    onEditButton: (Int) -> Unit,
    onMoveButton: (from: Int, to: Int) -> Unit,
    onDeleteButton: (Int) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean
) {
    val violet = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(0f) }
    var dropTargetIndex by remember { mutableIntStateOf(-1) }
    var recentlyMovedIndex by remember { mutableIntStateOf(-1) }

    // Clear highlight after short delay whenever it changes
    LaunchedEffect(recentlyMovedIndex) {
        if (recentlyMovedIndex >= 0) {
            delay(700)
            recentlyMovedIndex = -1
        }
    }

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
                    Text("Button layout", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            1 to Icons.Filled.ViewList,
                            2 to Icons.Filled.GridView,
                            3 to Icons.Filled.ViewModule
                        ).forEach { (cols, icon) ->
                            val selected = columnCount == cols
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) Color(0xFF2A1E4A) else Color(0xFF181327))
                                    .border(
                                        1.dp,
                                        if (selected) Color(0xFF9B6DFF) else Color.White.copy(alpha = 0.10f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onColumnCountChange(cols) },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (selected) Color(0xFF9B6DFF) else Color(0xFF6A5F8A),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "$cols col",
                                        color = if (selected) Color(0xFFE4D7FF) else Color(0xFF8A8899),
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
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
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            buttons.forEachIndexed { index, button ->
                                val isDragging = draggedIndex == index
                                val isRecentlyMoved = recentlyMovedIndex == index
                                val isDroppingHere = draggedIndex != -1 && dropTargetIndex == index && index != draggedIndex

                                // Drop indicator ABOVE – when item will land here from below
                                if (isDroppingHere && dropTargetIndex < draggedIndex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 1.dp)
                                            .height(2.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(Color(0xFF9B6DFF))
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .zIndex(if (isDragging) 1f else 0f)
                                        .offset { IntOffset(0, if (isDragging) dragOffsetY.roundToInt() else 0) }
                                        .onSizeChanged { size ->
                                            if (itemHeightPx == 0f) itemHeightPx = size.height.toFloat()
                                        }
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when {
                                                isDragging -> Color(0xFF251B45)
                                                isRecentlyMoved -> Color(0xFF1E2E1E)
                                                else -> Color(0xFF181327)
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            when {
                                                isDragging -> Color(0xFF9B6DFF).copy(alpha = 0.6f)
                                                isRecentlyMoved -> Color(0xFF4CAF50).copy(alpha = 0.6f)
                                                else -> Color.White.copy(alpha = 0.10f)
                                            },
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.OpenWith,
                                        contentDescription = "Drag to reorder",
                                        tint = if (isDragging) Color(0xFF9B6DFF) else Color(0xFF6A5F8A),
                                        modifier = Modifier
                                            .size(22.dp)
                                            .pointerInput(index) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        draggedIndex = index
                                                        dragOffsetY = 0f
                                                        dropTargetIndex = index
                                                    },
                                                    onDragEnd = {
                                                        val steps = if (itemHeightPx > 0f)
                                                            (dragOffsetY / itemHeightPx).roundToInt()
                                                        else 0
                                                        val from = draggedIndex
                                                        val to = (from + steps).coerceIn(0, buttons.lastIndex)
                                                        draggedIndex = -1
                                                        dragOffsetY = 0f
                                                        dropTargetIndex = -1
                                                        if (from != to) {
                                                            recentlyMovedIndex = to
                                                            scope.launch { onMoveButton(from, to) }
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        draggedIndex = -1
                                                        dragOffsetY = 0f
                                                        dropTargetIndex = -1
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffsetY += dragAmount.y
                                                        dropTargetIndex = if (itemHeightPx > 0f) {
                                                            (draggedIndex + (dragOffsetY / itemHeightPx).roundToInt())
                                                                .coerceIn(0, buttons.lastIndex)
                                                        } else draggedIndex
                                                    }
                                                )
                                            }
                                    )

                                    Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                                        Text(button.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Text(
                                            if (button.code.isBlank()) "No code" else button.code,
                                            color = Color(0xFF8A8899),
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Edit",
                                            tint = Color(0xFF9B6DFF),
                                            modifier = Modifier.size(18.dp).clickable { onEditButton(index) }
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete",
                                            tint = Color(0xFFFF7B9D),
                                            modifier = Modifier.size(18.dp).clickable { onDeleteButton(index) }
                                        )
                                    }
                                }

                                // Drop indicator BELOW – when item will land here from above
                                if (isDroppingHere && dropTargetIndex > draggedIndex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 1.dp)
                                            .height(2.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(Color(0xFF9B6DFF))
                                    )
                                }
                            }
                        }
                    }
                }
            }
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

    }
}
