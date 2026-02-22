package com.vamp.haron.presentation.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.common.util.iconRes
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.model.GalleryHolder
import com.vamp.haron.domain.model.NavigationEvent
import com.vamp.haron.domain.model.PlaylistHolder
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
    val previewError: String? = null,
    val previewAdjacentFiles: List<FileEntry> = emptyList(),
    val previewCurrentIndex: Int = 0,
    val previewCache: Map<Int, PreviewData> = emptyMap()
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

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>(extraBufferCapacity = 1)
    val navigationEvent = _navigationEvent.asSharedFlow()

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

    // --- Preview file (QuickPreview dialog, full-featured like main screen) ---

    fun loadPreview(path: String) {
        val file = File(path)
        if (!file.exists()) {
            _toastMessage.tryEmit("Файл не найден")
            return
        }

        // Build adjacent files list from the same duplicate group
        val st = _state.value
        val group = st.groups.find { g -> g.files.any { it.path == path } }
        val adjacentEntries = if (group != null) {
            group.files.map { f ->
                val ff = File(f.path)
                FileEntry(
                    name = ff.name, path = ff.absolutePath, isDirectory = false,
                    size = ff.length(), lastModified = ff.lastModified(),
                    extension = ff.extension.lowercase(), isHidden = ff.isHidden, childCount = 0
                )
            }
        } else {
            listOf(
                FileEntry(
                    name = file.name, path = file.absolutePath, isDirectory = false,
                    size = file.length(), lastModified = file.lastModified(),
                    extension = file.extension.lowercase(), isHidden = file.isHidden, childCount = 0
                )
            )
        }
        val currentIndex = adjacentEntries.indexOfFirst { it.path == path }.coerceAtLeast(0)
        val entry = adjacentEntries[currentIndex]

        _state.update {
            it.copy(
                previewEntry = entry,
                previewData = null,
                previewLoading = true,
                previewError = null,
                previewAdjacentFiles = adjacentEntries,
                previewCurrentIndex = currentIndex,
                previewCache = emptyMap()
            )
        }
        viewModelScope.launch {
            loadPreviewUseCase(entry)
                .onSuccess { data ->
                    _state.update {
                        it.copy(
                            previewData = data,
                            previewLoading = false,
                            previewCache = it.previewCache + (currentIndex to data)
                        )
                    }
                    preloadPreview(currentIndex - 1, adjacentEntries)
                    preloadPreview(currentIndex + 1, adjacentEntries)
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(previewLoading = false, previewError = e.message ?: "Ошибка загрузки")
                    }
                }
        }
    }

    fun onPreviewFileChanged(newIndex: Int) {
        val st = _state.value
        val files = st.previewAdjacentFiles
        if (newIndex !in files.indices) return
        val newEntry = files[newIndex]

        // Check cache
        val cached = st.previewCache[newIndex]
        if (cached != null) {
            _state.update {
                it.copy(
                    previewEntry = newEntry,
                    previewData = cached,
                    previewLoading = false,
                    previewError = null,
                    previewCurrentIndex = newIndex
                )
            }
            preloadPreview(newIndex - 1, files)
            preloadPreview(newIndex + 1, files)
            return
        }

        _state.update {
            it.copy(
                previewEntry = newEntry,
                previewData = null,
                previewLoading = true,
                previewError = null,
                previewCurrentIndex = newIndex
            )
        }
        viewModelScope.launch {
            loadPreviewUseCase(newEntry)
                .onSuccess { data ->
                    val current = _state.value
                    if (current.previewEntry?.path == newEntry.path) {
                        _state.update {
                            it.copy(
                                previewData = data,
                                previewLoading = false,
                                previewCache = it.previewCache + (newIndex to data)
                            )
                        }
                        preloadPreview(newIndex - 1, files)
                        preloadPreview(newIndex + 1, files)
                    }
                }
                .onFailure { e ->
                    val current = _state.value
                    if (current.previewEntry?.path == newEntry.path) {
                        _state.update {
                            it.copy(previewLoading = false, previewError = e.message ?: "Ошибка")
                        }
                    }
                }
        }
    }

    private fun preloadPreview(index: Int, files: List<FileEntry>) {
        if (index !in files.indices) return
        if (index in _state.value.previewCache) return
        viewModelScope.launch {
            loadPreviewUseCase(files[index])
                .onSuccess { data ->
                    _state.update {
                        it.copy(previewCache = it.previewCache + (index to data))
                    }
                }
        }
    }

    fun buildPlaylistFromPreview(entry: FileEntry, adjacentFiles: List<FileEntry>, currentIndex: Int): Int {
        val mediaFiles = adjacentFiles.filter { f ->
            !f.isDirectory && f.iconRes() in listOf("video", "audio")
        }
        PlaylistHolder.items = mediaFiles.map { f ->
            PlaylistHolder.PlaylistItem(filePath = f.path, fileName = f.name, fileType = f.iconRes())
        }
        val idx = mediaFiles.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
        PlaylistHolder.startIndex = idx
        return idx
    }

    fun buildGalleryFromPreview(entry: FileEntry, adjacentFiles: List<FileEntry>, currentIndex: Int): Int {
        val imageFiles = adjacentFiles.filter { f ->
            !f.isDirectory && f.iconRes() == "image"
        }
        GalleryHolder.items = imageFiles.map { f ->
            GalleryHolder.GalleryItem(filePath = f.path, fileName = f.name, fileSize = f.size)
        }
        val idx = imageFiles.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
        GalleryHolder.startIndex = idx
        return idx
    }

    /** Build playlist from all media files across all duplicate groups. Returns startIndex (0) or -1 if no media. */
    fun buildPlaylistFromAllGroups(): Int {
        val st = _state.value
        val mediaFiles = st.groups.flatMap { group ->
            group.files.filter { f ->
                val ext = f.name.substringAfterLast('.', "").lowercase()
                ext in listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "3gp", "3gpp", "ts", "m4v", "mts",
                    "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus")
            }
        }.distinctBy { it.path }
        if (mediaFiles.isEmpty()) {
            _toastMessage.tryEmit("Нет медиафайлов среди дубликатов")
            return -1
        }
        PlaylistHolder.items = mediaFiles.map { f ->
            val ext = f.name.substringAfterLast('.', "").lowercase()
            val type = if (ext in listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "3gp", "3gpp", "ts", "m4v", "mts")) "video" else "audio"
            PlaylistHolder.PlaylistItem(filePath = f.path, fileName = f.name, fileType = type)
        }
        PlaylistHolder.startIndex = 0
        return 0
    }

    fun dismissPreview() {
        _state.update {
            it.copy(
                previewEntry = null, previewData = null, previewLoading = false, previewError = null,
                previewAdjacentFiles = emptyList(), previewCurrentIndex = 0, previewCache = emptyMap()
            )
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
