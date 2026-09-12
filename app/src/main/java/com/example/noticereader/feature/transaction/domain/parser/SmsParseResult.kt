package com.example.noticereader.feature.transaction.domain.parser

import com.example.noticereader.feature.transaction.domain.model.ParsedBkashSms

/**
 * The outcome of trying to parse one bKash SMS body.
 *
 * The parser never throws. It always returns either [Success] with the extracted
 * details, or [Failure] with a reason and the untouched raw message. A [Failure]
 * is stored as a PARSING_ERROR row so a human can handle it by hand (§8.2).
 */
sealed interface SmsParseResult {

    data class Success(val data: ParsedBkashSms) : SmsParseResult

    data class Failure(
        val reason: Reason,
        val rawMessage: String,
    ) : SmsParseResult {

        /** Which required field could not be read. Used only for logging. */
        enum class Reason {
            MISSING_TRX_ID,
            MISSING_AMOUNT,
            MISSING_SENDER,
            MISSING_OR_INVALID_DATE,
        }
    }
}
