package com.example.noticereader.feature.transaction.presentation

import com.example.noticereader.feature.transaction.domain.model.Transaction
import com.example.noticereader.feature.transaction.presentation.model.TransactionRowUi
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns a domain [Transaction] into a display-ready [TransactionRowUi] (§10.5, §10.7).
 * Kept pure so it is easy to read and test.
 */
object TransactionFormatting {

    /** Bangladeshi Taka sign, two decimals (§10.7). */
    private const val TAKA = "৳" // ৳

    // e.g. "09 Sep 2026, 10:08 PM" — readable, not ISO (§10.7).
    private val DISPLAY_TIME: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

    fun toRow(transaction: Transaction): TransactionRowUi {
        val isParsingError = transaction.amount == null || transaction.sender == null

        return TransactionRowUi(
            trxId = transaction.trxId,
            amountText = transaction.amount?.let { formatAmount(it) },
            title = transaction.sender ?: firstLineOf(transaction.rawMessage),
            timeText = formatTime(transaction.occurredAt ?: transaction.receivedAt),
            status = transaction.status,
            isParsingError = isParsingError,
        )
    }

    fun formatAmount(amount: Double): String =
        "$TAKA ${String.format(Locale.ENGLISH, "%,.2f", amount)}"

    fun formatTime(time: OffsetDateTime): String = time.format(DISPLAY_TIME)

    /**
     * A short "how long ago" label for the status bar (§10.2). Coarse buckets are enough.
     */
    fun relativeFromMillis(eventMillis: Long, nowMillis: Long): String {
        val seconds = ((nowMillis - eventMillis) / 1000).coerceAtLeast(0)
        return when {
            seconds < 60 -> "just now"
            seconds < 3600 -> "${seconds / 60} min ago"
            seconds < 86_400 -> "${seconds / 3600} hr ago"
            else -> "${seconds / 86_400} day(s) ago"
        }
    }

    private fun firstLineOf(raw: String): String =
        raw.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "Unparsed message" }
}
