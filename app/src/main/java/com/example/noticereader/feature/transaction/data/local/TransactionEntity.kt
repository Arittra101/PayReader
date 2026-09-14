package com.example.noticereader.feature.transaction.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The Room row for one transaction (requirment.md §7.1).
 *
 * `trxId` is the primary key, which is what enforces "one row per transaction" — inserting the
 * same SMS twice is ignored (§1.1 rule 3, §8.3).
 *
 * Times are stored as epoch milliseconds (UTC instant) so ordering is a simple numeric compare.
 * They are always displayed back in Asia/Dhaka via [com.example.noticereader.feature.transaction.domain.BkashTime].
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val trxId: String,
    val amount: Double?,
    val sender: String?,
    /** Payment time from the SMS body. Null for parsing errors. */
    val occurredAtEpochMillis: Long?,
    /** When the phone saw the SMS. Always present. */
    val receivedAtEpochMillis: Long,
    val rawMessage: String,
    /** [com.example.noticereader.feature.transaction.domain.model.TransactionStatus] name. */
    val status: String,
    /**
     * Whether the backend has accepted this row. Tracked separately from [status] so a
     * PARSING_ERROR row can be uploaded (§8.2) while still showing in the Failed tab for a human
     * (§10.3) — its status stays PARSING_ERROR even after a successful upload.
     */
    val uploaded: Boolean = false,
    /**
     * The value the list is sorted by: `occurredAt` when known, otherwise `receivedAt`.
     * Sorting by this (newest first) satisfies §10.4 while still placing parsing-error rows
     * (which have no occurred time) sensibly.
     */
    val sortTimestamp: Long,
)
