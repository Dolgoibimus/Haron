package com.vamp.haron.common.audio

import com.vamp.core.logger.EcosystemLogger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * M4A/MP4 metadata tag reader/writer.
 * Handles ilst atoms inside moov/udta/meta for iTunes-style metadata.
 * Spec: ISO 14496-12, Apple iTunes metadata spec.
 */
object M4aTagEditor {
    private const val TAG = "Haron/M4aTag"

    // Atom type constants (©nam = 0xA96E616D)
    private const val ATOM_NAM = "\u00A9nam" // title
    private const val ATOM_ART = "\u00A9ART" // artist
    private const val ATOM_ALB = "\u00A9alb" // album
    private const val ATOM_DAY = "\u00A9day" // year
    private const val ATOM_GEN = "\u00A9gen" // genre
    private const val ATOM_COVR = "covr"     // cover art

    fun readTags(file: File): AudioTagData? {
        if (!file.exists() || file.length() < 8) return null
        try {
            RandomAccessFile(file, "r").use { raf ->
                val ilst = findIlst(raf) ?: return null
                val result = AudioTagData()
                parseIlst(ilst, result)
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

    // ─── Read ───

    private data class AtomInfo(val offset: Long, val size: Long, val type: String, val headerSize: Int)

    private fun findAtom(raf: RandomAccessFile, start: Long, end: Long, targetType: String): AtomInfo? {
        var pos = start
        while (pos + 8 <= end) {
            raf.seek(pos)
            val sizeBytes = ByteArray(4)
            raf.readFully(sizeBytes)
            val typeBytes = ByteArray(4)
            raf.readFully(typeBytes)
            var size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
            var headerSize = 8

            if (size == 1L) {
                // Extended size
                val extBytes = ByteArray(8)
                raf.readFully(extBytes)
                size = ByteBuffer.wrap(extBytes).order(ByteOrder.BIG_ENDIAN).long
                headerSize = 16
            } else if (size == 0L) {
                size = end - pos
            }

            val type = String(typeBytes, Charsets.ISO_8859_1)
            if (type == targetType) return AtomInfo(pos, size, type, headerSize)
            if (size < 8) break
            pos += size
        }
        return null
    }

    private fun findIlst(raf: RandomAccessFile): ByteArray? {
        val fileSize = raf.length()
        val moov = findAtom(raf, 0, fileSize, "moov") ?: return null
        val moovEnd = moov.offset + moov.size
        val udta = findAtom(raf, moov.offset + moov.headerSize, moovEnd, "udta") ?: return null
        val udtaEnd = udta.offset + udta.size
        val meta = findAtom(raf, udta.offset + udta.headerSize, udtaEnd, "meta") ?: return null
        // meta has 4 extra bytes (version + flags)
        val metaDataStart = meta.offset + meta.headerSize + 4
        val metaEnd = meta.offset + meta.size
        val ilst = findAtom(raf, metaDataStart, metaEnd, "ilst") ?: return null

        val ilstDataSize = (ilst.size - ilst.headerSize).toInt()
        if (ilstDataSize <= 0) return null
        raf.seek(ilst.offset + ilst.headerSize)
        val data = ByteArray(ilstDataSize)
        raf.readFully(data)
        return data
    }

    private fun parseIlst(ilstData: ByteArray, result: AudioTagData) {
        var pos = 0
        while (pos + 8 <= ilstData.size) {
            val atomSize = ByteBuffer.wrap(ilstData, pos, 4).order(ByteOrder.BIG_ENDIAN).int
            if (atomSize < 8 || pos + atomSize > ilstData.size) break
            val atomType = String(ilstData, pos + 4, 4, Charsets.ISO_8859_1)

            // Find 'data' sub-atom
            val dataOffset = pos + 8
            val dataEnd = pos + atomSize
            if (dataOffset + 16 <= dataEnd) {
                val dataAtomSize = ByteBuffer.wrap(ilstData, dataOffset, 4).order(ByteOrder.BIG_ENDIAN).int
                val dataAtomType = String(ilstData, dataOffset + 4, 4, Charsets.ISO_8859_1)
                if (dataAtomType == "data" && dataAtomSize >= 16) {
                    val flags = ((ilstData[dataOffset + 9].toInt() and 0xFF) shl 16) or
                            ((ilstData[dataOffset + 10].toInt() and 0xFF) shl 8) or
                            (ilstData[dataOffset + 11].toInt() and 0xFF)
                    val valueStart = dataOffset + 16
                    val valueLen = dataAtomSize - 16

                    if (valueLen > 0 && valueStart + valueLen <= ilstData.size) {
                        when (atomType) {
                            ATOM_NAM -> result.title = String(ilstData, valueStart, valueLen, Charsets.UTF_8)
                            ATOM_ART -> result.artist = String(ilstData, valueStart, valueLen, Charsets.UTF_8)
                            ATOM_ALB -> result.album = String(ilstData, valueStart, valueLen, Charsets.UTF_8)
                            ATOM_DAY -> result.year = String(ilstData, valueStart, valueLen, Charsets.UTF_8)
                            ATOM_GEN -> result.genre = String(ilstData, valueStart, valueLen, Charsets.UTF_8)
                            ATOM_COVR -> {
                                result.coverArt = ilstData.copyOfRange(valueStart, valueStart + valueLen)
                                result.coverMimeType = if (flags == 14) "image/png" else "image/jpeg"
                            }
                        }
                    }
                }
            }
            pos += atomSize
        }
    }

    // ─── Write ───

    private fun writeTagsInternal(file: File, tags: AudioTagData): Boolean {
        try {
            // Parse file structure
            RandomAccessFile(file, "r").use { raf ->
                val fileSize = raf.length()
                val moov = findAtom(raf, 0, fileSize, "moov")
                    ?: return false.also { EcosystemLogger.e(TAG, "No moov atom") }
                val moovEnd = moov.offset + moov.size

                // Find mdat position for stco/co64 offset adjustment
                val mdat = findAtom(raf, 0, fileSize, "mdat")

                // Read entire moov
                raf.seek(moov.offset)
                val moovBytes = ByteArray(moov.size.toInt())
                raf.readFully(moovBytes)

                // Build new ilst
                val newIlst = buildIlst(tags)

                // Find udta/meta/ilst inside moov bytes
                val newMoov = replaceIlstInMoov(moovBytes, newIlst) ?: return false

                // Calculate delta for stco/co64
                val moovDelta = newMoov.size - moovBytes.size

                // Update stco/co64 if moov is before mdat
                val finalMoov = if (mdat != null && moov.offset < mdat.offset && moovDelta != 0) {
                    updateChunkOffsets(newMoov, moovDelta)
                } else newMoov

                // Read everything before moov, and everything after moov
                val beforeMoov = ByteArray(moov.offset.toInt())
                raf.seek(0)
                raf.readFully(beforeMoov)
                val afterMoov = ByteArray((fileSize - moovEnd).toInt())
                raf.seek(moovEnd)
                raf.readFully(afterMoov)

                // Write file
                RandomAccessFile(file, "rw").use { out ->
                    out.setLength(0)
                    out.write(beforeMoov)
                    out.write(finalMoov)
                    out.write(afterMoov)
                }
            }
            return true
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "writeTags: ${e.message}")
            return false
        }
    }

    private fun replaceIlstInMoov(moovBytes: ByteArray, newIlst: ByteArray): ByteArray? {
        // Find udta in moov
        val udtaPos = findAtomInBytes(moovBytes, 8, moovBytes.size, "udta")

        if (udtaPos == null) {
            // No udta — create udta/meta/hdlr/ilst and append to moov
            val metaContent = buildHdlrAtom() + wrapAtom("ilst", newIlst)
            val metaAtom = wrapMetaAtom(metaContent)
            val udtaAtom = wrapAtom("udta", metaAtom)

            val result = ByteArrayOutputStream()
            result.write(moovBytes)
            result.write(udtaAtom)
            val out = result.toByteArray()
            // Update moov size
            updateAtomSize(out, 0, out.size)
            return out
        }

        val udtaSize = readBE32(moovBytes, udtaPos)
        val udtaEnd = udtaPos + udtaSize

        // Find meta in udta
        val metaPos = findAtomInBytes(moovBytes, udtaPos + 8, udtaEnd, "meta")

        if (metaPos == null) {
            // No meta — create meta/hdlr/ilst and insert into udta
            val metaContent = buildHdlrAtom() + wrapAtom("ilst", newIlst)
            val metaAtom = wrapMetaAtom(metaContent)

            val result = ByteArrayOutputStream()
            result.write(moovBytes, 0, udtaEnd) // everything in udta
            result.write(metaAtom)
            result.write(moovBytes, udtaEnd, moovBytes.size - udtaEnd)
            val out = result.toByteArray()
            updateAtomSize(out, udtaPos, udtaSize + metaAtom.size)
            updateAtomSize(out, 0, out.size)
            return out
        }

        val metaSize = readBE32(moovBytes, metaPos)
        val metaEnd = metaPos + metaSize
        // meta has +4 version/flags
        val metaDataStart = metaPos + 12

        // Find ilst in meta
        val ilstPos = findAtomInBytes(moovBytes, metaDataStart, metaEnd, "ilst")

        if (ilstPos == null) {
            // No ilst — append to meta
            val ilstAtom = wrapAtom("ilst", newIlst)
            val result = ByteArrayOutputStream()
            result.write(moovBytes, 0, metaEnd)
            result.write(ilstAtom)
            result.write(moovBytes, metaEnd, moovBytes.size - metaEnd)
            val out = result.toByteArray()
            updateAtomSize(out, metaPos, metaSize + ilstAtom.size)
            updateAtomSize(out, udtaPos, udtaSize + ilstAtom.size)
            updateAtomSize(out, 0, out.size)
            return out
        }

        val oldIlstSize = readBE32(moovBytes, ilstPos)
        val ilstEnd = ilstPos + oldIlstSize

        // Check for free atom after ilst
        var freeSize = 0
        if (ilstEnd + 8 <= metaEnd) {
            val nextType = String(moovBytes, ilstEnd + 4, 4, Charsets.ISO_8859_1)
            if (nextType == "free") freeSize = readBE32(moovBytes, ilstEnd)
        }

        val newIlstAtom = wrapAtom("ilst", newIlst)
        val delta = newIlstAtom.size - oldIlstSize

        // Build result: replace ilst (and maybe absorb/create free atom)
        val result = ByteArrayOutputStream()
        result.write(moovBytes, 0, ilstPos) // before ilst
        result.write(newIlstAtom) // new ilst

        val afterIlstStart = ilstEnd + freeSize // skip old ilst + old free
        val remainingInMeta = afterIlstStart

        // Add free atom for padding if we have space
        val availableSpace = oldIlstSize + freeSize
        val spaceLeft = availableSpace - newIlstAtom.size
        if (spaceLeft >= 8) {
            // Fit with free padding
            result.write(buildFreeAtom(spaceLeft))
            result.write(moovBytes, afterIlstStart, moovBytes.size - afterIlstStart)
            // Sizes unchanged since we used exact same space
            return result.toByteArray()
        }

        // No room for padding, sizes change
        result.write(moovBytes, afterIlstStart, moovBytes.size - afterIlstStart)
        val out = result.toByteArray()

        val totalDelta = newIlstAtom.size - (oldIlstSize + freeSize)
        updateAtomSize(out, metaPos, metaSize + totalDelta)
        updateAtomSize(out, udtaPos, udtaSize + totalDelta)
        updateAtomSize(out, 0, out.size)
        return out
    }

    // ─── Atom builders ───

    private fun buildIlst(tags: AudioTagData): ByteArray {
        val out = ByteArrayOutputStream()
        if (tags.title.isNotEmpty()) out.write(buildTextAtom(ATOM_NAM, tags.title))
        if (tags.artist.isNotEmpty()) out.write(buildTextAtom(ATOM_ART, tags.artist))
        if (tags.album.isNotEmpty()) out.write(buildTextAtom(ATOM_ALB, tags.album))
        if (tags.year.isNotEmpty()) out.write(buildTextAtom(ATOM_DAY, tags.year))
        if (tags.genre.isNotEmpty()) out.write(buildTextAtom(ATOM_GEN, tags.genre))
        if (tags.coverArt != null) {
            val isPng = tags.coverMimeType == "image/png"
            out.write(buildCoverAtom(tags.coverArt!!, isPng))
        }
        return out.toByteArray()
    }

    private fun buildTextAtom(type: String, value: String): ByteArray {
        val valueBytes = value.toByteArray(Charsets.UTF_8)
        // data atom: 16 bytes header + value
        val dataAtomSize = 16 + valueBytes.size
        val dataAtom = ByteArrayOutputStream()
        writeBE32(dataAtom, dataAtomSize)
        dataAtom.write("data".toByteArray(Charsets.ISO_8859_1))
        writeBE32(dataAtom, 1) // version(0) + flags(1 = UTF8)
        writeBE32(dataAtom, 0) // locale
        dataAtom.write(valueBytes)

        // Wrap in parent atom
        return wrapAtom(type, dataAtom.toByteArray())
    }

    private fun buildCoverAtom(imageBytes: ByteArray, isPng: Boolean): ByteArray {
        val flags = if (isPng) 14 else 13
        val dataAtomSize = 16 + imageBytes.size
        val dataAtom = ByteArrayOutputStream()
        writeBE32(dataAtom, dataAtomSize)
        dataAtom.write("data".toByteArray(Charsets.ISO_8859_1))
        writeBE32(dataAtom, flags) // version(0) + flags(13=JPEG, 14=PNG)
        writeBE32(dataAtom, 0) // locale
        dataAtom.write(imageBytes)

        return wrapAtom(ATOM_COVR, dataAtom.toByteArray())
    }

    private fun wrapAtom(type: String, content: ByteArray): ByteArray {
        val size = 8 + content.size
        val out = ByteArrayOutputStream(size)
        writeBE32(out, size)
        out.write(type.toByteArray(Charsets.ISO_8859_1))
        out.write(content)
        return out.toByteArray()
    }

    private fun wrapMetaAtom(content: ByteArray): ByteArray {
        // meta has 4 extra bytes (version + flags) between header and children
        val size = 12 + content.size
        val out = ByteArrayOutputStream(size)
        writeBE32(out, size)
        out.write("meta".toByteArray(Charsets.ISO_8859_1))
        writeBE32(out, 0) // version + flags
        out.write(content)
        return out.toByteArray()
    }

    private fun buildHdlrAtom(): ByteArray {
        val out = ByteArrayOutputStream()
        // hdlr inside meta: version/flags(4) + pre_defined(4) + handler_type(4) + reserved(12) + name(1)
        val data = ByteArray(25)
        // handler_type at offset 8
        System.arraycopy("mdir".toByteArray(), 0, data, 8, 4)
        // manufacturer at offset 12
        System.arraycopy("appl".toByteArray(), 0, data, 12, 4)
        return wrapAtom("hdlr", data)
    }

    private fun buildFreeAtom(size: Int): ByteArray {
        val out = ByteArrayOutputStream(size)
        writeBE32(out, size)
        out.write("free".toByteArray(Charsets.ISO_8859_1))
        out.write(ByteArray(size - 8))
        return out.toByteArray()
    }

    // ─── stco/co64 offset adjustment ───

    private fun updateChunkOffsets(moovBytes: ByteArray, delta: Int): ByteArray {
        val result = moovBytes.copyOf()
        updateStcoRecursive(result, 8, result.size, delta)
        return result
    }

    private fun updateStcoRecursive(data: ByteArray, start: Int, end: Int, delta: Int) {
        var pos = start
        while (pos + 8 <= end) {
            val atomSize = readBE32(data, pos)
            if (atomSize < 8 || pos + atomSize > end) break
            val atomType = String(data, pos + 4, 4, Charsets.ISO_8859_1)

            when (atomType) {
                "stco" -> {
                    // version(1) + flags(3) + entry_count(4) + offsets(4 each)
                    val countPos = pos + 12
                    if (countPos + 4 > end) { pos += atomSize; continue }
                    val count = readBE32(data, countPos)
                    for (i in 0 until count) {
                        val offPos = countPos + 4 + i * 4
                        if (offPos + 4 > end) break
                        val oldVal = readBE32(data, offPos)
                        writeBE32(data, offPos, oldVal + delta)
                    }
                }
                "co64" -> {
                    val countPos = pos + 12
                    if (countPos + 4 > end) { pos += atomSize; continue }
                    val count = readBE32(data, countPos)
                    for (i in 0 until count) {
                        val offPos = countPos + 4 + i * 8
                        if (offPos + 8 > end) break
                        val oldVal = readBE64(data, offPos)
                        writeBE64(data, offPos, oldVal + delta)
                    }
                }
                "moov", "trak", "mdia", "minf", "stbl" -> {
                    // Container atoms — recurse
                    updateStcoRecursive(data, pos + 8, pos + atomSize, delta)
                }
                "meta" -> {
                    // +4 for version/flags
                    updateStcoRecursive(data, pos + 12, pos + atomSize, delta)
                }
            }
            pos += atomSize
        }
    }

    // ─── Helpers ───

    private fun findAtomInBytes(data: ByteArray, start: Int, end: Int, targetType: String): Int? {
        var pos = start
        while (pos + 8 <= end) {
            val size = readBE32(data, pos)
            if (size < 8 || pos + size > end) break
            val type = String(data, pos + 4, 4, Charsets.ISO_8859_1)
            if (type == targetType) return pos
            pos += size
        }
        return null
    }

    private fun readBE32(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
    }

    private fun writeBE32(data: ByteArray, offset: Int, value: Int) {
        data[offset] = ((value shr 24) and 0xFF).toByte()
        data[offset + 1] = ((value shr 16) and 0xFF).toByte()
        data[offset + 2] = ((value shr 8) and 0xFF).toByte()
        data[offset + 3] = (value and 0xFF).toByte()
    }

    private fun readBE64(data: ByteArray, offset: Int): Long {
        return ((data[offset].toLong() and 0xFF) shl 56) or
                ((data[offset + 1].toLong() and 0xFF) shl 48) or
                ((data[offset + 2].toLong() and 0xFF) shl 40) or
                ((data[offset + 3].toLong() and 0xFF) shl 32) or
                ((data[offset + 4].toLong() and 0xFF) shl 24) or
                ((data[offset + 5].toLong() and 0xFF) shl 16) or
                ((data[offset + 6].toLong() and 0xFF) shl 8) or
                (data[offset + 7].toLong() and 0xFF)
    }

    private fun writeBE64(data: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 8) {
            data[offset + i] = ((value shr (56 - i * 8)) and 0xFF).toByte()
        }
    }

    private fun writeBE32(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 24) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun updateAtomSize(data: ByteArray, offset: Int, newSize: Int) {
        writeBE32(data, offset, newSize)
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
