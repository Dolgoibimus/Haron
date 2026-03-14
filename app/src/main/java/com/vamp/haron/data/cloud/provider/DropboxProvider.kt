package com.vamp.haron.data.cloud.provider

import android.content.Context
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.InvalidAccessTokenException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.CommitInfo
import com.dropbox.core.v2.files.UploadSessionCursor
import com.dropbox.core.v2.files.WriteMode
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.cloud.CloudOAuthHelper
import com.vamp.haron.data.cloud.CloudTokenStore
import com.vamp.haron.domain.model.CloudFileEntry
import com.vamp.haron.domain.model.CloudProvider
import com.vamp.haron.domain.model.CloudTransferProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong

class DropboxProvider(
    private val context: Context,
    private val tokenStore: CloudTokenStore
) : CloudProviderInterface {

    companion object {
        const val APP_KEY = "w16lhavuph6eee8"
        const val TOKEN_URL = "https://api.dropboxapi.com/oauth2/token"
        private const val CHUNK_SIZE = 4L * 1024 * 1024 // 4MB chunks for upload sessions
        private const val SINGLE_UPLOAD_LIMIT = 150L * 1024 * 1024 // 150MB — Dropbox single upload limit
    }

    private var client: DbxClientV2? = null

    override fun isAuthenticated(): Boolean {
        return tokenStore.load(CloudProvider.DROPBOX) != null
    }

    override fun getAuthUrl(): String? {
        val verifier = CloudOAuthHelper.generateCodeVerifier(CloudProvider.DROPBOX.scheme)
        val challenge = CloudOAuthHelper.generateCodeChallenge(verifier)
        return "https://www.dropbox.com/oauth2/authorize" +
            "?client_id=$APP_KEY" +
            "&response_type=code" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256" +
            "&redirect_uri=${CloudOAuthHelper.REDIRECT_URI_DROPBOX}" +
            "&token_access_type=offline"
    }

    override suspend fun handleAuthCode(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val verifier = CloudOAuthHelper.getCodeVerifier(CloudProvider.DROPBOX.scheme)
                ?: return@withContext Result.failure(Exception("No PKCE code verifier"))

            val tokenResult = CloudOAuthHelper.exchangeCodeForToken(
                TOKEN_URL,
                mapOf(
                    "client_id" to APP_KEY,
                    "code" to code,
                    "code_verifier" to verifier,
                    "grant_type" to "authorization_code",
                    "redirect_uri" to CloudOAuthHelper.REDIRECT_URI_DROPBOX
                )
            )
            CloudOAuthHelper.clearCodeVerifier(CloudProvider.DROPBOX.scheme)

            tokenResult.onSuccess { token ->
                tokenStore.save(
                    CloudProvider.DROPBOX,
                    CloudTokenStore.CloudTokens(accessToken = token.accessToken, refreshToken = token.refreshToken)
                )
                initClient()
                try {
                    val account = client?.users()?.currentAccount
                    if (account != null) {
                        tokenStore.save(
                            CloudProvider.DROPBOX,
                            CloudTokenStore.CloudTokens(
                                accessToken = token.accessToken,
                                refreshToken = token.refreshToken,
                                email = account.email ?: "",
                                displayName = account.name?.displayName ?: ""
                            )
                        )
                    }
                } catch (_: Exception) { }
            }
            tokenResult.map { }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Dropbox auth error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        try {
            client?.auth()?.tokenRevoke()
        } catch (_: Exception) { }
        tokenStore.remove(CloudProvider.DROPBOX)
        client = null
    }

    override suspend fun listFiles(path: String): Result<List<CloudFileEntry>> = withContext(Dispatchers.IO) {
        try {
            val dbxPath = if (path.isEmpty() || path == "/") "" else path
            EcosystemLogger.d(HaronConstants.TAG, "Dropbox listFiles: path='$path', dbxPath='$dbxPath'")

            val allEntries = mutableListOf<CloudFileEntry>()
            var result = withRefresh { c -> c.files().listFolder(dbxPath) }

            result.entries.mapTo(allEntries) { it.toCloudFileEntry() }
            EcosystemLogger.d(HaronConstants.TAG, "Dropbox listFiles: first page ${result.entries.size} items, hasMore=${result.hasMore}")

            while (result.hasMore) {
                result = withRefresh { c -> c.files().listFolderContinue(result.cursor) }
                result.entries.mapTo(allEntries) { it.toCloudFileEntry() }
                EcosystemLogger.d(HaronConstants.TAG, "Dropbox listFiles: next page ${result.entries.size} items, hasMore=${result.hasMore}")
            }

            EcosystemLogger.d(HaronConstants.TAG, "Dropbox listFiles: total ${allEntries.size} items")

            // Enrich image files with temporary links for thumbnail previews
            val enriched = enrichWithThumbnails(allEntries)
            Result.success(enriched)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Dropbox listFiles error: ${e.message}")
            Result.failure(e)
        }
    }

    override fun downloadFile(cloudFileId: String, localPath: String): Flow<CloudTransferProgress> = flow {
        try {
            val c = getClient() ?: throw Exception("Not authenticated")
            val fileName = cloudFileId.substringAfterLast('/')

            emit(CloudTransferProgress(fileName, 0, 0))

            val outputFile = File(localPath)
            val downloader = withRefreshSuspend { getClient()!!.files().download(cloudFileId) }
            val totalSize = downloader.result.size

            emit(CloudTransferProgress(fileName, 0, totalSize))

            downloader.inputStream.use { inputStream ->
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
                            emit(CloudTransferProgress(fileName, totalRead, totalSize))
                            lastEmitPercent = percent
                        }
                    }
                }
            }

            emit(CloudTransferProgress(fileName, totalSize, totalSize, isComplete = true))
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Dropbox download error: ${e.message}")
            emit(CloudTransferProgress("", 0, 0, isComplete = true, error = e.message))
        }
    }.flowOn(Dispatchers.IO)

    override fun uploadFile(localPath: String, cloudDirPath: String, fileName: String): Flow<CloudTransferProgress> = flow {
        try {
            val file = File(localPath)
            val totalSize = file.length()
            val targetPath = if (cloudDirPath.isEmpty() || cloudDirPath == "/") "/$fileName"
                             else "$cloudDirPath/$fileName"

            EcosystemLogger.d(HaronConstants.TAG, "Dropbox upload: START fileName=$fileName, localPath=$localPath, targetPath=$targetPath, size=$totalSize (${totalSize / 1024}KB)")

            if (!file.exists()) {
                EcosystemLogger.e(HaronConstants.TAG, "Dropbox upload: file does not exist: $localPath")
                emit(CloudTransferProgress(fileName, 0, 0, isComplete = true, error = "File not found: $localPath"))
                return@flow
            }
            if (!file.canRead()) {
                EcosystemLogger.e(HaronConstants.TAG, "Dropbox upload: cannot read file: $localPath")
                emit(CloudTransferProgress(fileName, 0, 0, isComplete = true, error = "Cannot read file: $localPath"))
                return@flow
            }

            emit(CloudTransferProgress(fileName, 0, totalSize))

            // Pre-refresh token before upload (prevents expired token mid-upload)
            EcosystemLogger.d(HaronConstants.TAG, "Dropbox upload: pre-refreshing token...")
            val refreshed = refreshToken()
            EcosystemLogger.d(HaronConstants.TAG, "Dropbox upload: pre-refresh result=$refreshed")

            // Retry wrapper: 2 attempts with token refresh on failure
            val maxAttempts = 2
            var lastError: Exception? = null
            for (attempt in 1..maxAttempts) {
                try {
                    EcosystemLogger.d(HaronConstants.TAG, "Dropbox upload: attempt $attempt/$maxAttempts")

                    if (totalSize <= SINGLE_UPLOAD_LIMIT) {
                        EcosystemLogger.d(HaronConstants.TAG, "Dropbox upload: using single upload (size ${totalSize / 1024}KB <= ${SINGLE_UPLOAD_LIMIT / 1024 / 1024}MB limit)")
                        // Small file: single upload with CountingInputStream + polling
                        val bytesRead = AtomicLong(0L)
                        val uploadJob = withContext(Dispatchers.IO) {
                            async {
                                FileInputStream(file).use { fis ->
                                    val countingStream = CountingInputStream(fis, bytesRead)
                                    EcosystemLogger.d(HaronConstants.TAG, "Dropbox upload: calling uploadAndFinish for $targetPath")
                                    val startTime = System.currentTimeMillis()
                                    val result = withRefreshSuspend {
                                        getClient()!!.files().uploadBuilder(targetPath)
                                            .withMode(WriteMode.OVERWRITE)
                                            .uploadAndFinish(countingStream)
                                    }
                                    val elapsed = System.currentTimeMillis() - startTime
                                    EcosystemLogger.d(HaronConstants.TAG, "Dropbox upload: uploadAndFinish completed in ${elapsed}ms, result name=${result.name}, size=${result.size}, rev=${result.rev}")
                                    result
                                }
                            }
                        }
                        var lastEmitPercent = -1
                        while (!uploadJob.isCompleted) {
                            delay(200)
                            val read = bytesRead.get()
                            val percent = if (totalSize > 0) ((read * 100) / totalSize).toInt() else 0
                            if (percent != lastEmitPercent) {
                                emit(CloudTransferProgress(fileName, read, totalSize))
                                lastEmitPercent = percent
                                if (percent % 10 == 0) {
                                    EcosystemLogger.d(HaronConstants.TAG, "Dropbox upload: progress $percent% ($read/$totalSize)")
                                }
                            }
                        }
                        uploadJob.await() // re-throws if upload failed
                    } else {
                        // Large file: chunked upload session (Dropbox limit 150MB per single call)
                        EcosystemLogger.d(HaronConstants.TAG, "Dropbox upload: using CHUNKED session for ${totalSize / 1024 / 1024}MB file (> ${SINGLE_UPLOAD_LIMIT / 1024 / 1024}MB limit)")
                        val c = getClient() ?: throw Exception("Not authenticated")
                        FileInputStream(file).use { fis ->
                            // Start session with first chunk
                            val firstChunk = minOf(CHUNK_SIZE, totalSize)
                            EcosystemLogger.d(HaronConstants.TAG, "Dropbox upload: uploadSessionStart, firstChunk=${firstChunk / 1024}KB")
                            val startResult = c.files().uploadSessionStart()
                                .uploadAndFinish(fis, firstChunk)
                            var offset = firstChunk
                            EcosystemLogger.d(HaronConstants.TAG, "Dropbox upload: session started, sessionId=${startResult.sessionId}, offset=$offset")
                            emit(CloudTransferProgress(fileName, offset, totalSize))

                            // Append chunks + finish with last chunk
                            var chunkNum = 1
                            while (offset < totalSize) {
                                val remaining = totalSize - offset
                                val thisChunk = minOf(CHUNK_SIZE, remaining)
                                val cursor = UploadSessionCursor(startResult.sessionId, offset)
                                chunkNum++

                                if (offset + thisChunk >= totalSize) {
                                    // Last chunk — finish session
                                    EcosystemLogger.d(HaronConstants.TAG, "Dropbox upload: chunk #$chunkNum FINISH, offset=$offset, chunkSize=${thisChunk / 1024}KB")
                                    val commitInfo = CommitInfo.newBuilder(targetPath)
                                        .withMode(WriteMode.OVERWRITE)
                                        .build()
                                    c.files().uploadSessionFinish(cursor, commitInfo)
                                        .uploadAndFinish(fis, thisChunk)
                                    EcosystemLogger.d(HaronConstants.TAG, "Dropbox upload: session finished OK")
                                } else {
                                    EcosystemLogger.d(HaronConstants.TAG, "Dropbox upload: chunk #$chunkNum APPEND, offset=$offset, chunkSize=${thisChunk / 1024}KB")
                                    c.files().uploadSessionAppendV2(cursor)
                                        .uploadAndFinish(fis, thisChunk)
                                }
                                offset += thisChunk
                                val percent = ((offset * 100) / totalSize).toInt()
                                EcosystemLogger.d(HaronConstants.TAG, "Dropbox upload: chunk #$chunkNum done, offset=$offset, $percent%")
                                emit(CloudTransferProgress(fileName, offset, totalSize))
                            }
                        }
                    }

                    lastError = null
                    EcosystemLogger.d(HaronConstants.TAG, "Dropbox upload: attempt $attempt SUCCEEDED")
                    break // success
                } catch (e: Exception) {
                    lastError = e
                    EcosystemLogger.e(HaronConstants.TAG, "Dropbox upload: attempt $attempt/$maxAttempts FAILED: ${e.javaClass.simpleName}: ${e.message}")
                    if (attempt < maxAttempts) {
                        EcosystemLogger.d(HaronConstants.TAG, "Dropbox upload: refreshing token before retry...")
                        refreshToken()
                        delay(1000)
                    }
                }
            }
            if (lastError != null) throw lastError

            emit(CloudTransferProgress(fileName, totalSize, totalSize, isComplete = true))
            EcosystemLogger.d(HaronConstants.TAG, "Dropbox upload: COMPLETE $fileName ($totalSize bytes)")
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Dropbox upload FINAL ERROR: ${e.javaClass.simpleName}: ${e.message}")
            e.cause?.let { cause ->
                EcosystemLogger.e(HaronConstants.TAG, "Dropbox upload cause: ${cause.javaClass.simpleName}: ${cause.message}")
            }
            emit(CloudTransferProgress(fileName, 0, 0, isComplete = true, error = e.message))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun delete(cloudFileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            withRefresh { c -> c.files().deleteV2(cloudFileId) }
            Result.success(Unit)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Dropbox delete error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun createFolder(parentPath: String, name: String): Result<CloudFileEntry> = withContext(Dispatchers.IO) {
        try {
            val folderPath = if (parentPath.isEmpty() || parentPath == "/") "/$name"
                             else "$parentPath/$name"
            val result = withRefresh { c -> c.files().createFolderV2(folderPath) }
            val meta = result.metadata
            Result.success(
                CloudFileEntry(
                    id = meta.id,
                    name = meta.name,
                    path = meta.pathDisplay ?: meta.pathLower ?: folderPath,
                    isDirectory = true,
                    provider = CloudProvider.DROPBOX
                )
            )
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Dropbox createFolder error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun rename(cloudFileId: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val parentPath = cloudFileId.substringBeforeLast('/')
            val newPath = "$parentPath/$newName"
            withRefresh { c -> c.files().moveV2(cloudFileId, newPath) }
            EcosystemLogger.d(HaronConstants.TAG, "Dropbox rename: $cloudFileId → $newPath")
            Result.success(Unit)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Dropbox rename error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun moveFile(
        fileId: String,
        currentParentId: String,
        newParentId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val fileName = fileId.substringAfterLast('/')
            val toPath = "$newParentId/$fileName"
            withRefresh { c -> c.files().moveV2(fileId, toPath) }
            EcosystemLogger.d(HaronConstants.TAG, "Dropbox moveFile: $fileId → $toPath")
            Result.success(Unit)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Dropbox moveFile error: ${e.message}")
            Result.failure(e)
        }
    }

    override fun getAccessToken(): String? {
        return tokenStore.load(CloudProvider.DROPBOX)?.accessToken
    }

    override suspend fun getFreshAccessToken(): String? = withContext(Dispatchers.IO) {
        try {
            refreshToken()
            EcosystemLogger.d(HaronConstants.TAG, "Dropbox getFreshAccessToken: token refreshed")
            tokenStore.load(CloudProvider.DROPBOX)?.accessToken
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Dropbox getFreshAccessToken failed: ${e.message}")
            tokenStore.load(CloudProvider.DROPBOX)?.accessToken
        }
    }

    override fun updateFileContent(cloudFileId: String, localPath: String): Flow<CloudTransferProgress> = flow {
        try {
            val file = File(localPath)
            val totalSize = file.length()
            val fileName = file.name

            EcosystemLogger.d(HaronConstants.TAG, "Dropbox updateFileContent: START cloudFileId=$cloudFileId, localPath=$localPath, size=$totalSize (${totalSize / 1024}KB)")

            emit(CloudTransferProgress(fileName, 0, totalSize))

            // Pre-refresh token
            EcosystemLogger.d(HaronConstants.TAG, "Dropbox updateFileContent: pre-refreshing token...")
            refreshToken()

            // Retry wrapper: 2 attempts
            val maxAttempts = 2
            var lastError: Exception? = null
            for (attempt in 1..maxAttempts) {
                try {
                    EcosystemLogger.d(HaronConstants.TAG, "Dropbox updateFileContent: attempt $attempt/$maxAttempts")

                    if (totalSize <= SINGLE_UPLOAD_LIMIT) {
                        val bytesRead = AtomicLong(0L)
                        val uploadJob = withContext(Dispatchers.IO) {
                            async {
                                FileInputStream(file).use { fis ->
                                    val countingStream = CountingInputStream(fis, bytesRead)
                                    EcosystemLogger.d(HaronConstants.TAG, "Dropbox updateFileContent: calling uploadAndFinish for $cloudFileId")
                                    val startTime = System.currentTimeMillis()
                                    val result = withRefreshSuspend {
                                        getClient()!!.files().uploadBuilder(cloudFileId)
                                            .withMode(WriteMode.OVERWRITE)
                                            .uploadAndFinish(countingStream)
                                    }
                                    val elapsed = System.currentTimeMillis() - startTime
                                    EcosystemLogger.d(HaronConstants.TAG, "Dropbox updateFileContent: completed in ${elapsed}ms, rev=${result.rev}")
                                    result
                                }
                            }
                        }
                        var lastEmitPercent = -1
                        while (!uploadJob.isCompleted) {
                            delay(200)
                            val read = bytesRead.get()
                            val percent = if (totalSize > 0) ((read * 100) / totalSize).toInt() else 0
                            if (percent != lastEmitPercent) {
                                emit(CloudTransferProgress(fileName, read, totalSize))
                                lastEmitPercent = percent
                            }
                        }
                        uploadJob.await()
                    } else {
                        EcosystemLogger.d(HaronConstants.TAG, "Dropbox updateFileContent: using CHUNKED session for ${totalSize / 1024 / 1024}MB file")
                        val c = getClient() ?: throw Exception("Not authenticated")
                        FileInputStream(file).use { fis ->
                            val firstChunk = minOf(CHUNK_SIZE, totalSize)
                            val startResult = c.files().uploadSessionStart()
                                .uploadAndFinish(fis, firstChunk)
                            var offset = firstChunk
                            EcosystemLogger.d(HaronConstants.TAG, "Dropbox updateFileContent: session started, sessionId=${startResult.sessionId}")
                            emit(CloudTransferProgress(fileName, offset, totalSize))

                            var chunkNum = 1
                            while (offset < totalSize) {
                                val remaining = totalSize - offset
                                val thisChunk = minOf(CHUNK_SIZE, remaining)
                                val cursor = UploadSessionCursor(startResult.sessionId, offset)
                                chunkNum++

                                if (offset + thisChunk >= totalSize) {
                                    val commitInfo = CommitInfo.newBuilder(cloudFileId)
                                        .withMode(WriteMode.OVERWRITE)
                                        .build()
                                    c.files().uploadSessionFinish(cursor, commitInfo)
                                        .uploadAndFinish(fis, thisChunk)
                                    EcosystemLogger.d(HaronConstants.TAG, "Dropbox updateFileContent: chunk #$chunkNum FINISH done")
                                } else {
                                    c.files().uploadSessionAppendV2(cursor)
                                        .uploadAndFinish(fis, thisChunk)
                                    EcosystemLogger.d(HaronConstants.TAG, "Dropbox updateFileContent: chunk #$chunkNum APPEND done")
                                }
                                offset += thisChunk
                                emit(CloudTransferProgress(fileName, offset, totalSize))
                            }
                        }
                    }

                    lastError = null
                    EcosystemLogger.d(HaronConstants.TAG, "Dropbox updateFileContent: attempt $attempt SUCCEEDED")
                    break
                } catch (e: Exception) {
                    lastError = e
                    EcosystemLogger.e(HaronConstants.TAG, "Dropbox updateFileContent: attempt $attempt/$maxAttempts FAILED: ${e.javaClass.simpleName}: ${e.message}")
                    if (attempt < maxAttempts) {
                        refreshToken()
                        delay(1000)
                    }
                }
            }
            if (lastError != null) throw lastError

            emit(CloudTransferProgress(fileName, totalSize, totalSize, isComplete = true))
            EcosystemLogger.d(HaronConstants.TAG, "Dropbox updateFileContent: COMPLETE $fileName")
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Dropbox updateFileContent FINAL ERROR: ${e.javaClass.simpleName}: ${e.message}")
            emit(CloudTransferProgress("", 0, 0, isComplete = true, error = e.message))
        }
    }.flowOn(Dispatchers.IO)

    // ── Token refresh ──────────────────────────────────────────────────

    private suspend fun refreshToken(): Boolean = withContext(Dispatchers.IO) {
        val tokens = tokenStore.load(CloudProvider.DROPBOX) ?: return@withContext false
        val refreshToken = tokens.refreshToken ?: return@withContext false
        EcosystemLogger.d(HaronConstants.TAG, "Dropbox: refreshing access token...")

        try {
            val result = CloudOAuthHelper.exchangeCodeForToken(
                TOKEN_URL,
                mapOf(
                    "client_id" to APP_KEY,
                    "refresh_token" to refreshToken,
                    "grant_type" to "refresh_token"
                )
            )
            result.onSuccess { newToken ->
                tokenStore.save(
                    CloudProvider.DROPBOX,
                    CloudTokenStore.CloudTokens(
                        accessToken = newToken.accessToken,
                        refreshToken = newToken.refreshToken ?: refreshToken,
                        email = tokens.email,
                        displayName = tokens.displayName
                    )
                )
                client = null
                initClient()
                EcosystemLogger.d(HaronConstants.TAG, "Dropbox: token refreshed successfully")
            }
            result.isSuccess
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Dropbox: token refresh failed: ${e.message}")
            false
        }
    }

    /** Execute Dropbox API call with automatic token refresh on InvalidAccessTokenException */
    private suspend fun <T> withRefresh(block: (DbxClientV2) -> T): T {
        val c = getClient() ?: throw Exception("Not authenticated")
        return try {
            block(c)
        } catch (e: InvalidAccessTokenException) {
            EcosystemLogger.d(HaronConstants.TAG, "Dropbox: token expired, refreshing...")
            if (refreshToken()) {
                val newClient = getClient() ?: throw Exception("Not authenticated after refresh")
                block(newClient)
            } else throw e
        }
    }

    /** Suspend variant of withRefresh for use inside coroutines where block itself is suspend */
    private suspend fun <T> withRefreshSuspend(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: InvalidAccessTokenException) {
            EcosystemLogger.d(HaronConstants.TAG, "Dropbox: token expired (suspend), refreshing...")
            if (refreshToken()) {
                block()
            } else throw e
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun getClient(): DbxClientV2? {
        if (client != null) return client
        initClient()
        return client
    }

    private fun initClient() {
        val tokens = tokenStore.load(CloudProvider.DROPBOX) ?: return
        val config = DbxRequestConfig.newBuilder("Haron").build()
        client = DbxClientV2(config, tokens.accessToken)
    }

    private fun com.dropbox.core.v2.files.Metadata.toCloudFileEntry(): CloudFileEntry {
        return when (this) {
            is FolderMetadata -> CloudFileEntry(
                id = pathDisplay ?: pathLower ?: "",
                name = name,
                path = pathDisplay ?: pathLower ?: "",
                isDirectory = true,
                provider = CloudProvider.DROPBOX
            )
            is FileMetadata -> CloudFileEntry(
                id = pathDisplay ?: pathLower ?: "",
                name = name,
                path = pathDisplay ?: pathLower ?: "",
                isDirectory = false,
                size = size,
                lastModified = serverModified?.time ?: 0L,
                provider = CloudProvider.DROPBOX
            )
            else -> CloudFileEntry(
                id = pathLower ?: "",
                name = name,
                path = pathDisplay ?: pathLower ?: "",
                isDirectory = false,
                provider = CloudProvider.DROPBOX
            )
        }
    }

    /** Generate temporary download links for previewable files (thumbnails in grid).
     *  Images, PDFs, text, code, documents — up to 50 files, max 10MB each. */
    private suspend fun enrichWithThumbnails(entries: List<CloudFileEntry>): List<CloudFileEntry> {
        val previewable = entries.filter { !it.isDirectory && isPreviewableFile(it.name) && it.size < 10 * 1024 * 1024 }
        if (previewable.isEmpty()) return entries

        val thumbLinks = coroutineScope {
            previewable.take(50).map { entry ->
                async {
                    try {
                        val c = getClient() ?: return@async null
                        val link = c.files().getTemporaryLink(entry.path)
                        entry.path to link.link
                    } catch (_: Exception) { null }
                }
            }.awaitAll()
        }.filterNotNull().toMap()

        if (thumbLinks.isEmpty()) return entries
        EcosystemLogger.d(HaronConstants.TAG, "Dropbox: enriched ${thumbLinks.size} files with temp links")
        return entries.map { e ->
            if (e.path in thumbLinks) e.copy(thumbnailUrl = thumbLinks[e.path])
            else e
        }
    }

    private fun isPreviewableFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "svg", "ico", "tiff", "tif",
            "pdf", "txt", "log", "md", "json", "xml", "html", "htm", "css", "js", "ts", "kt", "java",
            "py", "c", "cpp", "h", "hpp", "cs", "go", "rs", "sh", "bat", "yaml", "yml", "toml",
            "ini", "cfg", "conf", "properties", "gradle", "sql", "csv",
            "docx", "odt", "doc", "rtf", "fb2", "apk"
        )
    }

    /** InputStream wrapper that counts bytes read for progress tracking */
    private class CountingInputStream(
        stream: InputStream,
        private val counter: AtomicLong
    ) : FilterInputStream(stream) {
        override fun read(): Int {
            val b = super.read()
            if (b != -1) counter.incrementAndGet()
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) counter.addAndGet(n.toLong())
            return n
        }
    }
}
