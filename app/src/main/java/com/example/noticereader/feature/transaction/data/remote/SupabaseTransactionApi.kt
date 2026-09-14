package com.example.noticereader.feature.transaction.data.remote

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

/**
 * Uploads one transaction to Supabase via the PostgREST REST API (requirment.md §7).
 *
 * Insert is idempotent by design: a repeated `trx_id` hits the primary-key constraint and comes
 * back as `409 Conflict`, which [insert] treats as success — so re-processing the same SMS never
 * creates a second row and never surfaces an error the app has to special-case (§7.1, §8.3).
 *
 * We deliberately do NOT send `Prefer: resolution=ignore-duplicates`. That turns the request into a
 * PostgREST upsert, which requires SELECT privilege — but Row Level Security grants the anon role
 * insert-only (§7.2), so an upsert fails with 401 on every row. `return=minimal` is used for the
 * same reason: the anon role has no SELECT right, so asking PostgREST to echo back the inserted
 * row would fail.
 */
class SupabaseTransactionApi(
    private val client: HttpClient,
    private val config: SupabaseConfig,
) {

    /**
     * Sends one row.
     * @return true if Supabase accepted it (or it already existed); false for any rejection.
     * @throws Exception on a network/IO error, so the caller can retry (§7.3).
     */
    suspend fun insert(dto: TransactionDto): Boolean {
        if (!config.isConfigured) {
            Log.w(TAG, "Supabase not configured (missing URL/key in local.properties); skipping upload")
            return false
        }

        val response: HttpResponse = client.post(config.transactionsEndpoint) {
            header("apikey", config.anonKey)
            header("Authorization", "Bearer ${config.anonKey}")
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            setBody(dto)
        }

        // 409 Conflict = the row's trx_id already exists, i.e. it is safely there already (§8.3).
        val ok = response.status.isSuccess() || response.status == HttpStatusCode.Conflict
        if (!ok) {
            Log.w(TAG, "Supabase rejected insert of ${dto.trxId}: ${response.status}")
        }
        return ok
    }

    private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299

    private companion object {
        const val TAG = "SupabaseApi"
    }
}
