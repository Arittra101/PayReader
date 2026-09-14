package com.example.noticereader.app

import android.app.Application
import com.example.noticereader.core.notification.Notifications
import com.example.noticereader.feature.transaction.di.transactionModule
import com.example.noticereader.feature.transaction.work.WorkScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Application entry point. Starts dependency injection, prepares the notification channels, and
 * registers the periodic catch-up scan (requirment.md §6.1).
 *
 * SMS capture itself runs through the manifest-declared receiver, so nothing here keeps the app
 * alive (§1.1 rule 4). The keep-alive service is started only when the operator toggles it (§9.1).
 */
class BkashApp : Application(), KoinComponent {

    private val workScheduler: WorkScheduler by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@BkashApp)
            modules(transactionModule)
        }

        Notifications.ensureChannels(this)

        // Recover any SMS missed while the app wasn't running, every 15 minutes (§6.1).
        workScheduler.schedulePeriodicCatchUp()
    }
}
