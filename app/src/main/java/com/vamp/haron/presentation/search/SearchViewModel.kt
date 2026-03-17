package com.vamp.haron.presentation.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.vamp.haron.domain.usecase.websearch.IntentDetectorUseCase
import com.vamp.haron.domain.usecase.websearch.InternetArchiveSearchUseCase
import com.vamp.haron.domain.usecase.websearch.OpenDirectorySearchUseCase
import com.vamp.haron.domain.usecase.websearch.QueryParser
import com.vamp.haron.domain.usecase.websearch.TorrentSearchUseCase
import com.vamp.haron.domain.usecase.websearch.LibGenSearchUseCase
import com.vamp.haron.domain.usecase.websearch.WebNavigateUseCase
import com.vamp.haron.domain.usecase.websearch.WebNavigatorLink
import com.vamp.haron.domain.usecase.websearch.WebNavigatorPage
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
import kotlinx.coroutines.coroutineScope
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
    val webError: String? = null,
    val webSearchPhase: String? = null,   // "Detecting…" / "Searching…" / null
    val webSearchConfirmation: WebSearchConfirmation? = null,   // shown after intent detection
    // Per-source search status: key absent = still searching, key present = done (value = result count)
    val webSearchSourcesDone: Map<String, Int> = emptyMap(),
    // Web Navigator: stack of browsed pages; null = sheet hidden
    val webNavigatorStack: List<WebNavigatorPage>? = null
)

