package com.vex.irshark.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    secondaryActionLabelForItem: ((Int) -> String)? = null,
    secondaryActionEnabledForItem: ((Int) -> Boolean)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
                        onOpen = { onOpen(index) },
                        onAction = { onSecondaryAction(index) }
                    )
                }
            }
        }
    }
}
