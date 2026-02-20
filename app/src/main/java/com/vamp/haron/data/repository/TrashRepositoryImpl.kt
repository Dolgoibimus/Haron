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
                        val timestamp = System.currentTimeMillis()
                        val id = "${timestamp}_${src.name}"
                        val dest = File(trashDir, id)
                        val moved = src.renameTo(dest)
                        if (!moved) {
                            if (src.isDirectory) {
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
                                size = if (src.isDirectory) dirSize(dest) else dest.length(),
                                isDirectory = src.isDirectory
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
                    Result.success(readMeta())
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
                val entries = mutex.withLock { readMeta() }
                entries.sumOf { it.size }
            } catch (_: Exception) {
                0L
            }
        }

    // --- Helpers ---

    private fun ensureTrashDir() {
        if (!trashDir.exists()) trashDir.mkdirs()
    }

    private fun readMeta(): List<TrashEntry> {
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
        } catch (_: Exception) {
            emptyList()
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
        metaFile.writeText(array.toString())
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
