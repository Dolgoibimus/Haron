package com.vamp.haron.presentation.explorer.state

import com.vamp.haron.data.model.SortOrder
import com.vamp.haron.domain.model.FileEntry

data class PanelUiState(
    val currentPath: String = "",
    val displayPath: String = "",
    val files: List<FileEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val sortOrder: SortOrder = SortOrder(),
    val showHidden: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val searchQuery: String = "",
    val renamingPath: String? = null,
    val statusMessage: String? = null
)
