package com.vamp.haron.data.ftp

import com.vamp.haron.domain.model.FileEntry

data class FtpFileInfo(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val path: String,
    val permissions: String = ""
) {
    fun toFileEntry(host: String, port: Int): FileEntry {
        val fullPath = FtpPathUtils.buildPath(host, port, path)
        return FileEntry(
            name = name,
            path = fullPath,
            isDirectory = isDirectory,
            size = if (isDirectory) 0L else size,
            lastModified = lastModified,
            extension = if (!isDirectory) name.substringAfterLast('.', "").lowercase() else "",
            isHidden = false,
            childCount = 0
        )
    }
}

data class FtpTransferProgress(
    val fileName: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val isUpload: Boolean,
    val speedBytesPerSec: Long = 0L
)
