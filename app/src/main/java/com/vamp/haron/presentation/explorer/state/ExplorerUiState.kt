package com.vamp.haron.presentation.explorer.state

import com.vamp.haron.domain.model.ApkInstallInfo
import com.vamp.haron.domain.model.ConflictPair
import com.vamp.haron.domain.model.ConflictResolution
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.model.OperationProgress
import com.vamp.haron.domain.model.OperationType
import com.vamp.haron.domain.model.PanelId
import com.vamp.haron.domain.model.PreviewData
import com.vamp.haron.domain.model.ShelfItem
import com.vamp.haron.domain.model.TrashEntry
import com.vamp.haron.domain.usecase.FileProperties
import com.vamp.haron.domain.usecase.HashResult

data class ExplorerUiState(
    val topPanel: PanelUiState = PanelUiState(),
    val bottomPanel: PanelUiState = PanelUiState(),
    val activePanel: PanelId = PanelId.TOP,
    val panelRatio: Float = 0.5f,
    val dialogState: DialogState = DialogState.None,
    val favorites: List<String> = emptyList(),
    val recentPaths: List<String> = emptyList(),
    val showDrawer: Boolean = false,
    val showShelf: Boolean = false,
    val shelfItems: List<ShelfItem> = emptyList(),
    val dragState: DragState = DragState.Idle,
    val operationProgress: OperationProgress? = null,
    val trashSizeInfo: String = "",
    val themeMode: String = "system",
    val safRoots: List<Pair<String, String>> = emptyList(), // (uri, label)
    val originalFolders: Set<String> = emptySet(),
    val showBookmarkPopup: Boolean = false,
    val showToolsPopup: Boolean = false,
    val bookmarks: Map<Int, String> = emptyMap(),
    val folderSizeCache: Map<String, Long> = emptyMap(),
    val folderSizeCalculating: Boolean = false
)

sealed interface DialogState {
    data object None : DialogState
    data class ConfirmDelete(val paths: List<String>) : DialogState
    data object CreateFromTemplate : DialogState
    data class ShowTrash(
        val entries: List<TrashEntry> = emptyList(),
        val totalSize: Long = 0L,
        val maxSizeMb: Int = 0
    ) : DialogState
    data class ConfirmConflict(
        val conflictPairs: List<ConflictPair>,
        val currentIndex: Int = 0,
        val allPaths: List<String>,
        val destinationDir: String,
        val operationType: OperationType,
        val decisions: Map<String, ConflictResolution> = emptyMap()
    ) : DialogState
    data class QuickPreview(
        val entry: FileEntry,
        val previewData: PreviewData? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        val adjacentFiles: List<FileEntry> = emptyList(),
        val currentFileIndex: Int = 0,
        val previewCache: Map<Int, PreviewData> = emptyMap()
    ) : DialogState
    data class CreateArchive(
        val selectedPaths: List<String>
    ) : DialogState
    data class FilePropertiesState(
        val entry: FileEntry,
        val properties: FileProperties? = null,
        val hashResult: HashResult? = null,
        val isHashCalculating: Boolean = false
    ) : DialogState
    data class ApkInstallDialog(
        val entry: FileEntry,
        val apkInfo: ApkInstallInfo? = null,
        val isLoading: Boolean = true,
        val error: String? = null
    ) : DialogState
    data class EmptyFolderCleanup(
        val folders: List<String> = emptyList(),
        val isRecursive: Boolean = true,
        val selectedPaths: Set<String> = emptySet(),
        val isLoading: Boolean = false
    ) : DialogState
    data class ForceDeleteConfirm(
        val paths: List<String>,
        val names: List<String>
    ) : DialogState
}

enum class FileTemplate(val label: String, val extension: String) {
    FOLDER("Папка", ""),
    TXT("Текстовый файл (.txt)", ".txt"),
    MARKDOWN("Markdown (.md)", ".md"),
    DATED_FOLDER("Папка с датой", "")
}
