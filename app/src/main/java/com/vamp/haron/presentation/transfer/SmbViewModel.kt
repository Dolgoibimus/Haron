package com.vamp.haron.presentation.transfer

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.R
import com.vamp.haron.data.network.NetworkDevice
import com.vamp.haron.data.network.NetworkDeviceScanner
import com.vamp.haron.data.network.NetworkDeviceType
import com.vamp.haron.data.smb.SmbCredential
import com.vamp.haron.data.smb.SmbCredentialStore
import com.vamp.haron.data.smb.SmbFileInfo
import com.vamp.haron.data.smb.SmbManager
import com.vamp.haron.data.smb.SmbShareInfo
import com.vamp.haron.data.smb.SmbTransferProgress
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

data class SmbSavedServer(
    val host: String,
    val name: String = host
)

data class SmbNavEntry(
    val host: String,
    val share: String,
    val path: String
)

data class SmbUiState(
    val serverListMode: Boolean = true,
    val discoveredServers: List<NetworkDevice> = emptyList(),
    val savedServers: List<SmbSavedServer> = emptyList(),
    val connectedHost: String? = null,
    val connectedPort: Int = 445,
    val currentShare: String? = null,
    val currentPath: String = "",
    val files: List<SmbFileInfo> = emptyList(),
    val shares: List<SmbShareInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAuthDialog: Boolean = false,
    val authDialogHost: String? = null,
    val authDialogPort: Int = 445,
    val authDialogDeviceName: String? = null,
    val isConnecting: Boolean = false,
    val selectedFiles: Set<String> = emptySet(),
    val transferProgress: SmbTransferProgress? = null,
    val showCreateFolderDialog: Boolean = false,
    val showRenameDialog: Pair<String, String>? = null,
    val showManualConnectDialog: Boolean = false,
    val navigationHistory: List<SmbNavEntry> = emptyList(),
    // Dual-panel (after connection)
    val localPanel: LocalPanelState = LocalPanelState(),
    val activePanel: PanelId = PanelId.TOP,
    val panelRatio: Float = 0.5f
)

