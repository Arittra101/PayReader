package com.example.noticereader.feature.transaction.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.noticereader.R
import com.example.noticereader.core.notification.Notifications

/**
 * A do-nothing foreground service whose only job is to keep the app counted as active, so Android
 * and OEM battery managers don't push it into a low group or force-stop it (requirment.md §9).
 *
 * SMS capture works with this off; the service only makes upload timing more reliable. It uses the
 * `specialUse` type, not `dataSync`, because a `dataSync` FGS is capped at 6h/24h on Android 15+,
 * which would break an always-on service every day (§9.2). It does not auto-start after reboot
 * (§9.1) — the operator turns it back on by hand.
 */
class KeepAliveService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.ensureChannels(this)

        val notification = NotificationCompat.Builder(this, Notifications.KEEP_ALIVE_CHANNEL_ID)
            .setContentTitle("Listening for bKash SMS")
            .setContentText("Keeping the app active for reliable payment capture.")
            .setSmallIcon(R.drawable.ic_app_logo)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, Notifications.KEEP_ALIVE_NOTIFICATION_ID, notification, type)

        // Restart if the system kills the service while it should be running.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }
}
