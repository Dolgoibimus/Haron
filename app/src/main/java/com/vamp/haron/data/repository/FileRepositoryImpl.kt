package com.vamp.haron.data.repository

import android.content.Context
import android.net.Uri
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.common.util.toFileEntry
import com.vamp.haron.data.saf.SafFileOperations
import com.vamp.haron.data.saf.SafUriManager
import com.vamp.haron.data.saf.StorageVolumeHelper
import com.vamp.haron.domain.model.ConflictResolution
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.repository.FileRepository
import com.vamp.core.logger.EcosystemLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val safFileOperations: SafFileOperations,
    private val safUriManager: SafUriManager,
    private val storageVolumeHelper: StorageVolumeHelper
) : FileRepository {

    private fun isContentUri(path: String): Boolean = path.startsWith("content://")

    override suspend fun getFiles(path: String): Result<List<FileEntry>> =
        withContext(Dispatchers.IO) {
            if (isContentUri(path)) {
                return@withContext getSafFiles(path)
            }
            try {
                val dir = File(path)
                if (!dir.exists()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Путь не существует: $path")
                    )
                }
                if (!dir.isDirectory) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Не является папкой: $path")
                    )
                }
                val files = dir.listFiles()?.map { it.toFileEntry() } ?: emptyList()
                Result.success(files)
            } catch (e: SecurityException) {
                EcosystemLogger.e(HaronConstants.TAG, "Нет доступа к $path: ${e.message}")
                Result.failure(e)
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "Ошибка чтения $path: ${e.message}")
                Result.failure(e)
            }
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
                    name = "Внутренняя память",
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
            return "SD-карта"
        }
        // Format: "XXXX-XXXX:" for SD card root
        val volumeId = treeDocId.substringBefore(":")
        // Try matching with storage volumes
        val volumes = storageVolumeHelper.getRemovableVolumes()
        return volumes.firstOrNull()?.label ?: "SD-карта"
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
                val destDir = File(destinationDir)
                if (!destDir.isDirectory) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Назначение не является папкой: $destinationDir")
                    )
                }
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
                        // File → File
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
                        if (src.isDirectory) src.copyRecursively(dest, overwrite = false)
                        else src.copyTo(dest, overwrite = false)
                        count++
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
                val destDir = File(destinationDir)
                if (!destDir.isDirectory) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Назначение не является папкой: $destinationDir")
                    )
                }
            }
            var count = 0
            for (srcPath in sourcePaths) {
                val resolution = resolutions[srcPath] ?: ConflictResolution.RENAME
                val isSrcSaf = isContentUri(srcPath)

                when {
                    !isSrcSaf && !isDstSaf -> {
                        // File → File (original logic)
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
                        val moved = src.renameTo(dest)
                        if (!moved) {
                            if (src.isDirectory) src.copyRecursively(dest, overwrite = false)
                            else src.copyTo(dest, overwrite = false)
                            src.deleteRecursively()
                        }
                        count++
                    }
                    else -> {
                        // Mixed SAF: copy + delete source
                        val copyResult = copyFilesWithResolutions(
                            listOf(srcPath), destinationDir, mapOf(srcPath to resolution)
                        )
                        if (copyResult.isSuccess && (copyResult.getOrNull() ?: 0) > 0) {
                            if (isSrcSaf) {
                                safFileOperations.deleteFile(Uri.parse(srcPath))
                            } else {
                                File(srcPath).deleteRecursively()
                            }
                            count++
                        }
                    }
                }
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
                val file = File(path)
                if (!file.exists()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Файл не существует: $path")
                    )
                }
                val dest = File(file.parentFile, newName)
                if (dest.exists()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Файл с таким именем уже существует: $newName")
                    )
                }
                val success = file.renameTo(dest)
                if (success) {
                    EcosystemLogger.d(HaronConstants.TAG, "Переименовано: ${file.name} → $newName")
                    Result.success(dest.absolutePath)
                } else {
                    Result.failure(Exception("Не удалось переименовать файл"))
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
            Result.failure(Exception("Не удалось переименовать SAF файл"))
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
                    Result.failure(Exception("Не удалось создать папку через SAF"))
                }
            }
            try {
                val dir = File(parentPath, name)
                if (dir.exists()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Папка уже существует: $name")
                    )
                }
                val created = dir.mkdirs()
                if (created) {
                    EcosystemLogger.d(HaronConstants.TAG, "Создана папка: ${dir.absolutePath}")
                    Result.success(dir.absolutePath)
                } else {
                    Result.failure(Exception("Не удалось создать папку"))
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
                Result.failure(Exception("Не удалось создать файл через SAF"))
            }
        }
        try {
            val parent = File(parentPath)
            if (!parent.isDirectory) {
                return@withContext Result.failure(
                    IllegalArgumentException("Родительская папка не существует: $parentPath")
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
