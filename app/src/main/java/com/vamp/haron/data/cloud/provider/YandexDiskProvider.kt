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
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class YandexDiskProvider(
    private val context: Context,
    private val tokenStore: CloudTokenStore
) : CloudProviderInterface {

    companion object {
        const val CLIENT_ID = "15344409a0d246588f29e4c3af4492b5"
        const val CLIENT_SECRET = "806d0a878d674b9a9dea5148f46d0055"
        const val REDIRECT_URI = CloudOAuthHelper.REDIRECT_URI_YANDEX
        private const val AUTH_URL = "https://oauth.yandex.com/authorize"
        private const val TOKEN_URL = "https://oauth.yandex.com/token"
        private const val API_BASE = "https://cloud-api.yandex.net/v1/disk"
        private const val SCOPES = "cloud_api:disk.read cloud_api:disk.write cloud_api:disk.info"
    }

    override fun isAuthenticated(): Boolean {
        return tokenStore.load(CloudProvider.YANDEX_DISK) != null
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

    override suspend fun handleAuthCode(code: String): Result<Unit> = withContext(Dispatchers.IO) {
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

            tokenResult.onSuccess { token ->
                tokenStore.save(
                    CloudProvider.YANDEX_DISK,
                    CloudTokenStore.CloudTokens(
                        accessToken = token.accessToken,
                        refreshToken = token.refreshToken
                    )
                )
                // Fetch user info
                try {
                    val diskInfo = apiGet("/")
                    if (diskInfo != null) {
                        val userObj = diskInfo.optJSONObject("user")
                        val login = userObj?.optString("login", "") ?: ""
                        val displayName = userObj?.optString("display_name", "") ?: ""
                        tokenStore.save(
                            CloudProvider.YANDEX_DISK,
                            CloudTokenStore.CloudTokens(
                                accessToken = token.accessToken,
                                refreshToken = token.refreshToken,
                                email = login,
                                displayName = displayName
                            )
                        )
                        EcosystemLogger.d(HaronConstants.TAG, "YandexDisk: user=$login, displayName=$displayName")
                    }
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "YandexDisk: fetch user info failed: ${e.message}")
                }
            }
            tokenResult.map { }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "YandexDisk auth error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        tokenStore.remove(CloudProvider.YANDEX_DISK)
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

                // Build target path
                val targetDiskPath = when {
                    cloudDirPath.isEmpty() || cloudDirPath == "/" -> "disk:/$fileName"
                    cloudDirPath.startsWith("disk:") -> "$cloudDirPath/$fileName"
                    else -> "disk:/$cloudDirPath/$fileName"
                }
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk upload: targetDiskPath=$targetDiskPath")

                // Retry wrapper: 3 attempts with exponential backoff
                val maxAttempts = 3
                var lastError: Exception? = null
                for (attempt in 1..maxAttempts) {
                    try {
                        EcosystemLogger.d(HaronConstants.TAG, "YandexDisk upload: attempt $attempt/$maxAttempts")
                        uploadFileAttempt(file, targetDiskPath, fileName, totalSize) { written ->
                            trySend(CloudTransferProgress(fileName, written, totalSize))
                        }
                        lastError = null
                        EcosystemLogger.d(HaronConstants.TAG, "YandexDisk upload: attempt $attempt SUCCEEDED")
                        break // success
                    } catch (e: Exception) {
                        lastError = e
                        EcosystemLogger.e(HaronConstants.TAG, "YandexDisk upload: attempt $attempt/$maxAttempts FAILED: ${e.javaClass.simpleName}: ${e.message}")
                        if (attempt < maxAttempts) {
                            val backoffMs = attempt * 2000L // 2s, 4s
                            EcosystemLogger.d(HaronConstants.TAG, "YandexDisk upload: refreshing token, backoff ${backoffMs}ms before retry...")
                            refreshToken()
                            delay(backoffMs)
                        }
                    }
                }
                if (lastError != null) throw lastError

                send(CloudTransferProgress(fileName, totalSize, totalSize, isComplete = true))
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk upload: COMPLETE $fileName ($totalSize bytes)")
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "YandexDisk upload FINAL ERROR: ${e.javaClass.simpleName}: ${e.message}")
                e.cause?.let { cause ->
                    EcosystemLogger.e(HaronConstants.TAG, "YandexDisk upload cause: ${cause.javaClass.simpleName}: ${cause.message}")
                }
                send(CloudTransferProgress(fileName, 0, 0, isComplete = true, error = e.message))
            }
        }
    }

    /** Single upload attempt. Throws on failure. Uses chunked upload for files >100MB. */
    private fun uploadFileAttempt(
        file: File,
        targetDiskPath: String,
        fileName: String,
        totalSize: Long,
        onProgress: (Long) -> Unit
    ) {
        // Step 1: get upload URL
        val encodedPath = URLEncoder.encode(targetDiskPath, "UTF-8")
        EcosystemLogger.d(HaronConstants.TAG, "YandexDisk uploadAttempt: Step 1 — getting upload URL for $targetDiskPath")
        val startTime = System.currentTimeMillis()
        val linkJson = apiGet("/resources/upload?path=$encodedPath&overwrite=true")
            ?: throw Exception("Failed to get upload link")
        val href = linkJson.optString("href", "")
        if (href.isEmpty()) throw Exception("Empty upload href")
        EcosystemLogger.d(HaronConstants.TAG, "YandexDisk uploadAttempt: got upload URL in ${System.currentTimeMillis() - startTime}ms, href length=${href.length}")

        // Step 2: PUT file to href
        val isChunked = totalSize > 100 * 1024 * 1024
        EcosystemLogger.d(HaronConstants.TAG, "YandexDisk uploadAttempt: Step 2 — PUT file, chunked=$isChunked, size=${totalSize / 1024}KB")
        val url = URL(href)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            setRequestProperty("Content-Type", "application/octet-stream")
            setRequestProperty("Connection", "keep-alive")
            setRequestProperty("Keep-Alive", "timeout=600")
            doOutput = true
            if (isChunked) {
                setChunkedStreamingMode(4 * 1024 * 1024) // 4MB chunks
            } else {
                setFixedLengthStreamingMode(totalSize)
            }
            connectTimeout = 60_000
            readTimeout = 600_000 // 10 min for large files
        }

        try {
            val putStartTime = System.currentTimeMillis()
            FileInputStream(file).use { fis ->
                BufferedOutputStream(conn.outputStream, 262144).use { out ->
                    val buffer = ByteArray(262144)
                    var bytesRead: Int
                    var totalWritten = 0L
                    var lastEmitPercent = -1
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        totalWritten += bytesRead
                        val percent = if (totalSize > 0) ((totalWritten * 100) / totalSize).toInt() else 0
                        if (percent != lastEmitPercent) {
                            out.flush()
                            onProgress(totalWritten)
                            lastEmitPercent = percent
                            if (percent % 10 == 0) {
                                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk uploadAttempt: $fileName $percent% ($totalWritten/$totalSize)")
                            }
                        }
                    }
                }
            }
            val putElapsed = System.currentTimeMillis() - putStartTime
            EcosystemLogger.d(HaronConstants.TAG, "YandexDisk uploadAttempt: PUT body sent in ${putElapsed}ms, reading response...")
            val code = conn.responseCode
            EcosystemLogger.d(HaronConstants.TAG, "YandexDisk uploadAttempt: HTTP response code=$code")
            if (code !in 200..299) {
                val errorBody = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
                EcosystemLogger.e(HaronConstants.TAG, "YandexDisk uploadAttempt: FAILED HTTP $code, body=$errorBody")
                throw Exception("Upload failed: HTTP $code, $errorBody")
            }
            EcosystemLogger.d(HaronConstants.TAG, "YandexDisk uploadAttempt: SUCCESS, total time=${System.currentTimeMillis() - startTime}ms")
        } finally {
            conn.disconnect()
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
        return tokenStore.load(CloudProvider.YANDEX_DISK)?.accessToken
    }

    override suspend fun getFreshAccessToken(): String? = withContext(Dispatchers.IO) {
        try {
            refreshToken()
            tokenStore.load(CloudProvider.YANDEX_DISK)?.accessToken
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "YandexDisk getFreshAccessToken failed: ${e.message}")
            tokenStore.load(CloudProvider.YANDEX_DISK)?.accessToken
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

                // Pre-refresh token
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk updateFileContent: pre-refreshing token...")
                refreshToken()

                val diskPath = if (cloudFileId.startsWith("disk:")) cloudFileId else "disk:/$cloudFileId"
                EcosystemLogger.d(HaronConstants.TAG, "YandexDisk updateFileContent: diskPath=$diskPath")

                // Retry wrapper: 3 attempts with exponential backoff
                val maxAttempts = 3
                var lastError: Exception? = null
                for (attempt in 1..maxAttempts) {
                    try {
                        EcosystemLogger.d(HaronConstants.TAG, "YandexDisk updateFileContent: attempt $attempt/$maxAttempts")
                        uploadFileAttempt(file, diskPath, fileName, totalSize) { written ->
                            trySend(CloudTransferProgress(fileName, written, totalSize))
                        }
                        lastError = null
                        EcosystemLogger.d(HaronConstants.TAG, "YandexDisk updateFileContent: attempt $attempt SUCCEEDED")
                        break
                    } catch (e: Exception) {
                        lastError = e
                        EcosystemLogger.e(HaronConstants.TAG, "YandexDisk updateFileContent: attempt $attempt/$maxAttempts FAILED: ${e.javaClass.simpleName}: ${e.message}")
                        if (attempt < maxAttempts) {
                            val backoffMs = attempt * 2000L
                            refreshToken()
                            delay(backoffMs)
                        }
                    }
                }
                if (lastError != null) throw lastError

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
        val tokens = tokenStore.load(CloudProvider.YANDEX_DISK) ?: return@withContext false
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
                tokenStore.save(
                    CloudProvider.YANDEX_DISK,
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
        val token = tokenStore.load(CloudProvider.YANDEX_DISK)?.accessToken ?: return null
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
        val token = tokenStore.load(CloudProvider.YANDEX_DISK)?.accessToken
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
