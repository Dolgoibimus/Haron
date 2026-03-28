package com.vamp.haron.data.usb.ext4

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.domain.model.FileEntry
import java.io.File

private const val TAG = "Ext4FileOps"

/**
 * Handles all file operations for ext4:// paths.
 * Bridges between Haron's file operation system and Ext4UsbManager/lwext4.
 */
class Ext4FileOperations(private val ext4Manager: Ext4UsbManager) {

    val isMounted: Boolean get() = ext4Manager.isMounted

    /** Wrap any write operation — blocks thumbnail loading during the op */
    private inline fun <T> withFileOp(block: () -> T): T {
        Ext4IoScheduler.fileOpActive = true
        return try { block() } finally { Ext4IoScheduler.fileOpActive = false }
    }

    /** List directory → FileEntry list */
    fun listDir(ext4Path: String): List<FileEntry>? {
        val entries = ext4Manager.listDir(ext4Path) ?: return null
        return entries.map { it.toFileEntry(ext4Path) }
    }

    /** Copy local files TO ext4. [onFileCompleted] called after each file for live UI update. */
    fun copyToExt4(
        sourcePaths: List<String>,
        destDir: String,
        onFileCompleted: ((name: String, index: Int, total: Int) -> Unit)? = null
    ): Result<Int> {
        if (!isMounted) return Result.failure(IllegalStateException("ext4 not mounted"))
        return withFileOp { doCopyToExt4(sourcePaths, destDir, onFileCompleted) }
    }

    private fun doCopyToExt4(
        sourcePaths: List<String>, destDir: String,
        onFileCompleted: ((name: String, index: Int, total: Int) -> Unit)?
    ): Result<Int> {
        var success = 0
        var lastError: String? = null
        val total = sourcePaths.size

        for ((index, srcPath) in sourcePaths.withIndex()) {
            val srcFile = File(srcPath)
            if (!srcFile.exists()) {
                lastError = "Source not found: $srcPath"
                continue
            }

            val destPath = appendPath(destDir, srcFile.name)

            if (srcFile.isDirectory) {
                if (!copyDirToExt4(srcFile, destPath)) {
                    lastError = "Failed to copy dir: ${srcFile.name}"
                } else {
                    success++
                    onFileCompleted?.invoke(srcFile.name, index + 1, total)
                }
            } else {
                val ext4Internal = Ext4PathUtils.toInternalPath(destPath)
                EcosystemLogger.d(TAG, "copyToExt4: ${srcFile.absolutePath} → $ext4Internal (${srcFile.length()} bytes)")
                if (ext4Manager.copyFromLocal(srcFile, destPath)) {
                    Ext4Native.nativeCacheFlush()
                    success++
                    onFileCompleted?.invoke(srcFile.name, index + 1, total)
                } else {
                    lastError = "Failed to write: ${srcFile.name}"
                }
            }
        }

        return if (lastError != null && success == 0) {
            Result.failure(Exception(lastError))
        } else {
            Result.success(success)
        }
    }

    /** Copy files FROM ext4 to local storage */
    fun copyFromExt4(ext4Paths: List<String>, localDestDir: String): Result<Int> {
        if (!isMounted) return Result.failure(IllegalStateException("ext4 not mounted"))
        return withFileOp { doCopyFromExt4(ext4Paths, localDestDir) }
    }

    private fun doCopyFromExt4(ext4Paths: List<String>, localDestDir: String): Result<Int> {

        val destDir = File(localDestDir)
        if (!destDir.exists()) destDir.mkdirs()

        var success = 0
        var lastError: String? = null

        for (ext4Path in ext4Paths) {
            val name = ext4Path.substringAfterLast("/")
            val destFile = File(destDir, name)
            val isDir = Ext4Native.nativeIsDirectory(Ext4PathUtils.toInternalPath(ext4Path))

            if (isDir) {
                if (!copyDirFromExt4(ext4Path, destFile)) {
                    lastError = "Failed to copy dir: $name"
                } else success++
            } else {
                EcosystemLogger.d(TAG, "copyFromExt4: $ext4Path → ${destFile.absolutePath}")
                if (ext4Manager.copyToLocal(ext4Path, destFile)) {
                    success++
                } else {
                    lastError = "Failed to read: $name"
                }
            }
        }

        return if (lastError != null && success == 0) {
            Result.failure(Exception(lastError))
        } else {
            Result.success(success)
        }
    }

