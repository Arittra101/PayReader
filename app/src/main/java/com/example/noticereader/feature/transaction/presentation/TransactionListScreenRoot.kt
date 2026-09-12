package com.example.noticereader.feature.transaction.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * Stateful wrapper (technical_overview §4): obtains the ViewModel, collects state, and hands a
 * plain snapshot to the stateless [TransactionListScreen].
 */
@Composable
fun TransactionListScreenRoot(
    modifier: Modifier = Modifier,
    viewModel: TransactionListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Ask for SMS / notification permissions on first launch (§3.2).
    RequestSmsPermissionsOnce()

    TransactionListScreen(
        state = state,
        // SMS capture is driven by the manifest receiver, which is always registered.
        isListening = true,
        modifier = modifier,
    )
}
