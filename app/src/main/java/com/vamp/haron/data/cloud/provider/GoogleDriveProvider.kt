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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GoogleDriveProvider(
    private val context: Context,
    private val tokenStore: CloudTokenStore,
    private val tokenKey: String
) : CloudProviderInterface {

    companion object {
        val CLIENT_ID: String get() = com.vamp.haron.BuildConfig.GOOGLE_CLIENT_ID
        val CLIENT_SECRET: String get() = com.vamp.haron.BuildConfig.GOOGLE_CLIENT_SECRET
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val API_BASE = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
        private const val CHUNK_SIZE = 10 * 1024 * 1024 // 10MB chunks
    }

    override fun isAuthenticated(): Boolean {
        return tokenKey.isNotEmpty() && tokenStore.loadByKey(tokenKey) != null
    }

    override fun getAuthUrl(): String? {
        val verifier = CloudOAuthHelper.generateCodeVerifier(CloudProvider.GOOGLE_DRIVE.scheme)
        val challenge = CloudOAuthHelper.generateCodeChallenge(verifier)
        return "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=$CLIENT_ID" +
            "&redirect_uri=${URLEncoder.encode(CloudOAuthHelper.REDIRECT_URI_GDRIVE, "UTF-8")}" +
            "&response_type=code" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256" +
            "&scope=${URLEncoder.encode("https://www.googleapis.com/auth/drive", "UTF-8")}" +
            "&access_type=offline" +
            "&prompt=consent"
    }

    override suspend fun handleAuthCode(code: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val verifier = CloudOAuthHelper.getCodeVerifier(CloudProvider.GOOGLE_DRIVE.scheme)
                ?: return@withContext Result.failure(Exception("No PKCE code verifier"))

            val tokenResult = CloudOAuthHelper.exchangeCodeForToken(
                TOKEN_URL,
                mapOf(
                    "client_id" to CLIENT_ID,
                    "client_secret" to CLIENT_SECRET,
                    "code" to code,
                    "code_verifier" to verifier,
                    "grant_type" to "authorization_code",
                    "redirect_uri" to CloudOAuthHelper.REDIRECT_URI_GDRIVE
                )
            )
            CloudOAuthHelper.clearCodeVerifier(CloudProvider.GOOGLE_DRIVE.scheme)

            val token = tokenResult.getOrElse { return@withContext Result.failure(it) }

            // Save with pending key, fetch user info, then save with final key
            val pendingKey = "${CloudProvider.GOOGLE_DRIVE.scheme}:_pending"
            tokenStore.saveByKey(
                pendingKey,
                CloudTokenStore.CloudTokens(
                    accessToken = token.accessToken,
                    refreshToken = token.refreshToken
                )
            )

            // Fetch user info via REST API
            var email = ""
            var displayName = ""
            try {
                val aboutJson = apiGet("$API_BASE/about?fields=user", token.accessToken)
                val userObj = JSONObject(aboutJson).optJSONObject("user")
                email = userObj?.optString("emailAddress", "") ?: ""
                displayName = userObj?.optString("displayName", "") ?: ""
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "GDrive: failed to fetch user info: ${e.message}")
            }

            // Remove pending, save with final key
            tokenStore.removeByKey(pendingKey)
            val finalKey = "${CloudProvider.GOOGLE_DRIVE.scheme}:${email.ifEmpty { "account" }}"
            tokenStore.saveByKey(
                finalKey,
                CloudTokenStore.CloudTokens(
                    accessToken = token.accessToken,
                    refreshToken = token.refreshToken,
                    email = email,
                    displayName = displayName
                )
            )

            EcosystemLogger.d(HaronConstants.TAG, "GDrive auth success: accountId=$finalKey, email=$email")
            Result.success(finalKey)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "GDrive auth error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        if (tokenKey.isNotEmpty()) {
            tokenStore.removeByKey(tokenKey)
        }
    }

    override suspend fun listFiles(path: String): Result<List<CloudFileEntry>> = withContext(Dispatchers.IO) {
        try {
            val parentId = if (path.isEmpty() || path == "/") "root" else path
            EcosystemLogger.d(HaronConstants.TAG, "GDrive listFiles: path='$path', parentId='$parentId'")

            data class DriveFile(
                val id: String, val name: String, val mimeType: String?,
                val size: Long, val modifiedTime: Long, val parents: List<String>,
                val thumbnailLink: String?
            )

            val allFiles = mutableListOf<DriveFile>()
            var pageToken: String? = null
            do {
                val q = URLEncoder.encode("'$parentId' in parents and trashed=false", "UTF-8")
                val fields = URLEncoder.encode("nextPageToken,incompleteSearch,files(id,name,mimeType,size,modifiedTime,parents,thumbnailLink)", "UTF-8")
                var url = "$API_BASE/files?q=$q&fields=$fields&pageSize=500&orderBy=folder%2Cname"
                if (pageToken != null) url += "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}"

                val responseBody = withRefresh { token -> apiGet(url, token) }
                val json = JSONObject(responseBody)

                EcosystemLogger.d(HaronConstants.TAG, "GDrive listFiles page: ${json.optJSONArray("files")?.length() ?: 0} items, nextPage=${json.has("nextPageToken")}, incomplete=${json.optBoolean("incompleteSearch", false)}")

                val filesArr = json.optJSONArray("files")
                if (filesArr != null) {
                    for (i in 0 until filesArr.length()) {
                        val f = filesArr.getJSONObject(i)
                        val parentsList = mutableListOf<String>()
                        f.optJSONArray("parents")?.let { pa ->
                            for (j in 0 until pa.length()) parentsList.add(pa.getString(j))
                        }
                        allFiles.add(DriveFile(
                            id = f.getString("id"),
                            name = f.getString("name"),
                            mimeType = f.optString("mimeType", null),
                            size = f.optLong("size", 0L),
                            modifiedTime = parseGoogleTime(f.optString("modifiedTime", "")),
                            parents = parentsList,
                            thumbnailLink = f.optString("thumbnailLink", null).takeIf { it?.isNotEmpty() == true }
                        ))
                    }
                }
                pageToken = json.optString("nextPageToken", null).takeIf { it?.isNotEmpty() == true }
            } while (pageToken != null)

            val currentToken = tokenStore.loadByKey(tokenKey)?.accessToken
            val entries = allFiles.map { file ->
                val isDir = file.mimeType == "application/vnd.google-apps.folder"
                val rawThumbUrl = file.thumbnailLink?.replace(Regex("=s\\d+"), "=s800")
                    ?: if (!isDir && (file.mimeType?.startsWith("image/") == true || file.mimeType?.startsWith("video/") == true)) {
                        "$API_BASE/files/${file.id}?alt=media"
                    } else null
                // Append access_token for authenticated thumbnail download in grid
                val authThumbUrl = if (rawThumbUrl != null && currentToken != null) {
                    val sep = if ('?' in rawThumbUrl) "&" else "?"
                    "$rawThumbUrl${sep}access_token=$currentToken"
                } else rawThumbUrl
                CloudFileEntry(
                    id = file.id,
                    name = file.name,
                    path = file.id,
                    isDirectory = isDir,
                    size = file.size,
                    lastModified = file.modifiedTime,
                    mimeType = file.mimeType,
                    provider = CloudProvider.GOOGLE_DRIVE,
                    thumbnailUrl = authThumbUrl
                )
            }

            // Batch-count children for all folders in a single API query
            val folderIds = entries.filter { it.isDirectory }.map { it.id }
            val childCounts = if (folderIds.isNotEmpty()) {
                countChildrenBatch(folderIds)
            } else emptyMap()

            val enriched = if (childCounts.isNotEmpty()) {
                entries.map { e ->
                    if (e.isDirectory) e.copy(childCount = childCounts[e.id] ?: 0)
                    else e
                }
            } else entries

            EcosystemLogger.d(HaronConstants.TAG, "GDrive listFiles: found ${enriched.size} items (dirs=${enriched.count { it.isDirectory }}, files=${enriched.count { !it.isDirectory }})")
            enriched.forEach { e ->
                EcosystemLogger.d(HaronConstants.TAG, "  GDrive entry: name='${e.name}', id='${e.id}', isDir=${e.isDirectory}, size=${e.size}, children=${e.childCount}, thumb=${if (e.thumbnailUrl != null) (if (e.thumbnailUrl!!.contains("googleapis.com")) "auth-fallback" else "api") else "null"}")
            }

            Result.success(enriched)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "GDrive listFiles error: ${e.message}")
            Result.failure(e)
        }
    }

    override fun downloadFile(cloudFileId: String, localPath: String): Flow<CloudTransferProgress> = flow {
        try {
            // Get file metadata
            val fields = URLEncoder.encode("id,name,size,mimeType", "UTF-8")
            val metaUrl = "$API_BASE/files/$cloudFileId?fields=$fields"
            val metaBody = withRefresh { token -> apiGet(metaUrl, token) }
            val metaJson = JSONObject(metaBody)
            val totalSize = metaJson.optLong("size", 0L)
            val fileName = metaJson.getString("name")

            emit(CloudTransferProgress(fileName, 0, totalSize))

            // Stream download
            val token = getAccessToken() ?: throw Exception("Not authenticated")
            val downloadUrl = "$API_BASE/files/$cloudFileId?alt=media"
            val startTime = System.currentTimeMillis()
            val outputFile = File(localPath)

            val conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 30_000
                readTimeout = 60_000
            }

            try {
                if (conn.responseCode !in 200..299) {
                    throw java.io.IOException("Download failed: HTTP ${conn.responseCode}")
                }
                conn.inputStream.use { inputStream ->
                    BufferedOutputStream(FileOutputStream(outputFile), 262144).use { fos ->
                        val buffer = ByteArray(262144)
                        var totalRead = 0L
                        var lastEmitPercent = -1
                        while (true) {
                            val read = inputStream.read(buffer)
                            if (read == -1) break
                            fos.write(buffer, 0, read)
                            totalRead += read
                            val percent = if (totalSize > 0) ((totalRead * 100) / totalSize).toInt() else 0
                            if (percent != lastEmitPercent) {
                                val elapsed = System.currentTimeMillis() - startTime
                                val speed = if (elapsed > 500) totalRead * 1000 / elapsed else 0L
                                emit(CloudTransferProgress(fileName, totalRead, totalSize, speedBytesPerSec = speed))
                                lastEmitPercent = percent
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }

            emit(CloudTransferProgress(fileName, totalSize, totalSize, isComplete = true))
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "GDrive download error: ${e.message}")
            emit(CloudTransferProgress("", 0, 0, isComplete = true, error = e.message))
        }
    }.flowOn(Dispatchers.IO)

    override fun uploadFile(localPath: String, cloudDirPath: String, fileName: String): Flow<CloudTransferProgress> = flow {
        var lastError: Exception? = null
        val maxRetries = 3

        for (attempt in 1..maxRetries) {
            try {
                // Force-refresh token before upload to avoid Connection reset
                val refreshed = refreshToken()
                val token = getAccessToken() ?: throw Exception("Not authenticated")
                val file = File(localPath)
                val totalSize = file.length()

                EcosystemLogger.d(HaronConstants.TAG, "GDrive upload: attempt=$attempt/$maxRetries, tokenRefreshed=$refreshed, file=$fileName, size=${totalSize / 1024}KB")
                emit(CloudTransferProgress(fileName, 0, totalSize))

                val uploadStartTime = System.currentTimeMillis()
                val parentId = if (cloudDirPath.isEmpty() || cloudDirPath == "/") "root" else cloudDirPath

                // Init resumable upload
                val metadata = JSONObject().apply {
                    put("name", fileName)
                    put("parents", org.json.JSONArray().apply { put(parentId) })
                }
                val sessionUri = initResumableUpload(token, metadata.toString(), totalSize)

                // Upload in chunks
                FileInputStream(file).use { fis ->
                    var offset = 0L
                    val buffer = ByteArray(CHUNK_SIZE)
                    while (offset < totalSize) {
                        val remaining = (totalSize - offset).toInt().coerceAtMost(CHUNK_SIZE)
                        var bytesRead = 0
                        while (bytesRead < remaining) {
                            val r = fis.read(buffer, bytesRead, remaining - bytesRead)
                            if (r == -1) break
                            bytesRead += r
                        }
                        val chunk = if (bytesRead == buffer.size) buffer else buffer.copyOf(bytesRead)
                        uploadChunk(sessionUri, chunk, offset, totalSize, token)
                        offset += bytesRead

                        val percent = ((offset * 100) / totalSize).toInt()
                        if (percent % 10 == 0) {
                            EcosystemLogger.d(HaronConstants.TAG, "GDrive upload progress: $fileName $percent%")
                        }
                        val elapsed = System.currentTimeMillis() - uploadStartTime
                        val speed = if (elapsed > 500) offset * 1000 / elapsed else 0L
                        emit(CloudTransferProgress(fileName, offset, totalSize, speedBytesPerSec = speed))
                    }
                }

                emit(CloudTransferProgress(fileName, totalSize, totalSize, isComplete = true))
                EcosystemLogger.d(HaronConstants.TAG, "GDrive upload: SUCCESS $fileName (attempt $attempt)")
                lastError = null
                break // success
            } catch (e: Exception) {
                lastError = e
                EcosystemLogger.e(HaronConstants.TAG, "GDrive upload error (attempt $attempt/$maxRetries): ${e.javaClass.simpleName}: ${e.message}")
                if (attempt < maxRetries && e is java.io.IOException) {
                    val backoffMs = attempt * 2000L
                    EcosystemLogger.d(HaronConstants.TAG, "GDrive upload: retrying in ${backoffMs}ms...")
                    kotlinx.coroutines.delay(backoffMs)
                    continue
                }
                break
            }
        }

        if (lastError != null) {
            emit(CloudTransferProgress(fileName, 0, 0, isComplete = true, error = lastError!!.message))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun delete(cloudFileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            withRefresh { token -> apiDelete("$API_BASE/files/$cloudFileId", token) }
            Result.success(Unit)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "GDrive delete error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun rename(cloudFileId: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply { put("name", newName) }.toString()
            withRefresh { token -> apiPatch("$API_BASE/files/$cloudFileId", token, body) }
            EcosystemLogger.d(HaronConstants.TAG, "GDrive rename: $cloudFileId -> $newName")
            Result.success(Unit)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "GDrive rename error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun moveFile(
        fileId: String,
        currentParentId: String,
        newParentId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/files/$fileId?addParents=${URLEncoder.encode(newParentId, "UTF-8")}&removeParents=${URLEncoder.encode(currentParentId, "UTF-8")}&fields=id,parents"
            withRefresh { token -> apiPatch(url, token, null) }
            EcosystemLogger.d(HaronConstants.TAG, "GDrive moveFile: $fileId from $currentParentId to $newParentId")
            Result.success(Unit)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "GDrive moveFile error: ${e.message}")
            Result.failure(e)
        }
    }

    override fun getAccessToken(): String? {
        return tokenStore.loadByKey(tokenKey)?.accessToken
    }

    override suspend fun getFreshAccessToken(): String? = withContext(Dispatchers.IO) {
        try {
            refreshToken()
            EcosystemLogger.d(HaronConstants.TAG, "GDrive getFreshAccessToken: token force-refreshed")
            tokenStore.loadByKey(tokenKey)?.accessToken
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "GDrive getFreshAccessToken failed: ${e::class.simpleName}: ${e.message}")
            tokenStore.loadByKey(tokenKey)?.accessToken // return stored even if stale
        }
    }

    override fun updateFileContent(cloudFileId: String, localPath: String): Flow<CloudTransferProgress> = flow {
        try {
            val token = getAccessToken() ?: throw Exception("Not authenticated")
            val file = File(localPath)
            val totalSize = file.length()
            val fileName = file.name

            emit(CloudTransferProgress(fileName, 0, totalSize))

            val url = "$UPLOAD_BASE/files/$cloudFileId?uploadType=media"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "PATCH"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("Content-Length", totalSize.toString())
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 120_000
            }

            try {
                conn.outputStream.use { os ->
                    file.inputStream().use { it.copyTo(os, 262144) }
                }
                if (conn.responseCode !in 200..299) {
                    val errBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                    throw java.io.IOException("Update content failed: HTTP ${conn.responseCode} $errBody")
                }
            } finally {
                conn.disconnect()
            }

            emit(CloudTransferProgress(fileName, totalSize, totalSize, isComplete = true))
            EcosystemLogger.d(HaronConstants.TAG, "GDrive updateFileContent: $cloudFileId updated ($totalSize bytes)")
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "GDrive updateFileContent error: ${e.message}")
            emit(CloudTransferProgress("", 0, 0, isComplete = true, error = e.message))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun createFolder(parentPath: String, name: String): Result<CloudFileEntry> = withContext(Dispatchers.IO) {
        try {
            val parentId = if (parentPath.isEmpty() || parentPath == "/") "root" else parentPath
            val body = JSONObject().apply {
                put("name", name)
                put("mimeType", "application/vnd.google-apps.folder")
                put("parents", org.json.JSONArray().apply { put(parentId) })
            }.toString()

            val responseBody = withRefresh { token ->
                apiPost("$API_BASE/files?fields=${URLEncoder.encode("id,name,mimeType", "UTF-8")}", token, body)
            }
            val json = JSONObject(responseBody)

            Result.success(
                CloudFileEntry(
                    id = json.getString("id"),
                    name = json.getString("name"),
                    path = json.getString("id"),
                    isDirectory = true,
                    provider = CloudProvider.GOOGLE_DRIVE
                )
            )
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "GDrive createFolder error: ${e.message}")
            Result.failure(e)
        }
    }

    // ========== Batch child count ==========

    /**
     * Count children for multiple folders in batched API calls.
     * Groups folder IDs into chunks of 20 to keep query length manageable,
     * then uses a single files.list per chunk with OR-combined parent queries.
     */
    private suspend fun countChildrenBatch(folderIds: List<String>): Map<String, Int> {
        val countMap = mutableMapOf<String, Int>()
        val folderIdSet = folderIds.toSet()

        folderIds.chunked(20).forEach { chunk ->
            try {
                val parentQuery = chunk.joinToString(" or ") { "'$it' in parents" }
                val query = "($parentQuery) and trashed=false"

                var pageToken: String? = null
                do {
                    val q = URLEncoder.encode(query, "UTF-8")
                    val fields = URLEncoder.encode("nextPageToken,files(parents)", "UTF-8")
                    var url = "$API_BASE/files?q=$q&fields=$fields&pageSize=1000"
                    if (pageToken != null) url += "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}"

                    val responseBody = withRefresh { token -> apiGet(url, token) }
                    val json = JSONObject(responseBody)

                    val filesArr = json.optJSONArray("files")
                    if (filesArr != null) {
                        for (i in 0 until filesArr.length()) {
                            val f = filesArr.getJSONObject(i)
                            val parents = f.optJSONArray("parents")
                            if (parents != null) {
                                for (j in 0 until parents.length()) {
                                    val pid = parents.getString(j)
                                    if (pid in folderIdSet) {
                                        countMap[pid] = (countMap[pid] ?: 0) + 1
                                    }
                                }
                            }
                        }
                    }
                    pageToken = json.optString("nextPageToken", null).takeIf { it?.isNotEmpty() == true }
                } while (pageToken != null)
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "GDrive countChildren batch error: ${e.message}")
            }
        }

        return countMap
    }

    // ========== HTTP helpers ==========

    private fun apiGet(url: String, accessToken: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 30_000
            readTimeout = 30_000
        }
        return handleResponse(conn)
    }

    private fun apiPost(url: String, accessToken: String, jsonBody: String?): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $accessToken")
            if (jsonBody != null) {
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                doOutput = true
            }
            connectTimeout = 30_000
            readTimeout = 30_000
        }
        if (jsonBody != null) {
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
        }
        return handleResponse(conn)
    }

    private fun apiPatch(url: String, accessToken: String, jsonBody: String?): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            setRequestProperty("Authorization", "Bearer $accessToken")
            if (jsonBody != null) {
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                doOutput = true
            }
            connectTimeout = 30_000
            readTimeout = 30_000
        }
        if (jsonBody != null) {
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
        }
        return handleResponse(conn)
    }

    private fun apiDelete(url: String, accessToken: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 30_000
            readTimeout = 30_000
        }
        return handleResponse(conn)
    }

    private fun handleResponse(conn: HttpURLConnection): String {
        try {
            val code = conn.responseCode
            if (code == 401) {
                val errBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                throw HttpException(401, errBody)
            }
            if (code == 204) return "" // No Content (e.g. delete)
            if (code !in 200..299) {
                val errBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                throw java.io.IOException("HTTP $code: $errBody")
            }
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    // ========== Resumable upload helpers ==========

    private fun initResumableUpload(accessToken: String, metadataJson: String, contentLength: Long): String {
        val url = "$UPLOAD_BASE/files?uploadType=resumable&fields=${URLEncoder.encode("id,name,size", "UTF-8")}"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("X-Upload-Content-Type", "application/octet-stream")
            setRequestProperty("X-Upload-Content-Length", contentLength.toString())
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 30_000
            instanceFollowRedirects = false
        }
        conn.outputStream.use { it.write(metadataJson.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        if (code != 200) {
            val errBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
            conn.disconnect()
            throw java.io.IOException("Resumable upload init failed: HTTP $code $errBody")
        }
        val sessionUri = conn.getHeaderField("Location")
            ?: throw java.io.IOException("No Location header in resumable upload init response")
        conn.disconnect()
        return sessionUri
    }

    private fun uploadChunk(sessionUri: String, chunk: ByteArray, offset: Long, totalSize: Long, accessToken: String) {
        val endByte = offset + chunk.size - 1
        val conn = (URL(sessionUri).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/octet-stream")
            setRequestProperty("Content-Range", "bytes $offset-$endByte/$totalSize")
            setRequestProperty("Content-Length", chunk.size.toString())
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 120_000
        }
        conn.outputStream.use { it.write(chunk) }

        val code = conn.responseCode
        // 308 = Resume Incomplete (more chunks needed), 200/201 = complete
        if (code != 308 && code !in 200..201) {
            val errBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
            conn.disconnect()
            throw java.io.IOException("Chunk upload failed: HTTP $code $errBody")
        }
        conn.disconnect()
    }

    // ========== Token management ==========

    /** Refresh access token using refresh token */
    private suspend fun refreshToken(): Boolean = withContext(Dispatchers.IO) {
        val tokens = tokenStore.loadByKey(tokenKey) ?: return@withContext false
        val refreshToken = tokens.refreshToken ?: return@withContext false
        EcosystemLogger.d(HaronConstants.TAG, "GDrive: refreshing access token...")

        try {
            val result = CloudOAuthHelper.exchangeCodeForToken(
                TOKEN_URL,
                mapOf(
                    "client_id" to CLIENT_ID,
                    "client_secret" to CLIENT_SECRET,
                    "refresh_token" to refreshToken,
                    "grant_type" to "refresh_token"
                )
            )
            result.onSuccess { newToken ->
                tokenStore.saveByKey(
                    tokenKey,
                    CloudTokenStore.CloudTokens(
                        accessToken = newToken.accessToken,
                        refreshToken = newToken.refreshToken ?: refreshToken,
                        email = tokens.email,
                        displayName = tokens.displayName
                    )
                )
                EcosystemLogger.d(HaronConstants.TAG, "GDrive: token refreshed successfully")
            }
            result.isSuccess
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "GDrive: token refresh failed: ${e.message}")
            false
        }
    }

    /** Execute API call with automatic token refresh on 401 */
    private suspend fun <T> withRefresh(block: (String) -> T): T {
        val token = getAccessToken() ?: throw Exception("Not authenticated")
        return try {
            block(token)
        } catch (e: HttpException) {
            if (e.code == 401 && refreshToken()) {
                val newToken = getAccessToken() ?: throw Exception("Not authenticated after refresh")
                block(newToken)
            } else throw e
        }
    }

    /** Custom exception for HTTP error codes to enable 401 detection */
    private class HttpException(val code: Int, message: String) : java.io.IOException("HTTP $code: $message")

    // ========== Utilities ==========

    /** Parse Google's RFC 3339 date string to epoch millis */
    private fun parseGoogleTime(dateStr: String): Long {
        if (dateStr.isEmpty()) return 0L
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            format.timeZone = java.util.TimeZone.getTimeZone("UTC")
            format.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            try {
                val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                format.parse(dateStr)?.time ?: 0L
            } catch (_: Exception) { 0L }
        }
    }
}
