package com.vamp.haron.domain.model

data class TrashEntry(
    val id: String,
    val originalPath: String,
    val trashedAt: Long,
    val size: Long,
    val isDirectory: Boolean
)
