package com.example.noticereader.feature.transaction.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * Reads the current device-setup state (requirment.md §3.3, §10.2): which permissions are granted
 * and whether battery optimization is off. Kept separate from the ViewModel so the runtime checks
 * are in one place and easy to re-read whenever the app returns to the foreground.
 */
object SetupStatusReader {

    fun isPermissionGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun isReceiveSmsGranted(context: Context) =
        isPermissionGranted(context, Manifest.permission.RECEIVE_SMS)

    fun isReadSmsGranted(context: Context) =
        isPermissionGranted(context, Manifest.permission.READ_SMS)

    fun isNotificationsGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true // Not a runtime permission before Android 13.
        }

    /** True when Doze won't delay this app's uploads (§3.3). */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        val power = context.getSystemService<PowerManager>() ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }
}
