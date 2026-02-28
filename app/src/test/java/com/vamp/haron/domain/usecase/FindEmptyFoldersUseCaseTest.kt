package com.vamp.haron.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for FindEmptyFoldersUseCase — finds effectively empty directories.
 */
class FindEmptyFoldersUseCaseTest {

    private lateinit var useCase: FindEmptyFoldersUseCase
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        useCase = FindEmptyFoldersUseCase()
        tempDir = File(System.getProperty("java.io.tmpdir"), "haron_empty_folders_${System.nanoTime()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `no empty folders`() = runTest {
        val dir = File(tempDir, "full").apply { mkdirs() }
        File(dir, "file.txt").writeText("content")

        val result = useCase(tempDir.absolutePath, recursive = true).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `truly empty folder found`() = runTest {
        File(tempDir, "empty").mkdirs()
        File(tempDir, "full").mkdirs()
        File(File(tempDir, "full"), "file.txt").writeText("content")

        val result = useCase(tempDir.absolutePath, recursive = true).first()
        assertEquals(1, result.size)
        assertTrue(result[0].endsWith("empty"))
    }

    @Test
    fun `hidden-only files count as empty`() = runTest {
        val dir = File(tempDir, "hidden_only").apply { mkdirs() }
        File(dir, ".nomedia").writeText("")

        val result = useCase(tempDir.absolutePath, recursive = true).first()
        assertEquals(1, result.size)
        assertTrue(result[0].endsWith("hidden_only"))
    }

    @Test
    fun `folder with subdirs is not empty`() = runTest {
        val dir = File(tempDir, "has_subdir").apply { mkdirs() }
        File(dir, "child").mkdirs()

        val result = useCase(tempDir.absolutePath, recursive = true).first()
        // "child" is empty (no children), but "has_subdir" has a subdir so not empty
        assertEquals(1, result.size)
        assertTrue(result[0].endsWith("child"))
    }

    @Test
    fun `recursive finds nested empty folders`() = runTest {
        val a = File(tempDir, "a").apply { mkdirs() }
        val b = File(a, "b").apply { mkdirs() }
        val c = File(b, "c").apply { mkdirs() }
        // c is empty, b has subdir (c), a has subdir (b)

        val result = useCase(tempDir.absolutePath, recursive = true).first()
        // walkBottomUp: c is empty. After c is listed, b still has subdir c in filesystem
        // so b is not empty. a has subdir b, not empty.
        assertEquals(1, result.size)
        assertTrue(result[0].endsWith("c"))
    }

    @Test
    fun `non-recursive only checks first level`() = runTest {
        File(tempDir, "top_empty").mkdirs()
        val nested = File(tempDir, "parent").apply { mkdirs() }
        File(nested, "nested_empty").mkdirs()

        val result = useCase(tempDir.absolutePath, recursive = false).first()
        // Non-recursive: checks only "top_empty" and "parent" at first level
        // "top_empty" is empty, "parent" has subdir nested_empty → not "effectively empty"
        assertEquals(1, result.size)
        assertTrue(result[0].endsWith("top_empty"))
    }

    @Test
    fun `root not directory returns empty`() = runTest {
        val file = File(tempDir, "notdir.txt").apply { writeText("hi") }
        val result = useCase(file.absolutePath, recursive = true).first()
        assertTrue(result.isEmpty())
    }
}
