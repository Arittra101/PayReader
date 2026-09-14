package com.example.noticereader.feature.transaction.work

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.noticereader.R
import com.example.noticereader.core.notification.Notifications
import com.example.noticereader.feature.transaction.data.mapper.toDto
import com.example.noticereader.feature.transaction.data.remote.SupabaseTransactionApi
import com.example.noticereader.feature.transaction.domain.model.TransactionStatus
import com.example.noticereader.feature.transaction.domain.repository.TransactionRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Uploads every not-yet-confirmed row to Supabase (requirment.md §7.3).
 *
 * Picks up all PENDING and FAILED rows, sends each one, and updates its status: SUCCESS on
 * acceptance, FAILED otherwise. A failed row is never deleted (§1.1 rule 1) — if anything failed
 * or a network error occurred, the worker asks WorkManager to retry with the exponential backoff
 * set at enqueue time. The local row is always the source of truth; the upload can retry forever.
 */
class UploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val repository: TransactionRepository by inject()
    private val api: SupabaseTransactionApi by inject()

    override suspend fun doWork(): Result {
        val pending = repository.pendingUploads()
        if (pending.isEmpty()) return Result.success()

        var anyFailed = false
        for (transaction in pending) {
            try {
                if (api.insert(transaction.toDto())) {
                    // Marks it uploaded; a PARSING_ERROR row keeps its status so it stays in
                    // the Failed tab for a human (§8.2, §10.3).
                    repository.markUploaded(transaction.trxId)
                } else {
                    markFailed(transaction.trxId, transaction.status)
                    anyFailed = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Upload of ${transaction.trxId} failed; will retry", e)
                markFailed(transaction.trxId, transaction.status)
                anyFailed = true
            }
        }

        // Retry the whole batch with backoff if any row did not make it through.
        return if (anyFailed) Result.retry() else Result.success()
    }

    /**
     * Records an upload failure. A PARSING_ERROR row keeps its status (it still needs a human,
     * §8.2); everything else moves to FAILED so it shows in the Failed tab and is retried (§7.3).
     */
    private suspend fun markFailed(trxId: String, status: TransactionStatus) {
        if (status != TransactionStatus.PARSING_ERROR) {
            repository.updateStatus(trxId, TransactionStatus.FAILED)
        }
    }

    // Required when this runs as expedited work on API < 31, where it needs a foreground slot.
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, Notifications.WORK_CHANNEL_ID)
            .setContentTitle("Syncing payments")
            .setSmallIcon(R.drawable.ic_app_logo)
            .setOngoing(true)
            .build()
        return ForegroundInfo(Notifications.UPLOAD_NOTIFICATION_ID, notification)
    }

    private companion object {
        const val TAG = "UploadWorker"
    }
}
