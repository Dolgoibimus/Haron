package com.vamp.haron.data.ftp

import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private data class FtpConnection(
    val client: FTPClient,
    val credential: FtpCredential?,
    val mutex: Mutex = Mutex(),
    var lastUsed: Long = System.currentTimeMillis()
)

/**
 * FTP/FTPS client for browsing and transferring files on remote FTP servers.
 * Manages a pool of connections keyed by host:port, with automatic reconnection
 * and thread-safe access via per-connection mutexes.
 * Uses Apache Commons Net for FTP protocol operations.
 */
@Singleton
class FtpClientManager @Inject constructor(
    private val credentialStore: FtpCredentialStore
) {
    private val connections = ConcurrentHashMap<String, FtpConnection>()

    suspend fun connect(credential: FtpCredential): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val key = FtpPathUtils.connectionKey(credential.host, credential.port)
                EcosystemLogger.d(HaronConstants.TAG, "FtpClientManager: connecting to ${credential.host}:${credential.port} user=${credential.username}")
                disconnect(credential.host, credential.port)

                val client = if (credential.useFtps) FTPSClient(true) else FTPClient()
                client.connectTimeout = HaronConstants.FTP_CONNECT_TIMEOUT_MS
                client.defaultTimeout = HaronConstants.FTP_SO_TIMEOUT_MS
                client.controlEncoding = "UTF-8"
                client.setAutodetectUTF8(true)

                client.connect(credential.host, credential.port)
                val reply = client.replyCode
                if (!FTPReply.isPositiveCompletion(reply)) {
                    client.disconnect()
                    return@withContext Result.failure(
                        IllegalStateException("FTP connect rejected: $reply")
                    )
                }

                val loggedIn = client.login(credential.username, credential.password)
                if (!loggedIn) {
                    client.disconnect()
                    return@withContext Result.failure(
                        IllegalStateException("FTP login failed for ${credential.username}")
                    )
                }

                client.enterLocalPassiveMode()
                client.setFileType(FTP.BINARY_FILE_TYPE)
                client.soTimeout = HaronConstants.FTP_SO_TIMEOUT_MS
                // Request UTF-8 from server (IIS, vsftpd, etc.)
                try { client.sendCommand("OPTS UTF8 ON") } catch (_: Exception) {}

                connections[key] = FtpConnection(client, credential)
                EcosystemLogger.d(HaronConstants.TAG, "FtpClientManager: connected to ${credential.host}:${credential.port}")
                Result.success(Unit)
            } catch (e: Throwable) {
                EcosystemLogger.e(HaronConstants.TAG, "FtpClientManager: connect failed: ${e.message}")
                Result.failure(e)
            }
        }

    suspend fun connectAnonymous(host: String, port: Int): Result<Unit> =
        connect(FtpCredential(host, port, "anonymous", "haron@android", displayName = host))

    suspend fun listFiles(host: String, port: Int, path: String): Result<List<FtpFileInfo>> =
        withContext(Dispatchers.IO) {
            val key = FtpPathUtils.connectionKey(host, port)
            val conn = connections[key]
                ?: return@withContext Result.failure(IllegalStateException("Not connected"))
            conn.mutex.withLock {
                try {
                    conn.lastUsed = System.currentTimeMillis()
                    val dirPath = path.ifEmpty { "/" }
                    val ftpFiles: Array<FTPFile> = conn.client.listFiles(dirPath) ?: emptyArray()

                    val files = ftpFiles
                        .filter { it.name != "." && it.name != ".." }
                        .map { f ->
                            val fullPath = if (dirPath == "/") "/${f.name}"
                            else "$dirPath/${f.name}"
                            FtpFileInfo(
                                name = f.name,
                                isDirectory = f.isDirectory,
                                size = f.size,
                                lastModified = f.timestamp?.timeInMillis ?: 0L,
                                path = fullPath,
                                permissions = f.toFormattedString()?.take(10) ?: ""
                            )
                        }
                        .sortedWith(
                            compareByDescending<FtpFileInfo> { it.isDirectory }
                                .thenBy { it.name.lowercase() }
                        )

                    EcosystemLogger.d(HaronConstants.TAG, "FtpClientManager: listed ${files.size} files in $dirPath")
                    Result.success(files)
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "FtpClientManager: listFiles failed: ${e.message}")
                    // Try reconnect on failure
                    tryReconnect(key)
                    Result.failure(e)
                }
            }
        }

    fun downloadFile(host: String, port: Int, remotePath: String, localDest: File): Flow<FtpTransferProgress> = flow {
        val key = FtpPathUtils.connectionKey(host, port)
        val conn = connections[key]
            ?: throw IllegalStateException("Not connected")
        EcosystemLogger.d(HaronConstants.TAG, "FtpClientManager: download started $remotePath -> ${localDest.name}")

        conn.mutex.withLock {
            conn.lastUsed = System.currentTimeMillis()
            val fileName = remotePath.substringAfterLast("/")
            // Get remote file size
            val totalSize = conn.client.mlistFile(remotePath)?.size
                ?: conn.client.listFiles(remotePath)?.firstOrNull()?.size
                ?: -1L

            val actualDest = resolveConflict(localDest)
            actualDest.outputStream().use { output ->
                conn.client.retrieveFileStream(remotePath)?.use { input ->
                    val buffer = ByteArray(HaronConstants.TRANSFER_BUFFER_SIZE)
                    var transferred = 0L
                    var read: Int
                    val startTime = System.currentTimeMillis()
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        transferred += read
                        val elapsed = System.currentTimeMillis() - startTime
                        val speed = if (elapsed > 500) transferred * 1000 / elapsed else 0L
                        emit(FtpTransferProgress(fileName, transferred, totalSize, isUpload = false, speedBytesPerSec = speed))
                    }
                } ?: throw IllegalStateException("Cannot open remote file: $remotePath")
            }
            conn.client.completePendingCommand()
        }
        EcosystemLogger.d(HaronConstants.TAG, "FtpClientManager: download completed ${remotePath.substringAfterLast("/")}")
    }.flowOn(Dispatchers.IO)

    /** Get remote file size (for Content-Length in HTTP proxy) */
    suspend fun getFileSize(host: String, port: Int, remotePath: String): Long = withContext(Dispatchers.IO) {
        val key = FtpPathUtils.connectionKey(host, port)
        val conn = connections[key] ?: return@withContext -1L
        conn.mutex.withLock {
            conn.lastUsed = System.currentTimeMillis()
            conn.client.mlistFile(remotePath)?.size
                ?: conn.client.listFiles(remotePath)?.firstOrNull()?.size
                ?: -1L
        }
    }

    /** Open InputStream for streaming (caller must call completePendingCommand after closing stream) */
    suspend fun openInputStream(host: String, port: Int, remotePath: String, offset: Long = 0): Pair<java.io.InputStream, () -> Unit>? = withContext(Dispatchers.IO) {
        val key = FtpPathUtils.connectionKey(host, port)
        val conn = connections[key] ?: return@withContext null
        conn.mutex.lock()
        conn.lastUsed = System.currentTimeMillis()
        if (offset > 0) {
            conn.client.restartOffset = offset
        }
        val stream = conn.client.retrieveFileStream(remotePath)
        if (stream == null) {
            conn.mutex.unlock()
            return@withContext null
        }
        // Return stream + cleanup function (must be called when done)
        stream to {
            try { stream.close() } catch (_: Exception) {}
            try { conn.client.completePendingCommand() } catch (_: Exception) {}
            conn.mutex.unlock()
        }
    }

    fun uploadFile(host: String, port: Int, remotePath: String, localSrc: File): Flow<FtpTransferProgress> = flow {
        val key = FtpPathUtils.connectionKey(host, port)
        val conn = connections[key]
            ?: throw IllegalStateException("Not connected")
        EcosystemLogger.d(HaronConstants.TAG, "FtpClientManager: upload started ${localSrc.name} -> $remotePath")

        conn.mutex.withLock {
            conn.lastUsed = System.currentTimeMillis()
            val totalSize = localSrc.length()
            val fileName = localSrc.name

            conn.client.storeFileStream(remotePath)?.use { output ->
                localSrc.inputStream().use { input ->
                    val buffer = ByteArray(HaronConstants.TRANSFER_BUFFER_SIZE)
                    var transferred = 0L
                    var read: Int
                    val startTime = System.currentTimeMillis()
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        transferred += read
                        val elapsed = System.currentTimeMillis() - startTime
                        val speed = if (elapsed > 500) transferred * 1000 / elapsed else 0L
                        emit(FtpTransferProgress(fileName, transferred, totalSize, isUpload = true, speedBytesPerSec = speed))
                    }
                }
            } ?: throw IllegalStateException("Cannot store remote file: $remotePath")
            conn.client.completePendingCommand()
        }
        EcosystemLogger.d(HaronConstants.TAG, "FtpClientManager: upload completed ${localSrc.name}")
    }.flowOn(Dispatchers.IO)

    suspend fun createDirectory(host: String, port: Int, path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val key = FtpPathUtils.connectionKey(host, port)
            val conn = connections[key]
                ?: return@withContext Result.failure(IllegalStateException("Not connected"))
            conn.mutex.withLock {
                try {
                    EcosystemLogger.d(HaronConstants.TAG, "FtpClientManager: creating directory $path")
                    val success = conn.client.makeDirectory(path)
                    if (success) {
                        EcosystemLogger.d(HaronConstants.TAG, "FtpClientManager: directory created $path")
                        Result.success(Unit)
                    } else {
                        Result.failure(IllegalStateException("Failed to create directory: ${conn.client.replyString}"))
                    }
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "FtpClientManager: createDirectory failed: ${e.message}")
                    Result.failure(e)
                }
            }
        }

    suspend fun delete(host: String, port: Int, path: String, isDirectory: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            val key = FtpPathUtils.connectionKey(host, port)
            val conn = connections[key]
                ?: return@withContext Result.failure(IllegalStateException("Not connected"))
            conn.mutex.withLock {
                try {
                    EcosystemLogger.d(HaronConstants.TAG, "FtpClientManager: deleting ${if (isDirectory) "dir" else "file"} $path")
                    val success = if (isDirectory) {
                        deleteDirectoryRecursive(conn.client, path)
                    } else {
                        conn.client.deleteFile(path)
                    }
                    if (success) {
                        EcosystemLogger.d(HaronConstants.TAG, "FtpClientManager: deleted $path")
                        Result.success(Unit)
                    } else {
                        Result.failure(IllegalStateException("Failed to delete: ${conn.client.replyString}"))
                    }
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "FtpClientManager: delete failed: ${e.message}")
                    Result.failure(e)
                }
            }
        }

    suspend fun rename(host: String, port: Int, oldPath: String, newName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val key = FtpPathUtils.connectionKey(host, port)
            val conn = connections[key]
                ?: return@withContext Result.failure(IllegalStateException("Not connected"))
            conn.mutex.withLock {
                try {
                    val parentDir = oldPath.substringBeforeLast("/", "")
                    val newPath = if (parentDir.isEmpty()) "/$newName" else "$parentDir/$newName"
                    EcosystemLogger.d(HaronConstants.TAG, "FtpClientManager: renaming $oldPath -> $newPath")
                    val success = conn.client.rename(oldPath, newPath)
                    if (success) {
                        EcosystemLogger.d(HaronConstants.TAG, "FtpClientManager: renamed to $newName")
                        Result.success(Unit)
                    } else {
                        Result.failure(IllegalStateException("Failed to rename: ${conn.client.replyString}"))
                    }
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "FtpClientManager: rename failed: ${e.message}")
                    Result.failure(e)
                }
            }
        }

    fun disconnect(host: String, port: Int) {
        val key = FtpPathUtils.connectionKey(host, port)
        connections.remove(key)?.let { conn ->
            EcosystemLogger.d(HaronConstants.TAG, "FtpClientManager: disconnecting from $key")
            try {
                conn.client.logout()
            } catch (_: Exception) {
            }
            try {
                conn.client.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    fun disconnectAll() {
        EcosystemLogger.d(HaronConstants.TAG, "FtpClientManager: disconnecting all (${connections.size})")
        val keys = connections.keys.toList()
        keys.forEach { key ->
            connections.remove(key)?.let { conn ->
                try { conn.client.logout() } catch (_: Exception) {}
                try { conn.client.disconnect() } catch (_: Exception) {}
            }
        }
    }

    fun isConnected(host: String, port: Int): Boolean {
        val key = FtpPathUtils.connectionKey(host, port)
        val conn = connections[key] ?: return false
        return conn.client.isConnected
    }

    suspend fun autoReconnect(host: String, port: Int): Result<Unit> {
        EcosystemLogger.d(HaronConstants.TAG, "FtpClientManager: auto-reconnecting to $host:$port")
        val savedCred = credentialStore.load(host, port)
        return if (savedCred != null) {
            connect(savedCred)
        } else {
            connectAnonymous(host, port)
        }
    }

    private fun deleteDirectoryRecursive(client: FTPClient, path: String): Boolean {
        val files = client.listFiles(path) ?: return client.removeDirectory(path)
        for (f in files) {
            if (f.name == "." || f.name == "..") continue
            val childPath = "$path/${f.name}"
            if (f.isDirectory) {
                deleteDirectoryRecursive(client, childPath)
            } else {
                client.deleteFile(childPath)
            }
        }
        return client.removeDirectory(path)
    }

    private suspend fun tryReconnect(key: String) {
        val conn = connections[key] ?: return
        val cred = conn.credential ?: return
        try {
            connect(cred)
        } catch (_: Exception) {
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
