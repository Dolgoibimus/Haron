package com.vamp.haron.presentation.explorer.state

import com.vamp.haron.domain.model.OperationProgress
import com.vamp.haron.domain.model.OperationType
import com.vamp.haron.domain.model.PanelId
import com.vamp.haron.domain.model.TrashEntry

data class ExplorerUiState(
    val topPanel: PanelUiState = PanelUiState(),
    val bottomPanel: PanelUiState = PanelUiState(),
    val activePanel: PanelId = PanelId.TOP,
    val panelRatio: Float = 0.5f,
    val dialogState: DialogState = DialogState.None,
    val favorites: List<String> = emptyList(),
    val recentPaths: List<String> = emptyList(),
    val showFavoritesPanel: Boolean = false,
    val dragState: DragState = DragState.Idle,
    val operationProgress: OperationProgress? = null,
    val trashSizeInfo: String = "",
    val themeMode: String = "system"
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
        val conflictNames: List<String>,
        val allPaths: List<String>,
        val destinationDir: String,
        val operationType: OperationType
    ) : DialogState
}

enum class FileTemplate(val label: String, val extension: String) {
    FOLDER("Папка", ""),
    TXT("Текстовый файл (.txt)", ".txt"),
    MARKDOWN("Markdown (.md)", ".md"),
    DATED_FOLDER("Папка с датой", "")
}

