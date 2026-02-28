package com.vamp.haron.domain.usecase

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

        useCase(listOf(file.absolutePath), output.absolutePath)

        assertTrue(output.exists())
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

        useCase(listOf(dir.absolutePath), output.absolutePath)

        assertTrue(output.exists())
        ZipFile(output).use { zip ->
            val entries = zip.entries().toList().map { it.name }
            assertEquals(2, entries.size)
            assertTrue(entries.any { it.endsWith("a.txt") })
            assertTrue(entries.any { it.endsWith("b.txt") })
        }
    }

    @Test
    fun `zip multiple sources`() = runTest {
        val f1 = File(tempDir, "first.txt").apply { writeText("1") }
        val f2 = File(tempDir, "second.txt").apply { writeText("2") }
        val output = File(tempDir, "multi.zip")

        useCase(listOf(f1.absolutePath, f2.absolutePath), output.absolutePath)

        assertTrue(output.exists())
        ZipFile(output).use { zip ->
            val entries = zip.entries().toList()
            assertEquals(2, entries.size)
        }
    }
}
