package com.vamp.haron.presentation.explorer.state

import com.vamp.haron.domain.model.PanelId

data class ExplorerUiState(
    val topPanel: PanelUiState = PanelUiState(),
    val bottomPanel: PanelUiState = PanelUiState(),
    val activePanel: PanelId = PanelId.TOP,
    val panelRatio: Float = 0.5f,
    val dialogState: DialogState = DialogState.None,
    val favorites: List<String> = emptyList(),
    val recentPaths: List<String> = emptyList(),
    val showFavoritesPanel: Boolean = false
)

sealed interface DialogState {
    data object None : DialogState
    data class ConfirmDelete(val paths: List<String>) : DialogState
    data object CreateFromTemplate : DialogState
}

enum class FileTemplate(val label: String, val extension: String) {
    FOLDER("Папка", ""),
    TXT("Текстовый файл (.txt)", ".txt"),
    MARKDOWN("Markdown (.md)", ".md"),
    DATED_FOLDER("Папка с датой", "")
}

