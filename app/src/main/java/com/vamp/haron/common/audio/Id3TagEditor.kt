package com.vamp.haron.common.audio

import com.vamp.core.logger.EcosystemLogger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/**
 * Lightweight ID3v2.3 tag reader/writer for MP3 files.
 * Replaces jaudiotagger (~1 МБ) for the subset of operations Haron uses:
 * read/write text frames (TIT2, TPE1, TALB, TYER, TCON) and APIC (cover art).
 *
 * ID3v2.3 spec: https://id3.org/id3v2.3.0
 */
object Id3TagEditor {
    private const val TAG = "Haron/Id3Tag"

    // Frame IDs for ID3v2.3
    private const val FRAME_TITLE = "TIT2"
    private const val FRAME_ARTIST = "TPE1"
    private const val FRAME_ALBUM = "TALB"
    private const val FRAME_YEAR = "TYER"
    private const val FRAME_YEAR_V24 = "TDRC"
    private const val FRAME_GENRE = "TCON"
    private const val FRAME_PICTURE = "APIC"

    private val UTF8 = Charsets.UTF_8
    private val LATIN1 = Charsets.ISO_8859_1

    /**
     * Read all text tags from an MP3 file.
     * Returns null if file is not a valid ID3v2 MP3.
     */
    fun readTags(file: File): AudioTagData? {
        if (!file.exists() || file.length() < 10) return null
        try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(10)
                raf.readFully(header)
                if (header[0].toInt().toChar() != 'I' || header[1].toInt().toChar() != 'D' || header[2].toInt().toChar() != '3') {
                    return null
                }
                val version = header[3].toInt() // 3 or 4
                val tagSize = decodeSyncsafe(header, 6)

                val tagData = ByteArray(tagSize)
                raf.readFully(tagData)

                val result = AudioTagData()
                var pos = 0
                while (pos + 10 <= tagSize) {
                    val frameId = String(tagData, pos, 4, LATIN1)
                    if (frameId[0] == '\u0000') break // padding
                    val frameSize = if (version >= 4) {
                        decodeSyncsafe(tagData, pos + 4)
                    } else {
                        decodeInt(tagData, pos + 4)
                    }
                    // skip frame flags (2 bytes)
                    val dataStart = pos + 10
                    val dataEnd = dataStart + frameSize

                    if (dataEnd > tagSize) break

                    when (frameId) {
                        FRAME_TITLE -> result.title = readTextFrame(tagData, dataStart, frameSize)
                        FRAME_ARTIST -> result.artist = readTextFrame(tagData, dataStart, frameSize)
                        FRAME_ALBUM -> result.album = readTextFrame(tagData, dataStart, frameSize)
                        FRAME_YEAR, FRAME_YEAR_V24 -> result.year = readTextFrame(tagData, dataStart, frameSize)
                        FRAME_GENRE -> result.genre = readTextFrame(tagData, dataStart, frameSize)
                        FRAME_PICTURE -> result.coverArt = readApicFrame(tagData, dataStart, frameSize)
                    }

                    pos = dataEnd
                }
                return result
            }
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "readTags failed: ${e.message}")
            return null
        }
    }

    /**
     * Write text tags to an MP3 file (ID3v2.3).
     * Preserves existing frames not being modified.
     * Returns true on success.
     */
    fun writeTags(file: File, tags: AudioTagData): Boolean {
        if (!file.exists()) return false
        try {
            val existingFrames = readAllFrames(file) ?: mutableMapOf()

            // Update/add text frames
            if (tags.title.isNotEmpty()) existingFrames[FRAME_TITLE] = buildTextFrame(FRAME_TITLE, tags.title)
            else existingFrames.remove(FRAME_TITLE)

            if (tags.artist.isNotEmpty()) existingFrames[FRAME_ARTIST] = buildTextFrame(FRAME_ARTIST, tags.artist)
            else existingFrames.remove(FRAME_ARTIST)

            if (tags.album.isNotEmpty()) existingFrames[FRAME_ALBUM] = buildTextFrame(FRAME_ALBUM, tags.album)
            else existingFrames.remove(FRAME_ALBUM)

            if (tags.year.isNotEmpty()) existingFrames[FRAME_YEAR] = buildTextFrame(FRAME_YEAR, tags.year)
            else existingFrames.remove(FRAME_YEAR)
            existingFrames.remove(FRAME_YEAR_V24) // remove v2.4 year if present

            if (tags.genre.isNotEmpty()) existingFrames[FRAME_GENRE] = buildTextFrame(FRAME_GENRE, tags.genre)
            else existingFrames.remove(FRAME_GENRE)

            if (tags.coverArt != null) {
                existingFrames[FRAME_PICTURE] = buildApicFrame(tags.coverArt!!, tags.coverMimeType ?: "image/jpeg")
            }

            return writeId3Tag(file, existingFrames)
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "writeTags failed: ${e.message}")
            return false
        }
    }

    /**
     * Write only cover art to an MP3 file.
     */
    fun writeCoverArt(file: File, imageBytes: ByteArray, mimeType: String = "image/jpeg"): Boolean {
        if (!file.exists()) return false
        try {
            val existingFrames = readAllFrames(file) ?: mutableMapOf()
            existingFrames[FRAME_PICTURE] = buildApicFrame(imageBytes, mimeType)
            return writeId3Tag(file, existingFrames)
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "writeCoverArt failed: ${e.message}")
            return false
        }
    }

    // ─── Internal: Read ───

    private fun readAllFrames(file: File): MutableMap<String, ByteArray>? {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(10)
            raf.readFully(header)
            if (header[0].toInt().toChar() != 'I' || header[1].toInt().toChar() != 'D' || header[2].toInt().toChar() != '3') {
                return null // no ID3 tag
            }
            val version = header[3].toInt()
            val tagSize = decodeSyncsafe(header, 6)
            val tagData = ByteArray(tagSize)
            raf.readFully(tagData)

            val frames = mutableMapOf<String, ByteArray>()
            var pos = 0
            while (pos + 10 <= tagSize) {
                val frameId = String(tagData, pos, 4, LATIN1)
                if (frameId[0] == '\u0000') break
                val frameSize = if (version >= 4) decodeSyncsafe(tagData, pos + 4) else decodeInt(tagData, pos + 4)
                val frameFlags = ByteArray(2)
                System.arraycopy(tagData, pos + 8, frameFlags, 0, 2)
                val fullFrame = ByteArray(10 + frameSize)
                System.arraycopy(tagData, pos, fullFrame, 0, fullFrame.size.coerceAtMost(tagSize - pos))
                frames[frameId] = fullFrame
                pos += 10 + frameSize
            }
            return frames
        }
    }

    private fun readTextFrame(data: ByteArray, offset: Int, size: Int): String {
        if (size < 2) return ""
        val encoding = data[offset].toInt() and 0xFF
        val charset = when (encoding) {
            0 -> LATIN1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> UTF8
            else -> LATIN1
        }
        // Skip encoding byte, read text
        var end = offset + 1 + (size - 1)
        // Trim null terminators
        while (end > offset + 1 && data[end - 1].toInt() == 0) end--
        if (end <= offset + 1) return ""
        return String(data, offset + 1, end - (offset + 1), charset).trim()
    }

    private fun readApicFrame(data: ByteArray, offset: Int, size: Int): ByteArray? {
        if (size < 4) return null
        val encoding = data[offset].toInt() and 0xFF
        // Skip encoding byte, read mime type (null-terminated Latin1)
        var pos = offset + 1
        val end = offset + size
        while (pos < end && data[pos].toInt() != 0) pos++
        pos++ // skip null
        if (pos >= end) return null
        pos++ // skip picture type byte
        // Skip description (null-terminated)
        if (encoding == 1 || encoding == 2) {
            // UTF-16 description: look for double-null
            while (pos + 1 < end) {
                if (data[pos].toInt() == 0 && data[pos + 1].toInt() == 0) { pos += 2; break }
                pos += 2
            }
        } else {
            while (pos < end && data[pos].toInt() != 0) pos++
            pos++ // skip null
        }
        if (pos >= end) return null
        val imageSize = end - pos
        val result = ByteArray(imageSize)
        System.arraycopy(data, pos, result, 0, imageSize)
        return result
    }

    // ─── Internal: Build Frames ───

    private fun buildTextFrame(frameId: String, text: String): ByteArray {
        // UTF-8 encoding (0x03) for ID3v2.3 compat (most players handle it)
        val textBytes = text.toByteArray(UTF8)
        val frameSize = 1 + textBytes.size // encoding byte + text
        val out = ByteArrayOutputStream(10 + frameSize)
        out.write(frameId.toByteArray(LATIN1))
        out.write(encodeInt(frameSize))
        out.write(byteArrayOf(0, 0)) // flags
        out.write(3) // UTF-8 encoding
        out.write(textBytes)
        return out.toByteArray()
    }

    private fun buildApicFrame(imageBytes: ByteArray, mimeType: String): ByteArray {
        val mimeBytes = mimeType.toByteArray(LATIN1)
        // encoding(1) + mime(N) + null(1) + pictureType(1) + description null(1) + image
        val frameSize = 1 + mimeBytes.size + 1 + 1 + 1 + imageBytes.size
        val out = ByteArrayOutputStream(10 + frameSize)
        out.write(FRAME_PICTURE.toByteArray(LATIN1))
        out.write(encodeInt(frameSize))
        out.write(byteArrayOf(0, 0)) // flags
        out.write(0) // Latin1 encoding
        out.write(mimeBytes)
        out.write(0) // null terminator
        out.write(3) // picture type: cover (front)
        out.write(0) // empty description null terminator
        out.write(imageBytes)
        return out.toByteArray()
    }

    // ─── Internal: Write ───

    private fun writeId3Tag(file: File, frames: Map<String, ByteArray>): Boolean {
        // Calculate total frames size
        val framesBytes = ByteArrayOutputStream()
        for ((_, frame) in frames) {
            framesBytes.write(frame)
        }
        val framesData = framesBytes.toByteArray()

        // Read old tag size and audio data
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(10)
            val hasId3: Boolean
            val oldTagEnd: Long

            if (raf.length() >= 10) {
                raf.readFully(header)
                hasId3 = header[0].toInt().toChar() == 'I' && header[1].toInt().toChar() == 'D' && header[2].toInt().toChar() == '3'
            } else {
                hasId3 = false
            }

            if (hasId3) {
                val oldTagSize = decodeSyncsafe(header, 6)
                oldTagEnd = 10L + oldTagSize
            } else {
                oldTagEnd = 0
            }

            // Read audio data (everything after old tag)
            raf.seek(oldTagEnd)
            val audioSize = raf.length() - oldTagEnd
            val audioData = ByteArray(audioSize.toInt())
            raf.readFully(audioData)

            // Add padding (2KB) for future edits without rewrite
            val padding = 2048
            val newTagSize = framesData.size + padding

            // Build new ID3v2.3 header
            val newHeader = ByteArray(10)
            newHeader[0] = 'I'.code.toByte()
            newHeader[1] = 'D'.code.toByte()
            newHeader[2] = '3'.code.toByte()
            newHeader[3] = 3 // version 2.3
            newHeader[4] = 0 // revision
            newHeader[5] = 0 // flags
            val syncsafe = encodeSyncsafe(newTagSize)
            System.arraycopy(syncsafe, 0, newHeader, 6, 4)

            // Write new file
            RandomAccessFile(file, "rw").use { out ->
                out.setLength(0)
                out.write(newHeader)
                out.write(framesData)
                out.write(ByteArray(padding)) // null padding
                out.write(audioData)
            }
        }
        EcosystemLogger.d(TAG, "writeId3Tag: wrote ${frames.size} frames to ${file.name}")
        return true
    }

    // ─── Encoding helpers ───

    private fun decodeSyncsafe(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0x7F) shl 21) or
                ((data[offset + 1].toInt() and 0x7F) shl 14) or
                ((data[offset + 2].toInt() and 0x7F) shl 7) or
                (data[offset + 3].toInt() and 0x7F)
    }

    private fun encodeSyncsafe(value: Int): ByteArray {
        return byteArrayOf(
            ((value shr 21) and 0x7F).toByte(),
            ((value shr 14) and 0x7F).toByte(),
            ((value shr 7) and 0x7F).toByte(),
            (value and 0x7F).toByte()
        )
    }

    private fun decodeInt(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
    }

    private fun encodeInt(value: Int): ByteArray {
        return byteArrayOf(
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }
}

data class AudioTagData(
    var title: String = "",
    var artist: String = "",
    var album: String = "",
    var year: String = "",
    var genre: String = "",
    var coverArt: ByteArray? = null,
    var coverMimeType: String? = null
)
