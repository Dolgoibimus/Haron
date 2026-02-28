package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.model.ComparisonStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for CompareFoldersUseCase — folder-level diff comparison.
 */
class CompareFoldersUseCaseTest {

    private lateinit var useCase: CompareFoldersUseCase
    private lateinit var tempDir: File
    private lateinit var leftDir: File
    private lateinit var rightDir: File

    @Before
    fun setUp() {
        useCase = CompareFoldersUseCase()
        tempDir = File(System.getProperty("java.io.tmpdir"), "haron_compare_folders_${System.nanoTime()}")
        tempDir.mkdirs()
        leftDir = File(tempDir, "left").apply { mkdirs() }
        rightDir = File(tempDir, "right").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `identical folders`() = runTest {
        File(leftDir, "file.txt").writeText("hello")
        File(rightDir, "file.txt").writeText("hello")

        val result = useCase(leftDir.absolutePath, rightDir.absolutePath)
        assertEquals(1, result.size)
        assertEquals(ComparisonStatus.IDENTICAL, result[0].status)
    }

    @Test
    fun `left only file`() = runTest {
        File(leftDir, "only_left.txt").writeText("left")

        val result = useCase(leftDir.absolutePath, rightDir.absolutePath)
        assertEquals(1, result.size)
        assertEquals(ComparisonStatus.LEFT_ONLY, result[0].status)
        assertEquals("only_left.txt", result[0].name)
    }

    @Test
    fun `right only file`() = runTest {
        File(rightDir, "only_right.txt").writeText("right")

        val result = useCase(leftDir.absolutePath, rightDir.absolutePath)
        assertEquals(1, result.size)
        assertEquals(ComparisonStatus.RIGHT_ONLY, result[0].status)
    }

    @Test
    fun `different content`() = runTest {
        File(leftDir, "diff.txt").writeText("version A")
        File(rightDir, "diff.txt").writeText("version B")

        val result = useCase(leftDir.absolutePath, rightDir.absolutePath)
        assertEquals(1, result.size)
        assertEquals(ComparisonStatus.DIFFERENT, result[0].status)
    }

    @Test
    fun `empty folders produce empty result`() = runTest {
        val result = useCase(leftDir.absolutePath, rightDir.absolutePath)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `progress callback called`() = runTest {
        File(leftDir, "a.txt").writeText("a")
        File(rightDir, "a.txt").writeText("a")
        File(leftDir, "b.txt").writeText("b")

        val progressCalls = mutableListOf<Pair<Int, Int>>()
        useCase(leftDir.absolutePath, rightDir.absolutePath) { current, total ->
            progressCalls.add(current to total)
        }
        assertTrue(progressCalls.isNotEmpty())
        assertEquals(2, progressCalls.last().second) // 2 unique paths
    }
}
