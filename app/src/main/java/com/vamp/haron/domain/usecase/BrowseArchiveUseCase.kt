package com.vamp.haron.domain.usecase

import android.content.Context
import android.net.Uri
import com.github.junrar.Archive
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.R
import com.vamp.haron.domain.model.ArchiveEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vamp.haron.common.util.AesZipHelper
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import com.vamp.haron.common.util.MultiVolumeRarHelper
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import javax.inject.Inject

class BrowseArchiveUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(
        archivePath: String,
        virtualPath: String = "",
        password: String? = null
    ): Result<List<ArchiveEntry>> = withContext(Dispatchers.IO) {
        try {
            val isContentUri = archivePath.startsWith("content://")
            val extension = archiveType(archivePath)
            EcosystemLogger.d(HaronConstants.TAG, "BrowseArchiveUseCase: opening type=$extension, virtualPath=$virtualPath")
            val entries = when (extension) {
                "zip" -> browseZip(archivePath, virtualPath, isContentUri, password)
                "7z" -> browse7z(archivePath, virtualPath, isContentUri, password)
                "rar" -> browseRar(archivePath, virtualPath, isContentUri, password)
                "tar", "tar.gz", "tar.bz2", "tar.xz" -> browseTar(archivePath, virtualPath, isContentUri, extension)
                else -> emptyList()
            }
            EcosystemLogger.d(HaronConstants.TAG, "BrowseArchiveUseCase: found ${entries.size} entries")
            Result.success(entries)
        } catch (e: Throwable) {
            val className = e.javaClass.simpleName
            val causeClass = e.cause?.javaClass?.simpleName ?: ""
            val msg = e.message ?: ""
            val causeMsg = e.cause?.message ?: ""
            when {
                // Encrypted archive
                className.contains("Encrypt", ignoreCase = true) ||
                    causeClass.contains("Encrypt", ignoreCase = true) ||
                    msg.contains("encrypted", ignoreCase = true) ||
                    msg.contains("password", ignoreCase = true) ||
                    causeMsg.contains("encrypted", ignoreCase = true) ||
                    causeMsg.contains("password", ignoreCase = true) ->
                    Result.failure(IllegalStateException("encrypted"))
                else -> {
                    EcosystemLogger.e(HaronConstants.TAG, "BrowseArchiveUseCase: error — ${e.message}")
                    Result.failure(e)
                }
            }
        }
    }

    private fun browseZip(archivePath: String, virtualPath: String, isContentUri: Boolean, password: String?): List<ArchiveEntry> {
        val file = if (isContentUri) copyToTemp(archivePath) else File(archivePath)
        try {
            val isEncrypted = AesZipHelper.isZipEncrypted(file)
            if (isEncrypted && password == null) {
                throw IllegalStateException("encrypted")
            }
            val prefix = if (virtualPath.isEmpty()) "" else "$virtualPath/"
            if (isEncrypted) {
                val entries = AesZipHelper.listEncryptedZip(file, password?.toCharArray())
                return filterDirectChildren(entries.map { e ->
                    ArchiveEntry(
                        name = e.name.trimEnd('/').substringAfterLast('/'),
                        fullPath = e.name.trimEnd('/'),
                        size = e.size.coerceAtLeast(0),
                        isDirectory = e.isDirectory,
                        compressedSize = e.compressedSize.coerceAtLeast(0),
                        lastModified = e.lastModified
                    )
                }, prefix)
            } else {
                val zipFile = java.util.zip.ZipFile(file)
                return zipFile.use { zf ->
                    filterDirectChildren(zf.entries().asSequence().map { e ->
                        ArchiveEntry(
                            name = e.name.trimEnd('/').substringAfterLast('/'),
                            fullPath = e.name.trimEnd('/'),
                            size = e.size.coerceAtLeast(0),
                            isDirectory = e.isDirectory,
                            compressedSize = e.compressedSize.coerceAtLeast(0),
                            lastModified = e.time
                        )
                    }.toList(), prefix)
                }
            }
        } finally {
            if (isContentUri) file.delete()
        }
    }

    private fun browse7z(archivePath: String, virtualPath: String, isContentUri: Boolean, password: String?): List<ArchiveEntry> {
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
                val builder = SevenZFile.builder().setSeekableByteChannel(ch)
                if (password != null) builder.setPassword(password.toCharArray())
                val sevenZ = builder.get()
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

    private fun browseRar(archivePath: String, virtualPath: String, isContentUri: Boolean, password: String?): List<ArchiveEntry> {
        val file = if (isContentUri) copyToTemp(archivePath) else File(archivePath)
        try {
            // Multi-volume RAR — always use 7-Zip-JBinding (junrar doesn't support split)
            if (!isContentUri && MultiVolumeRarHelper.isMultiVolumeRar(file)) {
                val firstVolume = MultiVolumeRarHelper.findFirstVolume(file)
                val missing = MultiVolumeRarHelper.findMissingVolumes(firstVolume)
                if (missing.isNotEmpty()) {
                    throw IllegalStateException("Missing RAR volumes: ${missing.joinToString()}")
                }
                EcosystemLogger.d(HaronConstants.TAG, "BrowseArchiveUseCase: multi-volume RAR, ${MultiVolumeRarHelper.countVolumes(firstVolume)} volumes, first=${firstVolume.name}")
                return browseMultiVolumeRar(firstVolume, virtualPath, password)
            }
            // Try junrar first (RAR4)
            return try {
                val archive = if (password != null) Archive(file, password) else Archive(file)
                archive.use { rar ->
                    if (rar.isEncrypted && password == null) {
                        throw IllegalStateException("encrypted")
                    }
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
            } catch (e: Throwable) {
                // RAR5 or other junrar failure — fallback to 7-Zip-JBinding
                if (e is IllegalStateException && e.message == "encrypted") throw e
                browseRarWith7Zip(file, virtualPath, password)
            }
        } finally {
            if (isContentUri) file.delete()
        }
    }

    /** Browse multi-volume RAR using 7-Zip-JBinding with IArchiveOpenVolumeCallback */
    private fun browseMultiVolumeRar(firstVolume: File, virtualPath: String, password: String?): List<ArchiveEntry> {
        SevenZip.initSevenZipFromPlatformJAR()
        val volumeCallback = MultiVolumeRarHelper.VolumeCallback(firstVolume)
        // Open first volume as stream, pass volume callback for subsequent volumes
        val raf = RandomAccessFile(firstVolume, "r")
        val firstStream = RandomAccessFileInStream(raf)
        val archive: IInArchive = SevenZip.openInArchive(null, firstStream, volumeCallback)
        try {
            // If password required, re-open with password
            // Note: 7z handles password via IArchiveOpenCallback or per-item extraction
            val count = archive.numberOfItems
            if (password == null && count > 0) {
                val encrypted = archive.getProperty(0, PropID.ENCRYPTED) as? Boolean ?: false
                if (encrypted) throw IllegalStateException("encrypted")
            }
            val prefix = if (virtualPath.isEmpty()) "" else "$virtualPath/"
            val all = mutableListOf<ArchiveEntry>()
            for (i in 0 until count) {
                val path = (archive.getProperty(i, PropID.PATH) as? String ?: "").replace('\\', '/')
                val isDir = archive.getProperty(i, PropID.IS_FOLDER) as? Boolean ?: false
                val size = (archive.getProperty(i, PropID.SIZE) as? Long) ?: 0L
                val packedSize = (archive.getProperty(i, PropID.PACKED_SIZE) as? Long) ?: 0L
                val lastMod = (archive.getProperty(i, PropID.LAST_MODIFICATION_TIME) as? java.util.Date)?.time ?: 0L
                all.add(ArchiveEntry(
                    name = path.trimEnd('/').substringAfterLast('/'),
                    fullPath = path.trimEnd('/'),
                    size = size.coerceAtLeast(0),
                    isDirectory = isDir,
                    compressedSize = packedSize.coerceAtLeast(0),
                    lastModified = lastMod
                ))
            }
            EcosystemLogger.d(HaronConstants.TAG, "BrowseArchiveUseCase: multi-volume RAR browsed OK, ${all.size} entries")
            return filterDirectChildren(all, prefix)
        } finally {
            archive.close()
            firstStream.close()
            raf.close()
            volumeCallback.close()
        }
    }

    /** Browse RAR (including RAR5) using 7-Zip-JBinding native engine */
    private fun browseRarWith7Zip(file: File, virtualPath: String, password: String?): List<ArchiveEntry> {
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
            // Check if encrypted and no password
            if (password == null && count > 0) {
                val encrypted = archive.getProperty(0, PropID.ENCRYPTED) as? Boolean ?: false
                if (encrypted) throw IllegalStateException("encrypted")
            }
            val prefix = if (virtualPath.isEmpty()) "" else "$virtualPath/"
            val all = mutableListOf<ArchiveEntry>()
            for (i in 0 until count) {
                val path = (archive.getProperty(i, PropID.PATH) as? String ?: "").replace('\\', '/')
                val isDir = archive.getProperty(i, PropID.IS_FOLDER) as? Boolean ?: false
                val size = (archive.getProperty(i, PropID.SIZE) as? Long) ?: 0L
                val packedSize = (archive.getProperty(i, PropID.PACKED_SIZE) as? Long) ?: 0L
                val lastMod = (archive.getProperty(i, PropID.LAST_MODIFICATION_TIME) as? java.util.Date)?.time ?: 0L
                all.add(ArchiveEntry(
                    name = path.trimEnd('/').substringAfterLast('/'),
                    fullPath = path.trimEnd('/'),
                    size = size.coerceAtLeast(0),
                    isDirectory = isDir,
                    compressedSize = packedSize.coerceAtLeast(0),
                    lastModified = lastMod
                ))
            }
            return filterDirectChildren(all, prefix)
        } finally {
            archive.close()
            stream.close()
            raf.close()
        }
    }

    private fun browseTar(archivePath: String, virtualPath: String, isContentUri: Boolean, type: String): List<ArchiveEntry> {
        val file = if (isContentUri) copyToTemp(archivePath) else File(archivePath)
        try {
            val prefix = if (virtualPath.isEmpty()) "" else "$virtualPath/"
            val rawStream = BufferedInputStream(FileInputStream(file))
            val decompressedStream = when (type) {
                "tar.gz" -> GzipCompressorInputStream(rawStream)
                "tar.bz2" -> BZip2CompressorInputStream(rawStream)
                "tar.xz" -> XZCompressorInputStream(rawStream)
                else -> rawStream // plain tar
            }
            TarArchiveInputStream(decompressedStream).use { tar ->
                val all = mutableListOf<ArchiveEntry>()
                var entry = tar.nextEntry
                while (entry != null) {
                    all.add(ArchiveEntry(
                        name = entry.name.trimEnd('/').substringAfterLast('/'),
                        fullPath = entry.name.trimEnd('/'),
                        size = entry.size.coerceAtLeast(0),
                        isDirectory = entry.isDirectory,
                        compressedSize = 0,
                        lastModified = entry.lastModifiedDate?.time ?: 0
                    ))
                    entry = tar.nextEntry
                }
                return filterDirectChildren(all, prefix)
            }
        } finally {
            if (isContentUri) file.delete()
        }
    }

    /**
     * From a flat list of all archive entries, filter to show only direct children of [prefix].
     * Also synthesizes virtual directories for entries that have intermediate paths.
     */
    internal fun filterDirectChildren(allEntries: List<ArchiveEntry>, prefix: String): List<ArchiveEntry> =
        Companion.filterDirectChildren(allEntries, prefix)

    companion object {
        fun archiveType(path: String): String {
            val lower = path.lowercase()
            return when {
                lower.endsWith(".tar.gz") || lower.endsWith(".tgz") || lower.endsWith(".tgz.gtar") -> "tar.gz"
                lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") -> "tar.bz2"
                lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> "tar.xz"
                lower.endsWith(".tar") || lower.endsWith(".gtar") -> "tar"
                // Multi-volume RAR: .part2.rar, .part3.rar, .r00, .r01, etc.
                Regex("""\.part\d+\.rar$""").containsMatchIn(lower) -> "rar"
                Regex("""\.r\d{2,}$""").containsMatchIn(lower) -> "rar"
                else -> lower.substringAfterLast('.')
            }
        }
        internal fun filterDirectChildren(allEntries: List<ArchiveEntry>, prefix: String): List<ArchiveEntry> {
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

        // Count direct children for each directory
        for ((name, entry) in directChildren.toMap()) {
            if (entry.isDirectory) {
                val dirPrefix = "${entry.fullPath}/"
                val childNames = mutableSetOf<String>()
                for (e in allEntries) {
                    if (!e.fullPath.startsWith(dirPrefix)) continue
                    val relative = e.fullPath.removePrefix(dirPrefix)
                    if (relative.isEmpty()) continue
                    childNames.add(relative.split('/')[0])
                }
                directChildren[name] = entry.copy(childCount = childNames.size)
            }
        }

        return directChildren.values
            .sortedWith(compareByDescending<ArchiveEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
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
