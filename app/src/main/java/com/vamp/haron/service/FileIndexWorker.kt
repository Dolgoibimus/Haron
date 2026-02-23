package com.vamp.haron.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vamp.haron.domain.repository.SearchRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class FileIndexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val searchRepository: SearchRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            searchRepository.indexAllFiles()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME_ONE_TIME = "file_index_one_time"
        const val WORK_NAME_SCREEN_ON = "file_index_screen_on"
    }
}
