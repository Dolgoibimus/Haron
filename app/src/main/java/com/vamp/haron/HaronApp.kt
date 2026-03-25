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
import com.vamp.haron.data.shizuku.ShizukuManager
import dagger.hilt.android.HiltAndroidApp
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

    @Inject
    lateinit var shizukuManager: ShizukuManager

    private var contentObserver: FileContentObserver? = null
    private var screenOnReceiver: ScreenOnReceiver? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Lightweight init — must stay on main thread
        EcosystemPreferences.init(this)
        EcosystemLogger.init(this)
        ReadingPositionManager.init(readingPositionDao)

        // Catch uncaught exceptions and write to log before process dies
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stackTrace = throwable.stackTraceToString()
                EcosystemLogger.e("Haron/CRASH", "FATAL on ${thread.name}: ${throwable.message}\n$stackTrace")
            } catch (_: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // ContentObserver moved to MainActivity lifecycle (register onResume, unregister onPause)

        // Register ScreenOnReceiver for auto-indexing on screen unlock
        val (receiver, filter) = ScreenOnReceiver.create()
        screenOnReceiver = receiver
        registerReceiver(receiver, filter)

        // Shizuku — listeners only, lightweight
        shizukuManager.init()

        // Heavy init — move off main thread to prevent 14s+ lag
        appScope.launch {
            EcosystemDatabase.getInstance(this@HaronApp)
        }
        appScope.launch {
            PDFBoxResourceLoader.init(this@HaronApp)
        }
        appScope.launch {
            tesseractOcr.init()
        }
        // Cast requires main thread — defer to after onCreate finishes
        Handler(Looper.getMainLooper()).post {
            castManager.initialize()
        }
    }

    override fun onTerminate() {
        screenOnReceiver?.let { unregisterReceiver(it) }
        shizukuManager.cleanup()
        super.onTerminate()
    }
}
