package com.example.noticereader.feature.transaction.presentation.model

import com.example.noticereader.feature.transaction.domain.model.TransactionStatus

/**
 * One transaction as the list shows it (§10.5). All values are already formatted for display,
 * so the Composable stays dumb and preview-friendly. It also carries the raw fields the detail
 * bottom sheet needs (§10.6) and the timestamp the list is filtered and sorted by (§10.4).
 */
data class TransactionRowUi(
    val trxId: String,
    /** e.g. "৳ 500.00". Null for a parsing error, where the amount is unknown. */
    val amountText: String?,
    /** The sender name, or the first line of the raw SMS for a parsing error. */
    val title: String,
    /** e.g. "09 Sep 2026, 10:08 PM", in Asia/Dhaka. The payment time when known. */
    val timeText: String,
    val status: TransactionStatus,
    val isParsingError: Boolean,
    /** Payment time (or received time for a parse error), epoch millis — for filtering/sorting. */
    val sortTimestampMillis: Long,
    /** Full original SMS text, shown in the detail sheet (§10.6). */
    val rawMessage: String,
    /** Sender as parsed; null for a parsing error. */
    val senderText: String?,
    /** When the phone saw the SMS, formatted; always present. */
    val receivedTimeText: String,
)
