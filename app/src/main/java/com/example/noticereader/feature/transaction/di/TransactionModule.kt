package com.example.noticereader.feature.transaction.di

import androidx.room.Room
import com.example.noticereader.feature.transaction.data.local.BkashDatabase
import com.example.noticereader.feature.transaction.data.repository.TransactionRepositoryImpl
import com.example.noticereader.feature.transaction.domain.repository.TransactionRepository
import com.example.noticereader.feature.transaction.presentation.TransactionListViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin wiring for the bKash transaction feature (technical_overview §5.3):
 * database, DAO, repository, ViewModel.
 */
val transactionModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            BkashDatabase::class.java,
            "bkash_db",
        ).build()
    }
    single { get<BkashDatabase>().transactionDao() }

    singleOf(::TransactionRepositoryImpl).bind<TransactionRepository>()

    viewModelOf(::TransactionListViewModel)
}
