package com.vamp.haron.data.cloud.provider

import android.content.Context
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.UserCredentials
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.cloud.CloudOAuthHelper
import com.vamp.haron.data.cloud.CloudTokenStore
import com.vamp.haron.domain.model.CloudFileEntry
import com.vamp.haron.domain.model.CloudProvider
import com.vamp.haron.domain.model.CloudTransferProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Date

class GoogleDriveProvider(
    private val context: Context,
    private val tokenStore: CloudTokenStore,
    private val tokenKey: String
) : CloudProviderInterface {

    companion object {
        val CLIENT_ID: String get() = com.vamp.haron.BuildConfig.GOOGLE_CLIENT_ID
        val CLIENT_SECRET: String get() = com.vamp.haron.BuildConfig.GOOGLE_CLIENT_SECRET
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    }

    private var driveService: Drive? = null

    override fun isAuthenticated(): Boolean {
        return tokenKey.isNotEmpty() && tokenStore.loadByKey(tokenKey) != null
    }

    override fun getAuthUrl(): String? {
        val verifier = CloudOAuthHelper.generateCodeVerifier(CloudProvider.GOOGLE_DRIVE.scheme)
        val challenge = CloudOAuthHelper.generateCodeChallenge(verifier)
        return "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=$CLIENT_ID" +
            "&redirect_uri=${java.net.URLEncoder.encode(CloudOAuthHelper.REDIRECT_URI_GDRIVE, "UTF-8")}" +
            "&response_type=code" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256" +
            "&scope=${java.net.URLEncoder.encode("https://www.googleapis.com/auth/drive", "UTF-8")}" +
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

            // Create temporary Drive service to fetch user info
            var email = ""
            var displayName = ""
            try {
                val tempService = buildDriveService(token.accessToken, token.refreshToken)
                val about = tempService.about().get().setFields("user").execute()
                val user = about?.user
                email = user?.emailAddress ?: ""
                displayName = user?.displayName ?: ""
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
        driveService = null
    }

    override suspend fun listFiles(path: String): Result<List<CloudFileEntry>> = withContext(Dispatchers.IO) {
        try {
            val parentId = if (path.isEmpty() || path == "/") "root" else path
            EcosystemLogger.d(HaronConstants.TAG, "GDrive listFiles: path='$path', parentId='$parentId'")

            val allFiles = mutableListOf<com.google.api.services.drive.model.File>()
            var pageToken: String? = null
            do {
                val result = withRefresh { service ->
                    val req = service.files().list()
                        .setQ("'$parentId' in parents and trashed=false")
                        .setFields("nextPageToken,incompleteSearch,files(id,name,mimeType,size,modifiedTime,parents,thumbnailLink)")
                        .setPageSize(500)
                        .setOrderBy("folder,name")
                    if (pageToken != null) req.setPageToken(pageToken)
                    req.execute()
                }
                EcosystemLogger.d(HaronConstants.TAG, "GDrive listFiles page: ${result.files?.size ?: 0} items, nextPage=${result.nextPageToken != null}, incomplete=${result.incompleteSearch}")
                result.files?.let { allFiles.addAll(it) }
                pageToken = result.nextPageToken
            } while (pageToken != null)

            val currentToken = tokenStore.loadByKey(tokenKey)?.accessToken
            val entries = allFiles.map { file ->
                val isDir = file.mimeType == "application/vnd.google-apps.folder"
                val rawThumbUrl = file.thumbnailLink?.replace(Regex("=s\\d+"), "=s800")
                    ?: if (!isDir && (file.mimeType?.startsWith("image/") == true || file.mimeType?.startsWith("video/") == true)) {
                        "https://www.googleapis.com/drive/v3/files/${file.id}?alt=media"
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
                    size = file.getSize()?.toLong() ?: 0L,
                    lastModified = file.modifiedTime?.value ?: 0L,
                    mimeType = file.mimeType,
                    provider = CloudProvider.GOOGLE_DRIVE,
                    thumbnailUrl = authThumbUrl
                )
            } ?: emptyList()

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
            val fileMeta = withRefresh { service ->
                service.files().get(cloudFileId)
                    .setFields("id,name,size,mimeType")
                    .execute()
            }
            val totalSize = fileMeta.getSize()?.toLong() ?: 0L
            val fileName = fileMeta.name

            emit(CloudTransferProgress(fileName, 0, totalSize))

            val service = getService() ?: throw Exception("Not authenticated")
            val outputFile = File(localPath)

            // Stream download with manual progress tracking
            val startTime = System.currentTimeMillis()
            val inputStream = service.files().get(cloudFileId).executeMediaAsInputStream()
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
                inputStream.close()
            }

            emit(CloudTransferProgress(fileName, totalSize, totalSize, isComplete = true))
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "GDrive download error: ${e.message}")
            emit(CloudTransferProgress("", 0, 0, isComplete = true, error = e.message))
        }
    }.flowOn(Dispatchers.IO)

    override fun uploadFile(localPath: String, cloudDirPath: String, fileName: String): Flow<CloudTransferProgress> = callbackFlow {
        var lastError: Exception? = null
        val maxRetries = 3

        for (attempt in 1..maxRetries) {
            try {
                // Force-refresh token before upload to avoid Connection reset
                val refreshed = refreshToken()
                EcosystemLogger.d(HaronConstants.TAG, "GDrive upload: attempt=$attempt/$maxRetries, tokenRefreshed=$refreshed, file=$fileName, size=${File(localPath).length() / 1024}KB")
                val service = getService() ?: throw Exception("Not authenticated")
                val file = File(localPath)
                val totalSize = file.length()

                trySend(CloudTransferProgress(fileName, 0, totalSize))

                val uploadStartTime = System.currentTimeMillis()
                val parentId = if (cloudDirPath.isEmpty() || cloudDirPath == "/") "root" else cloudDirPath
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = fileName
                    parents = listOf(parentId)
                }

                val mediaContent = com.google.api.client.http.FileContent(null, file)
                val createReq = service.files().create(fileMetadata, mediaContent)
                    .setFields("id,name,size")

                // Enable resumable upload with progress — 10MB chunks for better throughput
                createReq.mediaHttpUploader?.apply {
                    isDirectUploadEnabled = false // use resumable
                    chunkSize = 10 * 1024 * 1024 // 10MB chunks (Google minimum recommended)
                }

                createReq.mediaHttpUploader?.setProgressListener { uploader ->
                    val percent = when (uploader.uploadState) {
                        com.google.api.client.googleapis.media.MediaHttpUploader.UploadState.MEDIA_IN_PROGRESS -> {
                            val p = (uploader.progress * 100).toInt()
                            if (p % 10 == 0) {
                                EcosystemLogger.d(HaronConstants.TAG, "GDrive upload progress: $fileName $p%")
                            }
                            p
                        }
                        com.google.api.client.googleapis.media.MediaHttpUploader.UploadState.MEDIA_COMPLETE -> {
                            EcosystemLogger.d(HaronConstants.TAG, "GDrive upload progress: $fileName 100% COMPLETE")
                            100
                        }
                        else -> 0
                    }
                    val transferred = (totalSize * percent / 100)
                    val elapsed = System.currentTimeMillis() - uploadStartTime
                    val speed = if (elapsed > 500) transferred * 1000 / elapsed else 0L
                    trySend(CloudTransferProgress(fileName, transferred, totalSize, speedBytesPerSec = speed))
                }

                withContext(Dispatchers.IO) { createReq.execute() }

                trySend(CloudTransferProgress(fileName, totalSize, totalSize, isComplete = true))
                EcosystemLogger.d(HaronConstants.TAG, "GDrive upload: SUCCESS $fileName (attempt $attempt)")
                lastError = null
                break // success — exit retry loop
            } catch (e: Exception) {
                lastError = e
                EcosystemLogger.e(HaronConstants.TAG, "GDrive upload error (attempt $attempt/$maxRetries): ${e.javaClass.simpleName}: ${e.message}")
                // Retry on ANY IOException (connection reset, end of stream, timeout, etc.)
                if (attempt < maxRetries && e is java.io.IOException) {
                    val backoffMs = attempt * 2000L // 2s, 4s
                    EcosystemLogger.d(HaronConstants.TAG, "GDrive upload: retrying in ${backoffMs}ms...")
                    kotlinx.coroutines.delay(backoffMs)
                    driveService = null // force re-init
                    continue
                }
                break // non-retryable error or max retries exhausted
            }
        }

        if (lastError != null) {
            trySend(CloudTransferProgress(fileName, 0, 0, isComplete = true, error = lastError!!.message))
        }
        channel.close()
        awaitClose { }
    }

    override suspend fun delete(cloudFileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            withRefresh { service -> service.files().delete(cloudFileId).execute() }
            Result.success(Unit)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "GDrive delete error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun rename(cloudFileId: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val fileMeta = com.google.api.services.drive.model.File().apply { this.name = newName }
            withRefresh { service -> service.files().update(cloudFileId, fileMeta).execute() }
            EcosystemLogger.d(HaronConstants.TAG, "GDrive rename: $cloudFileId → $newName")
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
            withRefresh { service ->
                service.files().update(fileId, null)
                    .setAddParents(newParentId)
                    .setRemoveParents(currentParentId)
                    .setFields("id,parents")
                    .execute()
            }
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
            // Always force-refresh the token — Google SDK caches old token with Date(Long.MAX_VALUE)
            // so withRefresh won't trigger 401-based refresh
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
            val service = getService() ?: throw Exception("Not authenticated")
            val file = File(localPath)
            val totalSize = file.length()
            val fileName = file.name

            emit(CloudTransferProgress(fileName, 0, totalSize))

            val mediaContent = com.google.api.client.http.FileContent(null, file)
            service.files().update(cloudFileId, null, mediaContent).execute()

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
            val folderMetadata = com.google.api.services.drive.model.File().apply {
                this.name = name
                this.mimeType = "application/vnd.google-apps.folder"
                this.parents = listOf(parentId)
            }
            val created = withRefresh { service ->
                service.files().create(folderMetadata)
                    .setFields("id,name,mimeType")
                    .execute()
            }

            Result.success(
                CloudFileEntry(
                    id = created.id,
                    name = created.name,
                    path = created.id,
                    isDirectory = true,
                    provider = CloudProvider.GOOGLE_DRIVE
                )
            )
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "GDrive createFolder error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Count children for multiple folders in batched API calls.
     * Groups folder IDs into chunks of 20 to keep query length manageable,
     * then uses a single files.list per chunk with OR-combined parent queries.
     */
    private suspend fun countChildrenBatch(folderIds: List<String>): Map<String, Int> {
        val countMap = mutableMapOf<String, Int>()
        val folderIdSet = folderIds.toSet()

        // Chunk to avoid query length limits
        folderIds.chunked(20).forEach { chunk ->
            try {
                val parentQuery = chunk.joinToString(" or ") { "'$it' in parents" }
                val query = "($parentQuery) and trashed=false"

                var pageToken: String? = null
                do {
                    val result = withRefresh { service ->
                        val req = service.files().list()
                            .setQ(query)
                            .setFields("nextPageToken,files(parents)")
                            .setPageSize(1000)
                        if (pageToken != null) req.setPageToken(pageToken)
                        req.execute()
                    }
                    result.files?.forEach { file ->
                        file.parents?.forEach { pid ->
                            if (pid in folderIdSet) {
                                countMap[pid] = (countMap[pid] ?: 0) + 1
                            }
                        }
                    }
                    pageToken = result.nextPageToken
                } while (pageToken != null)
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "GDrive countChildren batch error: ${e.message}")
            }
        }

        return countMap
    }

    private fun buildDriveService(accessToken: String, refreshToken: String?): Drive {
        val credBuilder = UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setAccessToken(AccessToken(accessToken, Date(Long.MAX_VALUE)))
        refreshToken?.let { credBuilder.setRefreshToken(it) }
        val credentials = credBuilder.build()
        val transport = com.google.api.client.http.javanet.NetHttpTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        return Drive.Builder(transport, jsonFactory, HttpCredentialsAdapter(credentials))
            .setApplicationName("Haron")
            .build()
    }

    private fun getService(): Drive? {
        if (driveService != null) return driveService
        initDriveService()
        return driveService
    }

    private fun initDriveService() {
        val tokens = tokenStore.loadByKey(tokenKey) ?: return
        driveService = buildDriveService(tokens.accessToken, tokens.refreshToken)
    }

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
                driveService = null
                initDriveService()
                EcosystemLogger.d(HaronConstants.TAG, "GDrive: token refreshed successfully")
            }
            result.isSuccess
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "GDrive: token refresh failed: ${e.message}")
            false
        }
    }

    /** Execute Drive API call with automatic token refresh on 401 */
    private suspend fun <T> withRefresh(block: (Drive) -> T): T {
        val service = getService() ?: throw Exception("Not authenticated")
        return try {
            block(service)
        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            if (e.statusCode == 401 && refreshToken()) {
                val newService = getService() ?: throw Exception("Not authenticated after refresh")
                block(newService)
            } else throw e
        } catch (e: com.google.api.client.http.HttpResponseException) {
            if (e.statusCode == 401 && refreshToken()) {
                val newService = getService() ?: throw Exception("Not authenticated after refresh")
                block(newService)
            } else throw e
        }
    }
}
