package com.vamp.haron.data.observer

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.service.FileIndexWorker

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

        val request = OneTimeWorkRequestBuilder<FileIndexWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            FileIndexWorker.WORK_NAME_ONE_TIME,
            ExistingWorkPolicy.KEEP,
            request
        )
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
}
