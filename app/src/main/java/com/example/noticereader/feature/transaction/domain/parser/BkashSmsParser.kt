package com.example.noticereader.feature.transaction.domain.parser

import com.example.noticereader.feature.transaction.domain.BkashTime
import com.example.noticereader.feature.transaction.domain.model.ParsedBkashSms
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Pulls the payment details out of a bKash "money received" SMS body (requirment.md §5).
 *
 * Assumes the message already passed [BkashSmsFilter]. The parser never throws: if any
 * required field is missing or malformed, it returns [SmsParseResult.Failure] with the
 * raw message untouched, so the caller can store it as a PARSING_ERROR row (§8.2).
 *
 * ### The two shapes we handle
 * Both real formats end the same way ("... Fee Tk X. Balance Tk Y. TrxID Z at DD/MM/YYYY HH:mm"):
 *
 *  - P2P / cash-in:  "You have received Tk 500.00 from 01712345678. Fee ..."
 *  - Bank deposit:   "You have received deposit from iBanking of Tk 500.00 from City Bank. Fee ..."
 *
 * ### Why the regexes are anchored
 * The message contains three "Tk" values — received, Fee, and Balance. A naive regex grabs
 * the wrong one, which produces *silently wrong data* instead of a visible error. So we only
 * ever look for the amount in the text *before* the word "Fee" (§5.3).
 */
object BkashSmsParser {

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    /** "TrxID DI94C4NOI8" -> captures "DI94C4NOI8". */
    private val TRX_ID_REGEX = Regex("""TrxID\s+([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)

    /** "Tk 500.00" or "Tk 1,500" -> captures the number, with optional thousands commas. */
    private val AMOUNT_REGEX = Regex("""Tk\s*([\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

    /** "09/09/2026 22:08" at the end of the message. */
    private val DATE_REGEX = Regex("""(\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2})""")

    /**
     * The amount phrase that appears *inside* the sender text of the deposit format,
     * e.g. "iBanking of Tk 500.00 from City Bank". Removing "Tk 500.00 from " leaves
     * the clean sender "iBanking of City Bank".
     */
    private val EMBEDDED_AMOUNT_REGEX =
        Regex("""Tk\s+[\d,]+(?:\.\d+)?\s+from\s+""", RegexOption.IGNORE_CASE)

    fun parse(rawMessage: String): SmsParseResult {
        val trxId = TRX_ID_REGEX.find(rawMessage)?.groupValues?.get(1)
            ?: return failure(SmsParseResult.Failure.Reason.MISSING_TRX_ID, rawMessage)

        val amount = parseAmount(rawMessage)
            ?: return failure(SmsParseResult.Failure.Reason.MISSING_AMOUNT, rawMessage)

        val sender = parseSender(rawMessage)
            ?: return failure(SmsParseResult.Failure.Reason.MISSING_SENDER, rawMessage)

        val occurredAt = parseOccurredAt(rawMessage)
            ?: return failure(SmsParseResult.Failure.Reason.MISSING_OR_INVALID_DATE, rawMessage)

        return SmsParseResult.Success(
            ParsedBkashSms(
                trxId = trxId,
                amount = amount,
                sender = sender,
                occurredAt = occurredAt,
                rawMessage = rawMessage,
            )
        )
    }

    /**
     * Reads the received amount. Looks only at the text before "Fee" so the Fee and
     * Balance values can never be picked by mistake. Within that region we take the
     * last "Tk" value, which is the received amount in every known format.
     */
    private fun parseAmount(body: String): Double? {
        val amountRegion = regionBeforeFee(body)
        val match = AMOUNT_REGEX.findAll(amountRegion).lastOrNull() ?: return null
        val number = match.groupValues[1].replace(",", "")
        return number.toDoubleOrNull()
    }

    /**
     * Reads the sender: the text between the first "from" and the "Fee" section.
     * For the deposit format this region also contains the amount phrase
     * ("iBanking of Tk 500.00 from City Bank"), which we strip out to leave a clean name.
     */
    private fun parseSender(body: String): String? {
        val fromKeyword = "from "
        val fromIndex = body.indexOf(fromKeyword, ignoreCase = true)
        if (fromIndex < 0) return null

        val feeIndex = body.indexOf("Fee", ignoreCase = true)
        val end = if (feeIndex > fromIndex) feeIndex else body.length

        val rawSender = body.substring(fromIndex + fromKeyword.length, end)
            .trim()
            .trimEnd('.', ' ')

        val cleaned = rawSender
            .replace(EMBEDDED_AMOUNT_REGEX, "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        return cleaned.ifBlank { null }
    }

    /**
     * Reads the payment time from the end of the body ("DD/MM/YYYY HH:mm") and pins it
     * to Asia/Dhaka (+06:00). Returns null if the date is missing or malformed.
     */
    private fun parseOccurredAt(body: String): java.time.OffsetDateTime? {
        val dateText = DATE_REGEX.find(body)?.groupValues?.get(1) ?: return null
        return try {
            LocalDateTime.parse(dateText, DATE_FORMAT).atOffset(BkashTime.OFFSET)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The slice of the message where the received amount lives: everything before "Fee".
     * Falls back to the text before "Balance", then the whole body, so a format without a
     * Fee line still avoids grabbing the Balance value.
     */
    private fun regionBeforeFee(body: String): String {
        val feeIndex = body.indexOf("Fee", ignoreCase = true)
        if (feeIndex >= 0) return body.substring(0, feeIndex)

        val balanceIndex = body.indexOf("Balance", ignoreCase = true)
        if (balanceIndex >= 0) return body.substring(0, balanceIndex)

        return body
    }

    private fun failure(reason: SmsParseResult.Failure.Reason, rawMessage: String) =
        SmsParseResult.Failure(reason, rawMessage)
}
