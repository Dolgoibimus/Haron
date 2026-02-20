package com.vamp.haron.presentation.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.data.model.SortOrder
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.model.PanelId
import com.vamp.haron.domain.repository.FileRepository
import com.vamp.haron.domain.repository.TrashRepository
import com.vamp.haron.domain.usecase.CleanExpiredTrashUseCase
import com.vamp.haron.domain.usecase.CopyFilesUseCase
import com.vamp.haron.domain.usecase.CreateDirectoryUseCase
import com.vamp.haron.domain.usecase.CreateFileUseCase
import com.vamp.haron.domain.usecase.DeleteFilesUseCase
import com.vamp.haron.domain.usecase.EmptyTrashUseCase
import com.vamp.haron.domain.usecase.GetFilesUseCase
import com.vamp.haron.domain.usecase.MoveFilesUseCase
import com.vamp.haron.domain.usecase.MoveToTrashUseCase
import com.vamp.haron.domain.usecase.RenameFileUseCase
import com.vamp.haron.domain.usecase.RestoreFromTrashUseCase
import com.vamp.haron.presentation.explorer.state.DragState
import com.vamp.haron.presentation.explorer.state.DialogState
import com.vamp.haron.presentation.explorer.state.ExplorerUiState
import com.vamp.haron.presentation.explorer.state.FileTemplate
import com.vamp.haron.presentation.explorer.state.PanelUiState
import androidx.compose.ui.geometry.Offset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ExplorerViewModel @Inject constructor(
    private val getFilesUseCase: GetFilesUseCase,
    private val fileRepository: FileRepository,
    private val preferences: HaronPreferences,
    private val copyFilesUseCase: CopyFilesUseCase,
    private val moveFilesUseCase: MoveFilesUseCase,
    private val deleteFilesUseCase: DeleteFilesUseCase,
    private val renameFileUseCase: RenameFileUseCase,
    private val createDirectoryUseCase: CreateDirectoryUseCase,
    private val createFileUseCase: CreateFileUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase,
    private val restoreFromTrashUseCase: RestoreFromTrashUseCase,
    private val emptyTrashUseCase: EmptyTrashUseCase,
    private val cleanExpiredTrashUseCase: CleanExpiredTrashUseCase,
    private val trashRepository: TrashRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorerUiState())

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage = _toastMessage.asSharedFlow()
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    /** Selection snapshot at the start of a drag gesture (for non-contiguous multi-range). */
    private var dragBaseSelection: Set<String> = emptySet()

    init {
        val savedSort = preferences.getSortOrder()
        val showHidden = preferences.showHidden
        val panelRatio = preferences.panelRatio
        _uiState.update {
            it.copy(
                topPanel = it.topPanel.copy(sortOrder = savedSort, showHidden = showHidden),
                bottomPanel = it.bottomPanel.copy(sortOrder = savedSort, showHidden = showHidden),
                panelRatio = panelRatio,
                favorites = preferences.getFavorites(),
                recentPaths = preferences.getRecentPaths()
            )
        }
        navigateTo(PanelId.TOP, HaronConstants.ROOT_PATH)
        navigateTo(PanelId.BOTTOM, HaronConstants.ROOT_PATH)

        // Автоочистка просроченных записей корзины
        viewModelScope.launch {
            cleanExpiredTrashUseCase()
        }
    }

    // --- Navigation ---

    fun navigateTo(panelId: PanelId, path: String) {
        viewModelScope.launch {
            updatePanel(panelId) { it.copy(isLoading = true, error = null) }

            val panel = getPanel(panelId)
            val displayPath = path.removePrefix(HaronConstants.ROOT_PATH).ifEmpty { "/" }

            getFilesUseCase(
                path = path,
                sortOrder = panel.sortOrder,
                showHidden = panel.showHidden
            ).onSuccess { files ->
                updatePanel(panelId) {
                    it.copy(
                        currentPath = path,
                        displayPath = displayPath,
                        files = files,
                        isLoading = false,
                        error = null
                    )
                }
                // Track recent paths
                preferences.addRecentPath(path)
                _uiState.update { it.copy(recentPaths = preferences.getRecentPaths()) }
                EcosystemLogger.d(HaronConstants.TAG, "[$panelId] Открыта папка: $path (${files.size} файлов)")
            }.onFailure { error ->
                updatePanel(panelId) {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Неизвестная ошибка"
                    )
                }
                EcosystemLogger.e(HaronConstants.TAG, "[$panelId] Ошибка навигации: $path — ${error.message}")
            }
        }
    }

    fun navigateUp(panelId: PanelId): Boolean {
        val currentPath = getPanel(panelId).currentPath
        val parentPath = fileRepository.getParentPath(currentPath) ?: return false
        navigateTo(panelId, parentPath)
        return true
    }

    fun onFileClick(panelId: PanelId, entry: FileEntry) {
        setActivePanel(panelId)
        val panel = getPanel(panelId)
        if (panel.isSelectionMode) {
            toggleSelection(panelId, entry.path)
        } else if (entry.isDirectory) {
            navigateTo(panelId, entry.path)
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

    fun canNavigateUp(panelId: PanelId): Boolean {
        return fileRepository.getParentPath(getPanel(panelId).currentPath) != null
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
        updatePanel(panelId) { it.copy(showHidden = newValue) }
        refreshPanel(panelId)
    }

    // --- Search ---

    fun setSearchQuery(panelId: PanelId, query: String) {
        updatePanel(panelId) { it.copy(searchQuery = query) }
    }

    fun clearSearch(panelId: PanelId) {
        updatePanel(panelId) { it.copy(searchQuery = "") }
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
        if (paths.isEmpty()) return

        val selected = sourcePanel.files.filter { it.path in sourcePanel.selectedPaths }
        val dirs = selected.count { it.isDirectory }
        val files = selected.size - dirs
        viewModelScope.launch {
            copyFilesUseCase(paths, targetPanel.currentPath)
                .onSuccess {
                    clearSelection(activeId)
                    showStatusMessage(targetId, "Скопировано: ${formatFileCount(dirs, files)}")
                    refreshPanel(targetId)
                }
                .onFailure { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "Ошибка копирования: ${e.message}")
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
        if (paths.isEmpty()) return

        val selected = sourcePanel.files.filter { it.path in sourcePanel.selectedPaths }
        val dirs = selected.count { it.isDirectory }
        val files = selected.size - dirs
        viewModelScope.launch {
            moveFilesUseCase(paths, targetPanel.currentPath)
                .onSuccess {
                    clearSelection(activeId)
                    showStatusMessage(targetId, "Перемещено: ${formatFileCount(dirs, files)}")
                    refreshPanel(activeId)
                    refreshPanel(targetId)
                }
                .onFailure { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "Ошибка перемещения: ${e.message}")
                }
        }
    }

    fun requestDeleteSelected() {
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)
        val paths = panel.selectedPaths.toList()
        if (paths.isEmpty()) return
        _uiState.update { it.copy(dialogState = DialogState.ConfirmDelete(paths)) }
    }

    fun confirmDelete(paths: List<String>) {
        dismissDialog()
        val activeId = _uiState.value.activePanel
        viewModelScope.launch {
            moveToTrashUseCase(paths)
                .onSuccess { count ->
                    clearSelection(activeId)
                    showStatusMessage(activeId, "В корзине: $count")
                    refreshBothIfSamePath(activeId)
                }
                .onFailure { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "Ошибка перемещения в корзину: ${e.message}")
                }
        }
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
            renameFileUseCase(path, newName)
                .onSuccess { refreshBothIfSamePath(activeId) }
                .onFailure { e ->
                    _toastMessage.tryEmit(e.message ?: "Ошибка переименования")
                    EcosystemLogger.e(HaronConstants.TAG, "Ошибка переименования: ${e.message}")
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

    // --- Templates ---

    fun requestCreateFromTemplate() {
        _uiState.update { it.copy(dialogState = DialogState.CreateFromTemplate) }
    }

    fun confirmCreateFromTemplate(template: FileTemplate, name: String) {
        dismissDialog()
        val activeId = _uiState.value.activePanel
        val panel = getPanel(activeId)

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
    }

    fun removeFavorite(path: String) {
        preferences.removeFavorite(path)
        _uiState.update { it.copy(favorites = preferences.getFavorites()) }
    }

    fun toggleFavoritesPanel() {
        _uiState.update { it.copy(showFavoritesPanel = !it.showFavoritesPanel) }
    }

    fun dismissFavoritesPanel() {
        _uiState.update { it.copy(showFavoritesPanel = false) }
    }

    fun navigateFromFavorites(path: String) {
        val activeId = _uiState.value.activePanel
        dismissFavoritesPanel()
        navigateTo(activeId, path)
    }

    // --- Trash ---

    fun showTrash() {
        viewModelScope.launch {
            trashRepository.getTrashEntries()
                .onSuccess { entries ->
                    val totalSize = trashRepository.getTrashSize()
                    _uiState.update {
                        it.copy(dialogState = DialogState.ShowTrash(entries, totalSize))
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
                    showStatusMessage(_uiState.value.activePanel, "Восстановлено: $count")
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
            trashRepository.deleteFromTrash(ids)
                .onSuccess {
                    showTrash()
                }
                .onFailure { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "Ошибка удаления из корзины: ${e.message}")
                }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            emptyTrashUseCase()
                .onSuccess { count ->
                    showStatusMessage(_uiState.value.activePanel, "Корзина очищена: $count")
                    dismissDialog()
                }
                .onFailure { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "Ошибка очистки корзины: ${e.message}")
                }
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

    fun endDrag(targetPanelId: PanelId?) {
        val current = _uiState.value.dragState
        if (current !is DragState.Dragging) return
        _uiState.update { it.copy(dragState = DragState.Idle) }

        if (targetPanelId == null || targetPanelId == current.sourcePanelId) return

        val sourcePanel = getPanel(current.sourcePanelId)
        val targetPanel = getPanel(targetPanelId)
        if (sourcePanel.currentPath == targetPanel.currentPath) return
        viewModelScope.launch {
            moveFilesUseCase(current.draggedPaths, targetPanel.currentPath)
                .onSuccess {
                    clearSelection(current.sourcePanelId)
                    showStatusMessage(targetPanelId, "Перемещено: ${current.fileCount}")
                    refreshPanel(current.sourcePanelId)
                    refreshPanel(targetPanelId)
                }
                .onFailure { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "Ошибка DnD перемещения: ${e.message}")
                }
        }
    }

    fun cancelDrag() {
        _uiState.update { it.copy(dragState = DragState.Idle) }
    }

    // --- Helpers ---

    fun getSelectedTotalSize(): Long {
        val state = _uiState.value
        val activeId = state.activePanel
        val panel = getPanel(activeId)
        return panel.files
            .filter { it.path in panel.selectedPaths }
            .sumOf { it.size }
    }

    fun refreshPanel(panelId: PanelId) {
        val path = getPanel(panelId).currentPath
        if (path.isNotEmpty()) {
            navigateTo(panelId, path)
        }
    }

    private fun refreshBothIfSamePath(panelId: PanelId) {
        refreshPanel(panelId)
        val otherId = if (panelId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
        if (getPanel(panelId).currentPath == getPanel(otherId).currentPath) {
            refreshPanel(otherId)
        }
    }

    private fun showStatusMessage(panelId: PanelId, message: String) {
        updatePanel(panelId) { it.copy(statusMessage = message) }
        viewModelScope.launch {
            delay(5000)
            updatePanel(panelId) { it.copy(statusMessage = null) }
        }
    }

    private fun formatFileCount(dirs: Int, files: Int): String {
        val parts = buildList {
            if (dirs > 0) add("$dirs ${pluralDirs(dirs)}")
            if (files > 0) add("$files ${pluralFiles(files)}")
        }
        return parts.joinToString(" и ").ifEmpty { "0 файлов" }
    }

    private fun pluralDirs(count: Int): String {
        val mod100 = count % 100
        val mod10 = count % 10
        return when {
            mod100 in 11..14 -> "папок"
            mod10 == 1 -> "папка"
            mod10 in 2..4 -> "папки"
            else -> "папок"
        }
    }

    private fun pluralFiles(count: Int): String {
        val mod100 = count % 100
        val mod10 = count % 10
        return when {
            mod100 in 11..14 -> "файлов"
            mod10 == 1 -> "файл"
            mod10 in 2..4 -> "файла"
            else -> "файлов"
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
}
