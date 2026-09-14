package com.example.noticereader.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

/**
 * Central place for the app's notification channels. Two low-importance channels, both silent —
 * these notifications exist only because Android requires one for a foreground service (§9) and
 * for expedited work on older APIs; the operator should never be bothered by them.
 */
object Notifications {

    const val KEEP_ALIVE_CHANNEL_ID = "keep_alive"
    const val WORK_CHANNEL_ID = "background_work"

    const val KEEP_ALIVE_NOTIFICATION_ID = 1001
    const val UPLOAD_NOTIFICATION_ID = 1002
    const val SCAN_NOTIFICATION_ID = 1003

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                KEEP_ALIVE_CHANNEL_ID,
                "Keep-alive service",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Keeps the app active so it reliably catches bKash SMS." },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                WORK_CHANNEL_ID,
                "Background sync",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Uploading payments and scanning the inbox." },
        )
    }
}
