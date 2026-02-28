package com.vamp.haron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileEntryTest {

    private fun entry(
        name: String = "test.txt",
        path: String = "/test/test.txt",
        isDir: Boolean = false,
        size: Long = 0L,
        ext: String = "txt",
        isHidden: Boolean = false,
        childCount: Int = 0,
        isContentUri: Boolean = false,
        isProtected: Boolean = false
    ) = FileEntry(
        name = name,
        path = path,
        isDirectory = isDir,
        size = size,
        lastModified = 0L,
        extension = ext,
        isHidden = isHidden,
        childCount = childCount,
        isContentUri = isContentUri,
        isProtected = isProtected
    )

    @Test
    fun `default values for optional fields`() {
        val e = entry()
        assertFalse(e.isContentUri)
        assertFalse(e.isProtected)
    }

    @Test
    fun `isContentUri when set`() {
        val e = entry(isContentUri = true)
        assertTrue(e.isContentUri)
    }

    @Test
    fun `isProtected when set`() {
        val e = entry(isProtected = true)
        assertTrue(e.isProtected)
    }

    @Test
    fun `directory entry with child count`() {
        val dir = entry(name = "docs", isDir = true, childCount = 15)
        assertTrue(dir.isDirectory)
        assertEquals(15, dir.childCount)
    }

    @Test
    fun `equality by all fields`() {
        val a = entry(name = "a.txt", path = "/a.txt", size = 100L)
        val b = entry(name = "a.txt", path = "/a.txt", size = 100L)
        assertEquals(a, b)
    }

    @Test
    fun `copy preserves all fields`() {
        val original = entry(
            name = "secret.dat",
            path = "/vault/secret.dat",
            isDir = false,
            size = 999L,
            ext = "dat",
            isHidden = true,
            childCount = 0,
            isContentUri = false,
            isProtected = true
        )
        val copy = original.copy(size = 1000L)
        assertEquals(1000L, copy.size)
        assertTrue(copy.isProtected)
        assertTrue(copy.isHidden)
        assertEquals("secret.dat", copy.name)
    }
}
