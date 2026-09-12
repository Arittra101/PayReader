package com.example.noticereader.feature.transaction.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.noticereader.feature.transaction.presentation.components.EmptyState
import com.example.noticereader.feature.transaction.presentation.components.StatusBar
import com.example.noticereader.feature.transaction.presentation.components.TransactionRow
import com.example.noticereader.feature.transaction.presentation.model.TransactionListUiState
import com.example.noticereader.feature.transaction.presentation.model.TransactionRowUi
import com.example.noticereader.feature.transaction.presentation.model.TransactionTab
import kotlinx.coroutines.launch

/**
 * The whole app is this one stateless screen (§10.1): status bar on top, then a TabRow +
 * HorizontalPager over the three tabs. It receives ready-made state and renders it — no
 * ViewModel, no repository here, so it stays preview-friendly.
 *
 * Tabs and pager stay in sync both ways: tapping a tab animates the pager, and swiping the
 * pager moves the tab indicator (§10.1).
 */
@Composable
fun TransactionListScreen(
    state: TransactionListUiState,
    isListening: Boolean,
    modifier: Modifier = Modifier,
) {
    val tabs = TransactionTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        StatusBar(
            isListening = isListening,
            lastSmsRelative = state.lastSmsRelative,
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

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val tab = tabs[page]
            TransactionPage(rows = state.rowsFor(tab), emptyText = tab.emptyText)
        }
    }
}

@Composable
private fun TransactionPage(
    rows: List<TransactionRowUi>,
    emptyText: String,
) {
    if (rows.isEmpty()) {
        EmptyState(text = emptyText)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        items(items = rows, key = { it.trxId }) { row ->
            TransactionRow(row = row, modifier = Modifier.padding(bottom = 12.dp))
        }
    }
}

/** Adds a count badge to the Success and Failed tabs, e.g. "Failed (3)" (§10.3). */
private fun tabLabel(tab: TransactionTab, state: TransactionListUiState): String = when (tab) {
    TransactionTab.ALL -> tab.label
    TransactionTab.SUCCESS -> "${tab.label} (${state.successCount})"
    TransactionTab.FAILED -> "${tab.label} (${state.failedCount})"
}
