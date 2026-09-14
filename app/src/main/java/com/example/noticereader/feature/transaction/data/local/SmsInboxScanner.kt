package com.example.noticereader.feature.transaction.data.local

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.example.noticereader.feature.transaction.data.mapper.toTransaction
import com.example.noticereader.feature.transaction.domain.BkashTime
import com.example.noticereader.feature.transaction.domain.parser.BkashSmsFilter
import com.example.noticereader.feature.transaction.domain.parser.BkashSmsParser
import com.example.noticereader.feature.transaction.domain.repository.TransactionRepository

/**
 * Reads the SMS inbox to recover messages the live receiver missed (requirment.md §6): after a
 * force-stop, a reboot on a locked screen, or a reinstall. Android never replays a missed
 * `SMS_RECEIVED`, so reading the inbox is the only recovery.
 *
 * It scans bKash messages newer than the most recent stored payment minus a 24h overlap, runs them
 * through the same filter and parser as the live path (§4, §5), and saves them under the same
 * unique `trx_id` rule — so anything already stored is silently skipped (§8.3). Re-reading old
 * messages is harmless; overlap is always preferred over a gap.
 */
class SmsInboxScanner(
    private val context: Context,
    private val repository: TransactionRepository,
) {

    /** @return how many new rows were inserted. */
    suspend fun scan(): Int {
        val newestStored = repository.newestOccurredAtMillis()
        val sinceMillis = newestStored?.let { it - OVERLAP_MILLIS } ?: 0L

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        // Bound by receipt date; the exact bKash sender check is done per-row by the filter.
        val selection = "${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(sinceMillis.toString())

        var inserted = 0
        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    val sender = cursor.getString(addressIdx).orEmpty()
                    val body = cursor.getString(bodyIdx).orEmpty()
                    if (!BkashSmsFilter.isBkashReceiveMessage(sender, body)) continue

                    val receivedAt = BkashTime.fromEpochMillis(cursor.getLong(dateIdx))
                    val transaction = BkashSmsParser.parse(body).toTransaction(receivedAt)
                    if (repository.save(transaction)) inserted++
                }
            }
        } catch (e: Exception) {
            // Missing READ_SMS permission or a vendor content-provider quirk — never crash a scan.
            Log.w(TAG, "Inbox scan failed", e)
        }

        if (inserted > 0) Log.i(TAG, "Catch-up scan recovered $inserted message(s)")
        return inserted
    }

    private companion object {
        const val TAG = "SmsInboxScanner"

        /** 24h overlap so a delayed message near the boundary is never skipped (§6.1). */
        const val OVERLAP_MILLIS = 24L * 60 * 60 * 1000
    }
}
