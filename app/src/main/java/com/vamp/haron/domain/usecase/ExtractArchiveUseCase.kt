package com.vamp.haron.domain.usecase

import android.content.Context
import android.net.Uri
import com.github.junrar.Archive
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import javax.inject.Inject

data class ExtractProgress(
    val current: Int,
    val total: Int,
    val fileName: String,
    val isComplete: Boolean = false,
    val error: String? = null
)

class ExtractArchiveUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(
        archivePath: String,
        destinationDir: String,
        selectedEntries: Set<String>? = null // null = extract all
    ): Flow<ExtractProgress> = flow {
        try {
            val isContentUri = archivePath.startsWith("content://")
            val extension = archivePath.substringAfterLast('.').lowercase()
            when (extension) {
                "zip" -> extractZip(archivePath, destinationDir, selectedEntries, isContentUri)
                "7z" -> extract7z(archivePath, destinationDir, selectedEntries, isContentUri)
                "rar" -> extractRar(archivePath, destinationDir, selectedEntries, isContentUri)
                else -> emit(ExtractProgress(0, 0, "", error = "Неподдерживаемый формат"))
            }
        } catch (e: Exception) {
            emit(ExtractProgress(0, 0, "", error = e.message ?: "Ошибка извлечения"))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<ExtractProgress>.extractZip(
        archivePath: String,
        destinationDir: String,
        selectedEntries: Set<String>?,
        isContentUri: Boolean
    ) {
        val file = if (isContentUri) copyToTemp(archivePath) else File(archivePath)
        try {
            val zipFile = ZipFile(file)
            zipFile.use { zip ->
                val entries = zip.entries().toList().filter { ze ->
                    !ze.isDirectory && (selectedEntries == null || selectedEntries.any { sel ->
                        ze.name.trimEnd('/').startsWith(sel)
                    })
                }
                val total = entries.size
                entries.forEachIndexed { index, ze ->
                    emit(ExtractProgress(index, total, ze.name.substringAfterLast('/')))
                    val outFile = File(destinationDir, ze.name)
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(ze).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                emit(ExtractProgress(total, total, "", isComplete = true))
            }
        } finally {
            if (isContentUri) file.delete()
        }
    }

    private suspend fun FlowCollector<ExtractProgress>.extract7z(
        archivePath: String,
        destinationDir: String,
        selectedEntries: Set<String>?,
        isContentUri: Boolean
    ) {
        val file = if (isContentUri) copyToTemp(archivePath) else File(archivePath)
        try {
            val channel = if (isContentUri) {
                SeekableInMemoryByteChannel(file.readBytes())
            } else {
                java.nio.channels.FileChannel.open(
                    file.toPath(), java.nio.file.StandardOpenOption.READ
                )
            }
            channel.use { ch ->
                val sevenZ = SevenZFile.builder().setSeekableByteChannel(ch).get()
                sevenZ.use { archive ->
                    val allEntries = archive.entries.filter { !it.isDirectory }.toList()
                    val filtered = allEntries.filter { e ->
                        selectedEntries == null || selectedEntries.any { sel ->
                            e.name.trimEnd('/').startsWith(sel)
                        }
                    }
                    val total = filtered.size
                    // Re-iterate to extract (SevenZFile is sequential)
                    val selectedNames = filtered.map { it.name }.toSet()
                    var current = 0
                    val sevenZ2 = SevenZFile.builder().setSeekableByteChannel(
                        if (isContentUri) SeekableInMemoryByteChannel(file.readBytes())
                        else java.nio.channels.FileChannel.open(file.toPath(), java.nio.file.StandardOpenOption.READ)
                    ).get()
                    sevenZ2.use { arc ->
                        var entry = arc.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name in selectedNames) {
                                emit(ExtractProgress(current, total, entry.name.substringAfterLast('/')))
                                val outFile = File(destinationDir, entry.name)
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { output ->
                                    val buf = ByteArray(8192)
                                    var len: Int
                                    while (arc.read(buf).also { len = it } > 0) {
                                        output.write(buf, 0, len)
                                    }
                                }
                                current++
                            }
                            entry = arc.nextEntry
                        }
                    }
                    emit(ExtractProgress(total, total, "", isComplete = true))
                }
            }
        } finally {
            if (isContentUri) file.delete()
        }
    }

    private suspend fun FlowCollector<ExtractProgress>.extractRar(
        archivePath: String,
        destinationDir: String,
        selectedEntries: Set<String>?,
        isContentUri: Boolean
    ) {
        val file = if (isContentUri) copyToTemp(archivePath) else File(archivePath)
        try {
            val archive = Archive(file)
            archive.use { rar ->
                val headers = (rar.fileHeaders ?: emptyList()).filter { h ->
                    !h.isDirectory && (selectedEntries == null || selectedEntries.any { sel ->
                        (h.fileName ?: "").replace('\\', '/').trimEnd('/').startsWith(sel)
                    })
                }
                val total = headers.size
                headers.forEachIndexed { index, header ->
                    val name = (header.fileName ?: "?").replace('\\', '/')
                    emit(ExtractProgress(index, total, name.substringAfterLast('/')))
                    val outFile = File(destinationDir, name)
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output ->
                        rar.extractFile(header, output)
                    }
                }
                emit(ExtractProgress(total, total, "", isComplete = true))
            }
        } finally {
            if (isContentUri) file.delete()
        }
    }

    private fun copyToTemp(contentUri: String): File {
        val ext = contentUri.substringAfterLast('.')
        val tempFile = File(context.cacheDir, "archive_extract_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(Uri.parse(contentUri))?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open content URI")
        return tempFile
    }
}
