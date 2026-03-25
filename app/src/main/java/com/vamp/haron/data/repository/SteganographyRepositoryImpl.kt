package com.vamp.haron.data.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.StegoDetectResult
import com.vamp.haron.domain.model.StegoPhase
import com.vamp.haron.domain.model.StegoProgress
import com.vamp.haron.domain.model.StegoResult
import com.vamp.haron.domain.repository.SteganographyRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Steganography implementation using tail-append method.
 *
 * Format:
 * [original carrier file]
 * [HEADER: magic(7) + version(1) + nameLen(2) + name(N) + payloadSize(8) + IV(12)]
 * [encrypted payload data]
 * [headerOffset(8 bytes)]
 * [FOOTER: "HRNSTEG!" (8 bytes)]
 */
/**
 * Steganography: hide/extract files inside PNG images using LSB encoding.
 * Payload stored in least significant bits of pixel channels.
 * Header: magic bytes + original filename + file size.
 */
@Singleton
class SteganographyRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SteganographyRepository {

    companion object {
        private const val TAG = "Stego"
        private const val MAGIC = "HRNSTEG"  // 7 bytes
        private const val FOOTER = "HRNSTEG!" // 8 bytes
        private const val VERSION: Byte = 1
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val STEGO_KEYSTORE_ALIAS = "haron_stego_key"
        private const val BUFFER_SIZE = 8192
    }

