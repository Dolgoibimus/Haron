package com.vamp.haron.presentation.explorer.state

import com.vamp.haron.data.model.SortOrder
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.usecase.ExtractProgress

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
    // Deep (recursive) search results — files found in subfolders
    val deepSearchResults: List<FileEntry>? = null,
    val isDeepSearching: Boolean = false,
    val deepSearchProgress: String? = null,
    val renamingPath: String? = null,
    val statusMessage: String? = null,
    val gridColumns: Int = 1,
    val navigationHistory: List<String> = emptyList(),
    val historyIndex: Int = -1,
    val isSafPath: Boolean = false,
    val scrollToIndex: Int = 0,
    val scrollToTrigger: Long = 0L,
    val showProtected: Boolean = false,
    // Archive inline browsing
    val archivePath: String? = null,
    val archiveVirtualPath: String = "",
    val archivePassword: String? = null,
    val archiveExtractProgress: ExtractProgress? = null,
    // Cloud breadcrumb stack: list of (displayName, cloudPath) from root to current
    val cloudBreadcrumbs: List<Pair<String, String>> = emptyList(),
    // Increment to force thumbnail reload (e.g. after album cover save)
    val thumbnailVersion: Int = 0,
    val focusedIndex: Int = -1, // -1 = no cursor
    val shiftMode: Boolean = false, // Shift-selection active
    val shiftAnchor: Int = -1, // anchor index for Shift range selection
    val lockedPaths: Set<String> = emptySet() // files locked by Shift selections
) {
    val isArchiveMode: Boolean get() = archivePath != null
}
