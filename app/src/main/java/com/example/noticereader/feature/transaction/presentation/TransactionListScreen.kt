package com.example.noticereader.feature.transaction.presentation

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.noticereader.feature.transaction.presentation.components.EmptyState
import com.example.noticereader.feature.transaction.presentation.components.FilterBar
import com.example.noticereader.feature.transaction.presentation.components.SetupSheet
import com.example.noticereader.feature.transaction.presentation.components.StatusBar
import com.example.noticereader.feature.transaction.presentation.components.TransactionDetailSheet
import com.example.noticereader.feature.transaction.presentation.components.TransactionRow
import com.example.noticereader.feature.transaction.presentation.model.DateFilter
import com.example.noticereader.feature.transaction.presentation.model.SetupStatus
import com.example.noticereader.feature.transaction.presentation.model.TransactionListUiState
import com.example.noticereader.feature.transaction.presentation.model.TransactionRowUi
import com.example.noticereader.feature.transaction.presentation.model.TransactionTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The whole app is this one stateless screen (§10.1): status bar, the shared date-filter/sort row
 * (§10.4), then a TabRow + HorizontalPager over the three tabs. It receives ready-made state and
 * callbacks and renders them.
 *
 * Tapping a row opens the detail sheet, long-pressing copies the TrxID, and the Failed tab shows a
 * "Retry all" action plus a per-row Retry button (§10.6). Pull-to-refresh runs the catch-up scan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    state: TransactionListUiState,
    setupStatus: SetupStatus,
    keepAliveEnabled: Boolean,
    onKeepAliveChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onSelectFilter: (DateFilter) -> Unit,
    onCustomRange: (Long, Long) -> Unit,
    onToggleSort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = TransactionTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var showSetupSheet by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var detailRow by remember { mutableStateOf<TransactionRowUi?>(null) }

    fun copyTrxId(trxId: String) {
        clipboard.setText(AnnotatedString(trxId))
        Toast.makeText(context, "TrxID copied", Toast.LENGTH_SHORT).show()
    }

    // The scan runs in the background; show a brief refresh spinner so the pull feels responsive.
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(800)
            isRefreshing = false
        }
    }

    if (showSetupSheet) {
        SetupSheet(status = setupStatus, onDismiss = { showSetupSheet = false })
    }
    detailRow?.let { row ->
        TransactionDetailSheet(
            row = row,
            onCopyTrxId = { copyTrxId(row.trxId) },
            onDismiss = { detailRow = null },
        )
    }
    if (showDatePicker) {
        DateRangePickerDialog(
            onDismiss = { showDatePicker = false },
            onConfirm = { start, endExclusive ->
                onCustomRange(start, endExclusive)
                showDatePicker = false
            },
        )
    }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        StatusBar(
            isReady = setupStatus.isFullyReady,
            lastSmsRelative = state.lastSmsRelative,
            keepAliveEnabled = keepAliveEnabled,
            onKeepAliveChange = onKeepAliveChange,
            onOpenSetup = { showSetupSheet = true },
        )

        TabRow(selectedTabIndex = pagerState.currentPage) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tabLabel(tab, state)) },
                )
            }
        }

        FilterBar(
            dateFilter = state.dateFilter,
            sortDirection = state.sortDirection,
            onSelectFilter = onSelectFilter,
            onCustomClick = { showDatePicker = true },
            onToggleSort = onToggleSort,
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                onRefresh()
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val tab = tabs[page]
                TransactionPage(
                    rows = state.rowsFor(tab),
                    emptyText = tab.emptyText,
                    showRetryAll = tab == TransactionTab.FAILED && state.failedCount > 0,
                    onRetryAll = onRetry,
                    onRowClick = { detailRow = it },
                    onRowLongPress = { copyTrxId(it.trxId) },
                    onRowRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun TransactionPage(
    rows: List<TransactionRowUi>,
    emptyText: String,
    showRetryAll: Boolean,
    onRetryAll: () -> Unit,
    onRowClick: (TransactionRowUi) -> Unit,
    onRowLongPress: (TransactionRowUi) -> Unit,
    onRowRetry: () -> Unit,
) {
    if (rows.isEmpty()) {
        EmptyState(text = emptyText)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        if (showRetryAll) {
            item(key = "retry_all_header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onRetryAll) { Text("Retry all") }
                }
            }
        }
        items(items = rows, key = { it.trxId }) { row ->
            TransactionRow(
                row = row,
                onClick = { onRowClick(row) },
                onLongClick = { onRowLongPress(row) },
                onRetry = onRowRetry,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (start: Long, endExclusive: Long) -> Unit,
) {
    val pickerState = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = pickerState.selectedStartDateMillis != null,
                onClick = {
                    val start = pickerState.selectedStartDateMillis ?: return@TextButton
                    // Include the whole end day; fall back to the start day when only one is picked.
                    val endDay = pickerState.selectedEndDateMillis ?: start
                    onConfirm(start, endDay + DAY_MILLIS)
                },
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(state = pickerState)
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** Adds a count badge to the Success and Failed tabs, e.g. "Failed (3)" (§10.3). */
private fun tabLabel(tab: TransactionTab, state: TransactionListUiState): String = when (tab) {
    TransactionTab.ALL -> tab.label
    TransactionTab.SUCCESS -> "${tab.label} (${state.successCount})"
    TransactionTab.FAILED -> "${tab.label} (${state.failedCount})"
}
