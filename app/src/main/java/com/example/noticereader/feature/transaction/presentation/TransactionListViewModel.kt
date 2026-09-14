package com.example.noticereader.feature.transaction.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.noticereader.feature.transaction.domain.BkashTime
import com.example.noticereader.feature.transaction.domain.model.Transaction
import com.example.noticereader.feature.transaction.domain.model.TransactionStatus
import com.example.noticereader.feature.transaction.domain.repository.TransactionRepository
import com.example.noticereader.feature.transaction.presentation.model.DateFilter
import com.example.noticereader.feature.transaction.presentation.model.SetupStatus
import com.example.noticereader.feature.transaction.presentation.model.SortDirection
import com.example.noticereader.feature.transaction.presentation.model.TransactionListUiState
import com.example.noticereader.feature.transaction.presentation.model.TransactionRowUi
import com.example.noticereader.feature.transaction.service.KeepAliveController
import com.example.noticereader.feature.transaction.work.WorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the single screen (§10): the live transaction list split per tab, the shared date filter
 * and sort order (§10.4), the setup checklist (§10.2), and the keep-alive toggle (§9). The list
 * stream comes straight from Room, so a new SMS appears and a PENDING row turns into SUCCESS with
 * no manual refresh (§10.7).
 */
class TransactionListViewModel(
    private val repository: TransactionRepository,
    private val workScheduler: WorkScheduler,
    private val keepAliveController: KeepAliveController,
    private val appContext: Context,
) : ViewModel() {

    // Default on app open: Today, newest first (§10.4).
    private val dateFilter = MutableStateFlow<DateFilter>(DateFilter.Today)
    private val sortDirection = MutableStateFlow(SortDirection.NEWEST_FIRST)

    val state: StateFlow<TransactionListUiState> =
        combine(
            repository.observeTransactions(),
            dateFilter,
            sortDirection,
        ) { transactions, filter, sort ->
            transactions.toUiState(filter, sort)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TransactionListUiState(),
        )

    /** Whether the operator has switched the keep-alive service on (§9). */
    val keepAliveEnabled: StateFlow<Boolean> = keepAliveController.isEnabled

    private val _setupStatus = MutableStateFlow(SetupStatus())
    val setupStatus: StateFlow<SetupStatus> = _setupStatus.asStateFlow()

    init {
        // A catch-up scan runs every time the app is opened (§6.1).
        workScheduler.enqueueCatchUpNow()
        refreshSetupStatus()
    }

    // --- Filter & sort (§10.4) ---

    fun setDateFilter(filter: DateFilter) {
        dateFilter.value = filter
    }

    /** Applies a custom [start, endExclusive) range chosen from the date range picker. */
    fun setCustomRange(startMillis: Long, endExclusiveMillis: Long) {
        dateFilter.value = DateFilter.Custom(startMillis, endExclusiveMillis)
    }

    fun toggleSortDirection() {
        sortDirection.value = when (sortDirection.value) {
            SortDirection.NEWEST_FIRST -> SortDirection.OLDEST_FIRST
            SortDirection.OLDEST_FIRST -> SortDirection.NEWEST_FIRST
        }
    }

    // --- Actions (§10.6) ---

    /** Pull-to-refresh: run the catch-up inbox scan (§10.6). */
    fun refresh() {
        workScheduler.enqueueCatchUpNow()
        refreshSetupStatus()
    }

    /**
     * Retry uploads (§10.6). The upload worker picks up every not-yet-synced row, so this covers
     * both a single FAILED row's retry button and the "Retry all" action.
     */
    fun retryUploads() {
        workScheduler.enqueueUpload()
    }

    fun setKeepAlive(enabled: Boolean) {
        keepAliveController.setEnabled(enabled)
        refreshSetupStatus()
    }

    /** Re-read permissions and battery state — call whenever the app returns to the foreground. */
    fun refreshSetupStatus() {
        viewModelScope.launch {
            val lastUploadMillis = repository.lastSuccessfulUploadAtMillis()
            _setupStatus.value = SetupStatus(
                receiveSmsGranted = SetupStatusReader.isReceiveSmsGranted(appContext),
                readSmsGranted = SetupStatusReader.isReadSmsGranted(appContext),
                notificationsGranted = SetupStatusReader.isNotificationsGranted(appContext),
                batteryOptimizationDisabled = SetupStatusReader.isBatteryOptimizationDisabled(appContext),
                keepAliveRunning = keepAliveController.isEnabled.value,
                lastSuccessfulUpload = lastUploadMillis?.let {
                    TransactionFormatting.formatTime(BkashTime.fromEpochMillis(it))
                },
            )
        }
    }

    private fun List<Transaction>.toUiState(
        filter: DateFilter,
        sort: SortDirection,
    ): TransactionListUiState {
        val now = System.currentTimeMillis()

        // "Last SMS" reflects the newest message overall, ignoring the date filter (§10.2).
        val lastSms = lastSmsRelative(now)

        val visible = map(TransactionFormatting::toRow)
            .filter { filter.matches(it.sortTimestampMillis, now) }
            .sortedBy(sort)

        // Success tab: only rows the backend accepted.
        val success = visible.filter { it.status == TransactionStatus.SUCCESS }

        // Failed tab: real upload failures AND parsing errors — both need a human (§10.3).
        val failed = visible.filter {
            it.status == TransactionStatus.FAILED || it.status == TransactionStatus.PARSING_ERROR
        }

        return TransactionListUiState(
            allRows = visible,
            successRows = success,
            failedRows = failed,
            lastSmsRelative = lastSms,
            dateFilter = filter,
            sortDirection = sort,
        )
    }

    private fun List<TransactionRowUi>.sortedBy(sort: SortDirection): List<TransactionRowUi> =
        when (sort) {
            SortDirection.NEWEST_FIRST -> sortedByDescending { it.sortTimestampMillis }
            SortDirection.OLDEST_FIRST -> sortedBy { it.sortTimestampMillis }
        }

    private fun List<Transaction>.lastSmsRelative(nowMillis: Long): String? {
        val newestReceivedMillis = maxOfOrNull { it.receivedAt.toInstant().toEpochMilli() }
            ?: return null
        return TransactionFormatting.relativeFromMillis(
            eventMillis = newestReceivedMillis,
            nowMillis = nowMillis,
        )
    }
}
