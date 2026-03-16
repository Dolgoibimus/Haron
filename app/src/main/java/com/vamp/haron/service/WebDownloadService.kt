package com.vamp.haron.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.MainActivity
import com.vamp.haron.R
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.SearchSource
import com.vamp.haron.domain.usecase.websearch.TorrentEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class DownloadProgress(
    val id: String,
    val fileName: String,
    val current: Long,
    val total: Long,
    val speed: Long,
    val isComplete: Boolean,
    val error: String? = null,
    val source: SearchSource
)

class WebDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_STICKY
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "download"
                val sourceStr = intent.getStringExtra(EXTRA_SOURCE) ?: SearchSource.OPEN_DIRECTORY.name
                val source = try { SearchSource.valueOf(sourceStr) } catch (_: Exception) { SearchSource.OPEN_DIRECTORY }
                val isMagnet = intent.getBooleanExtra(EXTRA_IS_MAGNET, false)
                startDownload(url, fileName, source, isMagnet)
            }
            ACTION_CANCEL -> {
                val id = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return START_STICKY
                cancelDownload(id)
            }
            ACTION_CANCEL_ALL -> {
                cancelAll()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (jobs.isEmpty()) {
            EcosystemLogger.d(HaronConstants.TAG, "WebDownloadService: onTaskRemoved — no active downloads, stopping")
            stopSelf()
        } else {
            EcosystemLogger.d(HaronConstants.TAG, "WebDownloadService: onTaskRemoved — ${jobs.size} downloads active, continuing")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        releaseLocks()
        super.onDestroy()
    }

    private fun startDownload(url: String, fileName: String, source: SearchSource, isMagnet: Boolean) {
        val id = UUID.randomUUID().toString()
        EcosystemLogger.d(HaronConstants.TAG, "WebDownload: start id=$id file=$fileName source=$source magnet=$isMagnet")

        // Add to downloads list
        _downloads.update { current ->
            current + DownloadProgress(
                id = id, fileName = fileName, current = 0, total = -1,
                speed = 0, isComplete = false, source = source
            )
        }

        startForegroundIfNeeded()

        val job = scope.launch {
            try {
                if (isMagnet) {
                    downloadTorrent(id, url, fileName, source)
                } else {
                    downloadHttp(id, url, fileName, source)
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "WebDownload: failed id=$id: ${e.message}")
                updateDownload(id) { it.copy(error = e.message, isComplete = true) }
            } finally {
                jobs.remove(id)
                checkStopSelf()
            }
        }
        jobs[id] = job
    }

    private suspend fun downloadHttp(id: String, url: String, fileName: String, source: SearchSource) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            updateDownload(id) { it.copy(error = "HTTP ${response.code}", isComplete = true) }
            response.close()
            return
        }

        val contentLength = response.body?.contentLength() ?: -1
        val inputStream = response.body?.byteStream() ?: run {
            updateDownload(id) { it.copy(error = "Empty response", isComplete = true) }
            response.close()
            return
        }

        val saveDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Haron"
        )
        if (!saveDir.exists()) saveDir.mkdirs()

        // Deduplicate filename
        var target = File(saveDir, fileName)
        var counter = 1
        while (target.exists()) {
            val nameWithoutExt = fileName.substringBeforeLast('.')
            val ext = fileName.substringAfterLast('.', "")
            target = if (ext.isNotEmpty()) {
                File(saveDir, "${nameWithoutExt}($counter).$ext")
            } else {
                File(saveDir, "${fileName}($counter)")
            }
            counter++
        }

        updateDownload(id) { it.copy(fileName = target.name, total = contentLength) }

        val buffer = ByteArray(8192)
        var totalRead = 0L
        var lastSpeedCheck = System.currentTimeMillis()
        var bytesInInterval = 0L

        target.outputStream().use { output ->
            inputStream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    totalRead += read
                    bytesInInterval += read

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastSpeedCheck
                    if (elapsed >= 500) {
                        val speed = (bytesInInterval * 1000) / elapsed
                        updateDownload(id) { it.copy(current = totalRead, speed = speed) }
                        updateNotification()
                        bytesInInterval = 0
                        lastSpeedCheck = now
                    }
                }
            }
        }
        response.close()

        EcosystemLogger.i(HaronConstants.TAG, "WebDownload: HTTP complete id=$id file=${target.name} size=$totalRead")
        updateDownload(id) { it.copy(current = totalRead, total = totalRead, isComplete = true, speed = 0) }
        updateNotification()
    }

    private suspend fun downloadTorrent(id: String, magnetUri: String, fileName: String, source: SearchSource) {
        val engine = torrentEngine ?: run {
            torrentEngine = TorrentEngine(applicationContext)
            torrentEngine!!
        }

        engine.download(magnetUri).collect { progress ->
            val displayName = if (progress.name.isNotEmpty()) progress.name else fileName
            updateDownload(id) {
                it.copy(
                    fileName = displayName,
                    current = (progress.progress * progress.totalSize).toLong(),
                    total = progress.totalSize,
                    speed = progress.downloadSpeed,
                    isComplete = progress.isComplete,
                    error = progress.error
                )
            }
            updateNotification()
        }
    }

    private fun cancelDownload(id: String) {
        EcosystemLogger.d(HaronConstants.TAG, "WebDownload: cancel id=$id")
        jobs[id]?.cancel()
        jobs.remove(id)
        _downloads.update { list -> list.filter { it.id != id } }
        updateNotification()
        checkStopSelf()
    }

    private fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        _downloads.value = emptyList()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateDownload(id: String, transform: (DownloadProgress) -> DownloadProgress) {
        _downloads.update { list ->
            list.map { if (it.id == id) transform(it) else it }
        }
    }

    private fun startForegroundIfNeeded() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Web Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "File downloads from internet"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val downloads = _downloads.value
        val active = downloads.filter { !it.isComplete && it.error == null }
        val completed = downloads.count { it.isComplete && it.error == null }

        val title = if (active.isNotEmpty()) {
            "Downloading ${active.size} file(s)"
        } else {
            "Downloads complete"
        }

        val text = if (active.isNotEmpty()) {
            val first = active.first()
            val percent = if (first.total > 0) (first.current * 100 / first.total) else 0
            "${first.fileName}: $percent%"
        } else {
            "$completed file(s) downloaded"
        }

        val cancelIntent = Intent(this, WebDownloadService::class.java).apply {
            action = ACTION_CANCEL_ALL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val progress = if (active.isNotEmpty() && active.first().total > 0) {
            val first = active.first()
            (first.current * 100 / first.total).toInt()
        } else 0

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, progress, active.isNotEmpty() && active.first().total <= 0)
            .setOngoing(active.isNotEmpty())
            .setSilent(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.cancel),
                cancelPendingIntent
            )
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun acquireLocks() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Haron:WebDownload")
        wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours max

        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Haron:WebDownload")
        wifiLock?.acquire()
    }

    private fun releaseLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
    }

    private fun checkStopSelf() {
        val active = _downloads.value.any { !it.isComplete && it.error == null }
        if (!active && jobs.isEmpty()) {
            EcosystemLogger.d(HaronConstants.TAG, "WebDownload: all done, stopping service")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    companion object {
        private const val CHANNEL_ID = "web_downloads"
        private const val NOTIFICATION_ID = 42010

        const val ACTION_DOWNLOAD = "com.vamp.haron.action.WEB_DOWNLOAD"
        const val ACTION_CANCEL = "com.vamp.haron.action.WEB_DOWNLOAD_CANCEL"
        const val ACTION_CANCEL_ALL = "com.vamp.haron.action.WEB_DOWNLOAD_CANCEL_ALL"

        const val EXTRA_URL = "url"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_IS_MAGNET = "is_magnet"
        const val EXTRA_DOWNLOAD_ID = "download_id"

        private val _downloads = MutableStateFlow<List<DownloadProgress>>(emptyList())
        val downloads: StateFlow<List<DownloadProgress>> = _downloads.asStateFlow()

        private var torrentEngine: TorrentEngine? = null

        fun startDownload(
            context: Context,
            url: String,
            fileName: String,
            source: SearchSource,
            isMagnet: Boolean = false
        ) {
            val intent = Intent(context, WebDownloadService::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_SOURCE, source.name)
                putExtra(EXTRA_IS_MAGNET, isMagnet)
            }
            context.startForegroundService(intent)
        }
    }
}
