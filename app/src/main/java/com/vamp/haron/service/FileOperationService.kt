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
import androidx.core.app.NotificationCompat
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.MainActivity
import com.vamp.haron.R
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.ConflictResolution
import com.vamp.haron.domain.model.OperationProgress
import com.vamp.haron.domain.model.OperationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FileOperationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var operationJob: Job? = null
    private var currentOperationType: OperationType = OperationType.COPY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelOperation()
                return START_NOT_STICKY
            }
        }

        val sourcePaths = intent?.getStringArrayListExtra(EXTRA_SOURCE_PATHS) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val destinationDir = intent.getStringExtra(EXTRA_DESTINATION_DIR) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val isMove = intent.getBooleanExtra(EXTRA_IS_MOVE, false)
        val resolutionName = intent.getStringExtra(EXTRA_CONFLICT_RESOLUTION)
        val conflictResolution = resolutionName?.let {
            try { ConflictResolution.valueOf(it) } catch (_: Exception) { ConflictResolution.RENAME }
        } ?: ConflictResolution.RENAME
        currentOperationType = if (isMove) OperationType.MOVE else OperationType.COPY

        val notification = buildNotification("Подготовка...", 0, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        operationJob = scope.launch {
            executeOperation(sourcePaths, destinationDir, isMove, conflictResolution)
        }

        return START_STICKY
    }

    private suspend fun executeOperation(
        sourcePaths: List<String>,
        destinationDir: String,
        isMove: Boolean,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME
    ) {
        val destDir = File(destinationDir)
        val total = sourcePaths.size
        val type = if (isMove) OperationType.MOVE else OperationType.COPY

        _progress.value = OperationProgress(current = 0, total = total, currentFileName = "", type = type)

        var completed = 0
        for ((index, srcPath) in sourcePaths.withIndex()) {
            val src = File(srcPath)
            if (!src.exists()) continue

            val fileName = src.name
            _progress.value = OperationProgress(
                current = index,
                total = total,
                currentFileName = fileName,
                type = type
            )

            withContext(Dispatchers.Main) {
                updateNotification(fileName, index, total)
            }

            try {
                val destFile = File(destDir, fileName)
                val dest = when {
                    !destFile.exists() -> destFile
                    conflictResolution == ConflictResolution.REPLACE -> {
                        destFile.deleteRecursively(); destFile
                    }
                    conflictResolution == ConflictResolution.RENAME -> resolveConflict(destDir, fileName)
                    conflictResolution == ConflictResolution.SKIP -> continue
                    else -> continue
                }
                if (isMove) {
                    val moved = src.renameTo(dest)
                    if (!moved) {
                        if (src.isDirectory) src.copyRecursively(dest, overwrite = false)
                        else src.copyTo(dest, overwrite = false)
                        src.deleteRecursively()
                    }
                } else {
                    if (src.isDirectory) src.copyRecursively(dest, overwrite = false)
                    else src.copyTo(dest, overwrite = false)
                }
                completed++
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "Ошибка операции с $fileName: ${e.message}")
                _progress.value = OperationProgress(
                    current = index,
                    total = total,
                    currentFileName = fileName,
                    type = type,
                    error = e.message
                )
            }
        }

        _progress.value = OperationProgress(
            current = completed,
            total = total,
            currentFileName = "",
            type = type,
            isComplete = true
        )

        withContext(Dispatchers.Main) {
            showCompleteNotification(completed, total, isMove)
            stopSelf()
        }
    }

    private fun cancelOperation() {
        operationJob?.cancel()
        _progress.value = _progress.value?.copy(
            isComplete = true,
            error = "Отменено"
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Файловые операции",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Прогресс копирования и перемещения файлов"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        fileName: String,
        current: Int,
        total: Int
    ): Notification {
        val cancelIntent = Intent(this, FileOperationService::class.java).apply {
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
            .setContentTitle(currentOperationType.label)
            .setContentText("$current/$total — $fileName")
            .setProgress(total, current, total == 0)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Отмена",
                cancelPendingIntent
            )
            .build()
    }

    private fun updateNotification(fileName: String, current: Int, total: Int) {
        val notification = buildNotification(fileName, current, total)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompleteNotification(completed: Int, total: Int, isMove: Boolean) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        val action = if (isMove) "Перемещено" else "Скопировано"
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$action: $completed/$total")
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun resolveConflict(destDir: File, name: String): File {
        var target = File(destDir, name)
        if (!target.exists()) return target
        val baseName = name.substringBeforeLast('.', name)
        val ext = if ('.' in name) ".${name.substringAfterLast('.')}" else ""
        var counter = 1
        while (target.exists()) {
            target = File(destDir, "${baseName}($counter)$ext")
            counter++
        }
        return target
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "file_operations"
        private const val NOTIFICATION_ID = 42001

        const val ACTION_CANCEL = "com.vamp.haron.CANCEL_OPERATION"
        const val EXTRA_SOURCE_PATHS = "source_paths"
        const val EXTRA_DESTINATION_DIR = "destination_dir"
        const val EXTRA_IS_MOVE = "is_move"
        const val EXTRA_CONFLICT_RESOLUTION = "conflict_resolution"

        private val _progress = MutableStateFlow<OperationProgress?>(null)
        val progress: StateFlow<OperationProgress?> = _progress.asStateFlow()

        fun start(
            context: Context,
            sourcePaths: List<String>,
            destinationDir: String,
            isMove: Boolean = false,
            conflictResolution: ConflictResolution = ConflictResolution.RENAME
        ) {
            val type = if (isMove) OperationType.MOVE else OperationType.COPY
            _progress.value = OperationProgress(
                current = 0,
                total = sourcePaths.size,
                currentFileName = "",
                type = type
            )
            val intent = Intent(context, FileOperationService::class.java).apply {
                putStringArrayListExtra(EXTRA_SOURCE_PATHS, ArrayList(sourcePaths))
                putExtra(EXTRA_DESTINATION_DIR, destinationDir)
                putExtra(EXTRA_IS_MOVE, isMove)
                putExtra(EXTRA_CONFLICT_RESOLUTION, conflictResolution.name)
            }
            context.startForegroundService(intent)
        }
    }
}
