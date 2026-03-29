package com.vamp.haron.presentation.explorer.state

import com.vamp.haron.data.usb.UsbVolume
import com.vamp.haron.domain.model.ApkInstallInfo
import com.vamp.haron.domain.model.GestureAction
import com.vamp.haron.domain.model.GestureType
import com.vamp.haron.domain.model.ConflictPair
import com.vamp.haron.domain.model.ConflictResolution
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.model.FileTag
import com.vamp.haron.domain.model.OperationProgress
import com.vamp.haron.domain.model.OperationType
import com.vamp.haron.R
import com.vamp.haron.domain.model.PanelId
import com.vamp.haron.domain.model.PreviewData
import com.vamp.haron.domain.model.ShelfItem
import com.vamp.haron.domain.model.TrashEntry
import com.vamp.haron.domain.usecase.CoverResult
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
    val operationProgress: OperationProgress? = null, // primary (first) progress — backward compat
    val operationProgressList: List<OperationProgress> = emptyList(), // all active operations
    val trashSizeInfo: String = "",
    val themeMode: String = "system",
    val safRoots: List<SafRootInfo> = emptyList(),
    val originalFolders: Set<String> = emptySet(),
    val showBookmarkPopup: Boolean = false,
    val showToolsPopup: Boolean = false,
    val bookmarks: Map<Int, String> = emptyMap(),
    val folderSizeCache: Map<String, Long> = emptyMap(),
    val folderSizeCalculating: Boolean = false,
    val storageSizeCache: Map<String, Long> = emptyMap(), // volume root → total bytes
    val tagDefinitions: List<FileTag> = emptyList(),
    val fileTags: Map<String, List<String>> = emptyMap(),
    val activeTagFilter: String? = null,
    val isShieldUnlocked: Boolean = false,
    val isProtecting: Boolean = false,
    val protectProgress: String? = null,
    val showShieldAuth: Boolean = false,
    val showShieldPinSetup: Boolean = false,
    val showAllProtectedAfterAuth: Boolean = false,
    val usbVolumes: List<UsbVolume> = emptyList(),
    val networkDevices: List<com.vamp.haron.data.network.NetworkDevice> = emptyList(),
    val gestureMappings: Map<GestureType, GestureAction> = GestureType.entries.associateWith { it.defaultAction },
    val quickSendState: QuickSendState = QuickSendState.Idle,
    val isListeningForTransfer: Boolean = false,
    val marqueeEnabled: Boolean = true,
    val quickReceiveProgress: com.vamp.haron.domain.model.TransferProgressInfo? = null,
    val quickReceiveDeviceName: String? = null,
    val torrentState: com.vamp.haron.domain.model.TorrentStreamState = com.vamp.haron.domain.model.TorrentStreamState.Idle
)

