package com.vamp.haron.presentation.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.haron.data.db.entity.FileIndexEntity
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.model.SearchNavigationHolder
import com.vamp.haron.domain.model.SearchSource
import com.vamp.haron.domain.model.WebSearchResult
import com.vamp.haron.domain.repository.DateFilter
import com.vamp.haron.domain.repository.FileCategory
import com.vamp.haron.domain.repository.IndexMode
import com.vamp.haron.domain.repository.IndexProgress
import com.vamp.haron.domain.repository.SearchFilter
import com.vamp.haron.domain.repository.SearchRepository
import com.vamp.haron.domain.repository.SizeFilter
import com.vamp.haron.domain.usecase.LoadPreviewUseCase
import com.vamp.haron.domain.usecase.SearchFilesUseCase
import com.vamp.haron.domain.usecase.websearch.InternetArchiveSearchUseCase
import com.vamp.haron.domain.usecase.websearch.OpenDirectorySearchUseCase
import com.vamp.haron.domain.usecase.websearch.TorrentSearchUseCase
import com.vamp.haron.domain.model.PreviewData
import com.vamp.haron.service.WebDownloadService
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.R
import com.vamp.haron.common.constants.HaronConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val category: FileCategory = FileCategory.ALL,
    val sizeFilter: SizeFilter = SizeFilter.ALL,
    val dateFilter: DateFilter = DateFilter.ALL,
    val searchInContent: Boolean = false,
    val expandedSnippetPath: String? = null,
    val results: List<FileIndexEntity> = emptyList(),
    val isSearching: Boolean = false,
    val indexProgress: IndexProgress = IndexProgress(),
    val indexedCount: Int = 0,
    val lastIndexedTime: Long? = null,
    val contentIndexSize: Long = 0L,
    val currentPage: Int = 0,
    val hasMore: Boolean = false,
    val previewDialog: PreviewDialogState? = null,
    // Web search tab
    val selectedTab: Int = 0,
    val webQuery: String = "",
    val webResults: List<WebSearchResult> = emptyList(),
    val isWebSearching: Boolean = false,
    val webError: String? = null
)

