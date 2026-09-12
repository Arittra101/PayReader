package com.example.noticereader.feature.transaction.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.noticereader.feature.transaction.domain.model.Transaction
import com.example.noticereader.feature.transaction.domain.model.TransactionStatus
import com.example.noticereader.feature.transaction.domain.repository.TransactionRepository
import com.example.noticereader.feature.transaction.presentation.model.TransactionListUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes the live transaction list, already split per tab (§10.3), for the single screen.
 *
 * The stream comes straight from Room, so a new SMS appears and a PENDING row turns into
 * SUCCESS without any manual refresh (§10.7).
 */
class TransactionListViewModel(
    repository: TransactionRepository,
) : ViewModel() {

    val state: StateFlow<TransactionListUiState> =
        repository.observeTransactions()
            .map { transactions -> transactions.toUiState() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TransactionListUiState(),
            )

    private fun List<Transaction>.toUiState(): TransactionListUiState {
        val rows = map(TransactionFormatting::toRow)

        // Success tab: only rows the backend accepted.
        val success = rows.filter { it.status == TransactionStatus.SUCCESS }

        // Failed tab: real upload failures AND parsing errors — both need a human (§10.3).
        val failed = rows.filter {
            it.status == TransactionStatus.FAILED || it.status == TransactionStatus.PARSING_ERROR
        }

        return TransactionListUiState(
            allRows = rows,
            successRows = success,
            failedRows = failed,
            lastSmsRelative = lastSmsRelative(),
        )
    }

    private fun List<Transaction>.lastSmsRelative(): String? {
        val newestReceivedMillis = maxOfOrNull { it.receivedAt.toInstant().toEpochMilli() }
            ?: return null
        return TransactionFormatting.relativeFromMillis(
            eventMillis = newestReceivedMillis,
            nowMillis = System.currentTimeMillis(),
        )
    }
}
