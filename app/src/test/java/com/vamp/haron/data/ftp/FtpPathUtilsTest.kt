package com.vamp.haron.data.ftp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FtpPathUtilsTest {

    // --- isFtpPath ---

    @Test
    fun `isFtpPath returns true for ftp prefix`() {
        assertTrue(FtpPathUtils.isFtpPath("ftp://192.168.1.5"))
        assertTrue(FtpPathUtils.isFtpPath("ftp://192.168.1.5/files"))
        assertTrue(FtpPathUtils.isFtpPath("ftp://myserver:2121/data"))
    }

    @Test
    fun `isFtpPath returns true for ftps prefix`() {
        assertTrue(FtpPathUtils.isFtpPath("ftps://192.168.1.5"))
        assertTrue(FtpPathUtils.isFtpPath("ftps://secure.host/dir"))
    }

    @Test
    fun `isFtpPath is case insensitive`() {
        assertTrue(FtpPathUtils.isFtpPath("FTP://HOST"))
        assertTrue(FtpPathUtils.isFtpPath("Ftp://host"))
        assertTrue(FtpPathUtils.isFtpPath("FTPS://HOST"))
        assertTrue(FtpPathUtils.isFtpPath("Ftps://host"))
    }

    @Test
    fun `isFtpPath returns false for non-ftp paths`() {
        assertFalse(FtpPathUtils.isFtpPath("/storage/emulated/0"))
        assertFalse(FtpPathUtils.isFtpPath("http://example.com"))
        assertFalse(FtpPathUtils.isFtpPath("smb://192.168.1.5"))
        assertFalse(FtpPathUtils.isFtpPath(""))
    }

    // --- parseHost ---

    @Test
    fun `parseHost extracts host from ftp path`() {
        assertEquals("192.168.1.5", FtpPathUtils.parseHost("ftp://192.168.1.5"))
        assertEquals("192.168.1.5", FtpPathUtils.parseHost("ftp://192.168.1.5/files"))
        assertEquals("192.168.1.5", FtpPathUtils.parseHost("ftp://192.168.1.5/files/sub"))
        assertEquals("myserver", FtpPathUtils.parseHost("ftp://myserver"))
    }

    @Test
    fun `parseHost extracts host from ftps path`() {
        assertEquals("secure.host", FtpPathUtils.parseHost("ftps://secure.host"))
        assertEquals("secure.host", FtpPathUtils.parseHost("ftps://secure.host/data"))
    }

    @Test
    fun `parseHost extracts host when port is present`() {
        assertEquals("192.168.1.5", FtpPathUtils.parseHost("ftp://192.168.1.5:2121"))
        assertEquals("192.168.1.5", FtpPathUtils.parseHost("ftp://192.168.1.5:2121/files"))
    }

    // --- parsePort ---

    @Test
    fun `parsePort returns default port when not specified`() {
        assertEquals(21, FtpPathUtils.parsePort("ftp://192.168.1.5"))
        assertEquals(21, FtpPathUtils.parsePort("ftp://192.168.1.5/files"))
    }

    @Test
    fun `parsePort extracts custom port`() {
        assertEquals(2121, FtpPathUtils.parsePort("ftp://192.168.1.5:2121"))
        assertEquals(8021, FtpPathUtils.parsePort("ftp://192.168.1.5:8021/files"))
        assertEquals(990, FtpPathUtils.parsePort("ftps://host:990/data"))
    }

    @Test
    fun `parsePort returns default for invalid port number`() {
        assertEquals(21, FtpPathUtils.parsePort("ftp://192.168.1.5:abc"))
        assertEquals(21, FtpPathUtils.parsePort("ftp://192.168.1.5:/files"))
    }

    // --- parseRelativePath ---

    @Test
    fun `parseRelativePath returns root when no path after host`() {
        assertEquals("/", FtpPathUtils.parseRelativePath("ftp://192.168.1.5"))
        assertEquals("/", FtpPathUtils.parseRelativePath("ftp://192.168.1.5:2121"))
    }

    @Test
    fun `parseRelativePath extracts path after host`() {
        assertEquals("/files", FtpPathUtils.parseRelativePath("ftp://192.168.1.5/files"))
        assertEquals("/files/sub", FtpPathUtils.parseRelativePath("ftp://192.168.1.5/files/sub"))
        assertEquals("/data", FtpPathUtils.parseRelativePath("ftp://192.168.1.5:2121/data"))
    }

    @Test
    fun `parseRelativePath works with ftps`() {
        assertEquals("/secure/dir", FtpPathUtils.parseRelativePath("ftps://host/secure/dir"))
    }

    // --- buildPath ---

    @Test
    fun `buildPath constructs path with default port and root`() {
        assertEquals("ftp://192.168.1.5", FtpPathUtils.buildPath("192.168.1.5"))
        assertEquals("ftp://192.168.1.5", FtpPathUtils.buildPath("192.168.1.5", 21, "/"))
    }

    @Test
    fun `buildPath includes non-default port`() {
        assertEquals("ftp://192.168.1.5:2121", FtpPathUtils.buildPath("192.168.1.5", 2121))
        assertEquals("ftp://192.168.1.5:2121/files", FtpPathUtils.buildPath("192.168.1.5", 2121, "/files"))
    }

    @Test
    fun `buildPath appends relative path`() {
        assertEquals("ftp://host/files", FtpPathUtils.buildPath("host", 21, "/files"))
        assertEquals("ftp://host/files/sub", FtpPathUtils.buildPath("host", 21, "/files/sub"))
    }

    @Test
    fun `buildPath normalizes relative path without leading slash`() {
        assertEquals("ftp://host/files", FtpPathUtils.buildPath("host", 21, "files"))
    }

    @Test
    fun `buildPath omits path for empty relative path`() {
        assertEquals("ftp://host", FtpPathUtils.buildPath("host", 21, ""))
    }

    // --- getParentPath ---

    @Test
    fun `getParentPath returns root when already at root`() {
        assertEquals("ftp://192.168.1.5", FtpPathUtils.getParentPath("ftp://192.168.1.5"))
        assertEquals("ftp://192.168.1.5:2121", FtpPathUtils.getParentPath("ftp://192.168.1.5:2121"))
    }

    @Test
    fun `getParentPath navigates up one level`() {
        assertEquals("ftp://192.168.1.5", FtpPathUtils.getParentPath("ftp://192.168.1.5/files"))
        assertEquals("ftp://192.168.1.5/files", FtpPathUtils.getParentPath("ftp://192.168.1.5/files/sub"))
    }

    @Test
    fun `getParentPath preserves non-default port`() {
        assertEquals("ftp://host:2121", FtpPathUtils.getParentPath("ftp://host:2121/data"))
        assertEquals("ftp://host:2121/data", FtpPathUtils.getParentPath("ftp://host:2121/data/sub"))
    }

    // --- getFileName ---

    @Test
    fun `getFileName returns last segment`() {
        assertEquals("file.txt", FtpPathUtils.getFileName("ftp://host/files/file.txt"))
        assertEquals("sub", FtpPathUtils.getFileName("ftp://host/files/sub"))
        assertEquals("files", FtpPathUtils.getFileName("ftp://host/files"))
    }

    @Test
    fun `getFileName handles trailing slash`() {
        assertEquals("sub", FtpPathUtils.getFileName("ftp://host/files/sub/"))
    }

    @Test
    fun `getFileName returns host when no path segments`() {
        assertEquals("192.168.1.5", FtpPathUtils.getFileName("ftp://192.168.1.5"))
    }

    // --- isRoot ---

    @Test
    fun `isRoot returns true when at root`() {
        assertTrue(FtpPathUtils.isRoot("ftp://192.168.1.5"))
        assertTrue(FtpPathUtils.isRoot("ftp://192.168.1.5:2121"))
        assertTrue(FtpPathUtils.isRoot("ftps://host"))
    }

    @Test
    fun `isRoot returns false when path is not root`() {
        assertFalse(FtpPathUtils.isRoot("ftp://192.168.1.5/files"))
        assertFalse(FtpPathUtils.isRoot("ftp://192.168.1.5/files/sub"))
    }

    // --- connectionKey ---

    @Test
    fun `connectionKey formats host and port`() {
        assertEquals("192.168.1.5:21", FtpPathUtils.connectionKey("192.168.1.5", 21))
        assertEquals("myserver:2121", FtpPathUtils.connectionKey("myserver", 2121))
        assertEquals("host:990", FtpPathUtils.connectionKey("host", 990))
    }
}
