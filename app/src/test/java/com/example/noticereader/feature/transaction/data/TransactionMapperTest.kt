package com.example.noticereader.feature.transaction.data

import com.example.noticereader.feature.transaction.data.mapper.toTransaction
import com.example.noticereader.feature.transaction.domain.BkashTime
import com.example.noticereader.feature.transaction.domain.model.TransactionStatus
import com.example.noticereader.feature.transaction.domain.parser.BkashSmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests that a parse outcome maps to the right stored [com.example.noticereader.feature.transaction.domain.model.Transaction]
 * (requirment.md §4.3, §8.2).
 */
class TransactionMapperTest {

    private val receivedAt = BkashTime.now()

    @Test
    fun `a parsed message becomes a PENDING transaction`() {
        val message =
            "You have received Tk 500.00 from 01712345678. " +
                "Fee Tk 0.00. Balance Tk 540.58. TrxID AB12CD34EF at 10/09/2026 09:05"

        val transaction = BkashSmsParser.parse(message).toTransaction(receivedAt)

        assertEquals(TransactionStatus.PENDING, transaction.status)
        assertEquals("AB12CD34EF", transaction.trxId)
        assertEquals(500.00, transaction.amount!!, 0.0)
    }

    @Test
    fun `an unparsable message becomes a PARSING_ERROR transaction that keeps the raw text`() {
        val message = "You have received some money but the format is weird. TrxID missing"

        val transaction = BkashSmsParser.parse(message).toTransaction(receivedAt)

        assertEquals(TransactionStatus.PARSING_ERROR, transaction.status)
        assertNull(transaction.amount)
        assertNull(transaction.sender)
        assertNull(transaction.occurredAt)
        assertEquals(message, transaction.rawMessage)
    }

    @Test
    fun `the same unparsable text always maps to the same id, so a re-scan is idempotent`() {
        // A catch-up scan (§6) re-reads the same SMS; the synthetic id must be stable so the
        // unique-trxId constraint ignores the duplicate instead of piling up rows.
        val message = "Broken bKash message with no fields"

        val first = BkashSmsParser.parse(message).toTransaction(receivedAt)
        val second = BkashSmsParser.parse(message).toTransaction(BkashTime.now())

        assertEquals(first.trxId, second.trxId)
    }
}
