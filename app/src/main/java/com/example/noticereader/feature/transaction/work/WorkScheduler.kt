package com.example.noticereader.feature.transaction.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * The single place that enqueues WorkManager jobs (requirment.md §6, §7). Holds only the app
 * context, so it can be injected anywhere (receiver, ViewModel, Application).
 */
class WorkScheduler(private val context: Context) {

    private val workManager get() = WorkManager.getInstance(context)

    /**
     * Uploads all pending/failed rows now (§7.3). Expedited so payment confirmation is not delayed
     * when the app has been idle (§7.4), with a network constraint and exponential backoff starting
     * at 10s. APPEND_OR_REPLACE means an SMS that arrives mid-upload still gets its own run.
     */
    fun enqueueUpload() {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(UPLOAD_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Runs the catch-up inbox scan once, right now (§6.1) — on app open and pull-to-refresh. */
    fun enqueueCatchUpNow() {
        val request = OneTimeWorkRequestBuilder<CatchUpWorker>().build()
        workManager.enqueueUniqueWork(CATCH_UP_NOW_WORK, ExistingWorkPolicy.KEEP, request)
    }

    /** Runs the catch-up scan every 15 minutes (§6.1). Registered once at app start. */
    fun schedulePeriodicCatchUp() {
        val request = PeriodicWorkRequestBuilder<CatchUpWorker>(15, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork(
            CATCH_UP_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val UPLOAD_WORK = "bkash_upload"
        const val CATCH_UP_NOW_WORK = "bkash_catch_up_now"
        const val CATCH_UP_PERIODIC_WORK = "bkash_catch_up_periodic"
    }
}
