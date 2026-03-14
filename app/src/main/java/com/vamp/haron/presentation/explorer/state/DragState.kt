package com.vamp.haron.presentation.explorer.state

import androidx.compose.ui.geometry.Offset
import com.vamp.haron.domain.model.PanelId

enum class DragOperation { COPY, MOVE }

sealed interface DragState {
    data object Idle : DragState
    data class Dragging(
        val sourcePanelId: PanelId,
        val draggedPaths: List<String>,
        val dragOffset: Offset,
        val fileCount: Int,
        val previewName: String,
        /** Folder path the drag is currently hovering over (for drop-into-folder) */
        val hoveredFolderPath: String? = null,
        val dragOperation: DragOperation = DragOperation.MOVE
    ) : DragState
}
