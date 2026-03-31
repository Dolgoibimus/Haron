package com.vamp.haron.common.crypto

import java.io.File
import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Streaming AES-256-GCM encryption in 1MB segments.
 * Each segment has its own 12-byte IV (derived from segment counter) and 16-byte GCM tag.
 * This avoids OOM on large files (standard CipherInputStream buffers entire file for GCM MAC).
 *
 * File format:
 * [1 byte: version = 0x01]
 * [12 bytes: base nonce (random)]
 * [Segment_0: 4b size + encrypted_chunk + 16b GCM tag]
 * [Segment_1: 4b size + encrypted_chunk + 16b GCM tag]
 * ...
 * Last segment is shorter (remaining bytes).
 *
 * Each segment uses nonce = baseNonce XOR segmentIndex (as 4-byte BE in last 4 bytes of nonce).
 */
object StreamingCipher {

    private const val VERSION: Byte = 0x01
    private const val NONCE_SIZE = 12
    private const val TAG_SIZE = 128 // bits
    private const val SEGMENT_SIZE = 1024 * 1024 // 1MB plaintext per segment
    private const val ALGORITHM = "AES/GCM/NoPadding"

    fun encrypt(key: SecretKey, src: File, dest: File) {
        val baseNonce = ByteArray(NONCE_SIZE)
        SecureRandom().nextBytes(baseNonce)

        dest.outputStream().buffered().use { out ->
            out.write(VERSION.toInt())
            out.write(baseNonce)

            src.inputStream().buffered().use { input ->
                val buffer = ByteArray(SEGMENT_SIZE)
                var segmentIndex = 0

                while (true) {
                    val bytesRead = readFully(input, buffer)
                    if (bytesRead <= 0) break

                    val nonce = deriveNonce(baseNonce, segmentIndex)
                    val cipher = Cipher.getInstance(ALGORITHM)
                    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, nonce))

                    val encrypted = cipher.doFinal(buffer, 0, bytesRead)
                    // Write segment size (4 bytes) + encrypted data (includes GCM tag)
                    out.write(intToBytes(encrypted.size))
                    out.write(encrypted)

                    segmentIndex++
                }
            }
        }
    }

    fun decrypt(key: SecretKey, enc: File, dest: File) {
        enc.inputStream().buffered().use { input ->
            val version = input.read()
            if (version != VERSION.toInt()) throw SecurityException("Unknown encryption version: $version")

            val baseNonce = ByteArray(NONCE_SIZE)
            if (input.read(baseNonce) != NONCE_SIZE) throw SecurityException("Truncated nonce")

            dest.outputStream().buffered().use { out ->
                var segmentIndex = 0
                val sizeBytes = ByteArray(4)

                while (true) {
                    val sizeRead = readFully(input, sizeBytes)
                    if (sizeRead < 4) break // EOF

                    val encSize = bytesToInt(sizeBytes)
                    if (encSize <= 0 || encSize > SEGMENT_SIZE + 16 + 64) {
                        throw SecurityException("Invalid segment size: $encSize")
                    }

                    val encData = ByteArray(encSize)
                    val dataRead = readFully(input, encData)
                    if (dataRead != encSize) throw SecurityException("Truncated segment $segmentIndex")

                    val nonce = deriveNonce(baseNonce, segmentIndex)
                    val cipher = Cipher.getInstance(ALGORITHM)
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, nonce))

                    val plain = cipher.doFinal(encData)
                    out.write(plain)

                    segmentIndex++
                }
            }
        }
    }

    /** Decrypt file to ByteArray in-memory (for thumbnails — no temp file on disk) */
    fun decryptToByteArray(key: SecretKey, enc: File): ByteArray {
        enc.inputStream().buffered().use { input ->
            val version = input.read()
            if (version != VERSION.toInt()) throw SecurityException("Unknown encryption version: $version")

            val baseNonce = ByteArray(NONCE_SIZE)
            if (input.read(baseNonce) != NONCE_SIZE) throw SecurityException("Truncated nonce")

            val out = java.io.ByteArrayOutputStream()
            var segmentIndex = 0
            val sizeBytes = ByteArray(4)

            while (true) {
                val sizeRead = readFully(input, sizeBytes)
                if (sizeRead < 4) break

                val encSize = bytesToInt(sizeBytes)
                if (encSize <= 0 || encSize > SEGMENT_SIZE + 16 + 64) {
                    throw SecurityException("Invalid segment size: $encSize")
                }

                val encData = ByteArray(encSize)
                val dataRead = readFully(input, encData)
                if (dataRead != encSize) throw SecurityException("Truncated segment $segmentIndex")

                val nonce = deriveNonce(baseNonce, segmentIndex)
                val cipher = Cipher.getInstance(ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, nonce))

                out.write(cipher.doFinal(encData))
                segmentIndex++
            }
            return out.toByteArray()
        }
    }

    /** Encrypt small data (e.g. index JSON) — single AES-GCM operation, no streaming needed */
    fun encryptBytes(key: SecretKey, data: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_SIZE)
        SecureRandom().nextBytes(nonce)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, nonce))
        val encrypted = cipher.doFinal(data)
        // nonce + encrypted (includes GCM tag)
        return nonce + encrypted
    }

    /** Decrypt small data */
    fun decryptBytes(key: SecretKey, data: ByteArray): ByteArray {
        if (data.size < NONCE_SIZE + 16) throw SecurityException("Data too short")
        val nonce = data.copyOfRange(0, NONCE_SIZE)
        val encrypted = data.copyOfRange(NONCE_SIZE, data.size)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, nonce))
        return cipher.doFinal(encrypted)
    }

    private fun deriveNonce(baseNonce: ByteArray, segmentIndex: Int): ByteArray {
        val nonce = baseNonce.copyOf()
        // XOR last 4 bytes with segment index (big-endian)
        val idxBytes = intToBytes(segmentIndex)
        for (i in 0..3) {
            nonce[NONCE_SIZE - 4 + i] = (nonce[NONCE_SIZE - 4 + i].toInt() xor idxBytes[i].toInt()).toByte()
        }
        return nonce
    }

    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte()
        )
    }

    private fun bytesToInt(bytes: ByteArray): Int {
        return (bytes[0].toInt() and 0xFF shl 24) or
               (bytes[1].toInt() and 0xFF shl 16) or
               (bytes[2].toInt() and 0xFF shl 8) or
               (bytes[3].toInt() and 0xFF)
    }

    /** Read exactly buffer.size bytes (or less at EOF) */
    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = input.read(buffer, total, buffer.size - total)
            if (n == -1) break
            total += n
        }
        return total
    }
}
