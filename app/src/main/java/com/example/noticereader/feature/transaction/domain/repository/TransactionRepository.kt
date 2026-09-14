package com.example.noticereader.feature.transaction.domain.repository

import com.example.noticereader.feature.transaction.domain.model.Transaction
import com.example.noticereader.feature.transaction.domain.model.TransactionStatus
import kotlinx.coroutines.flow.Flow

/**
 * The single entry point for reading and writing stored transactions.
 *
 * "Local first" (§1.1): every incoming SMS is saved here before any network call, and this
 * store is the source of truth. Inserts are idempotent on `trx_id` — re-processing the same
 * SMS never creates a second row (§1.1 rule 3, §8.3).
 */
interface TransactionRepository {

    /** Live stream of all transactions, newest first. Drives the UI directly (§10.7). */
    fun observeTransactions(): Flow<List<Transaction>>

    /**
     * Saves a transaction, ignoring it if its `trx_id` already exists.
     * @return true if a new row was inserted, false if it was a duplicate.
     */
    suspend fun save(transaction: Transaction): Boolean

    /** Updates the upload status of one row (used by the upload flow). */
    suspend fun updateStatus(trxId: String, status: TransactionStatus)

    /** Rows not yet accepted by the backend — for the upload worker (§7.3, §8.2). */
    suspend fun pendingUploads(): List<Transaction>

    /** Marks a row as accepted by the backend; parse-error rows keep their status (§8.2). */
    suspend fun markUploaded(trxId: String)

    /** The newest payment time stored, used to bound the catch-up inbox scan (§6.1). */
    suspend fun newestOccurredAtMillis(): Long?

    /** Epoch millis of the last successful upload, or null. Drives the setup checklist (§10.2). */
    suspend fun lastSuccessfulUploadAtMillis(): Long?
}