    /** Delete files/dirs on ext4 */
    fun delete(ext4Paths: List<String>): Result<Int> {
        if (!isMounted) return Result.failure(IllegalStateException("ext4 not mounted"))
        return withFileOp { doDelete(ext4Paths) }
    }

    private fun doDelete(ext4Paths: List<String>): Result<Int> {

        var success = 0
        for (path in ext4Paths) {
            val internal = Ext4PathUtils.toInternalPath(path)
            val isDir = Ext4Native.nativeIsDirectory(internal)
            EcosystemLogger.d(TAG, "delete: $path (isDir=$isDir)")
            if (isDir) {
                val ok = deleteDirRecursive(path)
                EcosystemLogger.d(TAG, "delete dir result: $ok")
                if (ok) { success++; Ext4Native.nativeCacheFlush() }
            } else {
                val ok = ext4Manager.remove(path)
                EcosystemLogger.d(TAG, "delete file result: $ok")
                if (ok) { success++; Ext4Native.nativeCacheFlush() }
            }
        }
        EcosystemLogger.d(TAG, "delete total: $success/${ext4Paths.size}")
        return Result.success(success)
    }

    /** Rename file/dir on ext4 */
    fun rename(ext4Path: String, newName: String): Boolean {
        if (!isMounted) return false
        return withFileOp { doRename(ext4Path, newName) }
    }

    private fun doRename(ext4Path: String, newName: String): Boolean {
        val parent = ext4Path.substringBeforeLast("/")
        val newPath = "$parent/$newName"
        EcosystemLogger.d(TAG, "rename: $ext4Path → $newPath")
        val ok = ext4Manager.rename(ext4Path, newPath)
        if (ok) Ext4Native.nativeCacheFlush()
        EcosystemLogger.d(TAG, "rename result: $ok")
        return ok
    }

    /** Create directory on ext4 */
    fun mkdir(ext4Path: String): Boolean {
        if (!isMounted) return false
        return withFileOp {
            EcosystemLogger.d(TAG, "mkdir: $ext4Path")
            val ok = ext4Manager.mkdir(ext4Path)
            if (ok) Ext4Native.nativeCacheFlush()
            ok
        }
    }

    /** Get file size */
    fun fileSize(ext4Path: String): Long {
        val internal = Ext4PathUtils.toInternalPath(ext4Path)
        return Ext4Native.nativeFileSize(internal)
    }

    /** Copy file from ext4 to local cache for opening. Uses Ext4CacheManager with size limits. */
    fun copyToCache(ext4Path: String, context: android.content.Context): File? {
        if (!isMounted) return null
        return Ext4CacheManager.getCachedFile(context, ext4Path, ext4Manager)
    }

    // --- Private helpers ---

    private fun copyDirToExt4(localDir: File, ext4DirPath: String): Boolean {
        if (!ext4Manager.mkdir(ext4DirPath)) return false
        val children = localDir.listFiles() ?: return true
        for (child in children) {
            val childPath = appendPath(ext4DirPath, child.name)
            if (child.isDirectory) {
                if (!copyDirToExt4(child, childPath)) return false
            } else {
                if (!ext4Manager.copyFromLocal(child, childPath)) return false
            }
        }
        return true
    }

    private fun copyDirFromExt4(ext4DirPath: String, localDir: File): Boolean {
        if (!localDir.mkdirs() && !localDir.exists()) return false
        val entries = ext4Manager.listDir(ext4DirPath) ?: return false
        for (entry in entries) {
            val childExt4 = appendPath(ext4DirPath, entry.name)
            val childLocal = File(localDir, entry.name)
            if (entry.type == Ext4EntryType.DIRECTORY) {
                if (!copyDirFromExt4(childExt4, childLocal)) return false
            } else {
                if (!ext4Manager.copyToLocal(childExt4, childLocal)) return false
            }
        }
        return true
    }

    private fun deleteDirRecursive(ext4DirPath: String): Boolean {
        val entries = ext4Manager.listDir(ext4DirPath) ?: return false
        for (entry in entries) {
            val childPath = appendPath(ext4DirPath, entry.name)
            if (entry.type == Ext4EntryType.DIRECTORY) {
                if (!deleteDirRecursive(childPath)) return false
            } else {
                if (!ext4Manager.remove(childPath)) return false
            }
        }
        return ext4Manager.remove(ext4DirPath)
    }

    private fun appendPath(base: String, name: String): String {
        return if (base.endsWith("/")) "$base$name" else "$base/$name"
    }
}
