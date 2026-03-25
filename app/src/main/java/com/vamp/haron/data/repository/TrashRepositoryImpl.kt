package com.vamp.haron.data.repository

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.TrashEntry
import com.vamp.haron.domain.repository.TrashRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages trash (recycle bin) with per-file metadata (meta.json).
 * Auto-eviction by max size (configurable). Atomic writes for crash safety.
 * Recovery from orphan files when meta.json is empty/corrupted.
 */
@Singleton
class TrashRepositoryImpl @Inject constructor() : TrashRepository {

    private val trashDir: File
        get() = File(HaronConstants.ROOT_PATH, HaronConstants.TRASH_DIR_NAME)

    private val metaFile: File
        get() = File(trashDir, HaronConstants.TRASH_META_FILE)

    private val mutex = Mutex()

    override suspend fun moveToTrash(paths: List<String>): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                mutex.withLock {
                    ensureTrashDir()
                    val entries = readMeta().toMutableList()
                    var count = 0
                    for (path in paths) {
                        val src = File(path)
                        if (!src.exists()) continue
                        val isDir = src.isDirectory
                        val timestamp = System.currentTimeMillis()
                        val id = "${timestamp}_${src.name}"
                        val dest = File(trashDir, id)
                        val moved = src.renameTo(dest)
                        if (!moved) {
                            if (isDir) {
                                src.copyRecursively(dest, overwrite = false)
                            } else {
                                src.copyTo(dest, overwrite = false)
                            }
                            src.deleteRecursively()
                        }
                        entries.add(
                            TrashEntry(
                                id = id,
                                originalPath = path,
                                trashedAt = timestamp,
                                size = if (isDir) dirSize(dest) else dest.length(),
                                isDirectory = isDir
                            )
                        )
                        count++
                    }
                    writeMeta(entries)
                    EcosystemLogger.d(HaronConstants.TAG, "В корзину: $count файлов")
                    Result.success(count)
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "Ошибка перемещения в корзину: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun getTrashEntries(): Result<List<TrashEntry>> =
        withContext(Dispatchers.IO) {
            try {
                mutex.withLock {
                    val entries = readMeta()
                    // Recalculate sizes from real files (meta.size may be stale for directories)
                    val fixed = entries.map { entry ->
                        val file = File(trashDir, entry.id)
                        if (file.exists() && file.isDirectory) {
                            entry.copy(size = dirSize(file), isDirectory = true)
                        } else if (file.exists()) {
                            entry.copy(size = file.length())
                        } else {
                            entry
                        }
                    }
                    Result.success(fixed)
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "Ошибка чтения корзины: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun restoreFromTrash(ids: List<String>): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                mutex.withLock {
                    val entries = readMeta().toMutableList()
                    var count = 0
                    val toRemove = mutableListOf<TrashEntry>()
                    for (id in ids) {
                        val entry = entries.find { it.id == id } ?: continue
                        val src = File(trashDir, id)
                        if (!src.exists()) {
                            toRemove.add(entry)
                            continue
                        }
                        val dest = File(entry.originalPath)
                        val parentDir = dest.parentFile
                        if (parentDir != null && !parentDir.exists()) {
                            parentDir.mkdirs()
                        }
                        val target = resolveConflict(parentDir ?: File(HaronConstants.ROOT_PATH), dest.name)
                        val moved = src.renameTo(target)
                        if (!moved) {
                            if (src.isDirectory) {
                                src.copyRecursively(target, overwrite = false)
                            } else {
                                src.copyTo(target, overwrite = false)
                            }
                            src.deleteRecursively()
                        }
                        toRemove.add(entry)
                        count++
                    }
                    entries.removeAll(toRemove.toSet())
                    writeMeta(entries)
                    EcosystemLogger.d(HaronConstants.TAG, "Восстановлено из корзины: $count файлов")
                    Result.success(count)
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "Ошибка восстановления: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun deleteFromTrash(ids: List<String>): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                mutex.withLock {
                    val entries = readMeta().toMutableList()
                    var count = 0
                    val toRemove = mutableListOf<TrashEntry>()
                    for (id in ids) {
                        val entry = entries.find { it.id == id } ?: continue
                        val file = File(trashDir, id)
                        if (file.exists()) file.deleteRecursively()
                        toRemove.add(entry)
                        count++
                    }
                    entries.removeAll(toRemove.toSet())
                    writeMeta(entries)
                    EcosystemLogger.d(HaronConstants.TAG, "Удалено из корзины навсегда: $count файлов")
                    Result.success(count)
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "Ошибка удаления из корзины: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun deleteFromTrashWithProgress(
        ids: List<String>,
        onProgress: suspend (deletedFiles: Int, totalFiles: Int, currentName: String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            mutex.withLock {
                val entries = readMeta().toMutableList()
                val toRemove = mutableListOf<TrashEntry>()
                // Collect all files to delete + count total
                data class FileTask(val file: File, val entryId: String, val entry: TrashEntry)
                val allFiles = mutableListOf<FileTask>()
                for (id in ids) {
                    val entry = entries.find { it.id == id } ?: continue
                    val root = File(trashDir, id)
                    if (!root.exists()) {
                        toRemove.add(entry)
                        continue
                    }
                    if (root.isDirectory) {
                        // Collect all files bottom-up (files first, then dirs)
                        val files = root.walkBottomUp().toList()
                        for (f in files) {
                            allFiles.add(FileTask(f, id, entry))
                        }
                    } else {
                        allFiles.add(FileTask(root, id, entry))
                    }
                    toRemove.add(entry)
                }
                val totalFiles = allFiles.size.coerceAtLeast(1)
                var deletedCount = 0
                for (task in allFiles) {
                    val name = task.file.name
                    task.file.delete()
                    deletedCount++
                    onProgress(deletedCount, totalFiles, name)
                }
                entries.removeAll(toRemove.toSet())
                writeMeta(entries)
                EcosystemLogger.d(HaronConstants.TAG, "Удалено из корзины навсегда: ${toRemove.size} записей, $deletedCount файлов")
                Result.success(toRemove.size)
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Ошибка удаления из корзины: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun emptyTrash(): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                mutex.withLock {
                    val entries = readMeta()
                    val count = entries.size
                    for (entry in entries) {
                        File(trashDir, entry.id).deleteRecursively()
                    }
                    writeMeta(emptyList())
                    EcosystemLogger.d(HaronConstants.TAG, "Корзина очищена: $count файлов")
                    Result.success(count)
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "Ошибка очистки корзины: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun cleanExpired(): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                mutex.withLock {
                    val entries = readMeta().toMutableList()
                    val ttlMs = TimeUnit.DAYS.toMillis(HaronConstants.TRASH_TTL_DAYS.toLong())
                    val now = System.currentTimeMillis()
                    val expired = entries.filter { now - it.trashedAt > ttlMs }
                    if (expired.isEmpty()) return@withContext Result.success(0)
                    for (entry in expired) {
                        File(trashDir, entry.id).deleteRecursively()
                    }
                    entries.removeAll(expired.toSet())
                    writeMeta(entries)
                    EcosystemLogger.d(HaronConstants.TAG, "Автоочистка корзины: ${expired.size} файлов")
                    Result.success(expired.size)
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "Ошибка автоочистки: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun getTrashSize(): Long =
        withContext(Dispatchers.IO) {
            try {
                // Calculate real size from files on disk (meta.size may be stale)
                if (!trashDir.exists()) return@withContext 0L
                trashDir.walkTopDown()
                    .filter { it.isFile && it.name != "meta.json" }
                    .sumOf { it.length() }
            } catch (_: Exception) {
                0L
            }
        }

    override suspend fun evictToFitSize(maxSizeBytes: Long): Int =
        withContext(Dispatchers.IO) {
            if (maxSizeBytes <= 0L) return@withContext 0
            mutex.withLock {
                val entries = readMeta().toMutableList()
                var currentSize = entries.sumOf { it.size }
                if (currentSize <= maxSizeBytes) return@withLock 0
                // Sort by trashedAt ascending (oldest first)
                val sorted = entries.sortedBy { it.trashedAt }
                val toRemove = mutableListOf<TrashEntry>()
                for (entry in sorted) {
                    if (currentSize <= maxSizeBytes) break
                    File(trashDir, entry.id).deleteRecursively()
                    currentSize -= entry.size
                    toRemove.add(entry)
                }
                entries.removeAll(toRemove.toSet())
                writeMeta(entries)
                if (toRemove.isNotEmpty()) {
                    EcosystemLogger.d(
                        HaronConstants.TAG,
                        "Корзина: удалено ${toRemove.size} старых записей для освобождения места"
                    )
                }
                toRemove.size
            }
        }

    // --- Helpers ---

    private fun ensureTrashDir() {
        if (!trashDir.exists()) trashDir.mkdirs()
    }

    private fun readMeta(): List<TrashEntry> {
        val entries = readMetaFromFile()
        // If meta is empty but trash folder has orphan files — recover
        if (entries.isEmpty()) {
            val recovered = recoverOrphanEntries()
            if (recovered.isNotEmpty()) {
                EcosystemLogger.w(HaronConstants.TAG, "Корзина: восстановлено ${recovered.size} записей из файловой системы")
                writeMeta(recovered)
                return recovered
            }
        }
        return entries
    }

    private fun readMetaFromFile(): List<TrashEntry> {
        if (!metaFile.exists()) return emptyList()
        return try {
            val json = metaFile.readText()
            if (json.isBlank()) return emptyList()
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                TrashEntry(
                    id = obj.getString("id"),
                    originalPath = obj.getString("originalPath"),
                    trashedAt = obj.getLong("trashedAt"),
                    size = obj.optLong("size", 0L),
                    isDirectory = obj.optBoolean("isDirectory", false)
                )
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Ошибка парсинга meta.json: ${e.message}")
            emptyList()
        }
    }

    /** Recover trash entries from orphan files when meta.json is lost/empty */
    private fun recoverOrphanEntries(): List<TrashEntry> {
        if (!trashDir.exists()) return emptyList()
        val orphans = trashDir.listFiles()?.filter {
            it.name != HaronConstants.TRASH_META_FILE
        } ?: return emptyList()
        if (orphans.isEmpty()) return emptyList()

        return orphans.map { file ->
            val name = file.name
            // Parse timestamp from name format: {timestamp}_{originalName}
            val underscoreIdx = name.indexOf('_')
            val timestamp = if (underscoreIdx > 0) {
                name.substring(0, underscoreIdx).toLongOrNull() ?: file.lastModified()
            } else {
                file.lastModified()
            }
            val originalName = if (underscoreIdx > 0) name.substring(underscoreIdx + 1) else name
            TrashEntry(
                id = name,
                originalPath = "${HaronConstants.ROOT_PATH}/$originalName",
                trashedAt = timestamp,
                size = if (file.isDirectory) dirSize(file) else file.length(),
                isDirectory = file.isDirectory
            )
        }
    }

    private fun writeMeta(entries: List<TrashEntry>) {
        ensureTrashDir()
        val array = JSONArray()
        for (entry in entries) {
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("originalPath", entry.originalPath)
                    put("trashedAt", entry.trashedAt)
                    put("size", entry.size)
                    put("isDirectory", entry.isDirectory)
                }
            )
        }
        // Atomic write: write to temp file, then rename to prevent corruption
        val tmpFile = File(trashDir, "meta.json.tmp")
        tmpFile.writeText(array.toString())
        if (tmpFile.length() > 0 || entries.isEmpty()) {
            metaFile.delete()
            tmpFile.renameTo(metaFile)
        } else {
            tmpFile.delete()
        }
    }

    private fun dirSize(dir: File): Long {
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
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
}
