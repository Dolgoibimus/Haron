package com.vamp.haron.data.repository

import android.content.Context
import android.net.Uri
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.common.util.toFileEntry
import com.vamp.haron.data.saf.SafFileOperations
import com.vamp.haron.data.saf.SafUriManager
import com.vamp.haron.data.saf.StorageVolumeHelper
import com.vamp.haron.data.shizuku.ShizukuManager
import com.vamp.haron.domain.model.ConflictResolution
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.R
import com.vamp.haron.domain.repository.FileRepository
import com.vamp.core.logger.EcosystemLogger
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core file operations: list, copy, move, delete, rename, create.
 * Handles normal paths, SAF (content://), and Shizuku (restricted Android/data).
 * Delegates to [ShizukuFileService] when src/dst is in restricted path.
 * Copy/move with conflict resolution: RENAME, REPLACE, SKIP.
 */
@Singleton
class FileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val safFileOperations: SafFileOperations,
    private val safUriManager: SafUriManager,
    private val storageVolumeHelper: StorageVolumeHelper,
    private val shizukuManager: ShizukuManager
) : FileRepository {

    private fun isContentUri(path: String): Boolean = path.startsWith("content://")

    override suspend fun getFiles(path: String): Result<List<FileEntry>> =
        withContext(Dispatchers.IO) {
            if (isContentUri(path)) {
                return@withContext getSafFiles(path)
            }
            try {
                val dir = File(path)
                val restricted = isRestrictedAndroidPath(path)
                // On Android 11+, File.exists() returns false for subfolders of Android/data and Android/obb
                // due to FUSE restrictions — skip existence check for restricted paths, let Shizuku handle it
                if (!dir.exists() && !restricted) {
                    return@withContext Result.failure(
                        IllegalArgumentException(context.getString(R.string.error_path_not_exists, path))
                    )
                }
                if (dir.exists() && !dir.isDirectory) {
                    return@withContext Result.failure(
                        IllegalArgumentException(context.getString(R.string.error_not_a_folder, path))
                    )
                }
                val rawFiles = dir.listFiles()
                if (rawFiles != null) {
                    val entries = rawFiles.map { it.toFileEntry() }
                    // Enrich childCount for restricted subdirectories via Shizuku
                    // (e.g., listing /Android/ — data and obb dirs have childCount=0 from File API)
                    val hasRestrictedChildren = entries.any { it.isDirectory && isRestrictedAndroidPath(it.path) && it.childCount == 0 }
                    if (hasRestrictedChildren && shizukuManager.ensureServiceBound()) {
                        val enriched = entries.map { entry ->
                            if (entry.isDirectory && isRestrictedAndroidPath(entry.path) && entry.childCount == 0) {
                                val count = shizukuManager.listFiles(entry.path)
                                    ?.count { !it.isHidden } ?: 0
                                entry.copy(childCount = count)
                            } else entry
                        }
                        return@withContext Result.success(enriched)
                    }
                    return@withContext Result.success(entries)
                }
                // listFiles() null or dir not visible — try SAF/Shizuku fallback for restricted paths
                if (restricted) {
                    val safUri = findSafUriForPath(path)
                    if (safUri != null) {
                        EcosystemLogger.d(HaronConstants.TAG, "getFiles: SAF fallback for $path")
                        return@withContext getSafFiles(safUri.toString())
                    }
                    // 2nd fallback: Shizuku
                    if (shizukuManager.ensureServiceBound()) {
                        val shizukuFiles = shizukuManager.listFiles(path)
                        if (shizukuFiles != null) {
                            EcosystemLogger.d(HaronConstants.TAG, "getFiles: Shizuku fallback for $path (${shizukuFiles.size} files)")
                            return@withContext Result.success(shizukuFiles)
                        }
                    }
                }
                Result.success(emptyList())
            } catch (e: SecurityException) {
                EcosystemLogger.e(HaronConstants.TAG, "Нет доступа к $path: ${e.message}")
                Result.failure(e)
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "Ошибка чтения $path: ${e.message}")
                Result.failure(e)
            }
        }

    fun isRestrictedAndroidPath(path: String): Boolean =
        path.contains("/Android/data") || path.contains("/Android/obb") || path.contains("/Android/media")

    fun filePathToDocId(filePath: String): String? {
        val internalPrefix = "/storage/emulated/0/"
        if (filePath.startsWith(internalPrefix)) {
            return "primary:" + filePath.removePrefix(internalPrefix)
        }
        val match = Regex("^/storage/([^/]+)/(.+)$").matchEntire(filePath)
        if (match != null) return "${match.groupValues[1]}:${match.groupValues[2]}"
        return null
    }

    fun hasSafAccessForPath(filePath: String): Boolean = findSafUriForPath(filePath) != null

    private fun findSafUriForPath(filePath: String): Uri? {
        val targetDocId = filePathToDocId(filePath) ?: return null
        for (uri in safUriManager.getPersistedUris()) {
            try {
                val treeDocId = DocumentsContract.getTreeDocumentId(uri)
                if (targetDocId == treeDocId || targetDocId.startsWith("$treeDocId/")) {
                    return DocumentsContract.buildDocumentUriUsingTree(uri, targetDocId)
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun getSafFiles(path: String): Result<List<FileEntry>> {
        val uri = Uri.parse(path)
        val treeUri = safFileOperations.getTreeUriForDocument(uri)
        return if (treeUri != null && safFileOperations.isTreeRoot(uri)) {
            safFileOperations.listFiles(uri)
        } else if (treeUri != null) {
            safFileOperations.listFilesForDocument(treeUri, uri)
        } else {
            // Try as tree URI directly
            safFileOperations.listFiles(uri)
        }
    }

    override fun getStorageRoots(): List<FileEntry> {
        val roots = mutableListOf<FileEntry>()
        val internal = File(HaronConstants.ROOT_PATH)
        if (internal.exists()) {
            roots.add(
                FileEntry(
                    name = context.getString(R.string.internal_storage),
                    path = internal.absolutePath,
                    isDirectory = true,
                    size = 0L,
                    lastModified = internal.lastModified(),
                    extension = "",
                    isHidden = false,
                    childCount = internal.listFiles()?.size ?: 0
                )
            )
        }
        // Add SAF-persisted storage roots (SD cards etc.)
        for (uri in safUriManager.getPersistedUris()) {
            roots.add(
                FileEntry(
                    name = getSafVolumeLabel(uri),
                    path = uri.toString(),
                    isDirectory = true,
                    size = 0L,
                    lastModified = 0L,
                    extension = "",
                    isHidden = false,
                    childCount = 0,
                    isContentUri = true
                )
            )
        }
        return roots
    }

    private fun getSafVolumeLabel(uri: Uri): String {
        // Try to extract a human-readable label from the URI
        val treeDocId = try {
            android.provider.DocumentsContract.getTreeDocumentId(uri)
        } catch (_: Exception) {
            return context.getString(R.string.sd_card)
        }
        // Format: "XXXX-XXXX:" for SD card root
        val volumeId = treeDocId.substringBefore(":")
        // Try matching with storage volumes
        val volumes = storageVolumeHelper.getRemovableVolumes()
        return volumes.firstOrNull()?.label ?: context.getString(R.string.sd_card)
    }

    override fun getParentPath(path: String): String? {
        if (isContentUri(path)) {
            return getSafParentPath(path)
        }
        val parent = File(path).parentFile ?: return null
        if (parent.absolutePath.length < HaronConstants.ROOT_PATH.length) return null
        return parent.absolutePath
    }

    private fun getSafParentPath(path: String): String? {
        val uri = Uri.parse(path)
        // Check if we're at the tree root
        if (safFileOperations.isTreeRoot(uri)) return null
        val parentUri = safFileOperations.getParentUri(uri) ?: return null
        return parentUri.toString()
    }

    override suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationDir: String,
        conflictResolution: ConflictResolution
    ): Result<Int> = withContext(Dispatchers.IO) {
        val resolutions = sourcePaths.associateWith { conflictResolution }
        copyFilesWithResolutions(sourcePaths, destinationDir, resolutions)
    }

    override suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationDir: String,
        conflictResolution: ConflictResolution
    ): Result<Int> = withContext(Dispatchers.IO) {
        val resolutions = sourcePaths.associateWith { conflictResolution }
        moveFilesWithResolutions(sourcePaths, destinationDir, resolutions)
    }

    override suspend fun copyFilesWithResolutions(
        sourcePaths: List<String>,
        destinationDir: String,
        resolutions: Map<String, ConflictResolution>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val isDstSaf = isContentUri(destinationDir)
            if (!isDstSaf) {
                val dstRestricted = isRestrictedAndroidPath(destinationDir)
                if (!dstRestricted) {
                    val destDir = File(destinationDir)
                    if (!destDir.isDirectory) {
                        return@withContext Result.failure(
                            IllegalArgumentException(context.getString(R.string.error_dest_not_folder, destinationDir))
                        )
                    }
                }
                // For restricted dest paths, skip File.isDirectory check (FUSE blocks it)
            }
            var count = 0
            for (srcPath in sourcePaths) {
                val resolution = resolutions[srcPath] ?: ConflictResolution.RENAME
                val isSrcSaf = isContentUri(srcPath)
                val name = if (isSrcSaf) {
                    Uri.parse(srcPath).lastPathSegment?.substringAfterLast('/') ?: continue
                } else {
                    File(srcPath).name
                }

                when {
                    !isSrcSaf && !isDstSaf -> {
                        val srcRestricted = isRestrictedAndroidPath(srcPath)
                        val dstRestricted = isRestrictedAndroidPath(destinationDir)

                        if ((srcRestricted || dstRestricted) && shizukuManager.ensureServiceBound()) {
                            // Shizuku path — use IPC for restricted file operations
                            val srcName = srcPath.substringAfterLast('/')
                            val destPath = "$destinationDir/$srcName"
                            val destExists = shizukuManager.exists(destPath) == true
                            val resolvedDest = when {
                                !destExists -> destPath
                                resolution == ConflictResolution.REPLACE -> {
                                    shizukuManager.deleteRecursively(destPath); destPath
                                }
                                resolution == ConflictResolution.RENAME -> resolveConflictViaShizuku(destinationDir, srcName)
                                resolution == ConflictResolution.SKIP -> continue
                                else -> continue
                            }
                            val isDir = shizukuManager.isDirectory(srcPath) == true
                            val isReplace = resolution == ConflictResolution.REPLACE
                            val ok = if (isDir) {
                                shizukuManager.copyDirectoryRecursively(srcPath, resolvedDest, isReplace)
                            } else {
                                shizukuManager.copyFile(srcPath, resolvedDest, isReplace)
                            }
                            if (ok) count++
                            else EcosystemLogger.e(HaronConstants.TAG, "Shizuku copy failed: $srcPath → $resolvedDest")
                        } else {
                            // File → File (normal paths)
                            val src = File(srcPath)
                            if (!src.exists()) continue
                            val destDir = File(destinationDir)
                            val destFile = File(destDir, src.name)
                            val dest = when {
                                !destFile.exists() -> destFile
                                resolution == ConflictResolution.REPLACE -> {
                                    destFile.deleteRecursively(); destFile
                                }
                                resolution == ConflictResolution.RENAME -> resolveConflict(destDir, src.name)
                                resolution == ConflictResolution.SKIP -> continue
                                else -> continue
                            }
                            val isReplace = resolution == ConflictResolution.REPLACE
                            if (src.isDirectory) safeCopyDirectory(src, dest, overwrite = isReplace)
                            else src.copyTo(dest, overwrite = isReplace)
                            count++
                        }
                    }
                    isSrcSaf && !isDstSaf -> {
                        // SAF → File
                        val destDir = File(destinationDir)
                        val destFile = File(destDir, name)
                        val dest = when {
                            !destFile.exists() -> destFile
                            resolution == ConflictResolution.REPLACE -> {
                                destFile.deleteRecursively(); destFile
                            }
                            resolution == ConflictResolution.RENAME -> resolveConflict(destDir, name)
                            resolution == ConflictResolution.SKIP -> continue
                            else -> continue
                        }
                        if (safFileOperations.copyFileFromSaf(Uri.parse(srcPath), dest)) count++
                    }
                    !isSrcSaf && isDstSaf -> {
                        // File → SAF
                        val src = File(srcPath)
                        if (!src.exists()) continue
                        val destUri = Uri.parse(destinationDir)
                        val existing = safFileOperations.findFile(destUri, src.name)
                        when {
                            existing == null -> { /* no conflict */ }
                            resolution == ConflictResolution.REPLACE -> existing.delete()
                            resolution == ConflictResolution.SKIP -> continue
                            resolution == ConflictResolution.RENAME -> { /* name will auto-increment via SAF */ }
                        }
                        val targetName = if (existing != null && resolution == ConflictResolution.RENAME) {
                            generateSafRename(destUri, src.name)
                        } else {
                            src.name
                        }
                        if (safFileOperations.copyFileToSaf(src, destUri, targetName)) count++
                    }
                    else -> {
                        // SAF → SAF
                        val destUri = Uri.parse(destinationDir)
                        val existing = safFileOperations.findFile(destUri, name)
                        when {
                            existing == null -> { /* no conflict */ }
                            resolution == ConflictResolution.REPLACE -> existing.delete()
                            resolution == ConflictResolution.SKIP -> continue
                            resolution == ConflictResolution.RENAME -> { /* handled below */ }
                        }
                        val targetName = if (existing != null && resolution == ConflictResolution.RENAME) {
                            generateSafRename(destUri, name)
                        } else {
                            name
                        }
                        val mimeType = android.webkit.MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(name.substringAfterLast('.', ""))
                            ?: "application/octet-stream"
                        if (safFileOperations.copySafToSaf(Uri.parse(srcPath), destUri, targetName, mimeType)) count++
                    }
                }
            }
            EcosystemLogger.d(HaronConstants.TAG, "Скопировано $count файлов в $destinationDir")
            Result.success(count)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Ошибка копирования: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun moveFilesWithResolutions(
        sourcePaths: List<String>,
        destinationDir: String,
        resolutions: Map<String, ConflictResolution>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val isDstSaf = isContentUri(destinationDir)
            if (!isDstSaf) {
                val dstRestricted = isRestrictedAndroidPath(destinationDir)
                if (!dstRestricted) {
                    val destDir = File(destinationDir)
                    if (!destDir.isDirectory) {
                        return@withContext Result.failure(
                            IllegalArgumentException(context.getString(R.string.error_dest_not_folder, destinationDir))
                        )
                    }
                }
            }
            var count = 0
            val movedFromDownload = mutableListOf<Pair<String, String>>()
            for (srcPath in sourcePaths) {
                val resolution = resolutions[srcPath] ?: ConflictResolution.RENAME
                val isSrcSaf = isContentUri(srcPath)

                when {
                    !isSrcSaf && !isDstSaf -> {
                        val srcRestricted = isRestrictedAndroidPath(srcPath)
                        val dstRestricted = isRestrictedAndroidPath(destinationDir)

                        if ((srcRestricted || dstRestricted) && shizukuManager.ensureServiceBound()) {
                            // Shizuku path — move via IPC
                            val srcName = srcPath.substringAfterLast('/')
                            val destPath = "$destinationDir/$srcName"
                            val destExists = shizukuManager.exists(destPath) == true
                            val resolvedDest = when {
                                !destExists -> destPath
                                resolution == ConflictResolution.REPLACE -> {
                                    shizukuManager.deleteRecursively(destPath); destPath
                                }
                                resolution == ConflictResolution.RENAME -> resolveConflictViaShizuku(destinationDir, srcName)
                                resolution == ConflictResolution.SKIP -> continue
                                else -> continue
                            }
                            // Guard: cannot move folder into itself
                            val isDir = shizukuManager.isDirectory(srcPath) == true
                            if (isDir && resolvedDest.startsWith("$srcPath/")) continue
                            // Try rename first (atomic, same filesystem)
                            val moved = shizukuManager.renameTo(srcPath, resolvedDest)
                            if (!moved) {
                                // Fallback: copy + delete
                                val isReplace = resolution == ConflictResolution.REPLACE
                                val copyOk = if (isDir) {
                                    shizukuManager.copyDirectoryRecursively(srcPath, resolvedDest, isReplace)
                                } else {
                                    shizukuManager.copyFile(srcPath, resolvedDest, isReplace)
                                }
                                if (copyOk) shizukuManager.deleteRecursively(srcPath)
                                else {
                                    EcosystemLogger.e(HaronConstants.TAG, "Shizuku move failed: $srcPath → $resolvedDest")
                                    continue
                                }
                            }
                            movedFromDownload.add(srcPath to resolvedDest)
                            count++
                        } else {
                            // File → File (normal paths)
                            val src = File(srcPath)
                            if (!src.exists()) continue
                            val destDir = File(destinationDir)
                            val destFile = File(destDir, src.name)
                            val dest = when {
                                !destFile.exists() -> destFile
                                resolution == ConflictResolution.REPLACE -> {
                                    destFile.deleteRecursively(); destFile
                                }
                                resolution == ConflictResolution.RENAME -> resolveConflict(destDir, src.name)
                                resolution == ConflictResolution.SKIP -> continue
                                else -> continue
                            }
                            // Guard: cannot move folder into itself
                            if (src.isDirectory && dest.absolutePath.startsWith(src.absolutePath + File.separator)) {
                                continue
                            }
                            val moved = src.renameTo(dest)
                            if (!moved) {
                                val isReplace = resolution == ConflictResolution.REPLACE
                                if (src.isDirectory) safeCopyDirectory(src, dest, overwrite = isReplace)
                                else src.copyTo(dest, overwrite = isReplace)
                                src.deleteRecursively()
                            }
                            movedFromDownload.add(srcPath to dest.absolutePath)
                            count++
                        }
                    }
                    else -> {
                        // Mixed SAF: copy + delete source
                        val copyResult = copyFilesWithResolutions(
                            listOf(srcPath), destinationDir, mapOf(srcPath to resolution)
                        )
                        if (copyResult.isSuccess && (copyResult.getOrNull() ?: 0) > 0) {
                            if (isSrcSaf) {
                                safFileOperations.deleteFile(Uri.parse(srcPath))
                            } else if (isRestrictedAndroidPath(srcPath) && shizukuManager.ensureServiceBound()) {
                                shizukuManager.deleteRecursively(srcPath)
                            } else {
                                File(srcPath).deleteRecursively()
                            }
                            count++
                        }
                    }
                }
            }
            // Clean DownloadManager orphan records (AOSP bug #148573846)
            if (movedFromDownload.isNotEmpty()) {
                com.vamp.haron.common.util.DownloadManagerCleaner.cleanAfterMove(context, movedFromDownload)
            }
            EcosystemLogger.d(HaronConstants.TAG, "Перемещено $count файлов в $destinationDir")
            Result.success(count)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Ошибка перемещения: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun deleteFiles(paths: List<String>): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                var count = 0
                for (path in paths) {
                    if (isContentUri(path)) {
                        if (safFileOperations.deleteFile(Uri.parse(path))) count++
                    } else if (isRestrictedAndroidPath(path) && shizukuManager.ensureServiceBound()) {
                        if (shizukuManager.deleteRecursively(path)) count++
                        else EcosystemLogger.e(HaronConstants.TAG, "Shizuku delete failed: $path")
                    } else {
                        val file = File(path)
                        if (!file.exists()) continue
                        val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                        if (deleted) count++
                    }
                }
                EcosystemLogger.d(HaronConstants.TAG, "Удалено $count файлов")
                Result.success(count)
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "Ошибка удаления: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun renameFile(path: String, newName: String): Result<String> =
        withContext(Dispatchers.IO) {
            if (isContentUri(path)) {
                return@withContext renameSafFile(path, newName)
            }
            try {
                if (isRestrictedAndroidPath(path) && shizukuManager.ensureServiceBound()) {
                    // Shizuku rename for restricted paths
                    val parentDir = path.substringBeforeLast('/')
                    val destPath = "$parentDir/$newName"
                    if (shizukuManager.exists(destPath) == true) {
                        return@withContext Result.failure(
                            IllegalArgumentException(context.getString(R.string.error_file_name_exists, newName))
                        )
                    }
                    val success = shizukuManager.renameTo(path, destPath)
                    return@withContext if (success) {
                        EcosystemLogger.d(HaronConstants.TAG, "Shizuku переименовано: $path → $newName")
                        Result.success(destPath)
                    } else {
                        Result.failure(Exception(context.getString(R.string.error_rename_failed)))
                    }
                }
                val file = File(path)
                if (!file.exists()) {
                    return@withContext Result.failure(
                        IllegalArgumentException(context.getString(R.string.error_file_not_exists, path))
                    )
                }
                val dest = File(file.parentFile, newName)
                if (dest.exists()) {
                    return@withContext Result.failure(
                        IllegalArgumentException(context.getString(R.string.error_file_name_exists, newName))
                    )
                }
                val success = file.renameTo(dest)
                if (success) {
                    EcosystemLogger.d(HaronConstants.TAG, "Переименовано: ${file.name} → $newName")
                    Result.success(dest.absolutePath)
                } else {
                    Result.failure(Exception(context.getString(R.string.error_rename_failed)))
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "Ошибка переименования: ${e.message}")
                Result.failure(e)
            }
        }

    private fun renameSafFile(path: String, newName: String): Result<String> {
        val uri = Uri.parse(path)
        val newUri = safFileOperations.renameFile(uri, newName)
        return if (newUri != null) {
            EcosystemLogger.d(HaronConstants.TAG, "SAF переименовано: $newName")
            Result.success(newUri.toString())
        } else {
            Result.failure(Exception(context.getString(R.string.error_rename_saf)))
        }
    }

    override suspend fun createDirectory(parentPath: String, name: String): Result<String> =
        withContext(Dispatchers.IO) {
            if (isContentUri(parentPath)) {
                val uri = safFileOperations.createDirectory(Uri.parse(parentPath), name)
                return@withContext if (uri != null) {
                    EcosystemLogger.d(HaronConstants.TAG, "SAF папка создана: $name")
                    Result.success(uri.toString())
                } else {
                    Result.failure(Exception(context.getString(R.string.error_create_folder_saf)))
                }
            }
            try {
                val dirPath = "$parentPath/$name"
                if (isRestrictedAndroidPath(parentPath) && shizukuManager.ensureServiceBound()) {
                    // Shizuku mkdir for restricted paths
                    if (shizukuManager.exists(dirPath) == true) {
                        return@withContext Result.failure(
                            IllegalArgumentException(context.getString(R.string.error_folder_exists, name))
                        )
                    }
                    val created = shizukuManager.mkdirs(dirPath)
                    return@withContext if (created) {
                        EcosystemLogger.d(HaronConstants.TAG, "Shizuku создана папка: $dirPath")
                        Result.success(dirPath)
                    } else {
                        Result.failure(Exception(context.getString(R.string.error_create_folder)))
                    }
                }
                val dir = File(parentPath, name)
                if (dir.exists()) {
                    return@withContext Result.failure(
                        IllegalArgumentException(context.getString(R.string.error_folder_exists, name))
                    )
                }
                val created = dir.mkdirs()
                if (created) {
                    EcosystemLogger.d(HaronConstants.TAG, "Создана папка: ${dir.absolutePath}")
                    Result.success(dir.absolutePath)
                } else {
                    Result.failure(Exception(context.getString(R.string.error_create_folder)))
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "Ошибка создания папки: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun createFile(
        parentPath: String,
        name: String,
        content: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (isContentUri(parentPath)) {
            val mimeType = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(name.substringAfterLast('.', ""))
                ?: "text/plain"
            val uri = safFileOperations.createFile(Uri.parse(parentPath), name, mimeType)
            if (uri != null && content.isNotEmpty()) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
            }
            return@withContext if (uri != null) {
                EcosystemLogger.d(HaronConstants.TAG, "SAF файл создан: $name")
                Result.success(uri.toString())
            } else {
                Result.failure(Exception(context.getString(R.string.error_create_file_saf)))
            }
        }
        try {
            val parent = File(parentPath)
            if (!parent.isDirectory) {
                return@withContext Result.failure(
                    IllegalArgumentException(context.getString(R.string.error_parent_not_exists, parentPath))
                )
            }
            val target = resolveConflict(parent, name)
            target.writeText(content)
            EcosystemLogger.d(HaronConstants.TAG, "Создан файл: ${target.absolutePath}")
            Result.success(target.absolutePath)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Ошибка создания файла: ${e.message}")
            Result.failure(e)
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

    /** Resolve name conflict via Shizuku exists() for restricted paths */
    private fun resolveConflictViaShizuku(destDir: String, name: String): String {
        var targetPath = "$destDir/$name"
        if (shizukuManager.exists(targetPath) != true) return targetPath
        val baseName = name.substringBeforeLast('.', name)
        val ext = if ('.' in name) ".${name.substringAfterLast('.')}" else ""
        var counter = 1
        while (shizukuManager.exists(targetPath) == true) {
            targetPath = "$destDir/${baseName}($counter)$ext"
            counter++
        }
        return targetPath
    }

    /**
     * Safe directory copy: takes a snapshot of the file tree BEFORE copying
     * to avoid infinite recursion when dest is inside src.
     * Also rejects copying a folder into itself.
     */
    private fun safeCopyDirectory(src: File, dest: File, overwrite: Boolean): Boolean {
        val srcAbsolute = src.absolutePath + File.separator
        if (dest.absolutePath.startsWith(srcAbsolute)) {
            throw IllegalArgumentException("Cannot copy folder into itself")
        }
        // Snapshot the tree BEFORE any writes
        val snapshot = src.walkTopDown().toList()
        for (file in snapshot) {
            val relPath = file.toRelativeString(src)
            val dstFile = File(dest, relPath)
            if (file.isDirectory) {
                dstFile.mkdirs()
            } else {
                dstFile.parentFile?.mkdirs()
                file.copyTo(dstFile, overwrite = overwrite)
            }
        }
        return true
    }

    private fun generateSafRename(parentUri: Uri, name: String): String {
        val baseName = name.substringBeforeLast('.', name)
        val ext = if ('.' in name) ".${name.substringAfterLast('.')}" else ""
        var counter = 1
        var candidate = "${baseName}($counter)$ext"
        while (safFileOperations.findFile(parentUri, candidate) != null) {
            counter++
            candidate = "${baseName}($counter)$ext"
        }
        return candidate
    }
}
