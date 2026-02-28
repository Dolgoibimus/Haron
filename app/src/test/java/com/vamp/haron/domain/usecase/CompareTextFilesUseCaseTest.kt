package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.model.DiffLineType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for CompareTextFilesUseCase — text diff algorithm.
 * Uses temp files to feed the use case.
 */
class CompareTextFilesUseCaseTest {

    private lateinit var useCase: CompareTextFilesUseCase
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        useCase = CompareTextFilesUseCase()
        tempDir = File(System.getProperty("java.io.tmpdir"), "haron_test_${System.nanoTime()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun writeFile(name: String, content: String): String {
        val f = File(tempDir, name)
        f.writeText(content)
        return f.absolutePath
    }

    @Test
    fun `identical files produce no diffs`() = runTest {
        val left = writeFile("a.txt", "line1\nline2\nline3")
        val right = writeFile("b.txt", "line1\nline2\nline3")
        val result = useCase(left, right)
        assertEquals(0, result.addedCount)
        assertEquals(0, result.removedCount)
        assertEquals(0, result.modifiedCount)
        assertEquals(3, result.leftLines.size)
        assertTrue(result.leftLines.all { it.type == DiffLineType.UNCHANGED })
    }

    @Test
    fun `added lines detected`() = runTest {
        val left = writeFile("a.txt", "line1\nline3")
        val right = writeFile("b.txt", "line1\nline2\nline3")
        val result = useCase(left, right)
        assertEquals(1, result.addedCount)
        assertEquals(0, result.removedCount)
    }

    @Test
    fun `removed lines detected`() = runTest {
        val left = writeFile("a.txt", "line1\nline2\nline3")
        val right = writeFile("b.txt", "line1\nline3")
        val result = useCase(left, right)
        assertEquals(0, result.addedCount)
        assertEquals(1, result.removedCount)
    }

    @Test
    fun `modified lines detected`() = runTest {
        val left = writeFile("a.txt", "line1\noriginal\nline3")
        val right = writeFile("b.txt", "line1\nchanged\nline3")
        val result = useCase(left, right)
        assertEquals(0, result.addedCount)
        assertEquals(0, result.removedCount)
        assertEquals(1, result.modifiedCount)
    }

    @Test
    fun `empty files produce empty result`() = runTest {
        val left = writeFile("a.txt", "")
        val right = writeFile("b.txt", "")
        val result = useCase(left, right)
        assertEquals(0, result.addedCount)
        assertEquals(0, result.removedCount)
        assertEquals(0, result.modifiedCount)
        assertTrue(result.leftLines.isEmpty())
    }

    @Test
    fun `left empty right has lines`() = runTest {
        val left = writeFile("a.txt", "")
        val right = writeFile("b.txt", "hello\nworld")
        val result = useCase(left, right)
        assertEquals(2, result.addedCount)
        assertEquals(0, result.removedCount)
    }

    @Test
    fun `left has lines right empty`() = runTest {
        val left = writeFile("a.txt", "hello\nworld")
        val right = writeFile("b.txt", "")
        val result = useCase(left, right)
        assertEquals(0, result.addedCount)
        assertEquals(2, result.removedCount)
    }

    @Test
    fun `left and right lines have same count`() = runTest {
        val left = writeFile("a.txt", "AAA\nBBB\nCCC")
        val right = writeFile("b.txt", "AAA\nBBB\nCCC")
        val result = useCase(left, right)
        assertEquals(result.leftLines.size, result.rightLines.size)
    }

    @Test
    fun `complex diff with all types`() = runTest {
        val left = writeFile("a.txt", "same\nremoved\nchangeA\nsame2")
        val right = writeFile("b.txt", "same\nchangeB\nsame2\nadded")
        val result = useCase(left, right)
        // should have some removed, some modified, some added
        assertTrue(result.addedCount + result.removedCount + result.modifiedCount > 0)
    }

    @Test
    fun `line numbers are correct for unchanged lines`() = runTest {
        val left = writeFile("a.txt", "first\nsecond\nthird")
        val right = writeFile("b.txt", "first\nsecond\nthird")
        val result = useCase(left, right)
        assertEquals(1, result.leftLines[0].lineNumber)
        assertEquals(2, result.leftLines[1].lineNumber)
        assertEquals(3, result.leftLines[2].lineNumber)
    }
}
