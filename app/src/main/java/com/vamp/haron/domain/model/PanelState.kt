package com.vamp.haron.domain.model

import com.vamp.haron.data.model.SortOrder

data class PanelState(
    val currentPath: String,
    val files: List<FileEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val sortOrder: SortOrder = SortOrder()
)
