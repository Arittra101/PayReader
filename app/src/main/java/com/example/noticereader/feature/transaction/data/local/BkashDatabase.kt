package com.example.noticereader.feature.transaction.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The local database for bKash transactions — the app's source of truth (§1.1).
 * Kept separate from the legacy notification database so the two features stay independent.
 */
@Database(
    entities = [TransactionEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class BkashDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        /** v2 adds the `uploaded` flag so parse-error rows can be uploaded yet stay flagged (§8.2). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN uploaded INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
