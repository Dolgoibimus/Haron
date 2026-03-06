package com.vamp.haron.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuzzyMatchTest {

    // --- levenshtein ---

    @Test
    fun `identical strings have distance 0`() {
        assertEquals(0, FuzzyMatch.levenshtein("hello", "hello"))
    }

    @Test
    fun `empty vs non-empty`() {
        assertEquals(5, FuzzyMatch.levenshtein("", "hello"))
        assertEquals(3, FuzzyMatch.levenshtein("abc", ""))
    }

    @Test
    fun `single character difference`() {
        assertEquals(1, FuzzyMatch.levenshtein("cat", "car"))
    }

    @Test
    fun `case insensitive`() {
        assertEquals(0, FuzzyMatch.levenshtein("Hello", "hello"))
    }

    @Test
    fun `russian strings`() {
        assertEquals(0, FuzzyMatch.levenshtein("загрузки", "Загрузки"))
        assertEquals(1, FuzzyMatch.levenshtein("загрузки", "загрузка"))
    }

    @Test
    fun `completely different strings`() {
        assertEquals(3, FuzzyMatch.levenshtein("abc", "xyz"))
    }

    // --- similarity ---

    @Test
    fun `identical strings have similarity 1`() {
        assertEquals(1f, FuzzyMatch.similarity("test", "test"))
    }

    @Test
    fun `completely different short strings`() {
        // "ab" vs "cd" → distance 2, max 2 → similarity 0
        assertEquals(0f, FuzzyMatch.similarity("ab", "cd"))
    }

    @Test
    fun `both empty strings have similarity 1`() {
        assertEquals(1f, FuzzyMatch.similarity("", ""))
    }

    // --- findBestMatch ---

    @Test
    fun `exact match wins`() {
        val candidates = listOf("Download", "Documents", "DCIM")
        assertEquals("Download", FuzzyMatch.findBestMatch("download", candidates))
    }

    @Test
    fun `contains match`() {
        val candidates = listOf("my-downloads", "Documents", "DCIM")
        assertEquals("my-downloads", FuzzyMatch.findBestMatch("download", candidates))
    }

    @Test
    fun `fuzzy match above threshold`() {
        val candidates = listOf("Download", "Documents", "DCIM")
        // "загрузки" vs these English names — should not match (too different)
        assertNull(FuzzyMatch.findBestMatch("загрузки", candidates))
    }

    @Test
    fun `fuzzy match with similar names`() {
        val candidates = listOf("Downloads", "Documents", "Desktop")
        // "Downlods" (typo) → should match "Downloads"
        assertEquals("Downloads", FuzzyMatch.findBestMatch("downlods", candidates))
    }

    @Test
    fun `below threshold returns null`() {
        val candidates = listOf("Alpha", "Beta", "Gamma")
        assertNull(FuzzyMatch.findBestMatch("zzzzz", candidates, threshold = 0.5f))
    }

    @Test
    fun `empty query returns null`() {
        assertNull(FuzzyMatch.findBestMatch("", listOf("a", "b")))
    }

    @Test
    fun `empty candidates returns null`() {
        assertNull(FuzzyMatch.findBestMatch("test", emptyList()))
    }
}
