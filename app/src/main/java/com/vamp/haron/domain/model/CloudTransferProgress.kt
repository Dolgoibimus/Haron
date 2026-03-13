package com.vamp.haron.domain.model

data class CloudTransferProgress(
    val fileName: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val isComplete: Boolean = false,
    val error: String? = null
) {
    val percent: Int
        get() = if (totalBytes > 0) ((bytesTransferred * 100) / totalBytes).toInt() else 0
}
