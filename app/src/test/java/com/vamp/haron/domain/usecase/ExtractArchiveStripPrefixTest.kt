package com.vamp.haron.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for ExtractArchiveUseCase.stripPrefix — strips the virtual path prefix
 * from archive entry paths so files are extracted flat into the destination.
 */
class ExtractArchiveStripPrefixTest {

    private fun strip(path: String, prefix: String) =
        ExtractArchiveUseCase.stripPrefix(path, prefix)

    @Test
    fun `empty prefix returns path unchanged`() {
        assertEquals("folder/file.txt", strip("folder/file.txt", ""))
    }

    @Test
    fun `matching prefix is stripped`() {
        assertEquals("file.txt", strip("folder/file.txt", "folder"))
    }

    @Test
    fun `deep prefix is stripped`() {
        assertEquals("file.txt", strip("a/b/c/file.txt", "a/b/c"))
    }

    @Test
    fun `non-matching prefix returns path unchanged`() {
        assertEquals("other/file.txt", strip("other/file.txt", "folder"))
    }

    @Test
    fun `partial prefix match does not strip`() {
        assertEquals("folder/file.txt", strip("folder/file.txt", "fold"))
    }

    @Test
    fun `prefix strips only the prefix part preserving subpath`() {
        assertEquals("sub/file.txt", strip("root/sub/file.txt", "root"))
    }

    @Test
    fun `prefix with trailing slash in path`() {
        assertEquals("file.txt", strip("docs/file.txt", "docs"))
    }
}
