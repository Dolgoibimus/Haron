package com.vamp.haron.domain.model

import com.vamp.haron.domain.usecase.DuplicateFile
import com.vamp.haron.domain.usecase.DuplicateGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateGroupTest {

    @Test
    fun `wastedSpace with 2 files returns size of 1 copy`() {
        val group = DuplicateGroup(
            hash = "abc123",
            size = 1024L,
            files = listOf(
                DuplicateFile("/a/file.txt", "file.txt", 0L, 1024L, isOldest = true),
                DuplicateFile("/b/file.txt", "file.txt", 0L, 1024L)
            )
        )
        assertEquals(1024L, group.wastedSpace)
    }

    @Test
    fun `wastedSpace with 5 files returns size times 4`() {
        val files = (1..5).map {
            DuplicateFile("/dir$it/f.dat", "f.dat", 0L, 500L, isOldest = it == 1)
        }
        val group = DuplicateGroup(hash = "def456", size = 500L, files = files)
        assertEquals(2000L, group.wastedSpace)
    }

    @Test
    fun `wastedSpace with 1 file returns 0`() {
        val group = DuplicateGroup(
            hash = "ghi789",
            size = 1_000_000L,
            files = listOf(DuplicateFile("/only/file.dat", "file.dat", 0L, 1_000_000L, isOldest = true))
        )
        assertEquals(0L, group.wastedSpace)
    }
}
