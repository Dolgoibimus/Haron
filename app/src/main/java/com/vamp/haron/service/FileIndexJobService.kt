package com.vamp.haron.service

import android.app.job.JobParameters
import android.app.job.JobService
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.repository.SearchRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FileIndexJobService : JobService() {

    @Inject
    lateinit var searchRepository: SearchRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        EcosystemLogger.d(HaronConstants.TAG, "FileIndexJobService: started")
        scope.launch {
            try {
                searchRepository.indexAllFiles()
                EcosystemLogger.d(HaronConstants.TAG, "FileIndexJobService: completed successfully")
                jobFinished(params, false)
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "FileIndexJobService: failed: ${e.message}")
                jobFinished(params, true) // reschedule
            }
        }
        return true // work is async
    }

    override fun onStopJob(params: JobParameters): Boolean {
        scope.cancel()
        return true // reschedule if stopped
    }

    companion object {
        const val JOB_ID_CONTENT_CHANGE = 1001
        const val JOB_ID_SCREEN_ON = 1002
    }
}
