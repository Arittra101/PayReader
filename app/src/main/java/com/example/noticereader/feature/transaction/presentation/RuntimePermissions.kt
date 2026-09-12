package com.example.noticereader.feature.transaction.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Requests the runtime permissions the app needs, once, on first launch (requirment.md §3.2):
 * RECEIVE_SMS, READ_SMS, and (Android 13+) POST_NOTIFICATIONS.
 *
 * The full manual device setup (battery, autostart, screen lock — §3.3) still has to be done by a
 * human; this only covers the permissions Android can grant through a dialog. The setup checklist
 * screen comes in a later phase.
 */
@Composable
fun RequestSmsPermissionsOnce() {
    val context = LocalContext.current

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* Result handled by the setup checklist in a later phase. */ }

    LaunchedEffect(Unit) {
        val notYetGranted = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (notYetGranted.isNotEmpty()) {
            launcher.launch(notYetGranted.toTypedArray())
        }
    }
}
