package com.vamp.haron.common.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Tests for AesZipHelper.
 *
 * NOTE: EcosystemLogger uses android.util.Log internally. This works because
 * `testOptions { unitTests.isReturnDefaultValues = true }` is set in build.gradle.kts,
 * so android.util.Log methods return 0 instead of throwing.
 */
class AesZipHelperTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "aes_zip_test_${System.nanoTime()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // ── isZipEncrypted ──────────────────────────────────────────────────

    @Test
    fun `isZipEncrypted returns false for normal ZIP`() {
        val zipFile = createNormalZip("normal.zip", mapOf("hello.txt" to "Hello World"))
        assertFalse(AesZipHelper.isZipEncrypted(zipFile))
    }

    @Test
    fun `isZipEncrypted returns false for empty ZIP`() {
        val zipFile = tempDir.resolve("empty.zip")
        ZipOutputStream(zipFile.outputStream()).use { /* no entries */ }
        assertFalse(AesZipHelper.isZipEncrypted(zipFile))
    }

    @Test
    fun `isZipEncrypted returns false for multi-entry normal ZIP`() {
        val zipFile = createNormalZip("multi.zip", mapOf(
            "file1.txt" to "content1",
            "file2.txt" to "content2",
            "dir/file3.txt" to "content3"
        ))
        assertFalse(AesZipHelper.isZipEncrypted(zipFile))
    }

    @Test
    fun `isZipEncrypted returns false for non-ZIP file`() {
        val notZip = tempDir.resolve("notzip.dat")
        notZip.writeText("this is not a zip file")
        assertFalse(AesZipHelper.isZipEncrypted(notZip))
    }

    @Test
    fun `isZipEncrypted returns true for encrypted ZIP`() {
        // Create an encrypted ZIP using AesZipHelper itself, then verify detection
        val srcFile = tempDir.resolve("secret.txt").apply { writeText("secret data") }
        val encZip = tempDir.resolve("encrypted.zip")
        val password = "testpassword123".toCharArray()

        AesZipHelper.createEncryptedZip(
            encZip.absolutePath,
            listOf(srcFile to "secret.txt"),
            password
        )

        assertTrue(AesZipHelper.isZipEncrypted(encZip))
    }

    // ── Round-trip: create → list → extract ─────────────────────────────

    @Test
    fun `round-trip single file`() {
        val content = "Hello, encrypted ZIP!"
        val srcFile = tempDir.resolve("test.txt").apply { writeText(content) }
        val zipFile = tempDir.resolve("roundtrip.zip")
        val password = "password123".toCharArray()

        AesZipHelper.createEncryptedZip(
            zipFile.absolutePath,
            listOf(srcFile to "test.txt"),
            password
        )

        assertTrue(zipFile.exists())
        assertTrue(zipFile.length() > 0)

        // List entries
        val entries = AesZipHelper.listEncryptedZip(zipFile, password)
        assertEquals(1, entries.size)
        assertEquals("test.txt", entries[0].name)
        assertTrue(entries[0].isEncrypted)
        assertFalse(entries[0].isDirectory)

        // Extract and verify content
        val decrypted = AesZipHelper.getDecryptedEntryBytes(zipFile, "test.txt", password)
        assertNotNull(decrypted)
        assertEquals(content, String(decrypted!!))
    }

    @Test
    fun `round-trip multiple files`() {
        val files = mapOf(
            "file1.txt" to "Content of file 1",
            "file2.txt" to "Content of file 2",
            "subdir/file3.txt" to "Content of file 3"
        )

        val srcFiles = files.map { (name, content) ->
            val f = tempDir.resolve(name.replace("/", "_")).apply { writeText(content) }
            f to name
        }

        val zipFile = tempDir.resolve("multi_roundtrip.zip")
        val password = "multi123".toCharArray()

        AesZipHelper.createEncryptedZip(zipFile.absolutePath, srcFiles, password)

        val entries = AesZipHelper.listEncryptedZip(zipFile, password)
        assertEquals(3, entries.size)

        for ((name, expectedContent) in files) {
            val decrypted = AesZipHelper.getDecryptedEntryBytes(zipFile, name, password)
            assertNotNull("Entry $name should be extractable", decrypted)
            assertEquals("Content mismatch for $name", expectedContent, String(decrypted!!))
        }
    }

    @Test
    fun `round-trip binary data`() {
        val data = ByteArray(10_000) { (it % 256).toByte() }
        val srcFile = tempDir.resolve("binary.dat").apply { writeBytes(data) }
        val zipFile = tempDir.resolve("binary.zip")
        val password = "binpass".toCharArray()

        AesZipHelper.createEncryptedZip(
            zipFile.absolutePath,
            listOf(srcFile to "binary.dat"),
            password
        )

        val decrypted = AesZipHelper.getDecryptedEntryBytes(zipFile, "binary.dat", password)
        assertNotNull(decrypted)
        assertTrue("Binary content should match", data.contentEquals(decrypted!!))
    }

    @Test
    fun `round-trip empty file`() {
        val srcFile = tempDir.resolve("empty.txt").apply { createNewFile() }
        val zipFile = tempDir.resolve("empty_entry.zip")
        val password = "emptypass".toCharArray()

        AesZipHelper.createEncryptedZip(
            zipFile.absolutePath,
            listOf(srcFile to "empty.txt"),
            password
        )

        val entries = AesZipHelper.listEncryptedZip(zipFile, password)
        assertEquals(1, entries.size)

        val decrypted = AesZipHelper.getDecryptedEntryBytes(zipFile, "empty.txt", password)
        assertNotNull(decrypted)
        assertEquals(0, decrypted!!.size)
    }

    // ── Wrong password ──────────────────────────────────────────────────

    @Test(expected = IllegalStateException::class)
    fun `wrong password throws IllegalStateException`() {
        val content = "secret content"
        val srcFile = tempDir.resolve("secret.txt").apply { writeText(content) }
        val zipFile = tempDir.resolve("wrong_pwd.zip")
        val password = "correct_password".toCharArray()
        val wrongPassword = "wrong_password".toCharArray()

        AesZipHelper.createEncryptedZip(
            zipFile.absolutePath,
            listOf(srcFile to "secret.txt"),
            password
        )

        // Should throw IllegalStateException("encrypted") — wrong password
        AesZipHelper.getDecryptedEntryBytes(zipFile, "secret.txt", wrongPassword)
    }

    // ── extractDecryptedEntry via OutputStream ──────────────────────────

    @Test
    fun `extractDecryptedEntry writes to output stream`() {
        val content = "stream extraction test"
        val srcFile = tempDir.resolve("stream.txt").apply { writeText(content) }
        val zipFile = tempDir.resolve("stream.zip")
        val password = "streampass".toCharArray()

        AesZipHelper.createEncryptedZip(
            zipFile.absolutePath,
            listOf(srcFile to "stream.txt"),
            password
        )

        val outFile = tempDir.resolve("extracted.txt")
        outFile.outputStream().use { os ->
            AesZipHelper.extractDecryptedEntry(zipFile, "stream.txt", password, os)
        }

        assertEquals(content, outFile.readText())
    }

    @Test(expected = IllegalStateException::class)
    fun `extractDecryptedEntry throws for missing entry`() {
        val srcFile = tempDir.resolve("exists.txt").apply { writeText("data") }
        val zipFile = tempDir.resolve("missing_entry.zip")
        val password = "pass".toCharArray()

        AesZipHelper.createEncryptedZip(
            zipFile.absolutePath,
            listOf(srcFile to "exists.txt"),
            password
        )

        val outFile = tempDir.resolve("missing.txt")
        outFile.outputStream().use { os ->
            AesZipHelper.extractDecryptedEntry(zipFile, "nonexistent.txt", password, os)
        }
    }

    // ── Progress callback ───────────────────────────────────────────────

    @Test
    fun `createEncryptedZip invokes progress callback`() {
        val files = (1..3).map { i ->
            val f = tempDir.resolve("prog$i.txt").apply { writeText("content $i") }
            f to "prog$i.txt"
        }
        val zipFile = tempDir.resolve("progress.zip")
        val password = "pass".toCharArray()

        val progressCalls = mutableListOf<Triple<Int, Int, String>>()
        AesZipHelper.createEncryptedZip(
            zipFile.absolutePath,
            files,
            password
        ) { index, total, name ->
            progressCalls.add(Triple(index, total, name))
        }

        assertEquals(3, progressCalls.size)
        assertEquals(0, progressCalls[0].first)
        assertEquals(3, progressCalls[0].second)
        assertEquals(2, progressCalls[2].first)
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun createNormalZip(name: String, entries: Map<String, String>): File {
        val zipFile = tempDir.resolve(name)
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            for ((entryName, content) in entries) {
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return zipFile
    }
}
