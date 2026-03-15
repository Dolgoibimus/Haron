package com.vamp.haron.data.sftp

data class SftpFileInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val permissions: String = ""
)
