package com.example.noticereader.feature.transaction.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The local database for bKash transactions — the app's source of truth (§1.1).
 * Kept separate from the legacy notification database so the two features stay independent.
 */
@Database(
    entities = [TransactionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class BkashDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}
