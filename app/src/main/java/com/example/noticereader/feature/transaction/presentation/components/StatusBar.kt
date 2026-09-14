package com.example.noticereader.feature.transaction.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * The always-visible status row at the top of the screen (§10.2): a coloured dot, a "Listening"
 * label, when the last SMS was seen, and the keep-alive service toggle (§9).
 *
 * The dot is green only when the whole device setup is correct ([isReady]). Tapping the row (not
 * the switch) opens the setup checklist bottom sheet via [onOpenSetup].
 */
@Composable
fun StatusBar(
    isReady: Boolean,
    lastSmsRelative: String?,
    keepAliveEnabled: Boolean,
    onKeepAliveChange: (Boolean) -> Unit,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSetup)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            val dotColor = if (isReady) Color(0xFF2E7D32) else Color(0xFFC62828)
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isReady) "Listening" else "Setup needed",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = lastSmsRelative?.let { "Last SMS: $it" } ?: "No SMS yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = keepAliveEnabled, onCheckedChange = onKeepAliveChange)
        }
    }
}

@Preview
@Composable
private fun StatusBarPreview() {
    StatusBar(
        isReady = true,
        lastSmsRelative = "2 min ago",
        keepAliveEnabled = true,
        onKeepAliveChange = {},
        onOpenSetup = {},
    )
}
