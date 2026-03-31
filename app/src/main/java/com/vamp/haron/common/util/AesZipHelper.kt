package com.vamp.haron.common.util

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val TAG = "AesZipHelper"

/**
 * WinZip AES encrypted ZIP helper.
 * Implements reading and writing of AES-256 encrypted ZIP files
 * compatible with WinZip AES format (extra field 0x9901).
 *
 * Format details:
 * - AES-256: salt=16 bytes, key=32 bytes
 * - PBKDF2WithHmacSHA1, 1000 iterations
 * - Derived key: 66 bytes = 32 (AES key) + 32 (HMAC key) + 2 (password verifier)
 * - Encryption: AES/CTR/NoPadding with little-endian counter starting at 1
 * - Authentication: HMAC-SHA1 over encrypted data, first 10 bytes stored after data
 */
object AesZipHelper {

    // AES extra field ID
    private const val AES_EXTRA_FIELD_ID: Int = 0x9901
    private const val AES_EXTRA_FIELD_SIZE: Int = 7 // data size (without header 4 bytes)

    // AES-256 parameters
    private const val SALT_LENGTH = 16
    private const val KEY_LENGTH = 32
    private const val PASSWORD_VERIFIER_LENGTH = 2
    private const val HMAC_LENGTH = 10
    private const val PBKDF2_ITERATIONS = 1000
    // derived key total: encryption key + hmac key + verifier = 32+32+2 = 66
    private const val DERIVED_KEY_LENGTH = KEY_LENGTH * 2 + PASSWORD_VERIFIER_LENGTH

    data class ZipEntryInfo(
        val name: String,
        val size: Long,
        val compressedSize: Long,
        val isDirectory: Boolean,
        val lastModified: Long,
        val isEncrypted: Boolean,
        val compressionMethod: Int
    )

