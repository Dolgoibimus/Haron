package com.vamp.haron.common.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {

    // --- toDurationString ---

    @Test
    fun `zero milliseconds returns 0 colon 00`() {
        assertEquals("0:00", 0L.toDurationString())
    }

    @Test
    fun `1 second returns 0 colon 01`() {
        assertEquals("0:01", 1_000L.toDurationString())
    }

    @Test
    fun `59 seconds returns 0 colon 59`() {
        assertEquals("0:59", 59_000L.toDurationString())
    }

    @Test
    fun `1 minute returns 1 colon 00`() {
        assertEquals("1:00", 60_000L.toDurationString())
    }

    @Test
    fun `1 minute 30 seconds`() {
        assertEquals("1:30", 90_000L.toDurationString())
    }

    @Test
    fun `59 minutes 59 seconds`() {
        assertEquals("59:59", (59 * 60_000L + 59_000L).toDurationString())
    }

    @Test
    fun `1 hour returns with hours prefix`() {
        assertEquals("1:00:00", 3_600_000L.toDurationString())
    }

    @Test
    fun `1 hour 5 minutes 3 seconds`() {
        assertEquals("1:05:03", (3_600_000L + 5 * 60_000L + 3_000L).toDurationString())
    }

    @Test
    fun `10 hours 30 minutes 45 seconds`() {
        val ms = 10 * 3_600_000L + 30 * 60_000L + 45_000L
        assertEquals("10:30:45", ms.toDurationString())
    }

    @Test
    fun `sub-second values truncated to 0`() {
        assertEquals("0:00", 999L.toDurationString())
    }
}
