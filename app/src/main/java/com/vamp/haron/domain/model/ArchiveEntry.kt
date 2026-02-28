package com.vamp.haron.domain.model

data class ArchiveEntry(
    val name: String,
    val fullPath: String,
    val size: Long,
    val isDirectory: Boolean,
    val compressedSize: Long = 0L,
    val lastModified: Long = 0L,
    val childCount: Int = 0
)
