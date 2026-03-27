package com.vamp.haron.domain.model

data class AppFileEntry(
    val path: String,
    val name: String,
    val size: Long,           // -1 = unknown/no access
    val isDirectory: Boolean,
    val isAccessible: Boolean, // false = needs Shizuku
    val children: List<AppFileEntry> = emptyList()
)

data class AppFilesInfo(
    val totalSize: Long,       // sum of all accessible sizes
    val entries: List<AppFileEntry>,
    val shizukuAvailable: Boolean
)
