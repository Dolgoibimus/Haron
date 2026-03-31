package com.vamp.haron.domain.usecase

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * Tests for CreateZipUseCase — ZIP archive creation.
 * Rewritten to use standard java.util.zip after zip4j removal.
 */
class CreateZipUseCaseTest {

    private lateinit var useCase: CreateZipUseCase
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        useCase = CreateZipUseCase()
        tempDir = File(System.getProperty("java.io.tmpdir"), "haron_zip_${System.nanoTime()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `zip single file`() = runTest {
        val file = File(tempDir, "test.txt").apply { writeText("hello world") }
        val output = File(tempDir, "output.zip")

        val progress = useCase(listOf(file.absolutePath), output.absolutePath).toList()

        assertTrue(output.exists())
        assertTrue(progress.last().isComplete)
        assertEquals(1, progress.last().total)
        ZipFile(output).use { zip ->
            val entries = zip.entries().toList()
            assertEquals(1, entries.size)
            assertEquals("test.txt", entries[0].name)
        }
    }

    @Test
    fun `zip directory`() = runTest {
        val dir = File(tempDir, "mydir").apply { mkdirs() }
        File(dir, "a.txt").writeText("aaa")
        File(dir, "b.txt").writeText("bbb")
        val output = File(tempDir, "dir_output.zip")

        val progress = useCase(listOf(dir.absolutePath), output.absolutePath).toList()

        assertTrue(output.exists())
        assertTrue(progress.last().isComplete)
        assertEquals(2, progress.last().total)
        ZipFile(output).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertTrue(names.any { it.endsWith("a.txt") })
            assertTrue(names.any { it.endsWith("b.txt") })
        }
    }

    @Test
    fun `zip multiple sources`() = runTest {
        val f1 = File(tempDir, "first.txt").apply { writeText("1") }
        val f2 = File(tempDir, "second.txt").apply { writeText("2") }
        val output = File(tempDir, "multi.zip")

        val progress = useCase(listOf(f1.absolutePath, f2.absolutePath), output.absolutePath).toList()

        assertTrue(output.exists())
        assertTrue(progress.last().isComplete)
        assertEquals(2, progress.last().total)
        ZipFile(output).use { zip ->
            assertEquals(2, zip.entries().toList().size)
        }
    }

    @Test
    fun `zip emits per-file progress`() = runTest {
        val f1 = File(tempDir, "a.txt").apply { writeText("aaa") }
        val f2 = File(tempDir, "b.txt").apply { writeText("bbb") }
        val f3 = File(tempDir, "c.txt").apply { writeText("ccc") }
        val output = File(tempDir, "progress.zip")

        val progress = useCase(
            listOf(f1.absolutePath, f2.absolutePath, f3.absolutePath),
            output.absolutePath
        ).toList()

        // Should have 3 progress emissions + 1 completion = 4
        assertEquals(4, progress.size)
        assertEquals(0, progress[0].current)
        assertEquals(3, progress[0].total)
        assertEquals(1, progress[1].current)
        assertEquals(2, progress[2].current)
        assertTrue(progress[3].isComplete)
        assertEquals(3, progress[3].total)
    }
}