    override fun hidePayload(
        carrierPath: String,
        payloadPath: String,
        outputPath: String
    ): Flow<StegoProgress> = flow {
        emit(StegoProgress(StegoPhase.COPYING_CARRIER, 0f))

        val carrier = File(carrierPath)
        val payload = File(payloadPath)
        val output = File(outputPath)

        // Step 1: Copy carrier to output
        carrier.inputStream().use { inp ->
            output.outputStream().use { out ->
                inp.copyTo(out, BUFFER_SIZE)
            }
        }
        emit(StegoProgress(StegoPhase.COPYING_CARRIER, 1f))

        // Step 2: Encrypt payload
        emit(StegoProgress(StegoPhase.ENCRYPTING, 0f))
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv

        val encryptedFile = File(context.cacheDir, "stego_enc_${System.currentTimeMillis()}")
        try {
            FileInputStream(payload).use { payloadIn ->
                FileOutputStream(encryptedFile).use { encOut ->
                    javax.crypto.CipherOutputStream(encOut, cipher).use { cipherOut ->
                        val buf = ByteArray(BUFFER_SIZE)
                        var total = 0L
                        var read: Int
                        while (payloadIn.read(buf).also { read = it } != -1) {
                            cipherOut.write(buf, 0, read)
                            total += read
                            emit(StegoProgress(StegoPhase.ENCRYPTING, (total.toFloat() / payload.length()).coerceAtMost(1f)))
                        }
                    }
                }
            }

            // Step 3: Append header + encrypted data + offset + footer
            emit(StegoProgress(StegoPhase.APPENDING, 0f))
            val payloadName = payload.name
            val nameBytes = payloadName.toByteArray(Charsets.UTF_8)
            val headerOffset = output.length()

            FileOutputStream(output, true).use { fos ->
                // Header
                fos.write(MAGIC.toByteArray(Charsets.US_ASCII)) // 7
                fos.write(byteArrayOf(VERSION))                  // 1
                fos.write(ByteBuffer.allocate(2).putShort(nameBytes.size.toShort()).array()) // 2
                fos.write(nameBytes)                             // N
                fos.write(ByteBuffer.allocate(8).putLong(encryptedFile.length()).array())    // 8
                fos.write(iv)                                    // 12

                // Encrypted payload
                FileInputStream(encryptedFile).use { encIn ->
                    val buf = ByteArray(BUFFER_SIZE)
                    var total = 0L
                    var read: Int
                    while (encIn.read(buf).also { read = it } != -1) {
                        fos.write(buf, 0, read)
                        total += read
                        emit(StegoProgress(StegoPhase.APPENDING, (total.toFloat() / encryptedFile.length()).coerceAtMost(1f)))
                    }
                }

                // Header offset (8 bytes)
                fos.write(ByteBuffer.allocate(8).putLong(headerOffset).array())
                // Footer
                fos.write(FOOTER.toByteArray(Charsets.US_ASCII))
            }

            emit(StegoProgress(StegoPhase.DONE, 1f))
        } finally {
            encryptedFile.delete()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun hidePayloadComplete(
        carrierPath: String,
        payloadPath: String,
        outputPath: String
    ): StegoResult = withContext(Dispatchers.IO) {
        try {
            var lastProgress = StegoProgress()
            hidePayload(carrierPath, payloadPath, outputPath).collect { lastProgress = it }
            if (lastProgress.phase == StegoPhase.DONE) {
                StegoResult.Hidden(outputPath, File(payloadPath).length())
            } else {
                StegoResult.Error("Failed at phase: ${lastProgress.phase}")
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "$TAG hide error: ${e.message}")
            StegoResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun detectHiddenData(filePath: String): StegoDetectResult = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.length() < 24) return@withContext StegoDetectResult(false)

            RandomAccessFile(file, "r").use { raf ->
                // Read footer
                raf.seek(file.length() - 8)
                val footerBytes = ByteArray(8)
                raf.readFully(footerBytes)
                val footer = String(footerBytes, Charsets.US_ASCII)
                if (footer != FOOTER) return@withContext StegoDetectResult(false)

                // Read header offset
                raf.seek(file.length() - 16)
                val offsetBytes = ByteArray(8)
                raf.readFully(offsetBytes)
                val headerOffset = ByteBuffer.wrap(offsetBytes).long

                if (headerOffset < 0 || headerOffset >= file.length()) {
                    return@withContext StegoDetectResult(false)
                }

                // Read header
                raf.seek(headerOffset)
                val magicBytes = ByteArray(7)
                raf.readFully(magicBytes)
                val magic = String(magicBytes, Charsets.US_ASCII)
                if (magic != MAGIC) return@withContext StegoDetectResult(false)

                val version = raf.readByte()
                val nameLenBytes = ByteArray(2)
                raf.readFully(nameLenBytes)
                val nameLen = ByteBuffer.wrap(nameLenBytes).short.toInt()
                val nameBytes = ByteArray(nameLen)
                raf.readFully(nameBytes)
                val payloadName = String(nameBytes, Charsets.UTF_8)

                val sizeBuf = ByteArray(8)
                raf.readFully(sizeBuf)
                val payloadSize = ByteBuffer.wrap(sizeBuf).long

                StegoDetectResult(
                    hasHiddenData = true,
                    payloadName = payloadName,
                    payloadSize = payloadSize
                )
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "$TAG detect error: ${e.message}")
            StegoDetectResult(false)
        }
    }

    override suspend fun extractPayload(filePath: String, outputDir: String): StegoResult = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            val raf = RandomAccessFile(file, "r")

            // Read footer
            raf.seek(file.length() - 8)
            val footerBytes = ByteArray(8)
            raf.readFully(footerBytes)
            if (String(footerBytes, Charsets.US_ASCII) != FOOTER) {
                raf.close()
                return@withContext StegoResult.Error("No hidden data found")
            }

            // Read header offset
            raf.seek(file.length() - 16)
            val offsetBytes = ByteArray(8)
            raf.readFully(offsetBytes)
            val headerOffset = ByteBuffer.wrap(offsetBytes).long

            // Read header
            raf.seek(headerOffset)
            val magicBytes = ByteArray(7)
            raf.readFully(magicBytes)
            if (String(magicBytes, Charsets.US_ASCII) != MAGIC) {
                raf.close()
                return@withContext StegoResult.Error("Invalid header")
            }

            val version = raf.readByte()
            val nameLenBytes = ByteArray(2)
            raf.readFully(nameLenBytes)
            val nameLen = ByteBuffer.wrap(nameLenBytes).short.toInt()
            val nameBytes = ByteArray(nameLen)
            raf.readFully(nameBytes)
            val payloadName = String(nameBytes, Charsets.UTF_8)

            val sizeBuf = ByteArray(8)
            raf.readFully(sizeBuf)
            val encryptedSize = ByteBuffer.wrap(sizeBuf).long

            val iv = ByteArray(GCM_IV_LENGTH)
            raf.readFully(iv)

            // Read encrypted data
            val dataStart = raf.filePointer
            val encryptedData = ByteArray(encryptedSize.toInt())
            raf.readFully(encryptedData)
            raf.close()

            // Decrypt
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            val outDir = File(outputDir)
            outDir.mkdirs()
            var outFile = File(outDir, payloadName)
            if (outFile.exists()) {
                val base = payloadName.substringBeforeLast('.')
                val ext = payloadName.substringAfterLast('.', "")
                var counter = 1
                while (outFile.exists()) {
                    val name = if (ext.isNotEmpty()) "${base}_($counter).$ext" else "${base}_($counter)"
                    outFile = File(outDir, name)
                    counter++
                }
            }

            javax.crypto.CipherInputStream(encryptedData.inputStream(), cipher).use { cis ->
                outFile.outputStream().use { fos ->
                    cis.copyTo(fos, BUFFER_SIZE)
                }
            }

            StegoResult.Extracted(outFile.absolutePath, payloadName)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "$TAG extract error: ${e.message}")
            StegoResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val existingKey = keyStore.getKey(STEGO_KEYSTORE_ALIAS, null)
        if (existingKey != null) return existingKey as SecretKey

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGen.init(
            KeyGenParameterSpec.Builder(
                STEGO_KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGen.generateKey()
    }
}
