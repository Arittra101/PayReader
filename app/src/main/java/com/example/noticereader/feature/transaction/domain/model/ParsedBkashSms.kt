package com.example.noticereader.feature.transaction.domain.model

import java.time.OffsetDateTime

/**
 * The payment details pulled out of a single bKash "money received" SMS.
 *
 * This is what the parser (§5) produces from the raw SMS body. It holds only the
 * fields that come *from the message text*. The moment the phone actually saw the
 * SMS (`received_at`) is added later, by the caller, because the parser cannot know it.
 *
 * See requirment.md §5.2 for the field rules.
 */
data class ParsedBkashSms(
    /** Alphanumeric id after "TrxID ". Unique everywhere — never duplicated. */
    val trxId: String,

    /**
     * The received amount, in Taka. This is the "Tk" value *before* the word "Fee".
     * It is never the Fee value and never the Balance value (§5.3).
     */
    val amount: Double,

    /** Who the money came from: a bank name or a mobile number. */
    val sender: String,

    /**
     * The real payment time, read from the end of the SMS body and interpreted as
     * Asia/Dhaka (UTC+6). We trust this over the phone clock, because a delayed SMS
     * still carries the true time in its text (§5.2).
     */
    val occurredAt: OffsetDateTime,

    /** The full, original SMS body. Always kept, even when parsing partly fails. */
    val rawMessage: String,
)
