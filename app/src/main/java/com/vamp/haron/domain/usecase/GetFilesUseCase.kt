package com.vamp.haron.domain.usecase

import com.vamp.haron.data.model.SortDirection
import com.vamp.haron.data.model.SortField
import com.vamp.haron.data.model.SortOrder
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.repository.FileRepository
import javax.inject.Inject

class GetFilesUseCase @Inject constructor(
    private val repository: FileRepository
) {
    suspend operator fun invoke(
        path: String,
        sortOrder: SortOrder,
        showHidden: Boolean
    ): Result<List<FileEntry>> {
        return repository.getFiles(path).map { files ->
            val filtered = if (showHidden) files else files.filter { !it.isHidden }
            sortFiles(filtered, sortOrder)
        }
    }

    private fun sortFiles(files: List<FileEntry>, sortOrder: SortOrder): List<FileEntry> {
        val (dirs, regularFiles) = files.partition { it.isDirectory }

        val comparator = when (sortOrder.field) {
            SortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortField.DATE -> compareBy<FileEntry> { it.lastModified }
            SortField.SIZE -> compareBy<FileEntry> { it.size }
            SortField.EXTENSION -> compareBy(String.CASE_INSENSITIVE_ORDER, FileEntry::extension)
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }

        val effectiveComparator = if (sortOrder.direction == SortDirection.DESCENDING) {
            comparator.reversed()
        } else {
            comparator
        }

        return dirs.sortedWith(effectiveComparator) + regularFiles.sortedWith(effectiveComparator)
    }
}
