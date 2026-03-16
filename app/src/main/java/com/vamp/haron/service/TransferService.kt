package com.vamp.haron.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.MainActivity
import com.vamp.haron.R
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.ftp.FtpServerConfig
import com.vamp.haron.data.ftp.FtpServerManager
import com.vamp.haron.domain.model.TransferProgressInfo
import com.vamp.haron.domain.model.TransferState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransferService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var transferJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var idleJob: Job? = null
    private var lastActivityTime = SystemClock.elapsedRealtime()
    private var ftpServerManager: FtpServerManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelTransfer()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                pauseTransfer()
                return START_STICKY
            }
            ACTION_RESUME -> {
                resumeTransfer()
                return START_STICKY
            }
            ACTION_START_SERVER -> {
                startHttpServer()
                return START_STICKY
            }
            ACTION_STOP_SERVER -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_RECEIVE -> {
                startReceiveMode()
                return START_STICKY
            }
            ACTION_STOP_RECEIVE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_FTP_SERVER -> {
                startFtpServer(intent)
                return START_STICKY
            }
            ACTION_STOP_FTP_SERVER -> {
                stopFtpServer()
                return START_NOT_STICKY
            }
        }

        val notification = buildNotification(
            getString(R.string.transfer_preparing), 0, 0
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireWakeLock()
        touchActivity()
        startIdleWatchdog()
        return START_STICKY
    }

    private fun startReceiveMode() {
        val notification = buildNotification(
            getString(R.string.transfer_receive_mode), 0, 0
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        acquireWakeLock()
        _state.value = TransferState.DISCOVERING
        EcosystemLogger.d(HaronConstants.TAG, "Receive mode started")
    }

    private fun startFtpServer(intent: Intent?) {
        val port = intent?.getIntExtra("ftp_port", HaronConstants.FTP_SERVER_DEFAULT_PORT)
            ?: HaronConstants.FTP_SERVER_DEFAULT_PORT
        val anonymous = intent?.getBooleanExtra("ftp_anonymous", true) ?: true
        val username = intent?.getStringExtra("ftp_username") ?: ""
        val password = intent?.getStringExtra("ftp_password") ?: ""
        val readOnly = intent?.getBooleanExtra("ftp_read_only", false) ?: false

        EcosystemLogger.d(HaronConstants.TAG, "TransferService: starting FTP server port=$port anon=$anonymous")

        val notification = buildNotification(
            "FTP Server running", 0, 0
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        acquireWakeLock()
    }

    private fun stopFtpServer() {
        EcosystemLogger.d(HaronConstants.TAG, "TransferService: stopping FTP server")
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startHttpServer() {
        val notification = buildNotification(
            getString(R.string.transfer_server_running), 0, 0
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        acquireWakeLock()
        _state.value = TransferState.TRANSFERRING
    }

    private fun cancelTransfer() {
        transferJob?.cancel()
        idleJob?.cancel()
        _state.value = TransferState.FAILED
        _progress.value = TransferProgressInfo()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun pauseTransfer() {
        _state.value = TransferState.PAUSED
        releaseWakeLock()
        EcosystemLogger.d(HaronConstants.TAG, "TransferService paused, WakeLock released")
        updateNotification(getString(R.string.transfer_paused), 0, 0)
    }

    private fun resumeTransfer() {
        _state.value = TransferState.TRANSFERRING
        acquireWakeLock()
        touchActivity()
        EcosystemLogger.d(HaronConstants.TAG, "TransferService resumed, WakeLock reacquired")
    }

    private fun touchActivity() {
        lastActivityTime = SystemClock.elapsedRealtime()
    }

    private fun startIdleWatchdog() {
        idleJob?.cancel()
        idleJob = scope.launch {
            while (isActive) {
                delay(IDLE_CHECK_INTERVAL_MS)
                val idleMs = SystemClock.elapsedRealtime() - lastActivityTime
                if (idleMs >= IDLE_TIMEOUT_MS) {
                    EcosystemLogger.d(HaronConstants.TAG, "TransferService idle timeout (${idleMs / 1000}s), cancelling")
                    withContext(Dispatchers.Main) { cancelTransfer() }
                }
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "haron:transfer"
            ).apply {
                acquire(30 * 60 * 1000L) // 30 min max
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.transfer_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.transfer_channel_desc)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        text: String,
        current: Int,
        total: Int
    ): Notification {
        val cancelIntent = Intent(this, TransferService::class.java).apply {
            action = ACTION_CANCEL
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.transfer_notification_title))
            .setContentText(text)
            .setProgress(total, current, total == 0)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.cancel),
                cancelPendingIntent
            )
            .build()
    }

    private fun updateNotification(text: String, current: Int, total: Int) {
        val notification = buildNotification(text, current, total)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // If no active transfer — stop self; otherwise keep running
        if (transferJob == null || transferJob?.isActive != true) {
            EcosystemLogger.d(HaronConstants.TAG, "TransferService: onTaskRemoved — no active transfer, stopping")
            stopSelf()
        } else {
            EcosystemLogger.d(HaronConstants.TAG, "TransferService: onTaskRemoved — transfer active, continuing")
        }
    }

    override fun onDestroy() {
        idleJob?.cancel()
        releaseWakeLock()
        scope.cancel()
        instance = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "file_transfer"
        private const val NOTIFICATION_ID = 42010
        private const val IDLE_CHECK_INTERVAL_MS = 60_000L
        private const val IDLE_TIMEOUT_MS = 15 * 60 * 1000L // 15 min

        const val ACTION_CANCEL = "com.vamp.haron.CANCEL_TRANSFER"
        const val ACTION_PAUSE = "com.vamp.haron.PAUSE_TRANSFER"
        const val ACTION_RESUME = "com.vamp.haron.RESUME_TRANSFER"
        const val ACTION_START_SERVER = "com.vamp.haron.START_SERVER"
        const val ACTION_STOP_SERVER = "com.vamp.haron.STOP_SERVER"
        const val ACTION_START_RECEIVE = "com.vamp.haron.START_RECEIVE"
        const val ACTION_STOP_RECEIVE = "com.vamp.haron.STOP_RECEIVE"
        const val ACTION_START_FTP_SERVER = "com.vamp.haron.START_FTP_SERVER"
        const val ACTION_STOP_FTP_SERVER = "com.vamp.haron.STOP_FTP_SERVER"

        @Volatile var instance: TransferService? = null
            private set

        private val _progress = MutableStateFlow(TransferProgressInfo())
        val progress: StateFlow<TransferProgressInfo> = _progress.asStateFlow()

        private val _state = MutableStateFlow(TransferState.IDLE)
        val state: StateFlow<TransferState> = _state.asStateFlow()

        fun updateProgress(info: TransferProgressInfo) {
            _progress.value = info
            instance?.touchActivity()
        }

        fun updateState(newState: TransferState) {
            _state.value = newState
        }

        fun startServer(context: Context) {
            val intent = Intent(context, TransferService::class.java).apply {
                action = ACTION_START_SERVER
            }
            context.startForegroundService(intent)
        }

        fun stopServer(context: Context) {
            val intent = Intent(context, TransferService::class.java).apply {
                action = ACTION_STOP_SERVER
            }
            context.startService(intent)
        }

        fun isPowerSaveMode(context: Context): Boolean {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isPowerSaveMode
        }
    }
}
