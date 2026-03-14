package com.vamp.haron.presentation.transfer

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.R
import com.vamp.haron.data.ftp.FtpClientManager
import com.vamp.haron.data.ftp.FtpCredential
import com.vamp.haron.data.ftp.FtpCredentialStore
import com.vamp.haron.data.ftp.FtpFileInfo
import com.vamp.haron.data.ftp.FtpTransferProgress
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

data class FtpSavedServer(
    val host: String,
    val port: Int = 21,
    val name: String = host,
    val useFtps: Boolean = false
)

data class FtpUiState(
    val serverListMode: Boolean = true,
    val savedServers: List<FtpSavedServer> = emptyList(),
    val connectedHost: String? = null,
    val connectedPort: Int = 21,
    val currentPath: String = "/",
    val files: List<FtpFileInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAuthDialog: Boolean = false,
    val authDialogHost: String? = null,
    val authDialogPort: Int = 21,
    val isConnecting: Boolean = false,
    val selectedFiles: Set<String> = emptySet(),
    val transferProgress: FtpTransferProgress? = null,
    val showCreateFolderDialog: Boolean = false,
    val showRenameDialog: Pair<String, String>? = null,
    val showManualConnectDialog: Boolean = false,
    // Dual-panel
    val localPanel: LocalPanelState = LocalPanelState(),
    val activePanel: PanelId = PanelId.TOP,
    val panelRatio: Float = 0.5f
)

