package com.vamp.haron.domain.model

enum class DiffLineType {
    UNCHANGED, ADDED, REMOVED, MODIFIED
}

data class DiffLine(
    val lineNumber: Int?,
    val text: String,
    val type: DiffLineType
)

data class TextDiffResult(
    val leftLines: List<DiffLine>,
    val rightLines: List<DiffLine>,
    val addedCount: Int,
    val removedCount: Int,
    val modifiedCount: Int
)

enum class ComparisonStatus {
    IDENTICAL, DIFFERENT, LEFT_ONLY, RIGHT_ONLY
}

data class FolderComparisonEntry(
    val relativePath: String,
    val name: String,
    val isDirectory: Boolean,
    val status: ComparisonStatus,
    val leftSize: Long?,
    val rightSize: Long?,
    val leftModified: Long?,
    val rightModified: Long?
)

data class FileMetadataComparison(
    val leftPath: String,
    val rightPath: String,
    val leftSize: Long,
    val rightSize: Long,
    val leftModified: Long,
    val rightModified: Long,
    val sameContent: Boolean
)
