package com.vamp.haron.data.observer

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.service.FileIndexJobService

class FileContentObserver(
    private val context: Context
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private var lastTriggerTime = 0L

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        val now = System.currentTimeMillis()
        // Debounce: at most once per 30 seconds to avoid constant re-indexing
        if (now - lastTriggerTime < 30_000) return
        lastTriggerTime = now
        EcosystemLogger.d(HaronConstants.TAG, "FileContentObserver: MediaStore changed, triggering re-index")

        scheduleIndexJob(context, FileIndexJobService.JOB_ID_CONTENT_CHANGE)
    }

    fun register() {
        context.contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri("external"),
            true,
            this
        )
    }

    fun unregister() {
        context.contentResolver.unregisterContentObserver(this)
    }

    companion object {
        fun scheduleIndexJob(context: Context, jobId: Int) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            // Skip if this job is already pending
            if (scheduler.getPendingJob(jobId) != null) return
            val jobInfo = JobInfo.Builder(
                jobId,
                ComponentName(context, FileIndexJobService::class.java)
            )
                .setRequiresDeviceIdle(false)
                .setRequiresCharging(false)
                .setOverrideDeadline(5_000) // run within 5s
                .build()
            scheduler.schedule(jobInfo)
        }
    }
}
