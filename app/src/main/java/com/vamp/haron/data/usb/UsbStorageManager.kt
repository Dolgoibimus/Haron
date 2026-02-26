package com.vamp.haron.data.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.storage.StorageManager
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.data.saf.StorageVolumeHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class UsbVolume(
    val path: String,
    val label: String,
    val totalSpace: Long,
    val freeSpace: Long
)

@Singleton
class UsbStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageVolumeHelper: StorageVolumeHelper
) {
    private val _usbVolumes = MutableStateFlow<List<UsbVolume>>(emptyList())
    val usbVolumes: StateFlow<List<UsbVolume>> = _usbVolumes.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val path = intent.data?.path
            EcosystemLogger.d("UsbStorageManager", "Broadcast: ${intent.action}, path=$path")
            refreshVolumes()
        }
    }

    private var registered = false

    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        context.registerReceiver(receiver, filter)
        registered = true
        refreshVolumes()
    }

    fun unregister() {
        if (!registered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) { /* already unregistered */ }
        registered = false
    }

    fun refreshVolumes() {
        val removable = storageVolumeHelper.getRemovableVolumes()
        val seen = mutableSetOf<String>()
        val volumes = removable.mapNotNull { vol ->
            val path = vol.path ?: return@mapNotNull null
            if (!seen.add(path)) return@mapNotNull null // skip duplicates
            val dir = File(path)
            if (!dir.exists()) return@mapNotNull null
            UsbVolume(
                path = path,
                label = vol.label,
                totalSpace = dir.totalSpace,
                freeSpace = dir.freeSpace
            )
        }
        _usbVolumes.value = volumes
    }

    /**
     * Attempt to safely eject a USB volume.
     * 1. Flush file buffers via sync
     * 2. Try programmatic unmount via StorageManager hidden API
     * Returns true if unmount succeeded, false if only sync was performed.
     */
    fun safeEject(usbPath: String): Boolean {
        // 1. Sync file buffers
        try {
            Runtime.getRuntime().exec("sync").waitFor()
        } catch (_: Exception) { /* ignore */ }

        // 2. Try unmount via reflection
        return try {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val getVolumes = sm.javaClass.getMethod("getVolumes")
            @Suppress("UNCHECKED_CAST")
            val volumes = getVolumes.invoke(sm) as List<*>
            for (vol in volumes) {
                if (vol == null) continue
                val pathMethod = vol.javaClass.getMethod("getPath")
                val volPath = (pathMethod.invoke(vol) as? File)?.absolutePath ?: continue
                if (volPath == usbPath) {
                    val id = vol.javaClass.getField("id").get(vol) as String
                    sm.javaClass.getMethod("unmount", String::class.java).invoke(sm, id)
                    EcosystemLogger.d("UsbStorageManager", "Unmounted: $id")
                    // Remove from list
                    _usbVolumes.value = _usbVolumes.value.filter { it.path != usbPath }
                    return true
                }
            }
            false
        } catch (e: Exception) {
            EcosystemLogger.w("UsbStorageManager", "Unmount failed: ${e.message}")
            // At least sync was done
            _usbVolumes.value = _usbVolumes.value.filter { it.path != usbPath }
            false
        }
    }

    /** Check if a given path is on a USB volume */
    fun isUsbPath(path: String): Boolean {
        return _usbVolumes.value.any { path.startsWith(it.path) }
    }

    /** Get the USB volume for a path, or null */
    fun getVolumeForPath(path: String): UsbVolume? {
        return _usbVolumes.value.firstOrNull { path.startsWith(it.path) }
    }
}
