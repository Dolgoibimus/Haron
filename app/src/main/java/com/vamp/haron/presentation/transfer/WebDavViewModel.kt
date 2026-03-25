package com.vamp.haron.presentation.transfer

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.R
import com.vamp.haron.data.ftp.FtpTransferProgress
import com.vamp.haron.data.webdav.WebDavCredential
import com.vamp.haron.data.webdav.WebDavCredentialStore
import com.vamp.haron.domain.model.PlaylistHolder
import com.vamp.haron.data.webdav.WebDavFileInfo
import com.vamp.haron.data.webdav.WebDavManager
import com.vamp.haron.domain.model.PanelId
import com.vamp.haron.presentation.transfer.state.LocalFileEntry
import com.vamp.haron.presentation.transfer.state.LocalPanelState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class WebDavSavedServer(
    val url: String,
    val name: String = url
)

data class WebDavUiState(
    val serverListMode: Boolean = true,
    val savedServers: List<WebDavSavedServer> = emptyList(),
    val connectedUrl: String? = null,
    val currentUrl: String = "",
    val files: List<WebDavFileInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAuthDialog: Boolean = false,
    val isConnecting: Boolean = false,
    val selectedFiles: Set<String> = emptySet(),
    val transferProgress: FtpTransferProgress? = null,
    val showCreateFolderDialog: Boolean = false,
    val showRenameDialog: Pair<String, String>? = null,
    // Dual-panel
    val localPanel: LocalPanelState = LocalPanelState(),
    val activePanel: PanelId = PanelId.TOP,
    val panelRatio: Float = 0.5f
)

/**
 * WebDAV client: browse remote servers (Nextcloud, etc.), upload/download files.
 * Manages connections, credentials, and saved server list.
 * Delegates to [WebDavManager] (Sardine).
 */
