package com.vamp.haron.data.transfer

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * JSON-based protocol negotiator for Haron-to-Haron transfers.
 * Handshake:
 * 1. Sender → Receiver: REQUEST { files: [{name, size}], protocol: "WIFI_DIRECT"|"HTTP"|"BLUETOOTH" }
 * 2. Receiver → Sender: ACCEPT { port: 12345 } or DECLINE { reason: "..." }
 * 3. Sender streams files sequentially with header: FILE_HEADER { name, size, index }
 */
object TransferProtocolNegotiator {

    private const val KEY_TYPE = "type"
    private const val KEY_FILES = "files"
    private const val KEY_NAME = "name"
    private const val KEY_SIZE = "size"
    private const val KEY_PROTOCOL = "protocol"
    private const val KEY_PORT = "port"
    private const val KEY_REASON = "reason"
    private const val KEY_INDEX = "index"
    private const val KEY_OFFSET = "offset"

    const val TYPE_REQUEST = "REQUEST"
    const val TYPE_ACCEPT = "ACCEPT"
    const val TYPE_DECLINE = "DECLINE"
    const val TYPE_FILE_HEADER = "FILE_HEADER"
    const val TYPE_COMPLETE = "COMPLETE"
    const val TYPE_DROP_REQUEST = "DROP_REQUEST"
    const val TYPE_QUICK_SEND = "QUICK_SEND"

    private const val KEY_SENDER_NAME = "sender_name"
    private const val KEY_SENDER_PORT = "sender_port"

    fun buildRequest(files: List<File>, protocol: String): String {
        val json = JSONObject().apply {
            put(KEY_TYPE, TYPE_REQUEST)
            put(KEY_PROTOCOL, protocol)
            put(KEY_FILES, JSONArray().apply {
                files.forEach { file ->
                    put(JSONObject().apply {
                        put(KEY_NAME, file.name)
                        put(KEY_SIZE, file.length())
                    })
                }
            })
        }
        return json.toString()
    }

    fun buildQuickSend(files: List<File>, senderName: String? = null): String {
        val json = JSONObject().apply {
            put(KEY_TYPE, TYPE_QUICK_SEND)
            if (senderName != null) put(KEY_SENDER_NAME, senderName)
            put(KEY_FILES, JSONArray().apply {
                files.forEach { file ->
                    put(JSONObject().apply {
                        put(KEY_NAME, file.name)
                        put(KEY_SIZE, file.length())
                    })
                }
            })
        }
        return json.toString()
    }

    fun parseQuickSendSender(json: String): String? {
        return try {
            val obj = JSONObject(json)
            if (obj.has(KEY_SENDER_NAME)) obj.getString(KEY_SENDER_NAME) else null
        } catch (_: Exception) {
            null
        }
    }

    fun buildAccept(port: Int): String {
        return JSONObject().apply {
            put(KEY_TYPE, TYPE_ACCEPT)
            put(KEY_PORT, port)
        }.toString()
    }

    fun buildDecline(reason: String): String {
        return JSONObject().apply {
            put(KEY_TYPE, TYPE_DECLINE)
            put(KEY_REASON, reason)
        }.toString()
    }

    fun buildFileHeader(name: String, size: Long, index: Int, offset: Long = 0): String {
        return JSONObject().apply {
            put(KEY_TYPE, TYPE_FILE_HEADER)
            put(KEY_NAME, name)
            put(KEY_SIZE, size)
            put(KEY_INDEX, index)
            put(KEY_OFFSET, offset)
        }.toString()
    }

    fun buildComplete(): String {
        return JSONObject().apply {
            put(KEY_TYPE, TYPE_COMPLETE)
        }.toString()
    }

    fun parseType(json: String): String {
        return JSONObject(json).optString(KEY_TYPE, "")
    }

    fun parseRequest(json: String): RequestData {
        val obj = JSONObject(json)
        val filesArray = obj.getJSONArray(KEY_FILES)
        val files = (0 until filesArray.length()).map { i ->
            val fileObj = filesArray.getJSONObject(i)
            FileInfo(fileObj.getString(KEY_NAME), fileObj.getLong(KEY_SIZE))
        }
        return RequestData(
            files = files,
            protocol = obj.optString(KEY_PROTOCOL, "HTTP")
        )
    }

    fun parseAccept(json: String): Int {
        return JSONObject(json).optInt(KEY_PORT, 0)
    }

    fun parseDecline(json: String): String {
        return JSONObject(json).optString(KEY_REASON, "Unknown")
    }

    fun buildDropRequest(senderName: String, senderPort: Int): String {
        return JSONObject().apply {
            put(KEY_TYPE, TYPE_DROP_REQUEST)
            put(KEY_SENDER_NAME, senderName)
            put(KEY_SENDER_PORT, senderPort)
        }.toString()
    }

    fun parseDropRequest(json: String): DropRequestData {
        val obj = JSONObject(json)
        return DropRequestData(
            senderName = obj.optString(KEY_SENDER_NAME, "Unknown"),
            senderPort = obj.optInt(KEY_SENDER_PORT, 0)
        )
    }

    fun parseFileHeader(json: String): FileHeaderData {
        val obj = JSONObject(json)
        return FileHeaderData(
            name = obj.getString(KEY_NAME),
            size = obj.getLong(KEY_SIZE),
            index = obj.getInt(KEY_INDEX),
            offset = obj.optLong(KEY_OFFSET, 0)
        )
    }

    data class FileInfo(val name: String, val size: Long)
    data class RequestData(val files: List<FileInfo>, val protocol: String)
    data class FileHeaderData(val name: String, val size: Long, val index: Int, val offset: Long)
    data class DropRequestData(val senderName: String, val senderPort: Int)
}
