package com.vamp.haron.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.vamp.haron.service.FileIndexWorker
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HaronWorkerFactory @Inject constructor(
    private val fileIndexWorkerFactory: FileIndexWorker.Factory
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            FileIndexWorker::class.java.name -> fileIndexWorkerFactory.create(appContext, workerParameters)
            else -> null
        }
    }
}
