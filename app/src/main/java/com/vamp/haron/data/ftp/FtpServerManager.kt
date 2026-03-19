package com.vamp.haron.data.ftp

import android.content.Context
import android.os.Environment
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.transfer.HttpFileServer
import dagger.hilt.android.qualifiers.ApplicationContext
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.WritePermission
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission
import org.apache.ftpserver.usermanager.impl.TransferRatePermission
import org.apache.ftpserver.ConnectionConfigFactory
import org.apache.ftpserver.DataConnectionConfigurationFactory
import javax.inject.Inject
import javax.inject.Singleton

data class FtpServerConfig(
    val port: Int = HaronConstants.FTP_SERVER_DEFAULT_PORT,
    val anonymousAccess: Boolean = true,
    val username: String = "",
    val password: String = "",
    val readOnly: Boolean = false
)

@Singleton
class FtpServerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpFileServer: HttpFileServer
) {
    private var server: FtpServer? = null
    private var actualPort: Int = HaronConstants.FTP_SERVER_DEFAULT_PORT

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start(config: FtpServerConfig): Int {
        stop()
        EcosystemLogger.d(HaronConstants.TAG, "FtpServerManager: starting on port ${config.port}, anonymous=${config.anonymousAccess}")

        val serverFactory = FtpServerFactory()

        // Connection config
        val connConfigFactory = ConnectionConfigFactory()
        connConfigFactory.isAnonymousLoginEnabled = config.anonymousAccess
        connConfigFactory.maxLogins = 20
        connConfigFactory.maxAnonymousLogins = 20
        connConfigFactory.maxLoginFailures = 5
        connConfigFactory.loginFailureDelay = 0
        serverFactory.connectionConfig = connConfigFactory.createConnectionConfig()

        // Configure listener
        val listenerFactory = ListenerFactory()
        listenerFactory.isImplicitSsl = false
        listenerFactory.serverAddress = "0.0.0.0"
        val dataConnFactory = DataConnectionConfigurationFactory()
        dataConnFactory.passivePorts = "${HaronConstants.FTP_PASSIVE_PORT_START}-${HaronConstants.FTP_PASSIVE_PORT_END}"
        dataConnFactory.isPassiveIpCheck = false
        listenerFactory.dataConnectionConfiguration = dataConnFactory.createDataConnectionConfiguration()

        // Try ports
        var port = config.port
        var started = false
        for (attempt in 0..1) {
            try {
                listenerFactory.port = port
                serverFactory.addListener("default", listenerFactory.createListener())

                // Setup users
                setupUsers(serverFactory, config)

                // Add ftplet for logging
                serverFactory.ftplets = mapOf("haron" to HaronFtplet())

                server = serverFactory.createServer()
                server?.start()
                actualPort = port
                isRunning = true
                started = true
                EcosystemLogger.d(HaronConstants.TAG, "FtpServerManager: started on port $port")
                break
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "FtpServerManager: failed to start on port $port: ${e.message}")
                server = null
                if (attempt == 0) {
                    port = HaronConstants.FTP_SERVER_PORT_FALLBACK
                    // Need to create fresh factory for retry
                    val retryFactory = FtpServerFactory()
                    val retryListenerFactory = ListenerFactory()
                    retryListenerFactory.port = port
                    retryListenerFactory.isImplicitSsl = false
                    retryListenerFactory.serverAddress = "0.0.0.0"
                    val retryConnConfig = ConnectionConfigFactory()
                    retryConnConfig.isAnonymousLoginEnabled = config.anonymousAccess
                    retryConnConfig.maxLogins = 20
                    retryConnConfig.maxAnonymousLogins = 20
                    retryFactory.connectionConfig = retryConnConfig.createConnectionConfig()
                    val retryDataConnFactory = DataConnectionConfigurationFactory()
                    retryDataConnFactory.passivePorts = "${HaronConstants.FTP_PASSIVE_PORT_START}-${HaronConstants.FTP_PASSIVE_PORT_END}"
                    retryDataConnFactory.isPassiveIpCheck = false
                    retryListenerFactory.dataConnectionConfiguration = retryDataConnFactory.createDataConnectionConfiguration()
                    retryFactory.addListener("default", retryListenerFactory.createListener())
                    setupUsers(retryFactory, config)
                    retryFactory.ftplets = mapOf("haron" to HaronFtplet())
                    try {
                        val retryServer = retryFactory.createServer()
                        retryServer.start()
                        server = retryServer
                        actualPort = port
                        isRunning = true
                        started = true
                        EcosystemLogger.d(HaronConstants.TAG, "FtpServerManager: started on fallback port $port")
                    } catch (e2: Exception) {
                        EcosystemLogger.e(HaronConstants.TAG, "FtpServerManager: fallback port $port also failed: ${e2.message}")
                    }
                    break
                }
            }
        }

        return if (started) actualPort else -1
    }

    fun stop() {
        server?.let {
            EcosystemLogger.d(HaronConstants.TAG, "FtpServerManager: stopping server")
            try {
                it.stop()
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "FtpServerManager: stop error: ${e.message}")
            }
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

    private fun setupUsers(serverFactory: FtpServerFactory, config: FtpServerConfig) {
        val homeDir = Environment.getExternalStorageDirectory().absolutePath
        val userManagerFactory = PropertiesUserManagerFactory()
        val userManager = userManagerFactory.createUserManager()

        val authorities = mutableListOf<Authority>()
        if (!config.readOnly) {
            authorities.add(WritePermission())
        }
        authorities.add(ConcurrentLoginPermission(10, 10))
        authorities.add(TransferRatePermission(0, 0))

        if (config.anonymousAccess) {
            val anonUser = BaseUser()
            anonUser.name = "anonymous"
            anonUser.homeDirectory = homeDir
            anonUser.authorities = authorities
            anonUser.enabled = true
            userManager.save(anonUser)
        }

        if (config.username.isNotEmpty()) {
            val authUser = BaseUser()
            authUser.name = config.username
            authUser.password = config.password
            authUser.homeDirectory = homeDir
            authUser.authorities = authorities
            authUser.enabled = true
            userManager.save(authUser)
        }

        serverFactory.userManager = userManager
    }
}
