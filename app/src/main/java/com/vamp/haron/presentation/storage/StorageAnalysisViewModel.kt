package com.vamp.haron.presentation.storage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.R
import com.vamp.haron.domain.usecase.AnalyzeStorageUseCase
import com.vamp.haron.domain.usecase.StorageAnalysis
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class StorageAnalysisState(
    val analysis: StorageAnalysis = StorageAnalysis(),
    val isLoading: Boolean = false,
    val expandedCategory: String? = null,
    val selectedFiles: Set<String> = emptySet(),
    val largeFilesExpanded: Boolean = false,
    val categoryFilesExpanded: Boolean = false
)

@HiltViewModel
class StorageAnalysisViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val analyzeStorageUseCase: AnalyzeStorageUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(StorageAnalysisState())
    val state = _state.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage = _toastMessage.asSharedFlow()

    fun startScan() {
        EcosystemLogger.d(HaronConstants.TAG, "StorageVM: starting storage analysis")
        _state.update { it.copy(isLoading = true, selectedFiles = emptySet(), expandedCategory = null, categoryFilesExpanded = false, largeFilesExpanded = false) }
        viewModelScope.launch {
            try {
                analyzeStorageUseCase().collect { analysis ->
                    _state.update { it.copy(analysis = analysis, isLoading = analysis.isScanning) }
                    if (!analysis.isScanning) {
                        EcosystemLogger.d(HaronConstants.TAG, "StorageVM: analysis complete, ${analysis.categories.size} categories")
                    }
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "StorageVM: analysis failed: ${e.message}")
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleCategory(name: String) {
        _state.update {
            it.copy(
                expandedCategory = if (it.expandedCategory == name) null else name,
                categoryFilesExpanded = false
            )
        }
    }

    fun toggleCategoryFilesExpanded() {
        _state.update { it.copy(categoryFilesExpanded = !it.categoryFilesExpanded) }
    }

    fun toggleFileSelection(path: String) {
        _state.update {
            val newSet = it.selectedFiles.toMutableSet()
            if (path in newSet) newSet.remove(path) else newSet.add(path)
            it.copy(selectedFiles = newSet)
        }
    }

    fun toggleLargeFilesExpanded() {
        _state.update { it.copy(largeFilesExpanded = !it.largeFilesExpanded) }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedFiles = emptySet()) }
    }

    fun deleteSelectedFiles() {
        val paths = _state.value.selectedFiles.toList()
        if (paths.isEmpty()) return

        EcosystemLogger.d(HaronConstants.TAG, "StorageVM: delete ${paths.size} selected files")
        viewModelScope.launch {
            var deleted = 0
            paths.forEach { path ->
                try {
                    val file = File(path)
                    if (file.exists() && file.delete()) deleted++
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "StorageVM: delete failed $path: ${e.message}")
                }
            }
            EcosystemLogger.d(HaronConstants.TAG, "StorageVM: deleted $deleted/${paths.size} files")
            _state.update { it.copy(selectedFiles = emptySet()) }
            _toastMessage.tryEmit(appContext.getString(R.string.storage_deleted_format, deleted))
            // Rescan
            startScan()
        }
    }
}
