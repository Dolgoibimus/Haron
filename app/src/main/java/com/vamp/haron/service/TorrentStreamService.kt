package com.vamp.haron.service

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
import com.vamp.haron.R
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.TorrentStreamState
import com.vamp.haron.domain.repository.TorrentStreamRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service for torrent streaming.
 * Keeps the process alive while downloading + streaming to VLC.
 */
class TorrentStreamService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ServiceEntryPoint {
        fun torrentStreamRepository(): TorrentStreamRepository
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var torrentRepo: TorrentStreamRepository

    companion object {
        private const val TAG = "TorrentService"
        private const val CHANNEL_ID = "torrent_stream"
        private const val NOTIFICATION_ID = 9901
        private const val ACTION_STOP = "com.vamp.haron.TORRENT_STOP"
        private const val EXTRA_URI = "uri"
        private const val EXTRA_FILE_INDEX = "file_index"
        private const val NO_PEERS_TIMEOUT_MS = 60_000L

        fun start(context: Context, uri: String, fileIndex: Int = -1) {
            val intent = Intent(context, TorrentStreamService::class.java).apply {
                putExtra(EXTRA_URI, uri)
                putExtra(EXTRA_FILE_INDEX, fileIndex)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TorrentStreamService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val ep = EntryPointAccessors.fromApplication(applicationContext, ServiceEntryPoint::class.java)
        torrentRepo = ep.torrentStreamRepository()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            EcosystemLogger.i(HaronConstants.TAG, "$TAG: stop requested")
            torrentRepo.stopStream()
            stopSelf()
            return START_NOT_STICKY
        }

        val uri = intent?.getStringExtra(EXTRA_URI)
        val fileIndex = intent?.getIntExtra(EXTRA_FILE_INDEX, -1) ?: -1

        if (uri == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("Torrent stream…", 0))
        acquireWakeLock()

        scope.launch {
            torrentRepo.startStream(uri, fileIndex)
        }

        // Monitor progress + no-peers timeout
        monitorJob?.cancel()
        monitorJob = scope.launch {
            val startTime = System.currentTimeMillis()
            var hadPeers = false
            while (true) {
                delay(5_000)
                val state = torrentRepo.state.value
                when (state) {
                    is TorrentStreamState.Buffering -> {
                        if (state.piecesDownloaded > 0) hadPeers = true
                        updateNotification("Buffering: ${state.percent}%", state.percent)
                    }
                    is TorrentStreamState.Streaming -> {
                        hadPeers = true
                        updateNotification("${state.fileName} — ${state.progress}%", state.progress)
                    }
                    is TorrentStreamState.Ready -> {
                        hadPeers = true
                        updateNotification("Ready: ${state.fileName}", 100)
                    }
                    is TorrentStreamState.Error -> {
                        updateNotification("Error: ${state.message}", 0)
                        delay(3_000)
                        stopSelf()
                        return@launch
                    }
                    is TorrentStreamState.Idle -> {
                        // Stopped externally
                        stopSelf()
                        return@launch
                    }
                    is TorrentStreamState.FetchingMetadata -> {
                        updateNotification("Fetching metadata…", 0)
                    }
                }

                // No-peers timeout
                if (!hadPeers && System.currentTimeMillis() - startTime > NO_PEERS_TIMEOUT_MS) {
                    EcosystemLogger.e(HaronConstants.TAG, "$TAG: no peers found after ${NO_PEERS_TIMEOUT_MS / 1000}s, stopping")
                    // Set error state before stopping so UI shows toast
                    (torrentRepo as? com.vamp.haron.data.torrent.TorrentStreamRepositoryImpl)?.let {
                        // Can't set state directly — use stopStream which sets Idle
                    }
                    torrentRepo.stopStream()
                    stopSelf()
                    return@launch
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        releaseWakeLock()
        super.onDestroy()
        EcosystemLogger.d(HaronConstants.TAG, "$TAG: destroyed")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Torrent Stream",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Torrent download and streaming progress"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String, progress: Int): android.app.Notification {
        val stopIntent = Intent(this, TorrentStreamService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Torrent Stream")
            .setContentText(text)
            .setProgress(100, progress, progress == 0)
            .addAction(R.drawable.ic_launcher_foreground, getString(R.string.torrent_cancel), stopPi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String, progress: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text, progress))
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "haron:torrent").apply {
            acquire(30 * 60 * 1000L) // 30 min max
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
