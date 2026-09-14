package com.example.noticereader.feature.transaction.data.mapper

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.noticereader.feature.transaction.data.local.TransactionEntity
import com.example.noticereader.feature.transaction.data.remote.TransactionDto
import com.example.noticereader.feature.transaction.domain.BkashTime
import com.example.noticereader.feature.transaction.domain.model.Transaction
import com.example.noticereader.feature.transaction.domain.model.TransactionStatus
import com.example.noticereader.feature.transaction.domain.parser.SmsParseResult
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Converts between the parser output, the domain [Transaction], and the Room [TransactionEntity].
 */

/**
 * Turns a parse outcome into a [Transaction] ready to store.
 *
 *  - A [SmsParseResult.Success] becomes a [TransactionStatus.PENDING] row with all fields.
 *  - A [SmsParseResult.Failure] becomes a [TransactionStatus.PARSING_ERROR] row that keeps only
 *    the raw message. Its id is synthesised from the raw text so that re-scanning the same SMS
 *    (§6) maps to the same row and is ignored, instead of piling up duplicates.
 */
fun SmsParseResult.toTransaction(receivedAt: OffsetDateTime): Transaction = when (this) {
    is SmsParseResult.Success -> Transaction(
        trxId = data.trxId,
        amount = data.amount,
        sender = data.sender,
        occurredAt = data.occurredAt,
        rawMessage = data.rawMessage,
        receivedAt = receivedAt,
        status = TransactionStatus.PENDING,
    )

    is SmsParseResult.Failure -> Transaction(
        trxId = syntheticParsingErrorId(rawMessage),
        amount = null,
        sender = null,
        occurredAt = null,
        rawMessage = rawMessage,
        receivedAt = receivedAt,
        status = TransactionStatus.PARSING_ERROR,
    )
}

private fun syntheticParsingErrorId(rawMessage: String): String =
    "PARSE_ERR_${rawMessage.hashCode()}"

@RequiresApi(Build.VERSION_CODES.O)
fun Transaction.toEntity(): TransactionEntity {
    val occurredAtMillis = occurredAt?.toInstant()?.toEpochMilli()
    val receivedAtMillis = receivedAt.toInstant().toEpochMilli()
    return TransactionEntity(
        trxId = trxId,
        amount = amount,
        sender = sender,
        occurredAtEpochMillis = occurredAtMillis,
        receivedAtEpochMillis = receivedAtMillis,
        rawMessage = rawMessage,
        status = status.name,
        // Sort by payment time when we have it, otherwise by when we received the SMS.
        sortTimestamp = occurredAtMillis ?: receivedAtMillis,
    )
}

/**
 * Turns a stored [Transaction] into the JSON body for Supabase (§7.1). Times are ISO 8601 with
 * the Asia/Dhaka offset so the backend keeps the real payment time.
 */
fun Transaction.toDto(): TransactionDto = TransactionDto(
    trxId = trxId,
    amount = amount,
    sender = sender,
    rawMessage = rawMessage,
    occurredAt = occurredAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    receivedAt = receivedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
)

fun TransactionEntity.toDomain(): Transaction = Transaction(
    trxId = trxId,
    amount = amount,
    sender = sender,
    occurredAt = occurredAtEpochMillis?.let(BkashTime::fromEpochMillis),
    rawMessage = rawMessage,
    receivedAt = BkashTime.fromEpochMillis(receivedAtEpochMillis),
    status = runCatching { TransactionStatus.valueOf(status) }
        .getOrDefault(TransactionStatus.PARSING_ERROR),
)
