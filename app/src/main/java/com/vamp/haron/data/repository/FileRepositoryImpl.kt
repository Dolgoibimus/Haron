package com.vamp.haron.data.repository

import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.common.util.toFileEntry
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.repository.FileRepository
import com.vamp.core.logger.EcosystemLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepositoryImpl @Inject constructor() : FileRepository {

    override suspend fun getFiles(path: String): Result<List<FileEntry>> =
        withContext(Dispatchers.IO) {
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
        return roots
    }

    override fun getParentPath(path: String): String? {
        val parent = File(path).parentFile ?: return null
        if (parent.absolutePath.length < HaronConstants.ROOT_PATH.length) return null
        return parent.absolutePath
    }

    override suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationDir: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val destDir = File(destinationDir)
            if (!destDir.isDirectory) {
                return@withContext Result.failure(
                    IllegalArgumentException("Назначение не является папкой: $destinationDir")
                )
            }
            var count = 0
            for (srcPath in sourcePaths) {
                val src = File(srcPath)
                if (!src.exists()) continue
                val dest = resolveConflict(destDir, src.name)
                if (src.isDirectory) {
                    src.copyRecursively(dest, overwrite = false)
                } else {
                    src.copyTo(dest, overwrite = false)
                }
                count++
            }
            EcosystemLogger.d(HaronConstants.TAG, "Скопировано $count файлов в $destinationDir")
            Result.success(count)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Ошибка копирования: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationDir: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val destDir = File(destinationDir)
            if (!destDir.isDirectory) {
                return@withContext Result.failure(
                    IllegalArgumentException("Назначение не является папкой: $destinationDir")
                )
            }
            var count = 0
            for (srcPath in sourcePaths) {
                val src = File(srcPath)
                if (!src.exists()) continue
                val dest = resolveConflict(destDir, src.name)
                val moved = src.renameTo(dest)
                if (!moved) {
                    // fallback: copy + delete
                    if (src.isDirectory) {
                        src.copyRecursively(dest, overwrite = false)
                    } else {
                        src.copyTo(dest, overwrite = false)
                    }
                    src.deleteRecursively()
                }
                count++
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
                    val file = File(path)
                    if (!file.exists()) continue
                    val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                    if (deleted) count++
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

    override suspend fun createDirectory(parentPath: String, name: String): Result<String> =
        withContext(Dispatchers.IO) {
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
}
