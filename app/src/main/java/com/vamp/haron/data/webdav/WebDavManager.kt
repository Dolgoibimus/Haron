package com.vamp.haron.data.webdav

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavManager @Inject constructor() {

    private val mutex = Mutex()
    private var credential: WebDavCredential? = null
    private var baseUrl: String = ""

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val isConnected: Boolean
        get() = credential != null && baseUrl.isNotEmpty()

    fun connect(cred: WebDavCredential): Result<Unit> {
        EcosystemLogger.d(HaronConstants.TAG, "WebDavManager: connecting to ${cred.url}")
        credential = cred
        baseUrl = cred.url.trimEnd('/')
        return Result.success(Unit)
    }

    fun disconnect() {
        EcosystemLogger.d(HaronConstants.TAG, "WebDavManager: disconnecting")
        credential = null
        baseUrl = ""
    }

    suspend fun listFiles(url: String): Result<List<WebDavFileInfo>> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val cred = credential
                        ?: return@withContext Result.failure(IllegalStateException("Not connected"))

                    val targetUrl = url.trimEnd('/') + "/"
                    EcosystemLogger.d(HaronConstants.TAG, "WebDavManager: PROPFIND $targetUrl")

                    val propfindBody = """<?xml version="1.0" encoding="UTF-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:resourcetype/>
    <d:getcontentlength/>
    <d:getlastmodified/>
    <d:getcontenttype/>
    <d:displayname/>
  </d:prop>
</d:propfind>""".toRequestBody("application/xml; charset=utf-8".toMediaType())

                    val requestBuilder = Request.Builder()
                        .url(targetUrl)
                        .method("PROPFIND", propfindBody)
                        .header("Depth", "1")
                        .header("Content-Type", "application/xml; charset=utf-8")

                    if (cred.username.isNotEmpty()) {
                        requestBuilder.header("Authorization", Credentials.basic(cred.username, cred.password))
                    }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string() ?: ""

                    if (!response.isSuccessful && response.code != 207) {
                        EcosystemLogger.e(HaronConstants.TAG, "WebDavManager: PROPFIND failed: ${response.code}")
                        return@withContext Result.failure(
                            IllegalStateException("PROPFIND failed: ${response.code} ${response.message}")
                        )
                    }

                    val files = parsePropfindResponse(body, targetUrl)
                    EcosystemLogger.d(HaronConstants.TAG, "WebDavManager: listed ${files.size} files in $targetUrl")
                    Result.success(files)
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "WebDavManager: listFiles failed: ${e.message}")
                    Result.failure(e)
                }
            }
        }

    fun downloadFile(fileUrl: String, localDest: File): Flow<WebDavTransferProgress> = flow {
        mutex.withLock {
            val cred = credential ?: throw IllegalStateException("Not connected")
            val fileName = fileUrl.trimEnd('/').substringAfterLast("/")
            EcosystemLogger.d(HaronConstants.TAG, "WebDavManager: download started $fileName")

            val requestBuilder = Request.Builder().url(fileUrl).get()
            if (cred.username.isNotEmpty()) {
                requestBuilder.header("Authorization", Credentials.basic(cred.username, cred.password))
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed: ${response.code}")
            }

            val totalSize = response.body?.contentLength() ?: -1L
            val actualDest = resolveConflict(localDest)
            var transferred = 0L

            response.body?.byteStream()?.use { input ->
                actualDest.outputStream().use { output ->
                    val buffer = ByteArray(HaronConstants.TRANSFER_BUFFER_SIZE)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        transferred += read
                        emit(WebDavTransferProgress(fileName, transferred, totalSize, isUpload = false))
                    }
                }
            }
            EcosystemLogger.d(HaronConstants.TAG, "WebDavManager: download completed $fileName")
        }
    }.flowOn(Dispatchers.IO)

    fun uploadFile(localSrc: File, remoteUrl: String): Flow<WebDavTransferProgress> = flow {
        mutex.withLock {
            val cred = credential ?: throw IllegalStateException("Not connected")
            val fileName = localSrc.name
            val totalSize = localSrc.length()
            EcosystemLogger.d(HaronConstants.TAG, "WebDavManager: upload started $fileName -> $remoteUrl")

            val body = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaType()
                override fun contentLength() = totalSize
                override fun writeTo(sink: BufferedSink) {
                    localSrc.inputStream().use { input ->
                        val buffer = ByteArray(HaronConstants.TRANSFER_BUFFER_SIZE)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            sink.write(buffer, 0, read)
                        }
                    }
                }
            }

            val requestBuilder = Request.Builder()
                .url(remoteUrl)
                .put(body)
            if (cred.username.isNotEmpty()) {
                requestBuilder.header("Authorization", Credentials.basic(cred.username, cred.password))
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 201 && response.code != 204) {
                throw IllegalStateException("Upload failed: ${response.code}")
            }
            emit(WebDavTransferProgress(fileName, totalSize, totalSize, isUpload = true))
            EcosystemLogger.d(HaronConstants.TAG, "WebDavManager: upload completed $fileName")
        }
    }.flowOn(Dispatchers.IO)

    suspend fun createDirectory(url: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val cred = credential
                        ?: return@withContext Result.failure(IllegalStateException("Not connected"))
                    val mkcolUrl = url.trimEnd('/') + "/"
                    EcosystemLogger.d(HaronConstants.TAG, "WebDavManager: MKCOL $mkcolUrl")

                    val requestBuilder = Request.Builder()
                        .url(mkcolUrl)
                        .method("MKCOL", null)
                    if (cred.username.isNotEmpty()) {
                        requestBuilder.header("Authorization", Credentials.basic(cred.username, cred.password))
                    }

                    val response = client.newCall(requestBuilder.build()).execute()
                    if (!response.isSuccessful && response.code != 201) {
                        return@withContext Result.failure(
                            IllegalStateException("MKCOL failed: ${response.code} ${response.message}")
                        )
                    }
                    EcosystemLogger.d(HaronConstants.TAG, "WebDavManager: directory created $mkcolUrl")
                    Result.success(Unit)
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "WebDavManager: createDirectory failed: ${e.message}")
                    Result.failure(e)
                }
            }
        }

    suspend fun delete(url: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val cred = credential
                        ?: return@withContext Result.failure(IllegalStateException("Not connected"))
                    EcosystemLogger.d(HaronConstants.TAG, "WebDavManager: DELETE $url")

                    val requestBuilder = Request.Builder()
                        .url(url)
                        .delete()
                    if (cred.username.isNotEmpty()) {
                        requestBuilder.header("Authorization", Credentials.basic(cred.username, cred.password))
                    }

                    val response = client.newCall(requestBuilder.build()).execute()
                    if (!response.isSuccessful && response.code != 204) {
                        return@withContext Result.failure(
                            IllegalStateException("DELETE failed: ${response.code} ${response.message}")
                        )
                    }
                    EcosystemLogger.d(HaronConstants.TAG, "WebDavManager: deleted $url")
                    Result.success(Unit)
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "WebDavManager: delete failed: ${e.message}")
                    Result.failure(e)
                }
            }
        }

    suspend fun rename(oldUrl: String, newName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val cred = credential
                        ?: return@withContext Result.failure(IllegalStateException("Not connected"))
                    val parentUrl = oldUrl.trimEnd('/').substringBeforeLast("/")
                    val destUrl = "$parentUrl/$newName"
                    EcosystemLogger.d(HaronConstants.TAG, "WebDavManager: MOVE $oldUrl -> $destUrl")

                    val requestBuilder = Request.Builder()
                        .url(oldUrl)
                        .method("MOVE", null)
                        .header("Destination", destUrl)
                        .header("Overwrite", "F")
                    if (cred.username.isNotEmpty()) {
                        requestBuilder.header("Authorization", Credentials.basic(cred.username, cred.password))
                    }

                    val response = client.newCall(requestBuilder.build()).execute()
                    if (!response.isSuccessful && response.code != 201 && response.code != 204) {
                        return@withContext Result.failure(
                            IllegalStateException("MOVE failed: ${response.code} ${response.message}")
                        )
                    }
                    EcosystemLogger.d(HaronConstants.TAG, "WebDavManager: renamed to $newName")
                    Result.success(Unit)
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "WebDavManager: rename failed: ${e.message}")
                    Result.failure(e)
                }
            }
        }

    /**
     * Verify connectivity by performing a PROPFIND on the base URL.
     */
    suspend fun verifyConnection(): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val cred = credential
                    ?: return@withContext Result.failure(IllegalStateException("Not connected"))
                val targetUrl = baseUrl.trimEnd('/') + "/"

                val propfindBody = """<?xml version="1.0" encoding="UTF-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
                    .toRequestBody("application/xml; charset=utf-8".toMediaType())

                val requestBuilder = Request.Builder()
                    .url(targetUrl)
                    .method("PROPFIND", propfindBody)
                    .header("Depth", "0")
                    .header("Content-Type", "application/xml; charset=utf-8")
                if (cred.username.isNotEmpty()) {
                    requestBuilder.header("Authorization", Credentials.basic(cred.username, cred.password))
                }

                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful && response.code != 207) {
                    return@withContext Result.failure(
                        IllegalStateException("Connection failed: ${response.code} ${response.message}")
                    )
                }
                EcosystemLogger.d(HaronConstants.TAG, "WebDavManager: connection verified to $targetUrl")
                Result.success(Unit)
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "WebDavManager: verifyConnection failed: ${e.message}")
                Result.failure(e)
            }
        }

    fun getBaseUrl(): String = baseUrl

    private fun parsePropfindResponse(xml: String, requestUrl: String): List<WebDavFileInfo> {
        val files = mutableListOf<WebDavFileInfo>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var href = ""
            var isDir = false
            var size = 0L
            var lastModified = 0L
            var contentType = ""
            var displayName = ""
            var inResponse = false

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val localName = parser.name
                        when (localName) {
                            "response" -> {
                                inResponse = true
                                href = ""
                                isDir = false
                                size = 0L
                                lastModified = 0L
                                contentType = ""
                                displayName = ""
                            }
                            "href" -> if (inResponse) href = readText(parser)
                            "collection" -> if (inResponse) isDir = true
                            "getcontentlength" -> if (inResponse) size = readText(parser).toLongOrNull() ?: 0L
                            "getlastmodified" -> if (inResponse) lastModified = parseHttpDate(readText(parser))
                            "getcontenttype" -> if (inResponse) contentType = readText(parser)
                            "displayname" -> if (inResponse) displayName = readText(parser)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "response" && inResponse) {
                            inResponse = false
                            // Skip the collection itself (first entry = the requested directory)
                            val normalizedHref = href.trimEnd('/')
                            val normalizedRequest = requestUrl.trimEnd('/')
                            if (normalizedHref != normalizedRequest && href.isNotEmpty()) {
                                val name = if (displayName.isNotEmpty()) displayName
                                else java.net.URLDecoder.decode(
                                    href.trimEnd('/').substringAfterLast("/"), "UTF-8"
                                )
                                files.add(
                                    WebDavFileInfo(
                                        name = name,
                                        path = href.trimEnd('/'),
                                        isDirectory = isDir,
                                        size = size,
                                        lastModified = lastModified,
                                        contentType = contentType
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "WebDavManager: XML parse error: ${e.message}")
        }

        return files.sortedWith(
            compareByDescending<WebDavFileInfo> { it.isDirectory }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.TEXT -> sb.append(parser.text ?: "")
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
            }
        }
        return sb.toString().trim()
    }

    private fun parseHttpDate(dateStr: String): Long {
        return try {
            val sdf = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US)
            sdf.parse(dateStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun resolveConflict(dest: File): File {
        if (!dest.exists()) return dest
        val baseName = dest.nameWithoutExtension
        val ext = dest.extension.let { if (it.isNotEmpty()) ".$it" else "" }
        var counter = 1
        var candidate: File
        do {
            candidate = File(dest.parentFile, "${baseName}($counter)$ext")
            counter++
        } while (candidate.exists())
        return candidate
    }
}
