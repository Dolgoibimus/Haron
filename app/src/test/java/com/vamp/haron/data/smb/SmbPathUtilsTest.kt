package com.vamp.haron.data.smb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbPathUtilsTest {

    @Test
    fun `isSmbPath returns true for smb prefix`() {
        assertTrue(SmbPathUtils.isSmbPath("smb://192.168.1.5"))
        assertTrue(SmbPathUtils.isSmbPath("smb://192.168.1.5/Share"))
        assertTrue(SmbPathUtils.isSmbPath("SMB://host"))
    }

    @Test
    fun `isSmbPath returns false for non-smb paths`() {
        assertFalse(SmbPathUtils.isSmbPath("/storage/emulated/0"))
        assertFalse(SmbPathUtils.isSmbPath("http://example.com"))
        assertFalse(SmbPathUtils.isSmbPath(""))
    }

    @Test
    fun `parseHost extracts host from smb path`() {
        assertEquals("192.168.1.5", SmbPathUtils.parseHost("smb://192.168.1.5"))
        assertEquals("192.168.1.5", SmbPathUtils.parseHost("smb://192.168.1.5/Share"))
        assertEquals("192.168.1.5", SmbPathUtils.parseHost("smb://192.168.1.5/Share/Docs"))
        assertEquals("myserver", SmbPathUtils.parseHost("smb://myserver"))
    }

    @Test
    fun `parseShare extracts share name`() {
        assertEquals("", SmbPathUtils.parseShare("smb://192.168.1.5"))
        assertEquals("Share", SmbPathUtils.parseShare("smb://192.168.1.5/Share"))
        assertEquals("Share", SmbPathUtils.parseShare("smb://192.168.1.5/Share/Docs"))
        assertEquals("Share", SmbPathUtils.parseShare("smb://192.168.1.5/Share/Docs/Sub"))
    }

    @Test
    fun `parseRelativePath extracts path after share`() {
        assertEquals("", SmbPathUtils.parseRelativePath("smb://192.168.1.5"))
        assertEquals("", SmbPathUtils.parseRelativePath("smb://192.168.1.5/Share"))
        assertEquals("Docs", SmbPathUtils.parseRelativePath("smb://192.168.1.5/Share/Docs"))
        assertEquals("Docs/Sub", SmbPathUtils.parseRelativePath("smb://192.168.1.5/Share/Docs/Sub"))
    }

    @Test
    fun `buildPath constructs correct smb path`() {
        assertEquals("smb://192.168.1.5", SmbPathUtils.buildPath("192.168.1.5"))
        assertEquals("smb://192.168.1.5/Share", SmbPathUtils.buildPath("192.168.1.5", "Share"))
        assertEquals("smb://192.168.1.5/Share/Docs", SmbPathUtils.buildPath("192.168.1.5", "Share", "Docs"))
    }

    @Test
    fun `buildPath with empty share does not add slash`() {
        assertEquals("smb://host", SmbPathUtils.buildPath("host", "", ""))
        assertEquals("smb://host", SmbPathUtils.buildPath("host"))
    }

    @Test
    fun `getParentPath returns parent`() {
        assertEquals("smb://192.168.1.5/Share", SmbPathUtils.getParentPath("smb://192.168.1.5/Share/Docs"))
        assertEquals("smb://192.168.1.5", SmbPathUtils.getParentPath("smb://192.168.1.5/Share"))
        assertEquals("smb://192.168.1.5/Share/Docs", SmbPathUtils.getParentPath("smb://192.168.1.5/Share/Docs/Sub"))
    }

    @Test
    fun `getFileName returns last segment`() {
        assertEquals("file.txt", SmbPathUtils.getFileName("smb://host/Share/file.txt"))
        assertEquals("Docs", SmbPathUtils.getFileName("smb://host/Share/Docs"))
        assertEquals("Share", SmbPathUtils.getFileName("smb://host/Share"))
    }

    @Test
    fun `getFileName handles trailing slash`() {
        assertEquals("Docs", SmbPathUtils.getFileName("smb://host/Share/Docs/"))
    }
}
