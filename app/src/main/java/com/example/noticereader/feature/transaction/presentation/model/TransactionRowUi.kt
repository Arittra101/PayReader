package com.example.noticereader.feature.transaction.presentation.model

import com.example.noticereader.feature.transaction.domain.model.TransactionStatus

/**
 * One transaction as the list shows it (§10.5). All values are already formatted for display,
 * so the Composable stays dumb and preview-friendly.
 */
data class TransactionRowUi(
    val trxId: String,
    /** e.g. "৳ 500.00". Null for a parsing error, where the amount is unknown. */
    val amountText: String?,
    /** The sender name, or the first line of the raw SMS for a parsing error. */
    val title: String,
    /** e.g. "09 Sep 2026, 10:08 PM", in Asia/Dhaka. */
    val timeText: String,
    val status: TransactionStatus,
    val isParsingError: Boolean,
)