data class PreviewDialogState(
    val entry: FileEntry,
    val previewData: PreviewData? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

sealed interface SearchNavigationEvent {
    data class NavigateToFile(val parentPath: String, val filePath: String) : SearchNavigationEvent
    data class OpenFile(val entry: FileEntry) : SearchNavigationEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchFilesUseCase: SearchFilesUseCase,
    private val searchRepository: SearchRepository,
    private val loadPreviewUseCase: LoadPreviewUseCase,
    private val openDirectorySearch: OpenDirectorySearchUseCase,
    private val torrentSearch: TorrentSearchUseCase,
    private val archiveSearch: InternetArchiveSearchUseCase,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<SearchNavigationEvent>(extraBufferCapacity = 1)
    val navigationEvent = _navigationEvent.asSharedFlow()

    private val _queryFlow = MutableStateFlow("")
    private var searchJob: Job? = null
    private var webSearchJob: Job? = null

    companion object {
        private const val PAGE_SIZE = 200
    }

    init {
        _queryFlow
            .debounce(300)
            .onEach { performSearch(resetPage = true) }
            .launchIn(viewModelScope)

        searchRepository.indexProgressFlow()
            .onEach { progress ->
                _uiState.update { it.copy(indexProgress = progress) }
            }
            .launchIn(viewModelScope)

        // When indexing completes in background, refresh data
        searchRepository.indexCompleted
            .filter { it }
            .onEach {
                loadIndexStats()
                performSearch(resetPage = true)
            }
            .launchIn(viewModelScope)

        loadIndexStats()
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        _queryFlow.value = query
    }

    fun setCategory(category: FileCategory) {
        _uiState.update { it.copy(category = category) }
        performSearch(resetPage = true)
    }

    fun setSizeFilter(sizeFilter: SizeFilter) {
        _uiState.update { it.copy(sizeFilter = sizeFilter) }
        performSearch(resetPage = true)
    }

    fun setDateFilter(dateFilter: DateFilter) {
        _uiState.update { it.copy(dateFilter = dateFilter) }
        performSearch(resetPage = true)
    }

    fun setSearchMode(inContent: Boolean) {
        _uiState.update { it.copy(searchInContent = inContent, expandedSnippetPath = null) }
        performSearch(resetPage = true)
    }

    fun toggleSnippet(path: String) {
        _uiState.update {
            it.copy(expandedSnippetPath = if (it.expandedSnippetPath == path) null else path)
        }
    }

    fun loadMore() {
        if (_uiState.value.hasMore && !_uiState.value.isSearching) {
            performSearch(resetPage = false)
        }
    }

    fun startBasicIndex() {
        EcosystemLogger.d(HaronConstants.TAG, "SearchVM: starting basic index")
        searchRepository.startIndexByMode(IndexMode.BASIC)
    }

    fun startMediaIndex() {
        EcosystemLogger.d(HaronConstants.TAG, "SearchVM: starting media index")
        searchRepository.startIndexByMode(IndexMode.MEDIA)
    }

    fun startVisualIndex() {
        EcosystemLogger.d(HaronConstants.TAG, "SearchVM: starting visual index")
        searchRepository.startIndexByMode(IndexMode.VISUAL)
    }

    // --- Icon tap → QuickPreview (single file, no pager) ---
    fun onIconClick(entity: FileIndexEntity) {
        if (entity.isDirectory) return
        // Skip video preview — VLC single-surface model crashes in search context
        val ext = entity.extension.lowercase()
        if (ext in listOf("mp4","avi","mkv","mov","wmv","flv","webm","3gp","3gpp","ts","m4v","mts","vob","ogv","divx")) return
        val fileEntry = entity.toFileEntry()
        _uiState.update {
            it.copy(previewDialog = PreviewDialogState(entry = fileEntry))
        }
        viewModelScope.launch {
            loadPreviewUseCase(fileEntry)
                .onSuccess { data ->
                    _uiState.update {
                        it.copy(previewDialog = it.previewDialog?.copy(
                            previewData = data,
                            isLoading = false
                        ))
                    }
                }
                .onFailure { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "SearchVM: preview load failed for ${entity.name}: ${e.message}")
                    _uiState.update {
                        it.copy(previewDialog = it.previewDialog?.copy(
                            isLoading = false,
                            error = e.message
                        ))
                    }
                }
        }
    }

    fun dismissPreview() {
        _uiState.update { it.copy(previewDialog = null) }
    }

    // --- Name tap → open file ---
    fun onNameClick(entity: FileIndexEntity) {
        val fileEntry = entity.toFileEntry()
        if (entity.isDirectory) {
            SearchNavigationHolder.targetFilePath = null
            SearchNavigationHolder.targetParentPath = entity.path
            _navigationEvent.tryEmit(SearchNavigationEvent.NavigateToFile(entity.path, entity.path))
            return
        }
        val state = _uiState.value
        SearchNavigationHolder.highlightQuery =
            if (state.searchInContent && state.query.isNotBlank()) state.query else null
        _navigationEvent.tryEmit(SearchNavigationEvent.OpenFile(fileEntry))
    }

    // --- Name long press → navigate to file location, scroll to file ---
    fun onNameLongPress(entity: FileIndexEntity) {
        SearchNavigationHolder.targetFilePath = entity.path
        SearchNavigationHolder.targetParentPath = entity.parentPath
        _navigationEvent.tryEmit(
            SearchNavigationEvent.NavigateToFile(entity.parentPath, entity.path)
        )
    }

    // --- Web search tab ---

    fun setSelectedTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun setWebQuery(query: String) {
        _uiState.update { it.copy(webQuery = query) }
    }

    fun searchWeb() {
        val query = _uiState.value.webQuery.trim()
        if (query.isBlank()) return

        webSearchJob?.cancel()
        EcosystemLogger.d(HaronConstants.TAG, "SearchVM: web search query=\"$query\"")
        _uiState.update { it.copy(isWebSearching = true, webError = null, webResults = emptyList()) }

        webSearchJob = viewModelScope.launch {
            try {
                val odDeferred = async { openDirectorySearch(query) }
                val torrentDeferred = async { torrentSearch(query) }
                val archiveDeferred = async { archiveSearch(query) }

                val odResults = try { odDeferred.await() } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "SearchVM: OD search error: ${e.message}")
                    emptyList()
                }
                val torrentResults = try { torrentDeferred.await() } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "SearchVM: Torrent search error: ${e.message}")
                    emptyList()
                }
                val archiveResults = try { archiveDeferred.await() } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "SearchVM: Archive search error: ${e.message}")
                    emptyList()
                }

                // Merge results: interleave sources for variety, OD first (direct downloads)
                val merged = mutableListOf<WebSearchResult>()
                merged.addAll(odResults.take(30))
                merged.addAll(archiveResults.take(30))
                merged.addAll(torrentResults.take(20))

                // Deduplicate by URL
                val seen = mutableSetOf<String>()
                val deduped = merged.filter { seen.add(it.url) }

                EcosystemLogger.i(HaronConstants.TAG, "SearchVM: web search done, ${deduped.size} results (OD=${odResults.size}, Torrent=${torrentResults.size}, Archive=${archiveResults.size})")

                _uiState.update {
                    it.copy(
                        webResults = deduped,
                        isWebSearching = false,
                        webError = if (deduped.isEmpty()) appContext.getString(R.string.web_no_results) else null
                    )
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "SearchVM: web search failed: ${e.message}")
                _uiState.update {
                    it.copy(isWebSearching = false, webError = e.message)
                }
            }
        }
    }

    fun downloadFile(result: WebSearchResult) {
        EcosystemLogger.d(HaronConstants.TAG, "SearchVM: download ${result.title} from ${result.source}")
        WebDownloadService.startDownload(
            context = appContext,
            url = result.url,
            fileName = result.title,
            source = result.source,
            isMagnet = result.isMagnet
        )
    }

    fun copyLink(result: WebSearchResult) {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("link", result.url))
    }

    private fun performSearch(resetPage: Boolean) {
        searchJob?.cancel()
        val state = _uiState.value
        val page = if (resetPage) 0 else state.currentPage + 1

        if (state.query.isNotBlank()) {
            EcosystemLogger.d(HaronConstants.TAG, "SearchVM: search query=\"${state.query}\" category=${state.category} inContent=${state.searchInContent} page=$page")
        }

        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }

            val filter = SearchFilter(
                query = state.query,
                category = state.category,
                sizeFilter = state.sizeFilter,
                dateFilter = state.dateFilter,
                searchInContent = state.searchInContent,
                limit = PAGE_SIZE,
                offset = page * PAGE_SIZE
            )

            try {
                val results = searchFilesUseCase(filter)
                _uiState.update { current ->
                    val allResults = if (resetPage) results else current.results + results
                    current.copy(
                        results = allResults,
                        isSearching = false,
                        currentPage = page,
                        hasMore = results.size == PAGE_SIZE
                    )
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "SearchVM: search failed: ${e.message}")
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    private fun loadIndexStats() {
        viewModelScope.launch {
            val count = searchRepository.getIndexedCount()
            val lastTime = searchRepository.getLastIndexedTime()
            val contentSize = searchRepository.getContentIndexSize()
            _uiState.update {
                it.copy(indexedCount = count, lastIndexedTime = lastTime, contentIndexSize = contentSize)
            }
        }
    }

    private fun FileIndexEntity.toFileEntry() = FileEntry(
        name = name,
        path = path,
        isDirectory = isDirectory,
        size = size,
        lastModified = lastModified,
        extension = extension,
        isHidden = isHidden,
        childCount = 0
    )
}
