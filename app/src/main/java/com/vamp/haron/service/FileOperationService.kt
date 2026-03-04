package com.vamp.haron.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
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
    private var wakeLock: PowerManager.WakeLock? = null

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

        val notification = buildNotification(getString(R.string.notification_preparing), 0, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "haron:fileop")
            .apply { acquire(60 * 60 * 1000L) }

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
        val total = sourcePaths.size
        val type = if (isMove) OperationType.MOVE else OperationType.COPY
        val isDstSaf = destinationDir.startsWith("content://")

        _progress.value = OperationProgress(current = 0, total = total, currentFileName = "", type = type)

        var completed = 0
        for ((index, srcPath) in sourcePaths.withIndex()) {
            val isSrcSaf = srcPath.startsWith("content://")
            val fileName = if (isSrcSaf) {
                Uri.parse(srcPath).lastPathSegment?.substringAfterLast('/') ?: "file"
            } else {
                File(srcPath).name
            }

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
                when {
                    !isSrcSaf && !isDstSaf -> {
                        // File → File (original)
                        val src = File(srcPath)
                        if (!src.exists()) continue
                        val destDir = File(destinationDir)
                        val destFile = File(destDir, fileName)
                        val dest = resolveFileConflict(destFile, conflictResolution, destDir, fileName) ?: continue
                        // Guard: cannot copy/move folder into itself
                        if (src.isDirectory && dest.absolutePath.startsWith(src.absolutePath + File.separator)) {
                            EcosystemLogger.e(HaronConstants.TAG, "Cannot copy folder into itself: ${src.path} → ${dest.path}")
                            continue
                        }
                        if (isMove) {
                            val moved = src.renameTo(dest)
                            if (!moved) {
                                if (src.isDirectory) safeCopyDirectory(src, dest)
                                else src.copyTo(dest, overwrite = false)
                                src.deleteRecursively()
                            }
                        } else {
                            if (src.isDirectory) safeCopyDirectory(src, dest)
                            else src.copyTo(dest, overwrite = false)
                        }
                        completed++
                    }
                    isSrcSaf && !isDstSaf -> {
                        // SAF → File (stream copy)
                        val destDir = File(destinationDir)
                        val destFile = File(destDir, fileName)
                        val dest = resolveFileConflict(destFile, conflictResolution, destDir, fileName) ?: continue
                        contentResolver.openInputStream(Uri.parse(srcPath))?.use { input ->
                            dest.outputStream().use { output -> input.copyTo(output) }
                        }
                        if (isMove) {
                            try {
                                android.provider.DocumentsContract.deleteDocument(contentResolver, Uri.parse(srcPath))
                            } catch (_: Exception) { }
                        }
                        completed++
                    }
                    !isSrcSaf && isDstSaf -> {
                        // File → SAF (stream copy)
                        val src = File(srcPath)
                        if (!src.exists()) continue
                        val destUri = Uri.parse(destinationDir)
                        val parent = DocumentFile.fromTreeUri(this@FileOperationService, destUri)
                        val existing = parent?.findFile(src.name)
                        val targetName = resolveSafConflict(existing, conflictResolution, destUri, src.name) ?: continue
                        val mimeType = android.webkit.MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(src.extension) ?: "application/octet-stream"
                        val destDoc = parent?.createFile(mimeType, targetName) ?: continue
                        contentResolver.openOutputStream(destDoc.uri)?.use { output ->
                            src.inputStream().use { input -> input.copyTo(output) }
                        }
                        if (isMove) src.deleteRecursively()
                        completed++
                    }
                    else -> {
                        // SAF → SAF (stream copy)
                        val srcUri = Uri.parse(srcPath)
                        val destUri = Uri.parse(destinationDir)
                        val parent = DocumentFile.fromTreeUri(this@FileOperationService, destUri)
                        val existing = parent?.findFile(fileName)
                        val targetName = resolveSafConflict(existing, conflictResolution, destUri, fileName) ?: continue
                        val mimeType = contentResolver.getType(srcUri) ?: "application/octet-stream"
                        val destDoc = parent?.createFile(mimeType, targetName) ?: continue
                        contentResolver.openInputStream(srcUri)?.use { input ->
                            contentResolver.openOutputStream(destDoc.uri)?.use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (isMove) {
                            try {
                                android.provider.DocumentsContract.deleteDocument(contentResolver, srcUri)
                            } catch (_: Exception) { }
                        }
                        completed++
                    }
                }
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

    private fun resolveFileConflict(
        destFile: File,
        resolution: ConflictResolution,
        destDir: File,
        fileName: String
    ): File? {
        return when {
            !destFile.exists() -> destFile
            resolution == ConflictResolution.REPLACE -> {
                destFile.deleteRecursively(); destFile
            }
            resolution == ConflictResolution.RENAME -> resolveConflict(destDir, fileName)
            resolution == ConflictResolution.SKIP -> null
            else -> null
        }
    }

    private fun resolveSafConflict(
        existing: DocumentFile?,
        resolution: ConflictResolution,
        parentUri: Uri,
        fileName: String
    ): String? {
        if (existing == null) return fileName
        return when (resolution) {
            ConflictResolution.REPLACE -> {
                existing.delete(); fileName
            }
            ConflictResolution.RENAME -> generateSafRename(parentUri, fileName)
            ConflictResolution.SKIP -> null
        }
    }

    private fun generateSafRename(parentUri: Uri, name: String): String {
        val parent = DocumentFile.fromTreeUri(this, parentUri) ?: return name
        val baseName = name.substringBeforeLast('.', name)
        val ext = if ('.' in name) ".${name.substringAfterLast('.')}" else ""
        var counter = 1
        var candidate = "${baseName}($counter)$ext"
        while (parent.findFile(candidate) != null) {
            counter++
            candidate = "${baseName}($counter)$ext"
        }
        return candidate
    }

    private fun cancelOperation() {
        operationJob?.cancel()
        _progress.value = _progress.value?.copy(
            isComplete = true,
            error = getString(R.string.operation_cancelled)
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
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
            .setContentTitle(getString(currentOperationType.labelRes))
            .setContentText("$current/$total — $fileName")
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

    private fun updateNotification(fileName: String, current: Int, total: Int) {
        val notification = buildNotification(fileName, current, total)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompleteNotification(completed: Int, total: Int, isMove: Boolean) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        val action = if (isMove) getString(R.string.operation_moved) else getString(R.string.operation_copied)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$action: $completed/$total")
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID + 1, notification)
    }

    /** Snapshot-based directory copy — avoids infinite recursion */
    private fun safeCopyDirectory(src: File, dest: File) {
        val snapshot = src.walkTopDown().toList()
        for (file in snapshot) {
            val relPath = file.toRelativeString(src)
            val dstFile = File(dest, relPath)
            if (file.isDirectory) {
                dstFile.mkdirs()
            } else {
                dstFile.parentFile?.mkdirs()
                file.copyTo(dstFile, overwrite = false)
            }
        }
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
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
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
