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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.MainActivity
import com.vamp.haron.R
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.domain.model.ConflictResolution
import com.vamp.haron.domain.model.OperationHolder
import com.vamp.haron.domain.model.OperationProgress
import com.vamp.haron.domain.model.OperationType
import com.vamp.haron.domain.repository.FileRepository
import com.vamp.haron.domain.repository.TrashRepository
import com.vamp.haron.domain.usecase.CreateZipUseCase
import com.vamp.haron.domain.usecase.ExtractArchiveUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
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
import java.io.File

class FileOperationService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ServiceEntryPoint {
        fun trashRepository(): TrashRepository
        fun fileRepository(): FileRepository
        fun preferences(): HaronPreferences
        fun createZipUseCase(): CreateZipUseCase
        fun extractArchiveUseCase(): ExtractArchiveUseCase
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var operationJob: Job? = null
    private var idleJob: Job? = null
    private var currentOperationType: OperationType = OperationType.COPY
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastProgressTime = SystemClock.elapsedRealtime()

    private lateinit var trashRepository: TrashRepository
    private lateinit var fileRepository: FileRepository
    private lateinit var preferences: HaronPreferences
    private lateinit var createZipUseCase: CreateZipUseCase
    private lateinit var extractArchiveUseCase: ExtractArchiveUseCase

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val ep = EntryPointAccessors.fromApplication(applicationContext, ServiceEntryPoint::class.java)
        trashRepository = ep.trashRepository()
        fileRepository = ep.fileRepository()
        preferences = ep.preferences()
        createZipUseCase = ep.createZipUseCase()
        extractArchiveUseCase = ep.extractArchiveUseCase()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelOperation()
                return START_NOT_STICKY
            }
            ACTION_DELETE -> {
                val paths = OperationHolder.deletePaths.toList()
                OperationHolder.clearDelete()
                if (paths.isEmpty()) { stopSelf(); return START_NOT_STICKY }
                val useTrash = intent.getBooleanExtra(EXTRA_USE_TRASH, true)
                startForegroundOp(OperationType.DELETE, paths.size)
                operationJob = scope.launch { executeDelete(paths, useTrash) }
                return START_STICKY
            }
            ACTION_ARCHIVE -> {
                val paths = OperationHolder.archivePaths.toList()
                val outputPath = OperationHolder.archiveOutputPath
                val password = OperationHolder.archivePassword
                val splitSizeMb = OperationHolder.archiveSplitSizeMb
                OperationHolder.clearArchive()
                if (paths.isEmpty() || outputPath.isEmpty()) { stopSelf(); return START_NOT_STICKY }
                startForegroundOp(OperationType.ARCHIVE, paths.size)
                operationJob = scope.launch { executeArchive(paths, outputPath, password, splitSizeMb) }
                return START_STICKY
            }
            ACTION_ARCHIVE_ONE_TO_ONE -> {
                val paths = OperationHolder.archivePaths.toList()
                OperationHolder.clearArchive()
                if (paths.isEmpty()) { stopSelf(); return START_NOT_STICKY }
                startForegroundOp(OperationType.ARCHIVE, paths.size)
                operationJob = scope.launch { executeArchiveOneToOne(paths) }
                return START_STICKY
            }
            ACTION_EXTRACT -> {
                val archivePath = OperationHolder.extractArchivePath
                val destDir = OperationHolder.extractDestDir
                val selectedEntries = OperationHolder.extractSelectedEntries
                val password = OperationHolder.extractPassword
                val basePrefix = OperationHolder.extractBasePrefix
                OperationHolder.clearExtract()
                if (archivePath.isEmpty() || destDir.isEmpty()) { stopSelf(); return START_NOT_STICKY }
                startForegroundOp(OperationType.EXTRACT, 0)
                operationJob = scope.launch { executeExtract(archivePath, destDir, selectedEntries, password, basePrefix) }
                return START_STICKY
            }
        }

        // Default: copy/move (original flow)
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

        startForegroundOp(currentOperationType, sourcePaths.size)

        operationJob = scope.launch {
            executeOperation(sourcePaths, destinationDir, isMove, conflictResolution)
        }

        return START_STICKY
    }

    private fun startForegroundOp(type: OperationType, total: Int) {
        currentOperationType = type
        val notification = buildNotification(getString(R.string.notification_preparing), 0, total)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "haron:fileop")
            .apply { acquire(30 * 60 * 1000L) }
        lastProgressTime = SystemClock.elapsedRealtime()
        startProgressIdleWatchdog()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (operationJob == null || operationJob?.isActive != true) {
            EcosystemLogger.d(HaronConstants.TAG, "FileOperationService: onTaskRemoved — no active operation, stopping")
            stopSelf()
        } else {
            EcosystemLogger.d(HaronConstants.TAG, "FileOperationService: onTaskRemoved — operation active, continuing")
        }
    }

    // ==================== DELETE ====================

    private suspend fun executeDelete(paths: List<String>, useTrash: Boolean) {
        val total = paths.size
        _progress.value = OperationProgress(0, total, "", OperationType.DELETE)

        // Pre-calculate trash eviction
        if (useTrash) {
            val nonSafPaths = paths.filter { !it.startsWith("content://") }
            if (nonSafPaths.isNotEmpty()) {
                val maxMb = preferences.trashMaxSizeMb
                if (maxMb > 0) {
                    val incomingSize = nonSafPaths.sumOf { p ->
                        val f = File(p)
                        if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                        else f.length()
                    }
                    val maxBytes = maxMb.toLong() * 1024 * 1024
                    val currentTrashSize = trashRepository.getTrashSize()
                    val needed = (currentTrashSize + incomingSize) - maxBytes
                    if (needed > 0) {
                        trashRepository.evictToFitSize(maxBytes - incomingSize)
                    }
                }
            }
        }

        var completed = 0
        var lastError: String? = null
        for ((index, path) in paths.withIndex()) {
            val isSaf = path.startsWith("content://")
            val fileName = if (isSaf) {
                Uri.parse(path).lastPathSegment?.substringAfterLast('/') ?: "file"
            } else {
                File(path).name
            }
            _progress.value = OperationProgress(index, total, fileName, OperationType.DELETE)
            lastProgressTime = SystemClock.elapsedRealtime()
            withContext(Dispatchers.Main) { updateNotification(fileName, index, total) }

            try {
                if (isSaf) {
                    fileRepository.deleteFiles(listOf(path)).onSuccess { completed++ }
                } else if (useTrash) {
                    trashRepository.moveToTrash(listOf(path))
                        .onSuccess { count -> completed += count }
                        .onFailure { e -> lastError = e.message }
                } else {
                    val f = File(path)
                    if (f.deleteRecursively()) completed++ else lastError = "Failed to delete $fileName"
                }
            } catch (e: Exception) {
                lastError = e.message
                EcosystemLogger.e(HaronConstants.TAG, "FileOperationService delete error: $fileName: ${e.message}")
            }
        }

        finishOperation(completed, total, OperationType.DELETE, lastError)
    }

    // ==================== ARCHIVE ====================

    private suspend fun executeArchive(
        sourcePaths: List<String>,
        outputPath: String,
        password: String?,
        splitSizeMb: Int
    ) {
        try {
            createZipUseCase(sourcePaths, outputPath, password, splitSizeMb)
                .collect { progress ->
                    _progress.value = OperationProgress(
                        current = progress.current,
                        total = progress.total,
                        currentFileName = progress.fileName,
                        type = OperationType.ARCHIVE
                    )
                    lastProgressTime = SystemClock.elapsedRealtime()
                    withContext(Dispatchers.Main) {
                        updateNotification(progress.fileName, progress.current, progress.total)
                    }
                }
            finishOperation(1, 1, OperationType.ARCHIVE, null)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "FileOperationService archive error: ${e.message}")
            finishOperation(0, 1, OperationType.ARCHIVE, e.message)
        }
    }

    private suspend fun executeArchiveOneToOne(paths: List<String>) {
        val total = paths.size
        var created = 0
        try {
            for ((index, srcPath) in paths.withIndex()) {
                val srcFile = File(srcPath)
                if (!srcFile.exists()) continue
                val parentDir = srcFile.parent ?: continue
                var outputPath = "$parentDir/${srcFile.nameWithoutExtension}.zip"
                if (File(outputPath).exists()) {
                    outputPath = CreateZipUseCase.findUniqueZipPath(outputPath)
                }

                _progress.value = OperationProgress(index + 1, total, srcFile.name, OperationType.ARCHIVE)
                lastProgressTime = SystemClock.elapsedRealtime()
                withContext(Dispatchers.Main) { updateNotification(srcFile.name, index + 1, total) }

                createZipUseCase(listOf(srcPath), outputPath).collect { /* consume flow */ }
                created++
            }
            finishOperation(created, total, OperationType.ARCHIVE, null)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "FileOperationService archive 1:1 error: ${e.message}")
            finishOperation(created, total, OperationType.ARCHIVE, e.message)
        }
    }

    // ==================== EXTRACT ====================

    private suspend fun executeExtract(
        archivePath: String,
        destDir: String,
        selectedEntries: Set<String>?,
        password: String?,
        basePrefix: String
    ) {
        try {
            extractArchiveUseCase(archivePath, destDir, selectedEntries, password, basePrefix)
                .collect { progress ->
                    _progress.value = OperationProgress(
                        current = progress.current,
                        total = progress.total,
                        currentFileName = progress.fileName,
                        type = OperationType.EXTRACT,
                        isComplete = progress.isComplete,
                        error = progress.error
                    )
                    lastProgressTime = SystemClock.elapsedRealtime()
                    withContext(Dispatchers.Main) {
                        updateNotification(progress.fileName, progress.current, progress.total)
                    }
                    if (progress.isComplete) {
                        finishOperation(
                            progress.current, progress.total, OperationType.EXTRACT, progress.error
                        )
                    }
                }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "FileOperationService extract error: ${e.message}")
            finishOperation(0, 0, OperationType.EXTRACT, e.message)
        }
    }

    // ==================== COPY / MOVE ====================

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
            lastProgressTime = SystemClock.elapsedRealtime()

            withContext(Dispatchers.Main) {
                updateNotification(fileName, index, total)
            }

            try {
                when {
                    !isSrcSaf && !isDstSaf -> {
                        val src = File(srcPath)
                        if (!src.exists()) continue
                        val destDir = File(destinationDir)
                        val destFile = File(destDir, fileName)
                        val dest = resolveFileConflict(destFile, conflictResolution, destDir, fileName) ?: continue
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

        finishOperation(completed, total, type, null)
    }

    // ==================== Common helpers ====================

    private suspend fun finishOperation(completed: Int, total: Int, type: OperationType, error: String?) {
        idleJob?.cancel()
        _progress.value = OperationProgress(
            current = completed,
            total = total,
            currentFileName = "",
            type = type,
            isComplete = true,
            error = error
        )
        withContext(Dispatchers.Main) {
            showCompleteNotification(completed, total, type)
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

    private fun startProgressIdleWatchdog() {
        idleJob?.cancel()
        idleJob = scope.launch {
            while (isActive) {
                delay(IDLE_CHECK_INTERVAL_MS)
                val idleMs = SystemClock.elapsedRealtime() - lastProgressTime
                if (idleMs >= PROGRESS_WARNING_MS && idleMs < PROGRESS_TIMEOUT_MS) {
                    EcosystemLogger.d(HaronConstants.TAG, "FileOperationService: no progress for ${idleMs / 1000}s")
                } else if (idleMs >= PROGRESS_TIMEOUT_MS) {
                    EcosystemLogger.e(HaronConstants.TAG, "FileOperationService: no progress for ${idleMs / 1000}s, cancelling")
                    withContext(Dispatchers.Main) { cancelOperation() }
                }
            }
        }
    }

    private fun cancelOperation() {
        operationJob?.cancel()
        idleJob?.cancel()
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

    private fun showCompleteNotification(completed: Int, total: Int, type: OperationType) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        val action = getString(type.labelRes)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$action: $completed/$total")
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID + 1, notification)
    }

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
        idleJob?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "file_operations"
        private const val NOTIFICATION_ID = 42001
        private const val IDLE_CHECK_INTERVAL_MS = 60_000L
        private const val PROGRESS_WARNING_MS = 5 * 60 * 1000L
        private const val PROGRESS_TIMEOUT_MS = 15 * 60 * 1000L
        const val ACTION_CANCEL = "com.vamp.haron.CANCEL_OPERATION"
        const val ACTION_DELETE = "com.vamp.haron.ACTION_DELETE"
        const val ACTION_ARCHIVE = "com.vamp.haron.ACTION_ARCHIVE"
        const val ACTION_ARCHIVE_ONE_TO_ONE = "com.vamp.haron.ACTION_ARCHIVE_ONE_TO_ONE"
        const val ACTION_EXTRACT = "com.vamp.haron.ACTION_EXTRACT"
        const val EXTRA_SOURCE_PATHS = "source_paths"
        const val EXTRA_DESTINATION_DIR = "destination_dir"
        const val EXTRA_IS_MOVE = "is_move"
        const val EXTRA_CONFLICT_RESOLUTION = "conflict_resolution"
        const val EXTRA_USE_TRASH = "use_trash"

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

        fun startDelete(context: Context, paths: List<String>, useTrash: Boolean = true) {
            OperationHolder.deletePaths = paths
            _progress.value = OperationProgress(0, paths.size, "", OperationType.DELETE)
            val intent = Intent(context, FileOperationService::class.java).apply {
                action = ACTION_DELETE
                putExtra(EXTRA_USE_TRASH, useTrash)
            }
            context.startForegroundService(intent)
        }

        fun startArchive(
            context: Context,
            paths: List<String>,
            outputPath: String,
            password: String? = null,
            splitSizeMb: Int = 0
        ) {
            OperationHolder.archivePaths = paths
            OperationHolder.archiveOutputPath = outputPath
            OperationHolder.archivePassword = password
            OperationHolder.archiveSplitSizeMb = splitSizeMb
            _progress.value = OperationProgress(0, paths.size, "", OperationType.ARCHIVE)
            val intent = Intent(context, FileOperationService::class.java).apply {
                action = ACTION_ARCHIVE
            }
            context.startForegroundService(intent)
        }

        fun startArchiveOneToOne(context: Context, paths: List<String>) {
            OperationHolder.archivePaths = paths
            _progress.value = OperationProgress(0, paths.size, "", OperationType.ARCHIVE)
            val intent = Intent(context, FileOperationService::class.java).apply {
                action = ACTION_ARCHIVE_ONE_TO_ONE
            }
            context.startForegroundService(intent)
        }

        fun startExtract(
            context: Context,
            archivePath: String,
            destDir: String,
            selectedEntries: Set<String>? = null,
            password: String? = null,
            basePrefix: String = ""
        ) {
            OperationHolder.extractArchivePath = archivePath
            OperationHolder.extractDestDir = destDir
            OperationHolder.extractSelectedEntries = selectedEntries
            OperationHolder.extractPassword = password
            OperationHolder.extractBasePrefix = basePrefix
            _progress.value = OperationProgress(0, 0, "", OperationType.EXTRACT)
            val intent = Intent(context, FileOperationService::class.java).apply {
                action = ACTION_EXTRACT
            }
            context.startForegroundService(intent)
        }
    }
}
