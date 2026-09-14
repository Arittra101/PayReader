package com.example.noticereader.feature.transaction.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * Stateful wrapper (technical_overview §4): obtains the ViewModel, collects state, and hands a
 * plain snapshot plus callbacks to the stateless [TransactionListScreen].
 */
@Composable
fun TransactionListScreenRoot(
    modifier: Modifier = Modifier,
    viewModel: TransactionListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val setupStatus by viewModel.setupStatus.collectAsStateWithLifecycle()
    val keepAliveEnabled by viewModel.keepAliveEnabled.collectAsStateWithLifecycle()

    // Ask for SMS / notification permissions on first launch (§3.2).
    RequestSmsPermissionsOnce()

    // Re-read permissions and battery state whenever the app comes back to the foreground —
    // the operator may have just changed them in system Settings (§3.3).
    LifecycleResumeEffect(Unit) {
        viewModel.refreshSetupStatus()
        onPauseOrDispose { }
    }

    TransactionListScreen(
        state = state,
        setupStatus = setupStatus,
        keepAliveEnabled = keepAliveEnabled,
        onKeepAliveChange = viewModel::setKeepAlive,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retryUploads,
        onSelectFilter = viewModel::setDateFilter,
        onCustomRange = viewModel::setCustomRange,
        onToggleSort = viewModel::toggleSortDirection,
        modifier = modifier,
    )
}