@HiltViewModel
class SmbViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val smbManager: SmbManager,
    private val credentialStore: SmbCredentialStore,
    private val networkDeviceScanner: NetworkDeviceScanner
) : ViewModel() {

    private val _state = MutableStateFlow(SmbUiState())
    val state: StateFlow<SmbUiState> = _state.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastMessage = _toastMessage.asSharedFlow()

    private var transferJob: Job? = null

    init {
        viewModelScope.launch {
            networkDeviceScanner.devices.collect { devices ->
                val smbDevices = devices.filter { it.type == NetworkDeviceType.SMB }
                _state.update { it.copy(discoveredServers = smbDevices) }
            }
        }
        loadSavedServers()
    }

    private fun loadSavedServers() {
        viewModelScope.launch {
            val hosts = credentialStore.listSavedHosts()
            _state.update {
                it.copy(savedServers = hosts.map { h -> SmbSavedServer(h) })
            }
        }
    }

    fun onServerTap(device: NetworkDevice) {
        val host = device.address
        val port = device.port
        val savedCred = credentialStore.load(host)
        if (savedCred != null) {
            connectWithCredential(host, port, savedCred, save = false)
        } else {
            _state.update {
                it.copy(
                    showAuthDialog = true,
                    authDialogHost = host,
                    authDialogPort = port,
                    authDialogDeviceName = device.displayName,
                    error = null
                )
            }
        }
    }

    fun onSavedServerTap(host: String) {
        val savedCred = credentialStore.load(host)
        if (savedCred != null) {
            connectWithCredential(host, 445, savedCred, save = false)
        } else {
            _state.update {
                it.copy(
                    showAuthDialog = true,
                    authDialogHost = host,
                    authDialogPort = 445,
                    authDialogDeviceName = host,
                    error = null
                )
            }
        }
    }

    fun onConnect(host: String, port: Int, credential: SmbCredential, save: Boolean) {
        connectWithCredential(host, port, credential, save)
    }

    fun onConnectAsGuest(host: String, port: Int) {
        EcosystemLogger.d(HaronConstants.TAG, "SmbVM: guest connect to $host:$port")
        _state.update { it.copy(isConnecting = true, showAuthDialog = false, error = null) }
        viewModelScope.launch {
            val result = smbManager.connectAsGuest(host, port)
            if (result.isSuccess) {
                EcosystemLogger.d(HaronConstants.TAG, "SmbVM: guest connected to $host")
                _state.update { it.copy(isConnecting = false, connectedHost = host, connectedPort = port) }
                loadShares(host)
                loadLocalFiles()
            } else {
                val msg = result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                EcosystemLogger.e(HaronConstants.TAG, "SmbVM: guest connect failed to $host: $msg")
                _state.update {
                    it.copy(
                        isConnecting = false,
                        error = msg,
                        showAuthDialog = true,
                        authDialogHost = host,
                        authDialogPort = port
                    )
                }
                _toastMessage.emit(appContext.getString(R.string.smb_connection_failed, msg))
            }
        }
    }

    fun onManualConnect(ip: String) {
        _state.update {
            it.copy(
                showManualConnectDialog = false,
                showAuthDialog = true,
                authDialogHost = ip.trim(),
                authDialogPort = 445,
                authDialogDeviceName = ip.trim()
            )
        }
    }

    fun onShowManualConnect() {
        _state.update { it.copy(showManualConnectDialog = true) }
    }

    fun onDismissManualConnect() {
        _state.update { it.copy(showManualConnectDialog = false) }
    }

    fun dismissAuthDialog() {
        _state.update { it.copy(showAuthDialog = false) }
    }

    fun onShareTap(share: SmbShareInfo) {
        val host = _state.value.connectedHost ?: return
        EcosystemLogger.d(HaronConstants.TAG, "SmbVM: open share ${share.name} on $host")
        _state.update {
            it.copy(
                currentShare = share.name,
                currentPath = "",
                serverListMode = false,
                navigationHistory = it.navigationHistory + SmbNavEntry(host, share.name, "")
            )
        }
        loadFiles(host, share.name, "")
    }

    fun onFileTap(file: SmbFileInfo) {
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

    fun onFileLongPress(file: SmbFileInfo) {
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

        if (s.currentPath.isNotEmpty()) {
            val parentPath = s.currentPath.substringBeforeLast("\\", "")
            _state.update { it.copy(currentPath = parentPath) }
            loadFiles(s.connectedHost!!, s.currentShare!!, parentPath)
            return true
        }

        if (s.currentShare != null) {
            _state.update { it.copy(currentShare = null, currentPath = "", files = emptyList()) }
            loadShares(s.connectedHost!!)
            return true
        }

        onDisconnect()
        return true
    }

    fun navigateToBreadcrumb(index: Int) {
        val s = _state.value
        val host = s.connectedHost ?: return

        // index 0 = host → back to share list
        if (index == 0) {
            _state.update { it.copy(currentShare = null, currentPath = "", files = emptyList()) }
            loadShares(host)
            return
        }
        // index 1 = share name → navigate to share root
        val share = s.currentShare ?: return
        if (index == 1) {
            _state.update { it.copy(currentPath = "", files = emptyList()) }
            loadFiles(host, share, "")
            return
        }

        // index 2+ = path segments (crumbs: [host(0), share(1), seg0(2), seg1(3), ...])
        val pathParts = s.currentPath.split("\\").filter { it.isNotEmpty() }
        val targetParts = pathParts.take(index - 1)
        val targetPath = targetParts.joinToString("\\")
        _state.update { it.copy(currentPath = targetPath) }
        loadFiles(host, share, targetPath)
    }

    fun getBreadcrumbs(): List<String> {
        val s = _state.value
        val crumbs = mutableListOf<String>()
        val host = s.connectedHost ?: return crumbs
        crumbs.add(host)
        val share = s.currentShare ?: return crumbs
        crumbs.add(share)
        if (s.currentPath.isNotEmpty()) {
            crumbs.addAll(s.currentPath.split("\\").filter { it.isNotEmpty() })
        }
        return crumbs
    }

    fun onDownloadSelected(destPath: String) {
        val s = _state.value
        val host = s.connectedHost ?: return
        val share = s.currentShare ?: return
        val selected = s.selectedFiles.toList()
        val destDir = File(destPath)

        EcosystemLogger.d(HaronConstants.TAG, "SmbVM: download ${selected.size} files from $host/$share to $destPath")
        transferJob = viewModelScope.launch {
            for (remotePath in selected) {
                val fileName = remotePath.substringAfterLast("\\")
                val localDest = File(destDir, fileName)
                smbManager.downloadFile(host, share, remotePath, localDest)
                    .catch { e ->
                        EcosystemLogger.e(HaronConstants.TAG, "SmbVM: download error $fileName: ${e.message}")
                        _toastMessage.emit(e.localizedMessage ?: "Download error")
                    }
                    .collect { progress ->
                        _state.update { it.copy(transferProgress = progress) }
                    }
            }
            EcosystemLogger.d(HaronConstants.TAG, "SmbVM: download complete ${selected.size} files")
            _state.update { it.copy(transferProgress = null, selectedFiles = emptySet()) }
            _toastMessage.emit(appContext.getString(R.string.smb_download) + ": ${selected.size}")
        }
    }

    fun onUploadFiles(localFiles: List<File>) {
        val s = _state.value
        val host = s.connectedHost ?: return
        val share = s.currentShare ?: return
        val remoteDirPath = s.currentPath

        EcosystemLogger.d(HaronConstants.TAG, "SmbVM: upload ${localFiles.size} files to $host/$share/$remoteDirPath")
        transferJob = viewModelScope.launch {
            for (file in localFiles) {
                val remotePath = if (remoteDirPath.isEmpty()) file.name
                else "$remoteDirPath\\${file.name}"
                smbManager.uploadFile(host, share, remotePath, file)
                    .catch { e ->
                        EcosystemLogger.e(HaronConstants.TAG, "SmbVM: upload error ${file.name}: ${e.message}")
                        _toastMessage.emit(e.localizedMessage ?: "Upload error")
                    }
                    .collect { progress ->
                        _state.update { it.copy(transferProgress = progress) }
                    }
            }
            EcosystemLogger.d(HaronConstants.TAG, "SmbVM: upload complete ${localFiles.size} files")
            _state.update { it.copy(transferProgress = null) }
            _toastMessage.emit(appContext.getString(R.string.smb_upload) + ": ${localFiles.size}")
            refreshFiles()
        }
    }

    fun onCreateFolder(name: String) {
        val s = _state.value
        val host = s.connectedHost ?: return
        val share = s.currentShare ?: return
        val folderPath = if (s.currentPath.isEmpty()) name else "${s.currentPath}\\$name"

        EcosystemLogger.d(HaronConstants.TAG, "SmbVM: create folder $folderPath on $host/$share")
        _state.update { it.copy(showCreateFolderDialog = false) }
        viewModelScope.launch {
            val result = smbManager.createDirectory(host, share, folderPath)
            if (result.isSuccess) {
                _toastMessage.emit(appContext.getString(R.string.folder_created))
                refreshFiles()
            } else {
                EcosystemLogger.e(HaronConstants.TAG, "SmbVM: create folder failed: ${result.exceptionOrNull()?.message}")
                _toastMessage.emit(result.exceptionOrNull()?.localizedMessage ?: "Error")
            }
        }
    }

    fun onDeleteSelected() {
        val s = _state.value
        val host = s.connectedHost ?: return
        val share = s.currentShare ?: return
        val selected = s.selectedFiles.toList()
        val filesList = s.files

        EcosystemLogger.d(HaronConstants.TAG, "SmbVM: delete ${selected.size} items on $host/$share")
        viewModelScope.launch {
            var deletedCount = 0
            for (path in selected) {
                val fileInfo = filesList.find { it.path == path }
                val isDir = fileInfo?.isDirectory ?: false
                val result = smbManager.delete(host, share, path, isDir)
                if (result.isSuccess) deletedCount++
            }
            EcosystemLogger.d(HaronConstants.TAG, "SmbVM: deleted $deletedCount/${selected.size} items")
            _state.update { it.copy(selectedFiles = emptySet()) }
            _toastMessage.emit(appContext.getString(R.string.deleted_count, deletedCount))
            refreshFiles()
        }
    }

    fun onRename(path: String, newName: String) {
        val s = _state.value
        val host = s.connectedHost ?: return
        val share = s.currentShare ?: return

        _state.update { it.copy(showRenameDialog = null) }
        viewModelScope.launch {
            val result = smbManager.rename(host, share, path, newName)
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
        if (host != null) {
            EcosystemLogger.d(HaronConstants.TAG, "SmbVM: disconnect from $host")
            smbManager.disconnect(host)
        }
        _state.update {
            it.copy(
                serverListMode = true,
                connectedHost = null,
                currentShare = null,
                currentPath = "",
                files = emptyList(),
                shares = emptyList(),
                selectedFiles = emptySet(),
                transferProgress = null,
                navigationHistory = emptyList(),
                error = null,
                localPanel = LocalPanelState(),
                activePanel = PanelId.TOP,
                panelRatio = 0.5f
            )
        }
    }

    fun onRemoveSavedServer(host: String) {
        credentialStore.remove(host)
        loadSavedServers()
    }

    fun onRefreshServers() {
        networkDeviceScanner.refreshDevices()
        loadSavedServers()
    }

    fun refreshFiles() {
        val s = _state.value
        val host = s.connectedHost ?: return
        val share = s.currentShare
        if (share != null) {
            loadFiles(host, share, s.currentPath)
        } else {
            loadShares(host)
        }
    }

    fun cancelTransfer() {
        transferJob?.cancel()
        transferJob = null
        _state.update { it.copy(transferProgress = null) }
    }

    fun getDownloadDir(): String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
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
                EcosystemLogger.e(HaronConstants.TAG, "SmbVM: load local files failed: ${e.message}")
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

    // --- Cross-panel operations ---

    fun downloadToLocalPanel() {
        val s = _state.value
        val host = s.connectedHost ?: return
        val share = s.currentShare ?: return
        val selected = s.selectedFiles.toList()
        val destDir = File(s.localPanel.currentPath)

        EcosystemLogger.d(HaronConstants.TAG, "SmbVM: download to local panel ${selected.size} files")
        transferJob = viewModelScope.launch {
            for (remotePath in selected) {
                val fileName = remotePath.substringAfterLast("\\")
                val localDest = File(destDir, fileName)
                smbManager.downloadFile(host, share, remotePath, localDest)
                    .catch { e ->
                        _toastMessage.emit(e.localizedMessage ?: "Download error")
                    }
                    .collect { progress ->
                        _state.update { it.copy(transferProgress = progress) }
                    }
            }
            _state.update { it.copy(transferProgress = null, selectedFiles = emptySet()) }
            _toastMessage.emit(appContext.getString(R.string.smb_download) + ": ${selected.size}")
            loadLocalFiles()
        }
    }

    fun uploadFromLocalPanel() {
        val s = _state.value
        val host = s.connectedHost ?: return
        val share = s.currentShare ?: return
        val remoteDirPath = s.currentPath
        val selected = s.localPanel.selectedPaths.toList()

        EcosystemLogger.d(HaronConstants.TAG, "SmbVM: upload from local panel ${selected.size} files")
        transferJob = viewModelScope.launch {
            for (path in selected) {
                val file = File(path)
                if (!file.exists()) continue
                val remotePath = if (remoteDirPath.isEmpty()) file.name
                else "$remoteDirPath\\${file.name}"
                smbManager.uploadFile(host, share, remotePath, file)
                    .catch { e ->
                        _toastMessage.emit(e.localizedMessage ?: "Upload error")
                    }
                    .collect { progress ->
                        _state.update { it.copy(transferProgress = progress) }
                    }
            }
            _state.update { it.copy(transferProgress = null) }
            _toastMessage.emit(appContext.getString(R.string.smb_upload) + ": ${selected.size}")
            clearLocalSelection()
            refreshFiles()
        }
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

    private fun connectWithCredential(host: String, port: Int, credential: SmbCredential, save: Boolean) {
        EcosystemLogger.d(HaronConstants.TAG, "SmbVM: connect to $host:$port user=${credential.username}")
        _state.update { it.copy(isConnecting = true, showAuthDialog = false, error = null) }
        viewModelScope.launch {
            val result = smbManager.connect(host, port, credential)
            if (result.isSuccess) {
                EcosystemLogger.d(HaronConstants.TAG, "SmbVM: connected to $host")
                if (save) {
                    credentialStore.save(host, credential)
                    loadSavedServers()
                }
                _state.update { it.copy(isConnecting = false, connectedHost = host, connectedPort = port) }
                loadShares(host)
                loadLocalFiles()
            } else {
                val msg = result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                EcosystemLogger.e(HaronConstants.TAG, "SmbVM: connect failed to $host: $msg")
                _state.update {
                    it.copy(
                        isConnecting = false,
                        error = msg,
                        showAuthDialog = true,
                        authDialogHost = host,
                        authDialogPort = port
                    )
                }
                _toastMessage.emit(appContext.getString(R.string.smb_connection_failed, msg))
            }
        }
    }

    private fun loadShares(host: String) {
        _state.update { it.copy(isLoading = true, serverListMode = false, error = null) }
        viewModelScope.launch {
            val result = smbManager.listShares(host)
            if (result.isSuccess) {
                val shares = result.getOrDefault(emptyList())
                EcosystemLogger.d(HaronConstants.TAG, "SmbVM: loaded ${shares.size} shares from $host")
                _state.update {
                    it.copy(
                        isLoading = false,
                        shares = shares,
                        currentShare = null,
                        currentPath = "",
                        files = emptyList()
                    )
                }
            } else {
                val msg = result.exceptionOrNull()?.localizedMessage ?: "Error"
                EcosystemLogger.e(HaronConstants.TAG, "SmbVM: load shares failed from $host: $msg")
                _state.update { it.copy(isLoading = false, error = msg) }
            }
        }
    }

    private fun loadFiles(host: String, share: String, path: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = smbManager.listFiles(host, share, path)
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
                EcosystemLogger.e(HaronConstants.TAG, "SmbVM: list files failed $host/$share/$path: $msg")
                _state.update { it.copy(isLoading = false, error = msg) }
            }
        }
    }

    private fun navigateToFolder(file: SmbFileInfo) {
        val s = _state.value
        val host = s.connectedHost ?: return
        val share = s.currentShare ?: return
        val newPath = file.path
        _state.update { it.copy(currentPath = newPath) }
        loadFiles(host, share, newPath)
    }

    private fun downloadSingleFile(file: SmbFileInfo) {
        val s = _state.value
        val host = s.connectedHost ?: return
        val share = s.currentShare ?: return
        val destDir = File(getDownloadDir())
        val localDest = File(destDir, file.name)

        EcosystemLogger.d(HaronConstants.TAG, "SmbVM: download file ${file.name} from $host/$share")
        transferJob = viewModelScope.launch {
            smbManager.downloadFile(host, share, file.path, localDest)
                .catch { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "SmbVM: download error ${file.name}: ${e.message}")
                    _toastMessage.emit(e.localizedMessage ?: "Download error")
                    _state.update { it.copy(transferProgress = null) }
                }
                .collect { progress ->
                    _state.update { it.copy(transferProgress = progress) }
                }
            _state.update { it.copy(transferProgress = null) }
            _toastMessage.emit(appContext.getString(R.string.smb_download) + ": ${file.name}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        smbManager.disconnectAll()
    }
}
