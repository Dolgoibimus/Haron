package com.vamp.haron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComparisonResultTest {

    @Test
    fun `DiffLine with lineNumber`() {
        val line = DiffLine(lineNumber = 5, text = "hello", type = DiffLineType.UNCHANGED)
        assertEquals(5, line.lineNumber)
        assertEquals("hello", line.text)
        assertEquals(DiffLineType.UNCHANGED, line.type)
    }

    @Test
    fun `DiffLine with null lineNumber for padding`() {
        val line = DiffLine(lineNumber = null, text = "", type = DiffLineType.ADDED)
        assertNull(line.lineNumber)
    }

    @Test
    fun `TextDiffResult counts`() {
        val result = TextDiffResult(
            leftLines = emptyList(),
            rightLines = emptyList(),
            addedCount = 3,
            removedCount = 1,
            modifiedCount = 2
        )
        assertEquals(3, result.addedCount)
        assertEquals(1, result.removedCount)
        assertEquals(2, result.modifiedCount)
    }

    @Test
    fun `FolderComparisonEntry fields`() {
        val entry = FolderComparisonEntry(
            relativePath = "docs/readme.md",
            name = "readme.md",
            isDirectory = false,
            status = ComparisonStatus.DIFFERENT,
            leftSize = 100L,
            rightSize = 200L,
            leftModified = 1000L,
            rightModified = 2000L
        )
        assertEquals(ComparisonStatus.DIFFERENT, entry.status)
        assertEquals(100L, entry.leftSize)
        assertEquals(200L, entry.rightSize)
    }

    @Test
    fun `FolderComparisonEntry LEFT_ONLY has null right fields`() {
        val entry = FolderComparisonEntry(
            relativePath = "old.txt",
            name = "old.txt",
            isDirectory = false,
            status = ComparisonStatus.LEFT_ONLY,
            leftSize = 50L,
            rightSize = null,
            leftModified = 1000L,
            rightModified = null
        )
        assertNull(entry.rightSize)
        assertNull(entry.rightModified)
    }

    @Test
    fun `ComparisonStatus enum values`() {
        val values = ComparisonStatus.values()
        assertEquals(4, values.size)
        assertEquals(ComparisonStatus.IDENTICAL, values[0])
        assertEquals(ComparisonStatus.DIFFERENT, values[1])
        assertEquals(ComparisonStatus.LEFT_ONLY, values[2])
        assertEquals(ComparisonStatus.RIGHT_ONLY, values[3])
    }

    @Test
    fun `DiffLineType enum values`() {
        val values = DiffLineType.values()
        assertEquals(4, values.size)
    }

    @Test
    fun `FileMetadataComparison fields`() {
        val meta = FileMetadataComparison(
            leftPath = "/a/file.txt",
            rightPath = "/b/file.txt",
            leftSize = 100,
            rightSize = 100,
            leftModified = 1000,
            rightModified = 2000,
            sameContent = true
        )
        assertEquals(true, meta.sameContent)
        assertEquals("/a/file.txt", meta.leftPath)
    }
}
