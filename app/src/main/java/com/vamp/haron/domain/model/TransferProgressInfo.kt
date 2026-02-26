package com.vamp.haron.domain.model

data class TransferProgressInfo(
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val currentFileName: String = "",
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val resumeOffset: Long = 0L
) {
    val fraction: Float
        get() = if (totalBytes > 0) bytesTransferred.toFloat() / totalBytes else 0f
}
