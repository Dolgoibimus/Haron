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
import com.vamp.haron.service.FileIndexWorker

class FileContentObserver(
    private val context: Context
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private var lastTriggerTime = 0L

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        val now = System.currentTimeMillis()
        // Debounce: at most once per 5 seconds
        if (now - lastTriggerTime < 5_000) return
        lastTriggerTime = now

        val request = OneTimeWorkRequestBuilder<FileIndexWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            FileIndexWorker.WORK_NAME_ONE_TIME,
            ExistingWorkPolicy.REPLACE,
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