data class WebSearchConfirmation(
    val originalQuery: String,
    val enhancedQuery: String,
    val detectionResult: IntentDetectorUseCase.IntentDetectionResult
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
    private val libGenSearch: LibGenSearchUseCase,
    private val webNavigate: WebNavigateUseCase,
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

    // Phase 1: detect intent (fast path: keywords) or show confirmation card (slow path: DDG)
    fun searchWeb() {
        val query = _uiState.value.webQuery.trim()
        if (query.isBlank()) return

        webSearchJob?.cancel()
        val parsed = QueryParser.parse(query)

        // Fast path: content type keywords detected ("книга", "музыка", "фильм"...)
        // → skip DDG detection and confirmation card, go straight to search
        if (parsed.contentHints.isNotEmpty()) {
            EcosystemLogger.d(HaronConstants.TAG, "SearchVM: keyword hints=${parsed.contentHints}, skipping confirmation")
            _uiState.update {
                it.copy(
                    isWebSearching = true,
                    webError = null,
                    webResults = emptyList(),
                    webSearchConfirmation = null,
                    webSearchPhase = appContext.getString(R.string.web_searching)
                )
            }
            webSearchJob = viewModelScope.launch {
                // IntentDetectorUseCase also has fast path 2 for keywords — no DDG call
                val detectionResult = IntentDetectorUseCase.detect(query)
                runWebSearch(parsed.searchQuery, detectionResult.effectiveTypes)
            }
            return
        }

        // Slow path: no keywords → DDG detection → show confirmation card
        EcosystemLogger.d(HaronConstants.TAG, "SearchVM: detect intent for \"$query\"")
        _uiState.update {
            it.copy(
                isWebSearching = true,
                webError = null,
                webResults = emptyList(),
                webSearchConfirmation = null,
                webSearchPhase = appContext.getString(R.string.web_detecting_type)
            )
        }

        webSearchJob = viewModelScope.launch {
            try {
                val detectionResult = IntentDetectorUseCase.detect(query)
                EcosystemLogger.d(HaronConstants.TAG, "SearchVM: intent=${detectionResult.contentType} heading=${detectionResult.heading} for \"$query\"")

                // Append suggested extension only if no explicit extension in query
                val enhancedQuery = if (parsed.extension == null &&
                    detectionResult.contentType.suggestedExtension != null) {
                    "$query ${detectionResult.contentType.suggestedExtension}"
                } else {
                    query
                }
                if (enhancedQuery != query) {
                    EcosystemLogger.d(HaronConstants.TAG, "SearchVM: enhanced query=\"$enhancedQuery\"")
                }

                _uiState.update {
                    it.copy(
                        isWebSearching = false,
                        webSearchPhase = null,
                        webSearchConfirmation = WebSearchConfirmation(
                            originalQuery = query,
                            enhancedQuery = enhancedQuery,
                            detectionResult = detectionResult
                        )
                    )
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "SearchVM: intent detect failed: ${e.message}")
                _uiState.update {
                    it.copy(isWebSearching = false, webSearchPhase = null, webError = e.message)
                }
            }
        }
    }

    // Phase 2: user confirmed — run the actual search
    fun confirmWebSearch() {
        val confirmation = _uiState.value.webSearchConfirmation ?: return
        _uiState.update {
            it.copy(
                webSearchConfirmation = null,
                isWebSearching = true,
                webError = null,
                webSearchPhase = appContext.getString(R.string.web_searching)
            )
        }

        webSearchJob?.cancel()
        webSearchJob = viewModelScope.launch {
            runWebSearch(confirmation.enhancedQuery, confirmation.detectionResult.effectiveTypes)
        }
    }

    private suspend fun runWebSearch(
        searchQuery: String,
        contentTypes: Set<IntentDetectorUseCase.ContentType>
    ) = coroutineScope {
        EcosystemLogger.d(HaronConstants.TAG, "SearchVM: search query=\"$searchQuery\" types=$contentTypes")
        // Reset results and source status
        _uiState.update { it.copy(webResults = emptyList(), webSearchSourcesDone = emptyMap()) }

        // Launch all sources in parallel; stream results as each completes
        val sources = listOf(
            launch {
                val r = try { openDirectorySearch(searchQuery, contentTypes) }
                        catch (e: Exception) { EcosystemLogger.e(HaronConstants.TAG, "SearchVM: OD error: ${e.message}"); emptyList() }
                appendWebResults(r.take(40), "od", r.size)
            },
            launch {
                val r = try { archiveSearch(searchQuery, contentTypes) }
                        catch (e: Exception) { EcosystemLogger.e(HaronConstants.TAG, "SearchVM: Archive error: ${e.message}"); emptyList() }
                appendWebResults(r.take(40), "archive", r.size)
            },
            launch {
                val r = try { libGenSearch(searchQuery, contentTypes) }
                        catch (e: Exception) { EcosystemLogger.e(HaronConstants.TAG, "SearchVM: LibGen error: ${e.message}"); emptyList() }
                appendWebResults(r.take(20), "libgen", r.size)
            },
            launch {
                val r = try { torrentSearch(searchQuery) }
                        catch (e: Exception) { EcosystemLogger.e(HaronConstants.TAG, "SearchVM: Torrent error: ${e.message}"); emptyList() }
                appendWebResults(r.take(20), "torrent", r.size)
            }
        )
        // coroutineScope waits for all sources to finish
        val total = _uiState.value.webResults.size
        EcosystemLogger.i(HaronConstants.TAG, "SearchVM: done, $total results total")
        _uiState.update {
            it.copy(
                isWebSearching = false,
                webSearchPhase = null,
                webError = if (it.webResults.isEmpty()) appContext.getString(R.string.web_no_results) else null
            )
        }
    }

    private fun appendWebResults(results: List<WebSearchResult>, sourceKey: String, count: Int) {
        _uiState.update { state ->
            val existing = state.webResults
            val combined = (existing + results).distinctBy { it.url }
            state.copy(
                webResults = combined,
                webSearchSourcesDone = state.webSearchSourcesDone + (sourceKey to count)
            )
        }
    }

    fun dismissWebSearchConfirmation() {
        _uiState.update { it.copy(webSearchConfirmation = null) }
    }

    // --- Web Navigator ---

    fun openWebNavigator(url: String) {
        // Push a loading placeholder, then fetch
        _uiState.update {
            it.copy(webNavigatorStack = listOf(
                WebNavigatorPage(url = url, title = url, links = emptyList(), isLoading = true)
            ))
        }
        viewModelScope.launch {
            val page = webNavigate.fetch(url)
            _uiState.update { it.copy(webNavigatorStack = listOf(page)) }
        }
    }

    fun webNavigatorNavigateTo(link: WebNavigatorLink) {
        if (link.isFile) {
            // Direct file → download
            EcosystemLogger.d(HaronConstants.TAG, "WebNavigator: download file ${link.href}")
            WebDownloadService.startDownload(
                context = appContext,
                url = link.href,
                fileName = link.text.ifEmpty { link.href.substringAfterLast('/') },
                source = com.vamp.haron.domain.model.SearchSource.OPEN_DIRECTORY,
                isMagnet = false
            )
            return
        }
        // Page → push loading placeholder, then fetch
        val stack = _uiState.value.webNavigatorStack ?: return
        val loadingPage = WebNavigatorPage(url = link.href, title = link.href, links = emptyList(), isLoading = true)
        _uiState.update { it.copy(webNavigatorStack = stack + loadingPage) }
        viewModelScope.launch {
            val page = webNavigate.fetch(link.href)
            val current = _uiState.value.webNavigatorStack ?: return@launch
            // Replace the last (loading) page
            _uiState.update { it.copy(webNavigatorStack = current.dropLast(1) + page) }
        }
    }

    fun webNavigatorBack() {
        val stack = _uiState.value.webNavigatorStack ?: return
        if (stack.size <= 1) {
            _uiState.update { it.copy(webNavigatorStack = null) }
        } else {
            _uiState.update { it.copy(webNavigatorStack = stack.dropLast(1)) }
        }
    }

    fun closeWebNavigator() {
        _uiState.update { it.copy(webNavigatorStack = null) }
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
