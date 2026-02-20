package com.vamp.haron.domain.model

data class ConflictFileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isImage: Boolean
)

data class ConflictPair(
    val source: ConflictFileInfo,
    val destination: ConflictFileInfo
)
