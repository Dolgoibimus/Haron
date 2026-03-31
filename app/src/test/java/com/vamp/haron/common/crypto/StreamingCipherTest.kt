package com.vamp.haron.common.crypto

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class StreamingCipherTest {

    private lateinit var tempDir: File
    private lateinit var key: SecretKey

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "streaming_cipher_test_${System.nanoTime()}")
        tempDir.mkdirs()

        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        key = keyGen.generateKey()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // ── File round-trip: encrypt → decrypt ──────────────────────────────

    @Test
    fun `empty file round-trip`() {
        val src = tempDir.resolve("empty.bin").apply { createNewFile() }
        val enc = tempDir.resolve("empty.enc")
        val dec = tempDir.resolve("empty.dec")

        StreamingCipher.encrypt(key, src, enc)
        assertTrue("Encrypted file should exist", enc.exists())
        // Encrypted file has at least version(1) + nonce(12) = 13 bytes
        assertTrue("Encrypted file should have header", enc.length() >= 13)

        StreamingCipher.decrypt(key, enc, dec)
        assertEquals(0L, dec.length())
    }

    @Test
    fun `small file round-trip (100 bytes)`() {
        val data = ByteArray(100) { it.toByte() }
        val src = tempDir.resolve("small.bin").apply { writeBytes(data) }
        val enc = tempDir.resolve("small.enc")
        val dec = tempDir.resolve("small.dec")

        StreamingCipher.encrypt(key, src, enc)
        StreamingCipher.decrypt(key, enc, dec)

        assertArrayEquals(data, dec.readBytes())
    }

    @Test
    fun `exact 1MB file round-trip (segment boundary)`() {
        val size = 1024 * 1024
        val data = ByteArray(size) { (it % 256).toByte() }
        val src = tempDir.resolve("exact1mb.bin").apply { writeBytes(data) }
        val enc = tempDir.resolve("exact1mb.enc")
        val dec = tempDir.resolve("exact1mb.dec")

        StreamingCipher.encrypt(key, src, enc)
        StreamingCipher.decrypt(key, enc, dec)

        assertArrayEquals(data, dec.readBytes())
    }

    @Test
    fun `multi-segment file round-trip (2_5 MB)`() {
        val size = (2.5 * 1024 * 1024).toInt()
        val data = ByteArray(size) { (it % 251).toByte() }
        val src = tempDir.resolve("multi.bin").apply { writeBytes(data) }
        val enc = tempDir.resolve("multi.enc")
        val dec = tempDir.resolve("multi.dec")

        StreamingCipher.encrypt(key, src, enc)
        StreamingCipher.decrypt(key, enc, dec)

        assertArrayEquals(data, dec.readBytes())
    }

    @Test
    fun `1 byte file round-trip`() {
        val data = byteArrayOf(0x42)
        val src = tempDir.resolve("one.bin").apply { writeBytes(data) }
        val enc = tempDir.resolve("one.enc")
        val dec = tempDir.resolve("one.dec")

        StreamingCipher.encrypt(key, src, enc)
        StreamingCipher.decrypt(key, enc, dec)

        assertArrayEquals(data, dec.readBytes())
    }

    @Test
    fun `1MB minus 1 byte file round-trip`() {
        val size = 1024 * 1024 - 1
        val data = ByteArray(size) { (it % 200).toByte() }
        val src = tempDir.resolve("almost1mb.bin").apply { writeBytes(data) }
        val enc = tempDir.resolve("almost1mb.enc")
        val dec = tempDir.resolve("almost1mb.dec")

        StreamingCipher.encrypt(key, src, enc)
        StreamingCipher.decrypt(key, enc, dec)

        assertArrayEquals(data, dec.readBytes())
    }

    @Test
    fun `1MB plus 1 byte file round-trip`() {
        val size = 1024 * 1024 + 1
        val data = ByteArray(size) { (it % 173).toByte() }
        val src = tempDir.resolve("over1mb.bin").apply { writeBytes(data) }
        val enc = tempDir.resolve("over1mb.enc")
        val dec = tempDir.resolve("over1mb.dec")

        StreamingCipher.encrypt(key, src, enc)
        StreamingCipher.decrypt(key, enc, dec)

        assertArrayEquals(data, dec.readBytes())
    }

    // ── decryptToByteArray ──────────────────────────────────────────────

    @Test
    fun `decryptToByteArray returns same content as original`() {
        val data = ByteArray(500) { (it * 7 % 256).toByte() }
        val src = tempDir.resolve("bytes.bin").apply { writeBytes(data) }
        val enc = tempDir.resolve("bytes.enc")

        StreamingCipher.encrypt(key, src, enc)
        val result = StreamingCipher.decryptToByteArray(key, enc)

        assertArrayEquals(data, result)
    }

    @Test
    fun `decryptToByteArray on empty encrypted file`() {
        val src = tempDir.resolve("empty2.bin").apply { createNewFile() }
        val enc = tempDir.resolve("empty2.enc")

        StreamingCipher.encrypt(key, src, enc)
        val result = StreamingCipher.decryptToByteArray(key, enc)

        assertEquals(0, result.size)
    }

    @Test
    fun `decryptToByteArray multi-segment`() {
        val size = 3 * 1024 * 1024
        val data = ByteArray(size) { (it % 127).toByte() }
        val src = tempDir.resolve("multi2.bin").apply { writeBytes(data) }
        val enc = tempDir.resolve("multi2.enc")

        StreamingCipher.encrypt(key, src, enc)
        val result = StreamingCipher.decryptToByteArray(key, enc)

        assertArrayEquals(data, result)
    }

    // ── encryptBytes / decryptBytes ─────────────────────────────────────

    @Test
    fun `encryptBytes decryptBytes round-trip`() {
        val data = "Hello, AES-GCM!".toByteArray()
        val encrypted = StreamingCipher.encryptBytes(key, data)
        val decrypted = StreamingCipher.decryptBytes(key, encrypted)

        assertArrayEquals(data, decrypted)
    }

    @Test
    fun `encryptBytes decryptBytes empty data`() {
        val data = ByteArray(0)
        val encrypted = StreamingCipher.encryptBytes(key, data)
        val decrypted = StreamingCipher.decryptBytes(key, encrypted)

        assertArrayEquals(data, decrypted)
    }

    @Test
    fun `encryptBytes produces different ciphertext each time (random nonce)`() {
        val data = "same data".toByteArray()
        val enc1 = StreamingCipher.encryptBytes(key, data)
        val enc2 = StreamingCipher.encryptBytes(key, data)

        // Both should decrypt to the same data
        assertArrayEquals(data, StreamingCipher.decryptBytes(key, enc1))
        assertArrayEquals(data, StreamingCipher.decryptBytes(key, enc2))

        // But ciphertexts should differ (different random nonces)
        assertTrue(
            "Encrypted outputs should differ due to random nonce",
            !enc1.contentEquals(enc2)
        )
    }

    @Test
    fun `encryptBytes large payload`() {
        val data = ByteArray(100_000) { (it % 256).toByte() }
        val encrypted = StreamingCipher.encryptBytes(key, data)
        val decrypted = StreamingCipher.decryptBytes(key, encrypted)

        assertArrayEquals(data, decrypted)
    }

    // ── Wrong key → decryption failure ──────────────────────────────────

    @Test(expected = Exception::class)
    fun `decrypt file with wrong key throws exception`() {
        val data = ByteArray(256) { it.toByte() }
        val src = tempDir.resolve("wrongkey.bin").apply { writeBytes(data) }
        val enc = tempDir.resolve("wrongkey.enc")
        val dec = tempDir.resolve("wrongkey.dec")

        StreamingCipher.encrypt(key, src, enc)

        val wrongKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        StreamingCipher.decrypt(wrongKey, enc, dec)
    }

    @Test(expected = Exception::class)
    fun `decryptBytes with wrong key throws exception`() {
        val data = "secret".toByteArray()
        val encrypted = StreamingCipher.encryptBytes(key, data)

        val wrongKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        StreamingCipher.decryptBytes(wrongKey, encrypted)
    }

    @Test(expected = Exception::class)
    fun `decryptToByteArray with wrong key throws exception`() {
        val data = ByteArray(100) { 0xAB.toByte() }
        val src = tempDir.resolve("wrongkey2.bin").apply { writeBytes(data) }
        val enc = tempDir.resolve("wrongkey2.enc")

        StreamingCipher.encrypt(key, src, enc)

        val wrongKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        StreamingCipher.decryptToByteArray(wrongKey, enc)
    }

    // ── Corrupted data ──────────────────────────────────────────────────

    @Test(expected = SecurityException::class)
    fun `corrupted version byte throws SecurityException`() {
        val data = ByteArray(50) { it.toByte() }
        val src = tempDir.resolve("corrupt_ver.bin").apply { writeBytes(data) }
        val enc = tempDir.resolve("corrupt_ver.enc")
        val dec = tempDir.resolve("corrupt_ver.dec")

        StreamingCipher.encrypt(key, src, enc)

        // Corrupt version byte (first byte)
        val encBytes = enc.readBytes()
        encBytes[0] = 0xFF.toByte()
        enc.writeBytes(encBytes)

        StreamingCipher.decrypt(key, enc, dec)
    }

    @Test(expected = Exception::class)
    fun `corrupted ciphertext throws exception`() {
        val data = ByteArray(500) { it.toByte() }
        val src = tempDir.resolve("corrupt_data.bin").apply { writeBytes(data) }
        val enc = tempDir.resolve("corrupt_data.enc")
        val dec = tempDir.resolve("corrupt_data.dec")

        StreamingCipher.encrypt(key, src, enc)

        // Corrupt a byte in the middle of ciphertext
        val encBytes = enc.readBytes()
        val mid = encBytes.size / 2
        encBytes[mid] = (encBytes[mid].toInt() xor 0xFF).toByte()
        enc.writeBytes(encBytes)

        StreamingCipher.decrypt(key, enc, dec)
    }

    @Test(expected = SecurityException::class)
    fun `truncated nonce throws SecurityException`() {
        // Create a file with just version byte and partial nonce
        val enc = tempDir.resolve("truncated.enc")
        enc.writeBytes(byteArrayOf(0x01, 0x00, 0x00)) // version + 2 bytes of nonce
        val dec = tempDir.resolve("truncated.dec")

        StreamingCipher.decrypt(key, enc, dec)
    }

    @Test(expected = SecurityException::class)
    fun `decryptBytes with data too short throws SecurityException`() {
        StreamingCipher.decryptBytes(key, ByteArray(10)) // < NONCE_SIZE(12) + 16
    }

    // ── Encrypted file is larger than source ────────────────────────────

    @Test
    fun `encrypted file is larger than source due to overhead`() {
        val data = ByteArray(100) { it.toByte() }
        val src = tempDir.resolve("overhead.bin").apply { writeBytes(data) }
        val enc = tempDir.resolve("overhead.enc")

        StreamingCipher.encrypt(key, src, enc)

        // Overhead: version(1) + nonce(12) + segmentSize(4) + GCMtag(16) = 33 minimum
        assertTrue(
            "Encrypted should be larger: src=${src.length()}, enc=${enc.length()}",
            enc.length() > src.length()
        )
    }

    // ── File encrypt produces different ciphertext each time ────────────

    @Test
    fun `file encrypt produces different ciphertext each call (random base nonce)`() {
        val data = "deterministic input".toByteArray()
        val src = tempDir.resolve("nonce_test.bin").apply { writeBytes(data) }
        val enc1 = tempDir.resolve("nonce1.enc")
        val enc2 = tempDir.resolve("nonce2.enc")

        StreamingCipher.encrypt(key, src, enc1)
        StreamingCipher.encrypt(key, src, enc2)

        assertTrue(
            "Two encryptions of same file should differ",
            !enc1.readBytes().contentEquals(enc2.readBytes())
        )
    }
}
