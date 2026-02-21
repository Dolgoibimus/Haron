package com.vamp.haron.domain.usecase

import android.content.Context
import android.net.Uri
import com.github.junrar.Archive
import com.vamp.haron.domain.model.ArchiveEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import javax.inject.Inject

class BrowseArchiveUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(
        archivePath: String,
        virtualPath: String = ""
    ): Result<List<ArchiveEntry>> = withContext(Dispatchers.IO) {
        try {
            val isContentUri = archivePath.startsWith("content://")
            val extension = archivePath.substringAfterLast('.').lowercase()
            val entries = when (extension) {
                "zip" -> browseZip(archivePath, virtualPath, isContentUri)
                "7z" -> browse7z(archivePath, virtualPath, isContentUri)
                "rar" -> browseRar(archivePath, virtualPath, isContentUri)
                else -> emptyList()
            }
            Result.success(entries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun browseZip(archivePath: String, virtualPath: String, isContentUri: Boolean): List<ArchiveEntry> {
        val file = if (isContentUri) copyToTemp(archivePath) else File(archivePath)
        try {
            val zipFile = ZipFile(file)
            return zipFile.use { zip ->
                val prefix = if (virtualPath.isEmpty()) "" else "$virtualPath/"
                val allEntries = zip.entries().toList()
                filterDirectChildren(allEntries.map { ze ->
                    ArchiveEntry(
                        name = ze.name.trimEnd('/').substringAfterLast('/'),
                        fullPath = ze.name.trimEnd('/'),
                        size = ze.size.coerceAtLeast(0),
                        isDirectory = ze.isDirectory,
                        compressedSize = ze.compressedSize.coerceAtLeast(0),
                        lastModified = ze.time
                    )
                }, prefix)
            }
        } finally {
            if (isContentUri) file.delete()
        }
    }

    private fun browse7z(archivePath: String, virtualPath: String, isContentUri: Boolean): List<ArchiveEntry> {
        val file = if (isContentUri) copyToTemp(archivePath) else File(archivePath)
        try {
            val channel = if (isContentUri) {
                SeekableInMemoryByteChannel(file.readBytes())
            } else {
                java.nio.channels.FileChannel.open(
                    file.toPath(), java.nio.file.StandardOpenOption.READ
                )
            }
            return channel.use { ch ->
                val sevenZ = SevenZFile.builder().setSeekableByteChannel(ch).get()
                sevenZ.use { archive ->
                    val prefix = if (virtualPath.isEmpty()) "" else "$virtualPath/"
                    val all = mutableListOf<ArchiveEntry>()
                    for (e in archive.entries) {
                        all.add(ArchiveEntry(
                            name = e.name.trimEnd('/').substringAfterLast('/'),
                            fullPath = e.name.trimEnd('/'),
                            size = e.size.coerceAtLeast(0),
                            isDirectory = e.isDirectory,
                            compressedSize = 0,
                            lastModified = e.lastModifiedDate?.time ?: 0
                        ))
                    }
                    filterDirectChildren(all, prefix)
                }
            }
        } finally {
            if (isContentUri) file.delete()
        }
    }

    private fun browseRar(archivePath: String, virtualPath: String, isContentUri: Boolean): List<ArchiveEntry> {
        val file = if (isContentUri) copyToTemp(archivePath) else File(archivePath)
        try {
            val archive = Archive(file)
            return archive.use { rar ->
                val prefix = if (virtualPath.isEmpty()) "" else "$virtualPath/"
                val all = (rar.fileHeaders ?: emptyList()).map { h ->
                    ArchiveEntry(
                        name = (h.fileName ?: "?").replace('\\', '/').trimEnd('/').substringAfterLast('/'),
                        fullPath = (h.fileName ?: "?").replace('\\', '/').trimEnd('/'),
                        size = h.fullUnpackSize.coerceAtLeast(0),
                        isDirectory = h.isDirectory,
                        compressedSize = h.fullPackSize.coerceAtLeast(0),
                        lastModified = h.mTime?.time ?: 0
                    )
                }
                filterDirectChildren(all, prefix)
            }
        } finally {
            if (isContentUri) file.delete()
        }
    }

    /**
     * From a flat list of all archive entries, filter to show only direct children of [prefix].
     * Also synthesizes virtual directories for entries that have intermediate paths.
     */
    private fun filterDirectChildren(allEntries: List<ArchiveEntry>, prefix: String): List<ArchiveEntry> {
        val directChildren = mutableMapOf<String, ArchiveEntry>()

        for (entry in allEntries) {
            val path = entry.fullPath
            if (prefix.isNotEmpty() && !path.startsWith(prefix)) continue
            val relative = if (prefix.isEmpty()) path else path.removePrefix(prefix)
            if (relative.isEmpty()) continue

            val parts = relative.split('/')
            val childName = parts[0]

            if (parts.size == 1) {
                // Direct child
                directChildren[childName] = entry
            } else {
                // Intermediate directory — synthesize if not already present
                if (childName !in directChildren) {
                    directChildren[childName] = ArchiveEntry(
                        name = childName,
                        fullPath = if (prefix.isEmpty()) childName else "${prefix.trimEnd('/')}/$childName",
                        size = 0,
                        isDirectory = true
                    )
                }
            }
        }

        return directChildren.values
            .sortedWith(compareByDescending<ArchiveEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private fun copyToTemp(contentUri: String): File {
        val ext = contentUri.substringAfterLast('.')
        val tempFile = File(context.cacheDir, "archive_browse_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(Uri.parse(contentUri))?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open content URI")
        return tempFile
    }
}
