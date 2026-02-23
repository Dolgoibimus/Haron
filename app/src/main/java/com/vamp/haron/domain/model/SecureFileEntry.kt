package com.vamp.haron.domain.model

data class SecureFileEntry(
    val id: String,
    val originalPath: String,
    val originalName: String,
    val originalSize: Long,
    val isDirectory: Boolean,
    val mimeType: String,
    val addedAt: Long
)
