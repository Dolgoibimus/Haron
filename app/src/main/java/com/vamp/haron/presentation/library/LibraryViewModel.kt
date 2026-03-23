package com.vamp.haron.presentation.library

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.db.dao.BookDao
import com.vamp.haron.data.db.entity.BookEntity
import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.model.PreviewData
import com.vamp.haron.domain.usecase.LoadPreviewUseCase
import com.vamp.haron.domain.usecase.ScanBooksUseCase
import com.vamp.haron.domain.usecase.ScanProgress
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.vamp.haron.common.util.ThumbnailCache
import java.io.File
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class LibraryUiState(
    val isFirstLaunch: Boolean = false,
    val showFolderPicker: Boolean = false,
    val scanFolders: List<String> = emptyList(),
    val excludedFolders: List<String> = emptyList(),
    val scanProgress: ScanProgress? = null,
    val gridColumnsByTab: Map<Int, Int> = mapOf(0 to 3, 1 to 3, 2 to 3),
    val selectedTab: Int = 0, // 0=FB2/EPUB, 1=PDF, 2=Other
    // Preview dialog
    val previewEntry: FileEntry? = null,
    val previewData: PreviewData? = null,
    val previewLoading: Boolean = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val bookDao: BookDao,
    private val scanBooksUseCase: ScanBooksUseCase,
    private val prefs: HaronPreferences,
    private val loadPreviewUseCase: LoadPreviewUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    val books = bookDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _openReader = MutableSharedFlow<BookEntity>(extraBufferCapacity = 1)
    val openReader: SharedFlow<BookEntity> = _openReader.asSharedFlow()

    private val _openPreview = MutableSharedFlow<BookEntity>(extraBufferCapacity = 1)
    val openPreview: SharedFlow<BookEntity> = _openPreview.asSharedFlow()

    /** Persistent cover cache — memory + disk (filesDir/book_covers/) */
    private val _coverMap = HashMap<String, Bitmap?>()
    private val _loadingKeys = HashSet<String>()
    private val _coverState = MutableStateFlow<Map<String, Bitmap?>>(emptyMap())
    val coverState: StateFlow<Map<String, Bitmap?>> = _coverState.asStateFlow()
    private val coverDir = File(appContext.filesDir, "book_covers").also { it.mkdirs() }

    private fun diskCacheFile(filePath: String): File {
        val hash = filePath.hashCode().toUInt().toString(16)
        return File(coverDir, "$hash.webp")
    }

    fun loadCoverIfNeeded(filePath: String, format: String) {
        if (_coverMap.containsKey(filePath) || filePath in _loadingKeys) return
        _loadingKeys.add(filePath)
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                // 1. Try disk cache
                val diskFile = diskCacheFile(filePath)
                if (diskFile.exists() && diskFile.length() > 0) {
                    BitmapFactory.decodeFile(diskFile.absolutePath)
                } else {
                    // 2. Generate thumbnail
                    val type = if (format == "pdf") "pdf" else "document"
                    val generated = ThumbnailCache.loadThumbnail(appContext, filePath, false, type)
                    // 3. Save to disk
                    if (generated != null) {
                        try {
                            diskFile.outputStream().use { out ->
                                generated.compress(Bitmap.CompressFormat.WEBP, 80, out)
                            }
                        } catch (_: Exception) { /* ignore write errors */ }
                    }
                    generated
                }
            }
            _coverMap[filePath] = bmp
            _loadingKeys.remove(filePath)
            _coverState.value = HashMap(_coverMap)
        }
    }

    init {
        // Restore grid columns per tab from prefs
        val cols = mapOf(
            0 to prefs.getLibraryGridColumns(0),
            1 to prefs.getLibraryGridColumns(1),
            2 to prefs.getLibraryGridColumns(2)
        )
        _state.update { it.copy(gridColumnsByTab = cols) }

        loadScanFolders()

        // Auto-sync cover cache: remove covers for deleted books
        viewModelScope.launch {
            books
                .map { list -> list.map { it.filePath }.toSet() }
                .filter { it.isNotEmpty() } // skip initial empty emission
                .distinctUntilChanged()
                .collect { currentPaths ->
                    val staleKeys = _coverMap.keys.filter { it !in currentPaths }
                    if (staleKeys.isNotEmpty()) {
                        staleKeys.forEach { key ->
                            _coverMap.remove(key)
                            diskCacheFile(key).delete()
                        }
                        _coverState.value = HashMap(_coverMap)
                    }
                }
        }
    }

    private fun loadScanFolders() {
        viewModelScope.launch {
            val removed = prefs.libraryRemovedFolders
            val excluded = prefs.libraryExcludedFolders
            _state.update { it.copy(excludedFolders = excluded.toList().sorted()) }

            val rawFolders = bookDao.getScanFolders()
                .filter { it !in removed }
            if (rawFolders.isEmpty()) {
                _state.update { it.copy(isFirstLaunch = true) }
            } else {
                val folders = deduplicateFolders(rawFolders)
                _state.update { it.copy(scanFolders = folders) }
                startScan(folders)
            }
        }
    }

    /** Remove folders that are subfolders of another folder in the list */
    private fun deduplicateFolders(folders: List<String>): List<String> {
        val sorted = folders.sortedBy { it.length }
        val result = mutableListOf<String>()
        for (folder in sorted) {
            val isSubfolder = result.any { parent ->
                folder.startsWith("$parent/")
            }
            if (!isSubfolder) {
                result.add(folder)
            }
        }
        return result
    }

    fun onFirstLaunchScanAll() {
        _state.update { it.copy(isFirstLaunch = false) }
        val root = Environment.getExternalStorageDirectory().absolutePath
        startScan(listOf(root))
    }

    fun onFirstLaunchPickFolders() {
        _state.update { it.copy(isFirstLaunch = false, showFolderPicker = true) }
    }

    fun dismissFolderPicker() {
        _state.update { it.copy(showFolderPicker = false) }
    }

    fun addScanFolder(path: String) {
        val current = _state.value.scanFolders
        if (path !in current) {
            _state.update { it.copy(scanFolders = current + path, showFolderPicker = false) }
            startScan(listOf(path))
        }
    }

    fun removeScanFolder(path: String) {
        _state.update { it.copy(scanFolders = it.scanFolders - path) }
        prefs.libraryRemovedFolders = prefs.libraryRemovedFolders + path
        viewModelScope.launch {
            bookDao.deleteByFolder(path)
        }
    }

    fun addExcludedFolder(path: String) {
        val updated = prefs.libraryExcludedFolders + path
        prefs.libraryExcludedFolders = updated
        _state.update { it.copy(excludedFolders = updated.toList().sorted()) }
        // Remove books from excluded folder
        viewModelScope.launch {
            bookDao.deleteByFolder(path)
        }
    }

    fun removeExcludedFolder(path: String) {
        val updated = prefs.libraryExcludedFolders - path
        prefs.libraryExcludedFolders = updated
        _state.update { it.copy(excludedFolders = updated.toList().sorted()) }
    }

    fun rescan() {
        val folders = _state.value.scanFolders
        if (folders.isNotEmpty()) {
            startScan(folders)
        }
    }

    private fun startScan(folders: List<String>) {
        viewModelScope.launch {
            val excluded = prefs.libraryExcludedFolders
            scanBooksUseCase.scan(folders, excluded).collect { progress ->
                _state.update { it.copy(scanProgress = if (progress.isComplete) null else progress) }
            }
            // Reload folders from DB (deduplicated), filtering removed
            val removed = prefs.libraryRemovedFolders
            val dbFolders = deduplicateFolders(bookDao.getScanFolders().filter { it !in removed })
            _state.update { it.copy(scanFolders = dbFolders) }
        }
    }

    fun onBookTap(book: BookEntity) {
        val file = java.io.File(book.filePath)
        val ext = file.extension.lowercase()
        val entry = FileEntry(
            name = file.name,
            path = file.absolutePath,
            isDirectory = false,
            size = book.fileSize,
            lastModified = file.lastModified(),
            extension = ext,
            isHidden = file.isHidden,
            childCount = 0
        )
        _state.update { it.copy(previewEntry = entry, previewData = null, previewLoading = true) }
        viewModelScope.launch {
            val result = loadPreviewUseCase(entry)
            val data = result.getOrNull()
            _state.update { it.copy(previewData = data, previewLoading = false) }
        }
    }

    fun dismissPreview() {
        _state.update { it.copy(previewEntry = null, previewData = null, previewLoading = false) }
    }

    fun setTab(tab: Int) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun onBookLongTap(book: BookEntity) {
        viewModelScope.launch {
            bookDao.updateProgress(book.filePath, book.progress)
        }
        _openReader.tryEmit(book)
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            bookDao.delete(book.filePath)
        }
    }

    fun adjustGridColumns(delta: Int) {
        _state.update {
            val tab = it.selectedTab
            val current = it.gridColumnsByTab[tab] ?: 3
            var newCol = current + delta
            if (newCol == 2) newCol = if (delta > 0) 3 else 1
            val clamped = newCol.coerceIn(1, 6)
            prefs.setLibraryGridColumns(tab, clamped)
            it.copy(gridColumnsByTab = it.gridColumnsByTab + (tab to clamped))
        }
    }

    /** Direct open book on tap (no preview) */
    fun openBook(book: BookEntity) {
        viewModelScope.launch {
            bookDao.updateProgress(book.filePath, book.progress)
        }
        _openReader.tryEmit(book)
    }
}
