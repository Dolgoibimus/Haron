package com.vamp.haron.domain.usecase

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.lingala.zip4j.ZipFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for CreateZipUseCase — ZIP archive creation with zip4j.
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
            val entries = zip.fileHeaders
            assertEquals(1, entries.size)
            assertEquals("test.txt", entries[0].fileName)
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
            val names = zip.fileHeaders.map { it.fileName }
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
            assertEquals(2, zip.fileHeaders.size)
        }
    }

    @Test
    fun `zip with password creates encrypted archive`() = runTest {
        val file = File(tempDir, "secret.txt").apply { writeText("top secret data") }
        val output = File(tempDir, "encrypted.zip")

        useCase(listOf(file.absolutePath), output.absolutePath, password = "mypass123").toList()

        assertTrue(output.exists())
        val zip = ZipFile(output)
        assertTrue(zip.isEncrypted)
        zip.setPassword("mypass123".toCharArray())
        zip.use { z ->
            val entries = z.fileHeaders
            assertEquals(1, entries.size)
            assertTrue(entries[0].isEncrypted)
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

    @Test
    fun `zip with password and no password produces different results`() = runTest {
        val file = File(tempDir, "data.txt").apply { writeText("some data here") }
        val outputPlain = File(tempDir, "plain.zip")
        val outputEncrypted = File(tempDir, "encrypted.zip")

        useCase(listOf(file.absolutePath), outputPlain.absolutePath).toList()
        useCase(listOf(file.absolutePath), outputEncrypted.absolutePath, password = "pass").toList()

        assertTrue(outputPlain.exists())
        assertTrue(outputEncrypted.exists())

        val plainZip = ZipFile(outputPlain)
        val encZip = ZipFile(outputEncrypted)

        assertTrue(!plainZip.isEncrypted)
        assertTrue(encZip.isEncrypted)

        plainZip.close()
        encZip.close()
    }

    @Test
    fun `zip empty password treated as no encryption`() = runTest {
        val file = File(tempDir, "noenc.txt").apply { writeText("no encryption") }
        val output = File(tempDir, "noenc.zip")

        useCase(listOf(file.absolutePath), output.absolutePath, password = "").toList()

        assertTrue(output.exists())
        val zip = ZipFile(output)
        assertTrue(!zip.isEncrypted)
        zip.close()
    }
}
