package com.example.noticereader.feature.transaction.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The JSON body sent to Supabase (requirment.md §7.1). Field names are snake_case to match the
 * Postgres column names exactly.
 *
 * Times are ISO 8601 with the Asia/Dhaka offset. `occurred_at` may be null for a parsing-error
 * row (§8.2) — the row is still uploaded so the pattern can be fixed later from real data.
 */
@Serializable
data class TransactionDto(
    @SerialName("trx_id") val trxId: String,
    @SerialName("amount") val amount: Double?,
    @SerialName("sender") val sender: String?,
    @SerialName("raw_message") val rawMessage: String,
    @SerialName("occurred_at") val occurredAt: String?,
    @SerialName("received_at") val receivedAt: String,
)
