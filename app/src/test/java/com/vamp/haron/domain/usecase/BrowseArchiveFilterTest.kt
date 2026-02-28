package com.vamp.haron.domain.usecase

import com.vamp.haron.domain.model.ArchiveEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for BrowseArchiveUseCase.filterDirectChildren — the core logic
 * that filters a flat list of archive entries into direct children of a given prefix,
 * synthesizes virtual directories, counts children, and sorts them.
 */
class BrowseArchiveFilterTest {

    private fun filter(entries: List<ArchiveEntry>, prefix: String) =
        BrowseArchiveUseCase.filterDirectChildren(entries, prefix)

    private fun ae(fullPath: String, isDir: Boolean = false, size: Long = 100L) = ArchiveEntry(
        name = fullPath.trimEnd('/').substringAfterLast('/'),
        fullPath = fullPath.trimEnd('/'),
        size = size,
        isDirectory = isDir
    )

    // --- Root level ---

    @Test
    fun `root level - returns direct files`() {
        val all = listOf(ae("readme.txt"), ae("data.csv"))
        val result = filter(all, "")
        assertEquals(2, result.size)
        assertEquals("data.csv", result[0].name) // sorted alphabetically
        assertEquals("readme.txt", result[1].name)
    }

    @Test
    fun `root level - synthesizes directories from nested entries`() {
        val all = listOf(
            ae("docs/readme.txt"),
            ae("docs/guide.pdf"),
            ae("src/main.kt")
        )
        val result = filter(all, "")
        assertEquals(2, result.size)
        assertTrue(result[0].isDirectory) // dirs first
        assertTrue(result[1].isDirectory)
        assertEquals("docs", result[0].name)
        assertEquals("src", result[1].name)
    }

    @Test
    fun `root level - mix of files and synthesized dirs`() {
        val all = listOf(
            ae("readme.txt"),
            ae("src/main.kt"),
            ae("src/util.kt")
        )
        val result = filter(all, "")
        assertEquals(2, result.size)
        assertTrue(result[0].isDirectory)  // dir first
        assertEquals("src", result[0].name)
        assertEquals("readme.txt", result[1].name)
    }

    @Test
    fun `root level - explicit directory entry preserved`() {
        val all = listOf(
            ae("lib/", isDir = true),
            ae("lib/core.jar")
        )
        val result = filter(all, "")
        assertEquals(1, result.size)
        assertEquals("lib", result[0].name)
        assertTrue(result[0].isDirectory)
    }

    // --- Subdirectory navigation ---

    @Test
    fun `subdirectory - filters to direct children of prefix`() {
        val all = listOf(
            ae("readme.txt"),
            ae("src/main.kt"),
            ae("src/util/helpers.kt"),
            ae("src/test.kt")
        )
        val result = filter(all, "src/")
        assertEquals(3, result.size)
        // dir first, then files
        assertTrue(result[0].isDirectory)
        assertEquals("util", result[0].name)
        // files: main.kt, test.kt
        val files = result.filter { !it.isDirectory }
        assertEquals(2, files.size)
    }

    @Test
    fun `deep subdirectory - works correctly`() {
        val all = listOf(
            ae("a/b/c/file1.txt"),
            ae("a/b/c/file2.txt"),
            ae("a/b/c/d/deep.txt")
        )
        val result = filter(all, "a/b/c/")
        assertEquals(3, result.size) // dir "d" + file1 + file2
        assertTrue(result[0].isDirectory)
        assertEquals("d", result[0].name)
    }

    // --- Child count ---

    @Test
    fun `childCount for synthesized directory counts direct children`() {
        val all = listOf(
            ae("folder/file1.txt"),
            ae("folder/file2.txt"),
            ae("folder/sub/file3.txt")
        )
        val result = filter(all, "")
        val folder = result.find { it.name == "folder" }!!
        // Direct children of "folder": file1.txt, file2.txt, sub (3 items)
        assertEquals(3, folder.childCount)
    }

    @Test
    fun `childCount for deep nested directory`() {
        val all = listOf(
            ae("a/b/c/d.txt"),
            ae("a/b/e.txt")
        )
        val result = filter(all, "")
        val a = result.find { it.name == "a" }!!
        // Direct children of "a": "b" (1 item)
        assertEquals(1, a.childCount)
    }

    @Test
    fun `childCount for file entries is 0`() {
        val all = listOf(ae("file.txt"))
        val result = filter(all, "")
        assertEquals(0, result[0].childCount)
    }

    @Test
    fun `childCount for directory with only subdirectories`() {
        val all = listOf(
            ae("parent/sub1/file.txt"),
            ae("parent/sub2/file.txt")
        )
        val result = filter(all, "")
        val parent = result.find { it.name == "parent" }!!
        // Direct children: sub1, sub2
        assertEquals(2, parent.childCount)
    }

    // --- Sorting ---

    @Test
    fun `directories come before files`() {
        val all = listOf(
            ae("zebra.txt"),
            ae("alpha/file.kt")
        )
        val result = filter(all, "")
        assertTrue(result[0].isDirectory)
        assertEquals("alpha", result[0].name)
        assertEquals("zebra.txt", result[1].name)
    }

    @Test
    fun `alphabetical sorting case-insensitive`() {
        val all = listOf(
            ae("Charlie.txt"),
            ae("alpha.txt"),
            ae("Bravo.txt")
        )
        val result = filter(all, "")
        assertEquals("alpha.txt", result[0].name)
        assertEquals("Bravo.txt", result[1].name)
        assertEquals("Charlie.txt", result[2].name)
    }

    // --- Edge cases ---

    @Test
    fun `empty entries list returns empty`() {
        val result = filter(emptyList(), "")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `prefix with no matching entries returns empty`() {
        val all = listOf(ae("other/file.txt"))
        val result = filter(all, "nonexistent/")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `entry matching prefix exactly is skipped`() {
        val all = listOf(
            ae("folder", isDir = true),
            ae("folder/file.txt")
        )
        val result = filter(all, "folder/")
        assertEquals(1, result.size)
        assertEquals("file.txt", result[0].name)
    }
}