sealed interface DialogState {
    data object None : DialogState
    data class ConfirmDelete(val paths: List<String>) : DialogState
    data class CreateFromTemplate(
        val allowedTemplates: List<FileTemplate> = FileTemplate.entries.toList()
    ) : DialogState
    data class ShowTrash(
        val entries: List<TrashEntry> = emptyList(),
        val totalSize: Long = 0L,
        val maxSizeMb: Int = 0,
        val deleteProgress: Float? = null,
        val deleteCurrentName: String? = null
    ) : DialogState
    data class ConfirmConflict(
        val conflictPairs: List<ConflictPair>,
        val currentIndex: Int = 0,
        val allPaths: List<String>,
        val destinationDir: String,
        val operationType: OperationType,
        val decisions: Map<String, ConflictResolution> = emptyMap(),
        val sourcePanelId: PanelId? = null,
        val targetPanelId: PanelId? = null
    ) : DialogState
    data class QuickPreview(
        val entry: FileEntry,
        val previewData: PreviewData? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        val adjacentFiles: List<FileEntry> = emptyList(),
        val currentFileIndex: Int = 0,
        val previewCache: Map<Int, PreviewData> = emptyMap(),
        val resolvedPath: String? = null
    ) : DialogState
    data class CreateArchive(
        val selectedPaths: List<String>
    ) : DialogState
    data class FilePropertiesState(
        val entry: FileEntry,
        val properties: FileProperties? = null,
        val hashResult: HashResult? = null,
        val isHashCalculating: Boolean = false,
        val coverResult: CoverResult? = null,
        val pendingCoverBytes: ByteArray? = null
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
    data class BatchRename(
        val paths: List<String>,
        val entries: List<FileEntry>
    ) : DialogState
    data class TagAssign(val paths: List<String>) : DialogState
    data object TagManage : DialogState
    data class CastModeSelect(
        val filePaths: List<String>,
        val availableModes: List<com.vamp.haron.domain.model.CastMode>
    ) : DialogState
    data class ArchivePassword(
        val panelId: PanelId,
        val archivePath: String,
        val errorMessage: String? = null
    ) : DialogState
    data class ArchiveExtractConflict(
        val archivePanelId: PanelId,
        val destinationDir: String,
        val conflictNames: List<String>,
        val selectedOnly: Boolean
    ) : DialogState
    data class TrashOverflow(
        val paths: List<String>,
        val incomingSize: Long,
        val maxSize: Long
    ) : DialogState
    data class ArchiveCreateConflict(
        val selectedPaths: List<String>,
        val outputPath: String,
        val archiveName: String,
        val password: String?,
        val splitSizeMb: Int
    ) : DialogState
    data class ArchiveExtractOptions(
        val archivePanelId: PanelId,
        val destinationDir: String,
        val selectedOnly: Boolean,
        val archiveName: String,
        val hasSingleRootFolder: Boolean,
        val isFromNormalFolder: Boolean = false,
        val archivePaths: List<String> = emptyList()
    ) : DialogState
    data class ApkDowngradeConfirm(
        val apkFile: java.io.File,
        val packageName: String,
        val installedVersionName: String,
        val installedVersionCode: Long,
        val apkVersionName: String,
        val apkVersionCode: Long
    ) : DialogState
    data class ShizukuNotInstalled(
        val panelId: PanelId,
        val path: String
    ) : DialogState
    data class ShizukuNotRunning(
        val panelId: PanelId,
        val path: String
    ) : DialogState
    data class CloudTransfer(
        val fileName: String,
        val percent: Int = 0,
        val isUpload: Boolean = false,
        val transfers: List<CloudTransferEntry> = emptyList()
    ) : DialogState {
        data class CloudTransferEntry(
            val id: String,
            val fileName: String,
            val percent: Int = 0,
            val isUpload: Boolean = false,
            val bytesTransferred: Long = 0L,
            val totalBytes: Long = 0L,
            val speedBytesPerSec: Long = 0L
        )
    }
    data class CloudCreateFolder(
        val panelId: PanelId,
        val cloudPath: String
    ) : DialogState
    data class DuplicateDialog(
        val paths: List<String>,
        val sourcePanelId: PanelId
    ) : DialogState
    data class TorrentFileSelect(
        val uri: String,
        val files: List<com.vamp.haron.domain.model.TorrentFileInfo>
    ) : DialogState
    data class TorrentBuffering(
        val percent: Int,
        val speed: Long
    ) : DialogState
    data class TorrentMagnetInput(val dummy: Unit = Unit) : DialogState
    data class ExtractArchivesDialog(
        val archivePaths: List<String>,
        val sourcePanelId: PanelId
    ) : DialogState
}

enum class DuplicateDestination {
    SAME_SUBFOLDER,
    OTHER_PANEL_SUBFOLDER,
    OTHER_PANEL_DIRECT
}

enum class ExtractDestination {
    NEXT_TO_ARCHIVE,
    SAME_PANEL,
    OTHER_PANEL
}

enum class FileTemplate(val labelRes: Int, val extension: String) {
    FOLDER(R.string.template_folder, ""),
    TXT(R.string.template_text_file, ".txt"),
    MARKDOWN(R.string.template_markdown, ".md"),
    DATED_FOLDER(R.string.template_dated_folder, "")
}

data class SafRootInfo(
    val label: String,
    val safUri: String, // empty if no access
    val path: String? = null, // direct filesystem path (e.g. /storage/877E-B1EE)
    val totalSpace: Long = 0L,
    val freeSpace: Long = 0L
)
