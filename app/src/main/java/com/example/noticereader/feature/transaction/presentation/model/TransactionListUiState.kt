package com.example.noticereader.feature.transaction.presentation.model

/**
 * Everything the single screen needs to render (§10). The rows are pre-split per tab so the
 * Composable does no filtering itself.
 */
data class TransactionListUiState(
    val allRows: List<TransactionRowUi> = emptyList(),
    val successRows: List<TransactionRowUi> = emptyList(),
    val failedRows: List<TransactionRowUi> = emptyList(),
    /** Relative time of the most recent SMS, e.g. "2 min ago". Null if none yet. */
    val lastSmsRelative: String? = null,
) {
    val successCount: Int get() = successRows.size

    /** The number the operator watches (§10.3). */
    val failedCount: Int get() = failedRows.size

    fun rowsFor(tab: TransactionTab): List<TransactionRowUi> = when (tab) {
        TransactionTab.ALL -> allRows
        TransactionTab.SUCCESS -> successRows
        TransactionTab.FAILED -> failedRows
    }
}
