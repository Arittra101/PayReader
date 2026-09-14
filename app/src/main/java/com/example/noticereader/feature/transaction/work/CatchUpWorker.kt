package com.example.noticereader.feature.transaction.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.noticereader.feature.transaction.data.local.SmsInboxScanner
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Runs the catch-up inbox scan (requirment.md §6), then kicks off an upload so any recovered rows
 * are sent. Scheduled every 15 minutes and also run on app open and pull-to-refresh (§6.1, §10.6).
 *
 * Reads only the local inbox, so it needs no network. Newly recovered rows are handed to the
 * upload worker, which carries its own network constraint and retry.
 */
class CatchUpWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val scanner: SmsInboxScanner by inject()
    private val scheduler: WorkScheduler by inject()

    override suspend fun doWork(): Result {
        val recovered = scanner.scan()
        // Always trigger an upload: the scan may have recovered rows, and earlier PENDING/FAILED
        // rows may still be waiting to be sent.
        scheduler.enqueueUpload()
        Log.i(TAG, "Catch-up complete; recovered=$recovered")
        return Result.success()
    }

    private companion object {
        const val TAG = "CatchUpWorker"
    }
}