@HiltViewModel
class WebDavViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val webDavManager: WebDavManager,
    private val credentialStore: WebDavCredentialStore
) : ViewModel() {

    private val _state = MutableStateFlow(WebDavUiState())
    val state: StateFlow<WebDavUiState> = _state.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastMessage = _toastMessage.asSharedFlow()

    private val _playMediaStream = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val playMediaStream = _playMediaStream.asSharedFlow()

    companion object {
        private val MEDIA_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts", "m2ts", "mpg", "mpeg",
            "mp3", "flac", "ogg", "wav", "aac", "m4a", "wma", "opus", "aiff"
        )
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts", "m2ts", "mpg", "mpeg"
        )
    }

    private var transferJob: Job? = null

    init {
        loadSavedServers()
    }

    private fun loadSavedServers() {
        viewModelScope.launch {
            val creds = credentialStore.listAll()
            _state.update {
                it.copy(savedServers = creds.map { c ->
                    WebDavSavedServer(c.url, c.displayName)
                })
            }
        }
    }

    fun onSavedServerTap(server: WebDavSavedServer) {
        val savedCred = credentialStore.load(server.url)
        if (savedCred != null) {
            connectWithCredential(savedCred, save = false)
        } else {
            _state.update { it.copy(showAuthDialog = true, error = null) }
        }
    }

    fun onShowAuthDialog() {
        _state.update { it.copy(showAuthDialog = true, error = null) }
    }

    fun dismissAuthDialog() {
        _state.update { it.copy(showAuthDialog = false) }
    }

    fun onConnect(url: String, username: String, password: String, save: Boolean) {
        val cred = WebDavCredential(
            url = url.trim().trimEnd('/'),
            username = username,
            password = password,
            displayName = url.trim().trimEnd('/')
        )
        connectWithCredential(cred, save)
    }

    fun onFileTap(file: WebDavFileInfo) {
        if (_state.value.selectedFiles.isNotEmpty()) {
            toggleSelection(file.path)
            return
        }
        if (file.isDirectory) {
            navigateToFolder(file)
        } else {
            val ext = file.name.substringAfterLast('.', "").lowercase()
            if (ext in MEDIA_EXTENSIONS) {
                streamMediaFile(file)
            } else {
                downloadSingleFile(file)
            }
        }
    }

    private fun streamMediaFile(file: WebDavFileInfo) {
        val s = _state.value
        val baseUrl = s.connectedUrl ?: return
        val cred = credentialStore.listAll().find { baseUrl.startsWith(it.url) }
        val mediaFiles = s.files.filter { f ->
            !f.isDirectory && f.name.substringAfterLast('.', "").lowercase() in MEDIA_EXTENSIONS
        }

        PlaylistHolder.items = mediaFiles.map { f ->
            val fExt = f.name.substringAfterLast('.', "").lowercase()
            // WebDAV = HTTP(S) URL with Basic Auth in URL
            val fullUrl = if (cred != null && cred.username.isNotBlank()) {
                val parsedUrl = java.net.URL(baseUrl)
                val userInfo = "${java.net.URLEncoder.encode(cred.username, "UTF-8")}:${java.net.URLEncoder.encode(cred.password, "UTF-8")}"
                "${parsedUrl.protocol}://$userInfo@${parsedUrl.host}${if (parsedUrl.port > 0) ":${parsedUrl.port}" else ""}${f.path}"
            } else {
                "$baseUrl${f.path}"
            }
            PlaylistHolder.PlaylistItem(
                filePath = fullUrl,
                fileName = f.name,
                fileType = if (fExt in VIDEO_EXTENSIONS) "video" else "audio"
            )
        }
        val startIndex = mediaFiles.indexOfFirst { it.path == file.path }.coerceAtLeast(0)
        PlaylistHolder.startIndex = startIndex

        EcosystemLogger.d(HaronConstants.TAG, "WebDavVM: stream media ${file.name}, playlist=${mediaFiles.size}")
        _playMediaStream.tryEmit(startIndex)
    }

    fun onFileLongPress(file: WebDavFileInfo) {
        toggleSelection(file.path)
    }

    private fun toggleSelection(path: String) {
        _state.update {
            val newSel = it.selectedFiles.toMutableSet()
            if (newSel.contains(path)) newSel.remove(path) else newSel.add(path)
            it.copy(selectedFiles = newSel)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedFiles = emptySet()) }
    }

    fun onNavigateUp(): Boolean {
        val s = _state.value
        if (s.serverListMode) return false
        val baseUrl = s.connectedUrl ?: return false

        val currentTrimmed = s.currentUrl.trimEnd('/')
        val baseTrimmed = baseUrl.trimEnd('/')

        if (currentTrimmed != baseTrimmed) {
            val parentUrl = currentTrimmed.substringBeforeLast("/")
            _state.update { it.copy(currentUrl = parentUrl) }
            loadFiles(parentUrl)
            return true
        }

        onDisconnect()
        return true
    }

    fun navigateToBreadcrumb(index: Int) {
        val s = _state.value
        val baseUrl = s.connectedUrl ?: return

        if (index == 0) {
            onDisconnect()
            return
        }

        val baseTrimmed = baseUrl.trimEnd('/')
        val relative = s.currentUrl.trimEnd('/').removePrefix(baseTrimmed).trimStart('/')
        val parts = relative.split("/").filter { it.isNotEmpty() }
        val targetParts = parts.take(index)
        val targetUrl = baseTrimmed + "/" + targetParts.joinToString("/")
        _state.update { it.copy(currentUrl = targetUrl) }
        loadFiles(targetUrl)
    }

    fun getBreadcrumbs(): List<String> {
        val s = _state.value
        val crumbs = mutableListOf<String>()
        val baseUrl = s.connectedUrl ?: return crumbs
        crumbs.add(baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/'))
        val baseTrimmed = baseUrl.trimEnd('/')
        val relative = s.currentUrl.trimEnd('/').removePrefix(baseTrimmed).trimStart('/')
        if (relative.isNotEmpty()) {
            crumbs.addAll(relative.split("/").filter { it.isNotEmpty() })
        }
        return crumbs
    }

    fun downloadToLocalPanel() {
        val s = _state.value
        val selected = s.selectedFiles.toList()
        val destDir = File(s.localPanel.currentPath)

        EcosystemLogger.d(HaronConstants.TAG, "WebDavVM: download to local ${selected.size} files")
        transferJob = viewModelScope.launch {
            for (remotePath in selected) {
                val fileName = remotePath.trimEnd('/').substringAfterLast("/")
                val fullUrl = resolveFullUrl(remotePath)
                val localDest = File(destDir, java.net.URLDecoder.decode(fileName, "UTF-8"))
                webDavManager.downloadFile(fullUrl, localDest)
                    .catch { e -> _toastMessage.emit(e.localizedMessage ?: "Download error") }
                    .collect { progress ->
                        _state.update { it.copy(transferProgress = FtpTransferProgress(progress.fileName, progress.bytesTransferred, progress.totalBytes, progress.isUpload)) }
                    }
            }
            _state.update { it.copy(transferProgress = null, selectedFiles = emptySet()) }
            _toastMessage.emit(appContext.getString(R.string.ftp_download) + ": ${selected.size}")
            loadLocalFiles()
        }
    }

    fun uploadFromLocalPanel() {
        val s = _state.value
        val remoteDirUrl = s.currentUrl
        val selected = s.localPanel.selectedPaths.toList()

        EcosystemLogger.d(HaronConstants.TAG, "WebDavVM: upload from local ${selected.size} files")
        transferJob = viewModelScope.launch {
            for (path in selected) {
                val file = File(path)
                if (!file.exists()) continue
                val remoteUrl = "${remoteDirUrl.trimEnd('/')}/${file.name}"
                webDavManager.uploadFile(file, remoteUrl)
                    .catch { e -> _toastMessage.emit(e.localizedMessage ?: "Upload error") }
                    .collect { progress ->
                        _state.update { it.copy(transferProgress = FtpTransferProgress(progress.fileName, progress.bytesTransferred, progress.totalBytes, progress.isUpload)) }
                    }
            }
            _state.update { it.copy(transferProgress = null) }
            _toastMessage.emit(appContext.getString(R.string.ftp_upload) + ": ${selected.size}")
            clearLocalSelection()
            refreshFiles()
        }
    }

    fun onCreateFolder(name: String) {
        val s = _state.value
        val folderUrl = "${s.currentUrl.trimEnd('/')}/$name"

        EcosystemLogger.d(HaronConstants.TAG, "WebDavVM: create folder $folderUrl")
        _state.update { it.copy(showCreateFolderDialog = false) }
        viewModelScope.launch {
            val result = webDavManager.createDirectory(folderUrl)
            if (result.isSuccess) {
                _toastMessage.emit(appContext.getString(R.string.folder_created))
                refreshFiles()
            } else {
                _toastMessage.emit(result.exceptionOrNull()?.localizedMessage ?: "Error")
            }
        }
    }

    fun onDeleteSelected() {
        val s = _state.value
        val selected = s.selectedFiles.toList()

        EcosystemLogger.d(HaronConstants.TAG, "WebDavVM: delete ${selected.size} items")
        viewModelScope.launch {
            var deletedCount = 0
            for (path in selected) {
                val fullUrl = resolveFullUrl(path)
                val result = webDavManager.delete(fullUrl)
                if (result.isSuccess) deletedCount++
            }
            _state.update { it.copy(selectedFiles = emptySet()) }
            _toastMessage.emit(appContext.getString(R.string.deleted_count, deletedCount))
            refreshFiles()
        }
    }

    fun onRename(path: String, newName: String) {
        _state.update { it.copy(showRenameDialog = null) }
        viewModelScope.launch {
            val fullUrl = resolveFullUrl(path)
            val result = webDavManager.rename(fullUrl, newName)
            if (result.isSuccess) {
                _toastMessage.emit(appContext.getString(R.string.renamed_to, newName))
                refreshFiles()
            } else {
                _toastMessage.emit(result.exceptionOrNull()?.localizedMessage ?: "Error")
            }
        }
    }

    fun showCreateFolderDialog() {
        _state.update { it.copy(showCreateFolderDialog = true) }
    }

    fun dismissCreateFolderDialog() {
        _state.update { it.copy(showCreateFolderDialog = false) }
    }

    fun showRenameDialog(path: String, currentName: String) {
        _state.update { it.copy(showRenameDialog = path to currentName) }
    }

    fun dismissRenameDialog() {
        _state.update { it.copy(showRenameDialog = null) }
    }

    fun onDisconnect() {
        EcosystemLogger.d(HaronConstants.TAG, "WebDavVM: disconnect")
        webDavManager.disconnect()
        _state.update {
            it.copy(
                serverListMode = true,
                connectedUrl = null,
                currentUrl = "",
                files = emptyList(),
                selectedFiles = emptySet(),
                transferProgress = null,
                error = null,
                localPanel = LocalPanelState(),
                activePanel = PanelId.TOP,
                panelRatio = 0.5f
            )
        }
    }

    fun onRemoveSavedServer(url: String) {
        credentialStore.remove(url)
        loadSavedServers()
    }

    fun refreshFiles() {
        val url = _state.value.currentUrl
        if (url.isNotEmpty()) loadFiles(url)
    }

    fun cancelTransfer() {
        transferJob?.cancel()
        transferJob = null
        _state.update { it.copy(transferProgress = null) }
    }

    // --- Local panel ---

    fun loadLocalFiles(path: String = _state.value.localPanel.currentPath) {
        _state.update { it.copy(localPanel = it.localPanel.copy(isLoading = true, error = null)) }
        viewModelScope.launch {
            try {
                val dir = File(path)
                val entries = (dir.listFiles() ?: emptyArray())
                    .filter { !it.name.startsWith(".") }
                    .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                    .map { f ->
                        LocalFileEntry(
                            name = f.name,
                            path = f.absolutePath,
                            isDirectory = f.isDirectory,
                            size = if (f.isFile) f.length() else 0L,
                            lastModified = f.lastModified()
                        )
                    }
                _state.update {
                    it.copy(localPanel = it.localPanel.copy(
                        currentPath = path,
                        files = entries,
                        isLoading = false,
                        selectedPaths = emptySet()
                    ))
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(localPanel = it.localPanel.copy(
                        isLoading = false,
                        error = e.localizedMessage ?: "Error"
                    ))
                }
            }
        }
    }

    fun onLocalFileTap(file: LocalFileEntry) {
        if (_state.value.localPanel.selectedPaths.isNotEmpty()) {
            toggleLocalSelection(file.path)
            return
        }
        if (file.isDirectory) loadLocalFiles(file.path)
    }

    fun onLocalFileLongPress(file: LocalFileEntry) {
        toggleLocalSelection(file.path)
    }

    private fun toggleLocalSelection(path: String) {
        _state.update {
            val newSel = it.localPanel.selectedPaths.toMutableSet()
            if (newSel.contains(path)) newSel.remove(path) else newSel.add(path)
            it.copy(localPanel = it.localPanel.copy(selectedPaths = newSel))
        }
    }

    fun clearLocalSelection() {
        _state.update { it.copy(localPanel = it.localPanel.copy(selectedPaths = emptySet())) }
    }

    fun navigateLocalUp(): Boolean {
        val lp = _state.value.localPanel
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        if (lp.currentPath == rootPath || lp.currentPath == "/") return false
        val parent = File(lp.currentPath).parent ?: return false
        loadLocalFiles(parent)
        return true
    }

    fun getLocalBreadcrumbs(): List<String> {
        val lp = _state.value.localPanel
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        val crumbs = mutableListOf(appContext.getString(R.string.smb_local_panel))
        val relative = lp.currentPath.removePrefix(rootPath).trimStart('/')
        if (relative.isNotEmpty()) {
            crumbs.addAll(relative.split("/").filter { it.isNotEmpty() })
        }
        return crumbs
    }

    fun navigateLocalBreadcrumb(index: Int) {
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        if (index == 0) {
            loadLocalFiles(rootPath)
            return
        }
        val lp = _state.value.localPanel
        val relative = lp.currentPath.removePrefix(rootPath).trimStart('/')
        val parts = relative.split("/").filter { it.isNotEmpty() }
        val targetParts = parts.take(index)
        val targetPath = rootPath + "/" + targetParts.joinToString("/")
        loadLocalFiles(targetPath)
    }

    // --- Panel management ---

    fun setActivePanel(panelId: PanelId) {
        _state.update { it.copy(activePanel = panelId) }
    }

    fun updatePanelRatio(ratio: Float) {
        _state.update { it.copy(panelRatio = ratio.coerceIn(0.2f, 0.8f)) }
    }

    fun applyPanelRatioDelta(delta: Float) {
        _state.update { it.copy(panelRatio = (it.panelRatio + delta).coerceIn(0.2f, 0.8f)) }
    }

    fun resetPanelRatio() {
        _state.update { it.copy(panelRatio = 0.5f) }
    }

    // --- Panel-aware operations ---

    fun onCreateFolderInActivePanel(name: String) {
        if (_state.value.activePanel == PanelId.BOTTOM) createLocalFolder(name)
        else onCreateFolder(name)
    }

    fun onDeleteSelectedInActivePanel() {
        if (_state.value.activePanel == PanelId.BOTTOM) deleteLocalSelected()
        else onDeleteSelected()
    }

    fun onRenameInActivePanel(path: String, newName: String) {
        if (_state.value.activePanel == PanelId.BOTTOM) renameLocal(path, newName)
        else onRename(path, newName)
    }

    fun onNavigateUpActivePanel(): Boolean {
        return if (_state.value.activePanel == PanelId.BOTTOM) navigateLocalUp()
        else onNavigateUp()
    }

    private fun createLocalFolder(name: String) {
        val lp = _state.value.localPanel
        val dir = File(lp.currentPath, name)
        _state.update { it.copy(showCreateFolderDialog = false) }
        viewModelScope.launch {
            try {
                if (dir.mkdirs()) {
                    _toastMessage.emit(appContext.getString(R.string.folder_created))
                    loadLocalFiles()
                } else {
                    _toastMessage.emit("Failed to create folder")
                }
            } catch (e: Exception) {
                _toastMessage.emit(e.localizedMessage ?: "Error")
            }
        }
    }

    private fun deleteLocalSelected() {
        val selected = _state.value.localPanel.selectedPaths.toList()
        viewModelScope.launch {
            var deletedCount = 0
            for (path in selected) {
                val file = File(path)
                val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (ok) deletedCount++
            }
            clearLocalSelection()
            _toastMessage.emit(appContext.getString(R.string.deleted_count, deletedCount))
            loadLocalFiles()
        }
    }

    private fun renameLocal(path: String, newName: String) {
        _state.update { it.copy(showRenameDialog = null) }
        viewModelScope.launch {
            try {
                val src = File(path)
                val dest = File(src.parentFile, newName)
                if (src.renameTo(dest)) {
                    _toastMessage.emit(appContext.getString(R.string.renamed_to, newName))
                    loadLocalFiles()
                } else {
                    _toastMessage.emit("Rename failed")
                }
            } catch (e: Exception) {
                _toastMessage.emit(e.localizedMessage ?: "Error")
            }
        }
    }

    private fun connectWithCredential(credential: WebDavCredential, save: Boolean) {
        EcosystemLogger.d(HaronConstants.TAG, "WebDavVM: connect to ${credential.url}")
        _state.update { it.copy(isConnecting = true, showAuthDialog = false, error = null) }
        viewModelScope.launch {
            val connectResult = webDavManager.connect(credential)
            if (connectResult.isFailure) {
                val msg = connectResult.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                _state.update { it.copy(isConnecting = false, error = msg, showAuthDialog = true) }
                _toastMessage.emit(appContext.getString(R.string.webdav_connection_error, msg))
                return@launch
            }

            val verifyResult = webDavManager.verifyConnection()
            if (verifyResult.isSuccess) {
                EcosystemLogger.d(HaronConstants.TAG, "WebDavVM: connected to ${credential.url}")
                if (save) {
                    credentialStore.save(credential)
                    loadSavedServers()
                }
                val baseUrl = webDavManager.getBaseUrl()
                _state.update {
                    it.copy(
                        isConnecting = false,
                        connectedUrl = baseUrl,
                        currentUrl = baseUrl,
                        serverListMode = false
                    )
                }
                loadFiles(baseUrl)
                loadLocalFiles()
            } else {
                val msg = verifyResult.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                EcosystemLogger.e(HaronConstants.TAG, "WebDavVM: connect failed: $msg")
                webDavManager.disconnect()
                _state.update {
                    it.copy(isConnecting = false, error = msg, showAuthDialog = true)
                }
                _toastMessage.emit(appContext.getString(R.string.webdav_connection_error, msg))
            }
        }
    }

    private fun loadFiles(url: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = webDavManager.listFiles(url)
            if (result.isSuccess) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        files = result.getOrDefault(emptyList()),
                        selectedFiles = emptySet()
                    )
                }
            } else {
                val msg = result.exceptionOrNull()?.localizedMessage ?: "Error"
                _state.update { it.copy(isLoading = false, error = msg) }
            }
        }
    }

    private fun navigateToFolder(file: WebDavFileInfo) {
        val fullUrl = resolveFullUrl(file.path)
        _state.update { it.copy(currentUrl = fullUrl) }
        loadFiles(fullUrl)
    }

    private fun downloadSingleFile(file: WebDavFileInfo) {
        val destDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
        val fileName = java.net.URLDecoder.decode(file.name, "UTF-8")
        val localDest = File(destDir, fileName)
        val fullUrl = resolveFullUrl(file.path)

        transferJob = viewModelScope.launch {
            webDavManager.downloadFile(fullUrl, localDest)
                .catch { e ->
                    _toastMessage.emit(e.localizedMessage ?: "Download error")
                    _state.update { it.copy(transferProgress = null) }
                }
                .collect { progress ->
                    _state.update { it.copy(transferProgress = FtpTransferProgress(progress.fileName, progress.bytesTransferred, progress.totalBytes, progress.isUpload)) }
                }
            _state.update { it.copy(transferProgress = null) }
            _toastMessage.emit(appContext.getString(R.string.ftp_download) + ": ${file.name}")
        }
    }

    /**
     * Resolves a path (which may be relative href from PROPFIND) to a full URL.
     */
    private fun resolveFullUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        // path is a relative href like /webdav/folder/file.txt
        val baseUrl = _state.value.connectedUrl ?: return path
        return try {
            val base = java.net.URL(baseUrl)
            java.net.URL(base.protocol, base.host, base.port, path).toString()
        } catch (_: Exception) {
            path
        }
    }

    override fun onCleared() {
        super.onCleared()
        webDavManager.disconnect()
    }
}
