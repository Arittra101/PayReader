package com.example.noticereader.feature.transaction.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.noticereader.feature.transaction.presentation.model.SetupStatus

/**
 * The setup checklist bottom sheet (requirment.md §10.2, §3.3). Shows the five checks that must all
 * pass for the status dot to go green, plus the time of the last successful upload.
 *
 * The app cannot flip most of these itself — a human does the manual device steps once (§3.3);
 * this sheet just reports whether each one is in place so the operator can confirm them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupSheet(
    status: SetupStatus,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Device setup", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(8.dp))

            ChecklistItem("Receive SMS permission", status.receiveSmsGranted)
            ChecklistItem("Read SMS permission", status.readSmsGranted)
            ChecklistItem("Notifications permission", status.notificationsGranted)
            ChecklistItem("Battery optimization disabled", status.batteryOptimizationDisabled)
            ChecklistItem("Keep-alive service running", status.keepAliveRunning)

            Spacer(Modifier.size(12.dp))
            Text(
                text = "Last successful upload: ${status.lastSuccessfulUpload ?: "none yet"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChecklistItem(label: String, done: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (done) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline
        Icon(
            imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (done) "Done" else "Not done",
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
