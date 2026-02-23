package com.vamp.haron.domain.model

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String,
    val isHidden: Boolean,
    val childCount: Int,
    val isContentUri: Boolean = false,
    val isProtected: Boolean = false
)
