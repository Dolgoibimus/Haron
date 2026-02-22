package com.vamp.haron.domain.model

sealed interface NavigationEvent {
    data class OpenMediaPlayer(
        val startIndex: Int = 0 // index in PlaylistHolder.items
    ) : NavigationEvent

    data class OpenTextEditor(
        val filePath: String,
        val fileName: String
    ) : NavigationEvent

    data class OpenGallery(
        val startIndex: Int = 0 // index in GalleryHolder.items
    ) : NavigationEvent

    data class OpenPdfReader(
        val filePath: String,
        val fileName: String
    ) : NavigationEvent

    data class OpenArchiveViewer(
        val filePath: String,
        val fileName: String
    ) : NavigationEvent

    data object OpenStorageAnalysis : NavigationEvent
}
