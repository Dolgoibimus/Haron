package com.vamp.haron.data.transfer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TransferProtocolNegotiatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ── buildRequest ──

    @Test
    fun `buildRequest creates valid JSON with files and protocol`() {
        val file1 = tempFolder.newFile("photo.jpg").apply { writeBytes(ByteArray(1024)) }
        val file2 = tempFolder.newFile("video.mp4").apply { writeBytes(ByteArray(2048)) }

        val result = TransferProtocolNegotiator.buildRequest(listOf(file1, file2), "WIFI_DIRECT")
        val json = JSONObject(result)

        assertEquals("REQUEST", json.getString("type"))
        assertEquals("WIFI_DIRECT", json.getString("protocol"))

        val files = json.getJSONArray("files")
        assertEquals(2, files.length())

        val f1 = files.getJSONObject(0)
        assertEquals("photo.jpg", f1.getString("name"))
        assertEquals(1024L, f1.getLong("size"))

        val f2 = files.getJSONObject(1)
        assertEquals("video.mp4", f2.getString("name"))
        assertEquals(2048L, f2.getLong("size"))
    }

    @Test
    fun `buildRequest with empty file list produces empty array`() {
        val result = TransferProtocolNegotiator.buildRequest(emptyList(), "HTTP")
        val json = JSONObject(result)

        assertEquals("REQUEST", json.getString("type"))
        assertEquals("HTTP", json.getString("protocol"))
        assertEquals(0, json.getJSONArray("files").length())
    }

    @Test
    fun `buildRequest with BLUETOOTH protocol`() {
        val file = tempFolder.newFile("doc.pdf").apply { writeBytes(ByteArray(500)) }
        val result = TransferProtocolNegotiator.buildRequest(listOf(file), "BLUETOOTH")
        val json = JSONObject(result)

        assertEquals("BLUETOOTH", json.getString("protocol"))
    }

    // ── buildAccept ──

    @Test
    fun `buildAccept creates valid JSON with port`() {
        val result = TransferProtocolNegotiator.buildAccept(8080)
        val json = JSONObject(result)

        assertEquals("ACCEPT", json.getString("type"))
        assertEquals(8080, json.getInt("port"))
    }

    @Test
    fun `buildAccept with zero port`() {
        val result = TransferProtocolNegotiator.buildAccept(0)
        val json = JSONObject(result)

        assertEquals(0, json.getInt("port"))
    }

    // ── buildDecline ──

    @Test
    fun `buildDecline creates valid JSON with reason`() {
        val result = TransferProtocolNegotiator.buildDecline("Not enough space")
        val json = JSONObject(result)

        assertEquals("DECLINE", json.getString("type"))
        assertEquals("Not enough space", json.getString("reason"))
    }

    @Test
    fun `buildDecline with empty reason`() {
        val result = TransferProtocolNegotiator.buildDecline("")
        val json = JSONObject(result)

        assertEquals("", json.getString("reason"))
    }

    // ── buildFileHeader ──

    @Test
    fun `buildFileHeader creates valid JSON with all fields`() {
        val result = TransferProtocolNegotiator.buildFileHeader("archive.zip", 999999L, 3, 5000L)
        val json = JSONObject(result)

        assertEquals("FILE_HEADER", json.getString("type"))
        assertEquals("archive.zip", json.getString("name"))
        assertEquals(999999L, json.getLong("size"))
        assertEquals(3, json.getInt("index"))
        assertEquals(5000L, json.getLong("offset"))
    }

    @Test
    fun `buildFileHeader with default offset zero`() {
        val result = TransferProtocolNegotiator.buildFileHeader("test.txt", 100L, 0)
        val json = JSONObject(result)

        assertEquals(0L, json.getLong("offset"))
    }

    // ── buildComplete ──

    @Test
    fun `buildComplete creates valid JSON`() {
        val result = TransferProtocolNegotiator.buildComplete()
        val json = JSONObject(result)

        assertEquals("COMPLETE", json.getString("type"))
    }

    // ── buildQuickSend ──

    @Test
    fun `buildQuickSend with sender name`() {
        val file = tempFolder.newFile("image.png").apply { writeBytes(ByteArray(4096)) }
        val result = TransferProtocolNegotiator.buildQuickSend(listOf(file), "MyPhone")
        val json = JSONObject(result)

        assertEquals("QUICK_SEND", json.getString("type"))
        assertEquals("MyPhone", json.getString("sender_name"))

        val files = json.getJSONArray("files")
        assertEquals(1, files.length())
        assertEquals("image.png", files.getJSONObject(0).getString("name"))
        assertEquals(4096L, files.getJSONObject(0).getLong("size"))
    }

    @Test
    fun `buildQuickSend without sender name omits field`() {
        val file = tempFolder.newFile("data.bin").apply { writeBytes(ByteArray(10)) }
        val result = TransferProtocolNegotiator.buildQuickSend(listOf(file))
        val json = JSONObject(result)

        assertEquals("QUICK_SEND", json.getString("type"))
        assertTrue(!json.has("sender_name"))
    }

    @Test
    fun `buildQuickSend with null sender name omits field`() {
        val file = tempFolder.newFile("note.txt").apply { writeBytes(ByteArray(5)) }
        val result = TransferProtocolNegotiator.buildQuickSend(listOf(file), null)
        val json = JSONObject(result)

        assertTrue(!json.has("sender_name"))
    }

    // ── buildDropRequest ──

    @Test
    fun `buildDropRequest creates valid JSON`() {
        val result = TransferProtocolNegotiator.buildDropRequest("Tablet", 9090)
        val json = JSONObject(result)

        assertEquals("DROP_REQUEST", json.getString("type"))
        assertEquals("Tablet", json.getString("sender_name"))
        assertEquals(9090, json.getInt("sender_port"))
    }

    // ── parseType ──

    @Test
    fun `parseType extracts type from valid JSON`() {
        assertEquals("REQUEST", TransferProtocolNegotiator.parseType("""{"type":"REQUEST"}"""))
        assertEquals("ACCEPT", TransferProtocolNegotiator.parseType("""{"type":"ACCEPT"}"""))
        assertEquals("DECLINE", TransferProtocolNegotiator.parseType("""{"type":"DECLINE"}"""))
        assertEquals("FILE_HEADER", TransferProtocolNegotiator.parseType("""{"type":"FILE_HEADER"}"""))
        assertEquals("COMPLETE", TransferProtocolNegotiator.parseType("""{"type":"COMPLETE"}"""))
        assertEquals("DROP_REQUEST", TransferProtocolNegotiator.parseType("""{"type":"DROP_REQUEST"}"""))
        assertEquals("QUICK_SEND", TransferProtocolNegotiator.parseType("""{"type":"QUICK_SEND"}"""))
    }

    @Test
    fun `parseType returns empty string when type is missing`() {
        assertEquals("", TransferProtocolNegotiator.parseType("""{"port":8080}"""))
    }

    @Test(expected = Exception::class)
    fun `parseType throws on invalid JSON`() {
        TransferProtocolNegotiator.parseType("not json at all")
    }

    // ── parseRequest ──

    @Test
    fun `parseRequest extracts files and protocol`() {
        val json = """
            {
                "type": "REQUEST",
                "protocol": "WIFI_DIRECT",
                "files": [
                    {"name": "a.txt", "size": 100},
                    {"name": "b.mp4", "size": 999999}
                ]
            }
        """.trimIndent()

        val data = TransferProtocolNegotiator.parseRequest(json)

        assertEquals("WIFI_DIRECT", data.protocol)
        assertEquals(2, data.files.size)
        assertEquals("a.txt", data.files[0].name)
        assertEquals(100L, data.files[0].size)
        assertEquals("b.mp4", data.files[1].name)
        assertEquals(999999L, data.files[1].size)
    }

    @Test
    fun `parseRequest defaults protocol to HTTP when missing`() {
        val json = """{"type":"REQUEST","files":[]}"""
        val data = TransferProtocolNegotiator.parseRequest(json)

        assertEquals("HTTP", data.protocol)
        assertTrue(data.files.isEmpty())
    }

    // ── parseAccept ──

    @Test
    fun `parseAccept extracts port`() {
        val port = TransferProtocolNegotiator.parseAccept("""{"type":"ACCEPT","port":12345}""")
        assertEquals(12345, port)
    }

    @Test
    fun `parseAccept returns 0 when port is missing`() {
        val port = TransferProtocolNegotiator.parseAccept("""{"type":"ACCEPT"}""")
        assertEquals(0, port)
    }

    // ── parseDecline ──

    @Test
    fun `parseDecline extracts reason`() {
        val reason = TransferProtocolNegotiator.parseDecline("""{"type":"DECLINE","reason":"User rejected"}""")
        assertEquals("User rejected", reason)
    }

    @Test
    fun `parseDecline returns Unknown when reason is missing`() {
        val reason = TransferProtocolNegotiator.parseDecline("""{"type":"DECLINE"}""")
        assertEquals("Unknown", reason)
    }

    // ── parseFileHeader ──

    @Test
    fun `parseFileHeader extracts all fields`() {
        val json = """{"type":"FILE_HEADER","name":"report.pdf","size":50000,"index":2,"offset":1024}"""
        val header = TransferProtocolNegotiator.parseFileHeader(json)

        assertEquals("report.pdf", header.name)
        assertEquals(50000L, header.size)
        assertEquals(2, header.index)
        assertEquals(1024L, header.offset)
    }

    @Test
    fun `parseFileHeader defaults offset to 0 when missing`() {
        val json = """{"type":"FILE_HEADER","name":"file.bin","size":200,"index":0}"""
        val header = TransferProtocolNegotiator.parseFileHeader(json)

        assertEquals(0L, header.offset)
    }

    // ── parseQuickSendSender ──

    @Test
    fun `parseQuickSendSender extracts sender name`() {
        val name = TransferProtocolNegotiator.parseQuickSendSender(
            """{"type":"QUICK_SEND","sender_name":"Galaxy S24","files":[]}"""
        )
        assertEquals("Galaxy S24", name)
    }

    @Test
    fun `parseQuickSendSender returns null when sender_name is missing`() {
        val name = TransferProtocolNegotiator.parseQuickSendSender(
            """{"type":"QUICK_SEND","files":[]}"""
        )
        assertNull(name)
    }

    @Test
    fun `parseQuickSendSender returns null on invalid JSON`() {
        val name = TransferProtocolNegotiator.parseQuickSendSender("broken")
        assertNull(name)
    }

    // ── parseDropRequest ──

    @Test
    fun `parseDropRequest extracts sender name and port`() {
        val json = """{"type":"DROP_REQUEST","sender_name":"Laptop","sender_port":7070}"""
        val data = TransferProtocolNegotiator.parseDropRequest(json)

        assertEquals("Laptop", data.senderName)
        assertEquals(7070, data.senderPort)
    }

    @Test
    fun `parseDropRequest defaults when fields are missing`() {
        val data = TransferProtocolNegotiator.parseDropRequest("""{"type":"DROP_REQUEST"}""")

        assertEquals("Unknown", data.senderName)
        assertEquals(0, data.senderPort)
    }

    // ── Round-trip tests ──

    @Test
    fun `round-trip buildRequest then parseRequest`() {
        val file1 = tempFolder.newFile("song.mp3").apply { writeBytes(ByteArray(3000)) }
        val file2 = tempFolder.newFile("cover.jpg").apply { writeBytes(ByteArray(800)) }

        val built = TransferProtocolNegotiator.buildRequest(listOf(file1, file2), "HTTP")
        val parsed = TransferProtocolNegotiator.parseRequest(built)

        assertEquals("HTTP", parsed.protocol)
        assertEquals(2, parsed.files.size)
        assertEquals("song.mp3", parsed.files[0].name)
        assertEquals(3000L, parsed.files[0].size)
        assertEquals("cover.jpg", parsed.files[1].name)
        assertEquals(800L, parsed.files[1].size)
    }

    @Test
    fun `round-trip buildAccept then parseAccept`() {
        val built = TransferProtocolNegotiator.buildAccept(54321)
        val port = TransferProtocolNegotiator.parseAccept(built)

        assertEquals(54321, port)
    }

    @Test
    fun `round-trip buildDecline then parseDecline`() {
        val built = TransferProtocolNegotiator.buildDecline("Storage full")
        val reason = TransferProtocolNegotiator.parseDecline(built)

        assertEquals("Storage full", reason)
    }

    @Test
    fun `round-trip buildFileHeader then parseFileHeader`() {
        val built = TransferProtocolNegotiator.buildFileHeader("data.csv", 123456L, 5, 10000L)
        val header = TransferProtocolNegotiator.parseFileHeader(built)

        assertEquals("data.csv", header.name)
        assertEquals(123456L, header.size)
        assertEquals(5, header.index)
        assertEquals(10000L, header.offset)
    }

    @Test
    fun `round-trip buildFileHeader with default offset then parseFileHeader`() {
        val built = TransferProtocolNegotiator.buildFileHeader("notes.txt", 50L, 0)
        val header = TransferProtocolNegotiator.parseFileHeader(built)

        assertEquals(0L, header.offset)
    }

    @Test
    fun `round-trip buildDropRequest then parseDropRequest`() {
        val built = TransferProtocolNegotiator.buildDropRequest("Pixel 8", 6060)
        val parsed = TransferProtocolNegotiator.parseDropRequest(built)

        assertEquals("Pixel 8", parsed.senderName)
        assertEquals(6060, parsed.senderPort)
    }

    @Test
    fun `round-trip buildQuickSend then parseRequest extracts files`() {
        val file = tempFolder.newFile("quick.txt").apply { writeBytes(ByteArray(256)) }
        val built = TransferProtocolNegotiator.buildQuickSend(listOf(file), "Sender")

        // QuickSend JSON has same files array structure — parseRequest can read it
        val parsed = TransferProtocolNegotiator.parseRequest(built)
        assertEquals(1, parsed.files.size)
        assertEquals("quick.txt", parsed.files[0].name)
        assertEquals(256L, parsed.files[0].size)
    }

    @Test
    fun `round-trip buildQuickSend then parseQuickSendSender`() {
        val file = tempFolder.newFile("qs.bin").apply { writeBytes(ByteArray(1)) }
        val built = TransferProtocolNegotiator.buildQuickSend(listOf(file), "TestDevice")
        val sender = TransferProtocolNegotiator.parseQuickSendSender(built)

        assertEquals("TestDevice", sender)
    }

    @Test
    fun `round-trip buildComplete then parseType`() {
        val built = TransferProtocolNegotiator.buildComplete()
        val type = TransferProtocolNegotiator.parseType(built)

        assertEquals("COMPLETE", type)
    }

    // ── Type constants verification ──

    @Test
    fun `type constants have expected values`() {
        assertEquals("REQUEST", TransferProtocolNegotiator.TYPE_REQUEST)
        assertEquals("ACCEPT", TransferProtocolNegotiator.TYPE_ACCEPT)
        assertEquals("DECLINE", TransferProtocolNegotiator.TYPE_DECLINE)
        assertEquals("FILE_HEADER", TransferProtocolNegotiator.TYPE_FILE_HEADER)
        assertEquals("COMPLETE", TransferProtocolNegotiator.TYPE_COMPLETE)
        assertEquals("DROP_REQUEST", TransferProtocolNegotiator.TYPE_DROP_REQUEST)
        assertEquals("QUICK_SEND", TransferProtocolNegotiator.TYPE_QUICK_SEND)
    }

    // ── parseType on built messages ──

    @Test
    fun `parseType correctly identifies all built message types`() {
        val file = tempFolder.newFile("t.txt").apply { writeBytes(ByteArray(1)) }

        assertEquals("REQUEST", TransferProtocolNegotiator.parseType(
            TransferProtocolNegotiator.buildRequest(listOf(file), "HTTP")
        ))
        assertEquals("ACCEPT", TransferProtocolNegotiator.parseType(
            TransferProtocolNegotiator.buildAccept(8080)
        ))
        assertEquals("DECLINE", TransferProtocolNegotiator.parseType(
            TransferProtocolNegotiator.buildDecline("no")
        ))
        assertEquals("FILE_HEADER", TransferProtocolNegotiator.parseType(
            TransferProtocolNegotiator.buildFileHeader("f", 1L, 0)
        ))
        assertEquals("COMPLETE", TransferProtocolNegotiator.parseType(
            TransferProtocolNegotiator.buildComplete()
        ))
        assertEquals("DROP_REQUEST", TransferProtocolNegotiator.parseType(
            TransferProtocolNegotiator.buildDropRequest("s", 1)
        ))
        assertEquals("QUICK_SEND", TransferProtocolNegotiator.parseType(
            TransferProtocolNegotiator.buildQuickSend(listOf(file))
        ))
    }

    // ── Edge cases ──

    @Test
    fun `buildRequest with single empty file`() {
        val file = tempFolder.newFile("empty.txt") // 0 bytes
        val result = TransferProtocolNegotiator.buildRequest(listOf(file), "HTTP")
        val json = JSONObject(result)

        val files = json.getJSONArray("files")
        assertEquals(1, files.length())
        assertEquals(0L, files.getJSONObject(0).getLong("size"))
    }

    @Test
    fun `buildFileHeader with large size`() {
        val result = TransferProtocolNegotiator.buildFileHeader("huge.iso", Long.MAX_VALUE, 0, 0)
        val header = TransferProtocolNegotiator.parseFileHeader(result)

        assertEquals(Long.MAX_VALUE, header.size)
    }

    @Test
    fun `buildDecline with special characters in reason`() {
        val reason = """User said "no" & left <room>"""
        val built = TransferProtocolNegotiator.buildDecline(reason)
        val parsed = TransferProtocolNegotiator.parseDecline(built)

        assertEquals(reason, parsed)
    }

    @Test
    fun `buildDropRequest with unicode sender name`() {
        val built = TransferProtocolNegotiator.buildDropRequest("Телефон Юзера", 5555)
        val parsed = TransferProtocolNegotiator.parseDropRequest(built)

        assertEquals("Телефон Юзера", parsed.senderName)
    }
}
