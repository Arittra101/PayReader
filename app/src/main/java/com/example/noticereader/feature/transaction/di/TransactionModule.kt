package com.example.noticereader.feature.transaction.di

import androidx.room.Room
import com.example.noticereader.feature.transaction.data.local.BkashDatabase
import com.example.noticereader.feature.transaction.data.local.SmsInboxScanner
import com.example.noticereader.feature.transaction.data.remote.SupabaseConfig
import com.example.noticereader.feature.transaction.data.remote.SupabaseTransactionApi
import com.example.noticereader.feature.transaction.data.repository.TransactionRepositoryImpl
import com.example.noticereader.feature.transaction.domain.repository.TransactionRepository
import com.example.noticereader.feature.transaction.presentation.TransactionListViewModel
import com.example.noticereader.feature.transaction.service.KeepAliveController
import com.example.noticereader.feature.transaction.work.WorkScheduler
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin wiring for the bKash transaction feature: database, DAO, repository, Supabase upload
 * (§7), catch-up scan (§6), keep-alive service (§9), and the ViewModel.
 */
val transactionModule = module {
    // --- Local storage (Phase 2) ---
    single {
        Room.databaseBuilder(
            androidContext(),
            BkashDatabase::class.java,
            "bkash_db",
        ).addMigrations(BkashDatabase.MIGRATION_1_2).build()
    }
    single { get<BkashDatabase>().transactionDao() }
    singleOf(::TransactionRepositoryImpl).bind<TransactionRepository>()

    // --- Supabase upload (Phase 3) ---
    single { SupabaseConfig() }
    single {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
            install(Logging)
        }
    }
    singleOf(::SupabaseTransactionApi)

    // --- Reliability (Phase 4) ---
    single { WorkScheduler(androidContext()) }
    single { SmsInboxScanner(androidContext(), get()) }
    single { KeepAliveController(androidContext()) }

    // --- UI ---
    viewModel {
        TransactionListViewModel(
            repository = get(),
            workScheduler = get(),
            keepAliveController = get(),
            appContext = androidContext(),
        )
    }
}
