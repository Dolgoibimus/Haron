package com.vamp.haron.domain.usecase

import android.content.Context
import android.net.Uri
import com.github.junrar.Archive
import com.vamp.haron.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import net.lingala.zip4j.ZipFile as Zip4jFile
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
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
        selectedEntries: Set<String>? = null, // null = extract all
        password: String? = null
    ): Flow<ExtractProgress> = flow {
        try {
            val isContentUri = archivePath.startsWith("content://")
            val extension = archivePath.substringAfterLast('.').lowercase()
            when (extension) {
                "zip" -> extractZip(archivePath, destinationDir, selectedEntries, isContentUri, password)
                "7z" -> extract7z(archivePath, destinationDir, selectedEntries, isContentUri, password)
                "rar" -> extractRar(archivePath, destinationDir, selectedEntries, isContentUri, password)
                else -> emit(ExtractProgress(0, 0, "", error = context.getString(R.string.extract_unsupported_format)))
            }
        } catch (e: Exception) {
            emit(ExtractProgress(0, 0, "", error = e.message ?: context.getString(R.string.extract_error_generic)))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<ExtractProgress>.extractZip(
        archivePath: String,
        destinationDir: String,
        selectedEntries: Set<String>?,
        isContentUri: Boolean,
        password: String?
    ) {
        val file = if (isContentUri) copyToTemp(archivePath) else File(archivePath)
        try {
            if (password != null) {
                extractZipWithPassword(file, destinationDir, selectedEntries, password)
                return
            }
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

    private suspend fun FlowCollector<ExtractProgress>.extractZipWithPassword(
        file: File,
        destinationDir: String,
        selectedEntries: Set<String>?,
        password: String
    ) {
        val zip4j = Zip4jFile(file)
        zip4j.setPassword(password.toCharArray())
        val headers = zip4j.fileHeaders.filter { h ->
            !h.isDirectory && (selectedEntries == null || selectedEntries.any { sel ->
                h.fileName.trimEnd('/').startsWith(sel)
            })
        }
        val total = headers.size
        headers.forEachIndexed { index, header ->
            emit(ExtractProgress(index, total, header.fileName.substringAfterLast('/')))
            val outFile = File(destinationDir, header.fileName)
            outFile.parentFile?.mkdirs()
            zip4j.getInputStream(header).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        emit(ExtractProgress(total, total, "", isComplete = true))
    }

    private suspend fun FlowCollector<ExtractProgress>.extract7z(
        archivePath: String,
        destinationDir: String,
        selectedEntries: Set<String>?,
        isContentUri: Boolean,
        password: String?
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
                val builder = SevenZFile.builder().setSeekableByteChannel(ch)
                if (password != null) builder.setPassword(password.toCharArray())
                val sevenZ = builder.get()
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
                    val builder2 = SevenZFile.builder().setSeekableByteChannel(
                        if (isContentUri) SeekableInMemoryByteChannel(file.readBytes())
                        else java.nio.channels.FileChannel.open(file.toPath(), java.nio.file.StandardOpenOption.READ)
                    )
                    if (password != null) builder2.setPassword(password.toCharArray())
                    val sevenZ2 = builder2.get()
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
        isContentUri: Boolean,
        password: String?
    ) {
        val file = if (isContentUri) copyToTemp(archivePath) else File(archivePath)
        try {
            // Try junrar first (RAR4)
            try {
                val archive = if (password != null) Archive(file, password) else Archive(file)
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
            } catch (e: Exception) {
                // RAR5 or other junrar failure — fallback to 7-Zip-JBinding
                extractRarWith7Zip(file, destinationDir, selectedEntries, password)
            }
        } finally {
            if (isContentUri) file.delete()
        }
    }

    /** Extract RAR (including RAR5) using 7-Zip-JBinding native engine */
    private suspend fun FlowCollector<ExtractProgress>.extractRarWith7Zip(
        file: File,
        destinationDir: String,
        selectedEntries: Set<String>?,
        password: String?
    ) {
        SevenZip.initSevenZipFromPlatformJAR()
        val raf = RandomAccessFile(file, "r")
        val stream = RandomAccessFileInStream(raf)
        val archive: IInArchive = if (password != null) {
            SevenZip.openInArchive(null, stream, password)
        } else {
            SevenZip.openInArchive(null, stream)
        }
        try {
            val count = archive.numberOfItems
            // Collect indices to extract
            val toExtract = mutableListOf<Int>()
            for (i in 0 until count) {
                val isDir = archive.getProperty(i, PropID.IS_FOLDER) as? Boolean ?: false
                if (isDir) continue
                val path = (archive.getProperty(i, PropID.PATH) as? String ?: "").replace('\\', '/')
                if (selectedEntries == null || selectedEntries.any { sel -> path.trimEnd('/').startsWith(sel) }) {
                    toExtract.add(i)
                }
            }
            val total = toExtract.size
            var current = 0

            for (idx in toExtract) {
                val path = (archive.getProperty(idx, PropID.PATH) as? String ?: "").replace('\\', '/')
                val fileName = path.substringAfterLast('/')
                emit(ExtractProgress(current, total, fileName))
                val outFile = File(destinationDir, path)
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { fos ->
                    archive.extractSlow(idx, ISequentialOutStream { data ->
                        fos.write(data)
                        data.size
                    }, password ?: "")
                }
                current++
            }
            emit(ExtractProgress(total, total, "", isComplete = true))
        } finally {
            archive.close()
            stream.close()
            raf.close()
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
