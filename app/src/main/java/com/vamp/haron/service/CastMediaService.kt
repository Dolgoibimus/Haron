package com.vamp.haron.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.MainActivity
import com.vamp.haron.R
import com.vamp.haron.common.constants.HaronConstants

class CastMediaService : Service() {

    companion object {
        const val CHANNEL_ID = "haron_cast_media"
        const val NOTIFICATION_ID = 2002
        const val ACTION_START = "com.vamp.haron.CAST_MEDIA_START"
        const val ACTION_STOP = "com.vamp.haron.CAST_MEDIA_STOP"
        const val ACTION_PLAY_PAUSE = "com.vamp.haron.CAST_PLAY_PAUSE"
        const val ACTION_DISCONNECT = "com.vamp.haron.CAST_DISCONNECT"
        const val EXTRA_TITLE = "title"
        const val EXTRA_DEVICE_NAME = "device_name"

        var isRunning: Boolean = false
            private set

        /** Callbacks set by CastViewModel */
        var onPlayPauseRequested: (() -> Unit)? = null
        var onDisconnectRequested: (() -> Unit)? = null
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var title: String = ""
    private var deviceName: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always call startForeground immediately to avoid ForegroundServiceDidNotStartInTimeException
        startForeground(NOTIFICATION_ID, buildNotification(isPlaying = true))

        when (intent?.action) {
            ACTION_START -> {
                title = intent.getStringExtra(EXTRA_TITLE) ?: ""
                deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: ""
                updateNotification(isPlaying = true)
                acquireWakeLock()
                isRunning = true
                EcosystemLogger.d(HaronConstants.TAG, "CastMediaService started: title=$title, device=$deviceName")
            }
            ACTION_PLAY_PAUSE -> {
                onPlayPauseRequested?.invoke()
                EcosystemLogger.d(HaronConstants.TAG, "CastMediaService play/pause")
            }
            ACTION_DISCONNECT -> {
                onDisconnectRequested?.invoke()
                stopSelfAndCleanup()
            }
            ACTION_STOP -> {
                stopSelfAndCleanup()
            }
            else -> {
                stopSelfAndCleanup()
            }
        }
        return START_NOT_STICKY
    }

    fun updatePlayingState(isPlaying: Boolean) {
        updateNotification(isPlaying)
    }

    private fun stopSelfAndCleanup() {
        isRunning = false
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        EcosystemLogger.d(HaronConstants.TAG, "CastMediaService stopped")
    }

    private fun buildNotification(isPlaying: Boolean = true): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, CastMediaService::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePi = PendingIntent.getService(
            this, 1, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseLabel = if (isPlaying) "Pause" else "Play"

        val disconnectIntent = Intent(this, CastMediaService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPi = PendingIntent.getService(
            this, 2, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subtitle = getString(R.string.cast_media_notification_text, deviceName)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title.ifEmpty { getString(R.string.cast_title) })
            .setContentText(subtitle)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentPi)
            .addAction(playPauseIcon, playPauseLabel, playPausePi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.cast_notification_disconnect), disconnectPi)
            .build()
    }

    private fun updateNotification(isPlaying: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(isPlaying))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.cast_media_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.setShowBadge(false)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "haron:cast_service")
            .apply { acquire(30 * 60 * 1000L) } // 30 min max
        EcosystemLogger.d(HaronConstants.TAG, "CastMediaService WakeLock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
            EcosystemLogger.d(HaronConstants.TAG, "CastMediaService WakeLock released")
        }
        wakeLock = null
    }

    override fun onDestroy() {
        isRunning = false
        releaseWakeLock()
        super.onDestroy()
    }
}
