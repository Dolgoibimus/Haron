package com.vamp.haron.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for ForceDeleteUseCase — permanent deletion bypassing trash.
 * Uses real temp files on JVM filesystem.
 */
class ForceDeleteUseCaseTest {

    private lateinit var useCase: ForceDeleteUseCase
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        useCase = ForceDeleteUseCase()
        tempDir = File(System.getProperty("java.io.tmpdir"), "haron_force_del_${System.nanoTime()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `delete single file`() = runTest {
        val file = File(tempDir, "test.txt").apply { writeText("hello") }
        assertTrue(file.exists())

        val result = useCase(listOf(file.absolutePath))
        assertEquals(1, result.getOrThrow())
        assertFalse(file.exists())
    }

    @Test
    fun `delete directory with contents`() = runTest {
        val dir = File(tempDir, "subdir").apply { mkdirs() }
        File(dir, "a.txt").writeText("a")
        File(dir, "b.txt").writeText("b")

        val result = useCase(listOf(dir.absolutePath))
        assertEquals(1, result.getOrThrow())
        assertFalse(dir.exists())
    }

    @Test
    fun `delete nested directories`() = runTest {
        val dir = File(tempDir, "level1").apply { mkdirs() }
        val sub = File(dir, "level2").apply { mkdirs() }
        val deep = File(sub, "level3").apply { mkdirs() }
        File(deep, "deep.txt").writeText("deep")

        val result = useCase(listOf(dir.absolutePath))
        assertEquals(1, result.getOrThrow())
        assertFalse(dir.exists())
    }

    @Test
    fun `non-existent path does not count as deleted`() = runTest {
        val result = useCase(listOf("/nonexistent/path/file.txt"))
        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun `empty list returns 0`() = runTest {
        val result = useCase(emptyList())
        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun `progress callback fires`() = runTest {
        val f1 = File(tempDir, "a.txt").apply { writeText("a") }
        val f2 = File(tempDir, "b.txt").apply { writeText("b") }

        val progress = mutableListOf<Pair<Int, String>>()
        val result = useCase(listOf(f1.absolutePath, f2.absolutePath)) { current, name ->
            progress.add(current to name)
        }
        assertEquals(2, result.getOrThrow())
        assertTrue(progress.isNotEmpty())
    }
}
