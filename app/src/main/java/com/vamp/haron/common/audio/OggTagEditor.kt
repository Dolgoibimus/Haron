package com.vamp.haron.common.audio

import com.vamp.core.logger.EcosystemLogger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * OGG Vorbis / OGG Opus tag reader/writer.
 * Handles Vorbis Comment packets inside OGG container.
 * Spec: https://www.xiph.org/ogg/doc/framing.html
 */
object OggTagEditor {
    private const val TAG = "Haron/OggTag"

    private val VORBIS_COMMENT_MAGIC = byteArrayOf(0x03, 0x76, 0x6F, 0x72, 0x62, 0x69, 0x73) // 0x03 + "vorbis"
    private val OPUS_TAGS_MAGIC = "OpusTags".toByteArray(Charsets.US_ASCII)

    private enum class OggType { VORBIS, OPUS }

    fun readTags(file: File): AudioTagData? {
        if (!file.exists() || file.length() < 28) return null
        try {
            RandomAccessFile(file, "r").use { raf ->
                // Read first page to detect type
                val page0 = readPage(raf) ?: return null
                val type = detectType(page0.data) ?: return null

                // Read second page (comment header)
                val page1 = readPage(raf) ?: return null
                // Comment may span multiple pages (continuation)
                var commentData = page1.data
                while (true) {
                    val pos = raf.filePointer
                    val nextPage = readPage(raf) ?: break
                    if (nextPage.headerType and 0x01 != 0) {
                        // continuation page
                        commentData = commentData + nextPage.data
                    } else {
                        raf.seek(pos) // not a continuation, rewind
                        break
                    }
                }

                val result = AudioTagData()
                val magicLen = if (type == OggType.VORBIS) 7 else 8
                if (commentData.size <= magicLen) return null
                val vcData = commentData.copyOfRange(magicLen, commentData.size)
                parseVorbisComment(vcData, result)
                return result
            }
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "readTags: ${e.message}")
            return null
        }
    }

    fun writeTags(file: File, tags: AudioTagData): Boolean {
        return safeWrite(file) { tempFile -> writeTagsInternal(tempFile, tags) }
    }

    fun writeCoverArt(file: File, imageBytes: ByteArray, mimeType: String = "image/jpeg"): Boolean {
        return safeWrite(file) { tempFile ->
            val existing = readTags(tempFile) ?: AudioTagData()
            existing.coverArt = imageBytes
            existing.coverMimeType = mimeType
            writeTagsInternal(tempFile, existing)
        }
    }

    private fun writeTagsInternal(file: File, tags: AudioTagData): Boolean {
        try {
            RandomAccessFile(file, "r").use { raf ->
                // Page 0: identification header — keep as is
                val page0 = readPage(raf) ?: return false
                val type = detectType(page0.data) ?: return false
                val serialNumber = page0.serialNumber

                // Read comment pages (page 1+, may have continuations)
                val commentPageStart = raf.filePointer
                var commentPage = readPage(raf) ?: return false
                while (true) {
                    val pos = raf.filePointer
                    val nextPage = readPage(raf) ?: break
                    if (nextPage.headerType and 0x01 != 0) {
                        // continuation, skip
                    } else {
                        raf.seek(pos)
                        break
                    }
                }

                // Read remaining audio pages
                val audioStart = raf.filePointer
                val audioSize = raf.length() - audioStart
                val audioBytes = ByteArray(audioSize.toInt())
                raf.readFully(audioBytes)

                // Build new comment packet
                val commentPacket = buildCommentPacket(type, tags)

                // Split comment packet into OGG pages
                val commentPages = packIntoPages(commentPacket, serialNumber, 1)

                // Rebuild audio pages with corrected sequence numbers
                val seqOffset = commentPages.size + 1 // page0(seq=0) + N comment pages
                val fixedAudio = fixSequenceNumbers(audioBytes, seqOffset)

                // Write file
                RandomAccessFile(file, "rw").use { out ->
                    out.setLength(0)
                    out.write(encodePage(page0))
                    for (p in commentPages) {
                        out.write(encodePage(p))
                    }
                    out.write(fixedAudio)
                }
            }
            return true
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "writeTags: ${e.message}")
            return false
        }
    }

    // ─── OGG Page structure ───

    private data class OggPage(
        val headerType: Int,
        val granulePosition: Long,
        val serialNumber: Int,
        val sequenceNumber: Int,
        val segmentTable: ByteArray,
        val data: ByteArray
    )

    private fun readPage(raf: RandomAccessFile): OggPage? {
        val header = ByteArray(27)
        try { raf.readFully(header) } catch (_: Exception) { return null }
        if (header[0].toInt().toChar() != 'O' || header[1].toInt().toChar() != 'g' ||
            header[2].toInt().toChar() != 'g' || header[3].toInt().toChar() != 'S') return null

        val headerType = header[5].toInt() and 0xFF
        val granule = ByteBuffer.wrap(header, 6, 8).order(ByteOrder.LITTLE_ENDIAN).long
        val serial = ByteBuffer.wrap(header, 14, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val sequence = ByteBuffer.wrap(header, 18, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val numSegments = header[26].toInt() and 0xFF

        val segTable = ByteArray(numSegments)
        raf.readFully(segTable)

        val dataSize = segTable.sumOf { it.toInt() and 0xFF }
        val data = ByteArray(dataSize)
        raf.readFully(data)

        return OggPage(headerType, granule, serial, sequence, segTable, data)
    }

    private fun encodePage(page: OggPage): ByteArray {
        val headerSize = 27 + page.segmentTable.size
        val out = ByteArray(headerSize + page.data.size)

        // Capture pattern
        out[0] = 'O'.code.toByte(); out[1] = 'g'.code.toByte()
        out[2] = 'g'.code.toByte(); out[3] = 'S'.code.toByte()
        out[4] = 0 // version
        out[5] = page.headerType.toByte()

        ByteBuffer.wrap(out, 6, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(page.granulePosition)
        ByteBuffer.wrap(out, 14, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(page.serialNumber)
        ByteBuffer.wrap(out, 18, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(page.sequenceNumber)

        // CRC placeholder = 0
        out[22] = 0; out[23] = 0; out[24] = 0; out[25] = 0

        out[26] = page.segmentTable.size.toByte()
        System.arraycopy(page.segmentTable, 0, out, 27, page.segmentTable.size)
        System.arraycopy(page.data, 0, out, headerSize, page.data.size)

        // Calculate CRC
        val crc = oggCrc32(out)
        ByteBuffer.wrap(out, 22, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(crc)

        return out
    }

    private fun packIntoPages(packet: ByteArray, serialNumber: Int, startSeq: Int): List<OggPage> {
        val pages = mutableListOf<OggPage>()
        var offset = 0
        var seq = startSeq

        while (offset < packet.size) {
            val remaining = packet.size - offset
            // Max data per page: 255 * 255 = 65025
            val maxData = 255 * 255
            val pageDataSize = remaining.coerceAtMost(maxData)
            val pageData = packet.copyOfRange(offset, offset + pageDataSize)

            // Build segment table
            val segments = mutableListOf<Byte>()
            var left = pageDataSize
            while (left >= 255) {
                segments.add(255.toByte())
                left -= 255
            }
            // Terminal lacing value (< 255 means packet end on this page)
            if (offset + pageDataSize >= packet.size) {
                segments.add(left.toByte()) // packet ends
            } else {
                if (left > 0) segments.add(left.toByte())
                // No terminal < 255, packet continues
            }

            val headerType = if (offset > 0) 0x01 else 0x00 // continuation flag

            pages.add(OggPage(
                headerType = headerType,
                granulePosition = 0, // header pages have granule = 0
                serialNumber = serialNumber,
                sequenceNumber = seq++,
                segmentTable = segments.toByteArray(),
                data = pageData
            ))

            offset += pageDataSize
        }
        return pages
    }

    private fun fixSequenceNumbers(audioBytes: ByteArray, firstAudioSeq: Int): ByteArray {
        val result = audioBytes.copyOf()
        var pos = 0
        var seq = firstAudioSeq

        while (pos + 27 <= result.size) {
            if (result[pos].toInt().toChar() != 'O' || result[pos + 1].toInt().toChar() != 'g' ||
                result[pos + 2].toInt().toChar() != 'g' || result[pos + 3].toInt().toChar() != 'S') {
                pos++
                continue
            }

            val numSegments = result[pos + 26].toInt() and 0xFF
            if (pos + 27 + numSegments > result.size) break

            // Update sequence number
            ByteBuffer.wrap(result, pos + 18, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(seq++)

            // Recalculate CRC
            result[pos + 22] = 0; result[pos + 23] = 0; result[pos + 24] = 0; result[pos + 25] = 0
            val dataSize = (0 until numSegments).sumOf { result[pos + 27 + it].toInt() and 0xFF }
            val pageSize = 27 + numSegments + dataSize
            if (pos + pageSize > result.size) break

            val crc = oggCrc32(result, pos, pageSize)
            ByteBuffer.wrap(result, pos + 22, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(crc)

            pos += pageSize
        }
        return result
    }

    // ─── Vorbis Comment ───

    private fun parseVorbisComment(data: ByteArray, result: AudioTagData) {
        if (data.size < 8) return
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val vendorLen = buf.int
        if (vendorLen < 0 || vendorLen > buf.remaining()) return
        buf.position(buf.position() + vendorLen)
        if (buf.remaining() < 4) return
        val count = buf.int
        for (i in 0 until count) {
            if (buf.remaining() < 4) break
            val len = buf.int
            if (len < 0 || len > buf.remaining()) break
            val bytes = ByteArray(len)
            buf.get(bytes)
            val comment = String(bytes, Charsets.UTF_8)
            val eq = comment.indexOf('=')
            if (eq <= 0) continue
            val key = comment.substring(0, eq).uppercase()
            val value = comment.substring(eq + 1)
            when (key) {
                "TITLE" -> result.title = value
                "ARTIST" -> result.artist = value
                "ALBUM" -> result.album = value
                "DATE" -> result.year = value
                "GENRE" -> result.genre = value
            }
        }
    }

    private fun buildCommentPacket(type: OggType, tags: AudioTagData): ByteArray {
        val out = ByteArrayOutputStream()

        // Magic
        if (type == OggType.VORBIS) out.write(VORBIS_COMMENT_MAGIC)
        else out.write(OPUS_TAGS_MAGIC)

        // Vorbis Comment body
        val vendor = "Haron".toByteArray(Charsets.UTF_8)
        writeLE32(out, vendor.size)
        out.write(vendor)

        val comments = mutableListOf<ByteArray>()
        if (tags.title.isNotEmpty()) comments.add("TITLE=${tags.title}".toByteArray(Charsets.UTF_8))
        if (tags.artist.isNotEmpty()) comments.add("ARTIST=${tags.artist}".toByteArray(Charsets.UTF_8))
        if (tags.album.isNotEmpty()) comments.add("ALBUM=${tags.album}".toByteArray(Charsets.UTF_8))
        if (tags.year.isNotEmpty()) comments.add("DATE=${tags.year}".toByteArray(Charsets.UTF_8))
        if (tags.genre.isNotEmpty()) comments.add("GENRE=${tags.genre}".toByteArray(Charsets.UTF_8))

        writeLE32(out, comments.size)
        for (c in comments) {
            writeLE32(out, c.size)
            out.write(c)
        }

        // Framing bit for Vorbis only
        if (type == OggType.VORBIS) out.write(1)

        return out.toByteArray()
    }

    private fun detectType(page0Data: ByteArray): OggType? {
        if (page0Data.size >= 7 && page0Data[0] == 0x01.toByte() &&
            String(page0Data, 1, 6, Charsets.US_ASCII) == "vorbis") return OggType.VORBIS
        if (page0Data.size >= 8 &&
            String(page0Data, 0, 8, Charsets.US_ASCII) == "OpusHead") return OggType.OPUS
        return null
    }

    // ─── OGG CRC-32 (polynomial 0x04C11DB7, MSB-first, no inversion) ───

    private val crcTable = IntArray(256).also { table ->
        for (i in 0 until 256) {
            var crc = i shl 24
            for (j in 0 until 8) {
                crc = if (crc and 0x80000000.toInt() != 0) (crc shl 1) xor 0x04C11DB7
                else crc shl 1
            }
            table[i] = crc
        }
    }

    private fun oggCrc32(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = 0
        for (i in offset until offset + length) {
            crc = (crc shl 8) xor crcTable[((crc ushr 24) xor (data[i].toInt() and 0xFF)) and 0xFF]
        }
        return crc
    }

    // ─── Helpers ───

    private fun writeLE32(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 24) and 0xFF)
    }

    private fun safeWrite(file: File, block: (File) -> Boolean): Boolean {
        val tempFile = File(file.parent, ".${file.name}.tmp")
        try {
            file.copyTo(tempFile, overwrite = true)
            if (!block(tempFile)) { tempFile.delete(); return false }
            val backup = File(file.parent, ".${file.name}.bak")
            file.renameTo(backup)
            return if (tempFile.renameTo(file)) {
                backup.delete(); true
            } else {
                backup.renameTo(file); tempFile.delete(); false
            }
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "safeWrite: ${e.message}")
            tempFile.delete()
            return false
        }
    }
}
