package com.vamp.haron.domain.model

import com.vamp.haron.R

data class OperationProgress(
    val current: Int,
    val total: Int,
    val currentFileName: String,
    val type: OperationType = OperationType.COPY,
    val isComplete: Boolean = false,
    val error: String? = null,
    val filePercent: Int = -1
)

enum class OperationType(val labelRes: Int) {
    COPY(R.string.operation_type_copy),
    MOVE(R.string.operation_type_move),
    DELETE(R.string.operation_type_delete),
    ARCHIVE(R.string.operation_type_archive),
    DOWNLOAD(R.string.operation_type_download),
    UPLOAD(R.string.operation_type_upload)
}
