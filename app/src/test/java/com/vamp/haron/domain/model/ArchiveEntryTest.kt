package com.vamp.haron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveEntryTest {

    @Test
    fun `default childCount is 0`() {
        val entry = ArchiveEntry(name = "file.txt", fullPath = "file.txt", size = 100, isDirectory = false)
        assertEquals(0, entry.childCount)
    }

    @Test
    fun `childCount is preserved in copy`() {
        val entry = ArchiveEntry(name = "dir", fullPath = "dir", size = 0, isDirectory = true, childCount = 5)
        val copy = entry.copy(name = "newDir")
        assertEquals(5, copy.childCount)
    }

    @Test
    fun `directory entry defaults`() {
        val dir = ArchiveEntry(name = "folder", fullPath = "folder", size = 0, isDirectory = true)
        assertTrue(dir.isDirectory)
        assertEquals(0L, dir.compressedSize)
        assertEquals(0L, dir.lastModified)
    }

    @Test
    fun `file entry with all fields`() {
        val file = ArchiveEntry(
            name = "data.bin",
            fullPath = "sub/data.bin",
            size = 1024,
            isDirectory = false,
            compressedSize = 512,
            lastModified = 1700000000000L,
            childCount = 0
        )
        assertFalse(file.isDirectory)
        assertEquals(1024L, file.size)
        assertEquals(512L, file.compressedSize)
        assertEquals(1700000000000L, file.lastModified)
    }
}
