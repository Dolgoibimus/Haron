package com.vamp.haron.presentation.comparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.ComparisonHolder
import com.vamp.haron.domain.model.FileMetadataComparison
import com.vamp.haron.domain.model.FolderComparisonEntry
import com.vamp.haron.domain.model.TextDiffResult
import com.vamp.haron.domain.usecase.CompareFoldersUseCase
import com.vamp.haron.domain.usecase.CompareTextFilesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

enum class ComparisonMode {
    TEXT, FOLDER, BINARY, LOADING
}

data class ComparisonUiState(
    val mode: ComparisonMode = ComparisonMode.LOADING,
    val leftName: String = "",
    val rightName: String = "",
    val textDiff: TextDiffResult? = null,
    val folderEntries: List<FolderComparisonEntry> = emptyList(),
    val binaryMetadata: FileMetadataComparison? = null,
    val error: String? = null,
    val filterStatus: String? = null, // null = all
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val isViewingFileDiff: Boolean = false
)

@HiltViewModel
class ComparisonViewModel @Inject constructor(
    private val compareTextFiles: CompareTextFilesUseCase,
    private val compareFolders: CompareFoldersUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ComparisonUiState())
    val state = _state.asStateFlow()

    // Saved folder-level names to restore when returning from file diff
    private var folderLeftName = ""
    private var folderRightName = ""

    init {
        startComparison()
    }

    private fun startComparison() {
        val leftPath = ComparisonHolder.leftPath
        val rightPath = ComparisonHolder.rightPath
        val leftFile = File(leftPath)
        val rightFile = File(rightPath)

        EcosystemLogger.d(HaronConstants.TAG, "ComparisonVM: start compare left=${leftFile.name} right=${rightFile.name}")

        _state.value = _state.value.copy(
            leftName = leftFile.name,
            rightName = rightFile.name,
            mode = ComparisonMode.LOADING
        )

        viewModelScope.launch {
            try {
                when {
                    leftFile.isDirectory && rightFile.isDirectory -> {
                        EcosystemLogger.d(HaronConstants.TAG, "ComparisonVM: folder comparison")
                        val entries = compareFolders(leftPath, rightPath) { current, total ->
                            _state.value = _state.value.copy(
                                progressCurrent = current,
                                progressTotal = total
                            )
                        }
                        folderLeftName = leftFile.name
                        folderRightName = rightFile.name
                        EcosystemLogger.d(HaronConstants.TAG, "ComparisonVM: folder comparison done, ${entries.size} entries")
                        _state.value = _state.value.copy(
                            mode = ComparisonMode.FOLDER,
                            folderEntries = entries
                        )
                    }
                    leftFile.isFile && rightFile.isFile && isTextFile(leftFile) && isTextFile(rightFile) -> {
                        EcosystemLogger.d(HaronConstants.TAG, "ComparisonVM: text comparison")
                        val diff = compareTextFiles(leftPath, rightPath)
                        _state.value = _state.value.copy(
                            mode = ComparisonMode.TEXT,
                            textDiff = diff
                        )
                    }
                    leftFile.isFile && rightFile.isFile -> {
                        EcosystemLogger.d(HaronConstants.TAG, "ComparisonVM: binary comparison")
                        val sameContent = if (leftFile.length() == rightFile.length()) {
                            md5(leftFile) == md5(rightFile)
                        } else false
                        _state.value = _state.value.copy(
                            mode = ComparisonMode.BINARY,
                            binaryMetadata = FileMetadataComparison(
                                leftPath = leftPath,
                                rightPath = rightPath,
                                leftSize = leftFile.length(),
                                rightSize = rightFile.length(),
                                leftModified = leftFile.lastModified(),
                                rightModified = rightFile.lastModified(),
                                sameContent = sameContent
                            )
                        )
                    }
                    else -> {
                        EcosystemLogger.w(HaronConstants.TAG, "ComparisonVM: incompatible types for comparison")
                        _state.value = _state.value.copy(
                            error = "Cannot compare: incompatible types"
                        )
                    }
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "ComparisonVM: comparison failed: ${e.message}")
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun setFilter(status: String?) {
        _state.value = _state.value.copy(filterStatus = status)
    }

    fun openFileDiff(relativePath: String) {
        val leftPath = ComparisonHolder.leftPath
        val rightPath = ComparisonHolder.rightPath
        val leftFile = File("$leftPath/$relativePath")
        val rightFile = File("$rightPath/$relativePath")
        if (!leftFile.isFile || !rightFile.isFile) return

        EcosystemLogger.d(HaronConstants.TAG, "ComparisonVM: open file diff $relativePath")
        viewModelScope.launch {
            try {
                val diff = compareTextFiles(leftFile.absolutePath, rightFile.absolutePath)
                _state.value = _state.value.copy(
                    mode = ComparisonMode.TEXT,
                    textDiff = diff,
                    leftName = leftFile.name,
                    rightName = rightFile.name,
                    isViewingFileDiff = true
                )
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "ComparisonVM: file diff failed for $relativePath: ${e.message}")
            }
        }
    }

    fun goBackToFolderList() {
        _state.value = _state.value.copy(
            mode = ComparisonMode.FOLDER,
            textDiff = null,
            leftName = folderLeftName,
            rightName = folderRightName,
            isViewingFileDiff = false
        )
    }

    private fun isTextFile(file: File): Boolean {
        val textExtensions = setOf(
            "txt", "md", "csv", "json", "xml", "html", "css", "js", "ts",
            "kt", "java", "py", "c", "cpp", "h", "hpp", "rb", "go", "rs",
            "sh", "bat", "yaml", "yml", "toml", "ini", "cfg", "conf",
            "log", "sql", "gradle", "properties", "gitignore", "env",
            "jsx", "tsx", "vue", "svelte", "scss", "less", "swift",
            "dart", "php", "pl", "r", "lua", "makefile", "dockerfile"
        )
        val ext = file.extension.lowercase()
        if (ext in textExtensions) return true
        if (file.length() > 5 * 1024 * 1024) return false // >5 MB — treat as binary
        // Check first 4096 bytes for null bytes
        return try {
            file.inputStream().use { input ->
                val buffer = ByteArray(4096)
                val read = input.read(buffer)
                if (read <= 0) return@use true
                for (i in 0 until read) {
                    if (buffer[i] == 0.toByte()) return@use false
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
