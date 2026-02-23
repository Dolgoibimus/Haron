package com.vamp.haron.data.observer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vamp.haron.service.FileIndexWorker

class ScreenOnReceiver : BroadcastReceiver() {

    private var lastIndexTime = 0L

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_ON) return

        val now = System.currentTimeMillis()
        // Throttle: re-index at most once per 30 minutes on screen on
        if (now - lastIndexTime < 30 * 60 * 1000L) return
        lastIndexTime = now

        val request = OneTimeWorkRequestBuilder<FileIndexWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            FileIndexWorker.WORK_NAME_SCREEN_ON,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        fun create(): Pair<ScreenOnReceiver, IntentFilter> {
            val receiver = ScreenOnReceiver()
            val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
            return receiver to filter
        }
    }
}
