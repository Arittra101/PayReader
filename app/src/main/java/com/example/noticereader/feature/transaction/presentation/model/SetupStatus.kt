package com.example.noticereader.feature.transaction.presentation.model

/**
 * The device-setup checklist shown in the status bottom sheet (requirment.md §10.2, §3.3).
 *
 * The dot at the top of the screen is green only when all of the boolean checks pass — this is the
 * operator's one-glance signal that the app is set up to reliably catch payments.
 */
data class SetupStatus(
    val receiveSmsGranted: Boolean = false,
    val readSmsGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val batteryOptimizationDisabled: Boolean = false,
    val keepAliveRunning: Boolean = false,
    /** Human-readable time of the last successful upload, or null if none yet. */
    val lastSuccessfulUpload: String? = null,
) {
    /** All five required checks pass (§10.2). The last-upload time is informational only. */
    val isFullyReady: Boolean
        get() = receiveSmsGranted &&
            readSmsGranted &&
            notificationsGranted &&
            batteryOptimizationDisabled &&
            keepAliveRunning
}
