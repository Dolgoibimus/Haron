package com.vamp.haron.presentation.archive

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.haron.R
import com.vamp.haron.domain.model.ArchiveEntry
import com.vamp.haron.domain.usecase.BrowseArchiveUseCase
import com.vamp.haron.domain.usecase.ExtractArchiveUseCase
import com.vamp.haron.domain.usecase.ExtractProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArchiveViewerState(
    val archivePath: String = "",
    val archiveName: String = "",
    val virtualPath: String = "",
    val entries: List<ArchiveEntry> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedEntries: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val extractProgress: ExtractProgress? = null,
    val breadcrumbs: List<String> = listOf(""),
    val showPasswordDialog: Boolean = false,
    val password: String? = null,
    val passwordError: String? = null
)

@HiltViewModel
class ArchiveViewerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val browseArchiveUseCase: BrowseArchiveUseCase,
    private val extractArchiveUseCase: ExtractArchiveUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ArchiveViewerState())
    val state = _state.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage = _toastMessage.asSharedFlow()

    private val _closeEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closeEvent = _closeEvent.asSharedFlow()

    fun init(archivePath: String, archiveName: String) {
        _state.update { it.copy(archivePath = archivePath, archiveName = archiveName) }
        loadEntries("")
    }

    fun navigateInto(dirPath: String) {
        val newPath = dirPath
        val crumbs = if (newPath.isEmpty()) {
            listOf("")
        } else {
            listOf("") + newPath.split('/').runningReduce { acc, part -> "$acc/$part" }
        }
        _state.update { it.copy(virtualPath = newPath, breadcrumbs = crumbs) }
        loadEntries(newPath)
    }

    fun navigateUp(): Boolean {
        val current = _state.value.virtualPath
        if (current.isEmpty()) return false
        val parent = current.substringBeforeLast('/', "")
        navigateInto(parent)
        return true
    }

    fun navigateToBreadcrumb(path: String) {
        navigateInto(path)
    }

    fun toggleSelection(entryPath: String) {
        _state.update { s ->
            val newSet = s.selectedEntries.toMutableSet()
            if (entryPath in newSet) newSet.remove(entryPath) else newSet.add(entryPath)
            s.copy(
                selectedEntries = newSet,
                isSelectionMode = newSet.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedEntries = emptySet(), isSelectionMode = false) }
    }

    fun onPasswordSubmit(password: String) {
        _state.update { it.copy(password = password, showPasswordDialog = false, passwordError = null) }
        loadEntries(_state.value.virtualPath)
    }

    fun onPasswordDismiss() {
        _state.update { it.copy(showPasswordDialog = false) }
    }

    fun extractAll(destinationDir: String) {
        extract(destinationDir, null)
    }

    fun extractSelected(destinationDir: String) {
        val selected = _state.value.selectedEntries
        if (selected.isEmpty()) return
        extract(destinationDir, selected)
    }

    private fun extract(destinationDir: String, selectedEntries: Set<String>?) {
        val archivePath = _state.value.archivePath
        val password = _state.value.password
        viewModelScope.launch {
            extractArchiveUseCase(archivePath, destinationDir, selectedEntries, password).collect { progress ->
                _state.update { it.copy(extractProgress = progress) }
                if (progress.isComplete) {
                    _toastMessage.tryEmit(appContext.getString(R.string.extracted_to_format, destinationDir))
                    clearSelection()
                    _closeEvent.tryEmit(Unit)
                }
                if (progress.error != null) {
                    _toastMessage.tryEmit(appContext.getString(R.string.error_format, progress.error ?: ""))
                }
            }
            _state.update { it.copy(extractProgress = null) }
        }
    }

    private fun loadEntries(virtualPath: String) {
        val archivePath = _state.value.archivePath
        val password = _state.value.password
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            browseArchiveUseCase(archivePath, virtualPath, password)
                .onSuccess { entries ->
                    _state.update { it.copy(entries = entries, isLoading = false, passwordError = null) }
                }
                .onFailure { e ->
                    val msg = e.message ?: ""
                    val causeName = e.cause?.javaClass?.simpleName ?: ""
                    val isPasswordError = msg.contains("password", ignoreCase = true) ||
                        msg.contains("encrypted", ignoreCase = true) ||
                        msg.contains("пароль", ignoreCase = true) ||
                        e.javaClass.simpleName.contains("Encrypt", ignoreCase = true) ||
                        causeName.contains("Encrypt", ignoreCase = true)
                    if (isPasswordError) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = null,
                                showPasswordDialog = true,
                                passwordError = if (password != null) appContext.getString(R.string.wrong_password) else null
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = e.message ?: appContext.getString(R.string.archive_read_error)
                            )
                        }
                    }
                }
        }
    }
}
