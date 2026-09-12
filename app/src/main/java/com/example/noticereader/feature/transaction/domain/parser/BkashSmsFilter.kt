package com.example.noticereader.feature.transaction.domain.parser

/**
 * Decides whether an incoming SMS is a bKash "money received" message worth parsing.
 *
 * This is the gate from requirment.md §4.2. A message is accepted only when ALL three
 * checks pass. Everything else is rejected silently. The exact sender match matters:
 * anyone can fake an SMS body, so a loose filter only widens the attack surface (§4.2).
 */
object BkashSmsFilter {

    /** The one sender id we accept, matched exactly (case-insensitive). */
    private const val BKASH_SENDER = "bKash"

    /** Any one of these phrases marks a "received money" message. */
    private val RECEIVE_KEYWORDS = listOf(
        "received deposit",
        "You have received",
        "received Tk",
    )

    /** Every bKash transaction message carries a transaction id under this label. */
    private const val TRX_ID_MARKER = "TrxID"

    /**
     * @param senderId the SMS sender address (e.g. "bKash"), from the SMS metadata.
     * @param body the SMS text.
     * @return true only if this is a bKash receive message we should parse and store.
     */
    fun isBkashReceiveMessage(senderId: String, body: String): Boolean {
        val isFromBkash = senderId.trim().equals(BKASH_SENDER, ignoreCase = true)
        val hasReceiveKeyword = RECEIVE_KEYWORDS.any { body.contains(it, ignoreCase = true) }
        val hasTrxId = body.contains(TRX_ID_MARKER, ignoreCase = true)

        return isFromBkash && hasReceiveKeyword && hasTrxId
    }
}
