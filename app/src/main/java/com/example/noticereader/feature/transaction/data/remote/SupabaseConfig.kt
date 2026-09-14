package com.example.noticereader.feature.transaction.data.remote

import com.example.noticereader.BuildConfig

/**
 * Supabase connection values (requirment.md §7.2), injected from BuildConfig so the keys live in
 * the git-ignored local.properties, not in source.
 *
 * [anonKey] is the publishable/anon key — never the service_role key. An APK can be unzipped and
 * read, and Row Level Security limits the anon role to insert-only on the transactions table, so a
 * leaked key can add junk rows but cannot read or delete payment history.
 */
data class SupabaseConfig(
    val baseUrl: String = BuildConfig.SUPABASE_URL,
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY,
) {
    /** True only when both values were provided. Upload is skipped (and retried) otherwise. */
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    /** The PostgREST endpoint for the transactions table. */
    val transactionsEndpoint: String get() = "${baseUrl.trimEnd('/')}/rest/v1/transactions"
}
