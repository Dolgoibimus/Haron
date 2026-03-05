package com.vamp.haron.data.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.storage.StorageManager
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.data.saf.StorageVolumeHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UsbStorageManager"

data class UsbVolume(
    val path: String,
    val label: String,
    val totalSpace: Long,
    val freeSpace: Long,
    /** Volume is detected but has no accessible file path — needs SAF to browse */
    val needsSaf: Boolean = false,
    val uuid: String? = null,
    /** Detected file system type: "FAT32", "NTFS", "unknown", null = not probed */
    val fileSystemType: String? = null,
    /** true → UI shows unsupported FS warning (NTFS etc.) */
    val unsupportedFs: Boolean = false
)

@Singleton
class UsbStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageVolumeHelper: StorageVolumeHelper,
    private val libaumsManager: LibaumsManager
) {
    private val _usbVolumes = MutableStateFlow<List<UsbVolume>>(emptyList())
    val usbVolumes: StateFlow<List<UsbVolume>> = _usbVolumes.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private var retryJob: Job? = null
    private var pollJob: Job? = null

    /** Paths received from MEDIA_MOUNTED broadcasts — used as fallback hints */
    private val broadcastMountedPaths = mutableSetOf<String>()

    /** Paths suppressed after removal — don't re-add until MEDIA_MOUNTED clears them */
    private val suppressedPaths = mutableSetOf<String>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val path = intent.data?.path
            EcosystemLogger.d(TAG, "Media broadcast: action=${intent.action}, path=$path")
            when (intent.action) {
                Intent.ACTION_MEDIA_MOUNTED -> {
                    if (path != null && path != "/dev/null") {
                        broadcastMountedPaths.add(path)
                        suppressedPaths.remove(path)
                        // Also un-suppress the UUID extracted from path
                        val mountedUuid = path.substringAfterLast("/")
                        if (mountedUuid.isNotEmpty()) suppressedPaths.remove(mountedUuid)
                    }
                    scheduleRetry()
                }
                Intent.ACTION_MEDIA_UNMOUNTED,
                Intent.ACTION_MEDIA_EJECT,
                Intent.ACTION_MEDIA_BAD_REMOVAL,
                Intent.ACTION_MEDIA_REMOVED -> {
                    if (path != null) {
                        broadcastMountedPaths.remove(path)
                        suppressedPaths.add(path)
                        // Extract UUID from broadcast path (e.g., /storage/0633-7530 → 0633-7530)
                        val broadcastUuid = path.substringAfterLast("/")
                        if (broadcastUuid.isNotEmpty()) suppressedPaths.add(broadcastUuid)
                        // Instantly remove volume from list (by path or by UUID for SAF-only volumes)
                        val current = _usbVolumes.value
                        val filtered = current.filter { vol ->
                            if (vol.unsupportedFs) {
                                // Unsupported FS volumes: always remove on media event
                                false
                            } else if (vol.needsSaf) {
                                // SAF-only volumes: match by UUID from broadcast path
                                vol.uuid != broadcastUuid
                            } else {
                                !vol.path.startsWith(path) && !path.startsWith(vol.path)
                            }
                        }
                        if (filtered.size != current.size) {
                            _usbVolumes.value = filtered
                            EcosystemLogger.d(TAG, "Instant remove: $path (${current.size}→${filtered.size})")
                        }
                    }
                    scheduleRefresh()
                }
            }
        }
    }

    private var registered = false

    fun register() {
        if (registered) return
        EcosystemLogger.d(TAG, "register() (API ${Build.VERSION.SDK_INT})")
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addAction(Intent.ACTION_MEDIA_CHECKING)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addDataScheme("file")
        }

        val usbFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            context.registerReceiver(usbHwReceiver, usbFilter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
            context.registerReceiver(usbHwReceiver, usbFilter)
        }

        registered = true
        scheduleRefresh()

        // Periodic poll every 10s as fallback
        pollJob = scope.launch {
            while (registered) {
                delay(10_000)
                doRefresh()
            }
        }
    }

    private val usbHwReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val device = intent.getParcelableExtra<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE)
            EcosystemLogger.d(TAG, "USB HW: action=${intent.action}, device=${device?.deviceName}, vendor=${device?.vendorId}, product=${device?.productId}")
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> scheduleRetry()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    // Suppress all current USB volume paths/UUIDs and clear list
                    val current = _usbVolumes.value
                    if (current.isNotEmpty()) {
                        current.forEach {
                            if (it.path.isNotEmpty()) suppressedPaths.add(it.path)
                            if (it.uuid != null) suppressedPaths.add(it.uuid)
                        }
                        _usbVolumes.value = emptyList()
                        EcosystemLogger.d(TAG, "USB HW detached → cleared all volumes")
                    }
                    libaumsManager.clearProbeResults()
                    scheduleRefresh()
                }
            }
        }
    }

    fun unregister() {
        if (!registered) return
        try {
            context.unregisterReceiver(receiver)
            context.unregisterReceiver(usbHwReceiver)
        } catch (_: Exception) { }
        registered = false
        retryJob?.cancel()
        pollJob?.cancel()
        libaumsManager.clearProbeResults()
    }

    /** Single refresh, debounced via Mutex */
    private fun scheduleRefresh() {
        scope.launch { doRefresh() }
    }

    /** Cancel any pending retry, start new retry sequence */
    private fun scheduleRetry() {
        retryJob?.cancel()
        retryJob = scope.launch {
            EcosystemLogger.d(TAG, "Retry sequence start (broadcastPaths=$broadcastMountedPaths)")
            doRefresh()
            // Extended retry: some devices (Sony API 28) take up to 10+ seconds to mount USB
            val delays = longArrayOf(1000, 2000, 4000, 8000, 12000)
            for ((i, d) in delays.withIndex()) {
                delay(d)
                val before = _usbVolumes.value.size
                val beforeSafCount = _usbVolumes.value.count { it.needsSaf }
                doRefresh()
                val after = _usbVolumes.value.size
                val afterSafCount = _usbVolumes.value.count { it.needsSaf }
                EcosystemLogger.d(TAG, "Retry #${i + 1} (${d}ms): $before→$after (saf:$beforeSafCount→$afterSafCount)")
                // Stop if we found new volumes with actual file paths (not SAF-only)
                if (after > before && afterSafCount <= beforeSafCount) break
            }

            // After all retries: if there are still physical USB devices with no mounted volumes,
            // probe via libaums to detect unsupported FS (NTFS).
            // Done AFTER retries to avoid false positives while kernel is still mounting.
            probeUnmountedDevices()
        }
    }

    /**
     * Probe USB Mass Storage devices that Android completely failed to mount.
     * Only called after ALL retries are done AND zero volumes were found.
     * If Android mounted anything (even SAF-only), we skip libaums probe entirely —
     * this avoids false NTFS detection and unwanted USB permission dialogs.
     */
    private suspend fun probeUnmountedDevices() {
        val currentVolumes = _usbVolumes.value
        if (currentVolumes.any { !it.unsupportedFs }) {
            // Android found at least one volume (normal or SAF) — no need to probe
            EcosystemLogger.d(TAG, "Skip libaums probe: ${currentVolumes.size} volume(s) already found")
            return
        }

        // Double-check: are there physical USB Mass Storage devices connected?
        if (!libaumsManager.hasPhysicalDevices()) return

        EcosystemLogger.d(TAG, "No volumes after retries but USB device present — probing via libaums...")
        try {
            val probeResults = libaumsManager.probeAllDevices()
            val newVolumes = mutableListOf<UsbVolume>()
            for (probe in probeResults) {
                if (!probe.supported) {
                    newVolumes.add(UsbVolume(
                        path = "",
                        label = probe.label,
                        totalSpace = probe.totalSpace,
                        freeSpace = probe.freeSpace,
                        fileSystemType = probe.fileSystemType,
                        unsupportedFs = true
                    ))
                }
            }
            if (newVolumes.isNotEmpty()) {
                _usbVolumes.value = newVolumes
                EcosystemLogger.d(TAG, "Added ${newVolumes.size} unsupported-FS volume(s)")
            }
        } catch (e: Exception) {
            EcosystemLogger.w(TAG, "libaums probe failed: ${e.message}")
        }
    }

    /** Thread-safe refresh — only one call at a time */
    private suspend fun doRefresh() {
        if (!refreshMutex.tryLock()) return // skip if already refreshing
        try {
            val removable = storageVolumeHelper.getRemovableVolumes()
            val seen = mutableSetOf<String>()
            val seenLabels = mutableSetOf<String>()
            val volumes = mutableListOf<UsbVolume>()

            for (vol in removable) {
                // Skip SD cards — they are shown in SAF roots section, not USB
                if (isLikelySdCard(vol.label)) {
                    EcosystemLogger.d(TAG, "  SKIP ${vol.label}: SD card (shown in SAF roots)")
                    continue
                }

                // If StorageVolumeHelper couldn't resolve path, try broadcast hint
                val resolvedPath = vol.path
                    ?: broadcastMountedPaths.firstOrNull { bp ->
                        vol.uuid != null && bp.contains(vol.uuid, ignoreCase = true)
                    }?.also {
                        EcosystemLogger.d(TAG, "  HINT ${vol.label}: broadcast path=$it")
                    }

                // Validate the resolved path
                val validPath = resolvedPath?.let { path ->
                    if (path in suppressedPaths) {
                        EcosystemLogger.d(TAG, "  SKIP ${vol.label}: suppressed (recently removed)")
                        return@let null
                    }
                    val dir = File(path)
                    if (!dir.exists() || !dir.canRead()) {
                        EcosystemLogger.d(TAG, "  SKIP ${vol.label}: $path exists=${dir.exists()}, canRead=${dir.canRead()}")
                        return@let null
                    }
                    path
                }

                if (validPath != null) {
                    if (!seen.add(validPath)) continue
                    seenLabels.add(vol.label)
                    val dir = File(validPath)
                    EcosystemLogger.d(TAG, "  OK ${vol.label}: $validPath (${dir.totalSpace / 1_000_000}MB)")
                    volumes.add(UsbVolume(
                        path = validPath,
                        label = vol.label,
                        totalSpace = dir.totalSpace,
                        freeSpace = dir.freeSpace,
                        uuid = vol.uuid
                    ))
                } else if (vol.uuid != null && vol.uuid !in suppressedPaths) {
                    // Volume detected by StorageManager but no accessible path — show as SAF-only
                    // This happens on some devices (Sony API 28) where USB OTG paths aren't directly accessible
                    val key = "saf:${vol.uuid}"
                    if (seen.add(key)) {
                        seenLabels.add(vol.label)
                        EcosystemLogger.d(TAG, "  SAF-ONLY ${vol.label}: uuid=${vol.uuid}, state=${vol.needsSaf}")
                        volumes.add(UsbVolume(
                            path = "",
                            label = vol.label,
                            totalSpace = 0L,
                            freeSpace = 0L,
                            needsSaf = true,
                            uuid = vol.uuid
                        ))
                    }
                } else {
                    EcosystemLogger.d(TAG, "  SKIP ${vol.label}: path=null, uuid=null")
                }
            }

            // Fallback: scan /storage/ and /mnt/media_rw/ for volumes not found by StorageManager
            val scanned = storageVolumeHelper.scanStorageDirectory()
            for (vol in scanned) {
                val path = vol.path ?: continue
                if (!seen.add(path)) continue
                if (isLikelySdCard(vol.label)) continue
                val dir = File(path)
                EcosystemLogger.d(TAG, "  OK (scan) ${vol.label}: $path (${dir.totalSpace / 1_000_000}MB)")
                volumes.add(UsbVolume(path = path, label = vol.label, totalSpace = dir.totalSpace, freeSpace = dir.freeSpace, uuid = vol.uuid))
            }

            _usbVolumes.value = volumes
            if (volumes.isNotEmpty() || removable.isNotEmpty() || scanned.isNotEmpty()) {
                EcosystemLogger.d(TAG, "Result: ${volumes.size} volume(s) (saf-only: ${volumes.count { it.needsSaf }}, unsupported-fs: ${volumes.count { it.unsupportedFs }})")
            }
        } finally {
            refreshMutex.unlock()
        }
    }

    /** Non-suspend wrapper for external callers */
    fun refreshVolumes() {
        scope.launch { doRefresh() }
    }

    fun safeEject(usbPath: String): Boolean {
        try {
            Runtime.getRuntime().exec("sync").waitFor()
        } catch (_: Exception) { }

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
                    EcosystemLogger.d(TAG, "Unmounted: $id")
                    _usbVolumes.value = _usbVolumes.value.filter { it.path != usbPath }
                    return true
                }
            }
            false
        } catch (e: Exception) {
            EcosystemLogger.w(TAG, "Unmount failed: ${e.message}")
            _usbVolumes.value = _usbVolumes.value.filter { it.path != usbPath }
            false
        }
    }

    fun isUsbPath(path: String): Boolean {
        return _usbVolumes.value.any { !it.needsSaf && it.path.isNotEmpty() && path.startsWith(it.path) }
    }

    fun getVolumeForPath(path: String): UsbVolume? {
        return _usbVolumes.value.firstOrNull { !it.needsSaf && it.path.isNotEmpty() && path.startsWith(it.path) }
    }

    /**
     * SD cards have "SD" as a standalone word in their label on all locales:
     * "SD-карта", "SD card", "SDカード", "carte SD", etc.
     * USB drives: "USB storage", "USB-накопитель", "Ventoy", "SanDisk", "SMBB".
     * Must NOT match brands like "SanDisk" where "sd" is inside a word.
     */
    private fun isLikelySdCard(label: String): Boolean {
        // "SD" must be at word boundary: start/space before, non-letter/end after
        return SD_CARD_PATTERN.containsMatchIn(label)
    }

    companion object {
        private val SD_CARD_PATTERN = Regex("(^|\\s)SD(\\s|[^a-zA-Z]|$)", RegexOption.IGNORE_CASE)
    }
}
