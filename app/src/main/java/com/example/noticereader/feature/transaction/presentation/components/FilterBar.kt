package com.example.noticereader.feature.transaction.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.noticereader.feature.transaction.presentation.model.DateFilter
import com.example.noticereader.feature.transaction.presentation.model.SortDirection

/**
 * The shared date filter and sort control that sits above the list (§10.4). Changing it applies to
 * all three tabs at once. Quick chips cover Today / 7 days / 30 days / All; the Custom chip opens a
 * date range picker; the trailing icon flips the sort direction (newest first ⇄ oldest first).
 */
@Composable
fun FilterBar(
    dateFilter: DateFilter,
    sortDirection: SortDirection,
    onSelectFilter: (DateFilter) -> Unit,
    onCustomClick: () -> Unit,
    onToggleSort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = dateFilter is DateFilter.Today,
                onClick = { onSelectFilter(DateFilter.Today) },
                label = { Text("Today") },
            )
            FilterChip(
                selected = dateFilter is DateFilter.Last7Days,
                onClick = { onSelectFilter(DateFilter.Last7Days) },
                label = { Text("7 days") },
            )
            FilterChip(
                selected = dateFilter is DateFilter.Last30Days,
                onClick = { onSelectFilter(DateFilter.Last30Days) },
                label = { Text("30 days") },
            )
            FilterChip(
                selected = dateFilter is DateFilter.All,
                onClick = { onSelectFilter(DateFilter.All) },
                label = { Text("All") },
            )
            FilterChip(
                selected = dateFilter is DateFilter.Custom,
                onClick = onCustomClick,
                label = { Text("Custom") },
            )
        }

        val newestFirst = sortDirection == SortDirection.NEWEST_FIRST
        IconButton(onClick = onToggleSort) {
            Icon(
                imageVector = if (newestFirst) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                contentDescription = if (newestFirst) "Newest first" else "Oldest first",
            )
        }
    }
}
