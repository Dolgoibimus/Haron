package com.vamp.haron.domain.usecase

import android.content.Context
import android.net.Uri
import com.github.junrar.Archive
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile as Zip4jFile
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import javax.inject.Inject

private const val TAG = "ReadArchiveEntry"
private const val MAX_ENTRY_SIZE = 10L * 1024 * 1024 // 10 MB

class ReadArchiveEntryUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Read raw bytes of a single entry from an archive without extracting to disk.
     * @param archivePath Path to the archive file
     * @param entryFullPath Full path of the entry inside the archive
     * @param password Optional password for encrypted archives
     * @return ByteArray of entry content, or null on failure
     */
    suspend operator fun invoke(
        archivePath: String,
        entryFullPath: String,
        password: String? = null
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val isContentUri = archivePath.startsWith("content://")
            val extension = BrowseArchiveUseCase.archiveType(archivePath)
            EcosystemLogger.d(TAG, "Reading entry=$entryFullPath from $extension archive")
            when (extension) {
                "zip" -> readFromZip(archivePath, entryFullPath, isContentUri, password)
                "7z" -> readFrom7z(archivePath, entryFullPath, isContentUri, password)
                "rar" -> readFromRar(archivePath, entryFullPath, isContentUri, password)
                "tar", "tar.gz", "tar.bz2", "tar.xz" -> readFromTar(archivePath, entryFullPath, isContentUri, extension)
                else -> null
            }
        } catch (e: Throwable) {
            EcosystemLogger.e(TAG, "Failed to read entry: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun readFromZip(
        archivePath: String,
        entryFullPath: String,
        isContentUri: Boolean,
        password: String?
    ): ByteArray? {
        val file = if (isContentUri) copyToTemp(archivePath) else File(archivePath)
        try {
            val zip = Zip4jFile(file)
            if (!password.isNullOrEmpty()) {
                zip.setPassword(password.toCharArray())
            }
            val header = zip.fileHeaders.firstOrNull { h ->
                h.fileName.trimEnd('/') == entryFullPath.trimEnd('/')
            } ?: return null

            if (header.uncompressedSize > MAX_ENTRY_SIZE) {
                EcosystemLogger.d(TAG, "Entry too large: ${header.uncompressedSize} > $MAX_ENTRY_SIZE")
                return null
            }

            return zip.getInputStream(header).use { input ->
                val baos = ByteArrayOutputStream(header.uncompressedSize.toInt().coerceAtLeast(1024))
                input.copyTo(baos)
                baos.toByteArray()
            }
        } finally {
            if (isContentUri) file.delete()
        }
    }

    private fun readFrom7z(
        archivePath: String,
        entryFullPath: String,
        isContentUri: Boolean,
        password: String?
    ): ByteArray? {
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
                    var entry = archive.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.trimEnd('/') == entryFullPath.trimEnd('/')) {
                            if (entry.size > MAX_ENTRY_SIZE) {
                                EcosystemLogger.d(TAG, "Entry too large: ${entry.size} > $MAX_ENTRY_SIZE")
                                return null
                            }
                            val baos = ByteArrayOutputStream(entry.size.toInt().coerceAtLeast(1024))
                            val buf = ByteArray(8192)
                            var len: Int
                            while (archive.read(buf).also { len = it } > 0) {
                                baos.write(buf, 0, len)
                            }
                            return baos.toByteArray()
                        }
                        entry = archive.nextEntry
                    }
                }
            }
            return null
        } finally {
            if (isContentUri) file.delete()
        }
    }

    private fun readFromRar(
        archivePath: String,
        entryFullPath: String,
        isContentUri: Boolean,
        password: String?
    ): ByteArray? {
        val file = if (isContentUri) copyToTemp(archivePath) else File(archivePath)
        try {
            // Try junrar first (RAR4)
            try {
                val archive = if (password != null) Archive(file, password) else Archive(file)
                archive.use { rar ->
                    val header = (rar.fileHeaders ?: emptyList()).firstOrNull { h ->
                        !h.isDirectory &&
                            (h.fileName ?: "").replace('\\', '/').trimEnd('/') == entryFullPath.trimEnd('/')
                    } ?: return null

                    if (header.fullUnpackSize > MAX_ENTRY_SIZE) {
                        EcosystemLogger.d(TAG, "Entry too large: ${header.fullUnpackSize} > $MAX_ENTRY_SIZE")
                        return null
                    }

                    val baos = ByteArrayOutputStream(header.fullUnpackSize.toInt().coerceAtLeast(1024))
                    rar.extractFile(header, baos)
                    return baos.toByteArray()
                }
            } catch (e: Throwable) {
                // RAR5 fallback — use 7-Zip-JBinding
                EcosystemLogger.d(TAG, "junrar failed (${e.javaClass.simpleName}), trying 7-Zip-JBinding")
                return readRarWith7Zip(file, entryFullPath, password)
            }
        } finally {
            if (isContentUri) file.delete()
        }
    }

    private fun readRarWith7Zip(
        file: File,
        entryFullPath: String,
        password: String?
    ): ByteArray? {
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
            for (i in 0 until count) {
                val isDir = archive.getProperty(i, PropID.IS_FOLDER) as? Boolean ?: false
                if (isDir) continue
                val path = (archive.getProperty(i, PropID.PATH) as? String ?: "").replace('\\', '/')
                if (path.trimEnd('/') == entryFullPath.trimEnd('/')) {
                    val size = archive.getProperty(i, PropID.SIZE) as? Long ?: 0L
                    if (size > MAX_ENTRY_SIZE) {
                        EcosystemLogger.d(TAG, "Entry too large: $size > $MAX_ENTRY_SIZE")
                        return null
                    }
                    val baos = ByteArrayOutputStream(size.toInt().coerceAtLeast(1024))
                    archive.extractSlow(i, ISequentialOutStream { data ->
                        baos.write(data)
                        data.size
                    }, password ?: "")
                    return baos.toByteArray()
                }
            }
            return null
        } finally {
            archive.close()
            stream.close()
            raf.close()
        }
    }

    private fun readFromTar(
        archivePath: String,
        entryFullPath: String,
        isContentUri: Boolean,
        type: String
    ): ByteArray? {
        val file = if (isContentUri) copyToTemp(archivePath) else File(archivePath)
        try {
            val rawStream = BufferedInputStream(FileInputStream(file))
            val decompressedStream = when (type) {
                "tar.gz" -> GzipCompressorInputStream(rawStream)
                "tar.bz2" -> BZip2CompressorInputStream(rawStream)
                "tar.xz" -> XZCompressorInputStream(rawStream)
                else -> rawStream
            }
            TarArchiveInputStream(decompressedStream).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.trimEnd('/') == entryFullPath.trimEnd('/')) {
                        if (entry.size > MAX_ENTRY_SIZE) {
                            EcosystemLogger.d(TAG, "Entry too large: ${entry.size} > $MAX_ENTRY_SIZE")
                            return null
                        }
                        val baos = ByteArrayOutputStream(entry.size.toInt().coerceAtLeast(1024))
                        tar.copyTo(baos)
                        return baos.toByteArray()
                    }
                    entry = tar.nextEntry
                }
            }
            return null
        } finally {
            if (isContentUri) file.delete()
        }
    }

    private fun copyToTemp(contentUri: String): File {
        val ext = contentUri.substringAfterLast('.')
        val tempFile = File(context.cacheDir, "archive_read_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(Uri.parse(contentUri))?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open content URI")
        return tempFile
    }
}
