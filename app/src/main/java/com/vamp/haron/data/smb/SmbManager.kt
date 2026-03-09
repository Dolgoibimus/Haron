package com.vamp.haron.data.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.common.SMBRuntimeException
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.PipeShare
import com.rapid7.client.dcerpc.Interface
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.mssrvs.dto.NetShareInfo0
import com.rapid7.client.dcerpc.transport.SMBTransport
import com.rapid7.helper.smbj.share.NamedPipe
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.security.Security
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class SmbShareInfo(
    val name: String,
    val type: Int = 0
)

data class SmbFileInfo(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val path: String
)

data class SmbTransferProgress(
    val fileName: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val isUpload: Boolean
)

private data class SmbConnection(
    val client: SMBClient,
    val connection: Connection,
    val session: Session,
    val credential: SmbCredential?,
    var lastUsed: Long = System.currentTimeMillis()
)

@Singleton
class SmbManager @Inject constructor(
    private val smbClient: SMBClient,
    private val credentialStore: SmbCredentialStore
) {
    init {
        // Replace Android's limited BouncyCastle with full version (bcprov-jdk15to18)
        // Required for smbj NTLM/SPNEGO crypto (MD4, RC4, etc.)
        val currentBc = Security.getProvider("BC")
        if (currentBc == null || currentBc.javaClass.name != "org.bouncycastle.jce.provider.BouncyCastleProvider") {
            Security.removeProvider("BC")
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
        EcosystemLogger.d(HaronConstants.TAG, "SmbManager: initialized, BouncyCastle provider configured")
    }

    private val connections = ConcurrentHashMap<String, SmbConnection>()
    private val openShares = ConcurrentHashMap<String, DiskShare>()

    suspend fun connect(host: String, port: Int, credential: SmbCredential): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                EcosystemLogger.d(HaronConstants.TAG, "SmbManager: connecting to $host:$port user=${credential.username}")
                disconnect(host)
                val conn = smbClient.connect(host, port)
                val authCtx = AuthenticationContext(
                    credential.username,
                    credential.password.toCharArray(),
                    credential.domain.ifEmpty { null }
                )
                val session = conn.authenticate(authCtx)
                connections[host] = SmbConnection(smbClient, conn, session, credential)
                EcosystemLogger.d(HaronConstants.TAG, "SmbManager: connected to $host")
                Result.success(Unit)
            } catch (e: Throwable) {
                EcosystemLogger.e(HaronConstants.TAG, "SmbManager: connect failed to $host: ${e.message}")
                Result.failure(e)
            }
        }

    suspend fun connectAsGuest(host: String, port: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                EcosystemLogger.d(HaronConstants.TAG, "SmbManager: connecting as guest to $host:$port")
                disconnect(host)
                val conn = smbClient.connect(host, port)
                // Use empty credentials (Guest) instead of anonymous() to avoid null session key
                val guestCtx = AuthenticationContext("Guest", "".toCharArray(), "")
                val session = conn.authenticate(guestCtx)
                connections[host] = SmbConnection(smbClient, conn, session, null)
                EcosystemLogger.d(HaronConstants.TAG, "SmbManager: guest connected to $host")
                Result.success(Unit)
            } catch (e: Throwable) {
                EcosystemLogger.e(HaronConstants.TAG, "SmbManager: guest connect failed to $host: ${e.message}")
                Result.failure(e)
            }
        }

    suspend fun listShares(host: String): Result<List<SmbShareInfo>> =
        withContext(Dispatchers.IO) {
            try {
                EcosystemLogger.d(HaronConstants.TAG, "SmbManager: listing shares on $host")
                val smbConn = getConnection(host)
                    ?: return@withContext Result.failure(IllegalStateException("Not connected"))
                smbConn.lastUsed = System.currentTimeMillis()

                // Open IPC$ share manually so we can close it after use (prevents connection leak)
                val ipcShare = smbConn.session.connectShare("IPC$")
                val filtered = try {
                    val pipeShare = ipcShare as? PipeShare
                        ?: return@withContext Result.failure(IllegalStateException("IPC$ is not a PipeShare"))
                    val namedPipe = NamedPipe(smbConn.session, pipeShare, "srvsvc")
                    val transport = SMBTransport(namedPipe)
                    transport.bind(Interface.SRVSVC_V3_0, Interface.NDR_32BIT_V2)
                    val serverService = ServerService(transport)
                    val shares: List<NetShareInfo0> = serverService.shares0
                    shares
                        .map { SmbShareInfo(name = it.netName, type = 0) }
                        .filter { !it.name.endsWith("$") }
                } finally {
                    try { ipcShare.close() } catch (_: Exception) {}
                }

                EcosystemLogger.d(HaronConstants.TAG, "SmbManager: found ${filtered.size} shares on $host")
                Result.success(filtered)
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "SmbManager: listShares failed on $host: ${e.message}")
                Result.failure(e)
            }
        }

    suspend fun listFiles(host: String, share: String, path: String): Result<List<SmbFileInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val diskShare = getOrOpenShare(host, share)
                    ?: return@withContext Result.failure(IllegalStateException("Not connected"))
                val dirPath = path.ifEmpty { "" }
                val entries: List<FileIdBothDirectoryInformation> = diskShare.list(dirPath)

                val files = entries
                    .filter { it.fileName != "." && it.fileName != ".." }
                    .map { entry ->
                        val isDir = entry.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
                        val fullPath = if (dirPath.isEmpty()) entry.fileName
                        else "$dirPath\\${entry.fileName}"
                        SmbFileInfo(
                            name = entry.fileName,
                            isDirectory = isDir,
                            size = entry.endOfFile,
                            lastModified = entry.lastWriteTime.toEpochMillis(),
                            path = fullPath
                        )
                    }
                    .sortedWith(compareByDescending<SmbFileInfo> { it.isDirectory }.thenBy { it.name.lowercase() })

                EcosystemLogger.d(HaronConstants.TAG, "SmbManager: listed ${files.size} files in $share/$dirPath")
                Result.success(files)
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "SmbManager: listFiles failed in $share/$path: ${e.message}")
                Result.failure(e)
            }
        }

    fun downloadFile(host: String, share: String, remotePath: String, localDest: File): Flow<SmbTransferProgress> = flow {
        EcosystemLogger.d(HaronConstants.TAG, "SmbManager: download started $share/$remotePath -> ${localDest.name}")
        val diskShare = getOrOpenShare(host, share)
            ?: throw IllegalStateException("Not connected")

        val fileHandle = diskShare.openFile(
            remotePath,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            null
        )

        val fileName = remotePath.substringAfterLast("\\").ifEmpty { remotePath }
        val totalSize = fileHandle.fileInformation.standardInformation.endOfFile

        fileHandle.inputStream.use { input ->
            val actualDest = resolveConflict(localDest)
            actualDest.outputStream().use { output ->
                val buffer = ByteArray(HaronConstants.TRANSFER_BUFFER_SIZE)
                var transferred = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    transferred += read
                    emit(SmbTransferProgress(fileName, transferred, totalSize, isUpload = false))
                }
            }
        }
        fileHandle.close()
        EcosystemLogger.d(HaronConstants.TAG, "SmbManager: download completed $fileName ($totalSize bytes)")
    }.flowOn(Dispatchers.IO)

    fun uploadFile(host: String, share: String, remotePath: String, localSrc: File): Flow<SmbTransferProgress> = flow {
        EcosystemLogger.d(HaronConstants.TAG, "SmbManager: upload started ${localSrc.name} -> $share/$remotePath")
        val diskShare = getOrOpenShare(host, share)
            ?: throw IllegalStateException("Not connected")

        val fileHandle = diskShare.openFile(
            remotePath,
            EnumSet.of(AccessMask.GENERIC_WRITE, AccessMask.FILE_WRITE_DATA),
            EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
            EnumSet.of(SMB2ShareAccess.FILE_SHARE_WRITE),
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
        )

        val totalSize = localSrc.length()
        val fileName = localSrc.name

        localSrc.inputStream().use { input ->
            fileHandle.outputStream.use { output ->
                val buffer = ByteArray(HaronConstants.TRANSFER_BUFFER_SIZE)
                var transferred = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    transferred += read
                    emit(SmbTransferProgress(fileName, transferred, totalSize, isUpload = true))
                }
            }
        }
        fileHandle.close()
        EcosystemLogger.d(HaronConstants.TAG, "SmbManager: upload completed $fileName ($totalSize bytes)")
    }.flowOn(Dispatchers.IO)

    suspend fun createDirectory(host: String, share: String, path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                EcosystemLogger.d(HaronConstants.TAG, "SmbManager: creating directory $share/$path")
                val diskShare = getOrOpenShare(host, share)
                    ?: return@withContext Result.failure(IllegalStateException("Not connected"))
                diskShare.mkdir(path)
                EcosystemLogger.d(HaronConstants.TAG, "SmbManager: directory created $share/$path")
                Result.success(Unit)
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "SmbManager: createDirectory failed $share/$path: ${e.message}")
                Result.failure(e)
            }
        }

    suspend fun delete(host: String, share: String, path: String, isDirectory: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                EcosystemLogger.d(HaronConstants.TAG, "SmbManager: deleting ${if (isDirectory) "dir" else "file"} $share/$path")
                val diskShare = getOrOpenShare(host, share)
                    ?: return@withContext Result.failure(IllegalStateException("Not connected"))
                if (isDirectory) {
                    diskShare.rmdir(path, true)
                } else {
                    diskShare.rm(path)
                }
                EcosystemLogger.d(HaronConstants.TAG, "SmbManager: deleted $share/$path")
                Result.success(Unit)
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "SmbManager: delete failed $share/$path: ${e.message}")
                Result.failure(e)
            }
        }

    suspend fun rename(host: String, share: String, oldPath: String, newName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                EcosystemLogger.d(HaronConstants.TAG, "SmbManager: renaming $share/$oldPath -> $newName")
                val diskShare = getOrOpenShare(host, share)
                    ?: return@withContext Result.failure(IllegalStateException("Not connected"))
                val parentDir = oldPath.substringBeforeLast("\\", "")
                val newPath = if (parentDir.isEmpty()) newName else "$parentDir\\$newName"

                val fileHandle = diskShare.openFile(
                    oldPath,
                    EnumSet.of(AccessMask.GENERIC_ALL, AccessMask.DELETE),
                    null,
                    EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE, SMB2ShareAccess.FILE_SHARE_DELETE),
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                fileHandle.rename(newPath)
                fileHandle.close()
                EcosystemLogger.d(HaronConstants.TAG, "SmbManager: renamed to $newName")
                Result.success(Unit)
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "SmbManager: rename failed $share/$oldPath: ${e.message}")
                Result.failure(e)
            }
        }

    fun disconnect(host: String) {
        val shareKeysToRemove = openShares.keys.filter { it.startsWith("$host/") }
        EcosystemLogger.d(HaronConstants.TAG, "SmbManager: disconnecting from $host, closing ${shareKeysToRemove.size} shares")
        shareKeysToRemove.forEach { key ->
            try { openShares.remove(key)?.close() } catch (_: Exception) {}
        }
        connections.remove(host)?.let { conn ->
            try { conn.session.close() } catch (_: Exception) {}
            try { conn.connection.close() } catch (_: Exception) {}
        }
        EcosystemLogger.d(HaronConstants.TAG, "SmbManager: disconnected from $host, remaining connections=${connections.size}, shares=${openShares.size}")
    }

    fun disconnectAll() {
        EcosystemLogger.d(HaronConstants.TAG, "SmbManager: disconnecting all (${connections.size} connections)")
        val hosts = connections.keys.toList()
        hosts.forEach { disconnect(it) }
    }

    fun isConnected(host: String): Boolean {
        val conn = connections[host] ?: return false
        return try {
            conn.connection.isConnected
        } catch (_: Exception) {
            connections.remove(host)
            false
        }
    }

    suspend fun autoReconnect(host: String, port: Int): Result<Unit> {
        EcosystemLogger.d(HaronConstants.TAG, "SmbManager: auto-reconnecting to $host:$port")
        val conn = connections[host]
        val savedCred = conn?.credential ?: credentialStore.load(host)
        return if (savedCred != null) {
            connect(host, port, savedCred)
        } else {
            connectAsGuest(host, port)
        }
    }

    private fun getConnection(host: String): SmbConnection? {
        val conn = connections[host] ?: return null
        if (!conn.connection.isConnected) {
            connections.remove(host)
            return null
        }
        return conn
    }

    private fun getOrOpenShare(host: String, shareName: String): DiskShare? {
        val key = "$host/$shareName"
        val existing = openShares[key]
        if (existing != null) {
            try {
                if (existing.isConnected) return existing
            } catch (_: Exception) { }
            // Close stale share before removing
            try { existing.close() } catch (_: Exception) { }
            openShares.remove(key)
        }
        val conn = getConnection(host) ?: return null
        return try {
            val share = conn.session.connectShare(shareName) as DiskShare
            openShares[key] = share
            EcosystemLogger.d(HaronConstants.TAG, "SmbManager: opened share $shareName on $host (total open: ${openShares.size})")
            share
        } catch (e: SMBRuntimeException) {
            EcosystemLogger.w(HaronConstants.TAG, "SmbManager: failed to open share $shareName: ${e.message}")
            null
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
