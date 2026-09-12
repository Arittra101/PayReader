package com.example.noticereader.feature.transaction.parser

import com.example.noticereader.feature.transaction.domain.parser.BkashSmsParser
import com.example.noticereader.feature.transaction.domain.parser.SmsParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [BkashSmsParser] (requirment.md §5, §13 Phase 1).
 *
 * A wrong parser is the only failure mode that produces silently wrong data instead of a
 * visible error, so these are the most important tests in the project. Each covers a real
 * bKash message shape or a specific parsing trap.
 */
class BkashSmsParserTest {

    private fun success(message: String): SmsParseResult.Success {
        val result = BkashSmsParser.parse(message)
        assertTrue("Expected Success but was $result", result is SmsParseResult.Success)
        return result as SmsParseResult.Success
    }

    // ---- Happy paths ---------------------------------------------------------

    @Test
    fun `parses the deposit example from the spec`() {
        // The exact message from requirment.md §5.1.
        val message =
            "You have received deposit from iBanking of Tk 500.00 from City Bank. " +
                "Fee Tk 0.00. Balance Tk 540.58. TrxID DI94C4NOI8 at 09/09/2026 22:08"

        val data = success(message).data

        assertEquals("DI94C4NOI8", data.trxId)
        assertEquals(500.00, data.amount, 0.0)
        assertEquals("iBanking of City Bank", data.sender)
        // Time from the body, pinned to Asia/Dhaka (+06:00).
        assertEquals("2026-09-09T22:08+06:00", data.occurredAt.toString())
        assertEquals(message, data.rawMessage)
    }

    @Test
    fun `parses a P2P receive message with a mobile-number sender`() {
        val message =
            "You have received Tk 1,500.50 from 01712345678. " +
                "Fee Tk 0.00. Balance Tk 2040.58. TrxID AB12CD34EF at 10/09/2026 09:05"

        val data = success(message).data

        assertEquals("AB12CD34EF", data.trxId)
        assertEquals(1500.50, data.amount, 0.0)
        assertEquals("01712345678", data.sender)
        assertEquals("2026-09-10T09:05+06:00", data.occurredAt.toString())
    }

    // ---- The amount trap (§5.3) ---------------------------------------------

    @Test
    fun `takes the received amount, never the Fee or Balance value`() {
        // Received = 500, Fee = 10, Balance = 999999. A lazy regex grabs Fee or Balance.
        val message =
            "You have received Tk 500.00 from 01712345678. " +
                "Fee Tk 10.00. Balance Tk 999999.99. TrxID ZZ99YY88 at 01/01/2026 00:00"

        assertEquals(500.00, success(message).data.amount, 0.0)
    }

    // ---- Failures are graceful, never crashes (§8.2) -------------------------

    @Test
    fun `reports MISSING_TRX_ID when the id is absent`() {
        val message = "You have received Tk 500.00 from 01712345678. Fee Tk 0.00 at 09/09/2026 22:08"

        val result = BkashSmsParser.parse(message)

        assertTrue(result is SmsParseResult.Failure)
        assertEquals(
            SmsParseResult.Failure.Reason.MISSING_TRX_ID,
            (result as SmsParseResult.Failure).reason,
        )
        // The raw message is always preserved so a human can handle it by hand.
        assertEquals(message, result.rawMessage)
    }

    @Test
    fun `reports MISSING_OR_INVALID_DATE when the date is absent`() {
        val message = "You have received Tk 500.00 from 01712345678. Fee Tk 0.00. TrxID ZZ99YY88"

        val result = BkashSmsParser.parse(message)

        assertTrue(result is SmsParseResult.Failure)
        assertEquals(
            SmsParseResult.Failure.Reason.MISSING_OR_INVALID_DATE,
            (result as SmsParseResult.Failure).reason,
        )
    }

    @Test
    fun `does not throw on a completely unrelated message`() {
        // Should return a Failure, not blow up.
        val result = BkashSmsParser.parse("Hello, this is not a bKash message at all")
        assertTrue(result is SmsParseResult.Failure)
    }
}
