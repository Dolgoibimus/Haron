package com.vamp.haron.domain.model

sealed interface NavigationEvent {
    data class OpenMediaPlayer(
        val startIndex: Int = 0 // index in PlaylistHolder.items
    ) : NavigationEvent

    data class OpenTextEditor(
        val filePath: String,
        val fileName: String
    ) : NavigationEvent
}
