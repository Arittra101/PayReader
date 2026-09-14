package com.example.noticereader.feature.transaction.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

/**
 * Turns the keep-alive service (requirment.md §9) on and off and remembers the operator's choice.
 *
 * The chosen state is persisted so the setup checklist (§10.2) can show whether the service should
 * be running. It is deliberately not restarted after a reboot (§9.1); the operator re-enables it by
 * hand when they next open the app.
 */
class KeepAliveController(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))

    /** True when the operator has switched the service on. Drives the UI toggle and the dot (§10.2). */
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        _isEnabled.value = enabled
        if (enabled) KeepAliveService.start(context) else KeepAliveService.stop(context)
    }

    private companion object {
        const val PREFS = "keep_alive_prefs"
        const val KEY_ENABLED = "enabled"
    }
}
