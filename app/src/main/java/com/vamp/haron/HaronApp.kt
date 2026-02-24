package com.vamp.haron

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.vamp.core.db.EcosystemDatabase
import com.vamp.core.db.EcosystemPreferences
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.data.observer.FileContentObserver
import com.vamp.haron.data.observer.ScreenOnReceiver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HaronApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private var contentObserver: FileContentObserver? = null
    private var screenOnReceiver: ScreenOnReceiver? = null

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        EcosystemPreferences.init(this)
        EcosystemLogger.init(this)
        EcosystemDatabase.getInstance(this)
        PDFBoxResourceLoader.init(this)

        // Register ContentObserver for media changes
        contentObserver = FileContentObserver(applicationContext).also { it.register() }

        // Register ScreenOnReceiver for auto-indexing on screen unlock
        val (receiver, filter) = ScreenOnReceiver.create()
        screenOnReceiver = receiver
        registerReceiver(receiver, filter)
    }

    override fun onTerminate() {
        contentObserver?.unregister()
        screenOnReceiver?.let { unregisterReceiver(it) }
        super.onTerminate()
    }
}