    /**
     * Check if a ZIP file contains any encrypted entries.
     * Uses general purpose bit flag (bit 0) to detect encryption.
     */
    fun isZipEncrypted(file: File): Boolean {
        return try {
            // Parse raw ZIP to check encryption flag on entries
            RandomAccessFile(file, "r").use { raf ->
                val entries = parseLocalFileHeaders(raf)
                entries.any { it.isEncrypted }
            }
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "isZipEncrypted check failed: ${e.message}")
            false
        }
    }

    /**
     * List entries of an encrypted ZIP file.
     * For unencrypted ZIPs, use standard java.util.zip.ZipFile instead.
     */
    fun listEncryptedZip(file: File, password: CharArray?): List<ZipEntryInfo> {
        // We parse the central directory to get accurate info
        return parseCentralDirectory(file)
    }

    /**
     * Get decrypted InputStream for a single entry from an encrypted ZIP.
     */
    fun getDecryptedEntryBytes(file: File, entryName: String, password: CharArray): ByteArray? {
        RandomAccessFile(file, "r").use { raf ->
            val centralEntries = parseCentralDirectory(file)
            val target = centralEntries.firstOrNull { it.name.trimEnd('/') == entryName.trimEnd('/') }
                ?: return null

            // Find local file header offset from central directory
            val localOffset = findLocalHeaderOffset(raf, entryName)
                ?: return null

            return readAndDecryptEntry(raf, localOffset, password, target.compressionMethod)
        }
    }

    /**
     * Extract a single encrypted entry to an OutputStream.
     */
    fun extractDecryptedEntry(file: File, entryName: String, password: CharArray, output: OutputStream) {
        val bytes = getDecryptedEntryBytes(file, entryName, password)
            ?: throw IllegalStateException("Entry not found: $entryName")
        output.write(bytes)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Creation
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Create an AES-256 encrypted ZIP file.
     * @param outputPath Path for the output ZIP
     * @param files List of (File, zipPath) pairs
     * @param password Encryption password
     * @param onProgress Called with (index, total, fileName) for each file
     */
    fun createEncryptedZip(
        outputPath: String,
        files: List<Pair<File, String>>,
        password: CharArray,
        onProgress: ((Int, Int, String) -> Unit)? = null
    ) {
        val total = files.size
        FileOutputStream(outputPath).use { fos ->
            val entries = mutableListOf<CentralDirRecord>()
            var offset = 0L

            for ((index, pair) in files.withIndex()) {
                val (srcFile, zipPath) = pair
                onProgress?.invoke(index, total, srcFile.name)

                val record = writeEncryptedEntry(fos, srcFile, zipPath, password, offset)
                entries.add(record)
                offset += record.localHeaderSize + record.encryptedDataSize
            }

            // Write central directory
            val centralDirOffset = offset
            var centralDirSize = 0L
            for (record in entries) {
                val cdBytes = buildCentralDirEntry(record)
                fos.write(cdBytes)
                centralDirSize += cdBytes.size
            }

            // Write EOCD (End of Central Directory)
            val eocd = buildEocd(entries.size, centralDirSize.toInt(), centralDirOffset.toInt())
            fos.write(eocd)
        }
        EcosystemLogger.d(TAG, "createEncryptedZip: wrote $total entries to $outputPath")
    }

    /**
     * Create a split (multi-volume) ZIP. Encrypted if password is non-null.
     * @param splitSizeBytes Maximum size per volume in bytes
     */
    fun createSplitZip(
        outputPath: String,
        files: List<Pair<File, String>>,
        password: CharArray?,
        splitSizeBytes: Long,
        onProgress: ((Int, Int, String) -> Unit)? = null
    ) {
        if (password != null) {
            // For encrypted split, build in-memory first then split
            createEncryptedSplitZip(outputPath, files, password, splitSizeBytes, onProgress)
        } else {
            createUnencryptedSplitZip(outputPath, files, splitSizeBytes, onProgress)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal — reading
    // ──────────────────────────────────────────────────────────────────────

    private data class LocalEntry(
        val name: String,
        val isEncrypted: Boolean,
        val compressionMethod: Int,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val offset: Long,
        val dataOffset: Long, // offset to actual (encrypted) data
        val lastModTime: Int,
        val lastModDate: Int
    )

    private fun parseLocalFileHeaders(raf: RandomAccessFile): List<LocalEntry> {
        val entries = mutableListOf<LocalEntry>()
        raf.seek(0)
        val buf4 = ByteArray(4)

        while (raf.filePointer < raf.length() - 4) {
            val entryOffset = raf.filePointer
            raf.readFully(buf4)
            val sig = ByteBuffer.wrap(buf4).order(ByteOrder.LITTLE_ENDIAN).int

            // Local file header signature
            if (sig != 0x04034b50) break

            val buf26 = ByteArray(26)
            raf.readFully(buf26)
            val bb = ByteBuffer.wrap(buf26).order(ByteOrder.LITTLE_ENDIAN)
            bb.short // version needed
            val flags = bb.short.toInt() and 0xFFFF
            val method = bb.short.toInt() and 0xFFFF
            val modTime = bb.short.toInt() and 0xFFFF
            val modDate = bb.short.toInt() and 0xFFFF
            bb.int // crc32
            val compSize = bb.int.toLong() and 0xFFFFFFFFL
            val uncompSize = bb.int.toLong() and 0xFFFFFFFFL
            val nameLen = bb.short.toInt() and 0xFFFF
            val extraLen = bb.short.toInt() and 0xFFFF

            val nameBuf = ByteArray(nameLen)
            raf.readFully(nameBuf)
            val name = String(nameBuf, Charsets.UTF_8)

            val extraBuf = if (extraLen > 0) ByteArray(extraLen).also { raf.readFully(it) } else ByteArray(0)
            val dataOffset = raf.filePointer
            val isEncrypted = (flags and 1) != 0

            entries.add(LocalEntry(
                name = name,
                isEncrypted = isEncrypted,
                compressionMethod = method,
                compressedSize = compSize,
                uncompressedSize = uncompSize,
                offset = entryOffset,
                dataOffset = dataOffset,
                lastModTime = modTime,
                lastModDate = modDate
            ))

            // Skip data
            if (compSize > 0) {
                raf.seek(dataOffset + compSize)
            }

            // Data descriptor (bit 3 of flags)
            if ((flags and 8) != 0 && compSize == 0L) {
                // We can't reliably skip data descriptors without knowing size
                // For encrypted files this won't happen (AES stores size in header)
                break
            }
        }
        return entries
    }

    private fun parseCentralDirectory(file: File): List<ZipEntryInfo> {
        RandomAccessFile(file, "r").use { raf ->
            // Find EOCD (End of Central Directory Record)
            val eocdOffset = findEocdOffset(raf) ?: return emptyList()
            raf.seek(eocdOffset + 10) // skip sig(4) + diskNumber(2) + centralDirDisk(2) + entriesOnDisk(2)
            val buf = ByteArray(12)
            raf.readFully(buf)
            val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
            val totalEntries = bb.short.toInt() and 0xFFFF
            val centralDirSize = bb.int.toLong() and 0xFFFFFFFFL
            val centralDirOffset = bb.int.toLong() and 0xFFFFFFFFL

            val entries = mutableListOf<ZipEntryInfo>()
            raf.seek(centralDirOffset)

            for (i in 0 until totalEntries) {
                val sigBuf = ByteArray(4)
                raf.readFully(sigBuf)
                val sig = ByteBuffer.wrap(sigBuf).order(ByteOrder.LITTLE_ENDIAN).int
                if (sig != 0x02014b50) break

                val hdrBuf = ByteArray(42)
                raf.readFully(hdrBuf)
                val hb = ByteBuffer.wrap(hdrBuf).order(ByteOrder.LITTLE_ENDIAN)
                hb.short // version made by
                hb.short // version needed
                val flags = hb.short.toInt() and 0xFFFF
                val method = hb.short.toInt() and 0xFFFF
                val modTime = hb.short.toInt() and 0xFFFF
                val modDate = hb.short.toInt() and 0xFFFF
                hb.int // crc32
                val compSize = hb.int.toLong() and 0xFFFFFFFFL
                val uncompSize = hb.int.toLong() and 0xFFFFFFFFL
                val nameLen = hb.short.toInt() and 0xFFFF
                val extraLen = hb.short.toInt() and 0xFFFF
                val commentLen = hb.short.toInt() and 0xFFFF
                hb.short // disk number start
                hb.short // internal file attributes
                hb.int // external file attributes
                hb.int // relative offset of local header

                val nameBuf = ByteArray(nameLen)
                raf.readFully(nameBuf)
                val name = String(nameBuf, Charsets.UTF_8)

                // Skip extra + comment
                if (extraLen + commentLen > 0) {
                    raf.skipBytes(extraLen + commentLen)
                }

                val isEncrypted = (flags and 1) != 0
                val isDirectory = name.endsWith("/")

                // Convert DOS date/time to epoch millis
                val lastMod = dosDateTimeToEpoch(modDate, modTime)

                // For AES encrypted, actual compression method is stored in AES extra field
                // The method field is set to 99 (0x63)
                val actualMethod = if (method == 99) {
                    // Would need to parse extra field for real method; default DEFLATE
                    8 // DEFLATE
                } else method

                entries.add(ZipEntryInfo(
                    name = name,
                    size = uncompSize,
                    compressedSize = compSize,
                    isDirectory = isDirectory,
                    lastModified = lastMod,
                    isEncrypted = isEncrypted,
                    compressionMethod = actualMethod
                ))
            }
            return entries
        }
    }

    private fun findEocdOffset(raf: RandomAccessFile): Long? {
        val fileLen = raf.length()
        // EOCD is at least 22 bytes, max comment 65535
        val searchStart = maxOf(0L, fileLen - 22 - 65535)
        val searchLen = (fileLen - searchStart).toInt()
        raf.seek(searchStart)
        val buf = ByteArray(searchLen)
        raf.readFully(buf)
        // Search backwards for EOCD signature 0x06054b50
        for (i in buf.size - 22 downTo 0) {
            if (buf[i] == 0x50.toByte() && buf[i + 1] == 0x4b.toByte() &&
                buf[i + 2] == 0x05.toByte() && buf[i + 3] == 0x06.toByte()
            ) {
                return searchStart + i
            }
        }
        return null
    }

    private fun findLocalHeaderOffset(raf: RandomAccessFile, entryName: String): Long? {
        // Find from central directory
        val fileLen = raf.length()
        val eocdOffset = findEocdOffset(raf) ?: return null
        raf.seek(eocdOffset + 10)
        val buf = ByteArray(12)
        raf.readFully(buf)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        val totalEntries = bb.short.toInt() and 0xFFFF
        bb.int // central dir size
        val centralDirOffset = bb.int.toLong() and 0xFFFFFFFFL

        raf.seek(centralDirOffset)
        for (i in 0 until totalEntries) {
            val sigBuf = ByteArray(4)
            raf.readFully(sigBuf)
            val sig = ByteBuffer.wrap(sigBuf).order(ByteOrder.LITTLE_ENDIAN).int
            if (sig != 0x02014b50) break

            val hdrBuf = ByteArray(42)
            raf.readFully(hdrBuf)
            val hb = ByteBuffer.wrap(hdrBuf).order(ByteOrder.LITTLE_ENDIAN)
            hb.short // version made by
            hb.short // version needed
            hb.short // flags
            hb.short // method
            hb.short // mod time
            hb.short // mod date
            hb.int // crc32
            hb.int // comp size
            hb.int // uncomp size
            val nameLen = hb.short.toInt() and 0xFFFF
            val extraLen = hb.short.toInt() and 0xFFFF
            val commentLen = hb.short.toInt() and 0xFFFF
            hb.short // disk number
            hb.short // internal attrs
            hb.int // external attrs
            val localOffset = hb.int.toLong() and 0xFFFFFFFFL

            val nameBuf = ByteArray(nameLen)
            raf.readFully(nameBuf)
            val name = String(nameBuf, Charsets.UTF_8)

            if (extraLen + commentLen > 0) {
                raf.skipBytes(extraLen + commentLen)
            }

            if (name.trimEnd('/') == entryName.trimEnd('/')) {
                return localOffset
            }
        }
        return null
    }

    private fun readAndDecryptEntry(
        raf: RandomAccessFile,
        localOffset: Long,
        password: CharArray,
        centralCompressionMethod: Int
    ): ByteArray? {
        raf.seek(localOffset)
        val sigBuf = ByteArray(4)
        raf.readFully(sigBuf)
        val sig = ByteBuffer.wrap(sigBuf).order(ByteOrder.LITTLE_ENDIAN).int
        if (sig != 0x04034b50) return null

        val hdrBuf = ByteArray(26)
        raf.readFully(hdrBuf)
        val bb = ByteBuffer.wrap(hdrBuf).order(ByteOrder.LITTLE_ENDIAN)
        bb.short // version needed
        val flags = bb.short.toInt() and 0xFFFF
        val method = bb.short.toInt() and 0xFFFF
        bb.short // mod time
        bb.short // mod date
        bb.int // crc32
        val compSize = bb.int.toLong() and 0xFFFFFFFFL
        bb.int // uncomp size
        val nameLen = bb.short.toInt() and 0xFFFF
        val extraLen = bb.short.toInt() and 0xFFFF

        raf.skipBytes(nameLen)

        // Parse extra field to find AES info
        var aesVendorVersion = 0
        var aesKeyStrength = 0
        var actualCompressionMethod = centralCompressionMethod
        if (extraLen > 0) {
            val extraBuf = ByteArray(extraLen)
            raf.readFully(extraBuf)
            val ebb = ByteBuffer.wrap(extraBuf).order(ByteOrder.LITTLE_ENDIAN)
            var pos = 0
            while (pos + 4 <= extraLen) {
                val fieldId = ebb.short.toInt() and 0xFFFF
                val fieldSize = ebb.short.toInt() and 0xFFFF
                pos += 4
                if (fieldId == AES_EXTRA_FIELD_ID && fieldSize >= AES_EXTRA_FIELD_SIZE) {
                    aesVendorVersion = ebb.short.toInt() and 0xFFFF
                    ebb.short // vendor ID ("AE")
                    aesKeyStrength = ebb.get().toInt() and 0xFF // 1=128, 2=192, 3=256
                    actualCompressionMethod = ebb.short.toInt() and 0xFFFF
                    pos += fieldSize
                    break
                } else {
                    ebb.position(ebb.position() + fieldSize)
                    pos += fieldSize
                }
            }
        }

        // Read encrypted data
        val dataSize = compSize.toInt()
        val encryptedData = ByteArray(dataSize)
        raf.readFully(encryptedData)

        if (method != 99 || aesKeyStrength == 0) {
            // Not AES encrypted — shouldn't be called for this
            return null
        }

        // AES data layout: salt(16) + verifier(2) + encrypted_compressed_data + hmac(10)
        val saltLen = when (aesKeyStrength) {
            1 -> 8   // AES-128
            2 -> 12  // AES-192
            3 -> 16  // AES-256
            else -> 16
        }
        val aesKeyLen = when (aesKeyStrength) {
            1 -> 16  // AES-128
            2 -> 24  // AES-192
            3 -> 32  // AES-256
            else -> 32
        }
        val derivedLen = aesKeyLen * 2 + 2

        if (dataSize < saltLen + PASSWORD_VERIFIER_LENGTH + HMAC_LENGTH) {
            EcosystemLogger.e(TAG, "AES data too short: $dataSize")
            return null
        }

        val salt = encryptedData.copyOfRange(0, saltLen)
        val storedVerifier = encryptedData.copyOfRange(saltLen, saltLen + PASSWORD_VERIFIER_LENGTH)
        val encPayload = encryptedData.copyOfRange(
            saltLen + PASSWORD_VERIFIER_LENGTH,
            dataSize - HMAC_LENGTH
        )
        val storedHmac = encryptedData.copyOfRange(dataSize - HMAC_LENGTH, dataSize)

        // Derive keys
        val derivedKey = deriveKey(password, salt, derivedLen)
        val aesKey = derivedKey.copyOfRange(0, aesKeyLen)
        val hmacKey = derivedKey.copyOfRange(aesKeyLen, aesKeyLen * 2)
        val verifier = derivedKey.copyOfRange(aesKeyLen * 2, aesKeyLen * 2 + 2)

        // Verify password
        if (!verifier.contentEquals(storedVerifier)) {
            throw IllegalStateException("encrypted") // Wrong password
        }

        // Verify HMAC
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA1"))
        val computedHmac = mac.doFinal(encPayload)
        val truncatedHmac = computedHmac.copyOfRange(0, HMAC_LENGTH)
        if (!truncatedHmac.contentEquals(storedHmac)) {
            EcosystemLogger.e(TAG, "HMAC verification failed")
            throw IllegalStateException("Archive integrity check failed (HMAC mismatch)")
        }

        // Decrypt with AES-CTR (WinZip counter format)
        val decrypted = decryptAesCtr(encPayload, aesKey)

        // Decompress if needed
        return when (actualCompressionMethod) {
            0 -> decrypted // STORED
            8 -> inflate(decrypted) // DEFLATE
            else -> {
                EcosystemLogger.e(TAG, "Unsupported compression method: $actualCompressionMethod")
                decrypted
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal — crypto
    // ──────────────────────────────────────────────────────────────────────

    private fun deriveKey(password: CharArray, salt: ByteArray, keyLength: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, keyLength * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val key = factory.generateSecret(spec)
        return key.encoded
    }

    /**
     * WinZip AES-CTR decryption.
     * Counter starts at 1, stored in little-endian format in a 16-byte IV block.
     * Only the low 4 bytes are used as counter (little-endian).
     */
    private fun decryptAesCtr(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))

        val result = ByteArray(data.size)
        val blockSize = 16
        var counter = 1
        var offset = 0

        while (offset < data.size) {
            // Build counter block (16 bytes, little-endian counter in first 4 bytes, rest zero)
            val counterBlock = ByteArray(blockSize)
            counterBlock[0] = (counter and 0xFF).toByte()
            counterBlock[1] = ((counter shr 8) and 0xFF).toByte()
            counterBlock[2] = ((counter shr 16) and 0xFF).toByte()
            counterBlock[3] = ((counter shr 24) and 0xFF).toByte()

            val keystreamBlock = cipher.doFinal(counterBlock)

            val bytesToXor = minOf(blockSize, data.size - offset)
            for (i in 0 until bytesToXor) {
                result[offset + i] = (data[offset + i].toInt() xor keystreamBlock[i].toInt()).toByte()
            }

            offset += blockSize
            counter++
        }
        return result
    }

    /**
     * AES-CTR encryption (same as decryption — XOR is symmetric).
     */
    private fun encryptAesCtr(data: ByteArray, key: ByteArray): ByteArray {
        return decryptAesCtr(data, key) // CTR mode XOR is symmetric
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater(true) // raw inflate (no zlib header)
        inflater.setInput(data)
        val baos = ByteArrayOutputStream(data.size * 2)
        val buf = ByteArray(8192)
        while (!inflater.finished()) {
            val count = inflater.inflate(buf)
            if (count == 0 && inflater.needsInput()) break
            baos.write(buf, 0, count)
        }
        inflater.end()
        return baos.toByteArray()
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true) // raw deflate
        deflater.setInput(data)
        deflater.finish()
        val baos = ByteArrayOutputStream(data.size)
        val buf = ByteArray(8192)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            baos.write(buf, 0, count)
        }
        deflater.end()
        return baos.toByteArray()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal — writing
    // ──────────────────────────────────────────────────────────────────────

    private data class CentralDirRecord(
        val name: String,
        val localHeaderOffset: Long,
        val localHeaderSize: Int,
        val encryptedDataSize: Int,
        val crc32: Long,
        val uncompressedSize: Long,
        val compressedSize: Long, // total = salt + verifier + encrypted + hmac
        val modTime: Int,
        val modDate: Int,
        val isDirectory: Boolean,
        val extraField: ByteArray
    )

    private fun writeEncryptedEntry(
        out: OutputStream,
        srcFile: File,
        zipPath: String,
        password: CharArray,
        currentOffset: Long
    ): CentralDirRecord {
        val rawData = srcFile.readBytes()
        val crc = CRC32()
        crc.update(rawData)
        val crc32Value = crc.value

        // Compress
        val compressed = deflate(rawData)

        // Generate salt
        val salt = ByteArray(SALT_LENGTH)
        java.security.SecureRandom().nextBytes(salt)

        // Derive keys
        val derived = deriveKey(password, salt, DERIVED_KEY_LENGTH)
        val aesKey = derived.copyOfRange(0, KEY_LENGTH)
        val hmacKey = derived.copyOfRange(KEY_LENGTH, KEY_LENGTH * 2)
        val verifier = derived.copyOfRange(KEY_LENGTH * 2, KEY_LENGTH * 2 + PASSWORD_VERIFIER_LENGTH)

        // Encrypt
        val encrypted = encryptAesCtr(compressed, aesKey)

        // HMAC
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA1"))
        val hmacFull = mac.doFinal(encrypted)
        val hmac10 = hmacFull.copyOfRange(0, HMAC_LENGTH)

        // Total data: salt + verifier + encrypted + hmac
        val totalDataSize = SALT_LENGTH + PASSWORD_VERIFIER_LENGTH + encrypted.size + HMAC_LENGTH

        // Build AES extra field (0x9901)
        val extraField = buildAesExtraField()

        // Build local file header
        val nameBytes = zipPath.toByteArray(Charsets.UTF_8)
        val now = epochToDosDateTime(System.currentTimeMillis())
        val modTime = now.first
        val modDate = now.second

        val localHeader = ByteBuffer.allocate(30 + nameBytes.size + extraField.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        localHeader.putInt(0x04034b50) // signature
        localHeader.putShort(51) // version needed (5.1 for AES)
        localHeader.putShort(1) // general purpose bit flag (encrypted)
        localHeader.putShort(99) // compression method (AES)
        localHeader.putShort(modTime.toShort())
        localHeader.putShort(modDate.toShort())
        localHeader.putInt(crc32Value.toInt()) // CRC-32
        localHeader.putInt(totalDataSize) // compressed size
        localHeader.putInt(rawData.size) // uncompressed size
        localHeader.putShort(nameBytes.size.toShort())
        localHeader.putShort(extraField.size.toShort())
        localHeader.put(nameBytes)
        localHeader.put(extraField)

        val headerBytes = localHeader.array()
        out.write(headerBytes)

        // Write data
        out.write(salt)
        out.write(verifier)
        out.write(encrypted)
        out.write(hmac10)

        return CentralDirRecord(
            name = zipPath,
            localHeaderOffset = currentOffset,
            localHeaderSize = headerBytes.size,
            encryptedDataSize = totalDataSize,
            crc32 = crc32Value,
            uncompressedSize = rawData.size.toLong(),
            compressedSize = totalDataSize.toLong(),
            modTime = modTime,
            modDate = modDate,
            isDirectory = false,
            extraField = extraField
        )
    }

    private fun buildAesExtraField(): ByteArray {
        val buf = ByteBuffer.allocate(4 + AES_EXTRA_FIELD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(AES_EXTRA_FIELD_ID.toShort()) // header ID
        buf.putShort(AES_EXTRA_FIELD_SIZE.toShort()) // data size
        buf.putShort(2) // AE-2 vendor version
        buf.put('A'.code.toByte()) // vendor ID
        buf.put('E'.code.toByte())
        buf.put(3) // AES key strength: 3 = AES-256
        buf.putShort(8) // actual compression method: DEFLATE
        return buf.array()
    }

    private fun buildCentralDirEntry(record: CentralDirRecord): ByteArray {
        val nameBytes = record.name.toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(46 + nameBytes.size + record.extraField.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0x02014b50) // central dir signature
        buf.putShort(51) // version made by (5.1)
        buf.putShort(51) // version needed (5.1)
        buf.putShort(1) // general purpose bit flag (encrypted)
        buf.putShort(99) // compression method (AES)
        buf.putShort(record.modTime.toShort())
        buf.putShort(record.modDate.toShort())
        buf.putInt(record.crc32.toInt())
        buf.putInt(record.compressedSize.toInt())
        buf.putInt(record.uncompressedSize.toInt())
        buf.putShort(nameBytes.size.toShort())
        buf.putShort(record.extraField.size.toShort())
        buf.putShort(0) // comment length
        buf.putShort(0) // disk number start
        buf.putShort(0) // internal file attributes
        buf.putInt(0) // external file attributes
        buf.putInt(record.localHeaderOffset.toInt()) // relative offset
        buf.put(nameBytes)
        buf.put(record.extraField)
        return buf.array()
    }

    private fun buildEocd(entryCount: Int, centralDirSize: Int, centralDirOffset: Int): ByteArray {
        val buf = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0x06054b50) // signature
        buf.putShort(0) // disk number
        buf.putShort(0) // disk with central dir
        buf.putShort(entryCount.toShort()) // entries on this disk
        buf.putShort(entryCount.toShort()) // total entries
        buf.putInt(centralDirSize) // central dir size
        buf.putInt(centralDirOffset) // central dir offset
        buf.putShort(0) // comment length
        return buf.array()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal — split ZIP
    // ──────────────────────────────────────────────────────────────────────

    private fun createEncryptedSplitZip(
        outputPath: String,
        files: List<Pair<File, String>>,
        password: CharArray,
        splitSizeBytes: Long,
        onProgress: ((Int, Int, String) -> Unit)?
    ) {
        // Build complete ZIP in temp file, then split
        val tempFile = File(outputPath + ".tmp")
        try {
            createEncryptedZip(tempFile.absolutePath, files, password, onProgress)
            splitFile(tempFile, outputPath, splitSizeBytes)
        } finally {
            tempFile.delete()
        }
    }

    private fun createUnencryptedSplitZip(
        outputPath: String,
        files: List<Pair<File, String>>,
        splitSizeBytes: Long,
        onProgress: ((Int, Int, String) -> Unit)?
    ) {
        val tempFile = File(outputPath + ".tmp")
        try {
            val total = files.size
            ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                files.forEachIndexed { index, (file, zipPath) ->
                    onProgress?.invoke(index, total, file.name)
                    val entry = ZipEntry(zipPath)
                    entry.time = file.lastModified()
                    zos.putNextEntry(entry)
                    FileInputStream(file).use { fis -> fis.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            splitFile(tempFile, outputPath, splitSizeBytes)
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Split a file into WinZip-compatible split volumes.
     * Output: .z01, .z02, ... .zip (last part)
     */
    private fun splitFile(source: File, outputPath: String, splitSizeBytes: Long) {
        val baseName = outputPath.substringBeforeLast(".")
        val totalSize = source.length()

        if (totalSize <= splitSizeBytes) {
            // No need to split — just rename
            source.copyTo(File(outputPath), overwrite = true)
            return
        }

        FileInputStream(source).use { fis ->
            var partNumber = 1
            var remaining = totalSize
            val buf = ByteArray(65536)

            while (remaining > 0) {
                val isLastPart = remaining <= splitSizeBytes
                val partPath = if (isLastPart) {
                    outputPath // .zip is the last part
                } else {
                    "$baseName.z${partNumber.toString().padStart(2, '0')}"
                }

                val partSize = if (isLastPart) remaining else splitSizeBytes
                FileOutputStream(partPath).use { fos ->
                    var written = 0L
                    while (written < partSize) {
                        val toRead = minOf(buf.size.toLong(), partSize - written).toInt()
                        val read = fis.read(buf, 0, toRead)
                        if (read == -1) break
                        fos.write(buf, 0, read)
                        written += read
                    }
                }
                remaining -= partSize
                if (!isLastPart) partNumber++
            }
            EcosystemLogger.d(TAG, "splitFile: created $partNumber volumes from ${source.name}")
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal — date conversion
    // ──────────────────────────────────────────────────────────────────────

    private fun dosDateTimeToEpoch(date: Int, time: Int): Long {
        val year = ((date shr 9) and 0x7F) + 1980
        val month = (date shr 5) and 0x0F
        val day = date and 0x1F
        val hour = (time shr 11) and 0x1F
        val minute = (time shr 5) and 0x3F
        val second = (time and 0x1F) * 2

        return try {
            val cal = java.util.Calendar.getInstance()
            cal.set(year, month - 1, day, hour, minute, second)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (_: Exception) {
            0L
        }
    }

    private fun epochToDosDateTime(epoch: Long): Pair<Int, Int> {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = epoch
        val year = cal.get(java.util.Calendar.YEAR)
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = cal.get(java.util.Calendar.MINUTE)
        val second = cal.get(java.util.Calendar.SECOND)

        val dosTime = (hour shl 11) or (minute shl 5) or (second / 2)
        val dosDate = ((year - 1980) shl 9) or (month shl 5) or day
        return Pair(dosTime, dosDate)
    }
}
