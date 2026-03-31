package com.vamp.haron.common.audio

import com.vamp.core.logger.EcosystemLogger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FLAC metadata tag reader/writer.
 * Handles VORBIS_COMMENT (type 4) and PICTURE (type 6) blocks.
 * Spec: https://xiph.org/flac/format.html
 */
object FlacTagEditor {
    private const val TAG = "Haron/FlacTag"

    private const val BLOCK_STREAMINFO = 0
    private const val BLOCK_PADDING = 1
    private const val BLOCK_VORBIS_COMMENT = 4
    private const val BLOCK_PICTURE = 6

    fun readTags(file: File): AudioTagData? {
        if (!file.exists() || file.length() < 8) return null
        try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (String(magic) != "fLaC") return null

                val result = AudioTagData()
                while (true) {
                    val blockHeader = ByteArray(4)
                    raf.readFully(blockHeader)
                    val isLast = (blockHeader[0].toInt() and 0x80) != 0
                    val blockType = blockHeader[0].toInt() and 0x7F
                    val blockLen = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                            ((blockHeader[2].toInt() and 0xFF) shl 8) or
                            (blockHeader[3].toInt() and 0xFF)

                    when (blockType) {
                        BLOCK_VORBIS_COMMENT -> {
                            val data = ByteArray(blockLen)
                            raf.readFully(data)
                            parseVorbisComment(data, result)
                        }
                        BLOCK_PICTURE -> {
                            val data = ByteArray(blockLen)
                            raf.readFully(data)
                            parsePicture(data, result)
                        }
                        else -> raf.skipBytes(blockLen)
                    }
                    if (isLast) break
                }
                return result
            }
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "readTags: ${e.message}")
            return null
        }
    }

    fun writeTags(file: File, tags: AudioTagData): Boolean {
        return safeWrite(file) { tempFile ->
            writeTagsInternal(tempFile, tags)
        }
    }

    fun writeCoverArt(file: File, imageBytes: ByteArray, mimeType: String = "image/jpeg"): Boolean {
        return safeWrite(file) { tempFile ->
            // Read existing tags, add cover
            val existing = readTags(tempFile) ?: AudioTagData()
            existing.coverArt = imageBytes
            existing.coverMimeType = mimeType
            writeTagsInternal(tempFile, existing)
        }
    }

    private fun writeTagsInternal(file: File, tags: AudioTagData): Boolean {
        try {
            // Read all blocks
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (String(magic) != "fLaC") return false

                data class Block(val type: Int, val data: ByteArray)
                val blocks = mutableListOf<Block>()
                var oldCommentPaddingSize = 0L

                while (true) {
                    val blockHeader = ByteArray(4)
                    raf.readFully(blockHeader)
                    val isLast = (blockHeader[0].toInt() and 0x80) != 0
                    val blockType = blockHeader[0].toInt() and 0x7F
                    val blockLen = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                            ((blockHeader[2].toInt() and 0xFF) shl 8) or
                            (blockHeader[3].toInt() and 0xFF)

                    val data = ByteArray(blockLen)
                    raf.readFully(data)

                    when (blockType) {
                        BLOCK_VORBIS_COMMENT -> oldCommentPaddingSize += 4 + blockLen
                        BLOCK_PADDING -> oldCommentPaddingSize += 4 + blockLen
                        BLOCK_PICTURE -> oldCommentPaddingSize += 4 + blockLen
                        else -> blocks.add(Block(blockType, data))
                    }
                    if (isLast) break
                }

                // Build new VORBIS_COMMENT
                val commentData = buildVorbisComment(tags)
                val newBlocks = blocks.toMutableList()
                newBlocks.add(Block(BLOCK_VORBIS_COMMENT, commentData))

                // Build PICTURE if cover art present
                if (tags.coverArt != null) {
                    val pictureData = buildPicture(tags.coverArt!!, tags.coverMimeType ?: "image/jpeg")
                    newBlocks.add(Block(BLOCK_PICTURE, pictureData))
                }

                // Calculate sizes for padding strategy
                val newMetaSize = newBlocks.sumOf { 4 + it.data.size }
                val oldMetaSize = (raf.filePointer - 4).toInt() // position after all blocks minus fLaC marker

                // Audio data
                val audioStart = raf.filePointer
                val audioSize = raf.length() - audioStart
                val audioData = ByteArray(audioSize.toInt())
                raf.readFully(audioData)

                // Write file
                RandomAccessFile(file, "rw").use { out ->
                    out.setLength(0)
                    out.write(magic) // fLaC

                    // Add padding (8KB for future edits)
                    val paddingSize = 8192
                    newBlocks.add(Block(BLOCK_PADDING, ByteArray(paddingSize)))

                    for ((idx, block) in newBlocks.withIndex()) {
                        val isLastBlock = idx == newBlocks.size - 1
                        val header = ByteArray(4)
                        header[0] = ((if (isLastBlock) 0x80 else 0) or (block.type and 0x7F)).toByte()
                        header[1] = ((block.data.size shr 16) and 0xFF).toByte()
                        header[2] = ((block.data.size shr 8) and 0xFF).toByte()
                        header[3] = (block.data.size and 0xFF).toByte()
                        out.write(header)
                        out.write(block.data)
                    }

                    out.write(audioData)
                }
            }
            return true
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "writeTags: ${e.message}")
            return false
        }
    }

    // ─── Vorbis Comment (little-endian lengths) ───

    private fun parseVorbisComment(data: ByteArray, result: AudioTagData) {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val vendorLen = buf.int
        if (vendorLen < 0 || vendorLen > data.size) return
        buf.position(buf.position() + vendorLen) // skip vendor string
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

    private fun buildVorbisComment(tags: AudioTagData): ByteArray {
        val out = ByteArrayOutputStream()
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
        return out.toByteArray()
    }

    // ─── PICTURE block (big-endian) ───

    private fun parsePicture(data: ByteArray, result: AudioTagData) {
        if (data.size < 32) return
        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val pictureType = buf.int
        if (pictureType != 3) return // only front cover
        val mimeLen = buf.int
        if (mimeLen < 0 || mimeLen > buf.remaining()) return
        val mimeBytes = ByteArray(mimeLen)
        buf.get(mimeBytes)
        val descLen = buf.int
        if (descLen < 0 || descLen > buf.remaining()) return
        buf.position(buf.position() + descLen) // skip description
        buf.int // width
        buf.int // height
        buf.int // color depth
        buf.int // colors used
        val dataLen = buf.int
        if (dataLen < 0 || dataLen > buf.remaining()) return
        val imageBytes = ByteArray(dataLen)
        buf.get(imageBytes)
        result.coverArt = imageBytes
        result.coverMimeType = String(mimeBytes, Charsets.US_ASCII)
    }

    private fun buildPicture(imageBytes: ByteArray, mimeType: String): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)

        fun writeBE32(v: Int) { buf.clear(); buf.putInt(v); out.write(buf.array()) }

        writeBE32(3) // picture type: front cover
        val mimeBytes = mimeType.toByteArray(Charsets.US_ASCII)
        writeBE32(mimeBytes.size)
        out.write(mimeBytes)
        writeBE32(0) // description length
        writeBE32(0) // width (unknown)
        writeBE32(0) // height (unknown)
        writeBE32(0) // color depth
        writeBE32(0) // colors used
        writeBE32(imageBytes.size)
        out.write(imageBytes)
        return out.toByteArray()
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
            if (!block(tempFile)) {
                tempFile.delete()
                return false
            }
            val backup = File(file.parent, ".${file.name}.bak")
            file.renameTo(backup)
            return if (tempFile.renameTo(file)) {
                backup.delete()
                true
            } else {
                backup.renameTo(file)
                tempFile.delete()
                false
            }
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "safeWrite: ${e.message}")
            tempFile.delete()
            return false
        }
    }
}
