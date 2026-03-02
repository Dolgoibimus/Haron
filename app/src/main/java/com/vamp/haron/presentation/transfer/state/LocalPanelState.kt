package com.vamp.haron.presentation.transfer.state

import android.os.Environment

data class LocalFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

data class LocalPanelState(
    val currentPath: String = Environment.getExternalStorageDirectory().absolutePath,
    val files: List<LocalFileEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedPaths: Set<String> = emptySet()
)
