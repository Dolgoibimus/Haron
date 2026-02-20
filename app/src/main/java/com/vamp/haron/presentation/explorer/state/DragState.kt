package com.vamp.haron.presentation.explorer.state

import androidx.compose.ui.geometry.Offset
import com.vamp.haron.domain.model.PanelId

sealed interface DragState {
    data object Idle : DragState
    data class Dragging(
        val sourcePanelId: PanelId,
        val draggedPaths: List<String>,
        val dragOffset: Offset,
        val fileCount: Int,
        val previewName: String
    ) : DragState
}
