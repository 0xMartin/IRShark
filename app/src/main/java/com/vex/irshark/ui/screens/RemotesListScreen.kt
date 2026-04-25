package com.vex.irshark.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vex.irshark.ui.components.EmptyCard
import com.vex.irshark.ui.components.ListRow

@Composable
fun RemotesListScreen(
    query: String,
    queryLabel: String,
    onQueryChange: (String) -> Unit,
    emptyText: String,
    items: List<Pair<String, String>>,
    onOpen: (Int) -> Unit,
    onSecondaryAction: (Int) -> Unit,
    secondaryActionLabel: String,
    topActionLabel: String? = null,
    onTopAction: (() -> Unit)? = null,
    secondaryActionLabelForItem: ((Int) -> String)? = null,
    secondaryActionEnabledForItem: ((Int) -> Boolean)? = null,
    isFavoriteForItem: ((Int) -> Boolean)? = null,
    onFavoriteToggleForItem: ((Int) -> Unit)? = null,
    onDuplicateForItem: ((Int) -> Unit)? = null,
    secondaryActionIcon: ImageVector? = null
) {
    val violet = MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!topActionLabel.isNullOrBlank() && onTopAction != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(violet.copy(alpha = 0.14f))
                    .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onTopAction)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(topActionLabel, color = violet, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(queryLabel) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (items.isEmpty()) {
            EmptyCard(emptyText)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items.indices.toList()) { index ->
                    val item = items[index]
                    ListRow(
                        title = item.first,
                        subtitle = item.second,
                        actionLabel = secondaryActionLabelForItem?.invoke(index) ?: secondaryActionLabel,
                        actionEnabled = secondaryActionEnabledForItem?.invoke(index) ?: true,
                        actionIcon = secondaryActionIcon,
                        onOpen = { onOpen(index) },
                        onAction = { onSecondaryAction(index) },
                        isFavorite = isFavoriteForItem?.invoke(index) ?: false,
                        onFavoriteToggle = onFavoriteToggleForItem?.let { { it(index) } },
                        onDuplicate = onDuplicateForItem?.let { { it(index) } }
                    )
                }
            }
        }
    }
}
