package com.example.noticereader.feature.transaction.parser

import com.example.noticereader.feature.transaction.domain.parser.BkashSmsFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [BkashSmsFilter] (requirment.md §4.2).
 *
 * A message is accepted only when it is from the exact sender "bKash", carries a receive
 * keyword, and contains "TrxID". Everything else is rejected.
 */
class BkashSmsFilterTest {

    private val validBody =
        "You have received Tk 500.00 from 01712345678. " +
            "Fee Tk 0.00. Balance Tk 540.58. TrxID DI94C4NOI8 at 09/09/2026 22:08"

    @Test
    fun `accepts a genuine bKash receive message`() {
        assertTrue(BkashSmsFilter.isBkashReceiveMessage("bKash", validBody))
    }

    @Test
    fun `sender match is case-insensitive`() {
        assertTrue(BkashSmsFilter.isBkashReceiveMessage("BKASH", validBody))
    }

    @Test
    fun `rejects a message from any other sender, even with a TrxID`() {
        // The exact-sender rule: a fake body from a different sender must be ignored (§4.2).
        assertFalse(BkashSmsFilter.isBkashReceiveMessage("bKash-Promo", validBody))
        assertFalse(BkashSmsFilter.isBkashReceiveMessage("01712345678", validBody))
    }

    @Test
    fun `rejects a bKash message without a receive keyword`() {
        val nonReceive =
            "Your payment of Tk 500.00 was successful. TrxID DI94C4NOI8 at 09/09/2026 22:08"
        assertFalse(BkashSmsFilter.isBkashReceiveMessage("bKash", nonReceive))
    }

    @Test
    fun `rejects a receive message without a TrxID`() {
        val noTrxId = "You have received Tk 500.00 from 01712345678. Fee Tk 0.00."
        assertFalse(BkashSmsFilter.isBkashReceiveMessage("bKash", noTrxId))
    }
}
