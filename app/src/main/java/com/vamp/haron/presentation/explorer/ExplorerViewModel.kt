package com.vamp.haron.presentation.explorer

import android.content.Context
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.core.db.EcosystemPreferences
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.R
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.data.model.SortOrder
import com.vamp.haron.data.saf.SafUriManager
import com.vamp.haron.data.saf.StorageVolumeHelper
import com.vamp.haron.domain.model.GalleryHolder
import com.vamp.haron.domain.model.NavigationEvent
import com.vamp.haron.domain.model.PlaylistHolder
import com.vamp.haron.domain.model.PreviewData
import com.vamp.haron.domain.model.SearchNavigationHolder
import com.vamp.haron.domain.model.TransferHolder
import com.vamp.haron.domain.model.ShelfItem
import com.vamp.haron.domain.model.ConflictFileInfo
import com.vamp.haron.domain.model.ConflictPair
import com.vamp.haron.domain.model.ConflictResolution
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.model.FileTag
import com.vamp.haron.domain.model.GestureAction
import com.vamp.haron.domain.model.GestureType
import com.vamp.haron.domain.model.OperationProgress
import com.vamp.haron.domain.model.OperationType
import com.vamp.haron.domain.model.PanelId
import com.vamp.haron.domain.repository.FileRepository
import com.vamp.haron.domain.repository.TrashRepository
import com.vamp.haron.domain.usecase.CleanExpiredTrashUseCase
import com.vamp.haron.domain.usecase.CopyFilesUseCase
import com.vamp.haron.domain.usecase.CreateZipUseCase
import com.vamp.haron.domain.usecase.CreateDirectoryUseCase
import com.vamp.haron.domain.usecase.CreateFileUseCase
import com.vamp.haron.domain.usecase.DeleteFilesUseCase
import com.vamp.haron.domain.usecase.EmptyTrashUseCase
import com.vamp.haron.domain.usecase.GetFilesUseCase
import com.vamp.haron.domain.usecase.LoadPreviewUseCase
import com.vamp.haron.domain.usecase.MoveFilesUseCase
import com.vamp.haron.domain.usecase.MoveToTrashUseCase
import com.vamp.haron.domain.usecase.RenameFileUseCase
import com.vamp.haron.domain.usecase.RestoreFromTrashUseCase
import com.vamp.haron.domain.usecase.GetFilePropertiesUseCase
import com.vamp.haron.domain.usecase.CalculateHashUseCase
import com.vamp.haron.domain.usecase.AudioTags
import com.vamp.haron.domain.usecase.CoverResult
import com.vamp.haron.domain.usecase.FetchAlbumCoverUseCase
import com.vamp.haron.domain.usecase.SaveAudioTagsUseCase
import com.vamp.haron.domain.usecase.BrowseArchiveUseCase
import com.vamp.haron.domain.usecase.ExtractArchiveUseCase
import com.vamp.haron.domain.usecase.ReadArchiveEntryUseCase
import com.vamp.haron.domain.usecase.FindEmptyFoldersUseCase
import com.vamp.haron.domain.usecase.LoadApkInstallInfoUseCase
import com.vamp.haron.common.util.HapticManager
import com.vamp.haron.data.network.NetworkDevice
import com.vamp.haron.data.network.NetworkDeviceScanner
import com.vamp.haron.data.network.NetworkDeviceType
import com.vamp.haron.data.transfer.ReceiveFileManager
import com.vamp.haron.domain.model.DiscoveredDevice
import com.vamp.haron.domain.model.TransferProgressInfo
import com.vamp.haron.domain.model.TransferProtocol
import com.vamp.haron.domain.repository.TransferRepository
import com.vamp.haron.presentation.explorer.state.QuickSendState
import com.vamp.haron.data.voice.VoiceCommandManager
import com.vamp.haron.data.voice.VoiceState
import com.vamp.haron.data.security.AuthManager
import com.vamp.haron.data.cloud.CloudManager
import com.vamp.haron.data.cloud.CloudOAuthHelper
import com.vamp.haron.data.ftp.FtpClientManager
import com.vamp.haron.data.ftp.FtpFileInfo
import com.vamp.haron.data.ftp.FtpPathUtils
import com.vamp.haron.data.shizuku.ShizukuManager
import com.vamp.haron.data.usb.UsbStorageManager
import com.vamp.haron.domain.model.CloudProvider
import com.vamp.haron.domain.repository.SecureFolderRepository
import com.vamp.haron.domain.usecase.BatchRenameUseCase
import com.vamp.haron.domain.usecase.ForceDeleteUseCase
import com.vamp.haron.presentation.explorer.state.DragOperation
import com.vamp.haron.presentation.explorer.state.DragState
import com.vamp.haron.presentation.explorer.state.DialogState
import com.vamp.haron.presentation.explorer.state.DuplicateDestination
import com.vamp.haron.presentation.explorer.state.ExtractDestination
import com.vamp.haron.presentation.explorer.state.ExplorerUiState
import com.vamp.haron.presentation.explorer.state.FileTemplate
import com.vamp.haron.presentation.explorer.state.PanelUiState
import com.vamp.haron.presentation.explorer.state.SafRootInfo
import com.vamp.haron.service.FileOperationService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.content.Intent
import android.provider.DocumentsContract
import com.vamp.haron.common.util.iconRes
import com.vamp.haron.common.util.mimeType
import com.vamp.haron.common.util.toFileSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Main ViewModel for dual-panel file explorer. Manages:
 * - File navigation, sorting, search (FTS5 + content grep)
 * - File operations: copy, move, delete, rename, archive, extract
 * - Selection, drag-and-drop, clipboard
 * - Cloud providers (Yandex, GDrive, Dropbox), network protocols (FTP/SFTP/SMB/WebDAV)
 * - Protected folder (AES-256), trash, tags, bookmarks
 * - Media preview, gallery, playlist building
 * - Voice commands, Shizuku fallback for restricted paths
 */
