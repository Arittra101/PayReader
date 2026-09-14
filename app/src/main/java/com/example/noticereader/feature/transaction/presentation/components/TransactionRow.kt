package com.example.noticereader.feature.transaction.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.noticereader.feature.transaction.domain.model.TransactionStatus
import com.example.noticereader.feature.transaction.presentation.model.TransactionRowUi

/**
 * One transaction card (§10.5). The amount is the largest text — it is what the operator scans for.
 * A parsing-error row has no amount, so it shows the raw text instead and is clearly marked by its
 * PARSE ERROR pill.
 *
 * Tapping opens the detail sheet, long-pressing copies the TrxID, and a FAILED row shows a Retry
 * button that re-enqueues the upload (§10.6).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    row: TransactionRowUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.amountText ?: "Unreadable amount",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                StatusPill(status = row.status)
            }

            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (row.isParsingError) "—" else row.trxId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = row.timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Only a real upload failure can be retried; a parse error needs a human, not a retry.
            if (row.status == TransactionStatus.FAILED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }
    }
}

@Preview
@Composable
private fun TransactionRowPreview() {
    TransactionRow(
        row = TransactionRowUi(
            trxId = "DI94C4NOI8",
            amountText = "৳ 500.00",
            title = "iBanking of City Bank",
            timeText = "09 Sep 2026, 10:08 PM",
            status = TransactionStatus.SUCCESS,
            isParsingError = false,
            sortTimestampMillis = 0L,
            rawMessage = "You have received deposit ...",
            senderText = "iBanking of City Bank",
            receivedTimeText = "09 Sep 2026, 10:08 PM",
        ),
        onClick = {},
        onLongClick = {},
        onRetry = {},
    )
}
