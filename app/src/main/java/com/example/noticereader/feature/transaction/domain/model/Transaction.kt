package com.example.noticereader.feature.transaction.domain.model

import java.time.OffsetDateTime

/**
 * A stored payment record — the app's source of truth (requirment.md §1.1 rule 1).
 *
 * For a normally parsed message all fields are present. For a [TransactionStatus.PARSING_ERROR]
 * row, [amount], [sender] and [occurredAt] are null because they could not be read; only
 * [rawMessage] and [receivedAt] are guaranteed. The UI shows those rows differently (§10.5).
 */
data class Transaction(
    /** Unique everywhere. For parsing errors this is a synthetic, stable id (see mapper). */
    val trxId: String,
    val amount: Double?,
    val sender: String?,
    val occurredAt: OffsetDateTime?,
    val rawMessage: String,
    /** When the phone actually saw the SMS. Always known. */
    val receivedAt: OffsetDateTime,
    val status: TransactionStatus,
)
