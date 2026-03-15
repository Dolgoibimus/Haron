package com.vamp.haron.data.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpProgressMonitor
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.terminal.SshCredential
import com.vamp.haron.data.terminal.SshCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Vector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SftpClientManager @Inject constructor(
    private val credentialStore: SshCredentialStore
) {
    private var session: Session? = null
    private var channel: ChannelSftp? = null
    private var currentCredential: SshCredential? = null
    private val mutex = Mutex()

    val isConnected: Boolean
        get() = session?.isConnected == true && channel?.isConnected == true

    suspend fun connect(credential: SshCredential): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                EcosystemLogger.d(HaronConstants.TAG, "SftpClientManager: connecting to ${credential.host}:${credential.port} user=${credential.user}")
                disconnect()

                val jsch = JSch()
                val newSession = jsch.getSession(credential.user, credential.host, credential.port)
                newSession.setPassword(credential.password)
                newSession.setConfig("StrictHostKeyChecking", "no")
                newSession.timeout = HaronConstants.FTP_CONNECT_TIMEOUT_MS

                newSession.connect(HaronConstants.FTP_CONNECT_TIMEOUT_MS)

                val sftp = newSession.openChannel("sftp") as ChannelSftp
                sftp.connect(HaronConstants.FTP_CONNECT_TIMEOUT_MS)

                session = newSession
                channel = sftp
                currentCredential = credential
                EcosystemLogger.d(HaronConstants.TAG, "SftpClientManager: connected to ${credential.host}:${credential.port}")
                Result.success(Unit)
            } catch (e: Throwable) {
                EcosystemLogger.e(HaronConstants.TAG, "SftpClientManager: connect failed: ${e.message}")
                disconnect()
                Result.failure(e)
            }
        }

    suspend fun listFiles(path: String): Result<List<SftpFileInfo>> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val ch = channel
                        ?: return@withContext Result.failure(IllegalStateException("Not connected"))
                    val dirPath = path.ifEmpty { "/" }
                    @Suppress("UNCHECKED_CAST")
                    val entries = ch.ls(dirPath) as Vector<ChannelSftp.LsEntry>

                    val files = entries
                        .filter { it.filename != "." && it.filename != ".." }
                        .map { entry ->
                            val attrs = entry.attrs
                            val fullPath = if (dirPath == "/") "/${entry.filename}"
                            else "$dirPath/${entry.filename}"
                            SftpFileInfo(
                                name = entry.filename,
                                path = fullPath,
                                isDirectory = attrs.isDir,
                                size = attrs.size,
                                lastModified = attrs.mTime.toLong() * 1000L,
                                permissions = attrs.permissionsString ?: ""
                            )
                        }
                        .sortedWith(
                            compareByDescending<SftpFileInfo> { it.isDirectory }
                                .thenBy { it.name.lowercase() }
                        )

                    EcosystemLogger.d(HaronConstants.TAG, "SftpClientManager: listed ${files.size} files in $dirPath")
                    Result.success(files)
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "SftpClientManager: listFiles failed: ${e.message}")
                    Result.failure(e)
                }
            }
        }

    fun downloadFile(remotePath: String, localDest: File): Flow<SftpTransferProgress> = flow {
        mutex.withLock {
            val ch = channel ?: throw IllegalStateException("Not connected")
            val fileName = remotePath.substringAfterLast("/")
            EcosystemLogger.d(HaronConstants.TAG, "SftpClientManager: download started $remotePath -> ${localDest.name}")

            val attrs = ch.stat(remotePath)
            val totalSize = attrs.size

            val actualDest = resolveConflict(localDest)
            val monitor = object : SftpProgressMonitor {
                var transferred = 0L
                override fun init(op: Int, src: String?, dest: String?, max: Long) {}
                override fun count(count: Long): Boolean {
                    transferred += count
                    return true
                }
                override fun end() {}
            }
            ch.get(remotePath, actualDest.absolutePath, monitor)
            emit(SftpTransferProgress(fileName, totalSize, totalSize, isUpload = false))
            EcosystemLogger.d(HaronConstants.TAG, "SftpClientManager: download completed $fileName")
        }
    }.flowOn(Dispatchers.IO)

    fun uploadFile(localSrc: File, remotePath: String): Flow<SftpTransferProgress> = flow {
        mutex.withLock {
            val ch = channel ?: throw IllegalStateException("Not connected")
            val fileName = localSrc.name
            val totalSize = localSrc.length()
            EcosystemLogger.d(HaronConstants.TAG, "SftpClientManager: upload started $fileName -> $remotePath")

            val monitor = object : SftpProgressMonitor {
                var transferred = 0L
                override fun init(op: Int, src: String?, dest: String?, max: Long) {}
                override fun count(count: Long): Boolean {
                    transferred += count
                    return true
                }
                override fun end() {}
            }
            ch.put(localSrc.absolutePath, remotePath, monitor, ChannelSftp.OVERWRITE)
            emit(SftpTransferProgress(fileName, totalSize, totalSize, isUpload = true))
            EcosystemLogger.d(HaronConstants.TAG, "SftpClientManager: upload completed $fileName")
        }
    }.flowOn(Dispatchers.IO)

    suspend fun createDirectory(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val ch = channel
                        ?: return@withContext Result.failure(IllegalStateException("Not connected"))
                    EcosystemLogger.d(HaronConstants.TAG, "SftpClientManager: creating directory $path")
                    ch.mkdir(path)
                    EcosystemLogger.d(HaronConstants.TAG, "SftpClientManager: directory created $path")
                    Result.success(Unit)
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "SftpClientManager: createDirectory failed: ${e.message}")
                    Result.failure(e)
                }
            }
        }

    suspend fun delete(path: String, isDirectory: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val ch = channel
                        ?: return@withContext Result.failure(IllegalStateException("Not connected"))
                    EcosystemLogger.d(HaronConstants.TAG, "SftpClientManager: deleting ${if (isDirectory) "dir" else "file"} $path")
                    if (isDirectory) {
                        deleteDirectoryRecursive(ch, path)
                    } else {
                        ch.rm(path)
                    }
                    EcosystemLogger.d(HaronConstants.TAG, "SftpClientManager: deleted $path")
                    Result.success(Unit)
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "SftpClientManager: delete failed: ${e.message}")
                    Result.failure(e)
                }
            }
        }

    suspend fun rename(oldPath: String, newName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val ch = channel
                        ?: return@withContext Result.failure(IllegalStateException("Not connected"))
                    val parentDir = oldPath.substringBeforeLast("/", "")
                    val newPath = if (parentDir.isEmpty()) "/$newName" else "$parentDir/$newName"
                    EcosystemLogger.d(HaronConstants.TAG, "SftpClientManager: renaming $oldPath -> $newPath")
                    ch.rename(oldPath, newPath)
                    EcosystemLogger.d(HaronConstants.TAG, "SftpClientManager: renamed to $newName")
                    Result.success(Unit)
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "SftpClientManager: rename failed: ${e.message}")
                    Result.failure(e)
                }
            }
        }

    fun disconnect() {
        EcosystemLogger.d(HaronConstants.TAG, "SftpClientManager: disconnecting")
        try { channel?.disconnect() } catch (_: Exception) {}
        try { session?.disconnect() } catch (_: Exception) {}
        channel = null
        session = null
        currentCredential = null
    }

    suspend fun autoReconnect(): Result<Unit> {
        val cred = currentCredential ?: return Result.failure(IllegalStateException("No credential"))
        EcosystemLogger.d(HaronConstants.TAG, "SftpClientManager: auto-reconnecting to ${cred.host}:${cred.port}")
        return connect(cred)
    }

    private fun deleteDirectoryRecursive(ch: ChannelSftp, path: String) {
        @Suppress("UNCHECKED_CAST")
        val entries = ch.ls(path) as Vector<ChannelSftp.LsEntry>
        for (entry in entries) {
            if (entry.filename == "." || entry.filename == "..") continue
            val childPath = "$path/${entry.filename}"
            if (entry.attrs.isDir) {
                deleteDirectoryRecursive(ch, childPath)
            } else {
                ch.rm(childPath)
            }
        }
        ch.rmdir(path)
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

data class SftpTransferProgress(
    val fileName: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val isUpload: Boolean
)