@HiltViewModel
class FtpViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val ftpClientManager: FtpClientManager,
    private val credentialStore: FtpCredentialStore
) : ViewModel() {

    private val _state = MutableStateFlow(FtpUiState())
    val state: StateFlow<FtpUiState> = _state.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastMessage = _toastMessage.asSharedFlow()

    private var transferJob: Job? = null

    init {
        loadSavedServers()
    }

    private fun loadSavedServers() {
        viewModelScope.launch {
            val creds = credentialStore.listAll()
            _state.update {
                it.copy(savedServers = creds.map { c ->
                    FtpSavedServer(c.host, c.port, c.displayName, c.useFtps)
                })
            }
        }
    }

    fun onSavedServerTap(server: FtpSavedServer) {
        val savedCred = credentialStore.load(server.host, server.port)
        if (savedCred != null) {
            connectWithCredential(savedCred, save = false)
        } else {
            _state.update {
                it.copy(
                    showAuthDialog = true,
                    authDialogHost = server.host,
                    authDialogPort = server.port,
                    error = null
                )
            }
        }
    }

    fun onShowManualConnect() {
        _state.update { it.copy(showManualConnectDialog = true) }
    }

    fun onDismissManualConnect() {
        _state.update { it.copy(showManualConnectDialog = false) }
    }

    fun onManualConnect(host: String, port: Int) {
        _state.update {
            it.copy(
                showManualConnectDialog = false,
                showAuthDialog = true,
                authDialogHost = host.trim(),
                authDialogPort = port,
                error = null
            )
        }
    }

    fun onConnect(credential: FtpCredential, save: Boolean) {
        connectWithCredential(credential, save)
    }

    fun onConnectAnonymous(host: String, port: Int) {
        EcosystemLogger.d(HaronConstants.TAG, "FtpVM: anonymous connect to $host:$port")
        _state.update { it.copy(isConnecting = true, showAuthDialog = false, error = null) }
        viewModelScope.launch {
            val result = ftpClientManager.connectAnonymous(host, port)
            if (result.isSuccess) {
                EcosystemLogger.d(HaronConstants.TAG, "FtpVM: anonymous connected to $host")
                _state.update {
                    it.copy(
                        isConnecting = false,
                        connectedHost = host,
                        connectedPort = port,
                        serverListMode = false,
                        currentPath = "/"
                    )
                }
                loadFiles(host, port, "/")
                loadLocalFiles()
            } else {
                val msg = result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                EcosystemLogger.e(HaronConstants.TAG, "FtpVM: anonymous connect failed: $msg")
                _state.update {
                    it.copy(
                        isConnecting = false,
                        error = msg,
                        showAuthDialog = true,
                        authDialogHost = host,
                        authDialogPort = port
                    )
                }
                _toastMessage.emit(appContext.getString(R.string.ftp_connection_failed, msg))
            }
        }
    }

    fun dismissAuthDialog() {
        _state.update { it.copy(showAuthDialog = false) }
    }

    fun onFileTap(file: FtpFileInfo) {
        if (_state.value.selectedFiles.isNotEmpty()) {
            toggleSelection(file.path)
            return
        }
        if (file.isDirectory) {
            navigateToFolder(file)
        } else {
            downloadSingleFile(file)
        }
    }

    fun onFileLongPress(file: FtpFileInfo) {
        toggleSelection(file.path)
    }

    private fun toggleSelection(path: String) {
        _state.update {
            val newSelection = it.selectedFiles.toMutableSet()
            if (newSelection.contains(path)) newSelection.remove(path) else newSelection.add(path)
            it.copy(selectedFiles = newSelection)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedFiles = emptySet()) }
    }

    fun onNavigateUp(): Boolean {
        val s = _state.value
        if (s.serverListMode) return false

        if (s.currentPath != "/" && s.currentPath.isNotEmpty()) {
            val parentPath = s.currentPath.substringBeforeLast("/", "/")
            val actualParent = if (parentPath.isEmpty()) "/" else parentPath
            _state.update { it.copy(currentPath = actualParent) }
            loadFiles(s.connectedHost!!, s.connectedPort, actualParent)
            return true
        }

        onDisconnect()
        return true
    }

    fun navigateToBreadcrumb(index: Int) {
        val s = _state.value
        val host = s.connectedHost ?: return

        // index 0 = host → disconnect
        if (index == 0) {
            onDisconnect()
            return
        }

        // index 1+ = path segments
        val pathParts = s.currentPath.split("/").filter { it.isNotEmpty() }
        val targetParts = pathParts.take(index)
        val targetPath = "/" + targetParts.joinToString("/")
        _state.update { it.copy(currentPath = targetPath) }
        loadFiles(host, s.connectedPort, targetPath)
    }

    fun getBreadcrumbs(): List<String> {
        val s = _state.value
        val crumbs = mutableListOf<String>()
        val host = s.connectedHost ?: return crumbs
        val portSuffix = if (s.connectedPort != HaronConstants.FTP_DEFAULT_PORT) ":${s.connectedPort}" else ""
        crumbs.add("$host$portSuffix")
        if (s.currentPath.isNotEmpty() && s.currentPath != "/") {
            crumbs.addAll(s.currentPath.split("/").filter { it.isNotEmpty() })
        }
        return crumbs
    }

    fun downloadToLocalPanel() {
        val s = _state.value
        val host = s.connectedHost ?: return
        val port = s.connectedPort
        val selected = s.selectedFiles.toList()
        val destDir = File(s.localPanel.currentPath)

        EcosystemLogger.d(HaronConstants.TAG, "FtpVM: download to local panel ${selected.size} files")
        transferJob = viewModelScope.launch {
            for (remotePath in selected) {
                val fileName = remotePath.substringAfterLast("/")
                val localDest = File(destDir, fileName)
                ftpClientManager.downloadFile(host, port, remotePath, localDest)
                    .catch { e ->
                        _toastMessage.emit(e.localizedMessage ?: "Download error")
                    }
                    .collect { progress ->
                        _state.update { it.copy(transferProgress = progress) }
                    }
            }
            _state.update { it.copy(transferProgress = null, selectedFiles = emptySet()) }
            _toastMessage.emit(appContext.getString(R.string.ftp_download) + ": ${selected.size}")
            loadLocalFiles()
        }
    }

    fun uploadFromLocalPanel() {
        val s = _state.value
        val host = s.connectedHost ?: return
        val port = s.connectedPort
        val remoteDirPath = s.currentPath
        val selected = s.localPanel.selectedPaths.toList()

        EcosystemLogger.d(HaronConstants.TAG, "FtpVM: upload from local panel ${selected.size} files")
        transferJob = viewModelScope.launch {
            for (path in selected) {
                val file = File(path)
                if (!file.exists()) continue
                val remotePath = "$remoteDirPath/${file.name}".replace("//", "/")
                ftpClientManager.uploadFile(host, port, remotePath, file)
                    .catch { e ->
                        _toastMessage.emit(e.localizedMessage ?: "Upload error")
                    }
                    .collect { progress ->
                        _state.update { it.copy(transferProgress = progress) }
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
        val host = s.connectedHost ?: return
        val port = s.connectedPort
        val folderPath = "${s.currentPath}/$name".replace("//", "/")

        EcosystemLogger.d(HaronConstants.TAG, "FtpVM: create folder $folderPath on $host:$port")
        _state.update { it.copy(showCreateFolderDialog = false) }
        viewModelScope.launch {
            val result = ftpClientManager.createDirectory(host, port, folderPath)
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
        val host = s.connectedHost ?: return
        val port = s.connectedPort
        val selected = s.selectedFiles.toList()
        val filesList = s.files

        EcosystemLogger.d(HaronConstants.TAG, "FtpVM: delete ${selected.size} items on $host:$port")
        viewModelScope.launch {
            var deletedCount = 0
            for (path in selected) {
                val fileInfo = filesList.find { it.path == path }
                val isDir = fileInfo?.isDirectory ?: false
                val result = ftpClientManager.delete(host, port, path, isDir)
                if (result.isSuccess) deletedCount++
            }
            _state.update { it.copy(selectedFiles = emptySet()) }
            _toastMessage.emit(appContext.getString(R.string.deleted_count, deletedCount))
            refreshFiles()
        }
    }

    fun onRename(path: String, newName: String) {
        val s = _state.value
        val host = s.connectedHost ?: return
        val port = s.connectedPort

        _state.update { it.copy(showRenameDialog = null) }
        viewModelScope.launch {
            val result = ftpClientManager.rename(host, port, path, newName)
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
        val host = _state.value.connectedHost
        val port = _state.value.connectedPort
        if (host != null) {
            EcosystemLogger.d(HaronConstants.TAG, "FtpVM: disconnect from $host:$port")
            ftpClientManager.disconnect(host, port)
        }
        _state.update {
            it.copy(
                serverListMode = true,
                connectedHost = null,
                currentPath = "/",
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

    fun onRemoveSavedServer(host: String, port: Int) {
        credentialStore.remove(host, port)
        loadSavedServers()
    }

    fun refreshFiles() {
        val s = _state.value
        val host = s.connectedHost ?: return
        loadFiles(host, s.connectedPort, s.currentPath)
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
                    it.copy(
                        localPanel = it.localPanel.copy(
                            currentPath = path,
                            files = entries,
                            isLoading = false,
                            selectedPaths = emptySet()
                        )
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        localPanel = it.localPanel.copy(
                            isLoading = false,
                            error = e.localizedMessage ?: "Error"
                        )
                    )
                }
            }
        }
    }

    fun onLocalFileTap(file: LocalFileEntry) {
        if (_state.value.localPanel.selectedPaths.isNotEmpty()) {
            toggleLocalSelection(file.path)
            return
        }
        if (file.isDirectory) {
            loadLocalFiles(file.path)
        }
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
        if (_state.value.activePanel == PanelId.BOTTOM) {
            createLocalFolder(name)
        } else {
            onCreateFolder(name)
        }
    }

    fun onDeleteSelectedInActivePanel() {
        if (_state.value.activePanel == PanelId.BOTTOM) {
            deleteLocalSelected()
        } else {
            onDeleteSelected()
        }
    }

    fun onRenameInActivePanel(path: String, newName: String) {
        if (_state.value.activePanel == PanelId.BOTTOM) {
            renameLocal(path, newName)
        } else {
            onRename(path, newName)
        }
    }

    fun onNavigateUpActivePanel(): Boolean {
        return if (_state.value.activePanel == PanelId.BOTTOM) {
            navigateLocalUp()
        } else {
            onNavigateUp()
        }
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

    private fun connectWithCredential(credential: FtpCredential, save: Boolean) {
        EcosystemLogger.d(HaronConstants.TAG, "FtpVM: connect to ${credential.host}:${credential.port} user=${credential.username}")
        _state.update { it.copy(isConnecting = true, showAuthDialog = false, error = null) }
        viewModelScope.launch {
            val result = ftpClientManager.connect(credential)
            if (result.isSuccess) {
                EcosystemLogger.d(HaronConstants.TAG, "FtpVM: connected to ${credential.host}")
                if (save) {
                    credentialStore.save(credential)
                    loadSavedServers()
                }
                _state.update {
                    it.copy(
                        isConnecting = false,
                        connectedHost = credential.host,
                        connectedPort = credential.port,
                        serverListMode = false,
                        currentPath = "/"
                    )
                }
                loadFiles(credential.host, credential.port, "/")
                loadLocalFiles()
            } else {
                val msg = result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                EcosystemLogger.e(HaronConstants.TAG, "FtpVM: connect failed: $msg")
                _state.update {
                    it.copy(
                        isConnecting = false,
                        error = msg,
                        showAuthDialog = true,
                        authDialogHost = credential.host,
                        authDialogPort = credential.port
                    )
                }
                _toastMessage.emit(appContext.getString(R.string.ftp_connection_failed, msg))
            }
        }
    }

    private fun loadFiles(host: String, port: Int, path: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = ftpClientManager.listFiles(host, port, path)
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

    private fun navigateToFolder(file: FtpFileInfo) {
        val s = _state.value
        val host = s.connectedHost ?: return
        _state.update { it.copy(currentPath = file.path) }
        loadFiles(host, s.connectedPort, file.path)
    }

    private fun downloadSingleFile(file: FtpFileInfo) {
        val s = _state.value
        val host = s.connectedHost ?: return
        val port = s.connectedPort
        val destDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
        val localDest = File(destDir, file.name)

        transferJob = viewModelScope.launch {
            ftpClientManager.downloadFile(host, port, file.path, localDest)
                .catch { e ->
                    _toastMessage.emit(e.localizedMessage ?: "Download error")
                    _state.update { it.copy(transferProgress = null) }
                }
                .collect { progress ->
                    _state.update { it.copy(transferProgress = progress) }
                }
            _state.update { it.copy(transferProgress = null) }
            _toastMessage.emit(appContext.getString(R.string.ftp_download) + ": ${file.name}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        ftpClientManager.disconnectAll()
    }
}
