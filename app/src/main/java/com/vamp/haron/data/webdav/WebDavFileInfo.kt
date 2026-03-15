package com.vamp.haron.data.webdav

data class WebDavFileInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val contentType: String = ""
)

data class WebDavTransferProgress(
    val fileName: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val isUpload: Boolean
)
