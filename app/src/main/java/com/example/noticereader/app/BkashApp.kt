package com.example.noticereader.app

import android.app.Application
import com.example.noticereader.feature.transaction.di.transactionModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Application entry point. Its only job is to start dependency injection.
 * SMS capture runs through the manifest-declared receiver, so nothing here needs to
 * keep the app alive (requirment.md §1.1 rule 4).
 */
class BkashApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@BkashApp)
            modules(transactionModule)
        }
    }
}
