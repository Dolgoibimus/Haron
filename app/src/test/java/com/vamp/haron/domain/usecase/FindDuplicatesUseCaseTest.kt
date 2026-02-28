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
 * Tests for FindDuplicatesUseCase — finds duplicate files by content hash.
 */
class FindDuplicatesUseCaseTest {

    private lateinit var context: Context
    private lateinit var useCase: FindDuplicatesUseCase
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.getString(any()) } returns "placeholder"
        every { context.getString(eq(R.string.found_candidates_format), any()) } returns "Found candidates"
        useCase = FindDuplicatesUseCase(context)
        tempDir = File(System.getProperty("java.io.tmpdir"), "haron_dups_${System.nanoTime()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `no duplicates`() = runTest {
        File(tempDir, "unique1.txt").writeText("aaa")
        File(tempDir, "unique2.txt").writeText("bbb")

        val emissions = useCase(tempDir.absolutePath).toList()
        val last = emissions.last()
        assertTrue(last.isComplete)
        assertTrue(last.groups.isEmpty())
    }

    @Test
    fun `same size different content — no duplicates`() = runTest {
        File(tempDir, "a.txt").writeText("abc")
        File(tempDir, "b.txt").writeText("xyz")

        val emissions = useCase(tempDir.absolutePath).toList()
        val last = emissions.last()
        assertTrue(last.isComplete)
        assertTrue(last.groups.isEmpty())
    }

    @Test
    fun `identical files found as duplicates`() = runTest {
        File(tempDir, "copy1.txt").writeText("duplicate content here")
        File(tempDir, "copy2.txt").writeText("duplicate content here")

        val emissions = useCase(tempDir.absolutePath).toList()
        val last = emissions.last()
        assertTrue(last.isComplete)
        assertEquals(1, last.groups.size)
        assertEquals(2, last.groups[0].files.size)
    }

    @Test
    fun `empty directory`() = runTest {
        val emissions = useCase(tempDir.absolutePath).toList()
        val last = emissions.last()
        assertTrue(last.isComplete)
        assertTrue(last.groups.isEmpty())
    }

    @Test
    fun `progress emissions include phase info`() = runTest {
        File(tempDir, "a.txt").writeText("content")
        File(tempDir, "b.txt").writeText("content")

        val emissions = useCase(tempDir.absolutePath).toList()
        // Should have at least: initial phase 1, end of phase 1, end of phase 2
        assertTrue(emissions.size >= 2)
        assertEquals(1, emissions[0].phase)
    }
}
