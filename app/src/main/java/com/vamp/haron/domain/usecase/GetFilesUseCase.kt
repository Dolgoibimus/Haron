package com.vamp.haron.domain.usecase

import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.model.SortDirection
import com.vamp.haron.data.model.SortField
import com.vamp.haron.data.model.SortOrder
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.model.SecureFileEntry
import com.vamp.haron.domain.repository.FileRepository
import com.vamp.haron.domain.repository.SecureFolderRepository
import javax.inject.Inject

class GetFilesUseCase @Inject constructor(
    private val repository: FileRepository,
    private val secureFolderRepository: SecureFolderRepository
) {
    suspend operator fun invoke(
        path: String,
        sortOrder: SortOrder,
        showHidden: Boolean,
        showProtected: Boolean = false
    ): Result<List<FileEntry>> {
        // Virtual secure path — show all protected entries
        if (path == HaronConstants.VIRTUAL_SECURE_PATH) {
            return try {
                val allEntries = secureFolderRepository.getAllProtectedEntries()
                val virtualEntries = allEntries.map { it.toFileEntry(allEntries) }
                Result.success(sortFiles(virtualEntries, sortOrder))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        // Protected directory (dir was deleted when protected) — show only protected children
        if (showProtected && secureFolderRepository.isFileProtected(path)) {
            return try {
                val allEntries = secureFolderRepository.getAllProtectedEntries()
                val children = allEntries.filter { entry ->
                    val parent = java.io.File(entry.originalPath).parent ?: ""
                    parent == path
                }
                val virtualEntries = children.map { it.toFileEntry(allEntries) }
                Result.success(sortFiles(virtualEntries, sortOrder))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        return repository.getFiles(path).map { files ->
            var filtered = if (showHidden) files else files.filter { !it.isHidden }
            if (showProtected) {
                val allEntries = secureFolderRepository.getAllProtectedEntries()
                val protectedEntries = allEntries.filter { entry ->
                    val parent = java.io.File(entry.originalPath).parent ?: ""
                    parent == path
                }
                val virtualEntries = protectedEntries.map { it.toFileEntry(allEntries) }
                // Exclude protected entries whose path already exists as a real file (avoids duplicate keys)
                val existingPaths = filtered.map { it.path }.toSet()
                filtered = filtered + virtualEntries.filter { it.path !in existingPaths }
            }
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

private fun SecureFileEntry.toFileEntry(allEntries: List<SecureFileEntry>? = null): FileEntry {
    val ext = originalName.substringAfterLast('.', "")
    val count = if (isDirectory && allEntries != null) {
        allEntries.count { it.originalPath.startsWith("$originalPath/") }
    } else 0
    return FileEntry(
        name = originalName,
        path = originalPath,
        isDirectory = isDirectory,
        size = originalSize,
        lastModified = addedAt,
        extension = ext,
        isHidden = false,
        childCount = count,
        isProtected = true
    )
}
