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
import com.vamp.haron.common.util.AesZipHelper
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import com.vamp.haron.common.util.MultiVolumeRarHelper
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import android.os.StatFs
import com.vamp.core.logger.EcosystemLogger
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import javax.inject.Inject

private const val TAG = "ExtractArchive"

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
        password: String? = null,
        basePrefix: String = "" // strip this prefix from output paths (for inline archive browsing)
    ): Flow<ExtractProgress> = flow {
        try {
            // Check free space before extraction
            val freeBytes = try {
                val stat = StatFs(destinationDir)
                stat.availableBytes
            } catch (_: Exception) { Long.MAX_VALUE }
            val archiveSize = try {
                if (archivePath.startsWith("content://")) 0L
                else File(archivePath).length()
            } catch (_: Exception) { 0L }
            // Heuristic: need at least 2x archive size for extraction
            if (archiveSize > 0 && freeBytes < archiveSize * 2) {
                val needed = android.text.format.Formatter.formatFileSize(context, archiveSize * 2)
                val available = android.text.format.Formatter.formatFileSize(context, freeBytes)
                EcosystemLogger.e(TAG, "Not enough space: need ~$needed, available $available")
                emit(ExtractProgress(0, 0, "", error = context.getString(R.string.extract_no_space, available)))
                return@flow
            }

            val isContentUri = archivePath.startsWith("content://")
            val extension = BrowseArchiveUseCase.archiveType(archivePath)
            EcosystemLogger.d(TAG, "Extracting $extension: ${archivePath.takeLast(60)} → $destinationDir")
            when (extension) {
                "zip" -> extractZip(archivePath, destinationDir, selectedEntries, isContentUri, password, basePrefix)
                "7z" -> extract7z(archivePath, destinationDir, selectedEntries, isContentUri, password, basePrefix)
                "rar" -> extractRar(archivePath, destinationDir, selectedEntries, isContentUri, password, basePrefix)
                "tar", "tar.gz", "tar.bz2", "tar.xz" -> extractTar(archivePath, destinationDir, selectedEntries, isContentUri, basePrefix, extension)
                else -> emit(ExtractProgress(0, 0, "", error = context.getString(R.string.extract_unsupported_format)))
            }
        } catch (e: Throwable) {
            EcosystemLogger.e(TAG, "Extract failed: ${e.javaClass.simpleName}: ${e.message}")
            emit(ExtractProgress(0, 0, "", error = e.message ?: context.getString(R.string.extract_error_generic)))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<ExtractProgress>.extractZip(
        archivePath: String,
        destinationDir: String,
        selectedEntries: Set<String>?,
        isContentUri: Boolean,
        password: String?,
        basePrefix: String
    ) {
        val file = if (isContentUri) copyToTemp(archivePath) else File(archivePath)
        try {
            val isEncrypted = AesZipHelper.isZipEncrypted(file)
            if (isEncrypted) {
                // AES encrypted ZIP — use AesZipHelper
                val pw = password?.toCharArray() ?: throw IllegalStateException("encrypted")
                val entries = AesZipHelper.listEncryptedZip(file, pw).filter { e ->
                    !e.isDirectory && (selectedEntries == null || selectedEntries.any { sel ->
                        e.name.trimEnd('/').startsWith(sel)
                    })
                }
                val total = entries.size
                entries.forEachIndexed { index, entry ->
                    emit(ExtractProgress(index, total, entry.name.substringAfterLast('/')))
                    val outputName = stripPrefix(entry.name, basePrefix)
                    val outFile = File(destinationDir, outputName)
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output ->
                        AesZipHelper.extractDecryptedEntry(file, entry.name, pw, output)
                    }
                }
                emit(ExtractProgress(total, total, "", isComplete = true))
            } else {
                // Unencrypted ZIP — use standard java.util.zip.ZipFile
                val zipFile = java.util.zip.ZipFile(file)
                zipFile.use { zf ->
                    val entries = zf.entries().asSequence().filter { e ->
                        !e.isDirectory && (selectedEntries == null || selectedEntries.any { sel ->
                            e.name.trimEnd('/').startsWith(sel)
                        })
                    }.toList()
                    val total = entries.size
                    entries.forEachIndexed { index, entry ->
                        emit(ExtractProgress(index, total, entry.name.substringAfterLast('/')))
                        val outputName = stripPrefix(entry.name, basePrefix)
                        val outFile = File(destinationDir, outputName)
                        outFile.parentFile?.mkdirs()
                        zf.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    emit(ExtractProgress(total, total, "", isComplete = true))
                }
            }
        } finally {
            if (isContentUri) file.delete()
        }
    }

    private suspend fun FlowCollector<ExtractProgress>.extract7z(
        archivePath: String,
        destinationDir: String,
        selectedEntries: Set<String>?,
        isContentUri: Boolean,
        password: String?,
        basePrefix: String
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
                                val outputName = stripPrefix(entry.name, basePrefix)
                                val outFile = File(destinationDir, outputName)
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
        password: String?,
        basePrefix: String
    ) {
        val file = if (isContentUri) copyToTemp(archivePath) else File(archivePath)
        try {
            // Multi-volume RAR — always use 7-Zip-JBinding (junrar doesn't support split)
            if (!isContentUri && MultiVolumeRarHelper.isMultiVolumeRar(file)) {
                val firstVolume = MultiVolumeRarHelper.findFirstVolume(file)
                val missing = MultiVolumeRarHelper.findMissingVolumes(firstVolume)
                if (missing.isNotEmpty()) {
                    emit(ExtractProgress(0, 0, "", error = context.getString(R.string.extract_missing_volumes, missing.joinToString())))
                    return
                }
                val volumeCount = MultiVolumeRarHelper.countVolumes(firstVolume)
                EcosystemLogger.d(TAG, "extractRar: multi-volume RAR, $volumeCount volumes, first=${firstVolume.name}")
                extractMultiVolumeRar(firstVolume, destinationDir, selectedEntries, password, basePrefix)
                return
            }
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
                        val outputName = stripPrefix(name, basePrefix)
                        val outFile = File(destinationDir, outputName)
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { output ->
                            rar.extractFile(header, output)
                        }
                    }
                    emit(ExtractProgress(total, total, "", isComplete = true))
                }
            } catch (e: Throwable) {
                // RAR5 or other junrar failure — fallback to 7-Zip-JBinding
                EcosystemLogger.d(TAG, "junrar failed (${e.javaClass.simpleName}), trying 7-Zip-JBinding")
                extractRarWith7Zip(file, destinationDir, selectedEntries, password, basePrefix)
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
        password: String?,
        basePrefix: String
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
                val outputName = stripPrefix(path, basePrefix)
                val outFile = File(destinationDir, outputName)
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

    /** Extract multi-volume RAR using 7-Zip-JBinding with IArchiveOpenVolumeCallback */
    private suspend fun FlowCollector<ExtractProgress>.extractMultiVolumeRar(
        firstVolume: File,
        destinationDir: String,
        selectedEntries: Set<String>?,
        password: String?,
        basePrefix: String
    ) {
        SevenZip.initSevenZipFromPlatformJAR()
        val volumeCallback = MultiVolumeRarHelper.VolumeCallback(firstVolume)
        val raf = RandomAccessFile(firstVolume, "r")
        val firstStream = RandomAccessFileInStream(raf)
        val archive: IInArchive = SevenZip.openInArchive(null, firstStream, volumeCallback)
        try {
            val count = archive.numberOfItems
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
            EcosystemLogger.d(TAG, "extractMultiVolumeRar: extracting $total files")

            for (idx in toExtract) {
                val path = (archive.getProperty(idx, PropID.PATH) as? String ?: "").replace('\\', '/')
                val fileName = path.substringAfterLast('/')
                emit(ExtractProgress(current, total, fileName))
                val outputName = stripPrefix(path, basePrefix)
                val outFile = File(destinationDir, outputName)
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { fos ->
                    archive.extractSlow(idx, ISequentialOutStream { data ->
                        fos.write(data)
                        data.size
                    }, password ?: "")
                }
                current++
            }
            EcosystemLogger.d(TAG, "extractMultiVolumeRar: complete, $total files extracted")
            emit(ExtractProgress(total, total, "", isComplete = true))
        } finally {
            archive.close()
            firstStream.close()
            raf.close()
            volumeCallback.close()
        }
    }

    private suspend fun FlowCollector<ExtractProgress>.extractTar(
        archivePath: String,
        destinationDir: String,
        selectedEntries: Set<String>?,
        isContentUri: Boolean,
        basePrefix: String,
        type: String
    ) {
        val file = if (isContentUri) copyToTemp(archivePath) else File(archivePath)
        try {
            // First pass: count entries to extract
            val toExtract = mutableListOf<String>()
            createTarStream(file, type).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.trimEnd('/')
                        if (selectedEntries == null || selectedEntries.any { sel -> name.startsWith(sel) }) {
                            toExtract.add(name)
                        }
                    }
                    entry = tar.nextEntry
                }
            }
            val total = toExtract.size
            val selectedNames = toExtract.toSet()

            // Second pass: extract
            var current = 0
            createTarStream(file, type).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.trimEnd('/') in selectedNames) {
                        val fileName = entry.name.trimEnd('/').substringAfterLast('/')
                        emit(ExtractProgress(current, total, fileName))
                        val outputName = stripPrefix(entry.name.trimEnd('/'), basePrefix)
                        val outFile = File(destinationDir, outputName)
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { output ->
                            tar.copyTo(output)
                        }
                        current++
                    }
                    entry = tar.nextEntry
                }
            }
            emit(ExtractProgress(total, total, "", isComplete = true))
        } finally {
            if (isContentUri) file.delete()
        }
    }

    private fun createTarStream(file: File, type: String): TarArchiveInputStream {
        val rawStream = BufferedInputStream(FileInputStream(file))
        val decompressedStream = when (type) {
            "tar.gz" -> GzipCompressorInputStream(rawStream)
            "tar.bz2" -> BZip2CompressorInputStream(rawStream)
            "tar.xz" -> XZCompressorInputStream(rawStream)
            else -> rawStream
        }
        return TarArchiveInputStream(decompressedStream)
    }

    internal fun stripPrefix(path: String, prefix: String): String =
        Companion.stripPrefix(path, prefix)

    companion object {
        internal fun stripPrefix(path: String, prefix: String): String {
            if (prefix.isEmpty()) return path
            val p = "$prefix/"
            return if (path.startsWith(p)) path.removePrefix(p) else path
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
