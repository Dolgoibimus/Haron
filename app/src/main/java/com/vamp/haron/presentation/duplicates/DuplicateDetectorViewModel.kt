package com.vamp.haron.presentation.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.usecase.DuplicateGroup
import com.vamp.haron.domain.usecase.DuplicateScanProgress
import com.vamp.haron.domain.usecase.FindDuplicatesUseCase
import com.vamp.haron.domain.model.PreviewData
import com.vamp.haron.domain.usecase.LoadPreviewUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class DuplicateDetectorState(
    val isScanning: Boolean = false,
    val progress: DuplicateScanProgress? = null,
    val groups: List<DuplicateGroup> = emptyList(),
    val expandedGroupHash: String? = null,
    val selectedPaths: Set<String> = emptySet(),
    val totalWastedSpace: Long = 0L,
    val totalDuplicateFiles: Int = 0,
    val isDeleting: Boolean = false,
    val originalOverrides: Map<String, String> = emptyMap(),
    val originalFolders: Set<String> = emptySet(),
    val isOriginalFolderMode: Boolean = false,
    val allFolderPaths: List<String> = emptyList(),
    val previewEntry: FileEntry? = null,
    val previewData: PreviewData? = null,
    val previewLoading: Boolean = false,
    val previewError: String? = null
)

