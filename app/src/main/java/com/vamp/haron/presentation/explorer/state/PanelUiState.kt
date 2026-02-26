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
    val isSearchActive: Boolean = false,
    val searchInContent: Boolean = false,
    val contentSearchSnippets: Map<String, String>? = null,
    val isContentIndexing: Boolean = false,
    val contentIndexProgress: String? = null,
    val renamingPath: String? = null,
    val statusMessage: String? = null,
    val gridColumns: Int = 1,
    val navigationHistory: List<String> = emptyList(),
    val historyIndex: Int = -1,
    val isSafPath: Boolean = false,
    val scrollToIndex: Int = 0,
    val scrollToTrigger: Long = 0L,
    val showProtected: Boolean = false
)
