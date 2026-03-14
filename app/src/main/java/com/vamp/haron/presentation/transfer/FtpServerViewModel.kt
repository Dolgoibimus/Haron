package com.vamp.haron.presentation.transfer

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.ftp.FtpServerConfig
import com.vamp.haron.data.ftp.FtpServerManager
import com.vamp.haron.service.TransferService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FtpServerUiState(
    val isRunning: Boolean = false,
    val serverUrl: String? = null,
    val port: Int = HaronConstants.FTP_SERVER_DEFAULT_PORT,
    val anonymousAccess: Boolean = true,
    val username: String = "",
    val password: String = "",
    val readOnly: Boolean = false
)

@HiltViewModel
class FtpServerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val ftpServerManager: FtpServerManager
) : ViewModel() {

    private val _state = MutableStateFlow(FtpServerUiState())
    val state: StateFlow<FtpServerUiState> = _state.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastMessage = _toastMessage.asSharedFlow()

    init {
        _state.update { it.copy(isRunning = ftpServerManager.isRunning) }
        if (ftpServerManager.isRunning) {
            _state.update { it.copy(serverUrl = ftpServerManager.getServerUrl()) }
        }
    }

    fun startServer() {
        val s = _state.value
        EcosystemLogger.d(HaronConstants.TAG, "FtpServerVM: starting server on port ${s.port}")
        viewModelScope.launch {
            val config = FtpServerConfig(
                port = s.port,
                anonymousAccess = s.anonymousAccess,
                username = s.username,
                password = s.password,
                readOnly = s.readOnly
            )

            // Start actual FTP server
            val actualPort = ftpServerManager.start(config)
            if (actualPort < 0) {
                _toastMessage.tryEmit("Failed to start FTP server")
                return@launch
            }

            // Start TransferService for foreground notification + wake lock
            val intent = Intent(appContext, TransferService::class.java).apply {
                action = TransferService.ACTION_START_FTP_SERVER
                putExtra("ftp_port", actualPort)
                putExtra("ftp_anonymous", config.anonymousAccess)
                putExtra("ftp_username", config.username)
                putExtra("ftp_password", config.password)
                putExtra("ftp_read_only", config.readOnly)
            }
            appContext.startForegroundService(intent)

            _state.update {
                it.copy(
                    isRunning = true,
                    serverUrl = ftpServerManager.getServerUrl()
                )
            }
        }
    }

    fun stopServer() {
        EcosystemLogger.d(HaronConstants.TAG, "FtpServerVM: stopping server")
        ftpServerManager.stop()

        val intent = Intent(appContext, TransferService::class.java).apply {
            action = TransferService.ACTION_STOP_FTP_SERVER
        }
        appContext.startService(intent)

        _state.update { it.copy(isRunning = false, serverUrl = null) }
    }

    fun setPort(port: Int) {
        _state.update { it.copy(port = port) }
    }

    fun setAnonymousAccess(enabled: Boolean) {
        _state.update { it.copy(anonymousAccess = enabled) }
    }

    fun setUsername(username: String) {
        _state.update { it.copy(username = username) }
    }

    fun setPassword(password: String) {
        _state.update { it.copy(password = password) }
    }

    fun setReadOnly(readOnly: Boolean) {
        _state.update { it.copy(readOnly = readOnly) }
    }
}
