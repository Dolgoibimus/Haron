package com.vamp.haron.data.observer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.service.FileIndexJobService

class ScreenOnReceiver : BroadcastReceiver() {

    private var lastIndexTime = 0L

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_ON) return

        val now = System.currentTimeMillis()
        // Throttle: re-index at most once per 30 minutes on screen on
        if (now - lastIndexTime < 30 * 60 * 1000L) return
        lastIndexTime = now
        EcosystemLogger.d(HaronConstants.TAG, "ScreenOnReceiver: screen on, triggering re-index")

        FileContentObserver.scheduleIndexJob(context, FileIndexJobService.JOB_ID_SCREEN_ON)
    }

    companion object {
        fun create(): Pair<ScreenOnReceiver, IntentFilter> {
            val receiver = ScreenOnReceiver()
            val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
            return receiver to filter
        }
    }
}
