package com.vamp.haron.domain.usecase

import android.content.Context
import com.vamp.haron.R
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for CalculateHashUseCase — MD5 + SHA-256 hash calculation.
 */
class CalculateHashUseCaseTest {

    private lateinit var context: Context
    private lateinit var useCase: CalculateHashUseCase
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.getString(R.string.hash_error) } returns "Error"
        useCase = CalculateHashUseCase(context)
        tempDir = File(System.getProperty("java.io.tmpdir"), "haron_hash_${System.nanoTime()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `known MD5 for empty file`() = runTest {
        val file = File(tempDir, "empty.bin").apply { writeBytes(byteArrayOf()) }
        val emissions = useCase(file.absolutePath).toList()
        val last = emissions.last()
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", last.md5)
        assertEquals(1f, last.progress)
    }

    @Test
    fun `known SHA-256 for empty file`() = runTest {
        val file = File(tempDir, "empty.bin").apply { writeBytes(byteArrayOf()) }
        val emissions = useCase(file.absolutePath).toList()
        val last = emissions.last()
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", last.sha256)
    }

    @Test
    fun `known hashes for text content`() = runTest {
        // MD5("hello") = 5d41402abc4b2a76b9719d911017c592
        // SHA-256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        val file = File(tempDir, "hello.txt").apply { writeBytes("hello".toByteArray()) }
        val emissions = useCase(file.absolutePath).toList()
        val last = emissions.last()
        assertEquals("5d41402abc4b2a76b9719d911017c592", last.md5)
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", last.sha256)
    }

    @Test
    fun `non-existent file returns error`() = runTest {
        val emissions = useCase("/nonexistent/file.txt").toList()
        val last = emissions.last()
        assertEquals("Error", last.md5)
        assertEquals("Error", last.sha256)
    }

    @Test
    fun `progress reaches 1f`() = runTest {
        val file = File(tempDir, "data.bin").apply { writeBytes(ByteArray(1024) { it.toByte() }) }
        val emissions = useCase(file.absolutePath).toList()
        val last = emissions.last()
        assertEquals(1f, last.progress)
        assertTrue(last.md5.isNotEmpty())
        assertTrue(last.sha256.isNotEmpty())
    }
}
