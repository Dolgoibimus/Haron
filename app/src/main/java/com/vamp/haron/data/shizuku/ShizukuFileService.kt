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

    override fun destroy() {
        exitProcess(0)
    }
}
