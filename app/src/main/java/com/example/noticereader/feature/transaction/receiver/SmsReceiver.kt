package com.example.noticereader.feature.transaction.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.noticereader.feature.transaction.data.mapper.toTransaction
import com.example.noticereader.feature.transaction.domain.BkashTime
import com.example.noticereader.feature.transaction.domain.parser.BkashSmsFilter
import com.example.noticereader.feature.transaction.domain.parser.BkashSmsParser
import com.example.noticereader.feature.transaction.domain.parser.SmsParseResult
import com.example.noticereader.feature.transaction.domain.repository.TransactionRepository
import com.example.noticereader.feature.transaction.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Catches incoming SMS in real time (requirment.md §4). Declared in the manifest so it works
 * even when the app is closed (§1.1 rule 4).
 *
 * The work is deliberately tiny — Android may kill the receiver after ~10 seconds (§4.3):
 *   1. filter the message (§4.2)
 *   2. parse it (§5)
 *   3. save it locally with status PENDING (or PARSING_ERROR) — this is the step that must finish
 *   4. (Phase 3) enqueue an expedited UploadWorker
 *
 * No network call happens here. Saving locally first is the whole design (§1.1 rule 1).
 */
class SmsReceiver : BroadcastReceiver(), KoinComponent {

    private val repository: TransactionRepository by inject()
    private val workScheduler: WorkScheduler by inject()

    // A tiny scope for the DB write. goAsync() keeps the receiver alive until it finishes.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // A long SMS arrives split into parts; join them back into one body.
        val senderId = messages.first().originatingAddress ?: return
        val body = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }

        if (!BkashSmsFilter.isBkashReceiveMessage(senderId, body)) return

        // Record the phone-clock time now, before any async work.
        val receivedAt = BkashTime.now()

        // goAsync() lets the short DB write finish after onReceive() returns.
        val pendingResult = goAsync()
        scope.launch {
            try {
                val result = BkashSmsParser.parse(body)
                val inserted = repository.save(result.toTransaction(receivedAt))

                if (result is SmsParseResult.Failure) {
                    Log.w(TAG, "bKash SMS failed to parse (${result.reason}); stored as PARSING_ERROR")
                }
                if (!inserted) {
                    Log.i(TAG, "Duplicate SMS ignored (trxId already stored)")
                }
                // Local write is done — now hand off to the expedited upload worker (§4.3, §7.3).
                // No network call happens in the receiver itself (§1.1 rule 1).
                if (inserted) workScheduler.enqueueUpload()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to store incoming SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SmsReceiver"
    }
}
