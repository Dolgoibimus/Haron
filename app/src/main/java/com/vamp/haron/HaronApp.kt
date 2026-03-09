package com.vamp.haron

import android.app.Application
import androidx.work.Configuration
import com.vamp.haron.di.HaronWorkerFactory
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.vamp.core.db.EcosystemDatabase
import com.vamp.core.db.EcosystemPreferences
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.data.observer.FileContentObserver
import com.vamp.haron.data.observer.ScreenOnReceiver
import com.vamp.haron.common.util.TesseractOcr
import com.vamp.haron.data.cast.GoogleCastManager
import com.vamp.haron.data.db.dao.ReadingPositionDao
import com.vamp.haron.data.reading.ReadingPositionManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HaronApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HaronWorkerFactory

    @Inject
    lateinit var castManager: GoogleCastManager

    @Inject
    lateinit var tesseractOcr: TesseractOcr

    @Inject
    lateinit var readingPositionDao: ReadingPositionDao

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
        ReadingPositionManager.init(readingPositionDao)

        // Initialize Google Cast (graceful degradation — no-op if GMS absent)
        castManager.initialize()

        // Copy Tesseract trained data (async-safe, skips if already copied)
        tesseractOcr.init()

        // Catch uncaught exceptions and write to log before process dies
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stackTrace = throwable.stackTraceToString()
                EcosystemLogger.e("Haron/CRASH", "FATAL on ${thread.name}: ${throwable.message}\n$stackTrace")
            } catch (_: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }

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
