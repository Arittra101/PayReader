package com.example.noticereader.feature.transaction.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.noticereader.feature.transaction.presentation.model.TransactionRowUi

/**
 * The detail bottom sheet opened by tapping a row (§10.6): every field plus the full raw SMS, and a
 * copy button for the transaction id. A parsing-error row shows what little is known (the raw text);
 * amount and sender are absent because they could not be read (§8.2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    row: TransactionRowUi,
    onCopyTrxId: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = row.amountText ?: "Unreadable amount",
                style = MaterialTheme.typography.headlineSmall,
            )
            StatusPill(status = row.status, modifier = Modifier.padding(vertical = 8.dp))

            Field("Sender", row.senderText ?: "—")
            Field("Transaction ID", if (row.isParsingError) "—" else row.trxId)
            Field("Payment time", row.timeText)
            Field("Received", row.receivedTimeText)

            Text(
                text = "Raw message",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(text = row.rawMessage, style = MaterialTheme.typography.bodyMedium)

            if (!row.isParsingError) {
                OutlinedButton(
                    onClick = onCopyTrxId,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Text(text = "Copy TrxID", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
