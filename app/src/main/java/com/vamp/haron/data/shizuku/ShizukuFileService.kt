package com.vamp.haron.data.shizuku

import java.io.File
import kotlin.system.exitProcess

/**
 * AIDL service implementation running as Shizuku UserService (UID 2000 / shell).
 * No Context, no Hilt, no EcosystemLogger — separate process.
 */
class ShizukuFileService : IShizukuFileService.Stub() {

    override fun listFiles(path: String): List<ShizukuFileEntry> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val children = dir.listFiles() ?: return emptyList()
        return children.map { file ->
            ShizukuFileEntry(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else 0L,
                lastModified = file.lastModified(),
                // Exclude hidden files from childCount — UI hides them by default
                childCount = if (file.isDirectory) {
                    file.listFiles()?.count { !it.name.startsWith(".") } ?: 0
                } else 0
            )
        }
    }

    override fun exists(path: String): Boolean = File(path).exists()

    override fun isDirectory(path: String): Boolean = File(path).isDirectory

    override fun copyFile(srcPath: String, destPath: String, overwrite: Boolean): Boolean {
        return try {
            val src = File(srcPath)
            val dest = File(destPath)
            if (!src.exists() || !src.isFile) return false
            dest.parentFile?.mkdirs()
            src.copyTo(dest, overwrite = overwrite)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun copyDirectoryRecursively(srcPath: String, destPath: String, overwrite: Boolean): Boolean {
        return try {
            val src = File(srcPath)
            val dest = File(destPath)
            if (!src.exists() || !src.isDirectory) return false
            // Snapshot tree BEFORE copying to avoid infinite recursion
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
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun deleteRecursively(path: String): Boolean {
        return try {
            val file = File(path)
            if (!file.exists()) return false
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } catch (_: Exception) {
            false
        }
    }

    override fun renameTo(srcPath: String, destPath: String): Boolean {
        return try {
            val src = File(srcPath)
            val dest = File(destPath)
            if (!src.exists()) return false
            src.renameTo(dest)
        } catch (_: Exception) {
            false
        }
    }

    override fun mkdirs(path: String): Boolean {
        return try {
            File(path).mkdirs()
        } catch (_: Exception) {
            false
        }
    }

    override fun calculateDirSize(path: String): Long {
        return try {
            val dir = File(path)
            if (!dir.exists()) return 0L
            if (dir.isFile) return dir.length()
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (_: Exception) {
            0L
        }
    }

    override fun destroy() {
        exitProcess(0)
    }
}
