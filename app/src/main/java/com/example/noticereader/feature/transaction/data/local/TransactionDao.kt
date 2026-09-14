package com.example.noticereader.feature.transaction.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    /** Live stream for the UI, newest first (§10.4, §10.7). */
    @Query("SELECT * FROM transactions ORDER BY sortTimestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    /**
     * Inserts a row, ignoring it if the `trxId` already exists. This is the whole of the
     * duplicate protection (§8.3): a re-processed SMS returns -1 and changes nothing.
     * @return the new row id, or -1 if it was a duplicate.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: TransactionEntity): Long

    @Query("UPDATE transactions SET status = :status WHERE trxId = :trxId")
    suspend fun updateStatus(trxId: String, status: String)

    /** Every row not yet accepted by the backend — the upload worker's work list (§7.3, §8.2). */
    @Query("SELECT * FROM transactions WHERE uploaded = 0")
    suspend fun getNotUploaded(): List<TransactionEntity>

    /**
     * Marks a row as accepted by the backend. A normal PENDING/FAILED row also flips to SUCCESS;
     * a PARSING_ERROR row keeps its status so it stays in the Failed tab for a human (§8.2, §10.3).
     */
    @Query(
        "UPDATE transactions SET uploaded = 1, " +
            "status = CASE WHEN status IN ('PENDING', 'FAILED') THEN 'SUCCESS' ELSE status END " +
            "WHERE trxId = :trxId",
    )
    suspend fun markUploaded(trxId: String)

    /** The newest payment time stored, used to bound the catch-up inbox scan (§6.1). */
    @Query("SELECT MAX(occurredAtEpochMillis) FROM transactions")
    suspend fun newestOccurredAtMillis(): Long?

    /** When the last row was accepted by the backend — shown on the setup checklist (§10.2). */
    @Query("SELECT MAX(receivedAtEpochMillis) FROM transactions WHERE status = 'SUCCESS'")
    suspend fun lastSuccessfulUploadMillis(): Long?
}
