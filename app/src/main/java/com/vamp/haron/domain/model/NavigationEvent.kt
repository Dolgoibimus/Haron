package com.vamp.haron.domain.model

sealed interface NavigationEvent {
    data class OpenMediaPlayer(
        val startIndex: Int = 0 // index in PlaylistHolder.items
    ) : NavigationEvent

    data class OpenTextEditor(
        val filePath: String,
        val fileName: String
    ) : NavigationEvent

    data class OpenTextEditorCloud(
        val localCachePath: String,
        val fileName: String,
        val cloudUri: String, // cloud://gdrive/fileId
        val otherPanelPath: String = "" // local path of the other panel for "save locally"
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

    data class OpenDocumentViewer(
        val filePath: String,
        val fileName: String
    ) : NavigationEvent

    data object OpenStorageAnalysis : NavigationEvent

    data object OpenDuplicateDetector : NavigationEvent

    data object OpenAppManager : NavigationEvent

    data object OpenSettings : NavigationEvent

    data object OpenFeatures : NavigationEvent

    data object OpenSupport : NavigationEvent
    data object OpenAbout : NavigationEvent

    data object OpenGlobalSearch : NavigationEvent

    data object OpenTransfer : NavigationEvent

    data object OpenTerminal : NavigationEvent

    data class HandleExternalFile(
        val filePath: String,
        val fileName: String,
        val mimeType: String?
    ) : NavigationEvent

    data object OpenComparison : NavigationEvent

    data object OpenSteganography : NavigationEvent

    data object OpenScanner : NavigationEvent

    data class RequestSafAccess(
        val panelId: PanelId,
        val filePath: String,
        val initialSafUri: android.net.Uri
    ) : NavigationEvent

    data object OpenCloudAuth : NavigationEvent
}
