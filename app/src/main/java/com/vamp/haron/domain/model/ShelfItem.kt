package com.vamp.haron.domain.model

data class ShelfItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long
)
