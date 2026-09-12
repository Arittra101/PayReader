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

    /** Rows waiting to be uploaded or retried — used by the upload flow in later phases (§7.3). */
    @Query("SELECT * FROM transactions WHERE status IN (:statuses)")
    suspend fun getByStatus(statuses: List<String>): List<TransactionEntity>

    /** The newest payment time stored, used to bound the catch-up inbox scan (§6.1). */
    @Query("SELECT MAX(occurredAtEpochMillis) FROM transactions")
    suspend fun newestOccurredAtMillis(): Long?
}
