package com.vamp.haron.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.repository.SearchRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class FileIndexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val searchRepository: SearchRepository
) : CoroutineWorker(context, params) {

    @AssistedFactory
    interface Factory {
        fun create(context: Context, params: WorkerParameters): FileIndexWorker
    }

    override suspend fun doWork(): Result {
        EcosystemLogger.d(HaronConstants.TAG, "FileIndexWorker: started")
        return try {
            searchRepository.indexAllFiles()
            EcosystemLogger.d(HaronConstants.TAG, "FileIndexWorker: completed successfully")
            Result.success()
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "FileIndexWorker: failed, will retry: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME_ONE_TIME = "file_index_one_time"
        const val WORK_NAME_SCREEN_ON = "file_index_screen_on"
    }
}
