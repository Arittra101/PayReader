package com.example.noticereader.feature.transaction.data.repository

import com.example.noticereader.feature.transaction.data.local.TransactionDao
import com.example.noticereader.feature.transaction.data.mapper.toDomain
import com.example.noticereader.feature.transaction.data.mapper.toEntity
import com.example.noticereader.feature.transaction.domain.model.Transaction
import com.example.noticereader.feature.transaction.domain.model.TransactionStatus
import com.example.noticereader.feature.transaction.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepositoryImpl(
    private val dao: TransactionDao,
) : TransactionRepository {

    override fun observeTransactions(): Flow<List<Transaction>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(transaction: Transaction): Boolean {
        val insertedRowId = dao.insertIgnore(transaction.toEntity())
        // Room returns -1 when the insert was ignored because the trxId already exists.
        return insertedRowId != -1L
    }

    override suspend fun updateStatus(trxId: String, status: TransactionStatus) {
        dao.updateStatus(trxId, status.name)
    }

    override suspend fun pendingUploads(): List<Transaction> =
        dao.getNotUploaded().map { it.toDomain() }

    override suspend fun markUploaded(trxId: String) = dao.markUploaded(trxId)

    override suspend fun newestOccurredAtMillis(): Long? = dao.newestOccurredAtMillis()

    override suspend fun lastSuccessfulUploadAtMillis(): Long? = dao.lastSuccessfulUploadMillis()
}
