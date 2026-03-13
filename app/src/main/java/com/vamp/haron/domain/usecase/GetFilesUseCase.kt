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
        // Virtual secure path — show only top-level protected entries
        if (path == HaronConstants.VIRTUAL_SECURE_PATH) {
            return try {
                val allEntries = secureFolderRepository.getAllProtectedEntries()
                // Collect paths of all protected directories
                val protectedDirPaths = allEntries
                    .filter { it.isDirectory }
                    .map { it.originalPath }
                    .toSet()
                // Show entry only if it's NOT a descendant of any protected directory
                val rootEntries = allEntries.filter { entry ->
                    protectedDirPaths.none { dirPath ->
                        entry.originalPath != dirPath &&
                            entry.originalPath.startsWith("$dirPath/")
                    }
                }
                val virtualEntries = rootEntries.map { it.toFileEntry(allEntries) }
                Result.success(sortFiles(virtualEntries, sortOrder))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        // Protected directory (dir itself is in the secure index — was deleted when protected)
        if (showProtected && secureFolderRepository.isFileProtected(path)) {
            return buildProtectedDirListing(path, sortOrder)
        }

        // Normal file listing — try reading real directory
        val realResult = repository.getFiles(path)
        val realFiles = realResult.getOrNull()

        if (realFiles != null) {
            // Real directory exists — apply filtering and optionally mix in protected entries
            var filtered = if (showHidden) realFiles else realFiles.filter { !it.isHidden }
            if (showProtected) {
                val allEntries = secureFolderRepository.getAllProtectedEntries()
                val prefix = "$path/"
                val protectedEntries = allEntries.filter { entry ->
                    entry.originalPath.startsWith(prefix) &&
                        !entry.isDirectory &&
                        !entry.originalPath.removePrefix(prefix).contains('/')
                }
                val virtualEntries = protectedEntries.map { it.toFileEntry(allEntries) }
                val existingPaths = filtered.map { it.path }.toSet()
                filtered = filtered + virtualEntries.filter { it.path !in existingPaths }
            }
            return Result.success(sortFiles(filtered, sortOrder))
        }

        // getFiles failed (dir doesn't exist) — check if virtual protected subdirectory
        if (showProtected && secureFolderRepository.hasProtectedDescendants(path)) {
            return buildProtectedDirListing(path, sortOrder)
        }

        // Nothing found — return the original failure
        return realResult.map { sortFiles(it, sortOrder) }
    }

    private suspend fun buildProtectedDirListing(
        path: String,
        sortOrder: SortOrder
    ): Result<List<FileEntry>> {
        return try {
            val allEntries = secureFolderRepository.getAllProtectedEntries()
            val prefix = "$path/"
            val descendants = allEntries.filter { it.originalPath.startsWith(prefix) && !it.isDirectory }
            val directChildren = mutableMapOf<String, FileEntry>()
            for (entry in descendants) {
                val relative = entry.originalPath.removePrefix(prefix)
                val parts = relative.split('/')
                val childName = parts[0]
                if (parts.size == 1) {
                    directChildren[childName] = entry.toFileEntry(allEntries)
                } else {
                    if (childName !in directChildren) {
                        val dirPath = "$path/$childName"
                        val childCount = allEntries.count { e ->
                            e.originalPath.startsWith("$dirPath/") && !e.isDirectory
                        }
                        directChildren[childName] = FileEntry(
                            name = childName,
                            path = dirPath,
                            isDirectory = true,
                            size = 0L,
                            lastModified = 0L,
                            extension = "",
                            isHidden = false,
                            childCount = childCount,
                            isProtected = true
                        )
                    }
                }
            }
            Result.success(sortFiles(directChildren.values.toList(), sortOrder))
        } catch (e: Exception) {
            Result.failure(e)
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
