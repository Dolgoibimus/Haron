package com.vamp.haron.data.cloud.provider

import android.content.Context
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.cloud.CloudOAuthHelper
import com.vamp.haron.data.cloud.CloudTokenStore
import com.vamp.haron.domain.model.CloudFileEntry
import com.vamp.haron.domain.model.CloudProvider
import com.vamp.haron.domain.model.CloudTransferProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class YandexDiskProvider(
    private val context: Context,
    private val tokenStore: CloudTokenStore,
    private val tokenKey: String
) : CloudProviderInterface {

    companion object {
        const val CLIENT_ID = "15344409a0d246588f29e4c3af4492b5"
        const val CLIENT_SECRET = "806d0a878d674b9a9dea5148f46d0055"
        const val REDIRECT_URI = CloudOAuthHelper.REDIRECT_URI_YANDEX
        private const val AUTH_URL = "https://oauth.yandex.ru/authorize"
        private const val TOKEN_URL = "https://oauth.yandex.ru/token"
        private const val API_BASE = "https://cloud-api.yandex.net/v1/disk"
        private const val SCOPES = "cloud_api:disk.read cloud_api:disk.write cloud_api:disk.info"
        private const val UPLOAD_CHUNK_SIZE = 10L * 1024 * 1024 // 10MB chunks for Content-Range upload

        /** Shared OkHttpClient for uploads — better connection management than HttpURLConnection */
        val uploadClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.MINUTES)
            .readTimeout(10, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build()
    }

    override fun isAuthenticated(): Boolean {
        return tokenKey.isNotEmpty() && tokenStore.loadByKey(tokenKey) != null
    }

    override fun getAuthUrl(): String? {
        val verifier = CloudOAuthHelper.generateCodeVerifier(CloudProvider.YANDEX_DISK.scheme)
        val challenge = CloudOAuthHelper.generateCodeChallenge(verifier)
        return "$AUTH_URL" +
            "?client_id=$CLIENT_ID" +
            "&response_type=code" +
            "&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
            "&scope=${URLEncoder.encode(SCOPES, "UTF-8")}" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256" +
            "&force_confirm=true"
    }

    override suspend fun handleAuthCode(code: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            EcosystemLogger.d(HaronConstants.TAG, "YandexDisk: handleAuthCode start")
            val verifier = CloudOAuthHelper.getCodeVerifier(CloudProvider.YANDEX_DISK.scheme)
                ?: return@withContext Result.failure(Exception("No PKCE code verifier"))

            val tokenResult = CloudOAuthHelper.exchangeCodeForToken(
                TOKEN_URL,
                mapOf(
                    "client_id" to CLIENT_ID,
                    "client_secret" to CLIENT_SECRET,
                    "code" to code,
                    "code_verifier" to verifier,
                    "grant_type" to "authorization_code"
                )
            )
            CloudOAuthHelper.clearCodeVerifier(CloudProvider.YANDEX_DISK.scheme)

            val token = tokenResult.getOrElse { return@withContext Result.failure(it) }

            // Save with pending key, fetch user info, then save with final key
            val pendingKey = "${CloudProvider.YANDEX_DISK.scheme}:_pending"
            tokenStore.saveByKey(
                pendingKey,
                CloudTokenStore.CloudTokens(
                    accessToken = token.accessToken,
                    refreshToken = token.refreshToken
                )
            )

            var login = ""
            var displayName = ""
            try {
                // Direct API call with new token to get user info
                val url = java.net.URL("$API_BASE/")
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "OAuth ${token.accessToken}")
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                try {
                    val responseCode = conn.responseCode
                    if (responseCode in 200..299) {
                        val json = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
                        val userObj = json.optJSONObject("user")
                        login = userObj?.optString("login", "") ?: ""
                        displayName = userObj?.optString("display_name", "") ?: ""
                    }
                } finally {
                    conn.disconnect()
                }
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk: user=$login, displayName=$displayName")
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "YandexDisk: fetch user info failed: ${e.message}")
            }

            tokenStore.removeByKey(pendingKey)
            val finalKey = "${CloudProvider.YANDEX_DISK.scheme}:${login.ifEmpty { "account" }}"
            tokenStore.saveByKey(
                finalKey,
                CloudTokenStore.CloudTokens(
                    accessToken = token.accessToken,
                    refreshToken = token.refreshToken,
                    email = login,
                    displayName = displayName
                )
            )

            EcosystemLogger.d(HaronConstants.TAG, "YandexDisk auth success: accountId=$finalKey, login=$login")
            Result.success(finalKey)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "YandexDisk auth error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        if (tokenKey.isNotEmpty()) {
            tokenStore.removeByKey(tokenKey)
        }
        EcosystemLogger.d(HaronConstants.TAG, "YandexDisk: signed out")
    }

    override suspend fun listFiles(path: String): Result<List<CloudFileEntry>> = withContext(Dispatchers.IO) {
        try {
            val diskPath = when {
                path.isEmpty() || path == "/" -> "disk:/"
                path.startsWith("disk:") -> path
                else -> "disk:/$path"
            }
            EcosystemLogger.d(HaronConstants.TAG, "YandexDisk listFiles: path='$path', diskPath='$diskPath'")

            val allEntries = mutableListOf<CloudFileEntry>()
            var offset = 0
            val limit = 500

            while (true) {
                val encodedPath = URLEncoder.encode(diskPath, "UTF-8")
                val json = withRefresh {
                    apiGet("/resources?path=$encodedPath&limit=$limit&offset=$offset")
                } ?: return@withContext Result.failure(Exception("Failed to list files"))

                val embedded = json.optJSONObject("_embedded")
                    ?: return@withContext Result.success(emptyList())
                val items = embedded.optJSONArray("items")
                    ?: return@withContext Result.success(allEntries)

                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val isDir = item.optString("type") == "dir"
                    val itemPath = item.optString("path", "")
                    val relativePath = itemPath.removePrefix("disk:/")

                    val childCount = -1 // populated below via parallel requests

                    allEntries.add(
                        CloudFileEntry(
                            id = relativePath,
                            name = item.optString("name", ""),
                            path = relativePath,
                            isDirectory = isDir,
                            size = item.optLong("size", 0L),
                            lastModified = parseIso8601(item.optString("modified", "")),
                            mimeType = item.optString("mime_type", "").ifEmpty { null },
                            provider = CloudProvider.YANDEX_DISK,
                            childCount = childCount,
                            thumbnailUrl = item.optString("preview", "").ifEmpty { null }
                                ?.let { url ->
                                    // Request larger preview: replace size=S with size=XL (800px)
                                    if ("size=" in url) url.replace(Regex("size=\\w+"), "size=XL")
                                    else if ("?" in url) "$url&size=XL"
                                    else "$url?size=XL"
                                }
                        )
                    )
                }

                val total = embedded.optInt("total", 0)
                offset += items.length()
                if (offset >= total) break
            }

            // Fetch childCount for folders in parallel (limit=0 → only metadata)
            val folders = allEntries.filter { it.isDirectory }
            if (folders.isNotEmpty()) {
                val counts = coroutineScope {
                    folders.map { folder ->
                        async(Dispatchers.IO) {
                            try {
                                val ep = URLEncoder.encode("disk:/${folder.path}", "UTF-8")
                                val meta = apiGet("/resources?path=$ep&limit=0")
                                val cnt = meta?.optJSONObject("_embedded")?.optInt("total", 0) ?: 0
                                folder.path to cnt
                            } catch (_: Exception) {
                                folder.path to 0
                            }
                        }
                    }.awaitAll()
                }.toMap()
                for (i in allEntries.indices) {
                    val e = allEntries[i]
                    if (e.isDirectory && e.path in counts) {
                        allEntries[i] = e.copy(childCount = counts[e.path] ?: 0)
                    }
                }
            }

            EcosystemLogger.d(HaronConstants.TAG, "YandexDisk listFiles: total ${allEntries.size} items")
            Result.success(allEntries)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "YandexDisk listFiles error: ${e.message}")
            Result.failure(e)
        }
    }

    override fun downloadFile(cloudFileId: String, localPath: String): Flow<CloudTransferProgress> = channelFlow {
        withContext(Dispatchers.IO) {
            try {
                val diskPath = if (cloudFileId.startsWith("disk:")) cloudFileId else "disk:/$cloudFileId"
                val fileName = cloudFileId.substringAfterLast('/')
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk download: path=$diskPath")

                send(CloudTransferProgress(fileName, 0, 0))

                // Step 1: get download URL
                val encodedPath = URLEncoder.encode(diskPath, "UTF-8")
                val linkJson = withRefreshSuspend {
                    apiGet("/resources/download?path=$encodedPath")
                } ?: throw Exception("Failed to get download link")

                val href = linkJson.optString("href", "")
                if (href.isEmpty()) throw Exception("Empty download href")

                // Step 2: download from href
                val url = URL(href)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    connectTimeout = 30_000
                    readTimeout = 60_000
                }

                try {
                    val totalSize = conn.contentLengthLong.let { if (it < 0) 0L else it }
                    send(CloudTransferProgress(fileName, 0, totalSize))

                    BufferedInputStream(conn.inputStream, 262144).use { input ->
                        BufferedOutputStream(FileOutputStream(File(localPath)), 262144).use { fos ->
                            val buffer = ByteArray(262144)
                            var bytesRead: Int
                            var totalRead = 0L
                            var lastEmitPercent = -1
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                fos.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                val percent = if (totalSize > 0) ((totalRead * 100) / totalSize).toInt() else 0
                                if (percent != lastEmitPercent) {
                                    trySend(CloudTransferProgress(fileName, totalRead, totalSize))
                                    lastEmitPercent = percent
                                }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }

                val fileSize = File(localPath).length()
                send(CloudTransferProgress(fileName, fileSize, fileSize, isComplete = true))
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk download complete: $fileName, ${fileSize}B")
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "YandexDisk download error: ${e.message}")
                send(CloudTransferProgress("", 0, 0, isComplete = true, error = e.message))
            }
        }
    }

    override fun uploadFile(localPath: String, cloudDirPath: String, fileName: String): Flow<CloudTransferProgress> = channelFlow {
        withContext(Dispatchers.IO) {
            try {
                val file = File(localPath)
                val totalSize = file.length()
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk upload: START fileName=$fileName, localPath=$localPath, cloudDirPath=$cloudDirPath, size=$totalSize (${totalSize / 1024}KB)")

                if (!file.exists()) {
                    EcosystemLogger.e(HaronConstants.TAG, "YandexDisk upload: file does not exist: $localPath")
                    send(CloudTransferProgress(fileName, 0, 0, isComplete = true, error = "File not found: $localPath"))
                    return@withContext
                }
                if (!file.canRead()) {
                    EcosystemLogger.e(HaronConstants.TAG, "YandexDisk upload: cannot read file: $localPath")
                    send(CloudTransferProgress(fileName, 0, 0, isComplete = true, error = "Cannot read file: $localPath"))
                    return@withContext
                }

                send(CloudTransferProgress(fileName, 0, totalSize))

                // Pre-refresh token before upload
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk upload: pre-refreshing token...")
                val refreshed = refreshToken()
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk upload: pre-refresh result=$refreshed")

                // Upload as .tmp to bypass MIME-type throttling (.mp4/.avi → 128KB/s)
                // then rename to real name after upload completes
                val ext = fileName.substringAfterLast('.', "")
                val throttledExts = setOf("mp4", "avi", "mkv", "wmv", "zip", "rar", "7z", "tar", "gz", "db", "dat")
                val useTemp = ext.lowercase() in throttledExts
                val uploadName = if (useTemp) "${fileName}.tmp" else fileName

                val targetDiskPath = when {
                    cloudDirPath.isEmpty() || cloudDirPath == "/" -> "disk:/$uploadName"
                    cloudDirPath.startsWith("disk:") -> "$cloudDirPath/$uploadName"
                    else -> "disk:/$cloudDirPath/$uploadName"
                }
                val finalDiskPath = if (useTemp) {
                    targetDiskPath.removeSuffix(".tmp")
                } else targetDiskPath
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk upload: targetDiskPath=$targetDiskPath, useTemp=$useTemp")

                // Step 1: get upload URL (lives 30 minutes)
                val encodedPath = URLEncoder.encode(targetDiskPath, "UTF-8")
                val linkJson = apiGet("/resources/upload?path=$encodedPath&overwrite=true")
                    ?: throw Exception("Failed to get upload link")
                val href = linkJson.optString("href", "")
                if (href.isEmpty()) throw Exception("Empty upload href")
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk upload: got upload URL, href length=${href.length}")

                // Step 2: chunked upload with Content-Range (per-chunk retry)
                val chunkSize = UPLOAD_CHUNK_SIZE
                val totalChunks = ((totalSize + chunkSize - 1) / chunkSize).toInt()
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk upload: $totalChunks chunks of ${chunkSize / 1024 / 1024}MB each")

                var offset = 0L
                var chunkNum = 0
                val startTime = System.currentTimeMillis()

                while (offset < totalSize) {
                    chunkNum++
                    val end = minOf(offset + chunkSize - 1, totalSize - 1)
                    val chunkLen = end - offset + 1
                    val contentRange = "bytes $offset-$end/$totalSize"

                    // Per-chunk retry: 5 attempts
                    var chunkSuccess = false
                    for (attempt in 1..5) {
                        try {
                            EcosystemLogger.d(HaronConstants.TAG, "YandexDisk upload: chunk $chunkNum/$totalChunks [$contentRange] attempt $attempt")

                            val chunkBody = object : RequestBody() {
                                override fun contentType() = "application/octet-stream".toMediaType()
                                override fun contentLength() = chunkLen

                                override fun writeTo(sink: BufferedSink) {
                                    java.io.RandomAccessFile(file, "r").use { raf ->
                                        raf.seek(offset)
                                        val buffer = ByteArray(65536)
                                        var remaining = chunkLen
                                        while (remaining > 0) {
                                            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                                            val read = raf.read(buffer, 0, toRead)
                                            if (read <= 0) break
                                            sink.write(buffer, 0, read)
                                            remaining -= read
                                        }
                                    }
                                }
                            }

                            val request = Request.Builder()
                                .url(href)
                                .put(chunkBody)
                                .header("Content-Range", contentRange)
                                .build()

                            uploadClient.newCall(request).execute().use { response ->
                                val code = response.code
                                if (code !in 200..299) {
                                    val errorBody = response.body?.string()
                                    throw Exception("HTTP $code: $errorBody")
                                }
                            }
                            chunkSuccess = true
                            break
                        } catch (e: Exception) {
                            EcosystemLogger.e(HaronConstants.TAG, "YandexDisk upload: chunk $chunkNum attempt $attempt FAILED: ${e.javaClass.simpleName}: ${e.message}")
                            if (attempt < 5) {
                                val backoff = attempt * 2000L
                                delay(backoff)
                                if (attempt >= 3) refreshToken()
                            }
                        }
                    }
                    if (!chunkSuccess) throw Exception("Chunk $chunkNum failed after 5 attempts")

                    offset = end + 1
                    val elapsed = System.currentTimeMillis() - startTime
                    val percent = ((offset * 100) / totalSize).toInt()
                    EcosystemLogger.d(HaronConstants.TAG, "YandexDisk upload: chunk $chunkNum/$totalChunks OK, $percent% (${offset / 1024}KB/${totalSize / 1024}KB) ${elapsed}ms")
                    trySend(CloudTransferProgress(fileName, offset, totalSize))
                }

                // Step 3: rename .tmp → real name (if throttling bypass was used)
                if (useTemp) {
                    EcosystemLogger.d(HaronConstants.TAG, "YandexDisk upload: renaming $targetDiskPath → $finalDiskPath")
                    val from = URLEncoder.encode(targetDiskPath, "UTF-8")
                    val to = URLEncoder.encode(finalDiskPath, "UTF-8")
                    apiRequest("POST", "/resources/move?from=$from&path=$to&overwrite=true", null)
                }

                val totalElapsed = System.currentTimeMillis() - startTime
                send(CloudTransferProgress(fileName, totalSize, totalSize, isComplete = true))
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk upload: COMPLETE $fileName ($totalSize bytes) in ${totalElapsed}ms")
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "YandexDisk upload FINAL ERROR: ${e.javaClass.simpleName}: ${e.message}")
                e.cause?.let { cause ->
                    EcosystemLogger.e(HaronConstants.TAG, "YandexDisk upload cause: ${cause.javaClass.simpleName}: ${cause.message}")
                }
                send(CloudTransferProgress(fileName, 0, 0, isComplete = true, error = e.message))
            }
        }
    }

    override suspend fun delete(cloudFileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val diskPath = if (cloudFileId.startsWith("disk:")) cloudFileId else "disk:/$cloudFileId"
            EcosystemLogger.d(HaronConstants.TAG, "YandexDisk delete: $diskPath")

            val encodedPath = URLEncoder.encode(diskPath, "UTF-8")
            val result = withRefresh {
                apiRequest("DELETE", "/resources?path=$encodedPath&permanently=false", null)
            }

            if (result.first in 200..299 || result.first == 204) {
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk delete success: $diskPath")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Delete failed: HTTP ${result.first}"))
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "YandexDisk delete error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun createFolder(parentPath: String, name: String): Result<CloudFileEntry> = withContext(Dispatchers.IO) {
        try {
            val folderPath = when {
                parentPath.isEmpty() || parentPath == "/" -> "disk:/$name"
                parentPath.startsWith("disk:") -> "$parentPath/$name"
                else -> "disk:/$parentPath/$name"
            }
            EcosystemLogger.d(HaronConstants.TAG, "YandexDisk createFolder: $folderPath")

            val encodedPath = URLEncoder.encode(folderPath, "UTF-8")
            val result = withRefresh {
                apiRequest("PUT", "/resources?path=$encodedPath", null)
            }

            if (result.first in 200..299) {
                val relativePath = folderPath.removePrefix("disk:/")
                Result.success(
                    CloudFileEntry(
                        id = relativePath,
                        name = name,
                        path = relativePath,
                        isDirectory = true,
                        provider = CloudProvider.YANDEX_DISK
                    )
                )
            } else {
                Result.failure(Exception("Create folder failed: HTTP ${result.first}, ${result.second}"))
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "YandexDisk createFolder error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun rename(cloudFileId: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val diskPath = if (cloudFileId.startsWith("disk:")) cloudFileId else "disk:/$cloudFileId"
            val parentDir = diskPath.substringBeforeLast('/')
            val newPath = "$parentDir/$newName"
            EcosystemLogger.d(HaronConstants.TAG, "YandexDisk rename: $diskPath → $newPath")

            val encodedFrom = URLEncoder.encode(diskPath, "UTF-8")
            val encodedTo = URLEncoder.encode(newPath, "UTF-8")
            val result = withRefresh {
                apiRequest("POST", "/resources/move?from=$encodedFrom&path=$encodedTo&overwrite=false", null)
            }

            if (result.first in 200..299) {
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk rename success")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Rename failed: HTTP ${result.first}"))
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "YandexDisk rename error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun moveFile(
        fileId: String,
        currentParentId: String,
        newParentId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val fromPath = if (fileId.startsWith("disk:")) fileId else "disk:/$fileId"
            val fileName = fromPath.substringAfterLast('/')
            val toParent = when {
                newParentId.isEmpty() || newParentId == "/" -> "disk:/"
                newParentId.startsWith("disk:") -> newParentId
                else -> "disk:/$newParentId"
            }
            val toPath = "$toParent/$fileName"
            EcosystemLogger.d(HaronConstants.TAG, "YandexDisk moveFile: $fromPath → $toPath")

            val encodedFrom = URLEncoder.encode(fromPath, "UTF-8")
            val encodedTo = URLEncoder.encode(toPath, "UTF-8")
            val result = withRefresh {
                apiRequest("POST", "/resources/move?from=$encodedFrom&path=$encodedTo&overwrite=false", null)
            }

            if (result.first in 200..299) {
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk moveFile success")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Move failed: HTTP ${result.first}"))
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "YandexDisk moveFile error: ${e.message}")
            Result.failure(e)
        }
    }

    override fun getAccessToken(): String? {
        return tokenStore.loadByKey(tokenKey)?.accessToken
    }

    override suspend fun getFreshAccessToken(): String? = withContext(Dispatchers.IO) {
        try {
            refreshToken()
            tokenStore.loadByKey(tokenKey)?.accessToken
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "YandexDisk getFreshAccessToken failed: ${e.message}")
            tokenStore.loadByKey(tokenKey)?.accessToken
        }
    }

    override fun updateFileContent(cloudFileId: String, localPath: String): Flow<CloudTransferProgress> = channelFlow {
        withContext(Dispatchers.IO) {
            try {
                val file = File(localPath)
                val totalSize = file.length()
                val fileName = file.name
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk updateFileContent: START cloudFileId=$cloudFileId, localPath=$localPath, size=$totalSize (${totalSize / 1024}KB)")

                send(CloudTransferProgress(fileName, 0, totalSize))

                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk updateFileContent: pre-refreshing token...")
                refreshToken()

                val diskPath = if (cloudFileId.startsWith("disk:")) cloudFileId else "disk:/$cloudFileId"

                // Get upload URL
                val encodedPath = URLEncoder.encode(diskPath, "UTF-8")
                val linkJson = apiGet("/resources/upload?path=$encodedPath&overwrite=true")
                    ?: throw Exception("Failed to get upload link")
                val href = linkJson.optString("href", "")
                if (href.isEmpty()) throw Exception("Empty upload href")

                // Chunked upload with Content-Range + per-chunk retry
                val chunkSize = UPLOAD_CHUNK_SIZE
                var offset = 0L
                var chunkNum = 0
                val totalChunks = ((totalSize + chunkSize - 1) / chunkSize).toInt()

                while (offset < totalSize) {
                    chunkNum++
                    val end = minOf(offset + chunkSize - 1, totalSize - 1)
                    val chunkLen = end - offset + 1
                    val contentRange = "bytes $offset-$end/$totalSize"

                    var chunkSuccess = false
                    for (attempt in 1..5) {
                        try {
                            val chunkBody = object : RequestBody() {
                                override fun contentType() = "application/octet-stream".toMediaType()
                                override fun contentLength() = chunkLen
                                override fun writeTo(sink: BufferedSink) {
                                    java.io.RandomAccessFile(file, "r").use { raf ->
                                        raf.seek(offset)
                                        val buffer = ByteArray(65536)
                                        var remaining = chunkLen
                                        while (remaining > 0) {
                                            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                                            val read = raf.read(buffer, 0, toRead)
                                            if (read <= 0) break
                                            sink.write(buffer, 0, read)
                                            remaining -= read
                                        }
                                    }
                                }
                            }

                            val request = Request.Builder()
                                .url(href)
                                .put(chunkBody)
                                .header("Content-Range", contentRange)
                                .build()

                            uploadClient.newCall(request).execute().use { response ->
                                if (response.code !in 200..299) {
                                    throw Exception("HTTP ${response.code}: ${response.body?.string()}")
                                }
                            }
                            chunkSuccess = true
                            break
                        } catch (e: Exception) {
                            EcosystemLogger.e(HaronConstants.TAG, "YandexDisk updateFileContent: chunk $chunkNum attempt $attempt FAILED: ${e.message}")
                            if (attempt < 5) delay(attempt * 2000L)
                        }
                    }
                    if (!chunkSuccess) throw Exception("Chunk $chunkNum failed after 5 attempts")

                    offset = end + 1
                    trySend(CloudTransferProgress(fileName, offset, totalSize))
                    EcosystemLogger.d(HaronConstants.TAG, "YandexDisk updateFileContent: chunk $chunkNum/$totalChunks OK, ${(offset * 100 / totalSize)}%")
                }

                send(CloudTransferProgress(fileName, totalSize, totalSize, isComplete = true))
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk updateFileContent: COMPLETE $fileName ($totalSize bytes)")
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "YandexDisk updateFileContent FINAL ERROR: ${e.javaClass.simpleName}: ${e.message}")
                send(CloudTransferProgress("", 0, 0, isComplete = true, error = e.message))
            }
        }
    }

    // ── Token refresh ──────────────────────────────────────────────────

    private suspend fun refreshToken(): Boolean = withContext(Dispatchers.IO) {
        val tokens = tokenStore.loadByKey(tokenKey) ?: return@withContext false
        val refreshTk = tokens.refreshToken ?: return@withContext false
        EcosystemLogger.d(HaronConstants.TAG, "YandexDisk: refreshing access token...")

        try {
            val result = CloudOAuthHelper.exchangeCodeForToken(
                TOKEN_URL,
                mapOf(
                    "client_id" to CLIENT_ID,
                    "client_secret" to CLIENT_SECRET,
                    "refresh_token" to refreshTk,
                    "grant_type" to "refresh_token"
                )
            )
            result.onSuccess { newToken ->
                tokenStore.saveByKey(
                    tokenKey,
                    CloudTokenStore.CloudTokens(
                        accessToken = newToken.accessToken,
                        refreshToken = newToken.refreshToken ?: refreshTk,
                        email = tokens.email,
                        displayName = tokens.displayName
                    )
                )
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk: token refreshed successfully")
            }
            result.isSuccess
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "YandexDisk: token refresh failed: ${e.message}")
            false
        }
    }

    /** Execute API call with automatic token refresh on 401 */
    private suspend fun <T> withRefresh(block: () -> T): T {
        return try {
            block()
        } catch (e: YandexAuthException) {
            EcosystemLogger.d(HaronConstants.TAG, "YandexDisk: token expired, refreshing...")
            if (refreshToken()) {
                block()
            } else throw e
        }
    }

    /** Suspend variant of withRefresh */
    private suspend fun <T> withRefreshSuspend(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: YandexAuthException) {
            EcosystemLogger.d(HaronConstants.TAG, "YandexDisk: token expired (suspend), refreshing...")
            if (refreshToken()) {
                block()
            } else throw e
        }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────

    private class YandexAuthException(message: String) : Exception(message)

    /** GET request to Yandex Disk API, returns parsed JSON */
    private fun apiGet(endpoint: String): JSONObject? {
        val token = tokenStore.loadByKey(tokenKey)?.accessToken ?: return null
        val url = URL("$API_BASE$endpoint")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "OAuth $token")
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        return try {
            val code = conn.responseCode
            if (code == 401) throw YandexAuthException("Token expired")
            if (code in 200..299) {
                JSONObject(conn.inputStream.bufferedReader().readText())
            } else {
                val error = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
                EcosystemLogger.e(HaronConstants.TAG, "YandexDisk API error: $code for $endpoint, $error")
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Generic HTTP request to Yandex Disk API, returns (responseCode, responseBody) */
    private fun apiRequest(method: String, endpoint: String, body: String?): Pair<Int, String?> {
        val token = tokenStore.loadByKey(tokenKey)?.accessToken
            ?: throw YandexAuthException("Not authenticated")
        val url = URL("$API_BASE$endpoint")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "OAuth $token")
            connectTimeout = 15_000
            readTimeout = 30_000
            if (body != null) {
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
        }
        return try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            if (code == 401) throw YandexAuthException("Token expired")
            val respBody = try {
                if (code in 200..299) conn.inputStream.bufferedReader().readText()
                else conn.errorStream?.bufferedReader()?.readText()
            } catch (_: Exception) { null }
            code to respBody
        } finally {
            conn.disconnect()
        }
    }

    private fun parseIso8601(dateStr: String): Long {
        return try {
            java.time.OffsetDateTime.parse(dateStr).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.LocalDateTime.parse(dateStr.replace("+0000", ""))
                    .toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            } catch (_: Exception) { 0L }
        }
    }
}
