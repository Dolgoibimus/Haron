package com.vamp.haron.domain.usecase

import android.content.Context
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.db.dao.BookDao
import com.vamp.haron.data.db.entity.BookEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import javax.inject.Inject

data class ScanProgress(
    val scanned: Int = 0,
    val total: Int = 0,
    val currentFile: String = "",
    val isComplete: Boolean = false
)

class ScanBooksUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao
) {
    companion object {
        val BOOK_EXTENSIONS = setOf("epub", "fb2", "mobi", "azw3", "pdf", "djvu", "doc", "docx", "odt", "rtf", "xlsx", "xls", "csv")
        private const val TAG = HaronConstants.TAG
    }

    fun scan(folders: List<String>, excludedFolders: Set<String> = emptySet()): Flow<ScanProgress> = flow {
        val allFiles = mutableListOf<File>()
        for (folder in folders) {
            val dir = File(folder)
            if (!dir.exists() || !dir.isDirectory) continue
            dir.walkTopDown()
                .onEnter { d -> d.absolutePath !in excludedFolders }
                .filter { it.isFile }
                .filter { f ->
                    val ext = f.extension.lowercase()
                    ext in BOOK_EXTENSIONS || f.name.lowercase().endsWith(".fb2.zip")
                }
                .forEach { allFiles.add(it) }
        }

        emit(ScanProgress(0, allFiles.size))
        EcosystemLogger.d(TAG, "ScanBooks: found ${allFiles.size} book files in ${folders.size} folders")

        for ((index, file) in allFiles.withIndex()) {
            try {
                val scanFolder = file.parentFile?.absolutePath ?: ""
                val existing = bookDao.getByPath(file.absolutePath)
                if (existing != null) {
                    // Update scanFolder if it changed
                    if (existing.scanFolder != scanFolder) {
                        bookDao.upsert(existing.copy(scanFolder = scanFolder))
                    }
                    // Skip if file unchanged AND metadata is populated
                    if (existing.fileSize == file.length() && existing.author.isNotBlank()) continue
                }

                val entity = extractMetadata(file, scanFolder)
                if (entity != null) {
                    // Preserve progress/lastRead from existing record
                    val toSave = if (existing != null) {
                        entity.copy(progress = existing.progress, lastRead = existing.lastRead, addedAt = existing.addedAt)
                    } else entity
                    bookDao.upsert(toSave)
                }
            } catch (e: Exception) {
                EcosystemLogger.e(TAG, "ScanBooks: error processing ${file.name}: ${e.message}")
            }
            if (index % 10 == 0 || index == allFiles.size - 1) {
                emit(ScanProgress(index + 1, allFiles.size, file.name))
            }
        }

        // Remove books whose files no longer exist or are corrupted
        val allBooks = bookDao.getAll()
        for (book in allBooks) {
            val f = File(book.filePath)
            if (!f.exists() || f.length() == 0L || book.fileSize == 0L) {
                bookDao.delete(book.filePath)
            }
        }

        emit(ScanProgress(allFiles.size, allFiles.size, isComplete = true))
        EcosystemLogger.d(TAG, "ScanBooks: scan complete")
    }.flowOn(Dispatchers.IO)

    private fun extractMetadata(file: File, scanFolder: String): BookEntity? {
        val ext = file.extension.lowercase()
        val isFb2Zip = file.name.lowercase().endsWith(".fb2.zip")

        return when {
            ext == "fb2" || isFb2Zip -> extractFb2Metadata(file, scanFolder, isFb2Zip)
            ext == "epub" -> extractEpubMetadata(file, scanFolder)
            ext == "pdf" -> BookEntity(
                filePath = file.absolutePath, title = file.nameWithoutExtension,
                format = "pdf", fileSize = file.length(), scanFolder = scanFolder
            )
            ext == "djvu" -> BookEntity(
                filePath = file.absolutePath, title = file.nameWithoutExtension,
                format = "djvu", fileSize = file.length(), scanFolder = scanFolder
            )
            ext in setOf("mobi", "azw3") -> BookEntity(
                filePath = file.absolutePath, title = file.nameWithoutExtension,
                format = ext, fileSize = file.length(), scanFolder = scanFolder
            )
            ext in setOf("doc", "docx", "odt", "rtf", "xlsx", "xls", "csv") -> BookEntity(
                filePath = file.absolutePath, title = file.nameWithoutExtension,
                format = ext, fileSize = file.length(), scanFolder = scanFolder
            )
            else -> null
        }
    }

    private fun extractFb2Metadata(file: File, scanFolder: String, isZip: Boolean): BookEntity? {
        // Read only first 50KB for metadata (description block is at the top)
        val maxBytes = 50_000
        val xml = try {
            if (isZip) {
                // Read ZIP bytes into memory first (workaround for FUSE reporting 0 size)
                val zipBytes = file.inputStream().use { it.readBytes() }
                if (zipBytes.isEmpty()) throw java.util.zip.ZipException("zip file is empty")
                val zipStream = java.util.zip.ZipInputStream(zipBytes.inputStream())
                var fb2Xml: String? = null
                var entry = zipStream.nextEntry
                while (entry != null) {
                    if (entry.name.lowercase().endsWith(".fb2")) {
                        val bytes = zipStream.readBytes().take(maxBytes).toByteArray()
                        fb2Xml = String(bytes, detectCharset(bytes))
                        break
                    }
                    entry = zipStream.nextEntry
                }
                zipStream.close()
                fb2Xml ?: throw IllegalStateException("No .fb2 in archive")
            } else {
                // For plain FB2, read first maxBytes
                val bytes = file.inputStream().use { stream ->
                    val buf = ByteArray(maxBytes)
                    val read = stream.read(buf)
                    if (read > 0) buf.copyOf(read) else ByteArray(0)
                }
                if (bytes.isEmpty()) throw IllegalStateException("Empty file")
                String(bytes, detectCharset(bytes))
            }
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "ScanBooks: FB2 read error ${file.name} (${file.length()} bytes): ${e.javaClass.simpleName}: ${e.message}")
            // Skip corrupted/empty files entirely
            return null
        }

        // Title
        val title = extractTag(xml, "book-title") ?: file.nameWithoutExtension

        // Author — search in title-info first, then fallback to any <author>
        val titleInfoMatch = Regex("<title-info>(.*?)</title-info>", RegexOption.DOT_MATCHES_ALL).find(xml)
        val authorSearchArea = titleInfoMatch?.groupValues?.get(1) ?: xml
        val authorMatch = Regex("<author>(.*?)</author>", RegexOption.DOT_MATCHES_ALL).find(authorSearchArea)
        var author = if (authorMatch != null) {
            val a = authorMatch.groupValues[1]
            val last = extractTag(a, "last-name") ?: ""
            val first = extractTag(a, "first-name") ?: ""
            val middle = extractTag(a, "middle-name") ?: ""
            val nickname = extractTag(a, "nickname") ?: ""
            val fullName = listOf(last, first, middle).filter { it.isNotBlank() }.joinToString(" ")
            fullName.ifBlank { nickname }
        } else ""
        if (author.isBlank()) {
            EcosystemLogger.d(TAG, "ScanBooks: no author found in '${file.name}', xml starts: ${xml.take(300)}")
        }

        // Language
        val lang = extractTag(xml, "lang")

        // Series
        // Series — attributes can be in any order: name then number, or number then name
        val seqMatch = Regex("""<sequence\s+name="([^"]*)"(?:\s+number="(\d+)")?""").find(xml)
            ?: Regex("""<sequence\s+number="(\d+)"\s+name="([^"]*)"""").find(xml)
        val series: String?
        val seriesNum: Int?
        if (seqMatch != null) {
            val g1 = seqMatch.groupValues[1]
            val g2 = seqMatch.groupValues[2]
            // Determine which group is name vs number
            if (g1.toIntOrNull() != null && g2.toIntOrNull() == null) {
                // number first, name second
                seriesNum = g1.toIntOrNull()
                series = g2.takeIf { it.isNotBlank() }
            } else {
                // name first, number second (or no number)
                series = g1.takeIf { it.isNotBlank() }
                seriesNum = g2.toIntOrNull()
            }
        } else {
            series = null
            seriesNum = null
        }

        // Annotation
        val annMatch = Regex("<annotation>(.*?)</annotation>", RegexOption.DOT_MATCHES_ALL).find(xml)
        val annotation = annMatch?.groupValues?.get(1)
            ?.replace(Regex("<[^>]+>"), "")
            ?.replace(Regex("&\\w+;"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(500)

        if (title.isNotBlank() || author.isNotBlank()) {
            EcosystemLogger.d(TAG, "ScanBooks: FB2 '${file.name}' → title='$title' author='$author' series='$series' #$seriesNum")
        }

        return BookEntity(
            filePath = file.absolutePath,
            title = title,
            author = author,
            format = "fb2",
            fileSize = file.length(),
            coverPath = null, // covers loaded lazily via ThumbnailCache
            annotation = annotation,
            language = lang,
            series = series,
            seriesNumber = seriesNum,
            scanFolder = scanFolder
        )
    }

    private fun extractEpubMetadata(file: File, scanFolder: String): BookEntity {
        // Basic EPUB metadata extraction via ZIP + OPF
        return try {
            ZipFile(file).use { zip ->
                // Find OPF file
                val containerEntry = zip.getEntry("META-INF/container.xml")
                val opfPath = if (containerEntry != null) {
                    val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
                    Regex("""full-path="([^"]+)"""").find(containerXml)?.groupValues?.get(1) ?: "content.opf"
                } else "content.opf"

                val opfEntry = zip.getEntry(opfPath)
                if (opfEntry == null) {
                    return BookEntity(
                        filePath = file.absolutePath, title = file.nameWithoutExtension,
                        format = "epub", fileSize = file.length(), scanFolder = scanFolder
                    )
                }

                val opfXml = zip.getInputStream(opfEntry).bufferedReader().readText()
                val opfDir = opfPath.substringBeforeLast('/', "")

                val title = extractTag(opfXml, "dc:title")
                    ?: extractTag(opfXml, "title")
                    ?: file.nameWithoutExtension
                val author = extractTag(opfXml, "dc:creator")
                    ?: extractTag(opfXml, "creator") ?: ""
                val lang = extractTag(opfXml, "dc:language")
                    ?: extractTag(opfXml, "language")
                val description = extractTag(opfXml, "dc:description")
                    ?.replace(Regex("<[^>]+>"), "")
                    ?.take(500)

                BookEntity(
                    filePath = file.absolutePath,
                    title = title,
                    author = author,
                    format = "epub",
                    fileSize = file.length(),
                    coverPath = null, // covers loaded lazily via ThumbnailCache
                    annotation = description,
                    language = lang,
                    scanFolder = scanFolder
                )
            }
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "ScanBooks: EPUB error ${file.name}: ${e.message}")
            BookEntity(
                filePath = file.absolutePath, title = file.nameWithoutExtension,
                format = "epub", fileSize = file.length(), scanFolder = scanFolder
            )
        }
    }

    private fun extractTag(xml: String, tag: String): String? {
        val match = Regex("<$tag[^>]*>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).find(xml)
        return match?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun detectCharset(bytes: ByteArray): java.nio.charset.Charset {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Charsets.UTF_8
        }
        val header = String(bytes, 0, minOf(200, bytes.size), Charsets.ISO_8859_1)
        val enc = Regex("""encoding=["\']([^"\']+)["\']""").find(header)?.groupValues?.get(1)?.trim()?.lowercase()
        if (enc != null) {
            return try { java.nio.charset.Charset.forName(enc) } catch (_: Exception) { Charsets.UTF_8 }
        }
        return Charsets.UTF_8
    }
}
