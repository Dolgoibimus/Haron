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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class OneDriveProvider(
    private val context: Context,
    private val tokenStore: CloudTokenStore
) : CloudProviderInterface {

    companion object {
        // TODO: Replace with actual Azure AD app client ID from Azure Portal
        const val CLIENT_ID = "YOUR_ONEDRIVE_CLIENT_ID"
        const val REDIRECT_URI = CloudOAuthHelper.REDIRECT_URI_ONEDRIVE
        const val SCOPES = "Files.ReadWrite.All User.Read offline_access"
        const val TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
        private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0"
    }

    override fun isAuthenticated(): Boolean {
        return tokenStore.load(CloudProvider.ONEDRIVE) != null
    }

    override fun getAuthUrl(): String? {
        val verifier = CloudOAuthHelper.generateCodeVerifier(CloudProvider.ONEDRIVE.scheme)
        val challenge = CloudOAuthHelper.generateCodeChallenge(verifier)
        return "https://login.microsoftonline.com/common/oauth2/v2.0/authorize" +
            "?client_id=$CLIENT_ID" +
            "&response_type=code" +
            "&redirect_uri=$REDIRECT_URI" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256" +
            "&scope=${java.net.URLEncoder.encode(SCOPES, "UTF-8")}" +
            "&response_mode=query"
    }

    override suspend fun handleAuthCode(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val verifier = CloudOAuthHelper.getCodeVerifier(CloudProvider.ONEDRIVE.scheme)
                ?: return@withContext Result.failure(Exception("No PKCE code verifier"))

            val tokenResult = CloudOAuthHelper.exchangeCodeForToken(
                TOKEN_URL,
                mapOf(
                    "client_id" to CLIENT_ID,
                    "code" to code,
                    "code_verifier" to verifier,
                    "grant_type" to "authorization_code",
                    "redirect_uri" to REDIRECT_URI
                )
            )
            CloudOAuthHelper.clearCodeVerifier(CloudProvider.ONEDRIVE.scheme)

            tokenResult.onSuccess { token ->
                tokenStore.save(
                    CloudProvider.ONEDRIVE,
                    CloudTokenStore.CloudTokens(
                        accessToken = token.accessToken,
                        refreshToken = token.refreshToken
                    )
                )
                // Fetch user info
                try {
                    val userJson = graphGet("/me")
                    if (userJson != null) {
                        val email = userJson.optString("mail", "")
                            .ifEmpty { userJson.optString("userPrincipalName", "") }
                        val displayName = userJson.optString("displayName", "")
                        tokenStore.save(
                            CloudProvider.ONEDRIVE,
                            CloudTokenStore.CloudTokens(
                                accessToken = token.accessToken,
                                refreshToken = token.refreshToken,
                                email = email,
                                displayName = displayName
                            )
                        )
                    }
                } catch (_: Exception) { }
            }
            tokenResult.map { }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "OneDrive auth error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        tokenStore.remove(CloudProvider.ONEDRIVE)
    }

    override suspend fun listFiles(path: String): Result<List<CloudFileEntry>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = if (path.isEmpty() || path == "/" || path == "root") {
                "/me/drive/root/children"
            } else {
                "/me/drive/items/$path/children"
            }

            val json = graphGet(endpoint)
                ?: return@withContext Result.failure(Exception("Failed to list files"))

            val items = json.optJSONArray("value") ?: return@withContext Result.success(emptyList())
            val entries = (0 until items.length()).map { i ->
                val item = items.getJSONObject(i)
                val isDir = item.has("folder")
                val folderChildCount = if (isDir) {
                    item.optJSONObject("folder")?.optInt("childCount", -1) ?: -1
                } else -1
                CloudFileEntry(
                    id = item.optString("id", ""),
                    name = item.optString("name", ""),
                    path = item.optString("id", ""),
                    isDirectory = isDir,
                    size = item.optLong("size", 0L),
                    lastModified = parseIso8601(item.optString("lastModifiedDateTime", "")),
                    mimeType = item.optJSONObject("file")?.optString("mimeType"),
                    provider = CloudProvider.ONEDRIVE,
                    childCount = folderChildCount
                )
            }

            Result.success(entries)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "OneDrive listFiles error: ${e.message}")
            Result.failure(e)
        }
    }

    override fun downloadFile(cloudFileId: String, localPath: String): Flow<CloudTransferProgress> = flow {
        try {
            // Get file metadata for name/size
            val meta = graphGet("/me/drive/items/$cloudFileId")
            val fileName = meta?.optString("name", "file") ?: "file"
            val totalSize = meta?.optLong("size", 0L) ?: 0L

            emit(CloudTransferProgress(fileName, 0, totalSize))

            // Download content (Graph returns 302 redirect)
            val token = tokenStore.load(CloudProvider.ONEDRIVE)?.accessToken
                ?: throw Exception("Not authenticated")
            val url = URL("$GRAPH_BASE/me/drive/items/$cloudFileId/content")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                instanceFollowRedirects = true
            }

            try {
                BufferedInputStream(conn.inputStream, 262144).use { input ->
                    BufferedOutputStream(FileOutputStream(File(localPath)), 262144).use { fos ->
                        val buffer = ByteArray(262144)
                        var bytesRead: Int
                        var totalRead = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalSize > 0) {
                                emit(CloudTransferProgress(fileName, totalRead, totalSize))
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }

            emit(CloudTransferProgress(fileName, totalSize, totalSize, isComplete = true))
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "OneDrive download error: ${e.message}")
            emit(CloudTransferProgress("", 0, 0, isComplete = true, error = e.message))
        }
    }.flowOn(Dispatchers.IO)

    override fun uploadFile(localPath: String, cloudDirPath: String, fileName: String): Flow<CloudTransferProgress> = flow {
        try {
            val token = tokenStore.load(CloudProvider.ONEDRIVE)?.accessToken
                ?: throw Exception("Not authenticated")
            val file = File(localPath)
            val totalSize = file.length()

            emit(CloudTransferProgress(fileName, 0, totalSize))

            val parentId = if (cloudDirPath.isEmpty() || cloudDirPath == "/") "root" else cloudDirPath
            val encodedName = java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
            val url = URL("$GRAPH_BASE/me/drive/items/$parentId:/$encodedName:/content")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("Content-Length", totalSize.toString())
                doOutput = true
            }

            try {
                BufferedInputStream(FileInputStream(file), 262144).use { fis ->
                    BufferedOutputStream(conn.outputStream, 262144).use { out ->
                        val buffer = ByteArray(262144)
                        var bytesRead: Int
                        var totalWritten = 0L
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                            totalWritten += bytesRead
                            emit(CloudTransferProgress(fileName, totalWritten, totalSize))
                        }
                    }
                }
                if (conn.responseCode !in 200..299) {
                    throw Exception("Upload failed: HTTP ${conn.responseCode}")
                }
            } finally {
                conn.disconnect()
            }

            emit(CloudTransferProgress(fileName, totalSize, totalSize, isComplete = true))
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "OneDrive upload error: ${e.message}")
            emit(CloudTransferProgress(fileName, 0, 0, isComplete = true, error = e.message))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun delete(cloudFileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = tokenStore.load(CloudProvider.ONEDRIVE)?.accessToken
                ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = URL("$GRAPH_BASE/me/drive/items/$cloudFileId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                setRequestProperty("Authorization", "Bearer $token")
            }
            try {
                if (conn.responseCode !in 200..299) {
                    return@withContext Result.failure(Exception("Delete failed: HTTP ${conn.responseCode}"))
                }
            } finally {
                conn.disconnect()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "OneDrive delete error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun createFolder(parentPath: String, name: String): Result<CloudFileEntry> = withContext(Dispatchers.IO) {
        try {
            val token = tokenStore.load(CloudProvider.ONEDRIVE)?.accessToken
                ?: return@withContext Result.failure(Exception("Not authenticated"))
            val parentId = if (parentPath.isEmpty() || parentPath == "/") "root" else parentPath
            val url = URL("$GRAPH_BASE/me/drive/items/$parentId/children")
            val body = JSONObject().apply {
                put("name", name)
                put("folder", JSONObject())
                put("@microsoft.graph.conflictBehavior", "rename")
            }
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                if (conn.responseCode !in 200..299) {
                    return@withContext Result.failure(Exception("Create folder failed: HTTP ${conn.responseCode}"))
                }
                val respJson = JSONObject(conn.inputStream.bufferedReader().readText())
                Result.success(
                    CloudFileEntry(
                        id = respJson.optString("id", ""),
                        name = respJson.optString("name", name),
                        path = respJson.optString("id", ""),
                        isDirectory = true,
                        provider = CloudProvider.ONEDRIVE
                    )
                )
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "OneDrive createFolder error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun rename(cloudFileId: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = tokenStore.load(CloudProvider.ONEDRIVE)?.accessToken
                ?: return@withContext Result.failure(Exception("Not authenticated"))
            val url = URL("$GRAPH_BASE/me/drive/items/$cloudFileId")
            val body = JSONObject().apply { put("name", newName) }
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PATCH"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                if (conn.responseCode !in 200..299) {
                    return@withContext Result.failure(Exception("Rename failed: HTTP ${conn.responseCode}"))
                }
            } finally {
                conn.disconnect()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "OneDrive rename error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun moveFile(
        fileId: String,
        currentParentId: String,
        newParentId: String
    ): Result<Unit> = Result.failure(Exception("Not implemented for OneDrive"))

    override fun getAccessToken(): String? {
        return tokenStore.load(CloudProvider.ONEDRIVE)?.accessToken
    }

    override fun updateFileContent(cloudFileId: String, localPath: String): Flow<CloudTransferProgress> = flow {
        try {
            val token = tokenStore.load(CloudProvider.ONEDRIVE)?.accessToken
                ?: throw Exception("Not authenticated")
            val file = File(localPath)
            val totalSize = file.length()
            val fileName = file.name

            emit(CloudTransferProgress(fileName, 0, totalSize))

            val url = URL("$GRAPH_BASE/me/drive/items/$cloudFileId/content")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("Content-Length", totalSize.toString())
                doOutput = true
            }
            try {
                BufferedInputStream(FileInputStream(file), 262144).use { fis ->
                    BufferedOutputStream(conn.outputStream, 262144).use { out ->
                        val buffer = ByteArray(262144)
                        var bytesRead: Int
                        var totalWritten = 0L
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                            totalWritten += bytesRead
                            emit(CloudTransferProgress(fileName, totalWritten, totalSize))
                        }
                    }
                }
                if (conn.responseCode !in 200..299) {
                    throw Exception("Update failed: HTTP ${conn.responseCode}")
                }
            } finally {
                conn.disconnect()
            }
            emit(CloudTransferProgress(fileName, totalSize, totalSize, isComplete = true))
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "OneDrive updateFileContent error: ${e.message}")
            emit(CloudTransferProgress("", 0, 0, isComplete = true, error = e.message))
        }
    }.flowOn(Dispatchers.IO)

    /** Execute GET request to Microsoft Graph API */
    private fun graphGet(endpoint: String): JSONObject? {
        val token = tokenStore.load(CloudProvider.ONEDRIVE)?.accessToken ?: return null
        val url = URL("$GRAPH_BASE$endpoint")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
        }
        return try {
            if (conn.responseCode == 200) {
                JSONObject(conn.inputStream.bufferedReader().readText())
            } else {
                EcosystemLogger.e(HaronConstants.TAG, "OneDrive API error: ${conn.responseCode} for $endpoint")
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseIso8601(dateStr: String): Long {
        return try {
            java.time.OffsetDateTime.parse(dateStr).toInstant().toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }
}
