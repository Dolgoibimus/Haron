package com.vamp.haron.data.ftp

import android.content.Context
import android.os.Environment
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.transfer.HttpFileServer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class FtpServerConfig(
    val port: Int = HaronConstants.FTP_SERVER_DEFAULT_PORT,
    val anonymousAccess: Boolean = true,
    val username: String = "",
    val password: String = "",
    val readOnly: Boolean = false
)

/**
 * Embedded FTP server for receiving files from other devices on the local network.
 * Built on SimpleFtpServer (pure socket, RFC 959), supports anonymous and authenticated access,
 * configurable read-only mode, and passive data connections.
 * Serves files from external storage root directory.
 */
@Singleton
class FtpServerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpFileServer: HttpFileServer
) {
    private var server: SimpleFtpServer? = null
    private var actualPort: Int = HaronConstants.FTP_SERVER_DEFAULT_PORT

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start(config: FtpServerConfig): Int {
        stop()
        EcosystemLogger.d(HaronConstants.TAG, "FtpServerManager: starting on port ${config.port}, anonymous=${config.anonymousAccess}")

        val homeDir = Environment.getExternalStorageDirectory().absolutePath
        val ftpServer = SimpleFtpServer(homeDir, config)

        ftpServer.onConnect = { addr ->
            EcosystemLogger.i(HaronConstants.TAG, "FTP: connect from $addr")
        }
        ftpServer.onLogin = { user, addr ->
            EcosystemLogger.i(HaronConstants.TAG, "FTP: login user=$user from $addr")
        }
        ftpServer.onUpload = { user, file ->
            EcosystemLogger.i(HaronConstants.TAG, "FTP: upload complete user=$user file=$file")
        }
        ftpServer.onDownload = { user, file ->
            EcosystemLogger.i(HaronConstants.TAG, "FTP: download complete user=$user file=$file")
        }
        ftpServer.onDisconnect = { addr ->
            EcosystemLogger.i(HaronConstants.TAG, "FTP: disconnect from $addr")
        }

        val port = ftpServer.start(config.port)
        if (port > 0) {
            server = ftpServer
            actualPort = port
            isRunning = true
            EcosystemLogger.d(HaronConstants.TAG, "FtpServerManager: started on port $port")
        } else {
            EcosystemLogger.e(HaronConstants.TAG, "FtpServerManager: failed to start")
        }
        return port
    }

    fun stop() {
        server?.let {
            EcosystemLogger.d(HaronConstants.TAG, "FtpServerManager: stopping server")
            it.stop()
            server = null
            isRunning = false
        }
    }

    fun getServerUrl(): String? {
        if (!isRunning) return null
        val ip = httpFileServer.getLocalIpAddress() ?: return null
        return "ftp://$ip:$actualPort"
    }

    fun getPort(): Int = actualPort
}