@HiltViewModel
class ExplorerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val getFilesUseCase: GetFilesUseCase,
    private val fileRepository: FileRepository,
    private val preferences: HaronPreferences,
    private val copyFilesUseCase: CopyFilesUseCase,
    private val moveFilesUseCase: MoveFilesUseCase,
    private val deleteFilesUseCase: DeleteFilesUseCase,
    private val renameFileUseCase: RenameFileUseCase,
    private val createDirectoryUseCase: CreateDirectoryUseCase,
    private val createFileUseCase: CreateFileUseCase,
    private val loadPreviewUseCase: LoadPreviewUseCase,
    private val createZipUseCase: CreateZipUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase,
    private val restoreFromTrashUseCase: RestoreFromTrashUseCase,
    private val emptyTrashUseCase: EmptyTrashUseCase,
    private val cleanExpiredTrashUseCase: CleanExpiredTrashUseCase,
    private val trashRepository: TrashRepository,
    private val safUriManager: SafUriManager,
    private val storageVolumeHelper: StorageVolumeHelper,
    private val getFilePropertiesUseCase: GetFilePropertiesUseCase,
    private val calculateHashUseCase: CalculateHashUseCase,
    private val fetchAlbumCoverUseCase: FetchAlbumCoverUseCase,
    private val saveAudioTagsUseCase: SaveAudioTagsUseCase,
    private val loadApkInstallInfoUseCase: LoadApkInstallInfoUseCase,
    private val hapticManager: HapticManager,
    private val forceDeleteUseCase: ForceDeleteUseCase,
    private val findEmptyFoldersUseCase: FindEmptyFoldersUseCase,
    private val batchRenameUseCase: BatchRenameUseCase,
    private val secureFolderRepository: SecureFolderRepository,
    private val authManager: AuthManager,
    private val searchRepository: com.vamp.haron.domain.repository.SearchRepository,
    private val usbStorageManager: UsbStorageManager,
    private val networkDeviceScanner: NetworkDeviceScanner,
    val voiceCommandManager: VoiceCommandManager,
    private val transferRepository: TransferRepository,
    private val receiveFileManager: ReceiveFileManager,
    private val browseArchiveUseCase: BrowseArchiveUseCase,
    private val extractArchiveUseCase: ExtractArchiveUseCase,
    val shizukuManager: ShizukuManager,
    private val cloudManager: CloudManager,
    private val httpFileServer: com.vamp.haron.data.transfer.HttpFileServer,
    private val ftpClientManager: FtpClientManager,
    val archiveThumbnailCache: com.vamp.haron.common.util.ArchiveThumbnailCache,
    private val readArchiveEntryUseCase: ReadArchiveEntryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorerUiState())

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage = _toastMessage.asSharedFlow()
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>(extraBufferCapacity = 1)
    val navigationEvent = _navigationEvent.asSharedFlow()

    /** Track preload jobs to cancel them when user swipes to a new page */
    private val preloadJobs = mutableListOf<Job>()

    // Cloud accounts state
    private val _cloudAccounts = MutableStateFlow(cloudManager.getConnectedAccounts())
    val cloudAccounts: StateFlow<List<com.vamp.haron.domain.model.CloudAccount>> = _cloudAccounts.asStateFlow()

    fun refreshCloudAccounts() {
        _cloudAccounts.value = cloudManager.getConnectedAccounts()
    }

    fun openCloudAuth() {
        // Start HTTP server for Google OAuth loopback callback, wait for it before opening browser
        viewModelScope.launch {
            if (!httpFileServer.isRunning()) {
                httpFileServer.start(emptyList())
            }
            CloudOAuthHelper.setGdriveLoopbackPort(httpFileServer.actualPort)
            EcosystemLogger.d(HaronConstants.TAG, "openCloudAuth: server running on port ${httpFileServer.actualPort}")
            // Only open browser after server is ready
            _navigationEvent.tryEmit(NavigationEvent.OpenCloudAuth)
        }
    }

    fun navigateToCloud(accountId: String) {
        val panelId = _uiState.value.activePanel
        navigateTo(panelId, "cloud://$accountId/")
    }

    fun cloudSignIn(provider: CloudProvider, code: String) {
        viewModelScope.launch {
            cloudManager.handleAuthCode(provider, code).onSuccess { accountId ->
                refreshCloudAccounts()
                navigateToCloud(accountId)
            }.onFailure { e ->
                _toastMessage.tryEmit(appContext.getString(R.string.cloud_error_auth, e.message ?: ""))
            }
        }
    }

    fun getCloudAuthUrl(provider: CloudProvider): String? {
        return cloudManager.getAuthUrl(provider)
    }

    fun cloudSignOut(accountId: String) {
        viewModelScope.launch {
            cloudManager.signOut(accountId)
            refreshCloudAccounts()
        }
    }

    /** Active cloud transfer jobs: transferId → Job */
    private val cloudTransferJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private var transferIdCounter = 0

    /** Mutex to serialize cloud uploads — parallel PUTs cause Yandex to reset ALL connections */
    private val cloudUploadMutex = Mutex()

    private data class CloudDownloadItem(
        val accountId: String,
        val cloudFileId: String,
        val localPath: String,
        val name: String,
        val size: Long
    )

    /**
     * Ensure cloud folder path exists, creating intermediate folders as needed.
     * E.g. relDir="MyFolder/sub1/sub2" → create MyFolder, then sub1 inside it, then sub2.
     * Uses [createdFolders] cache to avoid re-creating existing folders.
     * Returns the cloud path/ID of the deepest folder.
     */
    private suspend fun ensureCloudFolderPath(
        accountId: String,
        rootCloudDir: String,
        relDir: String,
        createdFolders: MutableMap<String, String>
    ): String {
        if (relDir.isEmpty() || relDir == "") return rootCloudDir
        // Check cache first
        createdFolders[relDir]?.let { return it }

        // Build path segment by segment
        val segments = relDir.split("/").filter { it.isNotEmpty() }
        var currentCloudDir = rootCloudDir
        var currentRelPath = ""
        for (segment in segments) {
            currentRelPath = if (currentRelPath.isEmpty()) segment else "$currentRelPath/$segment"
            val cached = createdFolders[currentRelPath]
            if (cached != null) {
                currentCloudDir = cached
                continue
            }
            // Create folder in cloud
            val result = cloudManager.createFolder(accountId, currentCloudDir, segment)
            result.onSuccess { entry ->
                // Extract the cloud path/ID from the created entry
                val createdPath = entry.path
                createdFolders[currentRelPath] = createdPath
                currentCloudDir = createdPath
                EcosystemLogger.d(HaronConstants.TAG, "ensureCloudFolderPath: created '$segment' -> $createdPath")
            }.onFailure { e ->
                // Folder might already exist — try listing to find it
                EcosystemLogger.d(HaronConstants.TAG, "ensureCloudFolderPath: createFolder failed for '$segment': ${e.message}, trying to find existing")
                val listResult = cloudManager.listFiles(accountId, currentCloudDir)
                val existing = listResult.getOrNull()?.find { it.isDirectory && it.name.equals(segment, ignoreCase = true) }
                if (existing != null) {
                    createdFolders[currentRelPath] = existing.path
                    currentCloudDir = existing.path
                    EcosystemLogger.d(HaronConstants.TAG, "ensureCloudFolderPath: found existing '$segment' -> ${existing.path}")
                } else {
                    throw RuntimeException("Failed to create cloud folder '$segment': ${e.message}")
                }
            }
        }
        return currentCloudDir
    }

    /** Start a cloud transfer and track it. Returns transfer ID. */
    private fun launchCloudTransfer(
        fileName: String,
        isUpload: Boolean,
        block: suspend (transferId: String) -> Unit
    ): String {
        val id = "ct_${++transferIdCounter}"
        val job = viewModelScope.launch {
            try {
                block(id)
            } finally {
                cloudTransferJobs.remove(id)
                removeTransferFromDialog(id)
            }
        }
        cloudTransferJobs[id] = job
        addTransferToDialog(id, fileName, 0, isUpload)
        return id
    }

    /** Update progress for a specific transfer */
    private fun updateTransferProgress(
        transferId: String, fileName: String, percent: Int, isUpload: Boolean,
        bytesTransferred: Long = 0L, totalBytes: Long = 0L, speedBytesPerSec: Long = 0L
    ) {
        _uiState.update { state ->
            val current = state.dialogState
            if (current is DialogState.CloudTransfer) {
                val updated = current.transfers.map {
                    if (it.id == transferId) it.copy(
                        fileName = fileName, percent = percent,
                        bytesTransferred = bytesTransferred, totalBytes = totalBytes,
                        speedBytesPerSec = speedBytesPerSec
                    ) else it
                }
                state.copy(dialogState = current.copy(
                    fileName = updated.firstOrNull()?.fileName ?: fileName,
                    percent = updated.firstOrNull()?.percent ?: percent,
                    transfers = updated
                ))
            } else state
        }
    }

    private fun addTransferToDialog(id: String, fileName: String, percent: Int, isUpload: Boolean) {
        _uiState.update { state ->
            val entry = DialogState.CloudTransfer.CloudTransferEntry(id, fileName, percent, isUpload)
            val current = state.dialogState
            if (current is DialogState.CloudTransfer) {
                val newList = current.transfers + entry
                state.copy(dialogState = current.copy(
                    fileName = current.fileName,
                    transfers = newList
                ))
            } else {
                state.copy(dialogState = DialogState.CloudTransfer(
                    fileName = fileName,
                    percent = percent,
                    isUpload = isUpload,
                    transfers = listOf(entry)
                ))
            }
        }
    }

    private fun removeTransferFromDialog(transferId: String) {
        _uiState.update { state ->
            val current = state.dialogState
            if (current is DialogState.CloudTransfer) {
                val remaining = current.transfers.filter { it.id != transferId }
                if (remaining.isEmpty()) {
                    state.copy(dialogState = DialogState.None)
                } else {
                    state.copy(dialogState = current.copy(
                        fileName = remaining.first().fileName,
                        percent = remaining.first().percent,
                        transfers = remaining
                    ))
                }
            } else state
        }
    }

    /** Launch a cloud job tracked in cloudTransferJobs (for batch ops using operationProgress, not CloudTransfer dialog). */
    private fun launchCloudJob(block: suspend () -> Unit) {
        val id = "ct_${++transferIdCounter}"
        cloudTransferJobs[id] = viewModelScope.launch {
            try { block() } finally { cloudTransferJobs.remove(id) }
        }
    }

    fun isCloudPath(path: String): Boolean = path.startsWith("cloud://")

    // ── Operation progress list helpers ──────────────────────────────────

    private var progressIdCounter = 0

    /** Generate a unique ID for a new operation progress. */
    private fun nextProgressId(): String = "op_${++progressIdCounter}"

    /** Update or add an operation progress by ID. Also updates legacy operationProgress for primary. */
    private fun updateProgress(progress: OperationProgress) {
        _uiState.update { state ->
            val list = state.operationProgressList.toMutableList()
            val idx = list.indexOfFirst { it.id == progress.id }
            if (idx >= 0) list[idx] = progress else list.add(progress)
            // Remove completed entries after a short display
            val active = list.filter { !it.isComplete }
            val completed = list.filter { it.isComplete }
            val newList = active + completed
            state.copy(
                operationProgress = newList.firstOrNull(),
                operationProgressList = newList
            )
        }
    }

    /** Remove a completed progress by ID. */
    private fun removeProgress(id: String) {
        _uiState.update { state ->
            val newList = state.operationProgressList.filter { it.id != id }
            state.copy(
                operationProgress = newList.firstOrNull(),
                operationProgressList = newList
            )
        }
    }

    /** Clear all progress entries. */
    private fun clearAllProgress() {
        _uiState.update { it.copy(operationProgress = null, operationProgressList = emptyList()) }
    }

    /** Get Authorization header for cloud thumbnail requests (Yandex needs OAuth, GDrive needs Bearer) */
    fun getCloudAuthHeader(currentPath: String): String? {
        if (!currentPath.startsWith("cloud://yandex")) return null
        val parsed = cloudManager.parseCloudUri(currentPath) ?: return null
        val token = cloudManager.getAccessToken(parsed.accountId)
        return if (token != null) "OAuth $token" else null
    }

    /**
     * Download a cloud file to cache, then open with appropriate viewer.
     */
    /**
     * Open cloud image in gallery — uses thumbnailUrl for direct loading without download.
     * Falls back to cache download if no URL available.
     */
    private fun cloudOpenGallery(panelId: PanelId, entry: FileEntry) {
        val panel = getPanel(panelId)
        val imageFiles = panel.files.filter { !it.isDirectory && it.iconRes() == "image" }
        GalleryHolder.items = imageFiles.map { f ->
            GalleryHolder.GalleryItem(
                filePath = f.path,
                fileName = f.name,
                fileSize = f.size,
                imageUrl = f.thumbnailUrl
            )
        }
        val startIndex = imageFiles.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
        GalleryHolder.startIndex = startIndex
        _navigationEvent.tryEmit(NavigationEvent.OpenGallery(startIndex))
    }

    fun cloudDownloadAndOpen(entry: FileEntry) {
        val parsed = cloudManager.parseCloudUri(entry.path) ?: return
        val (provider, cloudFileId) = parsed

        launchCloudTransfer(entry.name, isUpload = false) { transferId ->
            try {
                val cacheDir = File(appContext.cacheDir, "cloud_downloads")
                cacheDir.mkdirs()
                val localFile = File(cacheDir, entry.name)

                cloudManager.downloadFile(parsed.accountId, cloudFileId, localFile.absolutePath)
                    .collect { progress ->
                        updateTransferProgress(transferId, progress.fileName.ifEmpty { entry.name }, progress.percent, false, progress.bytesTransferred, progress.totalBytes, progress.speedBytesPerSec)
                        if (progress.isComplete) {
                            if (progress.error != null) {
                                _toastMessage.tryEmit(progress.error)
                            } else {
                                val localEntry = entry.copy(path = localFile.absolutePath)
                                onFileClick(_uiState.value.activePanel, localEntry)
                            }
                        }
                    }
            } catch (e: Exception) {
                _toastMessage.tryEmit(appContext.getString(R.string.cloud_error_download, e.message ?: ""))
            }
        }
    }

    /**
     * Download cloud text file, then open in editor with cloud save-back support.
     */
    fun cloudDownloadAndOpenText(entry: FileEntry) {
        val parsed = cloudManager.parseCloudUri(entry.path) ?: return
        val (provider, cloudFileId) = parsed

        launchCloudTransfer(entry.name, isUpload = false) { transferId ->
            try {
                val cacheDir = File(appContext.cacheDir, "cloud_downloads")
                cacheDir.mkdirs()
                val localFile = File(cacheDir, entry.name)

                cloudManager.downloadFile(parsed.accountId, cloudFileId, localFile.absolutePath)
                    .collect { progress ->
                        updateTransferProgress(transferId, progress.fileName.ifEmpty { entry.name }, progress.percent, false, progress.bytesTransferred, progress.totalBytes, progress.speedBytesPerSec)
                        if (progress.isComplete) {
                            if (progress.error != null) {
                                _toastMessage.tryEmit(progress.error)
                            } else {
                                _navigationEvent.tryEmit(
                                    NavigationEvent.OpenTextEditorCloud(
                                        localCachePath = localFile.absolutePath,
                                        fileName = entry.name,
                                        cloudUri = entry.path,
                                        otherPanelPath = getOtherPanelPath()
                                    )
                                )
                            }
                        }
                    }
            } catch (e: Exception) {
                _toastMessage.tryEmit(appContext.getString(R.string.cloud_error_download, e.message ?: ""))
            }
        }
    }

    /**
     * Upload a local file back to cloud, replacing the original.
     */
    fun cloudSaveBack(cloudUri: String, localPath: String) {
        val parsed = cloudManager.parseCloudUri(cloudUri) ?: return
        val (provider, cloudFileId) = parsed

        viewModelScope.launch {
            cloudManager.updateFileContent(parsed.accountId, cloudFileId, localPath)
                .collect { progress ->
                    if (progress.isComplete) {
                        if (progress.error != null) {
                            _toastMessage.tryEmit(progress.error)
                        } else {
                            _toastMessage.tryEmit(appContext.getString(R.string.cloud_save_success))
                        }
                    }
                }
        }
    }

    /** Download cloud archive to cache and navigate into it */
    private fun cloudDownloadAndNavigateArchive(panelId: PanelId, entry: FileEntry) {
        val parsed = cloudManager.parseCloudUri(entry.path) ?: return
        val (provider, cloudFileId) = parsed

        launchCloudTransfer(entry.name, isUpload = false) { transferId ->
            try {
                val cacheDir = File(appContext.cacheDir, "cloud_downloads")
                cacheDir.mkdirs()
                val localFile = File(cacheDir, entry.name)

                cloudManager.downloadFile(parsed.accountId, cloudFileId, localFile.absolutePath)
                    .collect { progress ->
                        updateTransferProgress(transferId, progress.fileName.ifEmpty { entry.name }, progress.percent, false, progress.bytesTransferred, progress.totalBytes, progress.speedBytesPerSec)
                        if (progress.isComplete) {
                            if (progress.error != null) {
                                _toastMessage.tryEmit(progress.error)
                            } else {
                                navigateIntoArchive(panelId, localFile.absolutePath, "", null)
                            }
                        }
                    }
            } catch (e: Exception) {
                _toastMessage.tryEmit(appContext.getString(R.string.cloud_error_download, e.message ?: ""))
            }
        }
    }

    /** Download cloud fb2/fb2.zip/document to cache and open in DocumentViewer */
    private fun cloudDownloadAndOpenDocument(entry: FileEntry) {
        val parsed = cloudManager.parseCloudUri(entry.path) ?: return
        val (provider, cloudFileId) = parsed

        launchCloudTransfer(entry.name, isUpload = false) { transferId ->
            try {
                val cacheDir = File(appContext.cacheDir, "cloud_downloads")
                cacheDir.mkdirs()
                val localFile = File(cacheDir, entry.name)

                cloudManager.downloadFile(parsed.accountId, cloudFileId, localFile.absolutePath)
                    .collect { progress ->
                        updateTransferProgress(transferId, progress.fileName.ifEmpty { entry.name }, progress.percent, false, progress.bytesTransferred, progress.totalBytes, progress.speedBytesPerSec)
                        if (progress.isComplete) {
                            if (progress.error != null) {
                                _toastMessage.tryEmit(progress.error)
                            } else {
                                preferences.lastDocumentFile = localFile.absolutePath
                                _navigationEvent.tryEmit(
                                    NavigationEvent.OpenDocumentViewer(localFile.absolutePath, entry.name)
                                )
                            }
                        }
                    }
            } catch (e: Exception) {
                _toastMessage.tryEmit(appContext.getString(R.string.cloud_error_download, e.message ?: ""))
            }
        }
    }

    /** Download cloud PDF to cache and open in PDF reader */
    private fun cloudDownloadAndOpenPdf(entry: FileEntry) {
        val parsed = cloudManager.parseCloudUri(entry.path) ?: return
        val (provider, cloudFileId) = parsed

        launchCloudTransfer(entry.name, isUpload = false) { transferId ->
            try {
                val cacheDir = File(appContext.cacheDir, "cloud_downloads")
                cacheDir.mkdirs()
                val localFile = File(cacheDir, entry.name)

                cloudManager.downloadFile(parsed.accountId, cloudFileId, localFile.absolutePath)
                    .collect { progress ->
                        updateTransferProgress(transferId, progress.fileName.ifEmpty { entry.name }, progress.percent, false, progress.bytesTransferred, progress.totalBytes, progress.speedBytesPerSec)
                        if (progress.isComplete) {
                            if (progress.error != null) {
                                _toastMessage.tryEmit(progress.error)
                            } else {
                                preferences.lastDocumentFile = localFile.absolutePath
                                _navigationEvent.tryEmit(
                                    NavigationEvent.OpenPdfReader(localFile.absolutePath, entry.name)
                                )
                            }
                        }
                    }
            } catch (e: Exception) {
                _toastMessage.tryEmit(appContext.getString(R.string.cloud_error_download, e.message ?: ""))
            }
        }
    }

    /**
     * Get the other panel's current path (for "save locally" in cloud text editor).
     */
    fun getOtherPanelPath(): String {
        val activeId = _uiState.value.activePanel
        val otherId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        return getPanel(otherId).currentPath
    }

    /**
     * Build cloud breadcrumbs for the current navigation.
     * If navigating to root → single entry. If going deeper → append. If jumping to a breadcrumb → truncate.
     */
    private fun buildCloudBreadcrumbs(
        current: List<Pair<String, String>>,
        cloudFullPath: String,
        providerDisplayName: String,
        cloudPath: String
    ): List<Pair<String, String>> {
        // Check if we're navigating to an already-existing breadcrumb (going back)
        val existingIndex = current.indexOfFirst { it.second == cloudFullPath }
        if (existingIndex >= 0) {
            return current.take(existingIndex + 1)
        }
        // Root navigation
        if (cloudPath == "root" || cloudPath.isEmpty()) {
            return listOf("$providerDisplayName:" to cloudFullPath)
        }
        // If current breadcrumbs are empty or from a different provider → start fresh
        if (current.isEmpty() || !current.first().second.startsWith(cloudFullPath.substringBefore("/", cloudFullPath).take(15))) {
            // Extract folder name from displayPath segments
            return listOf("$providerDisplayName:" to "cloud://${cloudFullPath.removePrefix("cloud://").substringBefore('/')}/root") +
                listOf(cloudPath.substringAfterLast('/').ifEmpty { cloudPath } to cloudFullPath)
        }
        // Going deeper — find the folder name from the file entries of the parent
        val parentPanel = current.lastOrNull()
        val parentPath = parentPanel?.second ?: ""
        // The folder name = name of the entry we clicked (cloudPath is fileId, not name)
        // We need to find it from parent's files
        val parentFiles = try {
            val panelId = _uiState.value.activePanel
            getPanel(panelId).files
        } catch (_: Exception) { emptyList() }
        val folderName = parentFiles.firstOrNull { it.path == cloudFullPath }?.name
            ?: cloudPath.substringAfterLast('/')
        return current + (folderName to cloudFullPath)
    }

    /**
     * Stream cloud media (video/audio) through local HTTP proxy without full download.
     */
    fun cloudStreamAndPlay(entry: FileEntry) {
        val parsed = cloudManager.parseCloudUri(entry.path) ?: return

        viewModelScope.launch {
            EcosystemLogger.d(HaronConstants.TAG, "cloudStreamAndPlay: entry=${entry.name}, path=${entry.path}")

            // Pre-refresh token before streaming (Google tokens expire after 1 hour)
            val freshToken = cloudManager.getFreshAccessToken(parsed.accountId)
            if (freshToken == null) {
                _toastMessage.tryEmit(appContext.getString(R.string.cloud_error_auth, "No token"))
                EcosystemLogger.e(HaronConstants.TAG, "cloudStreamAndPlay: no fresh token for ${parsed.accountId}")
                return@launch
            }
            EcosystemLogger.d(HaronConstants.TAG, "cloudStreamAndPlay: got fresh token (${freshToken.take(10)}...)")

            // Ensure HTTP server is running
            if (!httpFileServer.isRunning()) {
                httpFileServer.start(emptyList())
            }
            EcosystemLogger.d(HaronConstants.TAG, "cloudStreamAndPlay: server port=${httpFileServer.actualPort}")
            httpFileServer.clearCloudStreams()
            // Set up token provider so proxy gets fresh token on each request
            httpFileServer.cloudTokenProvider = { accountId ->
                cloudManager.getAccessToken(accountId)
            }

            // Build playlist from all media files in the current panel
            val panel = getPanel(_uiState.value.activePanel)
            val mediaFiles = panel.files.filter { f ->
                !f.isDirectory && f.iconRes() in listOf("video", "audio")
            }

            mediaFiles.forEach { f ->
                val p = cloudManager.parseCloudUri(f.path) ?: return@forEach
                val streamId = f.path.hashCode().toUInt().toString(16)
                httpFileServer.setupCloudStream(
                    streamId,
                    com.vamp.haron.data.transfer.HttpFileServer.CloudStreamConfig(
                        fileId = p.path,
                        accountId = p.accountId,
                        fileName = f.name,
                        fileSize = f.size
                    )
                )
            }

            PlaylistHolder.items = mediaFiles.map { f ->
                val streamId = f.path.hashCode().toUInt().toString(16)
                val streamUrl = httpFileServer.getCloudStreamUrl(streamId) ?: f.path
                PlaylistHolder.PlaylistItem(
                    filePath = streamUrl,
                    fileName = f.name,
                    fileType = f.iconRes()
                )
            }
            val startIndex = mediaFiles.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
            PlaylistHolder.startIndex = startIndex
            PlaylistHolder.items.forEachIndexed { i, item ->
                EcosystemLogger.d(HaronConstants.TAG, "cloudStreamAndPlay: [$i] ${item.fileName} → ${item.filePath}")
            }
            EcosystemLogger.d(HaronConstants.TAG, "cloudStreamAndPlay: ${mediaFiles.size} tracks, starting at $startIndex, navigating to player")
            _navigationEvent.tryEmit(NavigationEvent.OpenMediaPlayer(startIndex))
        }
    }

    /**
     * Download selected cloud files to the other panel's local directory.
     */
    fun cloudDownloadToLocal() {
        val state = _uiState.value
        val activeId = state.activePanel
        val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        val sourcePanel = getPanel(activeId)
        val targetPanel = getPanel(targetId)
        val selected = sourcePanel.files.filter { it.path in sourcePanel.selectedPaths }
        if (selected.isEmpty()) return

        val targetDir = targetPanel.currentPath
        if (isCloudPath(targetDir) || targetDir.startsWith("content://")) {
            _toastMessage.tryEmit(appContext.getString(R.string.cloud_download_local_only))
            return
        }

        clearSelection(activeId)

        val progressId = nextProgressId()
        launchCloudJob {
            // Flatten: expand cloud directories recursively into file list
            // Each item: (accountId, cloudFileId, localDestPath, fileSize)
            val filesToDownload = mutableListOf<CloudDownloadItem>()

            suspend fun collectFiles(entries: List<FileEntry>, localDir: String) {
                for (entry in entries) {
                    val parsed = cloudManager.parseCloudUri(entry.path) ?: continue
                    if (entry.isDirectory) {
                        // Create local directory
                        val subDir = File(localDir, entry.name)
                        subDir.mkdirs()
                        // List cloud folder contents and recurse
                        cloudManager.listFiles(parsed.accountId, parsed.path).onSuccess { children ->
                            collectFiles(children.map { it.toFileEntry() }, subDir.absolutePath)
                        }.onFailure { e ->
                            EcosystemLogger.e(HaronConstants.TAG, "Cloud download: failed to list folder ${entry.name}: ${e.message}")
                        }
                    } else {
                        filesToDownload.add(CloudDownloadItem(
                            parsed.accountId, parsed.path,
                            File(localDir, entry.name).absolutePath,
                            entry.name, entry.size
                        ))
                    }
                }
            }

            updateProgress(OperationProgress(0, selected.size, "", OperationType.DOWNLOAD, id = progressId))
            collectFiles(selected, targetDir)
            // Refresh target panel immediately so folders appear
            refreshPanel(targetId)

            if (filesToDownload.isEmpty()) {
                updateProgress(OperationProgress(0, 0, "", OperationType.DOWNLOAD, isComplete = true, id = progressId))
                _toastMessage.tryEmit(appContext.getString(R.string.cloud_download_complete, 0))
                delay(2000)
                removeProgress(progressId)
                return@launchCloudJob
            }

            val total = filesToDownload.size
            val totalBytes = filesToDownload.sumOf { it.size }
            var completedBytes = 0L
            var downloaded = 0

            for ((idx, item) in filesToDownload.withIndex()) {
                updateProgress(OperationProgress(
                    idx + 1, total, item.name, OperationType.DOWNLOAD,
                    filePercent = if (totalBytes > 0) ((completedBytes * 100) / totalBytes).toInt() else 0,
                    id = progressId
                ))

                try {
                    cloudManager.downloadFile(item.accountId, item.cloudFileId, item.localPath)
                        .collect { progress ->
                            val overallPercent = if (totalBytes > 0) {
                                ((completedBytes + progress.bytesTransferred) * 100 / totalBytes).toInt()
                            } else 0
                            updateProgress(OperationProgress(
                                idx + 1, total, item.name, OperationType.DOWNLOAD,
                                filePercent = overallPercent.coerceIn(0, 100),
                                id = progressId,
                                speedBytesPerSec = progress.speedBytesPerSec
                            ))
                        }
                    completedBytes += item.size
                    downloaded++
                    // Refresh so file appears immediately
                    if (downloaded % 3 == 0 || downloaded == 1) refreshPanel(targetId)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    completedBytes += item.size
                    EcosystemLogger.e(HaronConstants.TAG, "Cloud download failed: ${item.name}: ${e.message}")
                }
            }
            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
            updateProgress(OperationProgress(
                downloaded, total, "", OperationType.DOWNLOAD, isComplete = true, id = progressId
            ))
            _toastMessage.tryEmit(appContext.getString(R.string.cloud_download_complete, downloaded))

            delay(2000)
            removeProgress(progressId)
        }
    }

    /**
     * Upload selected local files and folders to the cloud panel's current directory.
     * Folders are uploaded recursively — structure is preserved in cloud.
     */
    fun cloudUploadFromLocal() {
        val state = _uiState.value
        val activeId = state.activePanel
        val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        val sourcePanel = getPanel(activeId)
        val targetPanel = getPanel(targetId)
        val selected = sourcePanel.files.filter { it.path in sourcePanel.selectedPaths }
        if (selected.isEmpty()) {
            EcosystemLogger.d(HaronConstants.TAG, "cloudUploadFromLocal: no items selected, aborting")
            return
        }

        val targetCloudPath = targetPanel.currentPath
        val parsed = cloudManager.parseCloudUri(targetCloudPath)
        if (parsed == null) {
            EcosystemLogger.e(HaronConstants.TAG, "cloudUploadFromLocal: cannot parse cloud URI: $targetCloudPath")
            return
        }
        val (provider, cloudDir) = parsed

        // Collect all files recursively (flatten directories)
        val filesToUpload = mutableListOf<Pair<File, String>>() // localFile, relative cloud dir
        for (entry in selected) {
            val file = File(entry.path)
            if (!file.exists()) continue
            if (file.isDirectory) {
                // Walk directory tree
                file.walkTopDown().forEach { f ->
                    if (f.isFile) {
                        // Relative path inside the selected folder: "folderName/sub/file.txt"
                        val relDir = f.parentFile?.toRelativeString(file.parentFile!!)?.replace('\\', '/') ?: ""
                        filesToUpload.add(f to relDir)
                    }
                }
            } else {
                filesToUpload.add(file to "")
            }
        }
        if (filesToUpload.isEmpty()) {
            EcosystemLogger.d(HaronConstants.TAG, "cloudUploadFromLocal: no files found after expanding directories")
            _toastMessage.tryEmit(appContext.getString(R.string.cloud_upload_empty_folder))
            return
        }

        EcosystemLogger.d(HaronConstants.TAG, "cloudUploadFromLocal: provider=$provider, cloudDir=$cloudDir, totalFiles=${filesToUpload.size}")

        clearSelection(activeId)
        val total = filesToUpload.size
        val totalBytes = filesToUpload.sumOf { it.first.length() }
        var completedBytes = 0L

        val progressId = nextProgressId()
        launchCloudJob {
            updateProgress(OperationProgress(0, total, "", OperationType.UPLOAD, id = progressId))

            // Cache of created cloud folders: relativeDirPath -> cloudFolderId/path
            val createdFolders = mutableMapOf<String, String>()
            createdFolders[""] = cloudDir // root = current cloud dir

            var uploaded = 0
            for ((idx, pair) in filesToUpload.withIndex()) {
                val (localFile, relDir) = pair
                EcosystemLogger.d(HaronConstants.TAG, "cloudUploadFromLocal: [${idx + 1}/$total] ${localFile.name} relDir=$relDir")
                updateProgress(OperationProgress(
                    idx + 1, total, localFile.name, OperationType.UPLOAD,
                    filePercent = if (totalBytes > 0) ((completedBytes * 100) / totalBytes).toInt() else 0,
                    id = progressId
                ))

                try {
                    // Ensure cloud folder structure exists
                    val prevFolderCount = createdFolders.size
                    val targetCloudDir = ensureCloudFolderPath(parsed.accountId, cloudDir, relDir, createdFolders)
                    // Refresh cloud panel if new folders were created
                    if (createdFolders.size > prevFolderCount) refreshPanel(targetId)

                    cloudUploadMutex.withLock {
                        cloudManager.uploadFile(parsed.accountId, localFile.absolutePath, targetCloudDir, localFile.name)
                            .collect { progress ->
                                if (progress.error != null) {
                                    EcosystemLogger.e(HaronConstants.TAG, "cloudUploadFromLocal: progress error for ${localFile.name}: ${progress.error}")
                                }
                                val overallPercent = if (totalBytes > 0) {
                                    ((completedBytes + progress.bytesTransferred) * 100 / totalBytes).toInt()
                                } else 0
                                updateProgress(OperationProgress(
                                    idx + 1, total, localFile.name, OperationType.UPLOAD,
                                    filePercent = overallPercent.coerceIn(0, 100),
                                    id = progressId,
                                    speedBytesPerSec = progress.speedBytesPerSec
                                ))
                            }
                    }
                    completedBytes += localFile.length()
                    uploaded++
                    // Refresh cloud panel so files appear progressively
                    if (uploaded % 3 == 0 || uploaded == 1) refreshPanel(targetId)
                    EcosystemLogger.d(HaronConstants.TAG, "cloudUploadFromLocal: [${idx + 1}/$total] ${localFile.name} uploaded OK")
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    completedBytes += localFile.length()
                    EcosystemLogger.e(HaronConstants.TAG, "cloudUploadFromLocal: [${idx + 1}/$total] ${localFile.name} FAILED: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
            EcosystemLogger.d(HaronConstants.TAG, "cloudUploadFromLocal: DONE, uploaded $uploaded/$total files")
            updateProgress(OperationProgress(
                uploaded, total, "", OperationType.UPLOAD, isComplete = true, id = progressId
            ))
            _toastMessage.tryEmit(appContext.getString(R.string.cloud_upload_complete, uploaded))

            delay(2000)
            removeProgress(progressId)
        }
    }

    /**
     * Delete selected files from cloud storage.
     */
    fun cloudDeleteSelected() {
        val state = _uiState.value
        val activeId = state.activePanel
        val panel = getPanel(activeId)
        val selected = panel.files.filter { it.path in panel.selectedPaths }
        if (selected.isEmpty()) return

        clearSelection(activeId)
        val total = selected.size
        EcosystemLogger.d(HaronConstants.TAG, "cloudDeleteSelected: $total files")
        val progressId = nextProgressId()
        launchCloudJob {
            updateProgress(OperationProgress(0, total, "", OperationType.DELETE, id = progressId))
            delay(300) // let progress bar animation play

            var deleted = 0
            for ((idx, entry) in selected.withIndex()) {
                updateProgress(OperationProgress(
                    idx + 1, total, entry.name, OperationType.DELETE,
                    filePercent = if (total > 0) ((idx * 100) / total) else 0,
                    id = progressId
                ))
                val parsed = cloudManager.parseCloudUri(entry.path) ?: continue
                val (provider, cloudFileId) = parsed
                EcosystemLogger.d(HaronConstants.TAG, "cloudDeleteSelected: deleting ${entry.name} (${idx + 1}/$total)")
                cloudManager.delete(parsed.accountId, cloudFileId)
                    .onSuccess {
                        deleted++
                        refreshPanel(activeId)
                    }
                    .onFailure { e ->
                        EcosystemLogger.e(HaronConstants.TAG, "Cloud delete failed: ${entry.name}: ${e.message}")
                    }
            }

            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
            updateProgress(OperationProgress(
                deleted, total, "", OperationType.DELETE, isComplete = true,
                filePercent = 100, id = progressId
            ))
            _toastMessage.tryEmit(appContext.getString(R.string.cloud_deleted, deleted))

            delay(2000)
            removeProgress(progressId)
        }
    }

    /**
     * Create a folder in the current cloud directory.
     */
    fun cloudCreateFolder(name: String) {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.CloudCreateFolder) return
        val parsed = cloudManager.parseCloudUri(dialog.cloudPath) ?: return
        val (provider, parentPath) = parsed

        _uiState.update { it.copy(dialogState = DialogState.None) }
        viewModelScope.launch {
            cloudManager.createFolder(parsed.accountId, parentPath, name)
                .onSuccess {
                    refreshPanel(PanelId.TOP)
                    refreshPanel(PanelId.BOTTOM)
                }
                .onFailure { e ->
                    _toastMessage.tryEmit(e.message ?: "Error")
                }
        }
    }

    fun showCloudCreateFolder() {
        val state = _uiState.value
        val panel = getPanel(state.activePanel)
        if (isCloudPath(panel.currentPath)) {
            _uiState.update {
                it.copy(dialogState = DialogState.CloudCreateFolder(state.activePanel, panel.currentPath))
            }
        }
    }

    fun cancelCloudTransfer() {
        cloudTransferJobs.values.forEach { it.cancel() }
        cloudTransferJobs.clear()
        _uiState.update { it.copy(dialogState = DialogState.None, operationProgress = null, operationProgressList = emptyList()) }
    }

    fun cancelSingleCloudTransfer(transferId: String) {
        cloudTransferJobs[transferId]?.cancel()
        // Job removal + dialog cleanup happens in the finally block of launchCloudTransfer/launchCloudJob
    }

    /** Selection snapshot at the start of a drag gesture (for non-contiguous multi-range). */
    private var dragBaseSelection: Set<String> = emptySet()

    // inlineOperationJob removed — all file operations now go through FileOperationService

    /** Scroll position cache: path → firstVisibleItemIndex */
    private val scrollCache = mutableMapOf<String, Int>()

    /** Current scroll positions reported by panels */
    private val panelScrollIndex = mutableMapOf<PanelId, Int>()

    /** Monotonic trigger counter for scroll events */
    private var scrollTrigger = 0L

    /** Folder size calculation jobs */
    private val folderSizeJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    /** Content search debounce jobs per panel */
    private val contentSearchJobs = mutableMapOf<PanelId, kotlinx.coroutines.Job>()
    private val folderIndexJobs = mutableMapOf<PanelId, kotlinx.coroutines.Job>()

    /** Navigation jobs per panel — cancel previous before starting new */
    private val navigationJobs = mutableMapOf<PanelId, kotlinx.coroutines.Job>()

    /** Live folder size refresh job — runs during active file operations */
    private var liveSizeRefreshJob: kotlinx.coroutines.Job? = null

    fun onScrollPositionChanged(panelId: PanelId, index: Int) {
        panelScrollIndex[panelId] = index
    }

    fun getScrollIndex(panelId: PanelId): Int = panelScrollIndex[panelId] ?: 0

    init {
        val savedSort = preferences.getSortOrder()
        val showHidden = preferences.showHidden
        val panelRatio = preferences.panelRatio
        val gridColumns = preferences.gridColumns
        // Load shelf and filter out non-existing files
        val shelfItems = preferences.getShelfItems().filter { item ->
            if (item.path.startsWith("content://")) true
            else File(item.path).exists()
        }
        if (shelfItems.size != preferences.getShelfItems().size) {
            preferences.saveShelfItems(shelfItems)
        }

        _uiState.update {
            it.copy(
                topPanel = it.topPanel.copy(sortOrder = savedSort, showHidden = showHidden, gridColumns = gridColumns),
                bottomPanel = it.bottomPanel.copy(sortOrder = savedSort, showHidden = showHidden, gridColumns = gridColumns),
                panelRatio = panelRatio,
                favorites = preferences.getFavorites(),
                recentPaths = preferences.getRecentPaths(),
                shelfItems = shelfItems,
                themeMode = EcosystemPreferences.theme,
                originalFolders = preferences.getOriginalFolders(),
                bookmarks = preferences.getBookmarks(),
                tagDefinitions = preferences.getTagDefinitions(),
                fileTags = preferences.getFileTagMappings(),
                gestureMappings = preferences.getGestureMappings(),
                marqueeEnabled = preferences.marqueeEnabled
            )
        }
        val topPath = preferences.topPanelPath.let { p ->
            if (p.startsWith("content://") || File(p).isDirectory) p else HaronConstants.ROOT_PATH
        }
        val bottomPath = preferences.bottomPanelPath.let { p ->
            if (p.startsWith("content://") || File(p).isDirectory) p else HaronConstants.ROOT_PATH
        }
        navigateTo(PanelId.TOP, topPath)
        navigateTo(PanelId.BOTTOM, bottomPath)

        // Register PACKAGE_ADDED receiver for install confirmation toast (lives with ViewModel)
        registerPackageAddedReceiver()

        // Загрузить SAF roots
        refreshSafRoots()

        // Автоочистка просроченных записей корзины + обновить инфо корзины
        viewModelScope.launch {
            cleanExpiredTrashUseCase()
            updateTrashSizeInfo()
        }

        // Очистка облачного кэша thumbnails:
        // - Одноразовая инвалидация cloud_thumbs (миграция с =s220 на =s800)
        // - TTL 7 дней для cloud_gallery
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = appContext.getSharedPreferences("haron_cache", android.content.Context.MODE_PRIVATE)
            val thumbsCacheVersion = 2 // bump to invalidate
            if (prefs.getInt("cloud_thumbs_version", 0) < thumbsCacheVersion) {
                val dir = File(appContext.cacheDir, "cloud_thumbs")
                if (dir.exists()) dir.deleteRecursively()
                prefs.edit().putInt("cloud_thumbs_version", thumbsCacheVersion).apply()
                EcosystemLogger.d(HaronConstants.TAG, "Cloud thumbs cache invalidated (v$thumbsCacheVersion)")
            }
            val maxAge = 7L * 24 * 60 * 60 * 1000 // 7 days
            val now = System.currentTimeMillis()
            listOf("cloud_thumbs", "cloud_gallery").forEach { dirName ->
                val dir = File(appContext.cacheDir, dirName)
                if (dir.exists()) {
                    dir.listFiles()?.forEach { f ->
                        if (now - f.lastModified() > maxAge) f.delete()
                    }
                }
            }
        }

        // Подписка на OAuth callback из deep link
        viewModelScope.launch {
            CloudOAuthHelper.pendingAuth.collect { auth ->
                if (auth != null) {
                    CloudOAuthHelper.pendingAuth.value = null
                    val provider = com.vamp.haron.domain.model.CloudProvider.entries
                        .find { it.scheme == auth.providerScheme }
                    if (provider != null) {
                        EcosystemLogger.d(HaronConstants.TAG, "OAuth callback: provider=${provider.scheme}")
                        cloudSignIn(provider, auth.code)
                    }
                }
            }
        }

        // Подписка на прогресс файловых операций из foreground service
        viewModelScope.launch {
            FileOperationService.progress.collect { progress ->
                _uiState.update { it.copy(operationProgress = progress) }
                if (progress?.isComplete == true) {
                    if (progress.error == null) {
                        hapticManager.completion()
                    } else {
                        hapticManager.error()
                    }
                    delay(500)
                    refreshPanel(PanelId.TOP)
                    refreshPanel(PanelId.BOTTOM)
                    // Post-completion side effects by operation type
                    when (progress.type) {
                        OperationType.DELETE -> updateTrashSizeInfo()
                        else -> { /* no extra action */ }
                    }
                    delay(3000)
                    _uiState.update { it.copy(operationProgress = null) }
                }
            }
        }

        // Real-time folder size refresh during file operations
        viewModelScope.launch {
            _uiState
                .map { it.operationProgress?.isComplete != true && it.operationProgress != null }
                .distinctUntilChanged()
                .collect { isActive ->
                    if (isActive) startLiveFolderSizeRefresh() else stopLiveFolderSizeRefresh()
                }
        }

        // Network discovery — find Haron instances and SMB shares
        networkDeviceScanner.startDiscovery()
        viewModelScope.launch {
            networkDeviceScanner.devices.collect { devices ->
                val enriched = devices.map { d ->
                    d.copy(
                        alias = preferences.getDeviceAlias(d.name),
                        isTrusted = preferences.isDeviceTrusted(d.name)
                    )
                }
                _uiState.update { it.copy(networkDevices = enriched) }
            }
        }

        // Listener is started in MainActivity (lifecycle-level) to stay alive across all screens
        _uiState.update { it.copy(isListeningForTransfer = true) }

        // USB OTG — register receiver and subscribe to volume changes
        usbStorageManager.register()
        viewModelScope.launch {
            var previousPaths = emptySet<String>()
            usbStorageManager.usbVolumes.collect { volumes ->
                _uiState.update { it.copy(usbVolumes = volumes) }
                val currentPaths = volumes.map { it.path }.toSet()
                // Detect new USB connections
                val added = currentPaths - previousPaths
                for (path in added) {
                    val vol = volumes.firstOrNull { it.path == path }
                    if (vol != null) {
                        _toastMessage.tryEmit(
                            appContext.getString(R.string.usb_connected, vol.label)
                        )
                    }
                }
                // Detect USB disconnections
                val removed = previousPaths - currentPaths
                for (path in removed) {
                    // Navigate away from USB path if panel is on it
                    navigateAwayFromUsb(path)
                }
                previousPaths = currentPaths
            }
        }

        // Voice commands are handled globally by HaronNavigation's dispatcher.
        // Local actions are passed via TransferHolder.pendingVoiceAction → ExplorerScreen.

        // Quick Send — auto-refresh panels when files received
        viewModelScope.launch {
            receiveFileManager.quickReceiveCompleted.collect { dirPath ->
                // Refresh any panel showing the receive directory
                if (_uiState.value.topPanel.currentPath == dirPath) refreshPanel(PanelId.TOP)
                if (_uiState.value.bottomPanel.currentPath == dirPath) refreshPanel(PanelId.BOTTOM)
            }
        }

        // Quick Receive — progress tracking
        viewModelScope.launch {
            receiveFileManager.quickReceiveProgress.collect { progress ->
                _uiState.update {
                    it.copy(
                        quickReceiveProgress = progress,
                        quickReceiveDeviceName = if (progress != null) (it.quickReceiveDeviceName ?: "") else null
                    )
                }
            }
        }

    }

    override fun onCleared() {
        super.onCleared()
        usbStorageManager.unregister()
        networkDeviceScanner.stopDiscovery()
        unregisterPackageRemovedReceiver()
        _packageAddedReceiver?.let {
            try { appContext.unregisterReceiver(it) } catch (_: Exception) {}
        }
        // Don't call receiveFileManager.stopListening() — the global listener
        // lives in MainActivity and must survive ViewModel lifecycle
    }

    private companion object {
        const val MAX_HISTORY_SIZE = 50
    }

    // --- USB OTG ---

    private fun navigateAwayFromUsb(usbPath: String) {
        val topPath = _uiState.value.topPanel.currentPath
        val bottomPath = _uiState.value.bottomPanel.currentPath
        if (topPath.startsWith(usbPath)) {
            navigateTo(PanelId.TOP, HaronConstants.ROOT_PATH)
        }
        if (bottomPath.startsWith(usbPath)) {
            navigateTo(PanelId.BOTTOM, HaronConstants.ROOT_PATH)
        }
    }

    fun ejectUsb(usbPath: String) {
        val volume = usbStorageManager.getVolumeForPath(usbPath)
        val label = volume?.label ?: usbPath.substringAfterLast('/')
        // Navigate away first
        navigateAwayFromUsb(usbPath)
        viewModelScope.launch {
            val unmounted = withContext(Dispatchers.IO) {
                usbStorageManager.safeEject(usbPath)
            }
            if (unmounted) {
                _toastMessage.tryEmit(
                    appContext.getString(R.string.usb_ejected_safe, label)
                )
            } else {
                _toastMessage.tryEmit(
                    appContext.getString(R.string.usb_safe_to_remove, label)
                )
            }
        }
    }

    // --- Network ---

    fun onNetworkDeviceTap(device: com.vamp.haron.data.network.NetworkDevice) {
        when (device.type) {
            NetworkDeviceType.HARON -> {
                // Open transfer screen to send files to this Haron device
                _toastMessage.tryEmit(
                    appContext.getString(R.string.network_haron_device, device.address)
                )
                openTransfer()
            }
            NetworkDeviceType.SMB -> {
                _toastMessage.tryEmit(
                    appContext.getString(R.string.network_smb_info, device.address, device.port)
                )
            }
        }
    }

    fun refreshNetwork() {
        networkDeviceScanner.refreshDevices()
    }

    // --- Quick Send ---

    fun startQuickSend(filePath: String, fileName: String, offset: Offset, fromTopPanel: Boolean) {
        val haronDevices = _uiState.value.networkDevices.filter { it.type == NetworkDeviceType.HARON }
        if (haronDevices.isEmpty()) {
            _toastMessage.tryEmit(appContext.getString(R.string.quick_send_no_devices))
            return
        }
        _uiState.update {
            it.copy(
                quickSendState = QuickSendState.DraggingToDevice(
                    filePath = filePath,
                    fileName = fileName,
                    anchorOffset = offset,
                    dragOffset = offset,
                    haronDevices = haronDevices,
                    fromTopPanel = fromTopPanel
                )
            )
        }
    }

    fun updateQuickSendDrag(offset: Offset) {
        val current = _uiState.value.quickSendState
        if (current is QuickSendState.DraggingToDevice) {
            _uiState.update {
                it.copy(quickSendState = current.copy(dragOffset = offset))
            }
        }
    }

    fun endQuickSendAtPosition(finalOffset: Offset) {
        val current = _uiState.value.quickSendState
        if (current !is QuickSendState.DraggingToDevice) {
            cancelQuickSend()
            return
        }
        // Hit-test: vertical list of rows, mirrors QuickSendOverlay Column layout
        val devices = current.haronDevices
        val dm = appContext.resources.displayMetrics
        val density = dm.density
        val screenHeightPx = dm.heightPixels.toFloat()
        val rowHeightPx = 48f * density
        val rowSpacingPx = 4f * density
        val paddingPx = 8f * density // vertical padding
        val horizontalPaddingPx = 12f * density
        val statusBarPx = 40f * density // approximate status bar

        val showAtBottom = current.fromTopPanel
        val count = devices.size
        val totalContentHeight = count * rowHeightPx + (count - 1) * rowSpacingPx

        var matched: NetworkDevice? = null

        devices.forEachIndexed { index, device ->
            val rowTop: Float
            if (showAtBottom) {
                // Column aligned to bottom: items top-to-bottom within bottom-aligned Column
                val columnTop = screenHeightPx - paddingPx - totalContentHeight
                rowTop = columnTop + index * (rowHeightPx + rowSpacingPx)
            } else {
                // Column aligned to top with status bar inset
                rowTop = statusBarPx + paddingPx + index * (rowHeightPx + rowSpacingPx)
            }
            val rowBottom = rowTop + rowHeightPx

            if (finalOffset.y in rowTop..rowBottom &&
                finalOffset.x >= horizontalPaddingPx &&
                finalOffset.x <= dm.widthPixels - horizontalPaddingPx
            ) {
                matched = device
            }
        }

        if (matched != null) {
            performQuickSend(current.filePath, matched!!.displayName, matched!!.address, matched!!.port)
        } else {
            cancelQuickSend()
        }
    }

    fun cancelQuickSend() {
        _uiState.update {
            it.copy(
                topPanel = it.topPanel.copy(selectedPaths = emptySet(), isSelectionMode = false),
                bottomPanel = it.bottomPanel.copy(selectedPaths = emptySet(), isSelectionMode = false),
                quickSendState = QuickSendState.Idle
            )
        }
    }

    /** Find WiFi Network for socket binding (avoids mobile data routing) */
    private fun findWifiNetwork(): android.net.Network? {
        val cm = appContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager ?: return null
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                return network
            }
        }
        return null
    }

    /** Try discovered port first, then scan 8080-8090 on ECONNREFUSED */
    private fun connectWithPortScan(address: String, preferredPort: Int): java.net.Socket {
        val wifiNetwork = findWifiNetwork()
        val portsToTry = buildList {
            add(preferredPort)
            for (p in HaronConstants.TRANSFER_PORT_START..HaronConstants.TRANSFER_PORT_END) {
                if (p != preferredPort) add(p)
            }
        }
        var lastError: Exception? = null
        for (tryPort in portsToTry) {
            try {
                val sock = java.net.Socket()
                // Bind to WiFi to avoid routing through mobile data
                wifiNetwork?.bindSocket(sock)
                sock.connect(java.net.InetSocketAddress(address, tryPort), 3_000)
                if (tryPort != preferredPort) {
                    EcosystemLogger.d(HaronConstants.TAG, "Port scan: connected on $tryPort (NSD reported $preferredPort)")
                }
                return sock
            } catch (e: java.net.ConnectException) {
                lastError = e
            } catch (e: java.net.SocketTimeoutException) {
                lastError = e
            }
        }
        throw lastError ?: java.io.IOException("Cannot connect to $address")
    }

    private fun performQuickSend(filePath: String, deviceName: String, address: String, port: Int) {
        _uiState.update { it.copy(quickSendState = QuickSendState.Sending(deviceName)) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    withContext(Dispatchers.Main) {
                        _toastMessage.tryEmit(appContext.getString(R.string.quick_send_failed, "File not found"))
                        _uiState.update { it.copy(quickSendState = QuickSendState.Idle) }
                    }
                    return@launch
                }

                connectWithPortScan(address, port).use { sock ->
                    val output = java.io.DataOutputStream(sock.getOutputStream())
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(sock.getInputStream()))

                    // Send QUICK_SEND with sender name for trust verification
                    val androidId = android.provider.Settings.Secure.getString(appContext.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: ""
                    val senderName = "Haron-${android.os.Build.MODEL}-${androidId.takeLast(8)}"
                    val request = com.vamp.haron.data.transfer.TransferProtocolNegotiator.buildQuickSend(listOf(file), senderName)
                    output.writeUTF(request)
                    output.flush()

                    // Wait for ACCEPT
                    val response = reader.readLine() ?: throw java.io.IOException("No response from receiver")
                    val type = com.vamp.haron.data.transfer.TransferProtocolNegotiator.parseType(response)
                    if (type == com.vamp.haron.data.transfer.TransferProtocolNegotiator.TYPE_DECLINE) {
                        withContext(Dispatchers.Main) {
                            _toastMessage.tryEmit(appContext.getString(R.string.quick_send_declined))
                            _uiState.update {
                                it.copy(
                                    topPanel = it.topPanel.copy(selectedPaths = emptySet(), isSelectionMode = false),
                                    bottomPanel = it.bottomPanel.copy(selectedPaths = emptySet(), isSelectionMode = false),
                                    quickSendState = QuickSendState.Idle
                                )
                            }
                        }
                        return@launch
                    }

                    // Send file header + data
                    val totalBytes = file.length()
                    output.writeUTF(
                        com.vamp.haron.data.transfer.TransferProtocolNegotiator.buildFileHeader(
                            file.name, totalBytes, 0
                        )
                    )
                    output.flush()

                    var transferred = 0L
                    val startTime = System.currentTimeMillis()
                    var lastUpdateTime = 0L
                    file.inputStream().use { input ->
                        val buffer = ByteArray(HaronConstants.TRANSFER_BUFFER_SIZE)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            transferred += read
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateTime >= 100 || transferred == totalBytes) {
                                lastUpdateTime = now
                                val elapsed = now - startTime
                                val speed = if (elapsed > 0) (transferred * 1000 / elapsed) else 0
                                val eta = if (speed > 0) ((totalBytes - transferred) / speed) else 0
                                val progress = com.vamp.haron.domain.model.TransferProgressInfo(
                                    bytesTransferred = transferred,
                                    totalBytes = totalBytes,
                                    currentFileIndex = 0,
                                    totalFiles = 1,
                                    currentFileName = file.name,
                                    speedBytesPerSec = speed,
                                    etaSeconds = eta
                                )
                                _uiState.update {
                                    it.copy(quickSendState = QuickSendState.Sending(deviceName, progress))
                                }
                            }
                        }
                    }
                    output.flush()

                    // Send COMPLETE
                    output.writeUTF(com.vamp.haron.data.transfer.TransferProtocolNegotiator.buildComplete())
                    output.flush()
                }

                withContext(Dispatchers.Main) {
                    _toastMessage.tryEmit(appContext.getString(R.string.quick_send_done, deviceName))
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "Quick send error: ${e.message}")
                withContext(Dispatchers.Main) {
                    _toastMessage.tryEmit(appContext.getString(R.string.quick_send_failed, e.message ?: ""))
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            topPanel = it.topPanel.copy(selectedPaths = emptySet(), isSelectionMode = false),
                            bottomPanel = it.bottomPanel.copy(selectedPaths = emptySet(), isSelectionMode = false),
                            quickSendState = QuickSendState.Idle
                        )
                    }
                }
            }
        }
    }

    // --- Navigation ---

    fun navigateTo(panelId: PanelId, path: String, pushHistory: Boolean = true) {
        navigationJobs[panelId]?.cancel()
        navigationJobs[panelId] = viewModelScope.launch {
            // Save current scroll position before navigating away (only when actually changing folder)
            val currentPath = getPanel(panelId).currentPath
            val isNewFolder = currentPath.isNotEmpty() && currentPath != path
            if (isNewFolder) {
                scrollCache[currentPath] = panelScrollIndex[panelId] ?: 0
            }

            // Clear search and cursor when navigating to a different folder
            if (isNewFolder) {
                contentSearchJobs[panelId]?.cancel()
                folderIndexJobs[panelId]?.cancel()
                updatePanel(panelId) { it.copy(isLoading = true, error = null, searchQuery = "", isSearchActive = false, searchInContent = false, contentSearchSnippets = null, isContentIndexing = false, contentIndexProgress = null, focusedIndex = -1, shiftMode = false, shiftAnchor = -1, lockedPaths = emptySet()) }
            } else {
                updatePanel(panelId) { it.copy(isLoading = true, error = null, focusedIndex = -1, shiftMode = false, shiftAnchor = -1, lockedPaths = emptySet()) }
            }

            val panel = getPanel(panelId)
            val displayPath = when {
                path == HaronConstants.VIRTUAL_SECURE_PATH -> appContext.getString(R.string.all_secure_files)
                path.startsWith("cloud://") -> {
                    val parsed = cloudManager.parseCloudUri(path)
                    if (parsed != null) "${parsed.provider.displayName}: /${parsed.path}" else path
                }
                FtpPathUtils.isFtpPath(path) -> {
                    val host = FtpPathUtils.parseHost(path)
                    val port = FtpPathUtils.parsePort(path)
                    val rel = FtpPathUtils.parseRelativePath(path)
                    "FTP: $host:$port$rel"
                }
                path.startsWith("content://") -> buildSafDisplayPath(path)
                else -> path.removePrefix(HaronConstants.ROOT_PATH).ifEmpty { "/" }
            }

            // Cloud path — use CloudManager instead of filesystem
            if (path.startsWith("cloud://")) {
                val parsed = cloudManager.parseCloudUri(path)
                if (parsed == null) {
                    updatePanel(panelId) { it.copy(isLoading = false, error = "Invalid cloud path") }
                    return@launch
                }
                val (provider, cloudPath) = parsed
                cloudManager.listFiles(parsed.accountId, cloudPath).onSuccess { cloudFiles ->
                    val fileEntries = cloudFiles.map { it.toFileEntry() }
                    updatePanel(panelId) {
                        var history = it.navigationHistory
                        var index = it.historyIndex
                        if (pushHistory) {
                            history = history.take(index + 1) + path
                            if (history.size > MAX_HISTORY_SIZE) {
                                history = history.takeLast(MAX_HISTORY_SIZE)
                            }
                            index = history.lastIndex
                        }
                        // Build cloud breadcrumbs
                        val newBreadcrumbs = buildCloudBreadcrumbs(it.cloudBreadcrumbs, path, provider.displayName, cloudPath)
                        it.copy(
                            currentPath = path,
                            displayPath = displayPath,
                            files = fileEntries,
                            isLoading = false,
                            error = null,
                            navigationHistory = history,
                            historyIndex = index,
                            selectedPaths = emptySet(),
                            cloudBreadcrumbs = newBreadcrumbs
                        )
                    }
                }.onFailure { e ->
                    updatePanel(panelId) { it.copy(isLoading = false, error = e.message) }
                }
                return@launch
            }

            // FTP path — use FtpClientManager
            if (FtpPathUtils.isFtpPath(path)) {
                val host = FtpPathUtils.parseHost(path)
                val port = FtpPathUtils.parsePort(path)
                val relPath = FtpPathUtils.parseRelativePath(path)
                ftpClientManager.listFiles(host, port, relPath).onSuccess { ftpFiles ->
                    val fileEntries = ftpFiles.map { it.toFileEntry(host, port) }
                    updatePanel(panelId) {
                        var history = it.navigationHistory
                        var index = it.historyIndex
                        if (pushHistory) {
                            history = history.take(index + 1) + path
                            if (history.size > MAX_HISTORY_SIZE) {
                                history = history.takeLast(MAX_HISTORY_SIZE)
                            }
                            index = history.lastIndex
                        }
                        it.copy(
                            currentPath = path,
                            displayPath = displayPath,
                            files = fileEntries,
                            isLoading = false,
                            error = null,
                            navigationHistory = history,
                            historyIndex = index,
                            selectedPaths = emptySet()
                        )
                    }
                }.onFailure { e ->
                    updatePanel(panelId) { it.copy(isLoading = false, error = e.message) }
                }
                return@launch
            }

            val t0 = System.nanoTime()
            getFilesUseCase(
                path = path,
                sortOrder = panel.sortOrder,
                showHidden = panel.showHidden,
                showProtected = _uiState.value.isShieldUnlocked
            ).onSuccess { files ->
                val loadMs = (System.nanoTime() - t0) / 1_000_000
                if (loadMs > 50) {
                    EcosystemLogger.d("Perf", "navigateTo($path): ${loadMs}ms, ${files.size} files")
                }
                val isNewPath = currentPath != path
                val savedScroll = if (isNewPath) scrollCache[path] ?: 0 else -1
                if (isNewPath) scrollTrigger++
                updatePanel(panelId) {
                    var history = it.navigationHistory
                    var index = it.historyIndex
                    if (pushHistory && path != HaronConstants.VIRTUAL_SECURE_PATH) {
                        // Обрезаем forward-стек и добавляем новый путь
                        // Виртуальный путь защищённой папки НЕ добавляется в историю —
                        // чтобы кнопка "назад" не возвращала в режим защиты после выхода
                        history = history.take(index + 1) + path
                        if (history.size > MAX_HISTORY_SIZE) {
                            history = history.takeLast(MAX_HISTORY_SIZE)
                        }
                        index = history.lastIndex
                    }
                    val base = it.copy(
                        currentPath = path,
                        displayPath = displayPath,
                        files = files,
                        isLoading = false,
                        error = null,
                        navigationHistory = history,
                        historyIndex = index,
                        isSafPath = path.startsWith("content://"),
                        showProtected = path == HaronConstants.VIRTUAL_SECURE_PATH ||
                            secureFolderRepository.isFileProtected(path) ||
                            (!java.io.File(path).exists() && secureFolderRepository.hasProtectedDescendants(path)),
                        // Ensure search is cleared when entering a new folder
                        // (guards against BasicTextField race re-emitting old query)
                        searchQuery = if (isNewPath) "" else it.searchQuery,
                        isSearchActive = if (isNewPath) false else it.isSearchActive,
                        searchInContent = if (isNewPath) false else it.searchInContent,
                        contentSearchSnippets = if (isNewPath) null else it.contentSearchSnippets,
                        isContentIndexing = if (isNewPath) false else it.isContentIndexing,
                        contentIndexProgress = if (isNewPath) null else it.contentIndexProgress
                    )
                    if (savedScroll >= 0) {
                        base.copy(
                            scrollToIndex = savedScroll,
                            scrollToTrigger = scrollTrigger
                        )
                    } else base
                }
                // Save panel path & track recent (skip virtual paths)
                if (path != HaronConstants.VIRTUAL_SECURE_PATH && !secureFolderRepository.isFileProtected(path) &&
                    !((!java.io.File(path).exists()) && secureFolderRepository.hasProtectedDescendants(path))) {
                    when (panelId) {
                        PanelId.TOP -> preferences.topPanelPath = path
                        PanelId.BOTTOM -> preferences.bottomPanelPath = path
                    }
                    if (!path.startsWith("content://") && !FtpPathUtils.isFtpPath(path)) {
                        preferences.addRecentPath(path)
                        _uiState.update { it.copy(recentPaths = preferences.getRecentPaths()) }
                    }
                }
                EcosystemLogger.d(HaronConstants.TAG, "[$panelId] Открыта папка: $path (${files.size} файлов)")
                // Restricted path fallback chain: SAF → Shizuku → dialog
                if (files.isEmpty() && !path.startsWith("content://") && isRestrictedAndroidDir(path)) {
                    when (shizukuManager.state.value) {
                        com.vamp.haron.data.shizuku.ShizukuState.BOUND -> {
                            // Shizuku already tried in FileRepositoryImpl — folder is genuinely empty
                            EcosystemLogger.d(HaronConstants.TAG, "[$panelId] Restricted path, Shizuku bound but folder empty: $path")
                        }
                        com.vamp.haron.data.shizuku.ShizukuState.READY -> {
                            // Shizuku ready but not bound yet — bind and re-navigate
                            EcosystemLogger.d(HaronConstants.TAG, "[$panelId] Restricted path, binding Shizuku: $path")
                            shizukuManager.bindService()
                            viewModelScope.launch {
                                if (shizukuManager.ensureServiceBound()) {
                                    navigateTo(panelId, path, pushHistory = false)
                                }
                            }
                        }
                        com.vamp.haron.data.shizuku.ShizukuState.NO_PERMISSION -> {
                            EcosystemLogger.d(HaronConstants.TAG, "[$panelId] Restricted path, requesting Shizuku permission")
                            shizukuManager.requestPermission()
                        }
                        com.vamp.haron.data.shizuku.ShizukuState.NOT_RUNNING -> {
                            _uiState.update { it.copy(dialogState = DialogState.ShizukuNotRunning(panelId, path)) }
                        }
                        com.vamp.haron.data.shizuku.ShizukuState.NOT_INSTALLED -> {
                            // Try SAF as last resort (works on some devices with rolled-back DocumentsUI)
                            if (!hasRestrictedPathSafAccess(path)) {
                                val docId = filePathToDocId(path)
                                if (docId != null) {
                                    val initialUri = DocumentsContract.buildDocumentUri(
                                        "com.android.externalstorage.documents", docId
                                    )
                                    EcosystemLogger.d(HaronConstants.TAG, "[$panelId] Restricted path, trying SAF: $path")
                                    _navigationEvent.tryEmit(
                                        NavigationEvent.RequestSafAccess(panelId, path, initialUri)
                                    )
                                } else {
                                    _uiState.update { it.copy(dialogState = DialogState.ShizukuNotInstalled(panelId, path)) }
                                }
                            }
                        }
                    }
                }
                // Calculate current folder size for breadcrumb (skip restricted paths — walkTopDown causes GC storm)
                val skipSizeCalc = path.startsWith("content://") ||
                    path.startsWith("cloud://") ||
                    FtpPathUtils.isFtpPath(path) ||
                    path == HaronConstants.VIRTUAL_SECURE_PATH ||
                    isRestrictedAndroidDir(path)
                if (!skipSizeCalc &&
                    _uiState.value.folderSizeCache[path] == null && !folderSizeJobs.containsKey(path)) {
                    calculateFolderSize(path)
                }
                // Calculate sizes for subdirectories (for display in file list)
                if (!skipSizeCalc) {
                    files.filter { it.isDirectory }.forEach { dir ->
                        if (_uiState.value.folderSizeCache[dir.path] == null && !folderSizeJobs.containsKey(dir.path)) {
                            calculateFolderSize(dir.path)
                        }
                    }
                }
            }.onFailure { error ->
                updatePanel(panelId) {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: appContext.getString(R.string.unknown_error)
                    )
                }
                EcosystemLogger.e(HaronConstants.TAG, "[$panelId] Ошибка навигации: $path — ${error.message}")
            }
        }
    }

    /**
     * Navigate to a folder and scroll to a specific file by its full path.
     * Used when returning from global search to show the file in context.
     */
    fun navigateToFileLocation(panelId: PanelId, folderPath: String, filePath: String) {
        navigationJobs[panelId]?.cancel()
        navigationJobs[panelId] = viewModelScope.launch {
            val currentPath = getPanel(panelId).currentPath
            val isNewFolder = currentPath.isNotEmpty() && currentPath != folderPath
            if (isNewFolder) {
                scrollCache[currentPath] = panelScrollIndex[panelId] ?: 0
            }

            // Clear search and cursor when navigating to a different folder
            if (isNewFolder) {
                contentSearchJobs[panelId]?.cancel()
                folderIndexJobs[panelId]?.cancel()
                updatePanel(panelId) { it.copy(isLoading = true, error = null, searchQuery = "", isSearchActive = false, searchInContent = false, contentSearchSnippets = null, isContentIndexing = false, contentIndexProgress = null, focusedIndex = -1, shiftMode = false, shiftAnchor = -1, lockedPaths = emptySet()) }
            } else {
                updatePanel(panelId) { it.copy(isLoading = true, error = null, focusedIndex = -1, shiftMode = false, shiftAnchor = -1, lockedPaths = emptySet()) }
            }

            val panel = getPanel(panelId)
            val displayPath = when {
                folderPath == HaronConstants.VIRTUAL_SECURE_PATH -> appContext.getString(R.string.all_secure_files)
                folderPath.startsWith("content://") -> buildSafDisplayPath(folderPath)
                else -> folderPath.removePrefix(HaronConstants.ROOT_PATH).ifEmpty { "/" }
            }

            getFilesUseCase(
                path = folderPath,
                sortOrder = panel.sortOrder,
                showHidden = panel.showHidden,
                showProtected = _uiState.value.isShieldUnlocked
            ).onSuccess { files ->
                scrollTrigger++
                val fileIndex = files.indexOfFirst { it.path == filePath }.coerceAtLeast(0)
                updatePanel(panelId) {
                    var history = it.navigationHistory
                    var index = it.historyIndex
                    history = history.take(index + 1) + folderPath
                    if (history.size > MAX_HISTORY_SIZE) {
                        history = history.takeLast(MAX_HISTORY_SIZE)
                    }
                    index = history.lastIndex
                    it.copy(
                        currentPath = folderPath,
                        displayPath = displayPath,
                        files = files,
                        isLoading = false,
                        error = null,
                        navigationHistory = history,
                        historyIndex = index,
                        isSafPath = folderPath.startsWith("content://"),
                        scrollToIndex = fileIndex,
                        scrollToTrigger = scrollTrigger,
                        // Clear search when entering a new folder
                        searchQuery = if (isNewFolder) "" else it.searchQuery,
                        isSearchActive = if (isNewFolder) false else it.isSearchActive,
                        searchInContent = if (isNewFolder) false else it.searchInContent,
                        contentSearchSnippets = if (isNewFolder) null else it.contentSearchSnippets,
                        isContentIndexing = if (isNewFolder) false else it.isContentIndexing,
                        contentIndexProgress = if (isNewFolder) null else it.contentIndexProgress
                    )
                }
                when (panelId) {
                    PanelId.TOP -> preferences.topPanelPath = folderPath
                    PanelId.BOTTOM -> preferences.bottomPanelPath = folderPath
                }
            }.onFailure { error ->
                updatePanel(panelId) {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: appContext.getString(R.string.unknown_error)
                    )
                }
            }
        }
    }

    fun navigateUp(panelId: PanelId, pushHistory: Boolean = true): Boolean {
        val panel = getPanel(panelId)
        // Archive mode — navigate up inside archive or exit archive
        if (panel.isArchiveMode) {
            if (panel.archiveVirtualPath.isNotEmpty()) {
                // Go up one level inside the archive
                val parentVirtual = panel.archiveVirtualPath.trimEnd('/').substringBeforeLast('/', "")
                navigateIntoArchive(panelId, panel.archivePath!!, parentVirtual, panel.archivePassword)
            } else {
                // Exit archive — return to the folder containing the archive file
                val archiveParent = File(panel.archivePath!!).parent ?: HaronConstants.ROOT_PATH
                // Force navigateTo to treat this as a "new folder" so scroll is restored
                updatePanel(panelId) { it.copy(archivePath = null, archiveVirtualPath = "", archivePassword = null, archiveExtractProgress = null, currentPath = "") }
                navigateTo(panelId, archiveParent, pushHistory = pushHistory)
            }
            return true
        }
        val currentPath = panel.currentPath
        // Virtual secure path — exit virtual view, turn off shield
        if (currentPath == HaronConstants.VIRTUAL_SECURE_PATH) {
            _uiState.update { it.copy(isShieldUnlocked = false) }
            if (canNavigateBack(panelId)) {
                navigateBack(panelId)
            } else {
                navigateTo(panelId, HaronConstants.ROOT_PATH, pushHistory = false)
            }
            return true
        }
        // Protected directory (real or virtual subdirectory) — use history back
        if (secureFolderRepository.isFileProtected(currentPath) ||
            (!java.io.File(currentPath).exists() && secureFolderRepository.hasProtectedDescendants(currentPath))) {
            if (canNavigateBack(panelId)) {
                navigateBack(panelId)
                return true
            }
            return false
        }
        val parentPath = fileRepository.getParentPath(currentPath) ?: return false
        navigateTo(panelId, parentPath, pushHistory = pushHistory)
        return true
    }

    fun onFileClick(panelId: PanelId, entry: FileEntry) {
        setActivePanel(panelId)
        val panel = getPanel(panelId)
        if (panel.isSelectionMode) {
            toggleSelection(panelId, entry.path)
        } else if (panel.isArchiveMode && entry.isDirectory) {
            // Navigate deeper inside the archive
            val virtualPath = entry.path.substringAfter("!/", "")
            navigateIntoArchive(panelId, panel.archivePath!!, virtualPath, panel.archivePassword)
            return
        } else if (panel.isArchiveMode && !entry.isDirectory) {
            // APK/XAPK inside archive — extract to temp and launch installer
            val nameLc = entry.name.lowercase()
            if (nameLc.endsWith(".apk") || nameLc.endsWith(".xapk") || nameLc.endsWith(".apks")) {
                installApkFromArchive(panel.archivePath!!, entry, panel.archivePassword)
                return
            }
            // Inside archive: all file taps open QuickPreview (no external apps/players)
            onIconClick(panelId, entry)
            return
        } else if (entry.isProtected && !entry.isDirectory) {
            onProtectedFileClick(entry)
            return
        } else if (entry.isDirectory) {
            navigateTo(panelId, entry.path)
        } else if (isCloudPath(entry.path)) {
            val type = entry.iconRes()
            val nameLc = entry.name.lowercase()
            // Cloud images — open gallery (download to cache, then open with swipe)
            if (type == "image") {
                cloudOpenGallery(panelId, entry)
                return
            }
            // Cloud media — stream without full download
            if (type in listOf("video", "audio")) {
                cloudStreamAndPlay(entry)
                return
            }
            // Cloud text — download to cache and open in text editor with cloud save
            if (type in listOf("text", "code")) {
                cloudDownloadAndOpenText(entry)
                return
            }
            // Cloud archive — download to cache and navigate into
            if (type == "archive" && !(nameLc.endsWith(".fb2.zip") || (nameLc.endsWith(".zip") && nameLc.contains(".fb2")))) {
                cloudDownloadAndNavigateArchive(panelId, entry)
                return
            }
            // Cloud fb2/fb2.zip — download to cache and open in DocumentViewer
            if (nameLc.endsWith(".fb2") || nameLc.endsWith(".fb2.zip") || (nameLc.endsWith(".zip") && nameLc.contains(".fb2"))) {
                cloudDownloadAndOpenDocument(entry)
                return
            }
            // Cloud PDF — download to cache and open in PDF reader
            if (type == "pdf") {
                cloudDownloadAndOpenPdf(entry)
                return
            }
            // Other cloud files — download to cache first, then open
            cloudDownloadAndOpen(entry)
            return
        } else if (entry.name.lowercase().let { it.endsWith(".fb2.zip") || (it.endsWith(".zip") && it.contains(".fb2")) }) {
            preferences.lastDocumentFile = entry.path
            _navigationEvent.tryEmit(NavigationEvent.OpenDocumentViewer(entry.path, entry.name))
        } else {
            // Set highlight query if in content search mode
            SearchNavigationHolder.highlightQuery = if (panel.searchInContent && panel.searchQuery.isNotBlank()) panel.searchQuery else null
            val type = entry.iconRes()
            when (type) {
                "video", "audio" -> {
                    // Build playlist from all media files in folder
                    val mediaFiles = panel.files.filter { f ->
                        !f.isDirectory && f.iconRes() in listOf("video", "audio")
                    }
                    PlaylistHolder.items = mediaFiles.map { f ->
                        PlaylistHolder.PlaylistItem(
                            filePath = f.path,
                            fileName = f.name,
                            fileType = f.iconRes()
                        )
                    }
                    val startIndex = mediaFiles.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
                    PlaylistHolder.startIndex = startIndex
                    EcosystemLogger.d(HaronConstants.TAG, "onFileClick: tapped=${entry.name}, startIndex=$startIndex, playlistSize=${mediaFiles.size}, actualFile=${mediaFiles.getOrNull(startIndex)?.name}")
                    preferences.lastMediaFile = entry.path
                    _navigationEvent.tryEmit(
                        NavigationEvent.OpenMediaPlayer(startIndex)
                    )
                }
                "text", "code" -> {
                    _navigationEvent.tryEmit(
                        NavigationEvent.OpenTextEditor(entry.path, entry.name)
                    )
                }
                "image" -> {
                    val imageFiles = panel.files.filter { f ->
                        !f.isDirectory && f.iconRes() == "image"
                    }
                    GalleryHolder.items = imageFiles.map { f ->
                        GalleryHolder.GalleryItem(
                            filePath = f.path,
                            fileName = f.name,
                            fileSize = f.size
                        )
                    }
                    val startIndex = imageFiles.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
                    GalleryHolder.startIndex = startIndex
                    _navigationEvent.tryEmit(NavigationEvent.OpenGallery(startIndex))
                }
                "pdf" -> {
                    preferences.lastDocumentFile = entry.path
                    _navigationEvent.tryEmit(
                        NavigationEvent.OpenPdfReader(entry.path, entry.name)
                    )
                }
                "document", "spreadsheet" -> {
                    preferences.lastDocumentFile = entry.path
                    _navigationEvent.tryEmit(
                        NavigationEvent.OpenDocumentViewer(entry.path, entry.name)
                    )
                }
                "archive" -> {
                    navigateIntoArchive(panelId, entry.path, "", null)
                }
                "apk" -> {
                    val ext = entry.name.lowercase().substringAfterLast('.')
                    if (ext == "xapk" || ext == "apks") {
                        installXapkFile(File(entry.path))
                    } else {
                        showApkInstallDialog(entry)
                    }
                }
                else -> {
                    // No built-in handler — open with external app
                    openWithExternalApp(entry)
                }
            }
        }
    }

    /** Build playlist from preview dialog context (for fullscreen play from QuickPreview).
     *  Returns the startIndex in the filtered media list. */
    fun buildPlaylistFromPreview(entry: FileEntry, adjacentFiles: List<FileEntry>, currentIndex: Int): Int {
        val mediaFiles = adjacentFiles.filter { f ->
            !f.isDirectory && f.iconRes() in listOf("video", "audio")
        }
        PlaylistHolder.items = mediaFiles.map { f ->
            PlaylistHolder.PlaylistItem(
                filePath = f.path,
                fileName = f.name,
                fileType = f.iconRes()
            )
        }
        // Find the index of the current entry in filtered media list
        val idx = mediaFiles.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
        PlaylistHolder.startIndex = idx
        return idx
    }

    /** Build gallery from preview dialog context. Returns startIndex in filtered image list. */
    fun buildGalleryFromPreview(entry: FileEntry, adjacentFiles: List<FileEntry>, currentIndex: Int): Int {
        val imageFiles = adjacentFiles.filter { f ->
            !f.isDirectory && f.iconRes() == "image"
        }
        GalleryHolder.items = imageFiles.map { f ->
            GalleryHolder.GalleryItem(
                filePath = f.path,
                fileName = f.name,
                fileSize = f.size
            )
        }
        val idx = imageFiles.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
        GalleryHolder.startIndex = idx
        return idx
    }

    /**
     * Download a cloud thumbnail at high resolution for gallery viewing.
     * Returns local cached file path or null on failure.
     */
    private suspend fun downloadCloudThumbnail(entry: FileEntry, size: Int = 1600): String? {
        val rawThumbUrl = entry.thumbnailUrl ?: return null
        val isYandex = entry.path.startsWith("cloud://yandex/")
        val isGDrive = entry.path.startsWith("cloud://gdrive/")
        // Resize URL: Google Drive (=s150→=s1600), Yandex (size=XL→size=XXXL)
        val thumbUrl = when {
            isGDrive -> rawThumbUrl.replace(Regex("=s\\d+"), "=s$size")
            isYandex && "size=" in rawThumbUrl -> rawThumbUrl.replace(Regex("size=\\w+"), "size=XXXL")
            else -> rawThumbUrl
        }
        val needsAuth = thumbUrl.contains("googleapis.com") ||
            thumbUrl.contains("yandex.ru") || thumbUrl.contains("yandex.net")
        val cacheDir = File(appContext.cacheDir, "cloud_gallery")
        cacheDir.mkdirs()
        val cacheKey = entry.path.hashCode().toUInt().toString(16)
        val thumbFile = File(cacheDir, "gallery_${cacheKey}.jpg")
        if (thumbFile.exists() && thumbFile.length() > 0) return thumbFile.absolutePath
        return try {
            withContext(Dispatchers.IO) {
                val url = java.net.URL(thumbUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                if (needsAuth) {
                    val parsed = cloudManager.parseCloudUri(entry.path)
                    if (parsed != null) {
                        val token = cloudManager.getFreshAccessToken(parsed.accountId)
                        if (token != null) {
                            val authPrefix = if (isYandex) "OAuth" else "Bearer"
                            conn.setRequestProperty("Authorization", "$authPrefix $token")
                        }
                    }
                }
                try {
                    conn.inputStream.use { input ->
                        java.io.FileOutputStream(thumbFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            }
            if (thumbFile.exists() && thumbFile.length() > 0) thumbFile.absolutePath else null
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "downloadCloudThumbnail failed: ${e.message}")
            null
        }
    }

    /**
     * Open gallery from cloud panel: download high-res thumbnails for all images, then navigate.
     */
    fun openCloudGallery(entry: FileEntry, adjacentFiles: List<FileEntry>, currentIndex: Int) {
        viewModelScope.launch {
            val imageFiles = adjacentFiles.filter { f ->
                !f.isDirectory && f.iconRes() == "image"
            }
            if (imageFiles.isEmpty()) return@launch

            _uiState.update { it.copy(dialogState = DialogState.CloudTransfer(entry.name)) }

            // Download thumbnails in parallel
            val items = withContext(Dispatchers.IO) {
                coroutineScope {
                    imageFiles.map { f ->
                        async {
                            val localPath = downloadCloudThumbnail(f)
                            GalleryHolder.GalleryItem(
                                filePath = localPath ?: f.path,
                                fileName = f.name,
                                fileSize = f.size
                            )
                        }
                    }.awaitAll()
                }
            }

            GalleryHolder.items = items
            val startIdx = imageFiles.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
            GalleryHolder.startIndex = startIdx

            _uiState.update { it.copy(dialogState = DialogState.None) }
            _navigationEvent.tryEmit(NavigationEvent.OpenGallery(startIdx))
        }
    }

    fun onFileLongClick(panelId: PanelId, entry: FileEntry) {
        setActivePanel(panelId)
        val panel = getPanel(panelId)
        // Save current selection as base for potential drag
        dragBaseSelection = panel.selectedPaths
        updatePanel(panelId) {
            it.copy(
                isSelectionMode = true,
                selectedPaths = dragBaseSelection + entry.path
            )
        }
    }

    fun onIconClick(panelId: PanelId, entry: FileEntry) {
        setActivePanel(panelId)
        val panel = getPanel(panelId)
        when {
            panel.isSelectionMode -> toggleSelection(panelId, entry.path)
            panel.isArchiveMode && entry.isDirectory -> {
                val virtualPath = entry.path.substringAfter("!/", "")
                navigateIntoArchive(panelId, panel.archivePath!!, virtualPath, panel.archivePassword)
            }
            entry.isDirectory -> navigateTo(panelId, entry.path)
            entry.iconRes() == "apk" && !entry.isProtected && !entry.path.contains("!/") -> showApkInstallDialog(entry)
            else -> {
                val allFiles = panel.files.filter { !it.isDirectory }
                val fileIndex = allFiles.indexOfFirst { it.path == entry.path }
                val idx = if (fileIndex >= 0) fileIndex else 0

                _uiState.update {
                    it.copy(dialogState = DialogState.QuickPreview(
                        entry = entry,
                        adjacentFiles = allFiles,
                        currentFileIndex = idx
                    ))
                }
                viewModelScope.launch {
                    val previewEntry = resolvePreviewEntry(entry)
                    loadPreviewUseCase(previewEntry)
                        .onSuccess { rawData ->
                            val data = adaptCloudPreview(entry, rawData)
                            val current = _uiState.value.dialogState
                            if (current is DialogState.QuickPreview && current.entry.path == entry.path) {
                                _uiState.update {
                                    it.copy(dialogState = current.copy(
                                        previewData = data,
                                        isLoading = false,
                                        previewCache = current.previewCache + (idx to data),
                                        resolvedPath = previewEntry.path.takeIf { it != entry.path }
                                    ))
                                }
                                // Preload neighbors
                                preloadPreview(idx - 1, allFiles)
                                preloadPreview(idx + 1, allFiles)
                            }
                        }
                        .onFailure { e ->
                            val current = _uiState.value.dialogState
                            if (current is DialogState.QuickPreview && current.entry.path == entry.path) {
                                _uiState.update {
                                    it.copy(dialogState = current.copy(
                                        isLoading = false,
                                        error = e.message ?: appContext.getString(R.string.error_loading_preview)
                                    ))
                                }
                            }
                        }
                }
            }
        }
    }

    fun canNavigateUp(panelId: PanelId): Boolean {
        val panel = getPanel(panelId)
        if (panel.isArchiveMode) return true
        val path = panel.currentPath
        if (path == HaronConstants.VIRTUAL_SECURE_PATH) return false
        if (secureFolderRepository.isFileProtected(path) ||
            (!java.io.File(path).exists() && secureFolderRepository.hasProtectedDescendants(path))) return canNavigateBack(panelId)
        return fileRepository.getParentPath(path) != null
    }

    fun navigateBack(panelId: PanelId) {
        val panel = getPanel(panelId)
        if (panel.historyIndex <= 0) {
            // No history — if in archive mode, exit archive
            if (panel.isArchiveMode) {
                val archiveParent = File(panel.archivePath!!).parent ?: HaronConstants.ROOT_PATH
                updatePanel(panelId) { it.copy(archivePath = null, archiveVirtualPath = "", archivePassword = null, archiveExtractProgress = null) }
                navigateTo(panelId, archiveParent, pushHistory = false)
            }
            return
        }
        val newIndex = panel.historyIndex - 1
        val path = panel.navigationHistory[newIndex]
        updatePanel(panelId) { it.copy(historyIndex = newIndex) }
        if (path.contains("!/")) {
            val archivePath = path.substringBefore("!/")
            val virtualPath = path.substringAfter("!/", "")
            navigateIntoArchive(panelId, archivePath, virtualPath, panel.archivePassword, pushHistory = false)
        } else {
            // Exiting archive mode — clear archive state, reset currentPath to trigger scroll restore
            if (panel.isArchiveMode) {
                updatePanel(panelId) { it.copy(archivePath = null, archiveVirtualPath = "", archivePassword = null, archiveExtractProgress = null, currentPath = "") }
            }
            navigateTo(panelId, path, pushHistory = false)
        }
    }

    fun navigateForward(panelId: PanelId) {
        val panel = getPanel(panelId)
        if (panel.historyIndex >= panel.navigationHistory.lastIndex) return
        val newIndex = panel.historyIndex + 1
        val path = panel.navigationHistory[newIndex]
        updatePanel(panelId) { it.copy(historyIndex = newIndex) }
        if (path.contains("!/")) {
            val archivePath = path.substringBefore("!/")
            val virtualPath = path.substringAfter("!/", "")
            navigateIntoArchive(panelId, archivePath, virtualPath, panel.archivePassword, pushHistory = false)
        } else {
            if (panel.isArchiveMode) {
                updatePanel(panelId) { it.copy(archivePath = null, archiveVirtualPath = "", archivePassword = null, archiveExtractProgress = null) }
            }
            navigateTo(panelId, path, pushHistory = false)
        }
    }

    fun canNavigateBack(panelId: PanelId): Boolean {
        val panel = getPanel(panelId)
        // Virtual secure root — no back (exit via shield button only)
        if (panel.currentPath == HaronConstants.VIRTUAL_SECURE_PATH) return false
        return panel.historyIndex > 0
    }

    fun canNavigateForward(panelId: PanelId): Boolean {
        val panel = getPanel(panelId)
        return panel.historyIndex < panel.navigationHistory.lastIndex
    }

    fun openInOtherPanel(panelId: PanelId) {
        val path = getPanel(panelId).currentPath
        val otherId = if (panelId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        navigateTo(otherId, path)
    }

    fun toggleOriginalFolder(panelId: PanelId) {
        val path = getPanel(panelId).currentPath
        _uiState.update { st ->
            val newFolders = st.originalFolders.toMutableSet()
            if (path in newFolders) newFolders.remove(path) else newFolders.add(path)
            st.copy(originalFolders = newFolders)
        }
        preferences.saveOriginalFolders(_uiState.value.originalFolders)
        val isNow = path in _uiState.value.originalFolders
        _toastMessage.tryEmit(if (isNow) appContext.getString(R.string.folder_marked_original) else appContext.getString(R.string.marker_removed))
    }

    fun cycleTheme() {
        val current = EcosystemPreferences.theme
        val next = when (current) {
            "system" -> "light"
            "light" -> "dark"
            else -> "system"
        }
        setTheme(next)
    }

    fun setTheme(theme: String) {
        EcosystemPreferences.theme = theme
        _uiState.update { it.copy(themeMode = theme) }
    }

    // --- SAF ---

    fun onSafUriGranted(uri: Uri) {
        safUriManager.persistUri(uri)
        refreshSafRoots()
        navigateTo(_uiState.value.activePanel, uri.toString())
    }

    /** Called after SAF picker grants access to Android/data or Android/obb */
    fun onSafAccessGrantedForRestrictedPath(uri: Uri, panelId: PanelId, filePath: String) {
        safUriManager.persistUri(uri)
        EcosystemLogger.d(HaronConstants.TAG, "SAF access granted for restricted path: $filePath, uri=$uri")
        // Re-navigate to the same file path — FileRepositoryImpl will now find the SAF URI and use it
        navigateTo(panelId, filePath, pushHistory = false)
    }

    // --- Shizuku ---

    fun dismissShizukuDialog() {
        _uiState.update { it.copy(dialogState = DialogState.None) }
    }

    fun onShizukuReady(panelId: PanelId, path: String) {
        _uiState.update { it.copy(dialogState = DialogState.None) }
        viewModelScope.launch {
            shizukuManager.refreshState()
            if (shizukuManager.ensureServiceBound()) {
                navigateTo(panelId, path, pushHistory = false)
            }
        }
    }

    private fun isRestrictedAndroidDir(path: String): Boolean =
        path.contains("/Android/data") || path.contains("/Android/obb") || path.contains("/Android/media")

    private fun hasRestrictedPathSafAccess(path: String): Boolean {
        val docId = filePathToDocId(path) ?: return false
        return safUriManager.getPersistedUris().any { uri ->
            try {
                val treeDocId = DocumentsContract.getTreeDocumentId(uri)
                docId == treeDocId || docId.startsWith("$treeDocId/")
            } catch (_: Exception) { false }
        }
    }

    private fun filePathToDocId(path: String): String? {
        val internalPrefix = "/storage/emulated/0/"
        if (path.startsWith(internalPrefix)) {
            return "primary:" + path.removePrefix(internalPrefix)
        }
        val match = Regex("^/storage/([^/]+)/(.+)$").matchEntire(path) ?: return null
        return "${match.groupValues[1]}:${match.groupValues[2]}"
    }

    fun hasRemovableStorage(): Boolean {
        return storageVolumeHelper.getRemovableVolumes().isNotEmpty() ||
            safUriManager.getPersistedUris().isNotEmpty()
    }

    fun getSdCardLabel(): String {
        val volumes = storageVolumeHelper.getRemovableVolumes()
        return volumes.firstOrNull()?.label ?: appContext.getString(R.string.sd_card)
    }

    fun navigateToSdCard() {
        val persisted = safUriManager.getPersistedUris()
        if (persisted.isNotEmpty()) {
            navigateTo(_uiState.value.activePanel, persisted.first().toString())
        }
        // If no persisted URI — UI should launch SAF picker
    }

    fun hasSafPermission(): Boolean {
        return safUriManager.getPersistedUris().isNotEmpty()
    }

    /**
     * Build list of storage items for DrawerMenu:
     * - Each removable volume from StorageVolumeHelper
     * - Matched against persisted SAF URIs to know if access is granted
     * Pair: (label, safUri?) — null uri means no access yet
     */
    fun refreshSafRoots() {
        val removable = storageVolumeHelper.getRemovableVolumes()
        val persisted = safUriManager.getPersistedUris()

        // Only show SD cards in SAF roots section — USB drives are shown separately
        // "SD" must be a standalone word: "SD-карта", "SD card" — but NOT "SanDisk"
        val sdPattern = Regex("(^|\\s)SD(\\s|[^a-zA-Z]|$)", RegexOption.IGNORE_CASE)
        val sdCards = removable.filter { vol -> sdPattern.containsMatchIn(vol.label) }
        val roots = sdCards.map { vol ->
            val matchingUri = persisted.find { uri ->
                try {
                    val treeDocId = android.provider.DocumentsContract.getTreeDocumentId(uri)
                    val volumeId = treeDocId.split(":").firstOrNull()
                    volumeId != null && volumeId != "primary" &&
                        (vol.uuid != null && volumeId.equals(vol.uuid, ignoreCase = true))
                } catch (_: Exception) { false }
            }
            val dir = vol.path?.let { java.io.File(it) }
            SafRootInfo(
                label = vol.label,
                safUri = matchingUri?.toString() ?: "",
                path = vol.path,
                totalSpace = dir?.totalSpace ?: 0L,
                freeSpace = dir?.freeSpace ?: 0L
            )
        }
        _uiState.update { it.copy(safRoots = roots) }
    }

    fun removeSafRoot(uri: String) {
        safUriManager.releaseUri(Uri.parse(uri))
        refreshSafRoots()
    }

    // --- Active panel ---

    fun setActivePanel(panelId: PanelId) {
        _uiState.update { it.copy(activePanel = panelId) }
    }

    // --- Panel ratio ---

    fun updatePanelRatio(ratio: Float) {
        val clamped = ratio.coerceIn(0.2f, 0.8f)
        _uiState.update { it.copy(panelRatio = clamped) }
    }

    fun savePanelRatio() {
        preferences.panelRatio = _uiState.value.panelRatio
    }

    fun resetPanelRatio() {
        _uiState.update { it.copy(panelRatio = 0.5f) }
        preferences.panelRatio = 0.5f
    }

    // --- Grid columns ---

    fun setGridColumns(columns: Int) {
        val clamped = columns.coerceIn(1, 6)
        _uiState.update { state ->
            state.copy(
                topPanel = state.topPanel.copy(gridColumns = clamped),
                bottomPanel = state.bottomPanel.copy(gridColumns = clamped)
            )
        }
        preferences.gridColumns = clamped
    }

    // --- Sort & hidden ---

    fun setSortOrder(panelId: PanelId, order: SortOrder) {
        preferences.saveSortOrder(order)
        updatePanel(panelId) { it.copy(sortOrder = order) }
        refreshPanel(panelId)
    }

    fun toggleShowHidden(panelId: PanelId) {
        val panel = getPanel(panelId)
        val newValue = !panel.showHidden
        preferences.showHidden = newValue
        // Update both panels (global setting) and refresh both
        _uiState.update {
            it.copy(
                topPanel = it.topPanel.copy(showHidden = newValue),
                bottomPanel = it.bottomPanel.copy(showHidden = newValue)
            )
        }
        refreshPanel(PanelId.TOP)
        refreshPanel(PanelId.BOTTOM)
        _toastMessage.tryEmit(
            appContext.getString(if (newValue) R.string.hidden_files_shown else R.string.hidden_files_hidden)
        )
    }

    // --- Search ---

    fun setSearchQuery(panelId: PanelId, query: String) {
        updatePanel(panelId) { it.copy(searchQuery = query) }
        val panel = getPanel(panelId)
        if (panel.searchInContent) {
            performContentSearch(panelId, query)
        }
    }

    fun openSearch(panelId: PanelId) {
        updatePanel(panelId) { it.copy(isSearchActive = true) }
    }

    fun closeSearch(panelId: PanelId) {
        contentSearchJobs[panelId]?.cancel()
        folderIndexJobs[panelId]?.cancel()
        updatePanel(panelId) { it.copy(isSearchActive = false, searchQuery = "", searchInContent = false, contentSearchSnippets = null, isContentIndexing = false, contentIndexProgress = null) }
        // Reload files to guarantee they are visible after search close
        refreshPanel(panelId)
    }

    fun clearSearch(panelId: PanelId) {
        folderIndexJobs[panelId]?.cancel()
        updatePanel(panelId) { it.copy(searchQuery = "", isSearchActive = false, searchInContent = false, contentSearchSnippets = null, isContentIndexing = false, contentIndexProgress = null) }
    }

    fun toggleSearchInContent(panelId: PanelId) {
        val panel = getPanel(panelId)
        val newInContent = !panel.searchInContent
        updatePanel(panelId) { it.copy(searchInContent = newInContent, contentSearchSnippets = null) }
        if (newInContent) {
            // Auto-index current folder content when enabling content search
            indexFolderAndSearch(panelId, panel.currentPath, panel.searchQuery)
        } else {
            folderIndexJobs[panelId]?.cancel()
            updatePanel(panelId) { it.copy(isContentIndexing = false, contentIndexProgress = null) }
        }
    }

    /** Active indexing jobs by folder path — shared between panels */
    private val activeFolderIndexJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    private fun indexFolderAndSearch(panelId: PanelId, folderPath: String, query: String) {
        folderIndexJobs[panelId]?.cancel()
        folderIndexJobs[panelId] = viewModelScope.launch {
            if (folderPath !in preferences.getContentIndexedFolders()) {
                // Reuse existing indexing job if another panel is already indexing this folder
                val existingJob = activeFolderIndexJobs[folderPath]
                if (existingJob != null && existingJob.isActive) {
                    updatePanel(panelId) { it.copy(isContentIndexing = true, contentIndexProgress = null) }
                    try {
                        existingJob.join()
                    } finally {
                        updatePanel(panelId) { it.copy(isContentIndexing = false, contentIndexProgress = null) }
                    }
                } else {
                    updatePanel(panelId) { it.copy(isContentIndexing = true, contentIndexProgress = null) }
                    try {
                        val indexJob = viewModelScope.launch {
                            searchRepository.indexFolderContent(folderPath, force = false) { processed, total ->
                                val progress = "$processed / $total"
                                for (pid in PanelId.entries) {
                                    val p = getPanel(pid)
                                    if (p.currentPath == folderPath && p.searchInContent) {
                                        updatePanel(pid) { it.copy(contentIndexProgress = progress) }
                                    }
                                }
                            }
                        }
                        activeFolderIndexJobs[folderPath] = indexJob
                        indexJob.join()
                        activeFolderIndexJobs.remove(folderPath)
                        preferences.addContentIndexedFolder(folderPath)
                    } finally {
                        updatePanel(panelId) { it.copy(isContentIndexing = false, contentIndexProgress = null) }
                        val otherPanelId = if (panelId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
                        val otherPanel = getPanel(otherPanelId)
                        if (otherPanel.currentPath == folderPath && otherPanel.isContentIndexing) {
                            updatePanel(otherPanelId) { it.copy(isContentIndexing = false, contentIndexProgress = null) }
                        }
                    }
                }
            }
            // After indexing (or skip), perform content search if query is present
            val currentPanel = getPanel(panelId)
            if (currentPanel.searchInContent && currentPanel.searchQuery.isNotBlank()) {
                val paths = searchRepository.searchContentInFolder(currentPanel.currentPath, currentPanel.searchQuery)
                updatePanel(panelId) { it.copy(contentSearchSnippets = paths) }
            }
        }
    }

    private fun performContentSearch(panelId: PanelId, query: String) {
        contentSearchJobs[panelId]?.cancel()
        if (query.isBlank()) {
            updatePanel(panelId) { it.copy(contentSearchSnippets = null) }
            return
        }
        contentSearchJobs[panelId] = viewModelScope.launch {
            delay(300) // debounce
            val panel = getPanel(panelId)
            val paths = searchRepository.searchContentInFolder(panel.currentPath, query)
            updatePanel(panelId) { it.copy(contentSearchSnippets = paths) }
        }
    }

    // --- Selection ---

    fun toggleSelection(panelId: PanelId, path: String) {
        updatePanel(panelId) { panel ->
            val newSet = panel.selectedPaths.toMutableSet()
            if (path in newSet) newSet.remove(path) else newSet.add(path)
            val stillSelecting = newSet.isNotEmpty()
            panel.copy(
                selectedPaths = newSet,
                isSelectionMode = stillSelecting
            )
        }
    }

    fun selectAll(panelId: PanelId) {
        updatePanel(panelId) { panel ->
            val allPaths = panel.files.map { it.path }.toSet()
            if (panel.selectedPaths == allPaths) {
                // Toggle: deselect all but stay in selection mode
                panel.copy(selectedPaths = emptySet(), isSelectionMode = true)
            } else {
                panel.copy(selectedPaths = allPaths, isSelectionMode = true)
            }
        }
    }

    fun selectByExtension(panelId: PanelId, extension: String) {
        updatePanel(panelId) { panel ->
            val matching = panel.files
                .filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() == extension }
                .map { it.path }
                .toSet()
            panel.copy(selectedPaths = matching, isSelectionMode = true)
        }
    }

    fun clearSelection(panelId: PanelId) {
        updatePanel(panelId) { it.copy(selectedPaths = emptySet(), isSelectionMode = false) }
    }

    fun selectRange(panelId: PanelId, fromIndex: Int, toIndex: Int) {
        updatePanel(panelId) { panel ->
            val files = if (panel.searchQuery.isBlank()) {
                panel.files
            } else {
                panel.files.filter { it.name.contains(panel.searchQuery, ignoreCase = true) }
            }
            val minIdx = minOf(fromIndex, toIndex).coerceAtLeast(0)
            val maxIdx = maxOf(fromIndex, toIndex).coerceAtMost(files.lastIndex)
            val rangePaths = files.subList(minIdx, maxIdx + 1).map { it.path }.toSet()
            // Merge with base selection for non-contiguous multi-range
            panel.copy(
                selectedPaths = dragBaseSelection + rangePaths,
                isSelectionMode = true
            )
        }
    }

    // --- File operations ---

    fun copySelectedToOtherPanel() {
        val state = _uiState.value
        val activeId = state.activePanel
        val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        val sourcePanel = getPanel(activeId)
        val targetPanel = getPanel(targetId)
        val paths = sourcePanel.selectedPaths.toList()
        EcosystemLogger.d(HaronConstants.TAG, "copySelectedToOtherPanel: activePanel=$activeId, selectedPaths=${paths.size}, target=${targetPanel.currentPath}")
        if (paths.isEmpty()) {
            EcosystemLogger.d(HaronConstants.TAG, "copySelectedToOtherPanel: EMPTY selection, returning")
            return
        }

        // Cloud → local: download
        if (paths.any { isCloudPath(it) }) {
            cloudDownloadToLocal()
            return
        }
        // Local → cloud: upload
        if (isCloudPath(targetPanel.currentPath)) {
            cloudUploadFromLocal()
            return
        }

        // Block copy TO virtual secure view (only when target panel is in protected context)
        if (targetPanel.currentPath == HaronConstants.VIRTUAL_SECURE_PATH ||
            (targetPanel.showProtected && secureFolderRepository.isFileProtected(targetPanel.currentPath))) {
            _toastMessage.tryEmit(appContext.getString(R.string.cannot_copy_to_virtual))
            return
        }

        // Protected source files — check via FileEntry.isProtected, not path index lookup
        val selectedEntries = sourcePanel.files.filter { it.path in sourcePanel.selectedPaths }
        val hasProtected = selectedEntries.any { it.isProtected }
        if (hasProtected) {
            copyProtectedFiles(paths, targetPanel.currentPath)
            return
        }

        val conflictPairs = buildConflictPairs(paths, targetPanel)
        if (conflictPairs.isNotEmpty()) {
            _uiState.update {
                it.copy(dialogState = DialogState.ConfirmConflict(
                    conflictPairs = conflictPairs,
                    allPaths = paths,
                    destinationDir = targetPanel.currentPath,
                    operationType = OperationType.COPY
                ))
            }
            return
        }

        EcosystemLogger.d(HaronConstants.TAG, "copySelectedToOtherPanel: no conflicts, executing copy to ${targetPanel.currentPath}")
        executeCopy(paths, targetPanel.currentPath, ConflictResolution.RENAME)
    }

    private fun executeCopy(
        paths: List<String>,
        destinationDir: String,
        resolution: ConflictResolution
    ) {
        val state = _uiState.value
        val activeId = state.activePanel
        val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        val sourcePanel = getPanel(activeId)

        EcosystemLogger.d(HaronConstants.TAG, "executeCopy: paths=${paths.size}, dest=$destinationDir, resolution=$resolution")
        for (p in paths) EcosystemLogger.d(HaronConstants.TAG, "executeCopy: src=$p")
        clearSelection(activeId)
        FileOperationService.start(appContext, paths, destinationDir, isMove = false, conflictResolution = resolution)
    }

    private fun executeCopyWithDecisions(
        paths: List<String>,
        destinationDir: String,
        decisions: Map<String, ConflictResolution>
    ) {
        val state = _uiState.value
        val activeId = state.activePanel
        val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        val sourcePanel = getPanel(activeId)

        val selected = sourcePanel.files.filter { it.path in paths.toSet() }
        val dirs = selected.count { it.isDirectory }
        val files = selected.size - dirs
        clearSelection(activeId)

        viewModelScope.launch {
            val total = paths.size
            var completed = 0
            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, "", OperationType.COPY))
            }
            for ((index, path) in paths.withIndex()) {
                val fileName = extractFileName(path)
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(index, total, fileName, OperationType.COPY))
                }
                val resolution = decisions[path] ?: ConflictResolution.RENAME
                fileRepository.copyFilesWithResolutions(
                    listOf(path), destinationDir, mapOf(path to resolution)
                ).onSuccess { c ->
                    completed += c
                    if (c > 0) {
                        val size = selected.firstOrNull { it.path == path }?.size ?: 0L
                        if (size > 0) adjustFolderSizeCache(destinationDir, +size)
                    }
                }
            }
            _uiState.update {
                it.copy(operationProgress = OperationProgress(completed, total, "", OperationType.COPY, isComplete = true))
            }
            val skipped = total - completed
            val msg = when {
                completed == 0 -> appContext.getString(R.string.skipped_count, total)
                skipped > 0 -> appContext.getString(R.string.copied_with_skip, completed, skipped)
                else -> appContext.getString(R.string.copied_format, formatFileCount(dirs, files))
            }
            showStatusMessage(targetId, msg)
            if (completed > 0) {
                hapticManager.success()
                copyTagsToDestination(paths, destinationDir)
            } else hapticManager.error()
            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
            viewModelScope.launch {
                delay(2000)
                _uiState.update {
                    if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it
                }
            }
        }
    }

    fun moveSelectedToOtherPanel() {
        val state = _uiState.value
        val activeId = state.activePanel
        val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        val sourcePanel = getPanel(activeId)
        val targetPanel = getPanel(targetId)
        val paths = sourcePanel.selectedPaths.toList()
        EcosystemLogger.d(HaronConstants.TAG, "moveSelectedToOtherPanel: activePanel=$activeId, selectedPaths=${paths.size}, target=${targetPanel.currentPath}")
        if (paths.isEmpty()) {
            EcosystemLogger.d(HaronConstants.TAG, "moveSelectedToOtherPanel: EMPTY selection, returning")
            return
        }

        // Cloud: move not supported (use copy + manual delete)
        if (paths.any { isCloudPath(it) } || isCloudPath(targetPanel.currentPath)) {
            _toastMessage.tryEmit(appContext.getString(R.string.cloud_download_local_only))
            return
        }

        // Block move TO virtual secure view (only when target panel is in protected context)
        if (targetPanel.currentPath == HaronConstants.VIRTUAL_SECURE_PATH ||
            (targetPanel.showProtected && secureFolderRepository.isFileProtected(targetPanel.currentPath))) {
            _toastMessage.tryEmit(appContext.getString(R.string.cannot_move_to_virtual))
            return
        }

        // Protected source files — check via FileEntry.isProtected, not path index lookup
        val selectedEntries = sourcePanel.files.filter { it.path in sourcePanel.selectedPaths }
        val hasProtected = selectedEntries.any { it.isProtected }
        if (hasProtected) {
            moveProtectedFiles(paths, targetPanel.currentPath)
            return
        }

        val conflictPairs = buildConflictPairs(paths, targetPanel)
        if (conflictPairs.isNotEmpty()) {
            _uiState.update {
                it.copy(dialogState = DialogState.ConfirmConflict(
                    conflictPairs = conflictPairs,
                    allPaths = paths,
                    destinationDir = targetPanel.currentPath,
                    operationType = OperationType.MOVE
                ))
            }
            return
        }

        EcosystemLogger.d(HaronConstants.TAG, "moveSelectedToOtherPanel: no conflicts, executing move to ${targetPanel.currentPath}")
        executeMove(paths, targetPanel.currentPath, ConflictResolution.RENAME)
    }

    private fun executeMove(
        paths: List<String>,
        destinationDir: String,
        resolution: ConflictResolution
    ) {
        val state = _uiState.value
        val activeId = state.activePanel
        val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        val sourcePanel = getPanel(activeId)

        EcosystemLogger.d(HaronConstants.TAG, "executeMove: paths=${paths.size}, dest=$destinationDir, resolution=$resolution")
        for (p in paths) EcosystemLogger.d(HaronConstants.TAG, "executeMove: src=$p")
        clearSelection(activeId)
        FileOperationService.start(appContext, paths, destinationDir, isMove = true, conflictResolution = resolution)
    }

    private fun executeMoveWithDecisions(
        paths: List<String>,
        destinationDir: String,
        decisions: Map<String, ConflictResolution>
    ) {
        val state = _uiState.value
        val activeId = state.activePanel
        val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        val sourcePanel = getPanel(activeId)

        val selected = sourcePanel.files.filter { it.path in paths.toSet() }
        val dirs = selected.count { it.isDirectory }
        val files = selected.size - dirs
        val sourceDir = sourcePanel.currentPath
        clearSelection(activeId)

        viewModelScope.launch {
            val total = paths.size
            var completed = 0
            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, "", OperationType.MOVE))
            }
            for ((index, path) in paths.withIndex()) {
                val fileName = extractFileName(path)
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(index, total, fileName, OperationType.MOVE))
                }
                val resolution = decisions[path] ?: ConflictResolution.RENAME
                fileRepository.moveFilesWithResolutions(
                    listOf(path), destinationDir, mapOf(path to resolution)
                ).onSuccess { c ->
                    completed += c
                    if (c > 0) {
                        val size = selected.firstOrNull { it.path == path }?.size ?: 0L
                        if (size > 0) {
                            adjustFolderSizeCache(destinationDir, +size)
                            adjustFolderSizeCache(sourceDir, -size)
                        }
                    }
                }
            }
            _uiState.update {
                it.copy(operationProgress = OperationProgress(completed, total, "", OperationType.MOVE, isComplete = true))
            }
            val skipped = total - completed
            val msg = when {
                completed == 0 -> appContext.getString(R.string.skipped_count, total)
                skipped > 0 -> appContext.getString(R.string.moved_with_skip, completed, skipped)
                else -> appContext.getString(R.string.moved_format, formatFileCount(dirs, files))
            }
            showStatusMessage(targetId, msg)
            if (completed > 0) {
                hapticManager.success()
                migrateTagsToDestination(paths, destinationDir)
            } else hapticManager.error()
            refreshPanel(activeId)
            refreshPanel(targetId)
            viewModelScope.launch {
                delay(2000)
                _uiState.update {
                    if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it
                }
            }
        }
    }

    fun requestDeleteSelected() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val paths = panel.selectedPaths.toList()
        if (paths.isEmpty()) return
        hapticManager.warning()
        _uiState.update { it.copy(dialogState = DialogState.ConfirmDelete(paths)) }
    }

    fun confirmDelete(paths: List<String>) {
        dismissDialog()
        val activeId = _uiState.value.activePanel
        clearSelection(activeId)

        // Cloud files — delete from cloud storage with progress bar
        if (paths.any { isCloudPath(it) }) {
            val cloudPaths = paths.filter { isCloudPath(it) }
            val panel = getPanel(activeId)
            val selected = panel.files.filter { it.path in cloudPaths }
            val total = selected.size
            EcosystemLogger.d(HaronConstants.TAG, "confirmDelete: cloud delete $total files")
            launchCloudJob {
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(0, total, "", OperationType.DELETE))
                }
                delay(300)

                var deleted = 0
                for ((idx, entry) in selected.withIndex()) {
                    _uiState.update {
                        it.copy(operationProgress = OperationProgress(
                            idx + 1, total, entry.name, OperationType.DELETE,
                            filePercent = if (total > 0) ((idx * 100) / total) else 0
                        ))
                    }
                    val parsed = cloudManager.parseCloudUri(entry.path) ?: continue
                    EcosystemLogger.d(HaronConstants.TAG, "confirmDelete: deleting ${entry.name} (${idx + 1}/$total)")
                    cloudManager.delete(parsed.accountId, parsed.path)
                        .onSuccess {
                            deleted++
                            refreshPanel(activeId)
                        }
                        .onFailure { e ->
                            EcosystemLogger.e(HaronConstants.TAG, "Cloud delete failed: ${entry.name}: ${e.message}")
                        }
                }

                _uiState.update {
                    it.copy(operationProgress = OperationProgress(
                        deleted, total, "", OperationType.DELETE, isComplete = true, filePercent = 100
                    ))
                }
                _toastMessage.tryEmit(appContext.getString(R.string.cloud_deleted, deleted))
                delay(2000)
                _uiState.update {
                    if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it
                }
            }
            return
        }

        // Protected files — delete permanently from secure storage (only for entries marked isProtected)
        val activePanel = getPanel(activeId)
        val protectedPaths = paths.filter { p -> activePanel.files.any { it.path == p && it.isProtected } }
        if (protectedPaths.isNotEmpty()) {
            deleteProtectedPermanently(protectedPaths)
            return
        }

        // Pre-check trash overflow (shows dialog if needed)
        viewModelScope.launch {
            val nonSafPaths = paths.filter { !it.startsWith("content://") }
            if (nonSafPaths.isNotEmpty()) {
                val maxMb = preferences.trashMaxSizeMb
                if (maxMb > 0) {
                    val incomingSize = nonSafPaths.sumOf { p ->
                        val f = File(p)
                        if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                        else f.length()
                    }
                    val maxBytes = maxMb.toLong() * 1024 * 1024
                    if (incomingSize > maxBytes) {
                        _uiState.update {
                            it.copy(
                                dialogState = DialogState.TrashOverflow(
                                    paths = paths,
                                    incomingSize = incomingSize,
                                    maxSize = maxBytes
                                )
                            )
                        }
                        return@launch
                    }
                }
            }
            // Delegate to foreground service
            FileOperationService.startDelete(appContext, paths, useTrash = true)
        }
    }

    // --- Cancel file operation ---

    fun cancelFileOperation() {
        // Cancel foreground service
        val intent = android.content.Intent(appContext, FileOperationService::class.java).apply {
            action = FileOperationService.ACTION_CANCEL
        }
        appContext.startService(intent)
        // Cancel cloud transfers
        cloudTransferJobs.values.forEach { it.cancel() }
        cloudTransferJobs.clear()
        _uiState.update { state ->
            val p = state.operationProgress
            if (p != null && !p.isComplete) {
                state.copy(operationProgress = null, operationProgressList = emptyList())
            } else {
                state.copy(operationProgressList = emptyList())
            }
        }
        _toastMessage.tryEmit(appContext.getString(R.string.operation_cancelled))
    }

    // --- Inline rename ---

    fun requestRename() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val selected = panel.selectedPaths.firstOrNull() ?: return
        updatePanel(activeId) {
            it.copy(
                selectedPaths = emptySet(),
                isSelectionMode = false,
                renamingPath = selected
            )
        }
    }

    fun confirmInlineRename(newName: String) {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val path = panel.renamingPath ?: return
        updatePanel(activeId) { it.copy(renamingPath = null) }

        viewModelScope.launch {
            if (isCloudPath(path)) {
                val parsed = cloudManager.parseCloudUri(path)
                if (parsed != null) {
                    val (provider, cloudFileId) = parsed
                    cloudManager.rename(parsed.accountId, cloudFileId, newName)
                        .onSuccess {
                            hapticManager.success()
                            _toastMessage.tryEmit(appContext.getString(R.string.cloud_rename_success))
                            refreshBothIfSamePath(activeId)
                        }
                        .onFailure { e ->
                            hapticManager.error()
                            _toastMessage.tryEmit(e.message ?: appContext.getString(R.string.error_rename))
                            EcosystemLogger.e(HaronConstants.TAG, "Cloud rename error: ${e.message}")
                        }
                }
            } else {
                renameFileUseCase(path, newName)
                    .onSuccess {
                        hapticManager.success()
                        val parentDir = path.substringBeforeLast('/')
                        val newPath = "$parentDir/$newName"
                        preferences.migrateFileTags(path, newPath)
                        refreshTags()
                        refreshBothIfSamePath(activeId)
                    }
                    .onFailure { e ->
                        hapticManager.error()
                        _toastMessage.tryEmit(e.message ?: appContext.getString(R.string.error_rename))
                        EcosystemLogger.e(HaronConstants.TAG, "Ошибка переименования: ${e.message}")
                    }
            }
        }
    }

    fun cancelInlineRename() {
        _uiState.update { state ->
            state.copy(
                topPanel = state.topPanel.copy(renamingPath = null),
                bottomPanel = state.bottomPanel.copy(renamingPath = null)
            )
        }
    }

    // --- Batch rename ---

    fun requestBatchRename() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val paths = panel.selectedPaths.toList()
        if (paths.size < 2) return
        val selected = panel.selectedPaths
        val matchedEntries = panel.files.filter { e -> e.path in selected }
        _uiState.update { it.copy(dialogState = DialogState.BatchRename(paths, matchedEntries)) }
    }

    fun confirmBatchRename(renames: List<Pair<String, String>>) {
        dismissDialog()
        val activeId = _uiState.value.activePanel
        viewModelScope.launch {
            clearSelection(activeId)
            batchRenameUseCase(renames)
                .onSuccess { count ->
                    // Migrate tags for each renamed file
                    renames.forEach { (oldPath, newName) ->
                        val parent = File(oldPath).parent ?: return@forEach
                        val newPath = "$parent/$newName"
                        preferences.migrateFileTags(oldPath, newPath)
                    }
                    refreshTags()
                    hapticManager.success()
                    _toastMessage.tryEmit(appContext.getString(R.string.batch_rename_success, count))
                    refreshBothIfSamePath(activeId)
                }
                .onFailure { e ->
                    hapticManager.error()
                    _toastMessage.tryEmit(e.message ?: appContext.getString(R.string.error_rename))
                    EcosystemLogger.e(HaronConstants.TAG, "Batch rename error: ${e.message}")
                    refreshBothIfSamePath(activeId)
                }
        }
    }

    fun saveBatchRenamePattern(pattern: String) {
        preferences.addRenamePattern(pattern)
    }

    fun getRenamePatterns(): List<String> = preferences.getRenamePatterns()

    // --- Tags ---

    fun requestTagAssign() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val paths = panel.selectedPaths.toList()
        if (paths.isEmpty()) return
        _uiState.update { it.copy(dialogState = DialogState.TagAssign(paths)) }
    }

    fun confirmTagAssign(paths: List<String>, tagNames: List<String>) {
        dismissDialog()
        paths.forEach { path -> preferences.setFileTags(path, tagNames) }
        refreshTags()
        clearSelection(_uiState.value.activePanel)
    }

    /** Paths for returning from TagManage back to TagAssign */
    private var tagAssignReturnPaths: List<String>? = null

    fun showTagManager() {
        // Remember TagAssign paths so we can return to it
        val current = _uiState.value.dialogState
        tagAssignReturnPaths = if (current is DialogState.TagAssign) current.paths else null
        _uiState.update { it.copy(dialogState = DialogState.TagManage) }
    }

    fun dismissTagManager() {
        val paths = tagAssignReturnPaths
        tagAssignReturnPaths = null
        if (paths != null) {
            _uiState.update { it.copy(dialogState = DialogState.TagAssign(paths)) }
        } else {
            dismissDialog()
        }
    }

    fun addTag(name: String, colorIndex: Int) {
        preferences.addTagDefinition(FileTag(name, colorIndex))
        refreshTags()
    }

    fun editTag(oldName: String, newName: String, colorIndex: Int) {
        preferences.updateTagDefinition(oldName, FileTag(newName, colorIndex))
        refreshTags()
    }

    fun deleteTag(name: String) {
        preferences.removeTagDefinition(name)
        // Clear active filter if it was this tag
        if (_uiState.value.activeTagFilter == name) {
            _uiState.update { it.copy(activeTagFilter = null) }
        }
        refreshTags()
    }

    fun setTagFilter(tagName: String?) {
        _uiState.update { it.copy(activeTagFilter = tagName) }
    }

    private fun refreshTags() {
        _uiState.update {
            it.copy(
                tagDefinitions = preferences.getTagDefinitions(),
                fileTags = preferences.getFileTagMappings()
            )
        }
    }

    /** Copy tags from source paths to destination directory (for copy operations). */
    private fun copyTagsToDestination(sourcePaths: List<String>, destinationDir: String) {
        val mappings = preferences.getFileTagMappings()
        for (path in sourcePaths) {
            if (path.startsWith("content://")) continue
            val tags = mappings[path] ?: continue
            if (tags.isEmpty()) continue
            val fileName = File(path).name
            val newPath = "$destinationDir/$fileName"
            preferences.setFileTags(newPath, tags)
        }
        refreshTags()
    }

    /** Migrate tags from source paths to destination directory (for move operations). */
    private fun migrateTagsToDestination(sourcePaths: List<String>, destinationDir: String) {
        for (path in sourcePaths) {
            if (path.startsWith("content://")) continue
            val fileName = File(path).name
            val newPath = "$destinationDir/$fileName"
            preferences.migrateFileTags(path, newPath)
        }
        refreshTags()
    }

    /** Remove tags for deleted paths. */
    private fun removeTagsForPaths(paths: List<String>) {
        for (path in paths) {
            if (path.startsWith("content://")) continue
            preferences.removeFileTags(path)
        }
        refreshTags()
    }

    // --- Templates ---

    fun requestCreateFromTemplate() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        if (isCloudPath(panel.currentPath)) {
            showCloudCreateFolder()
            return
        }
        if (panel.currentPath == HaronConstants.VIRTUAL_SECURE_PATH) {
            _toastMessage.tryEmit(appContext.getString(R.string.cannot_create_in_virtual))
            return
        }
        val inProtected = secureFolderRepository.isFileProtected(panel.currentPath)
        val templates = if (inProtected) {
            listOf(FileTemplate.FOLDER, FileTemplate.TXT)
        } else {
            FileTemplate.entries.toList()
        }
        _uiState.update { it.copy(dialogState = DialogState.CreateFromTemplate(templates)) }
    }

    /** Create folder only (from navbar radial menu) */
    fun requestCreateFolder() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        if (isCloudPath(panel.currentPath)) {
            showCloudCreateFolder()
            return
        }
        if (panel.currentPath == HaronConstants.VIRTUAL_SECURE_PATH) {
            _toastMessage.tryEmit(appContext.getString(R.string.cannot_create_in_virtual))
            return
        }
        _uiState.update { it.copy(dialogState = DialogState.CreateFromTemplate(listOf(FileTemplate.FOLDER))) }
    }

    fun confirmCreateFromTemplate(template: FileTemplate, name: String) {
        dismissDialog()
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)

        if (secureFolderRepository.isFileProtected(panel.currentPath)) {
            // Create in protected context — create file, encrypt, remove original
            createInProtectedDir(template, name, panel.currentPath, activeId)
            return
        }

        viewModelScope.launch {
            val result = when (template) {
                FileTemplate.FOLDER -> {
                    createDirectoryUseCase(panel.currentPath, name)
                }
                FileTemplate.TXT -> {
                    val fileName = if (name.endsWith(".txt")) name else "$name.txt"
                    createFileUseCase(panel.currentPath, fileName, "")
                }
                FileTemplate.MARKDOWN -> {
                    val fileName = if (name.endsWith(".md")) name else "$name.md"
                    val content = "# $name\n\n"
                    createFileUseCase(panel.currentPath, fileName, content)
                }
                FileTemplate.DATED_FOLDER -> {
                    val dateName = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    createDirectoryUseCase(panel.currentPath, dateName)
                }
            }
            result
                .onSuccess { refreshBothIfSamePath(activeId) }
                .onFailure { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "Ошибка создания: ${e.message}")
                }
        }
    }

    private fun createInProtectedDir(template: FileTemplate, name: String, parentPath: String, panelId: PanelId) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Recreate the protected directory temporarily
                    val parentDir = File(parentPath)
                    parentDir.mkdirs()

                    when (template) {
                        FileTemplate.FOLDER -> {
                            val dir = File(parentDir, name)
                            dir.mkdirs()
                            secureFolderRepository.protectFiles(listOf(dir.absolutePath)) { _, _ -> }
                        }
                        FileTemplate.TXT -> {
                            val fileName = if (name.endsWith(".txt")) name else "$name.txt"
                            val file = File(parentDir, fileName)
                            file.writeText("")
                            secureFolderRepository.protectFiles(listOf(file.absolutePath)) { _, _ -> }
                        }
                        else -> { /* Only FOLDER and TXT allowed in protected view */ }
                    }

                    // Clean up: remove parent dir if it's now empty (was recreated temporarily)
                    if (parentDir.exists() && parentDir.isDirectory && (parentDir.listFiles()?.isEmpty() == true)) {
                        parentDir.delete()
                    }
                }
                refreshPanel(PanelId.TOP)
                refreshPanel(PanelId.BOTTOM)
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "createInProtectedDir error: ${e.message}")
                _toastMessage.tryEmit(appContext.getString(R.string.protect_error, e.message ?: ""))
            }
        }
    }

    // --- Archive creation ---

    fun requestCreateArchive() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val paths = panel.selectedPaths.toList()
        EcosystemLogger.d(HaronConstants.TAG, "requestCreateArchive: panel=$activeId, selected=${paths.size}, " +
                "isArchive=${panel.isArchiveMode}, currentPath=${panel.currentPath}")
        if (paths.isEmpty()) {
            EcosystemLogger.d(HaronConstants.TAG, "requestCreateArchive: no files selected, skipping")
            _toastMessage.tryEmit(appContext.getString(R.string.no_files_selected))
            return
        }
        _uiState.update { it.copy(dialogState = DialogState.CreateArchive(paths)) }
    }

    fun confirmCreateArchive(
        selectedPaths: List<String>,
        archiveName: String,
        password: String? = null,
        splitSizeMb: Int = 0
    ) {
        dismissDialog()
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val name = if (archiveName.endsWith(".zip")) archiveName else "$archiveName.zip"

        EcosystemLogger.d(HaronConstants.TAG, "confirmCreateArchive: name=$name, sources=${selectedPaths.size}, " +
                "currentPath=${panel.currentPath}, isArchive=${panel.isArchiveMode}, split=${splitSizeMb}MB, hasPwd=${!password.isNullOrEmpty()}")

        // Don't allow archive creation from within archive view mode
        if (panel.isArchiveMode) {
            _toastMessage.tryEmit(appContext.getString(R.string.archive_create_error, "Cannot create archive from archive view"))
            EcosystemLogger.e(HaronConstants.TAG, "confirmCreateArchive: blocked — panel is in archive mode")
            return
        }

        val outputPath = if (panel.currentPath.startsWith("content://")) {
            File(appContext.cacheDir, name).absolutePath
        } else {
            "${panel.currentPath}/$name"
        }

        // Validate output parent dir
        val outputFile = File(outputPath)
        val parentDir = outputFile.parentFile
        if (parentDir == null || (!parentDir.exists() && !parentDir.mkdirs())) {
            _toastMessage.tryEmit(appContext.getString(R.string.archive_create_error, "Output directory not accessible"))
            EcosystemLogger.e(HaronConstants.TAG, "confirmCreateArchive: parent dir doesn't exist: ${parentDir?.absolutePath}")
            return
        }

        // Validate source files exist
        val missingFiles = selectedPaths.filter { !File(it).exists() }
        if (missingFiles.isNotEmpty()) {
            EcosystemLogger.e(HaronConstants.TAG, "confirmCreateArchive: ${missingFiles.size} source files missing: ${missingFiles.take(3)}")
            _toastMessage.tryEmit(appContext.getString(R.string.archive_create_error, "Source files not found (${missingFiles.size})"))
            return
        }

        EcosystemLogger.d(HaronConstants.TAG, "confirmCreateArchive: outputPath=$outputPath, all sources valid")

        // Check if file already exists — show conflict dialog
        if (File(outputPath).exists()) {
            EcosystemLogger.d(HaronConstants.TAG, "confirmCreateArchive: file exists, showing conflict dialog")
            _uiState.update {
                it.copy(dialogState = DialogState.ArchiveCreateConflict(
                    selectedPaths = selectedPaths,
                    outputPath = outputPath,
                    archiveName = name,
                    password = password,
                    splitSizeMb = splitSizeMb
                ))
            }
            return
        }

        doCreateArchive(selectedPaths, outputPath, name, password, splitSizeMb)
    }

    fun confirmArchiveCreateReplace(dialog: DialogState.ArchiveCreateConflict) {
        dismissDialog()
        val file = File(dialog.outputPath)
        if (file.exists()) file.delete()
        EcosystemLogger.d(HaronConstants.TAG, "confirmArchiveCreateReplace: deleted existing ${dialog.archiveName}")
        doCreateArchive(dialog.selectedPaths, dialog.outputPath, dialog.archiveName, dialog.password, dialog.splitSizeMb)
    }

    fun confirmArchiveCreateRename(dialog: DialogState.ArchiveCreateConflict) {
        dismissDialog()
        val renamedPath = CreateZipUseCase.findUniqueZipPath(dialog.outputPath)
        val renamedName = File(renamedPath).name
        EcosystemLogger.d(HaronConstants.TAG, "confirmArchiveCreateRename: renamed to $renamedName")
        doCreateArchive(dialog.selectedPaths, renamedPath, renamedName, dialog.password, dialog.splitSizeMb)
    }

    private fun doCreateArchive(
        selectedPaths: List<String>,
        outputPath: String,
        archiveName: String,
        password: String?,
        splitSizeMb: Int
    ) {
        clearSelection(_uiState.value.activePanel)
        FileOperationService.startArchive(appContext, selectedPaths, outputPath, password, splitSizeMb)
    }

    fun createArchiveOneToOne(selectedPaths: List<String>) {
        dismissDialog()
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)

        EcosystemLogger.d(HaronConstants.TAG, "createArchiveOneToOne: ${selectedPaths.size} files, panel=$activeId")

        if (panel.isArchiveMode) {
            _toastMessage.tryEmit(appContext.getString(R.string.archive_create_error, "Cannot create archive from archive view"))
            return
        }

        clearSelection(activeId)
        FileOperationService.startArchiveOneToOne(appContext, selectedPaths)
    }

    fun onPreviewFileChanged(newIndex: Int) {
        // Cancel pending preloads from previous page to avoid OOM / race conditions
        preloadJobs.forEach { it.cancel() }
        preloadJobs.clear()

        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.QuickPreview) return
        val files = dialog.adjacentFiles
        if (newIndex !in files.indices) return
        val newEntry = files[newIndex]

        // Check cache — if already loaded, show instantly without flash
        val cached = dialog.previewCache[newIndex]
        if (cached != null) {
            _uiState.update {
                it.copy(dialogState = dialog.copy(
                    entry = newEntry,
                    previewData = cached,
                    isLoading = false,
                    error = null,
                    currentFileIndex = newIndex
                ))
            }
            preloadPreview(newIndex - 1, files)
            preloadPreview(newIndex + 1, files)
            return
        }

        // Not cached — load with spinner
        _uiState.update {
            it.copy(dialogState = dialog.copy(
                entry = newEntry,
                previewData = null,
                isLoading = true,
                error = null,
                currentFileIndex = newIndex
            ))
        }

        viewModelScope.launch {
            val resolved = resolvePreviewEntry(newEntry)
            loadPreviewUseCase(resolved)
                .onSuccess { rawData ->
                    val data = adaptCloudPreview(newEntry, rawData)
                    val current = _uiState.value.dialogState
                    if (current is DialogState.QuickPreview && current.entry.path == newEntry.path) {
                        _uiState.update {
                            it.copy(dialogState = current.copy(
                                previewData = data,
                                isLoading = false,
                                previewCache = current.previewCache + (newIndex to data),
                                resolvedPath = resolved.path.takeIf { it != newEntry.path }
                            ))
                        }
                        preloadPreview(newIndex - 1, files)
                        preloadPreview(newIndex + 1, files)
                    }
                }
                .onFailure { e ->
                    val current = _uiState.value.dialogState
                    if (current is DialogState.QuickPreview && current.entry.path == newEntry.path) {
                        _uiState.update {
                            it.copy(dialogState = current.copy(
                                isLoading = false,
                                error = e.message ?: appContext.getString(R.string.unknown_error)
                            ))
                        }
                    }
                }
        }
    }

    /**
     * Delete current file from preview and navigate to next/previous.
     * Closes preview only if it was the last file.
     */
    fun deleteFromPreview() {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.QuickPreview) return
        val path = dialog.entry.path
        val files = dialog.adjacentFiles
        val idx = dialog.currentFileIndex
        val activeId = _uiState.value.activePanel

        if (files.size <= 1) {
            // Last file — close preview and delete
            dismissDialog()
            silentDelete(path, activeId)
            return
        }

        // Remove from list, fix index, rebuild cache
        val newFiles = files.toMutableList().apply { removeAt(idx) }
        val newIdx = if (idx >= newFiles.size) newFiles.size - 1 else idx
        val newEntry = newFiles[newIdx]

        // Shift cache indices
        val newCache = mutableMapOf<Int, PreviewData>()
        for ((k, v) in dialog.previewCache) {
            val newKey = when {
                k < idx -> k
                k == idx -> continue
                else -> k - 1
            }
            if (newKey in newFiles.indices) newCache[newKey] = v
        }

        val cached = newCache[newIdx]
        _uiState.update {
            it.copy(dialogState = dialog.copy(
                entry = newEntry,
                previewData = cached,
                isLoading = cached == null,
                error = null,
                adjacentFiles = newFiles,
                currentFileIndex = newIdx,
                previewCache = newCache
            ))
        }

        // Load preview if not cached, always preload neighbors
        if (cached == null) {
            viewModelScope.launch {
                val resolved = resolvePreviewEntry(newEntry)
                loadPreviewUseCase(resolved)
                    .onSuccess { rawData ->
                        val data = adaptCloudPreview(newEntry, rawData)
                        val current = _uiState.value.dialogState
                        if (current is DialogState.QuickPreview && current.entry.path == newEntry.path) {
                            _uiState.update {
                                it.copy(dialogState = current.copy(
                                    previewData = data,
                                    isLoading = false,
                                    previewCache = current.previewCache + (newIdx to data)
                                ))
                            }
                            preloadPreview(newIdx - 1, newFiles)
                            preloadPreview(newIdx + 1, newFiles)
                        }
                    }
                    .onFailure { e ->
                        val current = _uiState.value.dialogState
                        if (current is DialogState.QuickPreview && current.entry.path == newEntry.path) {
                            _uiState.update {
                                it.copy(dialogState = current.copy(
                                    isLoading = false,
                                    error = e.message
                                ))
                            }
                        }
                    }
            }
        } else {
            preloadPreview(newIdx - 1, newFiles)
            preloadPreview(newIdx + 1, newFiles)
        }

        // Delete file in background without closing preview
        silentDelete(path, activeId)
    }

    /** Delete a single file to trash without dismissing dialogs or showing progress. */
    private fun silentDelete(path: String, panelId: PanelId) {
        viewModelScope.launch {
            try {
                if (isCloudPath(path)) {
                    val parsed = cloudManager.parseCloudUri(path)
                    if (parsed != null) {
                        val (provider, cloudFileId) = parsed
                        cloudManager.delete(parsed.accountId, cloudFileId)
                    }
                } else if (path.startsWith("content://")) {
                    fileRepository.deleteFiles(listOf(path))
                } else {
                    trashRepository.moveToTrash(listOf(path))
                }
                removeTagsForPaths(listOf(path))
                updateTrashSizeInfo()
            } catch (_: Exception) { }
            refreshBothIfSamePath(panelId)
        }
    }

    /**
     * Cloud video/audio thumbnails are loaded as ImagePreview (JPEG file).
     * Convert to VideoPreview/AudioPreview so QuickPreview shows correct play button.
     */
    private fun adaptCloudPreview(entry: FileEntry, data: PreviewData): PreviewData {
        if (!isCloudPath(entry.path) || data !is PreviewData.ImagePreview) return data
        return when (entry.iconRes()) {
            "video" -> PreviewData.VideoPreview(
                fileName = entry.name, fileSize = entry.size, lastModified = entry.lastModified,
                thumbnail = data.bitmap, durationMs = 0L
            )
            "audio" -> PreviewData.AudioPreview(
                fileName = entry.name, fileSize = entry.size, lastModified = entry.lastModified,
                albumArt = data.bitmap, title = null, artist = null, album = null, durationMs = 0L
            )
            else -> data
        }
    }

    /** Resolve a FileEntry for preview: download cloud thumbnail / decrypt protected → return entry with local path. */
    private suspend fun resolvePreviewEntry(entry: FileEntry): FileEntry {
        // Archive entries: extract to temp file for preview
        if (entry.path.contains("!/")) {
            return resolveArchiveEntryForPreview(entry)
        }
        // Cloud files: download full file for archives, thumbnail for images
        if (isCloudPath(entry.path)) {
            val type = entry.iconRes()
            // Archives need full file download for preview (list contents)
            if (type == "archive") {
                return resolveCloudArchiveForPreview(entry)
            }
            val thumbUrl = entry.thumbnailUrl
            val isGDrive = entry.path.startsWith("cloud://gdrive/")
            val isYandex = entry.path.startsWith("cloud://yandex/")
            val isDropbox = entry.path.startsWith("cloud://dropbox/")
            if (thumbUrl.isNullOrEmpty()) {
                // No thumbnail URL — download file to cache for previewable types
                val previewableType = type in listOf("image", "text", "code", "pdf", "document")
                if (previewableType) {
                    return resolveCloudArchiveForPreview(entry)
                }
                return entry
            }
            // Dropbox non-images: thumbnailUrl is a direct file link (not a thumbnail image)
            // GDrive & Yandex: thumbnailUrl is always a thumbnail image (JPEG)
            // For text/code: thumbnail useless (can't read text from image) — download full file
            // For Dropbox non-images: download full file (thumbnailUrl = direct link, not thumbnail)
            if (isDropbox && type != "image") {
                return resolveCloudArchiveForPreview(entry)
            }
            if (type in listOf("text", "code") && (isGDrive || isYandex)) {
                return resolveCloudArchiveForPreview(entry)
            }
            // Google Drive: thumbnailUrl is always a thumbnail image (for any file type)
            // Yandex Disk: thumbnailUrl is a preview image (requires OAuth token)
            // Dropbox images: thumbnailUrl is a direct image file → decodable as bitmap
            val needsAuth = thumbUrl.contains("googleapis.com") || thumbUrl.contains("yandex.ru") || thumbUrl.contains("yandex.net")
            val cacheDir = File(appContext.cacheDir, "cloud_thumbs")
            cacheDir.mkdirs()
            // Stable cache key from cloud path
            val cacheKey = entry.path.hashCode().toUInt().toString(16)
            // Thumbnails are always images (JPEG) regardless of original file type
            val ext = "jpg"
            val thumbFile = File(cacheDir, "thumb_${cacheKey}.$ext")
            val tempThumbFile = File(cacheDir, "thumb_${cacheKey}.tmp")
            // Reuse cached thumbnail (only if no active download)
            if (thumbFile.exists() && thumbFile.length() > 0 && !tempThumbFile.exists()) {
                return entry.copy(path = thumbFile.absolutePath, extension = ext)
            }
            try {
                withContext(Dispatchers.IO) {
                    // For QuickPreview: request largest available thumbnail (XXXL = 1280px)
                    val previewUrl = if (isYandex && "size=" in thumbUrl) {
                        thumbUrl.replace(Regex("size=\\w+"), "size=XXXL")
                    } else thumbUrl
                    val url = java.net.URL(previewUrl)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 30_000
                    conn.instanceFollowRedirects = true
                    if (needsAuth) {
                        val parsed = cloudManager.parseCloudUri(entry.path)
                        if (parsed != null) {
                            val token = cloudManager.getFreshAccessToken(parsed.accountId)
                            if (token != null) {
                                val authPrefix = if (isYandex) "OAuth" else "Bearer"
                                conn.setRequestProperty("Authorization", "$authPrefix $token")
                            }
                        }
                    }
                    try {
                        conn.inputStream.use { input ->
                            java.io.FileOutputStream(tempThumbFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        tempThumbFile.renameTo(thumbFile)
                    } finally {
                        conn.disconnect()
                    }
                }
                if (thumbFile.exists() && thumbFile.length() > 0) {
                    return entry.copy(path = thumbFile.absolutePath, extension = ext)
                }
            } catch (e: Exception) {
                tempThumbFile.delete()
                EcosystemLogger.e(HaronConstants.TAG, "resolvePreviewEntry thumbnail download failed: ${e.message}")
            }
            return entry
        }

        if (!entry.isProtected) return entry
        val allEntries = secureFolderRepository.getAllProtectedEntries()
        val secEntry = allEntries.find { it.originalPath == entry.path } ?: return entry
        return secureFolderRepository.decryptToCache(secEntry.id).getOrNull()?.let { tempFile ->
            entry.copy(path = tempFile.absolutePath)
        } ?: entry
    }

    /** Download cloud file to cache for preview. Atomic write (temp + rename) to avoid race conditions. */
    /**
     * Extract an archive entry to a temp file so standard preview pipeline can handle it.
     * Uses ReadArchiveEntryUseCase for files ≤10MB (in-memory),
     * ExtractArchiveUseCase for larger files.
     */
    private suspend fun resolveArchiveEntryForPreview(entry: FileEntry): FileEntry {
        val archivePath = entry.path.substringBefore("!/")
        val entryFullPath = entry.path.substringAfter("!/")
        // Find password from active panel state
        val panel = getPanel(_uiState.value.activePanel)
        val password = panel.archivePassword

        val cacheDir = File(appContext.cacheDir, "archive_preview")
        cacheDir.mkdirs()
        val ext = entry.extension.ifEmpty { "bin" }
        val cacheKey = (archivePath + "!" + entryFullPath).hashCode().toUInt().toString(16)
        val tempFile = File(cacheDir, "${cacheKey}.$ext")

        // Reuse cached file if recent (< 5 min)
        if (tempFile.exists() && tempFile.length() > 0 &&
            System.currentTimeMillis() - tempFile.lastModified() < 5 * 60 * 1000) {
            return entry.copy(path = tempFile.absolutePath)
        }

        return try {
            if (entry.size <= 10L * 1024 * 1024) {
                // Small file — read into memory and write to temp
                val bytes = readArchiveEntryUseCase(archivePath, entryFullPath, password)
                if (bytes != null && bytes.isNotEmpty()) {
                    tempFile.writeBytes(bytes)
                    entry.copy(path = tempFile.absolutePath)
                } else {
                    EcosystemLogger.e(HaronConstants.TAG, "resolveArchiveEntryForPreview: no bytes for $entryFullPath")
                    entry
                }
            } else {
                // Large file — extract to temp via ExtractArchiveUseCase
                val setOf = setOf(entryFullPath)
                extractArchiveUseCase(archivePath, cacheDir.absolutePath, setOf, password)
                    .collect { /* wait for completion */ }
                val extracted = File(cacheDir, entryFullPath)
                if (extracted.exists()) {
                    // Move to stable cache name
                    extracted.renameTo(tempFile)
                    entry.copy(path = tempFile.absolutePath)
                } else {
                    entry
                }
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "resolveArchiveEntryForPreview failed: ${e.message}")
            entry
        }
    }

    private suspend fun resolveCloudArchiveForPreview(entry: FileEntry): FileEntry {
        val parsed = cloudManager.parseCloudUri(entry.path) ?: return entry
        val (provider, cloudFileId) = parsed
        val cacheDir = File(appContext.cacheDir, "cloud_downloads")
        cacheDir.mkdirs()
        val localFile = File(cacheDir, entry.name)
        val tempFile = File(cacheDir, "${entry.name}.downloading")
        // Reuse cached file if recent (< 10 min) AND no active download
        if (localFile.exists() && localFile.length() > 0 && !tempFile.exists() &&
            System.currentTimeMillis() - localFile.lastModified() < 10 * 60 * 1000) {
            return entry.copy(path = localFile.absolutePath)
        }
        return try {
            var resultEntry = entry
            cloudManager.downloadFile(parsed.accountId, cloudFileId, tempFile.absolutePath)
                .collect { progress ->
                    if (progress.isComplete && progress.error == null) {
                        tempFile.renameTo(localFile)
                        resultEntry = entry.copy(path = localFile.absolutePath)
                    }
                }
            resultEntry
        } catch (e: Exception) {
            tempFile.delete()
            EcosystemLogger.e(HaronConstants.TAG, "resolveCloudArchiveForPreview failed: ${e.message}")
            entry
        }
    }

    private fun preloadPreview(index: Int, files: List<FileEntry>) {
        if (index !in files.indices) return
        val current = _uiState.value.dialogState
        if (current !is DialogState.QuickPreview) return
        if (index in current.previewCache) return

        val job = viewModelScope.launch {
            try {
                val fileEntry = files[index]
                val resolved = resolvePreviewEntry(fileEntry)
                loadPreviewUseCase(resolved)
                    .onSuccess { rawData ->
                        val data = adaptCloudPreview(fileEntry, rawData)
                        val dialog = _uiState.value.dialogState
                        if (dialog is DialogState.QuickPreview) {
                            _uiState.update {
                                it.copy(dialogState = dialog.copy(
                                    previewCache = dialog.previewCache + (index to data)
                                ))
                            }
                        }
                    }
            } catch (_: Exception) {
                // Silently ignore preload errors
            }
        }
        preloadJobs.add(job)
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialogState = DialogState.None) }
    }

    // --- Favorites ---

    fun toggleFavorite(panelId: PanelId) {
        val path = getPanel(panelId).currentPath
        if (path in _uiState.value.favorites) {
            preferences.removeFavorite(path)
        } else {
            preferences.addFavorite(path)
        }
        _uiState.update { it.copy(favorites = preferences.getFavorites()) }
        updateWidget()
    }

    fun removeFavorite(path: String) {
        preferences.removeFavorite(path)
        _uiState.update { it.copy(favorites = preferences.getFavorites()) }
    }

    // --- Drawer ---

    fun toggleDrawer() {
        val opening = !_uiState.value.showDrawer
        _uiState.update {
            it.copy(
                showDrawer = !it.showDrawer,
                showShelf = false // close shelf when opening drawer
            )
        }
        if (opening) updateTrashSizeInfo()
    }

    fun dismissDrawer() {
        _uiState.update { it.copy(showDrawer = false) }
    }

    fun navigateFromDrawer(path: String) {
        val activeId = _uiState.value.activePanel
        dismissDrawer()
        navigateTo(activeId, path)
    }

    // --- Shelf ---

    fun addToShelf() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val selected = panel.files.filter { it.path in panel.selectedPaths }
        if (selected.isEmpty()) return

        val newItems = selected.map {
            ShelfItem(name = it.name, path = it.path, isDirectory = it.isDirectory, size = it.size)
        }
        preferences.addShelfItems(newItems)
        val updatedShelf = preferences.getShelfItems()
        _uiState.update { it.copy(shelfItems = updatedShelf) }
        clearSelection(activeId)
        _toastMessage.tryEmit(appContext.getString(R.string.added_to_shelf_count, selected.size))
    }

    fun removeFromShelf(path: String) {
        preferences.removeShelfItem(path)
        _uiState.update { it.copy(shelfItems = preferences.getShelfItems()) }
    }

    fun clearShelf() {
        preferences.clearShelf()
        _uiState.update { it.copy(shelfItems = emptyList()) }
    }

    fun toggleShelf() {
        _uiState.update {
            it.copy(
                showShelf = !it.showShelf,
                showDrawer = false // close drawer when opening shelf
            )
        }
    }

    fun dismissShelf() {
        _uiState.update { it.copy(showShelf = false) }
    }

    fun pasteFromShelf(isMove: Boolean) {
        val panel = getPanel(_uiState.value.activePanel)
        val destinationDir = panel.currentPath
        val items = _uiState.value.shelfItems
        if (items.isEmpty()) return

        val paths = items.map { it.path }
        dismissShelf()
        if (isMove) clearShelf()

        FileOperationService.start(appContext, paths, destinationDir, isMove = isMove, conflictResolution = ConflictResolution.RENAME)
    }

    // --- Duplicate Detector ---

    fun openDuplicateDetector() {
        _navigationEvent.tryEmit(NavigationEvent.OpenDuplicateDetector)
    }

    // --- Trash ---

    fun showTrash() {
        viewModelScope.launch {
            trashRepository.getTrashEntries()
                .onSuccess { entries ->
                    val totalSize = trashRepository.getTrashSize()
                    _uiState.update {
                        it.copy(dialogState = DialogState.ShowTrash(entries, totalSize, preferences.trashMaxSizeMb))
                    }
                }
                .onFailure { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "Ошибка чтения корзины: ${e.message}")
                }
        }
    }

    fun restoreFromTrash(ids: List<String>) {
        viewModelScope.launch {
            restoreFromTrashUseCase(ids)
                .onSuccess { count ->
                    updateTrashSizeInfo()
                    showStatusMessage(_uiState.value.activePanel, appContext.getString(R.string.restored_count, count))
                    refreshPanel(PanelId.TOP)
                    refreshPanel(PanelId.BOTTOM)
                    // Обновить диалог корзины
                    showTrash()
                }
                .onFailure { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "Ошибка восстановления: ${e.message}")
                }
        }
    }

    fun deleteFromTrashPermanently(ids: List<String>) {
        viewModelScope.launch {
            _uiState.update { state ->
                val dialog = state.dialogState as? DialogState.ShowTrash ?: return@update state
                state.copy(dialogState = dialog.copy(
                    deleteProgress = 0f,
                    deleteCurrentName = ""
                ))
            }
            trashRepository.deleteFromTrashWithProgress(ids) { deleted, total, name ->
                _uiState.update { state ->
                    val dialog = state.dialogState as? DialogState.ShowTrash ?: return@update state
                    state.copy(dialogState = dialog.copy(
                        deleteProgress = deleted.toFloat() / total,
                        deleteCurrentName = name
                    ))
                }
            }
            updateTrashSizeInfo()
            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
            showTrash()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val entries = trashRepository.getTrashEntries().getOrNull() ?: return@launch
            if (entries.isEmpty()) {
                dismissDialog()
                return@launch
            }
            val allIds = entries.map { it.id }
            _uiState.update { state ->
                val dialog = state.dialogState as? DialogState.ShowTrash ?: return@update state
                state.copy(dialogState = dialog.copy(
                    deleteProgress = 0f,
                    deleteCurrentName = ""
                ))
            }
            trashRepository.deleteFromTrashWithProgress(allIds) { deleted, total, name ->
                _uiState.update { state ->
                    val dialog = state.dialogState as? DialogState.ShowTrash ?: return@update state
                    state.copy(dialogState = dialog.copy(
                        deleteProgress = deleted.toFloat() / total,
                        deleteCurrentName = name
                    ))
                }
            }
            updateTrashSizeInfo()
            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
            showStatusMessage(_uiState.value.activePanel, appContext.getString(R.string.trash_cleared_count, entries.size))
            dismissDialog()
        }
    }

    // --- Drag-and-Drop ---

    fun startDrag(panelId: PanelId, paths: List<String>, offset: Offset) {
        if (paths.isEmpty()) return
        val panel = getPanel(panelId)
        val firstEntry = panel.files.find { it.path == paths.first() }
        _uiState.update {
            it.copy(
                dragState = DragState.Dragging(
                    sourcePanelId = panelId,
                    draggedPaths = paths,
                    dragOffset = offset,
                    fileCount = paths.size,
                    previewName = firstEntry?.name ?: paths.first().substringAfterLast('/')
                )
            )
        }
    }

    fun updateDragPosition(offset: Offset) {
        val current = _uiState.value.dragState
        if (current is DragState.Dragging) {
            _uiState.update {
                it.copy(dragState = current.copy(dragOffset = offset))
            }
        }
    }

    fun setDragHoveredFolder(folderPath: String?) {
        val current = _uiState.value.dragState
        if (current is DragState.Dragging && current.hoveredFolderPath != folderPath) {
            _uiState.update {
                it.copy(dragState = current.copy(hoveredFolderPath = folderPath))
            }
        }
    }

    fun updateDragOperation(operation: DragOperation) {
        val current = _uiState.value.dragState
        if (current is DragState.Dragging && current.dragOperation != operation) {
            _uiState.update { it.copy(dragState = current.copy(dragOperation = operation)) }
        }
    }

    fun endDrag(targetPanelId: PanelId?) {
        val current = _uiState.value.dragState
        if (current !is DragState.Dragging) return

        val hoveredFolder = current.hoveredFolderPath
        val operation = current.dragOperation
        EcosystemLogger.d(HaronConstants.TAG, "endDrag: targetPanel=$targetPanelId, source=${current.sourcePanelId}, hoveredFolder=$hoveredFolder, paths=${current.draggedPaths.size}, op=$operation")
        _uiState.update { it.copy(dragState = DragState.Idle) }

        // Drop into a specific folder (same or other panel)
        if (hoveredFolder != null) {
            val paths = current.draggedPaths
            // Don't move/copy a folder into itself
            if (paths.any { hoveredFolder.startsWith(it) } || paths.contains(hoveredFolder)) {
                EcosystemLogger.d(HaronConstants.TAG, "endDrag: abort — folder into itself")
                return
            }
            // Don't move/copy files into their own parent directory (no-op)
            val sourceDir = getPanel(current.sourcePanelId).currentPath
            if (hoveredFolder == sourceDir) {
                EcosystemLogger.d(HaronConstants.TAG, "endDrag: abort — same parent dir")
                return
            }
            clearSelection(current.sourcePanelId)
            val destPanelId = targetPanelId ?: current.sourcePanelId
            // Determine effective operation: same panel folder = always MOVE, cross-panel = user choice
            val isCrossPanel = destPanelId != current.sourcePanelId
            val effectiveOp = if (isCrossPanel) operation else DragOperation.MOVE
            val opType = if (effectiveOp == DragOperation.COPY) OperationType.COPY else OperationType.MOVE

            // Check for conflicts in the target folder (local files only)
            if (!isCloudPath(hoveredFolder) && !hoveredFolder.startsWith("content://")) {
                val destFolder = File(hoveredFolder)
                val existingNames = destFolder.listFiles()?.map { it.name }?.toSet() ?: emptySet()
                val sourcePanel = getPanel(current.sourcePanelId)
                val sourceFiles = sourcePanel.files.associateBy { it.path }
                val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
                val conflictPairs = mutableListOf<ConflictPair>()
                for (path in paths) {
                    val srcEntry = sourceFiles[path]
                    val name = srcEntry?.name ?: File(path).name
                    if (name in existingNames) {
                        val srcFile = File(path)
                        val destFile = File(hoveredFolder, name)
                        val srcExt = name.substringAfterLast('.', "").lowercase()
                        val dstExt = srcExt
                        conflictPairs.add(ConflictPair(
                            source = ConflictFileInfo(
                                name = name,
                                path = path,
                                size = srcEntry?.size ?: srcFile.length(),
                                lastModified = srcEntry?.lastModified ?: srcFile.lastModified(),
                                isImage = srcExt in imageExtensions
                            ),
                            destination = ConflictFileInfo(
                                name = name,
                                path = destFile.absolutePath,
                                size = destFile.length(),
                                lastModified = destFile.lastModified(),
                                isImage = dstExt in imageExtensions
                            )
                        ))
                    }
                }
                if (conflictPairs.isNotEmpty()) {
                    _uiState.update {
                        it.copy(dialogState = DialogState.ConfirmConflict(
                            conflictPairs = conflictPairs,
                            allPaths = paths,
                            destinationDir = hoveredFolder,
                            operationType = opType,
                            sourcePanelId = current.sourcePanelId,
                            targetPanelId = destPanelId
                        ))
                    }
                    return
                }
            }

            if (effectiveOp == DragOperation.COPY) {
                executeDragCopy(paths, hoveredFolder, current.sourcePanelId, destPanelId, current.fileCount, ConflictResolution.RENAME)
            } else {
                executeDragMove(paths, hoveredFolder, current.sourcePanelId, destPanelId, current.fileCount, ConflictResolution.RENAME)
            }
            return
        }

        if (targetPanelId == null || targetPanelId == current.sourcePanelId) {
            EcosystemLogger.d(HaronConstants.TAG, "endDrag: abort — no target panel or same panel (targetPanelId=$targetPanelId)")
            return
        }

        val sourcePanel = getPanel(current.sourcePanelId)
        val targetPanel = getPanel(targetPanelId)
        EcosystemLogger.d(HaronConstants.TAG, "endDrag: cross-panel, src=${sourcePanel.currentPath}, dst=${targetPanel.currentPath}, op=$operation")
        if (sourcePanel.currentPath == targetPanel.currentPath) return

        val paths = current.draggedPaths
        clearSelection(current.sourcePanelId)
        val opType = if (operation == DragOperation.COPY) OperationType.COPY else OperationType.MOVE

        // Conflict check — works for both local and cloud destinations (name-based comparison)
        try {
            val conflictPairs = buildConflictPairs(paths, targetPanel)
            if (conflictPairs.isNotEmpty()) {
                _uiState.update {
                    it.copy(dialogState = DialogState.ConfirmConflict(
                        conflictPairs = conflictPairs,
                        allPaths = paths,
                        destinationDir = targetPanel.currentPath,
                        operationType = opType,
                        sourcePanelId = current.sourcePanelId,
                        targetPanelId = targetPanelId
                    ))
                }
                return
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "endDrag: buildConflictPairs failed: ${e.message}")
        }

        if (operation == DragOperation.COPY) {
            EcosystemLogger.d(HaronConstants.TAG, "endDrag: calling executeDragCopy, paths=$paths, dest=${targetPanel.currentPath}")
            executeDragCopy(paths, targetPanel.currentPath, current.sourcePanelId, targetPanelId, current.fileCount, ConflictResolution.RENAME)
        } else {
            EcosystemLogger.d(HaronConstants.TAG, "endDrag: calling executeDragMove, paths=$paths, dest=${targetPanel.currentPath}")
            executeDragMove(paths, targetPanel.currentPath, current.sourcePanelId, targetPanelId, current.fileCount, ConflictResolution.RENAME)
        }
    }

    private fun executeDragMove(
        paths: List<String>,
        destinationDir: String,
        sourcePanelId: PanelId,
        targetPanelId: PanelId,
        fileCount: Int,
        resolution: ConflictResolution
    ) {
        EcosystemLogger.d(HaronConstants.TAG, "executeDragMove: ${paths.size} paths, dest=$destinationDir, isCloud=${isCloudPath(destinationDir)}")
        // Cloud → cloud: use cloud API
        if (paths.any { isCloudPath(it) } && isCloudPath(destinationDir)) {
            executeCloudDragMove(paths, destinationDir, sourcePanelId, targetPanelId)
            return
        }
        // Cloud → local: download
        if (paths.any { isCloudPath(it) } && !isCloudPath(destinationDir)) {
            executeDragCloudDownload(paths, destinationDir, sourcePanelId, targetPanelId)
            return
        }
        // Local → cloud: upload
        if (!paths.any { isCloudPath(it) } && isCloudPath(destinationDir)) {
            executeDragCloudUpload(paths, destinationDir, sourcePanelId, targetPanelId)
            return
        }

        val total = paths.size
        viewModelScope.launch {
            var completed = 0
            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, "", OperationType.MOVE))
            }

            for ((index, path) in paths.withIndex()) {
                val fileName = extractFileName(path)
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(index, total, fileName, OperationType.MOVE))
                }
                val srcDir = java.io.File(path).parent ?: ""
                fileRepository.moveFilesWithResolutions(
                    listOf(path), destinationDir, mapOf(path to resolution)
                ).onSuccess { c ->
                    completed += c
                    if (c > 0) {
                        val size = getFileSizeForDelta(path)
                        if (size > 0) {
                            adjustFolderSizeCache(destinationDir, +size)
                            adjustFolderSizeCache(srcDir, -size)
                        }
                    }
                }.onFailure { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "DnD move error: $fileName: ${e.message}")
                }
            }

            _uiState.update {
                it.copy(operationProgress = OperationProgress(completed, total, "", OperationType.MOVE, isComplete = true))
            }
            refreshPanel(sourcePanelId)
            refreshPanel(targetPanelId)
            if (completed > 0) hapticManager.success() else hapticManager.error()

            delay(2000)
            _uiState.update {
                if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it
            }
        }
    }

    private fun executeDragCopy(
        paths: List<String>,
        destinationDir: String,
        sourcePanelId: PanelId,
        targetPanelId: PanelId,
        fileCount: Int,
        resolution: ConflictResolution
    ) {
        EcosystemLogger.d(HaronConstants.TAG, "executeDragCopy: ${paths.size} paths, dest=$destinationDir, isCloud=${isCloudPath(destinationDir)}")
        // Cloud → cloud: use cloud API (inherently copy for cloud download/upload)
        if (paths.any { isCloudPath(it) } && isCloudPath(destinationDir)) {
            executeCloudDragMove(paths, destinationDir, sourcePanelId, targetPanelId)
            return
        }
        // Cloud → local: download (inherently copy)
        if (paths.any { isCloudPath(it) } && !isCloudPath(destinationDir)) {
            executeDragCloudDownload(paths, destinationDir, sourcePanelId, targetPanelId)
            return
        }
        // Local → cloud: upload (inherently copy)
        if (!paths.any { isCloudPath(it) } && isCloudPath(destinationDir)) {
            executeDragCloudUpload(paths, destinationDir, sourcePanelId, targetPanelId)
            return
        }

        val total = paths.size
        viewModelScope.launch {
            var completed = 0
            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, "", OperationType.COPY))
            }

            for ((index, path) in paths.withIndex()) {
                val fileName = extractFileName(path)
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(index, total, fileName, OperationType.COPY))
                }
                fileRepository.copyFilesWithResolutions(
                    listOf(path), destinationDir, mapOf(path to resolution)
                ).onSuccess { c ->
                    completed += c
                    if (c > 0) {
                        val size = getFileSizeForDelta(path)
                        if (size > 0) adjustFolderSizeCache(destinationDir, +size)
                    }
                }.onFailure { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "DnD copy error: $fileName: ${e.message}")
                    }
            }

            _uiState.update {
                it.copy(operationProgress = OperationProgress(completed, total, "", OperationType.COPY, isComplete = true))
            }
            // Refresh both panels to ensure UI shows real state
            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
            if (completed > 0) hapticManager.success() else hapticManager.error()

            delay(2000)
            _uiState.update {
                if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it
            }
        }
    }

    private fun executeDragCopyWithDecisions(
        paths: List<String>,
        destinationDir: String,
        sourcePanelId: PanelId,
        targetPanelId: PanelId,
        decisions: Map<String, ConflictResolution>
    ) {
        // Cloud routing (inherently copy for cloud)
        if (paths.any { isCloudPath(it) } && isCloudPath(destinationDir)) {
            executeCloudDragMoveWithDecisions(paths, destinationDir, sourcePanelId, targetPanelId, decisions)
            return
        }
        if (paths.any { isCloudPath(it) } && !isCloudPath(destinationDir)) {
            executeDragCloudDownloadWithDecisions(paths, destinationDir, sourcePanelId, targetPanelId, decisions)
            return
        }
        if (!paths.any { isCloudPath(it) } && isCloudPath(destinationDir)) {
            executeDragCloudUploadWithDecisions(paths, destinationDir, sourcePanelId, targetPanelId, decisions)
            return
        }

        val total = paths.size
        viewModelScope.launch {
            var completed = 0
            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, "", OperationType.COPY))
            }
            for ((index, path) in paths.withIndex()) {
                val fileName = extractFileName(path)
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(index, total, fileName, OperationType.COPY))
                }
                val resolution = decisions[path] ?: ConflictResolution.RENAME
                fileRepository.copyFilesWithResolutions(
                    listOf(path), destinationDir, mapOf(path to resolution)
                ).onSuccess { c -> completed += c }
            }
            _uiState.update {
                it.copy(operationProgress = OperationProgress(completed, total, "", OperationType.COPY, isComplete = true))
            }
            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
            if (completed > 0) hapticManager.success() else hapticManager.error()
            viewModelScope.launch {
                delay(2000)
                _uiState.update {
                    if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it
                }
            }
        }
    }

    fun cancelDrag() {
        _uiState.update { it.copy(dragState = DragState.Idle) }
    }

    private fun executeCloudDragMove(
        paths: List<String>,
        destinationDir: String,
        sourcePanelId: PanelId,
        targetPanelId: PanelId
    ) {
        viewModelScope.launch {
            val total = paths.size
            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, "", OperationType.MOVE))
            }

            val destParsed = cloudManager.parseCloudUri(destinationDir)
            if (destParsed == null) {
                EcosystemLogger.e(HaronConstants.TAG, "Cloud DnD: invalid destination URI: $destinationDir")
                _uiState.update { it.copy(operationProgress = null) }
                return@launch
            }
            val (destProvider, destFolderId) = destParsed

            // Source parent folder ID from panel's current path
            val sourcePanel = getPanel(sourcePanelId)
            val sourceParsed = cloudManager.parseCloudUri(sourcePanel.currentPath)
            val sourceParentId = sourceParsed?.path ?: "root"

            var moved = 0
            for (path in paths) {
                val parsed = cloudManager.parseCloudUri(path) ?: continue
                val (provider, fileId) = parsed
                if (provider != destProvider) {
                    EcosystemLogger.e(HaronConstants.TAG, "Cloud DnD: cross-provider move not supported")
                    continue
                }
                cloudManager.moveFile(parsed.accountId, fileId, sourceParentId, destFolderId)
                    .onSuccess { moved++ }
                    .onFailure { e ->
                        EcosystemLogger.e(HaronConstants.TAG, "Cloud DnD move error for $fileId: ${e.message}")
                    }
            }

            _uiState.update {
                it.copy(
                    operationProgress = OperationProgress(
                        total, total, "", OperationType.MOVE, isComplete = true
                    )
                )
            }
            showStatusMessage(targetPanelId, appContext.getString(R.string.cloud_moved, moved))
            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)

            delay(2000)
            _uiState.update { it.copy(operationProgress = null) }
        }
    }

    /**
     * DnD: cloud → local — download dragged cloud files into local destination folder.
     */
    private fun executeDragCloudDownload(
        paths: List<String>,
        destinationDir: String,
        sourcePanelId: PanelId,
        targetPanelId: PanelId
    ) {
        val progressId = nextProgressId()
        launchCloudJob {
            val total = paths.size
            val sourcePanel = getPanel(sourcePanelId)
            val nameMap = sourcePanel.files.associateBy { it.path }
            val totalBytes = paths.sumOf { nameMap[it]?.size ?: 0L }
            var completedBytes = 0L

            EcosystemLogger.d(HaronConstants.TAG, "executeDragCloudDownload: $total files → $destinationDir, totalBytes=$totalBytes")

            updateProgress(OperationProgress(0, total, "", OperationType.DOWNLOAD, id = progressId))

            var downloaded = 0
            for ((idx, path) in paths.withIndex()) {
                val parsed = cloudManager.parseCloudUri(path) ?: continue
                val (provider, cloudFileId) = parsed
                val entry = nameMap[path]
                val fileName = entry?.name ?: cloudFileId
                val fileSize = entry?.size ?: 0L
                val localFile = File(destinationDir, fileName)

                updateProgress(OperationProgress(
                    idx + 1, total, fileName, OperationType.DOWNLOAD,
                    filePercent = if (totalBytes > 0) ((completedBytes * 100) / totalBytes).toInt() else 0,
                    id = progressId
                ))

                try {
                    cloudManager.downloadFile(parsed.accountId, cloudFileId, localFile.absolutePath)
                        .collect { progress ->
                            val overallPercent = if (totalBytes > 0) {
                                ((completedBytes + progress.bytesTransferred) * 100 / totalBytes).toInt()
                            } else 0
                            updateProgress(OperationProgress(
                                idx + 1, total, fileName, OperationType.DOWNLOAD,
                                filePercent = overallPercent.coerceIn(0, 100),
                                id = progressId
                            ))
                        }
                    completedBytes += fileSize
                    downloaded++
                    refreshPanel(targetPanelId)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    completedBytes += fileSize
                    EcosystemLogger.e(HaronConstants.TAG, "DnD cloud download failed: $fileName: ${e.message}")
                }
            }

            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
            updateProgress(OperationProgress(
                downloaded, total, "", OperationType.DOWNLOAD, isComplete = true, id = progressId
            ))
            _toastMessage.tryEmit(appContext.getString(R.string.cloud_download_complete, downloaded))

            delay(2000)
            removeProgress(progressId)
        }
    }

    /**
     * DnD: local → cloud — upload dragged local files into cloud destination folder.
     */
    private fun executeDragCloudUpload(
        paths: List<String>,
        destinationDir: String,
        sourcePanelId: PanelId,
        targetPanelId: PanelId
    ) {
        EcosystemLogger.d(HaronConstants.TAG, "executeDragCloudUpload: called with ${paths.size} paths, dest=$destinationDir")
        val destParsed = cloudManager.parseCloudUri(destinationDir)
        if (destParsed == null) {
            EcosystemLogger.e(HaronConstants.TAG, "executeDragCloudUpload: invalid cloud URI: $destinationDir")
            return
        }
        val (provider, cloudDir) = destParsed
        EcosystemLogger.d(HaronConstants.TAG, "executeDragCloudUpload: provider=$provider, cloudDir=$cloudDir")

        // Collect all files recursively (flatten directories)
        val filesToUpload = mutableListOf<Pair<File, String>>() // localFile, relative dir
        for (path in paths) {
            val file = File(path)
            if (!file.exists()) continue
            if (file.isDirectory) {
                file.walkTopDown().forEach { f ->
                    if (f.isFile) {
                        val relDir = f.parentFile?.toRelativeString(file.parentFile!!)?.replace('\\', '/') ?: ""
                        filesToUpload.add(f to relDir)
                    }
                }
            } else {
                filesToUpload.add(file to "")
            }
        }
        val totalBytes = filesToUpload.sumOf { it.first.length() }
        var completedBytes = 0L

        val progressId = nextProgressId()
        launchCloudJob {
            val total = filesToUpload.size
            EcosystemLogger.d(HaronConstants.TAG, "executeDragCloudUpload: launching upload of $total files, totalBytes=$totalBytes")

            val createdFolders = mutableMapOf<String, String>()
            createdFolders[""] = cloudDir

            updateProgress(OperationProgress(0, total, "", OperationType.UPLOAD, id = progressId))

            var uploaded = 0
            for ((idx, pair) in filesToUpload.withIndex()) {
                val (file, relDir) = pair
                val fileName = file.name

                updateProgress(OperationProgress(
                    idx + 1, total, fileName, OperationType.UPLOAD,
                    filePercent = if (totalBytes > 0) ((completedBytes * 100) / totalBytes).toInt() else 0,
                    id = progressId
                ))

                try {
                    val targetCloudDir = ensureCloudFolderPath(destParsed.accountId, cloudDir, relDir, createdFolders)
                    cloudUploadMutex.withLock {
                        cloudManager.uploadFile(destParsed.accountId, file.absolutePath, targetCloudDir, fileName)
                            .collect { progress ->
                                val overallPercent = if (totalBytes > 0) {
                                    ((completedBytes + progress.bytesTransferred) * 100 / totalBytes).toInt()
                                } else 0
                                updateProgress(OperationProgress(
                                    idx + 1, total, fileName, OperationType.UPLOAD,
                                    filePercent = overallPercent.coerceIn(0, 100),
                                    id = progressId,
                                    speedBytesPerSec = progress.speedBytesPerSec
                                ))
                            }
                    }
                    completedBytes += file.length()
                    uploaded++
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    completedBytes += file.length()
                    EcosystemLogger.e(HaronConstants.TAG, "DnD cloud upload failed: $fileName: ${e.message}")
                }
            }
            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)

            updateProgress(OperationProgress(
                uploaded, total, "", OperationType.UPLOAD, isComplete = true, id = progressId
            ))
            _toastMessage.tryEmit(appContext.getString(R.string.cloud_upload_complete, uploaded))

            delay(2000)
            removeProgress(progressId)
        }
    }

    // --- Cloud DnD with conflict decisions ---

    /**
     * Generate unique name for cloud upload when existing names collide.
     */
    private fun generateCloudUniqueName(name: String, existingNames: Set<String>): String {
        if (name !in existingNames) return name
        val baseName = name.substringBeforeLast('.', name)
        val ext = if ('.' in name) ".${name.substringAfterLast('.')}" else ""
        var counter = 1
        while ("${baseName}($counter)$ext" in existingNames) counter++
        return "${baseName}($counter)$ext"
    }

    /**
     * DnD: local → cloud — upload with conflict decisions (SKIP/REPLACE/RENAME).
     */
    private fun executeDragCloudUploadWithDecisions(
        paths: List<String>,
        destinationDir: String,
        sourcePanelId: PanelId,
        targetPanelId: PanelId,
        decisions: Map<String, ConflictResolution>
    ) {
        val destParsed = cloudManager.parseCloudUri(destinationDir) ?: return
        val (provider, cloudDir) = destParsed
        val targetPanel = getPanel(targetPanelId)
        val existingCloudFiles = targetPanel.files.associateBy { it.name }
        val existingNames = existingCloudFiles.keys.toMutableSet()

        EcosystemLogger.d(HaronConstants.TAG, "executeDragCloudUploadWithDecisions: ${paths.size} paths, decisions=$decisions")

        // Build upload plan: (localFile, uploadName, needsDeleteExisting)
        data class UploadEntry(val file: File, val uploadName: String, val deleteCloudPath: String?)
        val uploadPlan = paths.mapNotNull { path ->
            val file = File(path)
            if (!file.exists() || file.isDirectory) return@mapNotNull null
            val resolution = decisions[path] ?: ConflictResolution.RENAME
            EcosystemLogger.d(HaronConstants.TAG, "  file=${file.name}, resolution=$resolution")
            when (resolution) {
                ConflictResolution.SKIP -> null
                ConflictResolution.REPLACE -> {
                    val existing = existingCloudFiles[file.name]
                    UploadEntry(file, file.name, existing?.path)
                }
                ConflictResolution.RENAME -> {
                    val uniqueName = generateCloudUniqueName(file.name, existingNames)
                    existingNames.add(uniqueName)
                    UploadEntry(file, uniqueName, null)
                }
            }
        }

        if (uploadPlan.isEmpty()) return

        val totalBytes = uploadPlan.sumOf { it.file.length() }
        var completedBytes = 0L

        val progressId = nextProgressId()
        launchCloudJob {
            val total = uploadPlan.size
            updateProgress(OperationProgress(0, total, "", OperationType.UPLOAD, id = progressId))

            // Pre-delete existing cloud files for REPLACE decisions
            for (entry in uploadPlan) {
                val deletePath = entry.deleteCloudPath ?: continue
                val parsed = cloudManager.parseCloudUri(deletePath) ?: continue
                cloudManager.delete(parsed.accountId, parsed.path)
                    .onSuccess { EcosystemLogger.d(HaronConstants.TAG, "  pre-deleted cloud file for replace: ${entry.uploadName}") }
                    .onFailure { e -> EcosystemLogger.e(HaronConstants.TAG, "  pre-delete failed: ${entry.uploadName}: ${e.message}") }
            }

            var uploaded = 0
            for ((idx, uploadEntry) in uploadPlan.withIndex()) {
                val file = uploadEntry.file
                val uploadName = uploadEntry.uploadName
                val fileSize = file.length()

                updateProgress(OperationProgress(
                    idx + 1, total, uploadName, OperationType.UPLOAD,
                    filePercent = if (totalBytes > 0) ((completedBytes * 100) / totalBytes).toInt() else 0,
                    id = progressId
                ))

                try {
                    cloudUploadMutex.withLock {
                        cloudManager.uploadFile(destParsed.accountId, file.absolutePath, cloudDir, uploadName)
                            .collect { progress ->
                                val overallPercent = if (totalBytes > 0) {
                                    ((completedBytes + progress.bytesTransferred) * 100 / totalBytes).toInt()
                                } else 0
                                updateProgress(OperationProgress(
                                    idx + 1, total, uploadName, OperationType.UPLOAD,
                                    filePercent = overallPercent.coerceIn(0, 100),
                                    id = progressId,
                                    speedBytesPerSec = progress.speedBytesPerSec
                                ))
                            }
                    }
                    completedBytes += fileSize
                    uploaded++
                    refreshPanel(targetPanelId)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    completedBytes += fileSize
                    EcosystemLogger.e(HaronConstants.TAG, "DnD cloud upload (decisions) failed: $uploadName: ${e.message}")
                }
            }

            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
            updateProgress(OperationProgress(uploaded, total, "", OperationType.UPLOAD, isComplete = true, id = progressId))
            _toastMessage.tryEmit(appContext.getString(R.string.cloud_upload_complete, uploaded))
            delay(2000)
            removeProgress(progressId)
        }
    }

    /**
     * DnD: cloud → local — download with conflict decisions (SKIP/REPLACE/RENAME).
     */
    private fun executeDragCloudDownloadWithDecisions(
        paths: List<String>,
        destinationDir: String,
        sourcePanelId: PanelId,
        targetPanelId: PanelId,
        decisions: Map<String, ConflictResolution>
    ) {
        EcosystemLogger.d(HaronConstants.TAG, "executeDragCloudDownloadWithDecisions: ${paths.size} paths, decisions=$decisions")

        val sourcePanel = getPanel(sourcePanelId)
        val nameMap = sourcePanel.files.associateBy { it.path }

        // Build download plan: (cloudPath, localTargetFile)
        val downloadPlan = paths.mapNotNull { path ->
            val entry = nameMap[path]
            val fileName = entry?.name ?: return@mapNotNull null
            val resolution = decisions[path] ?: ConflictResolution.RENAME
            EcosystemLogger.d(HaronConstants.TAG, "  file=$fileName, resolution=$resolution")
            when (resolution) {
                ConflictResolution.SKIP -> null
                ConflictResolution.REPLACE -> {
                    // Download will overwrite existing local file
                    Triple(path, File(destinationDir, fileName), entry)
                }
                ConflictResolution.RENAME -> {
                    // Generate unique local name
                    val destDir = File(destinationDir)
                    val baseName = fileName.substringBeforeLast('.', fileName)
                    val ext = if ('.' in fileName) ".${fileName.substringAfterLast('.')}" else ""
                    var target = File(destDir, fileName)
                    var counter = 1
                    while (target.exists()) {
                        target = File(destDir, "${baseName}($counter)$ext")
                        counter++
                    }
                    Triple(path, target, entry)
                }
            }
        }

        if (downloadPlan.isEmpty()) return

        val totalBytes = downloadPlan.sumOf { it.third.size }
        var completedBytes = 0L

        val progressId = nextProgressId()
        launchCloudJob {
            val total = downloadPlan.size
            updateProgress(OperationProgress(0, total, "", OperationType.DOWNLOAD, id = progressId))

            var downloaded = 0
            for ((idx, entry) in downloadPlan.withIndex()) {
                val (cloudPath, localFile, fileEntry) = entry
                val parsed = cloudManager.parseCloudUri(cloudPath) ?: continue
                val (provider, cloudFileId) = parsed
                val fileSize = fileEntry.size

                updateProgress(OperationProgress(
                    idx + 1, total, localFile.name, OperationType.DOWNLOAD,
                    filePercent = if (totalBytes > 0) ((completedBytes * 100) / totalBytes).toInt() else 0,
                    id = progressId
                ))

                try {
                    cloudManager.downloadFile(parsed.accountId, cloudFileId, localFile.absolutePath)
                        .collect { progress ->
                            val overallPercent = if (totalBytes > 0) {
                                ((completedBytes + progress.bytesTransferred) * 100 / totalBytes).toInt()
                            } else 0
                            updateProgress(OperationProgress(
                                idx + 1, total, localFile.name, OperationType.DOWNLOAD,
                                filePercent = overallPercent.coerceIn(0, 100),
                                id = progressId
                            ))
                        }
                    completedBytes += fileSize
                    downloaded++
                    refreshPanel(targetPanelId)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    completedBytes += fileSize
                    EcosystemLogger.e(HaronConstants.TAG, "DnD cloud download (decisions) failed: ${localFile.name}: ${e.message}")
                }
            }

            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
            updateProgress(OperationProgress(downloaded, total, "", OperationType.DOWNLOAD, isComplete = true, id = progressId))
            _toastMessage.tryEmit(appContext.getString(R.string.cloud_download_complete, downloaded))
            delay(2000)
            removeProgress(progressId)
        }
    }

    /**
     * DnD: cloud → cloud — move with conflict decisions (SKIP/REPLACE/RENAME).
     * For now, delegates to non-decision version (skipping SKIP files).
     */
    private fun executeCloudDragMoveWithDecisions(
        paths: List<String>,
        destinationDir: String,
        sourcePanelId: PanelId,
        targetPanelId: PanelId,
        decisions: Map<String, ConflictResolution>
    ) {
        val filteredPaths = paths.filter { path ->
            val resolution = decisions[path] ?: ConflictResolution.RENAME
            resolution != ConflictResolution.SKIP
        }
        if (filteredPaths.isEmpty()) return
        // TODO: handle REPLACE (delete existing) and RENAME for cloud-to-cloud
        executeCloudDragMove(filteredPaths, destinationDir, sourcePanelId, targetPanelId)
    }

    // --- Conflict resolution (per-file card) ---

    private fun buildConflictPairs(paths: List<String>, targetPanel: PanelUiState): List<ConflictPair> {
        val destFiles = targetPanel.files.associateBy { it.name }
        // Try both panels to find source entries (activePanel may not match drag source)
        val topFiles = getPanel(PanelId.TOP).files.associateBy { it.path }
        val bottomFiles = getPanel(PanelId.BOTTOM).files.associateBy { it.path }
        val pairs = mutableListOf<ConflictPair>()

        for (path in paths) {
            val srcEntry = topFiles[path] ?: bottomFiles[path]
            val name = srcEntry?.name ?: when {
                path.startsWith("content://") ->
                    Uri.parse(path).lastPathSegment?.substringAfterLast('/') ?: continue
                path.startsWith("cloud://") -> continue // cloud source without entry — can't resolve name
                else -> File(path).name
            }
            val destEntry = destFiles[name] ?: continue

            val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
            val srcExt = srcEntry?.extension ?: name.substringAfterLast('.', "").lowercase()
            val dstExt = destEntry.extension

            pairs.add(
                ConflictPair(
                    source = ConflictFileInfo(
                        name = name,
                        path = path,
                        size = srcEntry?.size ?: 0L,
                        lastModified = srcEntry?.lastModified ?: 0L,
                        isImage = srcExt in imageExtensions
                    ),
                    destination = ConflictFileInfo(
                        name = destEntry.name,
                        path = destEntry.path,
                        size = destEntry.size,
                        lastModified = destEntry.lastModified,
                        isImage = dstExt in imageExtensions
                    )
                )
            )
        }
        return pairs
    }

    fun resolveCurrentConflict(resolution: ConflictResolution, applyToAll: Boolean) {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.ConfirmConflict) return

        val currentPair = dialog.conflictPairs[dialog.currentIndex]
        val newDecisions = dialog.decisions.toMutableMap()
        newDecisions[currentPair.source.path] = resolution

        if (applyToAll) {
            // Apply same resolution to all remaining conflicts
            for (i in dialog.currentIndex + 1..dialog.conflictPairs.lastIndex) {
                newDecisions[dialog.conflictPairs[i].source.path] = resolution
            }
            dismissDialog()
            // Add default RENAME for non-conflict files
            val allDecisions = dialog.allPaths.associateWith { path ->
                newDecisions[path] ?: ConflictResolution.RENAME
            }
            executeWithDecisions(dialog, allDecisions)
        } else {
            val nextIndex = dialog.currentIndex + 1
            if (nextIndex >= dialog.conflictPairs.size) {
                // All conflicts resolved
                dismissDialog()
                val allDecisions = dialog.allPaths.associateWith { path ->
                    newDecisions[path] ?: ConflictResolution.RENAME
                }
                executeWithDecisions(dialog, allDecisions)
            } else {
                // Show next conflict card
                _uiState.update {
                    it.copy(dialogState = dialog.copy(
                        currentIndex = nextIndex,
                        decisions = newDecisions
                    ))
                }
            }
        }
    }

    private fun executeWithDecisions(
        dialog: DialogState.ConfirmConflict,
        decisions: Map<String, ConflictResolution>
    ) {
        // If all files are SKIP — no-op, don't show progress bar
        if (decisions.values.all { it == ConflictResolution.SKIP }) {
            EcosystemLogger.d(HaronConstants.TAG, "executeWithDecisions: all files SKIP, no-op")
            return
        }
        when (dialog.operationType) {
            OperationType.COPY -> {
                if (dialog.sourcePanelId != null && dialog.targetPanelId != null) {
                    // DnD copy case — panel IDs saved in dialog
                    executeDragCopyWithDecisions(
                        dialog.allPaths, dialog.destinationDir,
                        dialog.sourcePanelId, dialog.targetPanelId, decisions
                    )
                } else {
                    executeCopyWithDecisions(dialog.allPaths, dialog.destinationDir, decisions)
                }
            }
            OperationType.MOVE -> {
                if (dialog.sourcePanelId != null && dialog.targetPanelId != null) {
                    // DnD case — panel IDs saved in dialog
                    executeDragMoveWithDecisions(
                        dialog.allPaths, dialog.destinationDir,
                        dialog.sourcePanelId, dialog.targetPanelId, decisions
                    )
                } else {
                    val state = _uiState.value
                    val activeId = state.activePanel
                    val sourcePanel = getPanel(activeId)
                    if (sourcePanel.selectedPaths.isNotEmpty()) {
                        executeMoveWithDecisions(dialog.allPaths, dialog.destinationDir, decisions)
                    } else {
                        // Fallback DnD case
                        val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
                        executeDragMoveWithDecisions(dialog.allPaths, dialog.destinationDir, activeId, targetId, decisions)
                    }
                }
            }
            else -> {}
        }
    }

    private fun executeDragMoveWithDecisions(
        paths: List<String>,
        destinationDir: String,
        sourcePanelId: PanelId,
        targetPanelId: PanelId,
        decisions: Map<String, ConflictResolution>
    ) {
        // Cloud routing with decisions support
        if (paths.any { isCloudPath(it) } && isCloudPath(destinationDir)) {
            executeCloudDragMoveWithDecisions(paths, destinationDir, sourcePanelId, targetPanelId, decisions)
            return
        }
        if (paths.any { isCloudPath(it) } && !isCloudPath(destinationDir)) {
            executeDragCloudDownloadWithDecisions(paths, destinationDir, sourcePanelId, targetPanelId, decisions)
            return
        }
        if (!paths.any { isCloudPath(it) } && isCloudPath(destinationDir)) {
            executeDragCloudUploadWithDecisions(paths, destinationDir, sourcePanelId, targetPanelId, decisions)
            return
        }

        val total = paths.size
        viewModelScope.launch {
            var completed = 0
            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, "", OperationType.MOVE))
            }
            for ((index, path) in paths.withIndex()) {
                val fileName = extractFileName(path)
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(index, total, fileName, OperationType.MOVE))
                }
                val resolution = decisions[path] ?: ConflictResolution.RENAME
                fileRepository.moveFilesWithResolutions(
                    listOf(path), destinationDir, mapOf(path to resolution)
                ).onSuccess { c -> completed += c }
            }
            _uiState.update {
                it.copy(operationProgress = OperationProgress(completed, total, "", OperationType.MOVE, isComplete = true))
            }
            refreshPanel(sourcePanelId)
            refreshPanel(targetPanelId)
            if (completed > 0) hapticManager.success() else hapticManager.error()
            viewModelScope.launch {
                delay(2000)
                _uiState.update {
                    if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it
                }
            }
        }
    }

    /** Legacy single-resolution conflict handler (kept for backward compatibility with old dialog code paths) */
    fun confirmConflict(resolution: ConflictResolution) {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.ConfirmConflict) return
        // Apply same resolution to all
        resolveCurrentConflict(resolution, applyToAll = true)
    }

    // --- SAF display helpers ---

    private fun buildSafDisplayPath(path: String): String {
        val uri = Uri.parse(path)
        return try {
            val docId = android.provider.DocumentsContract.getDocumentId(uri)
            val treeDocId = android.provider.DocumentsContract.getTreeDocumentId(uri)
            val parts = docId.split(":")
            val treeParts = treeDocId.split(":")
            val relativePath = if (parts.size >= 2) parts[1] else ""
            val treeBasePath = if (treeParts.size >= 2) treeParts[1] else ""
            val suffix = if (relativePath.length > treeBasePath.length) {
                relativePath.removePrefix(treeBasePath).trimStart('/')
            } else {
                ""
            }
            if (suffix.isEmpty()) "/" else "/$suffix"
        } catch (_: Exception) {
            "/"
        }
    }

    // --- Helpers ---

    private fun updateTrashSizeInfo() {
        viewModelScope.launch {
            val trashSize = trashRepository.getTrashSize()
            val maxMb = preferences.trashMaxSizeMb
            val info = if (maxMb > 0) {
                val maxBytes = maxMb.toLong() * 1024 * 1024
                "${trashSize.toFileSize(appContext)} / ${maxBytes.toFileSize(appContext)}"
            } else {
                trashSize.toFileSize(appContext)
            }
            _uiState.update { it.copy(trashSizeInfo = info) }
        }
    }

    fun getSelectedTotalSize(): Long {
        val state = _uiState.value
        val activeId = state.activePanel
        val panel = getPanel(activeId)
        return panel.files
            .filter { it.path in panel.selectedPaths }
            .sumOf { it.size }
    }

    fun refreshPanel(panelId: PanelId) {
        val panel = getPanel(panelId)
        // Invalidate cached folder size so BreadcrumbBar shows fresh value
        val cachedPath = panel.currentPath
        if (cachedPath.isNotEmpty()) {
            folderSizeJobs[cachedPath]?.cancel()
            folderSizeJobs.remove(cachedPath)
            _uiState.update { state ->
                state.copy(folderSizeCache = state.folderSizeCache - cachedPath)
            }
        }
        if (panel.isArchiveMode) {
            navigateIntoArchive(panelId, panel.archivePath!!, panel.archiveVirtualPath, panel.archivePassword, pushHistory = false)
            return
        }
        if (cachedPath.isNotEmpty()) {
            navigateTo(panelId, cachedPath, pushHistory = false)
        }
    }

    // --- Cursor (Arrow navigation) ---
    // Cursor = selection: moving cursor selects the file under it

    fun moveFocusUp(panelId: PanelId) {
        val panel = getPanel(panelId)
        val fileCount = panel.files.size
        if (fileCount == 0) return
        val current = panel.focusedIndex
        val newIndex = if (current <= 0) {
            if (current < 0) startIndexForPanel(panelId, fileCount) else fileCount - 1
        } else current - 1
        applyCursorMove(panelId, newIndex, directionDown = false)
    }

    fun moveFocusDown(panelId: PanelId) {
        val panel = getPanel(panelId)
        val fileCount = panel.files.size
        if (fileCount == 0) return
        val current = panel.focusedIndex
        val newIndex = if (current < 0) {
            startIndexForPanel(panelId, fileCount)
        } else if (current >= fileCount - 1) 0 else current + 1
        applyCursorMove(panelId, newIndex, directionDown = true)
    }

    fun moveFocusLeft(panelId: PanelId) {
        val panel = getPanel(panelId)
        val fileCount = panel.files.size
        if (fileCount == 0) return
        val current = panel.focusedIndex
        val newIndex = if (current <= 0) {
            if (current < 0) startIndexForPanel(panelId, fileCount) else fileCount - 1
        } else current - 1
        applyCursorMove(panelId, newIndex, directionDown = false)
    }

    fun moveFocusRight(panelId: PanelId) {
        val panel = getPanel(panelId)
        val fileCount = panel.files.size
        if (fileCount == 0) return
        val current = panel.focusedIndex
        val newIndex = if (current < 0) {
            startIndexForPanel(panelId, fileCount)
        } else if (current >= fileCount - 1) 0 else current + 1
        applyCursorMove(panelId, newIndex, directionDown = true)
    }

    /** Start cursor at the first visible file, not at index 0 */
    private fun startIndexForPanel(panelId: PanelId, fileCount: Int): Int {
        val scrollIdx = panelScrollIndex[panelId] ?: 0
        return scrollIdx.coerceIn(0, fileCount - 1)
    }

    fun toggleShiftMode(panelId: PanelId) {
        val panel = getPanel(panelId)
        if (panel.shiftMode) {
            // Shift OFF — lock current selection, keep cursor file selected
            updatePanel(panelId) {
                it.copy(
                    shiftMode = false,
                    shiftAnchor = -1,
                    lockedPaths = it.selectedPaths // lock everything currently selected
                )
            }
        } else {
            // Shift ON — set anchor at current cursor
            val anchor = if (panel.focusedIndex >= 0) panel.focusedIndex else 0
            updatePanel(panelId) { it.copy(shiftMode = true, shiftAnchor = anchor) }
        }
    }

    private fun applyCursorMove(panelId: PanelId, newIndex: Int, directionDown: Boolean) {
        val panel = getPanel(panelId)
        val file = panel.files.getOrNull(newIndex) ?: return
        val cols = panel.gridColumns.coerceAtLeast(1)
        val isFirstTap = panel.focusedIndex < 0
        val scrollIdx = panelScrollIndex[panelId] ?: 0

        // Always scroll to the cursor file itself
        val scrollTarget = newIndex

        // Selection logic
        val newSelection = if (panel.shiftMode && panel.shiftAnchor >= 0) {
            // Shift mode: range from anchor to cursor + locked paths
            val from = minOf(panel.shiftAnchor, newIndex)
            val to = maxOf(panel.shiftAnchor, newIndex)
            val rangeSelection = (from..to).mapNotNull { i -> panel.files.getOrNull(i)?.path }.toSet()
            panel.lockedPaths + rangeSelection
        } else {
            // Normal: locked paths + cursor file only
            panel.lockedPaths + file.path
        }

        updatePanel(panelId) {
            it.copy(
                focusedIndex = newIndex,
                selectedPaths = newSelection,
                isSelectionMode = if (isFirstTap) it.isSelectionMode else newSelection.isNotEmpty(),
                scrollToIndex = scrollTarget,
                scrollToTrigger = System.currentTimeMillis()
            )
        }
    }

    fun clearFocus(panelId: PanelId) {
        updatePanel(panelId) { it.copy(focusedIndex = -1, shiftMode = false, shiftAnchor = -1, lockedPaths = emptySet()) }
    }

    /** Get the focused file entry (for actions when no selection) */
    fun getFocusedFile(panelId: PanelId): com.vamp.haron.domain.model.FileEntry? {
        val panel = getPanel(panelId)
        val idx = panel.focusedIndex
        return if (idx in panel.files.indices) panel.files[idx] else null
    }

    private fun refreshBothIfSamePath(panelId: PanelId) {
        // Invalidate content index cache — files changed in this folder
        val path = getPanel(panelId).currentPath
        if (path.isNotEmpty()) preferences.removeContentIndexedFolder(path)
        val otherId = if (panelId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        val otherPath = getPanel(otherId).currentPath
        if (otherPath.isNotEmpty()) preferences.removeContentIndexedFolder(otherPath)
        // Always refresh both panels to ensure UI shows real state
        refreshPanel(PanelId.TOP)
        refreshPanel(PanelId.BOTTOM)
    }

    // --- Inline Archive Browsing ---

    fun navigateIntoArchive(panelId: PanelId, archivePath: String, virtualPath: String, password: String?, pushHistory: Boolean = true) {
        // Save scroll position when entering archive from regular folder
        val panel = getPanel(panelId)
        if (!panel.isArchiveMode && panel.currentPath.isNotEmpty()) {
            scrollCache[panel.currentPath] = panelScrollIndex[panelId] ?: 0
        }
        navigationJobs[panelId]?.cancel()
        navigationJobs[panelId] = viewModelScope.launch {
            updatePanel(panelId) { it.copy(isLoading = true, error = null) }
            browseArchiveUseCase(archivePath, virtualPath, password).onSuccess { entries ->
                val archiveName = File(archivePath).name
                val displayPath = if (virtualPath.isEmpty()) archiveName else "$archiveName/$virtualPath"
                val fileEntries = entries.map { ae ->
                    FileEntry(
                        name = ae.name,
                        path = "$archivePath!/${ae.fullPath}",
                        isDirectory = ae.isDirectory,
                        size = ae.size,
                        lastModified = ae.lastModified,
                        extension = ae.name.substringAfterLast('.', ""),
                        isHidden = false,
                        childCount = ae.childCount
                    )
                }
                updatePanel(panelId) { panel ->
                    var history = panel.navigationHistory
                    var index = panel.historyIndex
                    if (pushHistory) {
                        val historyKey = "$archivePath!/$virtualPath"
                        history = history.take(index + 1) + historyKey
                        if (history.size > MAX_HISTORY_SIZE) {
                            history = history.takeLast(MAX_HISTORY_SIZE)
                        }
                        index = history.lastIndex
                    }
                    panel.copy(
                        files = fileEntries,
                        isLoading = false,
                        error = null,
                        archivePath = archivePath,
                        archiveVirtualPath = virtualPath,
                        archivePassword = password,
                        displayPath = displayPath,
                        currentPath = panel.currentPath, // keep real path for extraction target
                        selectedPaths = emptySet(),
                        isSelectionMode = false,
                        navigationHistory = history,
                        historyIndex = index,
                        searchQuery = "",
                        isSearchActive = false
                    )
                }
            }.onFailure { error ->
                val message = error.message ?: ""
                if (message == "encrypted" || message.contains("password", ignoreCase = true)) {
                    // Show password dialog
                    updatePanel(panelId) { it.copy(isLoading = false) }
                    val errorMsg = if (password != null) appContext.getString(R.string.wrong_password) else null
                    _uiState.update {
                        it.copy(dialogState = DialogState.ArchivePassword(
                            panelId = panelId,
                            archivePath = archivePath,
                            errorMessage = errorMsg
                        ))
                    }
                } else {
                    updatePanel(panelId) {
                        it.copy(
                            isLoading = false,
                            error = message.ifEmpty { appContext.getString(R.string.archive_read_error) }
                        )
                    }
                }
            }
        }
    }

    fun onArchivePasswordSubmit(panelId: PanelId, archivePath: String, password: String) {
        _uiState.update { it.copy(dialogState = DialogState.None) }
        navigateIntoArchive(panelId, archivePath, "", password)
    }

    fun onBreadcrumbClick(panelId: PanelId, segmentPath: String) {
        val panel = getPanel(panelId)
        if (panel.isArchiveMode) {
            // If user tapped on root "Storage" — exit archive mode
            if (segmentPath == HaronConstants.ROOT_PATH) {
                EcosystemLogger.d(HaronConstants.TAG, "onBreadcrumbClick: tapped root in archive mode, exiting archive")
                val archiveParent = File(panel.archivePath!!).parent ?: HaronConstants.ROOT_PATH
                updatePanel(panelId) { it.copy(archivePath = null, archiveVirtualPath = "", archivePassword = null, archiveExtractProgress = null, currentPath = "") }
                navigateTo(panelId, archiveParent)
                return
            }
            // segmentPath is built by BreadcrumbBar as ROOT_PATH + /segments
            // In archive mode, displayPath = "archiveName/subfolder/..."
            // Segments: [archiveName, folder, subfolder, ...]
            val afterRoot = segmentPath.removePrefix(HaronConstants.ROOT_PATH).trimStart('/')
            val parts = afterRoot.split('/').filter { it.isNotEmpty() }
            // First part = archive name → skip it, rest = virtual path
            val virtualPath = if (parts.size <= 1) "" else parts.drop(1).joinToString("/")
            navigateIntoArchive(panelId, panel.archivePath!!, virtualPath, panel.archivePassword)
        } else {
            navigateTo(panelId, segmentPath)
        }
    }

    fun extractFromArchive(archivePanelId: PanelId, selectedOnly: Boolean = true) {
        val archivePanel = getPanel(archivePanelId)
        if (!archivePanel.isArchiveMode) return

        val activeId = _uiState.value.activePanel
        val destinationDir = if (activeId == archivePanelId) {
            File(archivePanel.archivePath!!).parent ?: HaronConstants.ROOT_PATH
        } else {
            getPanel(activeId).currentPath
        }

        val archiveName = File(archivePanel.archivePath!!).nameWithoutExtension

        EcosystemLogger.d(HaronConstants.TAG, "extractFromArchive: archiveName=$archiveName, " +
                "virtualPath='${archivePanel.archiveVirtualPath}', files=${archivePanel.files.size}, " +
                "selectedOnly=$selectedOnly, dest=$destinationDir")

        _uiState.update {
            it.copy(dialogState = DialogState.ArchiveExtractOptions(
                archivePanelId = archivePanelId,
                destinationDir = destinationDir,
                selectedOnly = selectedOnly,
                archiveName = archiveName,
                hasSingleRootFolder = false
            ))
        }
    }

    fun confirmExtractHere(dialog: DialogState.ArchiveExtractOptions) {
        dismissDialog()
        if (dialog.isFromNormalFolder) {
            doExtractSelectedArchiveFiles(dialog.archivePanelId, dialog.archivePaths, dialog.destinationDir)
        } else {
            checkExtractConflictsAndProceed(dialog.archivePanelId, dialog.destinationDir, dialog.selectedOnly)
        }
    }

    fun confirmExtractToFolder(dialog: DialogState.ArchiveExtractOptions) {
        dismissDialog()
        val subFolder = File(dialog.destinationDir, dialog.archiveName)
        if (!subFolder.exists()) subFolder.mkdirs()
        EcosystemLogger.d(HaronConstants.TAG, "confirmExtractToFolder: extracting to subfolder ${subFolder.absolutePath}")
        if (dialog.isFromNormalFolder) {
            doExtractSelectedArchiveFiles(dialog.archivePanelId, dialog.archivePaths, subFolder.absolutePath)
        } else {
            checkExtractConflictsAndProceed(dialog.archivePanelId, subFolder.absolutePath, dialog.selectedOnly)
        }
    }

    private fun checkExtractConflictsAndProceed(archivePanelId: PanelId, destinationDir: String, selectedOnly: Boolean) {
        val archivePanel = getPanel(archivePanelId)

        val archiveFileNames = if (selectedOnly && archivePanel.selectedPaths.isNotEmpty()) {
            archivePanel.files.filter { it.path in archivePanel.selectedPaths }.map { it.name }
        } else {
            archivePanel.files.filter { !it.isDirectory }.map { it.name }
        }
        val destDir = File(destinationDir)
        val conflictNames = archiveFileNames.filter { File(destDir, it).exists() }

        if (conflictNames.isNotEmpty()) {
            _uiState.update {
                it.copy(dialogState = DialogState.ArchiveExtractConflict(
                    archivePanelId = archivePanelId,
                    destinationDir = destinationDir,
                    conflictNames = conflictNames,
                    selectedOnly = selectedOnly
                ))
            }
            return
        }

        doExtractFromArchive(archivePanelId, destinationDir, selectedOnly)
    }

    fun confirmArchiveExtract(archivePanelId: PanelId, destinationDir: String, selectedOnly: Boolean) {
        _uiState.update { it.copy(dialogState = DialogState.None) }
        doExtractFromArchive(archivePanelId, destinationDir, selectedOnly)
    }

    private fun doExtractFromArchive(archivePanelId: PanelId, destinationDir: String, selectedOnly: Boolean) {
        val archivePanel = getPanel(archivePanelId)
        if (!archivePanel.isArchiveMode) return
        val virtualPath = archivePanel.archiveVirtualPath

        val selectedEntries = if (selectedOnly && archivePanel.selectedPaths.isNotEmpty()) {
            archivePanel.selectedPaths.map { path ->
                path.substringAfter("!/", "")
            }.toSet()
        } else {
            archivePanel.files.map { fe ->
                fe.path.substringAfter("!/", "")
            }.toSet()
        }

        updatePanel(archivePanelId) {
            it.copy(selectedPaths = emptySet(), isSelectionMode = false)
        }
        FileOperationService.startExtract(
            appContext,
            archivePath = archivePanel.archivePath!!,
            destDir = destinationDir,
            selectedEntries = selectedEntries,
            password = archivePanel.archivePassword,
            basePrefix = virtualPath
        )
    }

    /** Extract selected archive file(s) from normal folder view into other panel. */
    private fun extractSelectedArchiveFiles(panelId: PanelId) {
        val panel = getPanel(panelId)
        val archiveExts = com.vamp.haron.common.util.ContentExtractor.ARCHIVE_EXTENSIONS
        val selectedArchives = panel.files
            .filter { it.path in panel.selectedPaths && it.name.substringAfterLast('.').lowercase() in archiveExts }
        if (selectedArchives.isEmpty()) {
            _toastMessage.tryEmit(appContext.getString(R.string.not_in_archive))
            return
        }
        val otherId = if (panelId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        val destDir = getPanel(otherId).currentPath.ifEmpty { panel.currentPath }

        // For single archive — always show extract options dialog
        if (selectedArchives.size == 1) {
            val archive = selectedArchives[0]
            val archiveName = File(archive.path).nameWithoutExtension
            _uiState.update {
                it.copy(dialogState = DialogState.ArchiveExtractOptions(
                    archivePanelId = panelId,
                    destinationDir = destDir,
                    selectedOnly = false,
                    archiveName = archiveName,
                    hasSingleRootFolder = false,
                    isFromNormalFolder = true,
                    archivePaths = listOf(archive.path)
                ))
            }
        } else {
            // Multiple archives — extract all directly without dialog
            doExtractSelectedArchiveFiles(panelId, selectedArchives.map { it.path }, destDir)
        }
    }

    private suspend fun checkSingleRootFolder(archivePath: String): Boolean {
        val result = browseArchiveUseCase(archivePath, "")
        return result.getOrNull()?.let { entries ->
            entries.size == 1 && entries[0].isDirectory
        } ?: false
    }

    private fun doExtractSelectedArchiveFiles(panelId: PanelId, archivePaths: List<String>, destDir: String) {
        updatePanel(panelId) {
            it.copy(selectedPaths = emptySet(), isSelectionMode = false)
        }
        // Extract each archive via service (sequentially — one at a time)
        // For multiple archives we extract the first one; completion handler in init{} will refresh panels
        for (archivePath in archivePaths) {
            FileOperationService.startExtract(appContext, archivePath, destDir)
        }
    }

    // --- Self-copy (duplicate) ---

    fun showDuplicateDialog() {
        EcosystemLogger.d(HaronConstants.TAG, "showDuplicateDialog: called")
        val state = _uiState.value
        val activeId = state.activePanel
        val sourcePanel = getPanel(activeId)
        val paths = sourcePanel.selectedPaths.toList()
        EcosystemLogger.d(HaronConstants.TAG, "showDuplicateDialog: activePanel=$activeId, paths=${paths.size}")
        if (paths.isEmpty()) return
        _uiState.update {
            it.copy(dialogState = DialogState.DuplicateDialog(
                paths = paths,
                sourcePanelId = activeId
            ))
        }
    }

    fun executeDuplicate(count: Int, destination: DuplicateDestination) {
        val state = _uiState.value
        val dialog = state.dialogState as? DialogState.DuplicateDialog ?: return
        val sourcePanelId = dialog.sourcePanelId
        val paths = dialog.paths
        val sourcePanel = getPanel(sourcePanelId)
        val targetPanelId = if (sourcePanelId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        val targetPanel = getPanel(targetPanelId)

        dismissDialog()
        clearSelection(sourcePanelId)

        // Determine which panel(s) will show new files
        val affectedPanelId = when (destination) {
            DuplicateDestination.SAME_SUBFOLDER -> sourcePanelId
            DuplicateDestination.OTHER_PANEL_SUBFOLDER, DuplicateDestination.OTHER_PANEL_DIRECT -> targetPanelId
        }
        val createsSubfolder = destination != DuplicateDestination.OTHER_PANEL_DIRECT

        viewModelScope.launch {
            // Track created subfolders → accumulated size for real-time cache updates
            val subfolderSizes = mutableMapOf<String, Long>()

            withContext(Dispatchers.IO) {
                for (srcPath in paths) {
                    val srcFile = File(srcPath)
                    val nameNoExt = srcFile.nameWithoutExtension
                    val ext = srcFile.extension
                    val isDir = srcFile.isDirectory
                    val srcSize = if (isDir) {
                        srcFile.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    } else {
                        srcFile.length()
                    }

                    val destBaseDir = when (destination) {
                        DuplicateDestination.SAME_SUBFOLDER -> {
                            val subDir = File(sourcePanel.currentPath, if (isDir) srcFile.name else nameNoExt)
                            subDir.mkdirs()
                            subDir.absolutePath
                        }
                        DuplicateDestination.OTHER_PANEL_SUBFOLDER -> {
                            val subDir = File(targetPanel.currentPath, if (isDir) srcFile.name else nameNoExt)
                            subDir.mkdirs()
                            subDir.absolutePath
                        }
                        DuplicateDestination.OTHER_PANEL_DIRECT -> {
                            targetPanel.currentPath
                        }
                    }

                    for (i in 1..count) {
                        val copyName = if (isDir) {
                            "${srcFile.name}($i)"
                        } else {
                            if (ext.isNotEmpty()) "$nameNoExt($i).$ext" else "$nameNoExt($i)"
                        }
                        val destPath = File(destBaseDir, copyName).absolutePath
                        try {
                            if (isDir) {
                                srcFile.copyRecursively(File(destPath), overwrite = false)
                            } else {
                                srcFile.copyTo(File(destPath), overwrite = false)
                            }
                            // Update subfolder size in real time
                            if (createsSubfolder) {
                                val accumulated = (subfolderSizes[destBaseDir] ?: 0L) + srcSize
                                subfolderSizes[destBaseDir] = accumulated
                            }
                        } catch (e: Exception) {
                            EcosystemLogger.e(HaronConstants.TAG, "executeDuplicate: failed to copy $srcPath -> $destPath: ${e.message}")
                        }
                    }
                }
            }
            // Refresh both panels to ensure UI shows real state
            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
            for ((folder, size) in subfolderSizes) {
                _uiState.update { s ->
                    s.copy(folderSizeCache = s.folderSizeCache + (folder to size))
                }
            }
            hapticManager.success()
            _toastMessage.tryEmit(appContext.getString(R.string.duplicate_create) + ": $count")
        }
    }

    // --- Extract archives to subfolders ---

    fun showExtractArchivesDialog(panelId: PanelId) {
        val panel = getPanel(panelId)
        val archiveExts = com.vamp.haron.common.util.ContentExtractor.ARCHIVE_EXTENSIONS
        val selectedArchives = panel.files
            .filter { it.path in panel.selectedPaths && it.name.substringAfterLast('.').lowercase() in archiveExts }
        if (selectedArchives.isEmpty()) {
            _toastMessage.tryEmit(appContext.getString(R.string.not_in_archive))
            return
        }
        _uiState.update {
            it.copy(dialogState = DialogState.ExtractArchivesDialog(
                archivePaths = selectedArchives.map { a -> a.path },
                sourcePanelId = panelId
            ))
        }
    }

    fun executeExtractArchives(destination: ExtractDestination) {
        val state = _uiState.value
        val dialog = state.dialogState as? DialogState.ExtractArchivesDialog ?: return
        val panelId = dialog.sourcePanelId
        val archivePaths = dialog.archivePaths
        val otherId = if (panelId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP

        dismissDialog()
        clearSelection(panelId)

        viewModelScope.launch {
            for (archivePath in archivePaths) {
                val archiveFile = File(archivePath)
                val archiveName = archiveFile.nameWithoutExtension

                val baseDir = when (destination) {
                    ExtractDestination.NEXT_TO_ARCHIVE ->
                        archiveFile.parent ?: getPanel(panelId).currentPath
                    ExtractDestination.SAME_PANEL ->
                        getPanel(panelId).currentPath
                    ExtractDestination.OTHER_PANEL ->
                        getPanel(otherId).currentPath
                }
                val destDir = File(baseDir, archiveName).absolutePath
                withContext(Dispatchers.IO) { File(destDir).mkdirs() }

                FileOperationService.startExtract(appContext, archivePath, destDir)
            }
        }
    }

    private fun invalidateFolderSizeCache(panelId: PanelId) {
        val path = getPanel(panelId).currentPath
        if (path.isNotEmpty()) {
            folderSizeJobs[path]?.cancel()
            folderSizeJobs.remove(path)
            _uiState.update { state ->
                state.copy(folderSizeCache = state.folderSizeCache - path)
            }
        }
    }

    private fun showStatusMessage(panelId: PanelId, message: String) {
        updatePanel(panelId) { it.copy(statusMessage = message) }
        viewModelScope.launch {
            delay(5000)
            updatePanel(panelId) { it.copy(statusMessage = null) }
        }
    }

    private fun extractFileName(path: String): String {
        return if (path.startsWith("content://")) {
            Uri.parse(path).lastPathSegment?.substringAfterLast('/') ?: "file"
        } else {
            path.substringAfterLast('/')
        }
    }

    private fun formatFileCount(dirs: Int, files: Int): String {
        val parts = buildList {
            if (dirs > 0) add("$dirs ${pluralDirs(dirs)}")
            if (files > 0) add("$files ${pluralFiles(files)}")
        }
        return parts.joinToString(appContext.getString(R.string.and_conjunction)).ifEmpty { appContext.getString(R.string.zero_files) }
    }

    private fun pluralDirs(count: Int): String {
        val mod100 = count % 100
        val mod10 = count % 10
        return when {
            mod100 in 11..14 -> appContext.getString(R.string.plural_folders_genitive)
            mod10 == 1 -> appContext.getString(R.string.plural_folders_nom)
            mod10 in 2..4 -> appContext.getString(R.string.plural_folders_gen_few)
            else -> appContext.getString(R.string.plural_folders_genitive)
        }
    }

    private fun pluralFiles(count: Int): String {
        val mod100 = count % 100
        val mod10 = count % 10
        return when {
            mod100 in 11..14 -> appContext.getString(R.string.plural_files_genitive)
            mod10 == 1 -> appContext.getString(R.string.plural_files_nom)
            mod10 in 2..4 -> appContext.getString(R.string.plural_files_gen_few)
            else -> appContext.getString(R.string.plural_files_genitive)
        }
    }

    private fun getPanel(panelId: PanelId): PanelUiState {
        return when (panelId) {
            PanelId.TOP -> _uiState.value.topPanel
            PanelId.BOTTOM -> _uiState.value.bottomPanel
        }
    }

    private fun updatePanel(panelId: PanelId, transform: (PanelUiState) -> PanelUiState) {
        _uiState.update { state ->
            when (panelId) {
                PanelId.TOP -> state.copy(topPanel = transform(state.topPanel))
                PanelId.BOTTOM -> state.copy(bottomPanel = transform(state.bottomPanel))
            }
        }
    }

    // --- File Properties ---

    fun showFileProperties(entry: FileEntry) {
        _uiState.update { it.copy(dialogState = DialogState.FilePropertiesState(entry = entry)) }
        viewModelScope.launch {
            getFilePropertiesUseCase(entry).collect { props ->
                _uiState.update { state ->
                    val dialog = state.dialogState
                    if (dialog is DialogState.FilePropertiesState && dialog.entry.path == entry.path) {
                        state.copy(dialogState = dialog.copy(properties = props))
                    } else state
                }
            }
        }
    }

    fun showSelectedFileProperties() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val selected = panel.files.filter { it.path in panel.selectedPaths }
        if (selected.size == 1) {
            showFileProperties(selected.first())
        }
    }

    fun calculateHash() {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.FilePropertiesState) return
        val entry = dialog.entry

        _uiState.update { state ->
            val d = state.dialogState
            if (d is DialogState.FilePropertiesState) {
                state.copy(dialogState = d.copy(isHashCalculating = true))
            } else state
        }

        viewModelScope.launch {
            calculateHashUseCase(entry.path).collect { hash ->
                _uiState.update { state ->
                    val d = state.dialogState
                    if (d is DialogState.FilePropertiesState && d.entry.path == entry.path) {
                        state.copy(dialogState = d.copy(hashResult = hash))
                    } else state
                }
            }
            _uiState.update { state ->
                val d = state.dialogState
                if (d is DialogState.FilePropertiesState) {
                    state.copy(dialogState = d.copy(isHashCalculating = false))
                } else state
            }
        }
    }

    fun removeExif() {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.FilePropertiesState) return
        val entry = dialog.entry

        viewModelScope.launch {
            val success = getFilePropertiesUseCase.removeExif(entry.path)
            if (success) {
                _toastMessage.tryEmit(appContext.getString(R.string.exif_data_removed))
                // Reload properties to reflect removal
                showFileProperties(entry)
            } else {
                _toastMessage.tryEmit(appContext.getString(R.string.exif_remove_error))
            }
        }
    }

    fun fetchAlbumCover(manualQuery: String? = null) {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.FilePropertiesState) return
        val entry = dialog.entry

        viewModelScope.launch {
            fetchAlbumCoverUseCase(entry.path, manualQuery).collect { result ->
                _uiState.update { state ->
                    val d = state.dialogState
                    if (d is DialogState.FilePropertiesState && d.entry.path == entry.path) {
                        val pendingBytes = if (result is CoverResult.Found) result.imageBytes else d.pendingCoverBytes
                        state.copy(dialogState = d.copy(coverResult = result, pendingCoverBytes = pendingBytes))
                    } else state
                }
            }
        }
    }

    fun saveAllAudioData(tags: AudioTags?) {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.FilePropertiesState) return
        val entry = dialog.entry
        val imageBytes = dialog.pendingCoverBytes

        viewModelScope.launch {
            // 1. Save tags first (sequential — avoid JAudiotagger race condition)
            if (tags != null) {
                val success = saveAudioTagsUseCase(entry.path, tags)
                if (success) {
                    _toastMessage.tryEmit(appContext.getString(R.string.audio_tags_saved))
                } else {
                    _toastMessage.tryEmit(appContext.getString(R.string.audio_tags_save_error))
                }
            }

            // 2. Then save cover (after tags are committed)
            if (imageBytes != null && imageBytes.isNotEmpty()) {
                fetchAlbumCoverUseCase.saveCover(entry.path, imageBytes).collect { result ->
                    _uiState.update { state ->
                        val d = state.dialogState
                        if (d is DialogState.FilePropertiesState && d.entry.path == entry.path) {
                            state.copy(dialogState = d.copy(coverResult = result))
                        } else state
                    }
                    if (result is CoverResult.Saved) {
                        _toastMessage.tryEmit(appContext.getString(R.string.audio_cover_saved))
                        val activeId = _uiState.value.activePanel
                        updatePanel(activeId) { it.copy(thumbnailVersion = it.thumbnailVersion + 1) }
                    } else if (result is CoverResult.Error) {
                        _toastMessage.tryEmit(appContext.getString(R.string.audio_cover_save_error))
                    }
                }
            }

            // 3. Refresh properties once at the end
            showFileProperties(entry)
        }
    }

    // --- APK Install ---

    fun showApkInstallDialog(entry: FileEntry) {
        _uiState.update {
            it.copy(dialogState = DialogState.ApkInstallDialog(entry = entry))
        }
        viewModelScope.launch {
            loadApkInstallInfoUseCase(entry)
                .onSuccess { info ->
                    val current = _uiState.value.dialogState
                    if (current is DialogState.ApkInstallDialog && current.entry.path == entry.path) {
                        _uiState.update {
                            it.copy(dialogState = current.copy(apkInfo = info, isLoading = false))
                        }
                    }
                }
                .onFailure { e ->
                    val current = _uiState.value.dialogState
                    if (current is DialogState.ApkInstallDialog && current.entry.path == entry.path) {
                        _uiState.update {
                            it.copy(dialogState = current.copy(
                                isLoading = false,
                                error = e.message ?: appContext.getString(R.string.apk_analysis_error)
                            ))
                        }
                    }
                }
        }
    }

    /**
     * Extract APK from archive to cacheDir and launch system installer.
     * Checks for version downgrade and shows confirmation dialog if needed.
     */
    private fun installApkFromArchive(archivePath: String, entry: FileEntry, password: String?) {
        val apkName = entry.name
        val entryFullPath = entry.path.substringAfter("!/", "").ifEmpty { entry.name }
        EcosystemLogger.d(HaronConstants.TAG, "installApkFromArchive: $apkName from archive=${archivePath.substringAfterLast('/')}")
        _toastMessage.tryEmit(appContext.getString(R.string.apk_extracting_from_archive))

        viewModelScope.launch {
            try {
                val tempDir = File(appContext.cacheDir, "apk_install")
                tempDir.mkdirs()
                tempDir.listFiles()?.forEach { it.deleteRecursively() }

                val selectedEntries = setOf(entryFullPath)
                extractArchiveUseCase(archivePath, tempDir.absolutePath, selectedEntries, password)
                    .collect { progress ->
                        if (progress.isComplete) {
                            val extractedFile = tempDir.walkTopDown().firstOrNull {
                                it.isFile && it.length() > 0
                            }
                            if (extractedFile != null) {
                                val nameLc = extractedFile.name.lowercase()
                                if (nameLc.endsWith(".xapk") || nameLc.endsWith(".apks")) {
                                    // XAPK/APKS — handle via XapkInstaller
                                    installXapkFile(extractedFile)
                                } else {
                                    // Regular APK
                                    EcosystemLogger.d(HaronConstants.TAG, "installApkFromArchive: extracted ${extractedFile.name}, size=${extractedFile.length()}")
                                    launchApkInstallOrDowngradeDialog(extractedFile)
                                }
                            } else {
                                EcosystemLogger.e(HaronConstants.TAG, "installApkFromArchive: file not found or empty after extraction")
                                _toastMessage.tryEmit(appContext.getString(R.string.extract_error_generic))
                            }
                        }
                        if (progress.error != null) {
                            EcosystemLogger.e(HaronConstants.TAG, "installApkFromArchive: error: ${progress.error}")
                            _toastMessage.tryEmit(appContext.getString(R.string.error_format, progress.error ?: ""))
                        }
                    }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "installApkFromArchive: exception: ${e.message}")
                _toastMessage.tryEmit(appContext.getString(R.string.error_format, e.message ?: ""))
            }
        }
    }

    /**
     * Install XAPK/APKS file: parse manifest, copy OBB, install APK(s).
     */
    private fun installXapkFile(xapkFile: File) {
        EcosystemLogger.d(HaronConstants.TAG, "installXapkFile: ${xapkFile.name}, size=${xapkFile.length()}")
        _toastMessage.tryEmit(appContext.getString(R.string.xapk_parsing))

        viewModelScope.launch {
            val manifest = com.vamp.haron.common.util.XapkInstaller.parseManifest(xapkFile)
            if (manifest == null) {
                EcosystemLogger.e(HaronConstants.TAG, "installXapkFile: invalid XAPK — no manifest or APKs")
                _toastMessage.tryEmit(appContext.getString(R.string.xapk_invalid))
                return@launch
            }

            val apkCount = manifest.apkFiles.size
            val obbCount = manifest.obbFiles.size
            EcosystemLogger.d(HaronConstants.TAG, "installXapkFile: pkg=${manifest.packageName}, apks=$apkCount, obbs=$obbCount")
            _toastMessage.tryEmit(appContext.getString(R.string.xapk_extracting, manifest.name, apkCount))

            val result = com.vamp.haron.common.util.XapkInstaller.install(appContext, xapkFile, manifest)
            if (!result.success) {
                EcosystemLogger.e(HaronConstants.TAG, "installXapkFile: failed: ${result.error}")
                _toastMessage.tryEmit(appContext.getString(R.string.xapk_install_failed, result.error ?: ""))
                return@launch
            }

            if (result.obbCopied > 0) {
                _toastMessage.tryEmit(appContext.getString(R.string.xapk_obb_copied, result.obbCopied))
            }

            if (!result.isSplitApk && result.singleApkFile != null) {
                // Single APK — use standard install flow with version check
                launchApkInstallOrDowngradeDialog(result.singleApkFile)
            } else if (result.isSplitApk) {
                // Split APK — already submitted to PackageInstaller session
                _toastMessage.tryEmit(appContext.getString(R.string.xapk_split_installing, apkCount))
            }
        }
    }

    /**
     * Check if APK is a downgrade. If yes — show dialog. If not — install directly.
     * Also used for regular APK install (not from archive).
     */
    internal fun launchApkInstallOrDowngradeDialog(apkFile: File) {
        try {
            val pm = appContext.packageManager
            EcosystemLogger.d(HaronConstants.TAG, "launchApkInstallOrDowngradeDialog: parsing ${apkFile.absolutePath}, size=${apkFile.length()}, exists=${apkFile.exists()}")
            val apkInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            EcosystemLogger.d(HaronConstants.TAG, "launchApkInstallOrDowngradeDialog: apkInfo=${if (apkInfo != null) "${apkInfo.packageName} v${apkInfo.versionName}" else "null"}")
            if (apkInfo != null) {
                val apkPackage = apkInfo.packageName
                val apkVersionName = apkInfo.versionName ?: "?"
                val apkVersionCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
                    apkInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    apkInfo.versionCode.toLong()
                }

                // Check if already installed
                try {
                    EcosystemLogger.d(HaronConstants.TAG, "launchApkInstallOrDowngradeDialog: checking installed version of $apkPackage")
                    val installed = pm.getPackageInfo(apkPackage, 0)
                    val installedVersionCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
                        installed.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        installed.versionCode.toLong()
                    }
                    val installedVersionName = installed.versionName ?: "?"

                    EcosystemLogger.d(HaronConstants.TAG, "launchApkInstallOrDowngradeDialog: installed=$installedVersionName($installedVersionCode), apk=$apkVersionName($apkVersionCode)")
                    if (apkVersionCode < installedVersionCode) {
                        // Downgrade detected — show dialog
                        EcosystemLogger.d(HaronConstants.TAG, "launchApkInstall: downgrade detected: installed=$installedVersionCode, apk=$apkVersionCode")
                        _uiState.update {
                            it.copy(dialogState = DialogState.ApkDowngradeConfirm(
                                apkFile = apkFile,
                                packageName = apkPackage,
                                installedVersionName = installedVersionName,
                                installedVersionCode = installedVersionCode,
                                apkVersionName = apkVersionName,
                                apkVersionCode = apkVersionCode
                            ))
                        }
                        return
                    } else if (apkVersionCode > installedVersionCode) {
                        _toastMessage.tryEmit(appContext.getString(R.string.apk_updating, installedVersionName, apkVersionName))
                    } else {
                        _toastMessage.tryEmit(appContext.getString(R.string.apk_reinstalling, apkVersionName))
                    }
                } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                    EcosystemLogger.d(HaronConstants.TAG, "launchApkInstallOrDowngradeDialog: package not installed (NameNotFound)")
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "launchApkInstallOrDowngradeDialog: installed check error: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "launchApkInstallOrDowngradeDialog: version check failed: ${e.javaClass.simpleName}: ${e.message}")
        }

        // No downgrade or check failed — install directly
        EcosystemLogger.d(HaronConstants.TAG, "launchApkInstallOrDowngradeDialog: proceeding to install")
        launchApkInstaller(apkFile)
    }

    /** Launch system installer for an APK file. useView=true for clean install after downgrade uninstall. */
    internal fun launchApkInstaller(apkFile: File, useView: Boolean = false) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                apkFile
            )
            val intent = if (useView) {
                // ACTION_VIEW via system PackageInstaller — bypasses VERSION_DOWNGRADE check after uninstall
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // Direct to system installer — avoid chooser
                    setClassName("com.google.android.packageinstaller", "com.android.packageinstaller.InstallStart")
                }
            } else {
                Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    data = uri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                }
            }
            appContext.startActivity(intent)
            _toastMessage.tryEmit(appContext.getString(R.string.apk_installer_launched))
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "launchApkInstaller: failed: ${e.message}")
            _toastMessage.tryEmit(appContext.getString(R.string.installer_open_failed))
        }
    }

    /** Uninstall package then install APK as clean install (downgrade flow) */
    fun uninstallAndInstall(dialog: DialogState.ApkDowngradeConfirm) {
        dismissDialog()
        val pkg = dialog.packageName
        val apkFile = dialog.apkFile
        EcosystemLogger.d(HaronConstants.TAG, "uninstallAndInstall: uninstalling $pkg, pending APK=${apkFile.name}")
        _toastMessage.tryEmit(appContext.getString(R.string.apk_downgrade_uninstalling))

        _pendingDowngradeApk = apkFile
        _pendingDowngradePackage = pkg

        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$pkg")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)

            // Register receiver with delay — skip stale broadcasts from previous install/update
            viewModelScope.launch {
                kotlinx.coroutines.delay(2000)
                registerPackageRemovedReceiver(pkg)
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "uninstallAndInstall: failed: ${e.message}")
            _toastMessage.tryEmit(appContext.getString(R.string.error_format, e.message ?: ""))
        }
    }

    private var _pendingDowngradeApk: File? = null
    private var _pendingDowngradePackage: String? = null
    private var _packageReceiver: android.content.BroadcastReceiver? = null

    private fun registerPackageRemovedReceiver(packageName: String) {
        unregisterPackageRemovedReceiver()
        _packageReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                val removedPkg = intent?.data?.schemeSpecificPart ?: return
                if (removedPkg != packageName) return
                val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (replacing) return
                EcosystemLogger.d(HaronConstants.TAG, "packageReceiver: $removedPkg removed, action=${intent.action}")
                onDowngradePackageRemoved()
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        appContext.registerReceiver(_packageReceiver, filter)
        EcosystemLogger.d(HaronConstants.TAG, "registerPackageRemovedReceiver: listening for $packageName")
    }

    private fun unregisterPackageRemovedReceiver() {
        _packageReceiver?.let {
            try { appContext.unregisterReceiver(it) } catch (_: Exception) {}
        }
        _packageReceiver = null
    }

    private fun onDowngradePackageRemoved() {
        val apkFile = _pendingDowngradeApk ?: return
        val pkg = _pendingDowngradePackage ?: return
        _pendingDowngradeApk = null
        _pendingDowngradePackage = null
        unregisterPackageRemovedReceiver()

        // Poll PM to confirm package is truly gone, then install
        viewModelScope.launch {
            var attempts = 0
            while (attempts < 30) {
                val stillInstalled = try {
                    appContext.packageManager.getPackageInfo(pkg, 0)
                    true
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                    false
                }
                if (!stillInstalled) {
                    EcosystemLogger.d(HaronConstants.TAG, "onDowngradePackageRemoved: PM confirmed removal after ${attempts * 500}ms, installing ${apkFile.name}")
                    if (apkFile.exists()) {
                        launchApkInstaller(apkFile, useView = true)
                    } else {
                        EcosystemLogger.e(HaronConstants.TAG, "onDowngradePackageRemoved: APK file gone")
                        _toastMessage.tryEmit(appContext.getString(R.string.extract_error_generic))
                    }
                    return@launch
                }
                attempts++
                kotlinx.coroutines.delay(500)
            }
            EcosystemLogger.e(HaronConstants.TAG, "onDowngradePackageRemoved: PM still reports package after 15s")
            _toastMessage.tryEmit(appContext.getString(R.string.apk_downgrade_uninstall_failed))
        }
    }

    private var _packageAddedReceiver: android.content.BroadcastReceiver? = null

    private fun registerPackageAddedReceiver() {
        _packageAddedReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.action == Intent.ACTION_PACKAGE_ADDED) {
                    val pkg = intent.data?.schemeSpecificPart ?: return
                    val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    onPackageInstalled(pkg, replacing)
                }
            }
        }
        val filter = android.content.IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addDataScheme("package")
        }
        appContext.registerReceiver(_packageAddedReceiver, filter)
    }

    // onCleared is defined near init{} — cleanup for receivers is there

    /** Called from Compose when PACKAGE_REMOVED broadcast is received (non-downgrade) */
    fun onPackageRemoved(packageName: String) {
        EcosystemLogger.d(HaronConstants.TAG, "onPackageRemoved: $packageName")
    }

    /** Called when a package is installed or updated */
    fun onPackageInstalled(packageName: String, isUpdate: Boolean) {
        try {
            val pm = appContext.packageManager
            val info = pm.getPackageInfo(packageName, 0)
            val appLabel = pm.getApplicationLabel(info.applicationInfo!!).toString()
            val versionName = info.versionName ?: "?"
            val msg = if (isUpdate) {
                appContext.getString(R.string.apk_updated_toast, appLabel, versionName)
            } else {
                appContext.getString(R.string.apk_installed_toast, appLabel, versionName)
            }
            _toastMessage.tryEmit(msg)
            EcosystemLogger.d(HaronConstants.TAG, "onPackageInstalled: $packageName v$versionName (update=$isUpdate)")
        } catch (_: Exception) {}
    }

    fun installApk(entry: FileEntry) {
        dismissDialog()
        if (entry.isContentUri) {
            // Content URI — can't check version, install directly
            try {
                val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    data = Uri.parse(entry.path)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                }
                appContext.startActivity(intent)
            } catch (_: Exception) {
                _toastMessage.tryEmit(appContext.getString(R.string.installer_open_failed))
            }
        } else {
            launchApkInstallOrDowngradeDialog(File(entry.path))
        }
    }

    // --- App Manager ---

    fun openAppManager() {
        _navigationEvent.tryEmit(NavigationEvent.OpenAppManager)
    }

    // --- Storage Analysis ---

    fun openStorageAnalysis() {
        _navigationEvent.tryEmit(NavigationEvent.OpenStorageAnalysis)
    }

    fun openGlobalSearch() {
        _navigationEvent.tryEmit(NavigationEvent.OpenGlobalSearch)
    }

    // --- File Transfer ---

    fun openTransfer() {
        _navigationEvent.tryEmit(NavigationEvent.OpenTransfer)
    }

    fun onSendToTransfer(paths: List<String>) {
        val files = paths.mapNotNull { path ->
            java.io.File(path).takeIf { it.exists() }
        }
        if (files.isEmpty()) return
        TransferHolder.selectedFiles = files
        _navigationEvent.tryEmit(NavigationEvent.OpenTransfer)
    }

    fun onSendSelected(paths: List<String>) {
        val files = paths.mapNotNull { path ->
            java.io.File(path).takeIf { it.exists() }
        }
        if (files.isEmpty()) return

        try {
            val uris = files.map { file ->
                androidx.core.content.FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    file
                )
            }

            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = files[0].let { f ->
                        android.webkit.MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(f.extension.lowercase())
                            ?: "*/*"
                    }
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            val chooser = Intent.createChooser(intent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(chooser)
        } catch (_: Exception) {
            _toastMessage.tryEmit(appContext.getString(R.string.no_app_for_file))
        }

        clearSelection(uiState.value.activePanel)
    }

    // --- Open with external app ---

    fun openWithExternalApp(entry: FileEntry) {
        if (entry.isDirectory) {
            _toastMessage.tryEmit(appContext.getString(R.string.cannot_open_folders_external))
            return
        }
        try {
            val uri = if (entry.isContentUri) {
                Uri.parse(entry.path)
            } else {
                androidx.core.content.FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    File(entry.path)
                )
            }
            val mime = entry.mimeType()
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, appContext.getString(R.string.open_in_chooser)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(chooser)
        } catch (_: Exception) {
            _toastMessage.tryEmit(appContext.getString(R.string.no_app_for_file))
        }
    }

    fun openSelectedWithExternalApp() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val selected = panel.files.filter { it.path in panel.selectedPaths }
        if (selected.size == 1 && !selected.first().isDirectory) {
            openWithExternalApp(selected.first())
        }
    }

    fun openTerminal() {
        _navigationEvent.tryEmit(NavigationEvent.OpenTerminal)
    }

    fun compareSelected() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val selected = panel.files.filter { it.path in panel.selectedPaths }

        if (selected.size == 2) {
            // Two items in the same panel
            com.vamp.haron.domain.model.ComparisonHolder.leftPath = selected[0].path
            com.vamp.haron.domain.model.ComparisonHolder.rightPath = selected[1].path
            clearSelection(activeId)
            _navigationEvent.tryEmit(NavigationEvent.OpenComparison)
        } else if (selected.size == 1) {
            // One item in active panel — check other panel for one selected item
            val otherId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
            val otherPanel = getPanel(otherId)
            val otherSelected = otherPanel.files.filter { it.path in otherPanel.selectedPaths }
            if (otherSelected.size == 1) {
                com.vamp.haron.domain.model.ComparisonHolder.leftPath = selected[0].path
                com.vamp.haron.domain.model.ComparisonHolder.rightPath = otherSelected[0].path
                clearSelection(activeId)
                clearSelection(otherId)
                _navigationEvent.tryEmit(NavigationEvent.OpenComparison)
            }
        }
    }

    fun hideSelectedInFile() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val selected = panel.files.filter { it.path in panel.selectedPaths }
        if (selected.size == 1 && !selected.first().isDirectory) {
            com.vamp.haron.domain.model.StegoHolder.payloadPath = selected.first().path
            com.vamp.haron.domain.model.StegoHolder.carrierPath = ""
            clearSelection(activeId)
            _navigationEvent.tryEmit(NavigationEvent.OpenSteganography)
        }
    }

    fun openSteganography() {
        _navigationEvent.tryEmit(NavigationEvent.OpenSteganography)
    }

    fun castSelected() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val selected = panel.files.filter { it.path in panel.selectedPaths }
        if (selected.isEmpty()) return

        val modes = mutableListOf<com.vamp.haron.domain.model.CastMode>()

        // Determine available modes based on selection
        val hasImages = selected.any { !it.isDirectory && it.path.lowercase().let { p ->
            p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png") ||
            p.endsWith(".gif") || p.endsWith(".webp") || p.endsWith(".bmp")
        }}
        val hasMedia = selected.any { !it.isDirectory && it.path.lowercase().let { p ->
            p.endsWith(".mp4") || p.endsWith(".mkv") || p.endsWith(".avi") ||
            p.endsWith(".webm") || p.endsWith(".mp3") || p.endsWith(".flac") ||
            p.endsWith(".ogg") || p.endsWith(".wav")
        }}
        val hasPdf = selected.any { !it.isDirectory && it.path.lowercase().endsWith(".pdf") }

        if (hasMedia || hasImages) modes.add(com.vamp.haron.domain.model.CastMode.SINGLE_MEDIA)
        if (hasImages && selected.size > 1) modes.add(com.vamp.haron.domain.model.CastMode.SLIDESHOW)
        if (hasPdf && selected.size == 1) modes.add(com.vamp.haron.domain.model.CastMode.PDF_PRESENTATION)
        modes.add(com.vamp.haron.domain.model.CastMode.SCREEN_MIRROR)

        val paths = selected.map { it.path }
        _uiState.update { it.copy(dialogState = DialogState.CastModeSelect(paths, modes)) }
    }

    fun openSettings() {
        _navigationEvent.tryEmit(NavigationEvent.OpenSettings)
    }

    fun openFeatures() {
        _navigationEvent.tryEmit(NavigationEvent.OpenFeatures)
    }

    fun openSupport() {
        _navigationEvent.tryEmit(NavigationEvent.OpenSupport)
    }

    fun openAbout() {
        _navigationEvent.tryEmit(NavigationEvent.OpenAbout)
    }

    // --- Gesture system ---

    fun executeGestureAction(action: GestureAction) {
        if (action == GestureAction.NONE) return
        // Dismiss any open dialog/drawer/shelf before executing voice command
        // (except when the command itself opens drawer/shelf)
        val st = _uiState.value
        if (st.dialogState != DialogState.None) dismissDialog()
        if (action != GestureAction.OPEN_DRAWER && st.showDrawer) dismissDrawer()
        if (action != GestureAction.OPEN_SHELF && st.showShelf) dismissShelf()
        // Use panel override if set (e.g. "назад вверху"), otherwise active panel
        val panelId = voiceCommandManager.consumePanelOverride() ?: _uiState.value.activePanel
        when (action) {
            GestureAction.NONE -> { /* do nothing */ }
            GestureAction.OPEN_DRAWER -> toggleDrawer()
            GestureAction.OPEN_SHELF -> toggleShelf()
            GestureAction.TOGGLE_HIDDEN -> toggleShowHidden(panelId)
            GestureAction.CREATE_NEW -> requestCreateFromTemplate()
            GestureAction.GLOBAL_SEARCH -> openGlobalSearch()
            GestureAction.OPEN_TERMINAL -> openTerminal()
            GestureAction.SELECT_ALL -> selectAll(panelId)
            GestureAction.REFRESH -> {
                refreshPanel(panelId)
                _toastMessage.tryEmit(appContext.getString(R.string.panel_refreshed))
            }
            GestureAction.GO_HOME -> navigateTo(panelId, HaronConstants.ROOT_PATH)
            GestureAction.SORT_CYCLE -> cycleSortOrder(panelId)
            GestureAction.OPEN_SETTINGS -> openSettings()
            GestureAction.OPEN_TRANSFER -> openTransfer()
            GestureAction.OPEN_TRASH -> showTrash()
            GestureAction.OPEN_STORAGE -> _navigationEvent.tryEmit(NavigationEvent.OpenStorageAnalysis)
            GestureAction.OPEN_DUPLICATES -> _navigationEvent.tryEmit(NavigationEvent.OpenDuplicateDetector)
            GestureAction.OPEN_APPS -> _navigationEvent.tryEmit(NavigationEvent.OpenAppManager)
            GestureAction.OPEN_SCANNER -> _navigationEvent.tryEmit(NavigationEvent.OpenScanner)
            GestureAction.SORT_SPECIFIC -> applySortFromVoice(panelId)
            // --- Voice Level 1 + Level 2 ---
            GestureAction.NAVIGATE_BACK -> {
                if (canNavigateBack(panelId)) navigateBack(panelId)
                else _toastMessage.tryEmit(appContext.getString(R.string.no_history_back))
            }
            GestureAction.NAVIGATE_FORWARD -> {
                val panel = getPanel(panelId)
                if (panel.historyIndex < panel.navigationHistory.lastIndex) navigateForward(panelId)
                else _toastMessage.tryEmit(appContext.getString(R.string.no_history_forward))
            }
            GestureAction.NAVIGATE_UP -> { navigateUp(panelId) }
            GestureAction.DELETE_SELECTED -> requestDeleteSelected()
            GestureAction.COPY_SELECTED -> copySelectedToOtherPanel()
            GestureAction.MOVE_SELECTED -> moveSelectedToOtherPanel()
            GestureAction.RENAME -> executeVoiceRename(panelId)
            GestureAction.CREATE_ARCHIVE -> requestCreateArchive()
            GestureAction.EXTRACT_ARCHIVE -> {
                val panel = getPanel(panelId)
                if (panel.isArchiveMode) {
                    extractFromArchive(panelId)
                } else {
                    // Try to extract selected archive file(s) into other panel
                    extractSelectedArchiveFiles(panelId)
                }
            }
            GestureAction.FILE_PROPERTIES -> showSelectedFileProperties()
            GestureAction.DESELECT_ALL -> clearSelection(panelId)
            GestureAction.NAVIGATE_TO_FOLDER -> navigateToFolderFromVoice(panelId)
            GestureAction.REFRESH_FOLDER_CACHE -> {
                val map = rebuildFolderCache()
                _toastMessage.tryEmit(appContext.getString(R.string.folder_cache_refreshed, map.size))
            }
            GestureAction.OPEN_SECURE_FOLDER -> showAllProtectedFiles()
            // Handled at NavHost level, not here
            GestureAction.OPEN_LOGS, GestureAction.LOGS_PAUSE, GestureAction.LOGS_RESUME -> {}
        }
    }

    private fun applySortFromVoice(panelId: PanelId) {
        val field = voiceCommandManager.pendingSortField
        val explicitDirection = voiceCommandManager.pendingSortDirection
        voiceCommandManager.consumeSortParams()
        if (field == null) return cycleSortOrder(panelId)
        val current = getPanel(panelId).sortOrder
        // If same field and no explicit direction → toggle direction
        val direction = explicitDirection ?: if (current.field == field) {
            if (current.direction == com.vamp.haron.data.model.SortDirection.ASCENDING)
                com.vamp.haron.data.model.SortDirection.DESCENDING
            else com.vamp.haron.data.model.SortDirection.ASCENDING
        } else {
            current.direction
        }
        val newOrder = com.vamp.haron.data.model.SortOrder(field, direction)
        setSortOrder(panelId, newOrder)
        val sortName = when (field) {
            com.vamp.haron.data.model.SortField.NAME -> appContext.getString(R.string.sort_by_name)
            com.vamp.haron.data.model.SortField.DATE -> appContext.getString(R.string.sort_by_date)
            com.vamp.haron.data.model.SortField.SIZE -> appContext.getString(R.string.sort_by_size)
            com.vamp.haron.data.model.SortField.EXTENSION -> appContext.getString(R.string.sort_by_type)
        }
        val dirName = if (direction == com.vamp.haron.data.model.SortDirection.ASCENDING) "↑" else "↓"
        _toastMessage.tryEmit(appContext.getString(R.string.sort_changed_to, "$sortName $dirName"))
    }

    private fun executeVoiceRename(panelId: PanelId) {
        val name = voiceCommandManager.consumeRenameName()
        if (name == null) {
            // No name specified — just open inline rename UI
            requestRename()
            return
        }
        val panel = getPanel(panelId)
        val selected = panel.selectedPaths.firstOrNull()
        if (selected == null) {
            _toastMessage.tryEmit(appContext.getString(R.string.select_files_first))
            return
        }
        viewModelScope.launch {
            // Preserve extension
            val file = java.io.File(selected)
            val ext = file.extension
            val newName = if (ext.isNotEmpty()) "$name.$ext" else name
            renameFileUseCase(selected, newName).fold(
                onSuccess = { newPath ->
                    _toastMessage.tryEmit(appContext.getString(R.string.renamed_to, java.io.File(newPath).name))
                    clearSelection(panelId)
                    refreshPanel(PanelId.TOP)
                    refreshPanel(PanelId.BOTTOM)
                },
                onFailure = { e ->
                    _toastMessage.tryEmit(appContext.getString(R.string.error_rename) + ": ${e.message}")
                }
            )
        }
    }

    /**
     * Stem-based aliases: Russian word stem → English folder name.
     * Ordered longest-first so "фотограф" matches before "фото".
     * Handles all case forms: камера/камеру/камере/камерой → Camera.
     */
    private val folderStemAliases = listOf(
        // Download
        "загрузк" to "Download", "загрузо" to "Download",
        // Documents
        "документ" to "Documents",
        // Pictures
        "изображен" to "Pictures", "картин" to "Pictures",
        // DCIM
        "фотограф" to "DCIM",
        // Camera
        "камер" to "Camera",
        // Movies
        "фильм" to "Movies",
        // Music
        "музык" to "Music",
        // Telegram
        "телеграм" to "Telegram",
        // WhatsApp
        "ватсап" to "WhatsApp", "вотсап" to "WhatsApp", "вацап" to "WhatsApp",
        // Screenshots
        "скриншот" to "Screenshots",
        // Bluetooth
        "блютуз" to "Bluetooth", "блютус" to "Bluetooth",
        // Notifications
        "уведомлен" to "Notifications",
        // Ringtones
        "рингтон" to "Ringtones",
        // Podcasts
        "подкаст" to "Podcasts",
        // Short stems last (to avoid false prefix matches)
        "фото" to "DCIM", "видео" to "Movies",
    )

    /** Resolve Russian query to English folder name via stem matching. */
    private fun resolveAlias(query: String): String? {
        val lower = query.lowercase()
        return folderStemAliases.firstOrNull { (stem, _) -> lower.startsWith(stem) }?.second
    }

    /** Cached folder name → path map. Permanent until manual refresh via voice command. */
    private var folderScanCache: Map<String, String> = emptyMap()

    private fun getFolderNameMap(): Map<String, String> {
        if (folderScanCache.isNotEmpty()) return folderScanCache
        return rebuildFolderCache()
    }

    /** Rebuild folder cache from storage scan. */
    private fun rebuildFolderCache(): Map<String, String> {
        val candidates = mutableListOf<String>()
        val root = java.io.File(com.vamp.haron.common.constants.HaronConstants.ROOT_PATH)
        scanFolders(root, maxDepth = 3, currentDepth = 0, candidates)

        val state = _uiState.value
        candidates.addAll(state.favorites.filter { java.io.File(it).isDirectory })
        candidates.addAll(state.recentPaths.filter { java.io.File(it).isDirectory })
        candidates.addAll(state.bookmarks.values.filter { java.io.File(it).isDirectory })

        // Sort by depth (shallowest first) so root-level folders win over nested ones
        // e.g. /storage/.../Music wins over /storage/.../TwinApps/Music
        val map = mutableMapOf<String, String>()
        for (path in candidates.distinct().sortedBy { it.count { c -> c == '/' || c == '\\' } }) {
            val name = java.io.File(path).name
            map.putIfAbsent(name, path)
        }
        folderScanCache = map
        return map
    }

    private fun navigateToFolderFromVoice(panelId: PanelId) {
        val query = voiceCommandManager.consumeFolderQuery() ?: return

        // Check Russian alias first (stem-based: камеру/камере/камерой → Camera)
        val resolvedAlias = resolveAlias(query)
        val searchName = resolvedAlias ?: query

        // Get cached folder map + add current directory subfolders
        val nameToPathMap = getFolderNameMap().toMutableMap()
        val panel = getPanel(panelId)
        for (f in panel.files) {
            if (f.isDirectory) nameToPathMap.putIfAbsent(java.io.File(f.path).name, f.path)
        }

        // Direct lookup by alias/name (case-insensitive)
        val directMatch = nameToPathMap.entries.firstOrNull {
            it.key.equals(searchName, ignoreCase = true)
        }
        if (directMatch != null) {
            navigateTo(panelId, directMatch.value)
            _toastMessage.tryEmit(appContext.getString(R.string.navigated_to_format, directMatch.key))
            return
        }

        // Fuzzy match
        val match = com.vamp.haron.common.util.FuzzyMatch.findBestMatch(
            searchName, nameToPathMap.keys.toList(), threshold = 0.4f
        )
        if (match != null) {
            val path = nameToPathMap[match]!!
            navigateTo(panelId, path)
            _toastMessage.tryEmit(appContext.getString(R.string.navigated_to_format, match))
        } else {
            _toastMessage.tryEmit(appContext.getString(R.string.folder_not_found_format, query))
        }
    }

    /** Recursively collect directory paths up to maxDepth. Skips hidden and Android/data. */
    private fun scanFolders(dir: java.io.File, maxDepth: Int, currentDepth: Int, out: MutableList<String>) {
        if (currentDepth > maxDepth) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (!child.isDirectory) continue
            val name = child.name
            // Skip hidden dirs and heavy system dirs
            if (name.startsWith(".") || name == "Android" && currentDepth == 0) continue
            out.add(child.absolutePath)
            scanFolders(child, maxDepth, currentDepth + 1, out)
        }
    }

    private fun cycleSortOrder(panelId: PanelId) {
        val current = getPanel(panelId).sortOrder
        val fields = com.vamp.haron.data.model.SortField.entries
        val nextIndex = (fields.indexOf(current.field) + 1) % fields.size
        val newOrder = current.copy(field = fields[nextIndex])
        setSortOrder(panelId, newOrder)
        val sortName = when (newOrder.field) {
            com.vamp.haron.data.model.SortField.NAME -> appContext.getString(R.string.sort_by_name)
            com.vamp.haron.data.model.SortField.DATE -> appContext.getString(R.string.sort_by_date)
            com.vamp.haron.data.model.SortField.SIZE -> appContext.getString(R.string.sort_by_size)
            com.vamp.haron.data.model.SortField.EXTENSION -> appContext.getString(R.string.sort_by_type)
        }
        _toastMessage.tryEmit(appContext.getString(R.string.sort_changed_to, sortName))
    }

    fun reloadGestureMappings() {
        _uiState.update {
            it.copy(
                gestureMappings = preferences.getGestureMappings(),
                marqueeEnabled = preferences.marqueeEnabled
            )
        }
    }

    // --- Force Delete ---

    fun requestForceDelete() {
        val state = _uiState.value
        val activeId = state.activePanel
        val panel = getPanel(activeId)
        val selected = panel.selectedPaths.toList()
        if (selected.isEmpty()) {
            _toastMessage.tryEmit(appContext.getString(R.string.select_files_first))
            return
        }
        val names = panel.files
            .filter { it.path in panel.selectedPaths }
            .map { it.name }
        _uiState.update {
            it.copy(dialogState = DialogState.ForceDeleteConfirm(selected, names))
        }
    }

    fun confirmTrashOverflowDelete(paths: List<String>) {
        dismissDialog()
        clearSelection(_uiState.value.activePanel)
        FileOperationService.startDelete(appContext, paths, useTrash = false)
    }

    fun confirmForceDelete(paths: List<String>) {
        dismissDialog()
        val activeId = _uiState.value.activePanel
        clearSelection(activeId)
        val total = paths.size

        viewModelScope.launch {
            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, "", OperationType.DELETE))
            }
            forceDeleteUseCase(paths) { current, fileName ->
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(current, total, fileName, OperationType.DELETE))
                }
            }
                .onSuccess { count ->
                    hapticManager.success()
                    _toastMessage.tryEmit(appContext.getString(R.string.force_deleted_count, count))
                }
                .onFailure { e ->
                    hapticManager.error()
                    _toastMessage.tryEmit(appContext.getString(R.string.error_format, e.message ?: ""))
                }
            _uiState.update {
                it.copy(operationProgress = OperationProgress(total, total, "", OperationType.DELETE, isComplete = true))
            }
            viewModelScope.launch {
                delay(2000)
                _uiState.update {
                    if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it
                }
            }
            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
        }
    }

    // --- Empty Folders ---

    fun findEmptyFolders() {
        val activeId = _uiState.value.activePanel
        val path = getPanel(activeId).currentPath
        if (path.startsWith("content://")) {
            _toastMessage.tryEmit(appContext.getString(R.string.saf_unavailable))
            return
        }
        _uiState.update {
            it.copy(dialogState = DialogState.EmptyFolderCleanup(isLoading = true, isRecursive = true))
        }
        viewModelScope.launch {
            findEmptyFoldersUseCase(path, recursive = true).collect { folders ->
                _uiState.update {
                    it.copy(dialogState = DialogState.EmptyFolderCleanup(
                        folders = folders,
                        isRecursive = true,
                        selectedPaths = folders.toSet(),
                        isLoading = false
                    ))
                }
            }
        }
    }

    fun toggleEmptyFoldersRecursive(recursive: Boolean) {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.EmptyFolderCleanup) return
        val activeId = _uiState.value.activePanel
        val path = getPanel(activeId).currentPath
        _uiState.update {
            it.copy(dialogState = dialog.copy(isLoading = true, isRecursive = recursive))
        }
        viewModelScope.launch {
            findEmptyFoldersUseCase(path, recursive = recursive).collect { folders ->
                _uiState.update {
                    it.copy(dialogState = DialogState.EmptyFolderCleanup(
                        folders = folders,
                        isRecursive = recursive,
                        selectedPaths = folders.toSet(),
                        isLoading = false
                    ))
                }
            }
        }
    }

    fun toggleEmptyFolderSelected(path: String) {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.EmptyFolderCleanup) return
        val newSelected = dialog.selectedPaths.toMutableSet()
        if (path in newSelected) newSelected.remove(path) else newSelected.add(path)
        _uiState.update { it.copy(dialogState = dialog.copy(selectedPaths = newSelected)) }
    }

    fun selectAllEmptyFolders() {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.EmptyFolderCleanup) return
        val newSelected = if (dialog.selectedPaths.size == dialog.folders.size) {
            emptySet()
        } else {
            dialog.folders.toSet()
        }
        _uiState.update { it.copy(dialogState = dialog.copy(selectedPaths = newSelected)) }
    }

    fun deleteEmptyFolders() {
        val dialog = _uiState.value.dialogState
        if (dialog !is DialogState.EmptyFolderCleanup) return
        val paths = dialog.selectedPaths.toList()
        if (paths.isEmpty()) return
        dismissDialog()

        viewModelScope.launch {
            moveToTrashUseCase(paths)
                .onSuccess { result ->
                    hapticManager.success()
                    _toastMessage.tryEmit(appContext.getString(R.string.empty_folders_deleted, result.movedCount))
                    updateTrashSizeInfo()
                }
                .onFailure { e ->
                    hapticManager.error()
                    _toastMessage.tryEmit(appContext.getString(R.string.error_format, e.message ?: ""))
                }
            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
        }
    }

    // --- Folder Size Calculation ---

    fun getSelectedTotalSizeForPanel(panelId: PanelId): Pair<Long, Boolean> {
        val state = _uiState.value
        val panel = getPanel(panelId)
        val selected = panel.files.filter { it.path in panel.selectedPaths }
        var total = 0L
        var calculating = false
        for (entry in selected) {
            if (entry.isDirectory) {
                val cached = state.folderSizeCache[entry.path]
                if (cached != null) {
                    total += cached
                } else {
                    calculating = true
                    if (!folderSizeJobs.containsKey(entry.path)) {
                        calculateFolderSize(entry.path)
                    }
                }
            } else {
                total += entry.size
            }
        }
        return total to calculating
    }

    fun getSelectedTotalSizeWithFolders(): Pair<Long, Boolean> {
        val state = _uiState.value
        val activeId = state.activePanel
        val panel = getPanel(activeId)
        val selected = panel.files.filter { it.path in panel.selectedPaths }
        var total = 0L
        var calculating = false
        for (entry in selected) {
            if (entry.isDirectory) {
                val cached = state.folderSizeCache[entry.path]
                if (cached != null) {
                    total += cached
                } else {
                    calculating = true
                    // Launch calculation if not already running
                    if (!folderSizeJobs.containsKey(entry.path)) {
                        calculateFolderSize(entry.path)
                    }
                }
            } else {
                total += entry.size
            }
        }
        return total to calculating
    }

    private fun calculateFolderSize(folderPath: String) {
        val job = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val size = when {
                    // Root: StorageStatsManager — used space on user partition (excludes OS ~24 GB)
                    folderPath == HaronConstants.ROOT_PATH -> {
                        try {
                            val ssm = appContext.getSystemService(android.content.Context.STORAGE_STATS_SERVICE) as android.app.usage.StorageStatsManager
                            val totalBytes = ssm.getTotalBytes(android.os.storage.StorageManager.UUID_DEFAULT)
                            val freeBytes = ssm.getFreeBytes(android.os.storage.StorageManager.UUID_DEFAULT)
                            totalBytes - freeBytes
                        } catch (_: Exception) {
                            try {
                                val statFs = android.os.StatFs(folderPath)
                                statFs.totalBytes - statFs.availableBytes
                            } catch (_: Exception) {
                                File(folderPath).walkTopDown().filter { it.isFile }.sumOf { it.length() }
                            }
                        }
                    }
                    // Shizuku available: use shell access for accurate sizes (handles Android/data, Android/obb)
                    shizukuManager.isServiceBound() -> {
                        shizukuManager.calculateDirSize(folderPath)
                            ?: File(folderPath).walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    }
                    // Fallback: regular walkTopDown
                    else -> {
                        File(folderPath).walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    }
                }
                _uiState.update { state ->
                    state.copy(folderSizeCache = state.folderSizeCache + (folderPath to size))
                }
            }
        }
        folderSizeJobs[folderPath] = job
        job.invokeOnCompletion { folderSizeJobs.remove(folderPath) }
    }

    fun showStorageSizeInfo(panelId: PanelId) {
        val panel = getPanel(panelId)
        val path = panel.currentPath
        if (path == HaronConstants.ROOT_PATH) {
            try {
                val ssm = appContext.getSystemService(android.content.Context.STORAGE_STATS_SERVICE) as android.app.usage.StorageStatsManager
                val totalBytes = ssm.getTotalBytes(android.os.storage.StorageManager.UUID_DEFAULT)
                val freeBytes = ssm.getFreeBytes(android.os.storage.StorageManager.UUID_DEFAULT)
                val usedBytes = totalBytes - freeBytes
                _toastMessage.tryEmit(appContext.getString(
                    R.string.storage_size_info,
                    usedBytes.toFileSize(appContext),
                    totalBytes.toFileSize(appContext)
                ))
            } catch (_: Exception) { /* ignore */ }
        } else {
            // Show folder size / storage total for non-root paths
            val folderSize = _uiState.value.folderSizeCache[path]
                ?: panel.files.filter { !it.isDirectory }.sumOf { it.size }
            val volumeRoot = getVolumeRoot(path)
            if (volumeRoot != null) {
                try {
                    val stat = android.os.StatFs(volumeRoot)
                    val totalBytes = stat.totalBytes
                    _toastMessage.tryEmit(appContext.getString(
                        R.string.storage_size_info,
                        folderSize.toFileSize(appContext),
                        totalBytes.toFileSize(appContext)
                    ))
                } catch (_: Exception) {
                    _toastMessage.tryEmit(folderSize.toFileSize(appContext))
                }
            } else {
                _toastMessage.tryEmit(folderSize.toFileSize(appContext))
            }
        }
    }

    fun clearFolderSizeCache() {
        folderSizeJobs.values.forEach { it.cancel() }
        folderSizeJobs.clear()
        _uiState.update { it.copy(folderSizeCache = emptyMap()) }
    }

    /** Returns the storage volume root for a given path, or null for cloud/FTP/SAF. */
    private fun getVolumeRoot(path: String): String? = when {
        path.startsWith("/storage/emulated/0") -> "/storage/emulated/0"
        path.startsWith("/storage/") -> path.split("/").take(3).joinToString("/")
        path == HaronConstants.ROOT_PATH -> HaronConstants.ROOT_PATH
        else -> null
    }

    /** Calculates total capacity of the storage volume for the given path (cached per volume root). */
    fun ensureStorageTotalCalculated(path: String) {
        val volumeRoot = getVolumeRoot(path) ?: return
        if (_uiState.value.storageSizeCache.containsKey(volumeRoot)) return
        viewModelScope.launch(Dispatchers.IO) {
            val totalBytes = try {
                if (volumeRoot == "/storage/emulated/0") {
                    val ssm = appContext.getSystemService(android.content.Context.STORAGE_STATS_SERVICE)
                            as android.app.usage.StorageStatsManager
                    ssm.getTotalBytes(android.os.storage.StorageManager.UUID_DEFAULT)
                } else {
                    android.os.StatFs(volumeRoot).totalBytes
                }
            } catch (_: Exception) {
                try { android.os.StatFs(volumeRoot).totalBytes } catch (_: Exception) { 0L }
            }
            if (totalBytes > 0) {
                _uiState.update { it.copy(storageSizeCache = it.storageSizeCache + (volumeRoot to totalBytes)) }
                EcosystemLogger.d(HaronConstants.TAG, "ensureStorageTotalCalculated: $volumeRoot = ${totalBytes / 1024 / 1024} MB")
            }
        }
    }

    /** Returns total storage size for the given path, or 0 if unknown/cloud. */
    fun getStorageTotalFor(path: String): Long {
        val volumeRoot = getVolumeRoot(path) ?: return 0L
        return _uiState.value.storageSizeCache[volumeRoot] ?: 0L
    }

    /** Instantly adjust cached folder size by delta (e.g. +size on copy, -size on delete/move). */
    private fun adjustFolderSizeCache(path: String, delta: Long) {
        if (delta == 0L || path.isEmpty() || path.contains("://")) return
        _uiState.update { state ->
            val current = state.folderSizeCache[path] ?: return@update state
            state.copy(folderSizeCache = state.folderSizeCache + (path to maxOf(0L, current + delta)))
        }
    }

    /** Get file size from currently loaded panel files by path. */
    private fun getFileSizeForDelta(path: String): Long {
        val state = _uiState.value
        return (state.topPanel.files + state.bottomPanel.files)
            .firstOrNull { it.path == path }?.size ?: 0L
    }

    private fun startLiveFolderSizeRefresh() {
        liveSizeRefreshJob?.cancel()
        liveSizeRefreshJob = viewModelScope.launch {
            while (true) {
                delay(2000)
                val state = _uiState.value
                listOf(state.topPanel.currentPath, state.bottomPanel.currentPath)
                    .filter { path ->
                        path.isNotEmpty() &&
                        !path.startsWith("content://") &&
                        !isRestrictedAndroidDir(path) &&
                        !path.contains("://") // skip cloud/ftp/virtual paths
                    }
                    .distinct()
                    .forEach { path ->
                        // Don't clear cache before recalc — avoids 0B flicker
                        if (!folderSizeJobs.containsKey(path)) calculateFolderSize(path)
                    }
            }
        }
        EcosystemLogger.d(HaronConstants.TAG, "startLiveFolderSizeRefresh: started")
    }

    private fun stopLiveFolderSizeRefresh() {
        liveSizeRefreshJob?.cancel()
        liveSizeRefreshJob = null
        EcosystemLogger.d(HaronConstants.TAG, "stopLiveFolderSizeRefresh: stopped")
    }

    // --- Bookmarks ---

    fun showBookmarkPopup() {
        _uiState.update {
            it.copy(
                showBookmarkPopup = true,
                bookmarks = preferences.getBookmarks()
            )
        }
    }

    fun dismissBookmarkPopup() {
        _uiState.update { it.copy(showBookmarkPopup = false) }
    }

    fun navigateToBookmark(slot: Int) {
        val path = _uiState.value.bookmarks[slot] ?: return
        dismissBookmarkPopup()
        val activeId = _uiState.value.activePanel
        navigateTo(activeId, path)
        hapticManager.tick()
    }

    fun saveBookmark(slot: Int) {
        val activeId = _uiState.value.activePanel
        val path = getPanel(activeId).currentPath
        preferences.setBookmark(slot, path)
        _uiState.update { it.copy(bookmarks = preferences.getBookmarks()) }
        hapticManager.success()
        _toastMessage.tryEmit(appContext.getString(R.string.bookmark_saved, slot))
    }

    // --- Tools popup ---

    fun showToolsPopup() {
        _uiState.update { it.copy(showToolsPopup = true) }
    }

    fun dismissToolsPopup() {
        _uiState.update { it.copy(showToolsPopup = false) }
    }

    fun onToolSelected(index: Int) {
        dismissToolsPopup()
        when (index) {
            0 -> showTrash()
            1 -> openStorageAnalysis()
            2 -> openDuplicateDetector()
            3 -> openAppManager()
            4 -> openLastMedia()
            5 -> openLastDocument()
        }
    }

    private fun openLastMedia() {
        val path = preferences.lastMediaFile
        if (path == null || !File(path).exists()) {
            _toastMessage.tryEmit(appContext.getString(R.string.no_recent_files))
            return
        }
        val file = File(path)
        PlaylistHolder.items = listOf(
            PlaylistHolder.PlaylistItem(
                filePath = path,
                fileName = file.name,
                fileType = file.name.substringAfterLast('.', "").lowercase().let {
                    when (it) {
                        "mp4", "mkv", "avi", "webm", "mov", "3gp" -> "video"
                        else -> "audio"
                    }
                }
            )
        )
        PlaylistHolder.startIndex = 0
        _navigationEvent.tryEmit(NavigationEvent.OpenMediaPlayer(0))
    }

    private fun openLastDocument() {
        val path = preferences.lastDocumentFile
        if (path == null || !File(path).exists()) {
            _toastMessage.tryEmit(appContext.getString(R.string.no_recent_files))
            return
        }
        val file = File(path)
        _navigationEvent.tryEmit(NavigationEvent.OpenPdfReader(path, file.name))
    }

    // --- Widget update ---

    fun updateWidget() {
        // Widget reads from SharedPreferences directly, just trigger update
        try {
            val intent = Intent("android.appwidget.action.APPWIDGET_UPDATE")
            intent.setPackage(appContext.packageName)
            appContext.sendBroadcast(intent)
        } catch (_: Exception) { }
    }

    // --- Protected file operations (virtual view) ---

    private fun copyProtectedFiles(paths: List<String>, destinationDir: String) {
        val activeId = _uiState.value.activePanel
        val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        clearSelection(activeId)

        viewModelScope.launch {
            val allEntries = secureFolderRepository.getAllProtectedEntries()
            // Expand directory entries → include children
            val expandedPaths = mutableListOf<String>()
            for (path in paths) {
                expandedPaths.add(path)
                val entry = allEntries.find { it.originalPath == path }
                if (entry != null && entry.isDirectory) {
                    allEntries.filter { it.originalPath.startsWith("$path/") && !it.isDirectory }
                        .forEach { expandedPaths.add(it.originalPath) }
                }
            }

            val fileEntries = expandedPaths.mapNotNull { p -> allEntries.find { it.originalPath == p } }
                .filter { !it.isDirectory }
            val total = fileEntries.size
            if (total == 0) {
                _toastMessage.tryEmit(appContext.getString(R.string.folder_empty))
                return@launch
            }

            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, appContext.getString(R.string.copying_secure_files), OperationType.COPY))
            }

            var completed = 0
            for ((index, entry) in fileEntries.withIndex()) {
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(index, total, entry.originalName, OperationType.COPY))
                }
                secureFolderRepository.decryptToCache(entry.id).onSuccess { tempFile ->
                    try {
                        // Preserve relative path inside directory
                        val relativePath = paths.firstOrNull { entry.originalPath.startsWith("$it/") }
                            ?.let { entry.originalPath.removePrefix("$it/").substringBeforeLast('/') }
                        val destDir = if (relativePath != null && relativePath.isNotEmpty()) {
                            File(destinationDir, relativePath).also { it.mkdirs() }
                        } else {
                            File(destinationDir)
                        }
                        val destFile = File(destDir, entry.originalName)
                        if (destFile.exists()) {
                            // Auto-rename
                            val baseName = entry.originalName.substringBeforeLast('.')
                            val ext = entry.originalName.substringAfterLast('.', "")
                            var counter = 1
                            var renamed: File
                            do {
                                val newName = if (ext.isNotEmpty()) "${baseName}_($counter).$ext" else "${baseName}_($counter)"
                                renamed = File(destDir, newName)
                                counter++
                            } while (renamed.exists())
                            tempFile.copyTo(renamed)
                        } else {
                            tempFile.copyTo(destFile)
                        }
                        tempFile.delete()
                        completed++
                    } catch (e: Exception) {
                        tempFile.delete()
                        EcosystemLogger.e(HaronConstants.TAG, "copyProtectedFiles error: ${e.message}")
                    }
                }
            }

            _uiState.update {
                it.copy(operationProgress = OperationProgress(completed, total, "", OperationType.COPY, isComplete = true))
            }
            if (completed > 0) {
                hapticManager.success()
                showStatusMessage(targetId, appContext.getString(R.string.copied_format, formatFileCount(0, completed)))
            } else {
                hapticManager.error()
            }
            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
            delay(2000)
            _uiState.update { if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it }
        }
    }

    private fun moveProtectedFiles(paths: List<String>, destinationDir: String) {
        val activeId = _uiState.value.activePanel
        val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        clearSelection(activeId)

        viewModelScope.launch {
            val allEntries = secureFolderRepository.getAllProtectedEntries()
            // Expand directory entries → include children
            val expandedPaths = mutableListOf<String>()
            for (path in paths) {
                expandedPaths.add(path)
                val entry = allEntries.find { it.originalPath == path }
                if (entry != null && entry.isDirectory) {
                    allEntries.filter { it.originalPath.startsWith("$path/") && !it.isDirectory }
                        .forEach { expandedPaths.add(it.originalPath) }
                }
            }

            val fileEntries = expandedPaths.mapNotNull { p -> allEntries.find { it.originalPath == p } }
                .filter { !it.isDirectory }
            val dirEntries = paths.mapNotNull { p -> allEntries.find { it.originalPath == p && it.isDirectory } }
            val total = fileEntries.size
            if (total == 0) {
                _toastMessage.tryEmit(appContext.getString(R.string.folder_empty))
                return@launch
            }

            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, appContext.getString(R.string.moving_secure_files), OperationType.MOVE))
            }

            var completed = 0
            val idsToRemove = mutableListOf<String>()

            for ((index, entry) in fileEntries.withIndex()) {
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(index, total, entry.originalName, OperationType.MOVE))
                }
                secureFolderRepository.decryptToCache(entry.id).onSuccess { tempFile ->
                    try {
                        val relativePath = paths.firstOrNull { entry.originalPath.startsWith("$it/") }
                            ?.let { entry.originalPath.removePrefix("$it/").substringBeforeLast('/') }
                        val destDir = if (relativePath != null && relativePath.isNotEmpty()) {
                            File(destinationDir, relativePath).also { it.mkdirs() }
                        } else {
                            File(destinationDir)
                        }
                        val destFile = File(destDir, entry.originalName)
                        if (destFile.exists()) {
                            val baseName = entry.originalName.substringBeforeLast('.')
                            val ext = entry.originalName.substringAfterLast('.', "")
                            var counter = 1
                            var renamed: File
                            do {
                                val newName = if (ext.isNotEmpty()) "${baseName}_($counter).$ext" else "${baseName}_($counter)"
                                renamed = File(destDir, newName)
                                counter++
                            } while (renamed.exists())
                            tempFile.copyTo(renamed)
                        } else {
                            tempFile.copyTo(destFile)
                        }
                        tempFile.delete()
                        idsToRemove.add(entry.id)
                        completed++
                    } catch (e: Exception) {
                        tempFile.delete()
                        EcosystemLogger.e(HaronConstants.TAG, "moveProtectedFiles error: ${e.message}")
                    }
                }
            }

            // Remove from secure storage (files + parent dirs)
            val allIdsToRemove = idsToRemove + dirEntries.map { it.id }
            if (allIdsToRemove.isNotEmpty()) {
                secureFolderRepository.deleteFromSecureStorage(allIdsToRemove) { _, _ -> }
            }

            _uiState.update {
                it.copy(operationProgress = OperationProgress(completed, total, "", OperationType.MOVE, isComplete = true))
            }
            if (completed > 0) {
                hapticManager.success()
                showStatusMessage(targetId, appContext.getString(R.string.moved_format, formatFileCount(0, completed)))
            } else {
                hapticManager.error()
            }
            refreshPanel(activeId)
            refreshPanel(targetId)
            delay(2000)
            _uiState.update { if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it }
        }
    }

    private fun deleteProtectedPermanently(paths: List<String>) {
        viewModelScope.launch {
            val allEntries = secureFolderRepository.getAllProtectedEntries()
            // Collect IDs: exact match + cascade for directories
            val directIds = allEntries.filter { it.originalPath in paths }.map { it.id }
            val dirPaths = allEntries.filter { it.isDirectory && it.originalPath in paths }.map { it.originalPath }
            val cascadeIds = if (dirPaths.isNotEmpty()) {
                allEntries.filter { entry ->
                    dirPaths.any { dir -> entry.originalPath.startsWith("$dir/") }
                }.map { it.id }
            } else emptyList()

            val ids = (directIds + cascadeIds).distinct()
            if (ids.isEmpty()) return@launch

            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, ids.size, "", OperationType.DELETE))
            }

            secureFolderRepository.deleteFromSecureStorage(ids) { current, name ->
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(current, ids.size, name, OperationType.DELETE))
                }
            }.onSuccess { count ->
                _uiState.update {
                    it.copy(operationProgress = OperationProgress(count, ids.size, "", OperationType.DELETE, isComplete = true))
                }
                hapticManager.success()
                _toastMessage.tryEmit(appContext.getString(R.string.secure_deleted_count, count))
                refreshPanel(PanelId.TOP)
                refreshPanel(PanelId.BOTTOM)
            }.onFailure { e ->
                _uiState.update { it.copy(operationProgress = null) }
                hapticManager.error()
                _toastMessage.tryEmit(appContext.getString(R.string.protect_error, e.message ?: ""))
            }

            delay(2000)
            _uiState.update { if (it.operationProgress?.isComplete == true) it.copy(operationProgress = null) else it }
        }
    }

    // --- Secure Folder / Shield ---

    fun toggleShield() {
        val currentState = _uiState.value
        if (currentState.isShieldUnlocked) {
            // Turn off shield
            _uiState.update { it.copy(isShieldUnlocked = false, showShieldAuth = false) }
            // If any panel is in virtual secure path, navigate back
            for (panelId in PanelId.entries) {
                if (getPanel(panelId).currentPath == HaronConstants.VIRTUAL_SECURE_PATH) {
                    if (canNavigateBack(panelId)) {
                        navigateBack(panelId)
                    } else {
                        navigateTo(panelId, HaronConstants.ROOT_PATH, pushHistory = false)
                    }
                } else {
                    refreshPanel(panelId)
                }
            }
            _toastMessage.tryEmit(appContext.getString(R.string.shield_off))
        } else {
            val method = authManager.getAppLockMethod()
            val hasAuth = method != com.vamp.haron.domain.model.AppLockMethod.NONE &&
                (authManager.isPinSet() || (authManager.hasBiometricHardware() && authManager.isBiometricEnrolled()))
            if (hasAuth) {
                _uiState.update { it.copy(showShieldAuth = true) }
            } else {
                // No protection configured — show PIN setup dialog
                _uiState.update { it.copy(showShieldPinSetup = true) }
            }
        }
    }

    fun onShieldAuthenticated() {
        val showAll = _uiState.value.showAllProtectedAfterAuth
        _uiState.update { it.copy(isShieldUnlocked = true, showShieldAuth = false, showAllProtectedAfterAuth = false) }
        if (showAll) {
            showAllProtectedFiles()
        } else {
            refreshPanel(PanelId.TOP)
            refreshPanel(PanelId.BOTTOM)
            _toastMessage.tryEmit(appContext.getString(R.string.shield_on))
        }
    }

    fun dismissShieldAuth() {
        _uiState.update { it.copy(showShieldAuth = false, showAllProtectedAfterAuth = false) }
    }

    fun dismissShieldPinSetup() {
        _uiState.update { it.copy(showShieldPinSetup = false) }
    }

    fun onShieldPinSetupConfirm(currentPin: String?, newPin: String, question: String?, answer: String?): Boolean {
        if (currentPin != null && !authManager.verifyPin(currentPin)) return false
        authManager.setPin(newPin)
        if (question != null && answer != null) {
            authManager.setSecurityQuestion(question, answer)
        }
        if (authManager.getAppLockMethod() == com.vamp.haron.domain.model.AppLockMethod.NONE) {
            authManager.setAppLockMethod(com.vamp.haron.domain.model.AppLockMethod.PIN_ONLY)
        }
        _uiState.update { it.copy(showShieldPinSetup = false) }
        _toastMessage.tryEmit(appContext.getString(R.string.pin_set_success))
        return true
    }

    fun verifyShieldPin(pin: String): Boolean = authManager.verifyPin(pin)

    fun getShieldLockMethod(): com.vamp.haron.domain.model.AppLockMethod = authManager.getAppLockMethod()

    fun hasShieldBiometric(): Boolean = authManager.hasBiometricHardware() && authManager.isBiometricEnrolled()

    fun getShieldPinLength(): Int = authManager.getPinLength()

    fun showAllProtectedFiles() {
        val activePanel = _uiState.value.activePanel
        if (!_uiState.value.isShieldUnlocked) {
            val method = authManager.getAppLockMethod()
            val hasAuth = method != com.vamp.haron.domain.model.AppLockMethod.NONE &&
                (authManager.isPinSet() || (authManager.hasBiometricHardware() && authManager.isBiometricEnrolled()))
            if (hasAuth) {
                _uiState.update { it.copy(showShieldAuth = true, showAllProtectedAfterAuth = true) }
            } else {
                // No protection configured — show PIN setup dialog
                _uiState.update { it.copy(showShieldPinSetup = true) }
            }
            return
        }
        navigateTo(activePanel, HaronConstants.VIRTUAL_SECURE_PATH)
    }

    /** Exit protected mode completely — find the last real folder in history and go there */
    fun exitProtectedMode(panelId: PanelId) {
        _uiState.update { it.copy(isShieldUnlocked = false, showShieldAuth = false) }
        val panel = getPanel(panelId)
        // Find last non-protected path in history
        val history = panel.navigationHistory
        val realPath = history.lastOrNull { path ->
            path != HaronConstants.VIRTUAL_SECURE_PATH &&
                !secureFolderRepository.isFileProtected(path) &&
                java.io.File(path).exists()
        }
        navigateTo(panelId, realPath ?: HaronConstants.ROOT_PATH, pushHistory = false)
    }

    fun protectSelectedFiles(explicitPaths: List<String>? = null) {
        val panelId = _uiState.value.activePanel
        val panel = getPanel(panelId)
        val paths = explicitPaths ?: panel.selectedPaths.toList()
        if (paths.isEmpty()) return

        // Collect protected directory paths to check other panel after operation
        val protectedDirs = paths.filter { File(it).isDirectory }.map { File(it).absolutePath }.toSet()

        viewModelScope.launch {
            _uiState.update { it.copy(isProtecting = true) }
            clearSelection(panelId)

            secureFolderRepository.protectFiles(paths) { current, name ->
                _uiState.update {
                    it.copy(protectProgress = appContext.getString(R.string.protecting_files, current, paths.size, name))
                }
            }.onSuccess { count ->
                _uiState.update { it.copy(isProtecting = false, protectProgress = null) }
                _toastMessage.tryEmit(appContext.getString(R.string.protect_success, count))

                // If other panel is inside a protected (now deleted) directory, navigate up
                for (panelId in PanelId.entries) {
                    val panelPath = getPanel(panelId).currentPath
                    val needsUp = protectedDirs.any { dir ->
                        panelPath == dir || panelPath.startsWith("$dir/")
                    }
                    if (needsUp) {
                        // Find the nearest existing parent
                        val parent = protectedDirs
                            .filter { panelPath == it || panelPath.startsWith("$it/") }
                            .maxByOrNull { it.length }
                            ?.let { File(it).parent }
                            ?: HaronConstants.ROOT_PATH
                        navigateTo(panelId, parent, pushHistory = false)
                    } else {
                        refreshPanel(panelId)
                    }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isProtecting = false, protectProgress = null) }
                _toastMessage.tryEmit(appContext.getString(R.string.protect_error, e.message ?: ""))
            }
        }
    }

    fun unprotectSelectedFiles(explicitPaths: List<String>? = null) {
        val panel = getPanel(_uiState.value.activePanel)
        val paths = explicitPaths ?: panel.selectedPaths.toList()
        EcosystemLogger.d(HaronConstants.TAG, "unprotectSelectedFiles: paths=$paths, explicitPaths=${explicitPaths != null}")
        if (paths.isEmpty()) {
            EcosystemLogger.d(HaronConstants.TAG, "unprotectSelectedFiles: paths empty, returning")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProtecting = true) }
            val allEntries = secureFolderRepository.getAllProtectedEntries()
            EcosystemLogger.d(HaronConstants.TAG, "unprotectSelectedFiles: allEntries=${allEntries.size}, paths to match: $paths")

            // Collect IDs: exact match + cascade for directories (all children)
            val directIds = allEntries.filter { it.originalPath in paths }.map { it.id }
            EcosystemLogger.d(HaronConstants.TAG, "unprotectSelectedFiles: directIds=${directIds.size}")
            val dirPaths = allEntries.filter { it.isDirectory && it.originalPath in paths }.map { it.originalPath }
            val cascadeIds = if (dirPaths.isNotEmpty()) {
                allEntries.filter { entry ->
                    !entry.isDirectory && dirPaths.any { dir -> entry.originalPath.startsWith("$dir/") }
                }.map { it.id }
            } else emptyList()

            // Merge, directories first (so folder is created before files are written)
            val dirEntryIds = directIds.filter { id -> allEntries.find { it.id == id }?.isDirectory == true }
            val fileEntryIds = (directIds + cascadeIds).distinct().filter { id -> allEntries.find { it.id == id }?.isDirectory != true }
            val ids = dirEntryIds + fileEntryIds
            EcosystemLogger.d(HaronConstants.TAG, "unprotectSelectedFiles: total ids=${ids.size} (dirs=${dirEntryIds.size}, files=${fileEntryIds.size})")

            if (ids.isEmpty()) {
                EcosystemLogger.e(HaronConstants.TAG, "unprotectSelectedFiles: no matching IDs found! allEntries paths: ${allEntries.map { it.originalPath }}")
                _uiState.update { it.copy(isProtecting = false) }
                _toastMessage.tryEmit("No matching protected files found")
                return@launch
            }

            clearSelection(_uiState.value.activePanel)

            secureFolderRepository.unprotectFiles(ids) { current, name ->
                _uiState.update {
                    it.copy(protectProgress = appContext.getString(R.string.unprotecting_files, current, ids.size, name))
                }
            }.onSuccess { count ->
                EcosystemLogger.i(HaronConstants.TAG, "unprotectSelectedFiles: success, count=$count")
                _uiState.update { it.copy(isProtecting = false, protectProgress = null) }
                _toastMessage.tryEmit(appContext.getString(R.string.unprotect_success, count))
                refreshPanel(PanelId.TOP)
                refreshPanel(PanelId.BOTTOM)
            }.onFailure { e ->
                EcosystemLogger.e(HaronConstants.TAG, "unprotectSelectedFiles: failed: ${e.message}")
                _uiState.update { it.copy(isProtecting = false, protectProgress = null) }
                _toastMessage.tryEmit(appContext.getString(R.string.protect_error, e.message ?: ""))
            }
        }
    }

    fun onProtectedFileClick(entry: FileEntry) {
        if (!entry.isProtected) return
        viewModelScope.launch {
            _toastMessage.tryEmit(appContext.getString(R.string.decrypting_file))
            val allEntries = secureFolderRepository.getAllProtectedEntries()
            val secureEntry = allEntries.find { it.originalPath == entry.path } ?: return@launch
            secureFolderRepository.decryptToCache(secureEntry.id).onSuccess { tempFile ->
                val type = entry.iconRes()
                when (type) {
                    "video", "audio" -> {
                        PlaylistHolder.items = listOf(
                            PlaylistHolder.PlaylistItem(
                                filePath = tempFile.absolutePath,
                                fileName = entry.name,
                                fileType = type
                            )
                        )
                        PlaylistHolder.startIndex = 0
                        _navigationEvent.tryEmit(NavigationEvent.OpenMediaPlayer(0))
                    }
                    "image" -> {
                        GalleryHolder.items = listOf(
                            GalleryHolder.GalleryItem(
                                filePath = tempFile.absolutePath,
                                fileName = entry.name,
                                fileSize = entry.size
                            )
                        )
                        GalleryHolder.startIndex = 0
                        _navigationEvent.tryEmit(NavigationEvent.OpenGallery(0))
                    }
                    "text", "code" -> {
                        _navigationEvent.tryEmit(
                            NavigationEvent.OpenTextEditor(tempFile.absolutePath, entry.name)
                        )
                    }
                    "pdf", "document" -> {
                        _navigationEvent.tryEmit(
                            NavigationEvent.OpenPdfReader(tempFile.absolutePath, entry.name)
                        )
                    }
                    "archive" -> {
                        val panelId = _uiState.value.activePanel
                        navigateIntoArchive(panelId, tempFile.absolutePath, "", null)
                    }
                    else -> {
                        openFileWithIntent(tempFile.absolutePath, entry.name)
                    }
                }
            }.onFailure { e ->
                _toastMessage.tryEmit(appContext.getString(R.string.protect_error, e.message ?: ""))
            }
        }
    }

    private fun openFileWithIntent(path: String, name: String) {
        try {
            val file = File(path)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                appContext, "${appContext.packageName}.fileprovider", file
            )
            val ext = name.substringAfterLast('.', "").lowercase()
            val mimeType = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(ext) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(Intent.createChooser(intent, name))
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "openFileWithIntent error: ${e.message}")
        }
    }

    fun getSecureFolderInfo(): Pair<Int, Long> {
        val entries = try {
            kotlinx.coroutines.runBlocking { secureFolderRepository.getAllProtectedEntries() }
        } catch (_: Exception) { emptyList() }
        return entries.size to entries.sumOf { it.originalSize }
    }

    fun getProtectedCountForDir(dirPath: String): Int {
        return try {
            kotlinx.coroutines.runBlocking { secureFolderRepository.getProtectedEntriesForDir(dirPath).size }
        } catch (_: Exception) { 0 }
    }

    fun hasAnyProtectedEntry(paths: Set<String>): Boolean {
        return paths.any { secureFolderRepository.isFileProtected(it) }
    }
}
