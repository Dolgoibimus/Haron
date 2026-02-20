package com.vamp.haron.presentation.explorer

import android.content.Context
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.core.db.EcosystemPreferences
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.data.model.SortOrder
import com.vamp.haron.data.saf.SafUriManager
import com.vamp.haron.data.saf.StorageVolumeHelper
import com.vamp.haron.domain.model.ConflictFileInfo
import com.vamp.haron.domain.model.ConflictPair
import com.vamp.haron.domain.model.ConflictResolution
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.model.OperationProgress
import com.vamp.haron.domain.model.OperationType
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
import kotlinx.coroutines.launch
import com.vamp.haron.common.util.toFileSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

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
    private val moveToTrashUseCase: MoveToTrashUseCase,
    private val restoreFromTrashUseCase: RestoreFromTrashUseCase,
    private val emptyTrashUseCase: EmptyTrashUseCase,
    private val cleanExpiredTrashUseCase: CleanExpiredTrashUseCase,
    private val trashRepository: TrashRepository,
    private val safUriManager: SafUriManager,
    private val storageVolumeHelper: StorageVolumeHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorerUiState())

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage = _toastMessage.asSharedFlow()
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    /** Selection snapshot at the start of a drag gesture (for non-contiguous multi-range). */
    private var dragBaseSelection: Set<String> = emptySet()

    /** Job for inline (non-service) file operations, used for cancellation. */
    private var inlineOperationJob: kotlinx.coroutines.Job? = null

    init {
        val savedSort = preferences.getSortOrder()
        val showHidden = preferences.showHidden
        val panelRatio = preferences.panelRatio
        val gridColumns = preferences.gridColumns
        _uiState.update {
            it.copy(
                topPanel = it.topPanel.copy(sortOrder = savedSort, showHidden = showHidden, gridColumns = gridColumns),
                bottomPanel = it.bottomPanel.copy(sortOrder = savedSort, showHidden = showHidden, gridColumns = gridColumns),
                panelRatio = panelRatio,
                favorites = preferences.getFavorites(),
                recentPaths = preferences.getRecentPaths(),
                themeMode = EcosystemPreferences.theme
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

        // Автоочистка просроченных записей корзины + обновить инфо корзины
        viewModelScope.launch {
            cleanExpiredTrashUseCase()
            updateTrashSizeInfo()
        }

        // Подписка на прогресс файловых операций из foreground service
        viewModelScope.launch {
            FileOperationService.progress.collect { progress ->
                _uiState.update { it.copy(operationProgress = progress) }
                // Когда операция завершена — обновить обе панели
                if (progress?.isComplete == true) {
                    delay(500)
                    refreshPanel(PanelId.TOP)
                    refreshPanel(PanelId.BOTTOM)
                    // Очистить прогресс через 3 секунды
                    delay(3000)
                    _uiState.update { it.copy(operationProgress = null) }
                }
            }
        }
    }

    /** Threshold: use foreground service for >10 files or >50MB total */
    private companion object {
        const val SERVICE_FILE_THRESHOLD = 10
        const val SERVICE_SIZE_THRESHOLD = 50L * 1024 * 1024 // 50 MB
        const val MAX_HISTORY_SIZE = 50
    }

    // --- Navigation ---

    fun navigateTo(panelId: PanelId, path: String, pushHistory: Boolean = true) {
        viewModelScope.launch {
            updatePanel(panelId) { it.copy(isLoading = true, error = null) }

            val panel = getPanel(panelId)
            val displayPath = if (path.startsWith("content://")) {
                buildSafDisplayPath(path)
            } else {
                path.removePrefix(HaronConstants.ROOT_PATH).ifEmpty { "/" }
            }

            getFilesUseCase(
                path = path,
                sortOrder = panel.sortOrder,
                showHidden = panel.showHidden
            ).onSuccess { files ->
                updatePanel(panelId) {
                    var history = it.navigationHistory
                    var index = it.historyIndex
                    if (pushHistory) {
                        // Обрезаем forward-стек и добавляем новый путь
                        history = history.take(index + 1) + path
                        if (history.size > MAX_HISTORY_SIZE) {
                            history = history.takeLast(MAX_HISTORY_SIZE)
                        }
                        index = history.lastIndex
                    }
                    it.copy(
                        currentPath = path,
                        displayPath = displayPath,
                        files = files,
                        isLoading = false,
                        error = null,
                        navigationHistory = history,
                        historyIndex = index
                    )
                }
                // Save panel path & track recent
                when (panelId) {
                    PanelId.TOP -> preferences.topPanelPath = path
                    PanelId.BOTTOM -> preferences.bottomPanelPath = path
                }
                if (!path.startsWith("content://")) {
                    preferences.addRecentPath(path)
                    _uiState.update { it.copy(recentPaths = preferences.getRecentPaths()) }
                }
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

    fun navigateBack(panelId: PanelId) {
        val panel = getPanel(panelId)
        if (panel.historyIndex <= 0) return
        val newIndex = panel.historyIndex - 1
        val path = panel.navigationHistory[newIndex]
        updatePanel(panelId) { it.copy(historyIndex = newIndex) }
        navigateTo(panelId, path, pushHistory = false)
    }

    fun navigateForward(panelId: PanelId) {
        val panel = getPanel(panelId)
        if (panel.historyIndex >= panel.navigationHistory.lastIndex) return
        val newIndex = panel.historyIndex + 1
        val path = panel.navigationHistory[newIndex]
        updatePanel(panelId) { it.copy(historyIndex = newIndex) }
        navigateTo(panelId, path, pushHistory = false)
    }

    fun canNavigateBack(panelId: PanelId): Boolean {
        return getPanel(panelId).historyIndex > 0
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

    fun cycleTheme() {
        val current = EcosystemPreferences.theme
        val next = when (current) {
            "system" -> "light"
            "light" -> "dark"
            else -> "system"
        }
        EcosystemPreferences.theme = next
        _uiState.update { it.copy(themeMode = next) }
    }

    // --- SAF ---

    fun onSafUriGranted(uri: Uri) {
        safUriManager.persistUri(uri)
        navigateTo(_uiState.value.activePanel, uri.toString())
    }

    fun hasRemovableStorage(): Boolean {
        return storageVolumeHelper.getRemovableVolumes().isNotEmpty() ||
            safUriManager.getPersistedUris().isNotEmpty()
    }

    fun getSdCardLabel(): String {
        val volumes = storageVolumeHelper.getRemovableVolumes()
        return volumes.firstOrNull()?.label ?: "SD-карта"
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
        val clamped = columns.coerceIn(1, 4)
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

        val selected = sourcePanel.files.filter { it.path in sourcePanel.selectedPaths }
        val totalSize = selected.sumOf { it.size }
        val dirs = selected.count { it.isDirectory }
        val files = selected.size - dirs
        clearSelection(activeId)

        if (paths.size > SERVICE_FILE_THRESHOLD || totalSize > SERVICE_SIZE_THRESHOLD) {
            FileOperationService.start(appContext, paths, destinationDir, isMove = false, conflictResolution = resolution)
        } else {
            val fileSizes = selected.associate { it.path to it.size }
            inlineOperationJob = viewModelScope.launch {
                runInlineOperation(paths, OperationType.COPY, fileSizes) { path ->
                    copyFilesUseCase(listOf(path), destinationDir, resolution)
                }
                showStatusMessage(targetId, "Скопировано: ${formatFileCount(dirs, files)}")
                refreshPanel(targetId)
            }
        }
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

        inlineOperationJob = viewModelScope.launch {
            val total = paths.size
            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, "", OperationType.COPY))
            }
            fileRepository.copyFilesWithResolutions(paths, destinationDir, decisions)
                .onSuccess { count ->
                    _uiState.update {
                        it.copy(operationProgress = OperationProgress(count, total, "", OperationType.COPY, isComplete = true))
                    }
                    val skipped = total - count
                    val msg = when {
                        count == 0 -> "Пропущено: $total"
                        skipped > 0 -> "Скопировано: $count, пропущено: $skipped"
                        else -> "Скопировано: ${formatFileCount(dirs, files)}"
                    }
                    showStatusMessage(targetId, msg)
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(operationProgress = OperationProgress(0, total, "", OperationType.COPY, isComplete = true, error = e.message))
                    }
                }
            refreshPanel(targetId)
            delay(2000)
            _uiState.update { it.copy(operationProgress = null) }
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

        val selected = sourcePanel.files.filter { it.path in sourcePanel.selectedPaths }
        val totalSize = selected.sumOf { it.size }
        val dirs = selected.count { it.isDirectory }
        val files = selected.size - dirs
        clearSelection(activeId)

        if (paths.size > SERVICE_FILE_THRESHOLD || totalSize > SERVICE_SIZE_THRESHOLD) {
            FileOperationService.start(appContext, paths, destinationDir, isMove = true, conflictResolution = resolution)
        } else {
            val fileSizes = selected.associate { it.path to it.size }
            inlineOperationJob = viewModelScope.launch {
                runInlineOperation(paths, OperationType.MOVE, fileSizes) { path ->
                    moveFilesUseCase(listOf(path), destinationDir, resolution)
                }
                showStatusMessage(targetId, "Перемещено: ${formatFileCount(dirs, files)}")
                refreshPanel(activeId)
                refreshPanel(targetId)
            }
        }
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
        clearSelection(activeId)

        inlineOperationJob = viewModelScope.launch {
            val total = paths.size
            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, "", OperationType.MOVE))
            }
            fileRepository.moveFilesWithResolutions(paths, destinationDir, decisions)
                .onSuccess { count ->
                    _uiState.update {
                        it.copy(operationProgress = OperationProgress(count, total, "", OperationType.MOVE, isComplete = true))
                    }
                    val skipped = total - count
                    val msg = when {
                        count == 0 -> "Пропущено: $total"
                        skipped > 0 -> "Перемещено: $count, пропущено: $skipped"
                        else -> "Перемещено: ${formatFileCount(dirs, files)}"
                    }
                    showStatusMessage(targetId, msg)
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(operationProgress = OperationProgress(0, total, "", OperationType.MOVE, isComplete = true, error = e.message))
                    }
                }
            refreshPanel(activeId)
            refreshPanel(targetId)
            delay(2000)
            _uiState.update { it.copy(operationProgress = null) }
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
        clearSelection(activeId)
        viewModelScope.launch {
            val total = paths.size
            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, "", OperationType.DELETE))
            }

            // For SAF files, delete directly (no trash support for content:// URIs)
            val hasSaf = paths.any { it.startsWith("content://") }
            if (hasSaf) {
                fileRepository.deleteFiles(paths)
                    .onSuccess { count ->
                        _uiState.update {
                            it.copy(operationProgress = OperationProgress(count, total, "", OperationType.DELETE, isComplete = true))
                        }
                        showStatusMessage(activeId, "Удалено: $count")
                    }
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(operationProgress = OperationProgress(0, total, "", OperationType.DELETE, isComplete = true, error = e.message))
                        }
                    }
            } else {
                moveToTrashUseCase(paths)
                    .onSuccess { result ->
                        _uiState.update {
                            it.copy(
                                operationProgress = OperationProgress(
                                    result.movedCount, total, "", OperationType.DELETE, isComplete = true
                                )
                            )
                        }
                        if (result.evictedCount > 0) {
                            _toastMessage.tryEmit(
                                "Автоудалено ${result.evictedCount} старых файлов из корзины для освобождения места"
                            )
                        }
                        updateTrashSizeInfo()
                        showStatusMessage(activeId, "В корзине: ${result.movedCount}")
                    }
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(
                                operationProgress = OperationProgress(
                                    0, total, "", OperationType.DELETE, isComplete = true, error = e.message
                                )
                            )
                        }
                        _toastMessage.tryEmit("Ошибка удаления: ${e.message}")
                    }
            }

            delay(2000)
            _uiState.update { it.copy(operationProgress = null) }
            refreshBothIfSamePath(activeId)
        }
    }

    /** Processes files one by one with progress updates in UI. */
    private suspend fun runInlineOperation(
        paths: List<String>,
        type: OperationType,
        fileSizes: Map<String, Long> = emptyMap(),
        action: suspend (String) -> Result<Int>
    ) {
        val total = paths.size
        _uiState.update {
            it.copy(operationProgress = OperationProgress(0, total, "", type))
        }
        var completed = 0
        for ((index, path) in paths.withIndex()) {
            val fileName = if (path.startsWith("content://")) {
                Uri.parse(path).lastPathSegment?.substringAfterLast('/') ?: "file"
            } else {
                path.substringAfterLast('/')
            }
            val size = fileSizes[path] ?: 0L
            val displayName = if (size > 1024 * 1024) {
                "$fileName (${size.toFileSize()})"
            } else {
                fileName
            }
            _uiState.update {
                it.copy(operationProgress = OperationProgress(index, total, displayName, type))
            }
            action(path).onSuccess { completed++ }
        }
        _uiState.update {
            it.copy(operationProgress = OperationProgress(completed, total, "", type, isComplete = true))
        }
        delay(2000)
        _uiState.update { it.copy(operationProgress = null) }
    }

    // --- Cancel file operation ---

    fun cancelFileOperation() {
        // Cancel foreground service
        val intent = android.content.Intent(appContext, FileOperationService::class.java).apply {
            action = FileOperationService.ACTION_CANCEL
        }
        appContext.startService(intent)
        // Cancel inline operation
        inlineOperationJob?.cancel()
        inlineOperationJob = null
        _uiState.update { state ->
            val p = state.operationProgress
            if (p != null && !p.isComplete) {
                state.copy(operationProgress = p.copy(isComplete = true, error = "Отменено"))
            } else state
        }
        viewModelScope.launch {
            delay(2000)
            _uiState.update { it.copy(operationProgress = null) }
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
                    updateTrashSizeInfo()
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
                    updateTrashSizeInfo()
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

        val paths = current.draggedPaths
        clearSelection(current.sourcePanelId)

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

        executeDragMove(paths, targetPanel.currentPath, current.sourcePanelId, targetPanelId, current.fileCount, ConflictResolution.RENAME)
    }

    private fun executeDragMove(
        paths: List<String>,
        destinationDir: String,
        sourcePanelId: PanelId,
        targetPanelId: PanelId,
        fileCount: Int,
        resolution: ConflictResolution
    ) {
        val total = paths.size
        val dirs = paths.count { p ->
            if (p.startsWith("content://")) false else File(p).isDirectory
        }
        val files = total - dirs
        viewModelScope.launch {
            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, "", OperationType.MOVE))
            }

            moveFilesUseCase(paths, destinationDir, resolution)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            operationProgress = OperationProgress(
                                total, total, "", OperationType.MOVE, isComplete = true
                            )
                        )
                    }
                    showStatusMessage(targetPanelId, "Перемещено: ${formatFileCount(dirs, files)}")
                    refreshPanel(sourcePanelId)
                    refreshPanel(targetPanelId)
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            operationProgress = OperationProgress(
                                0, total, "", OperationType.MOVE, isComplete = true, error = e.message
                            )
                        )
                    }
                    EcosystemLogger.e(HaronConstants.TAG, "Ошибка DnD перемещения: ${e.message}")
                }

            delay(2000)
            _uiState.update { it.copy(operationProgress = null) }
        }
    }

    fun cancelDrag() {
        _uiState.update { it.copy(dragState = DragState.Idle) }
    }

    // --- Conflict resolution (per-file card) ---

    private fun buildConflictPairs(paths: List<String>, targetPanel: PanelUiState): List<ConflictPair> {
        val destFiles = targetPanel.files.associateBy { it.name }
        val sourcePanel = getPanel(_uiState.value.activePanel)
        val sourceFiles = sourcePanel.files.associateBy { it.path }
        val pairs = mutableListOf<ConflictPair>()

        for (path in paths) {
            val srcEntry = sourceFiles[path]
            val name = srcEntry?.name ?: if (path.startsWith("content://")) {
                Uri.parse(path).lastPathSegment?.substringAfterLast('/') ?: continue
            } else {
                File(path).name
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
        when (dialog.operationType) {
            OperationType.COPY -> executeCopyWithDecisions(dialog.allPaths, dialog.destinationDir, decisions)
            OperationType.MOVE -> {
                val state = _uiState.value
                val activeId = state.activePanel
                val sourcePanel = getPanel(activeId)
                if (sourcePanel.selectedPaths.isNotEmpty()) {
                    executeMoveWithDecisions(dialog.allPaths, dialog.destinationDir, decisions)
                } else {
                    // DnD case
                    val targetId = if (activeId == PanelId.TOP) PanelId.BOTTOM else PanelId.TOP
                    executeDragMoveWithDecisions(dialog.allPaths, dialog.destinationDir, activeId, targetId, decisions)
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
        val total = paths.size
        viewModelScope.launch {
            _uiState.update {
                it.copy(operationProgress = OperationProgress(0, total, "", OperationType.MOVE))
            }
            fileRepository.moveFilesWithResolutions(paths, destinationDir, decisions)
                .onSuccess { count ->
                    _uiState.update {
                        it.copy(operationProgress = OperationProgress(count, total, "", OperationType.MOVE, isComplete = true))
                    }
                    refreshPanel(sourcePanelId)
                    refreshPanel(targetPanelId)
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(operationProgress = OperationProgress(0, total, "", OperationType.MOVE, isComplete = true, error = e.message))
                    }
                }
            delay(2000)
            _uiState.update { it.copy(operationProgress = null) }
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
                "${trashSize.toFileSize()} / ${maxBytes.toFileSize()}"
            } else {
                trashSize.toFileSize()
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
