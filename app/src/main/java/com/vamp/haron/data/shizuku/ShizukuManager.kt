package com.vamp.haron.data.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.FileEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

enum class ShizukuState {
    NOT_INSTALLED,
    NOT_RUNNING,
    NO_PERMISSION,
    READY,
    BOUND
}

@Singleton
class ShizukuManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow(ShizukuState.NOT_INSTALLED)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private var service: IShizukuFileService? = null
    private var isBound = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        EcosystemLogger.d(HaronConstants.TAG, "ShizukuManager: binder received")
        refreshState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        EcosystemLogger.d(HaronConstants.TAG, "ShizukuManager: binder dead")
        service = null
        isBound = false
        _state.value = ShizukuState.NOT_RUNNING
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            EcosystemLogger.d(
                HaronConstants.TAG,
                "ShizukuManager: permission result code=$requestCode, granted=${grantResult == PackageManager.PERMISSION_GRANTED}"
            )
            refreshState()
        }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                service = IShizukuFileService.Stub.asInterface(binder)
                isBound = true
                _state.value = ShizukuState.BOUND
                EcosystemLogger.d(HaronConstants.TAG, "ShizukuManager: service connected")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
            EcosystemLogger.d(HaronConstants.TAG, "ShizukuManager: service disconnected")
            refreshState()
        }
    }

    fun init() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        refreshState()
    }

    fun cleanup() {
        try {
            if (isBound) {
                Shizuku.unbindUserService(serviceArgs(), serviceConnection, true)
            }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "ShizukuManager: cleanup error: ${e.message}")
        }
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (_: Exception) {}
        service = null
        isBound = false
    }

    fun refreshState() {
        val newState = computeState()
        _state.value = newState
        EcosystemLogger.d(HaronConstants.TAG, "ShizukuManager: state=$newState")
    }

    private fun computeState(): ShizukuState {
        if (isBound && service != null) return ShizukuState.BOUND
        if (!isShizukuInstalled()) return ShizukuState.NOT_INSTALLED
        return try {
            if (!Shizuku.pingBinder()) return ShizukuState.NOT_RUNNING
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return ShizukuState.NO_PERMISSION
            }
            ShizukuState.READY
        } catch (_: Exception) {
            ShizukuState.NOT_RUNNING
        }
    }

    private fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun requestPermission(requestCode: Int = 1001) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "ShizukuManager: requestPermission error: ${e.message}")
        }
    }

    fun bindService() {
        if (isBound && service != null) return
        try {
            Shizuku.bindUserService(serviceArgs(), serviceConnection)
            EcosystemLogger.d(HaronConstants.TAG, "ShizukuManager: bindUserService called")
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "ShizukuManager: bindService error: ${e.message}")
        }
    }

    /**
     * Bind service and wait up to [timeoutMs] for connection.
     * @return true if service is bound and ready
     */
    suspend fun ensureServiceBound(timeoutMs: Long = 3000): Boolean {
        if (isBound && service != null) return true
        if (computeState() != ShizukuState.READY && computeState() != ShizukuState.BOUND) return false
        bindService()
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (isBound && service != null) return true
            kotlinx.coroutines.delay(50)
        }
        EcosystemLogger.e(HaronConstants.TAG, "ShizukuManager: ensureServiceBound timeout after ${timeoutMs}ms")
        return false
    }

    /**
     * List files at [path] via Shizuku IPC.
     * @return list of FileEntry, or null if service unavailable
     */
    fun listFiles(path: String): List<FileEntry>? {
        val svc = service ?: return null
        return try {
            svc.listFiles(path).map { it.toFileEntry() }
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "ShizukuManager: listFiles($path) error: ${e.message}")
            null
        }
    }

    private fun serviceArgs(): Shizuku.UserServiceArgs {
        return Shizuku.UserServiceArgs(
            ComponentName(
                context.packageName,
                ShizukuFileService::class.java.name
            )
        )
            .daemon(false)
            .processNameSuffix("shizuku_file")
            .debuggable(false)
            .version(1)
    }
}