@HiltViewModel
class DuplicateDetectorViewModel @Inject constructor(
    private val findDuplicatesUseCase: FindDuplicatesUseCase,
    private val preferences: HaronPreferences,
    private val loadPreviewUseCase: LoadPreviewUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(DuplicateDetectorState())
    val state: StateFlow<DuplicateDetectorState> = _state.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage = _toastMessage.asSharedFlow()

    init {
        // Load persistent overrides and folders
        val savedOverrides = preferences.getOriginalOverrides()
        val savedFolders = preferences.getOriginalFolders()
        _state.update {
            it.copy(
                originalOverrides = savedOverrides,
                originalFolders = savedFolders
            )
        }
        startScan()
    }

    fun startScan() {
        _state.update {
            it.copy(
                isScanning = true,
                groups = emptyList(),
                selectedPaths = emptySet(),
                expandedGroupHash = null,
                progress = null,
                isOriginalFolderMode = false,
                allFolderPaths = emptyList()
                // NOTE: originalOverrides and originalFolders are NOT reset
            )
        }

        viewModelScope.launch {
            findDuplicatesUseCase().collect { progress ->
                _state.update { st ->
                    if (progress.isComplete) {
                        st.copy(
                            isScanning = false,
                            progress = progress,
                            groups = progress.groups,
                            totalWastedSpace = progress.groups.sumOf { it.wastedSpace },
                            totalDuplicateFiles = progress.groups.sumOf { it.files.size }
                        )
                    } else {
                        st.copy(progress = progress)
                    }
                }
            }
        }
    }

    fun toggleGroup(hash: String) {
        _state.update {
            it.copy(
                expandedGroupHash = if (it.expandedGroupHash == hash) null else hash
            )
        }
    }

    fun toggleFileSelection(path: String) {
        _state.update {
            val newSet = it.selectedPaths.toMutableSet()
            if (path in newSet) newSet.remove(path) else newSet.add(path)
            it.copy(selectedPaths = newSet)
        }
    }

    fun toggleSelectAll() {
        if (_state.value.selectedPaths.isNotEmpty()) {
            clearSelection()
        } else {
            keepOldestInAllGroups()
        }
    }

    /**
     * Priority for determining original:
     * 1. Individual override (originalOverrides[hash]) — highest
     * 2. File in marked folder (originalFolders) — oldest among those
     * 3. Oldest file (isOldest) — default
     */
    fun getOriginalForGroup(group: DuplicateGroup): String? {
        val st = _state.value

        // 1. Individual override
        val override = st.originalOverrides[group.hash]
        if (override != null && group.files.any { it.path == override }) {
            return override
        }

        // 2. File in marked original folder
        if (st.originalFolders.isNotEmpty()) {
            val inFolders = group.files.filter { file ->
                val dir = file.path.substringBeforeLast('/')
                st.originalFolders.any { folder -> dir == folder || dir.startsWith("$folder/") }
            }
            if (inFolders.isNotEmpty()) {
                return inFolders.minByOrNull { it.lastModified }?.path
            }
        }

        // 3. Default — oldest
        return group.files.firstOrNull { it.isOldest }?.path
    }

    fun reassignOriginal(hash: String, path: String) {
        _state.update { st ->
            val newOverrides = st.originalOverrides.toMutableMap()
            newOverrides[hash] = path
            st.copy(originalOverrides = newOverrides)
        }
        preferences.saveOriginalOverrides(_state.value.originalOverrides)
        _toastMessage.tryEmit("Оригинал переназначен")
    }

    fun keepOldestInAllGroups() {
        val st = _state.value
        val toSelect = mutableSetOf<String>()
        for (group in st.groups) {
            val originalPath = getOriginalForGroup(group)
            for (file in group.files) {
                if (file.path != originalPath) toSelect.add(file.path)
            }
        }
        _state.update { it.copy(selectedPaths = toSelect) }
    }

    fun keepOldestInGroup(hash: String) {
        val st = _state.value
        val group = st.groups.find { it.hash == hash } ?: return
        val originalPath = getOriginalForGroup(group)
        val currentSelected = st.selectedPaths.toMutableSet()
        group.files.forEach { currentSelected.remove(it.path) }
        group.files.forEach { file ->
            if (file.path != originalPath) currentSelected.add(file.path)
        }
        _state.update { it.copy(selectedPaths = currentSelected) }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedPaths = emptySet()) }
    }

    // --- Original folder mode ---

    fun enterOriginalFolderMode() {
        val st = _state.value
        val folders = st.groups
            .flatMap { group -> group.files.map { it.path.substringBeforeLast('/') } }
            .distinct()
            .sorted()
        _state.update {
            it.copy(
                isOriginalFolderMode = true,
                allFolderPaths = folders
            )
        }
    }

    fun exitOriginalFolderMode() {
        _state.update { it.copy(isOriginalFolderMode = false) }
    }

    fun toggleOriginalFolder(path: String) {
        _state.update { st ->
            val newFolders = st.originalFolders.toMutableSet()
            if (path in newFolders) newFolders.remove(path) else newFolders.add(path)
            st.copy(originalFolders = newFolders)
        }
        preferences.saveOriginalFolders(_state.value.originalFolders)
    }

    // --- Preview file (QuickPreview dialog) ---

    fun loadPreview(path: String) {
        val file = File(path)
        if (!file.exists()) {
            _toastMessage.tryEmit("Файл не найден")
            return
        }
        val entry = FileEntry(
            name = file.name,
            path = file.absolutePath,
            isDirectory = false,
            size = file.length(),
            lastModified = file.lastModified(),
            extension = file.extension.lowercase(),
            isHidden = file.isHidden,
            childCount = 0
        )
        _state.update {
            it.copy(previewEntry = entry, previewData = null, previewLoading = true, previewError = null)
        }
        viewModelScope.launch {
            loadPreviewUseCase(entry)
                .onSuccess { data ->
                    _state.update { it.copy(previewData = data, previewLoading = false) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(previewLoading = false, previewError = e.message ?: "Ошибка загрузки")
                    }
                }
        }
    }

    fun dismissPreview() {
        _state.update {
            it.copy(previewEntry = null, previewData = null, previewLoading = false, previewError = null)
        }
    }

    fun deleteSelected() {
        val paths = _state.value.selectedPaths.toList()
        if (paths.isEmpty()) return

        _state.update { it.copy(isDeleting = true) }

        viewModelScope.launch {
            var deleted = 0
            val deletedPaths = mutableSetOf<String>()
            for (path in paths) {
                try {
                    val file = File(path)
                    if (file.exists() && file.delete()) {
                        deleted++
                        deletedPaths.add(path)
                    }
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "Delete duplicate error: ${e.message}")
                }
            }
            _toastMessage.tryEmit("Удалено дубликатов: $deleted")
            _state.update { st ->
                val updatedGroups = st.groups.mapNotNull { group ->
                    val remaining = group.files.filter { it.path !in deletedPaths }
                    if (remaining.size < 2) null
                    else group.copy(files = remaining)
                }
                val cleanedOverrides = st.originalOverrides.filter { (_, path) -> path !in deletedPaths }
                st.copy(
                    isDeleting = false,
                    selectedPaths = emptySet(),
                    groups = updatedGroups,
                    totalWastedSpace = updatedGroups.sumOf { it.wastedSpace },
                    totalDuplicateFiles = updatedGroups.sumOf { it.files.size },
                    originalOverrides = cleanedOverrides
                )
            }
            // Persist cleaned overrides
            preferences.saveOriginalOverrides(_state.value.originalOverrides)
        }
    }
}
