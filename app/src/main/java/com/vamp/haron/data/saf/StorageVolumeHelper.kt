package com.vamp.haron.data.saf

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.content.ContextCompat
import com.vamp.core.logger.EcosystemLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StorageVolumeHelper"

data class StorageVolumeInfo(
    val label: String,
    val path: String?,
    val uuid: String?,
    val isRemovable: Boolean,
    val needsSaf: Boolean,
    val state: String? = null
)

@Singleton
class StorageVolumeHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Cache to avoid spamming logs on repeated calls */
    private var cachedVolumes: List<StorageVolumeInfo>? = null
    private var cacheTimestamp = 0L

    /** Invalidate cache — call on USB attach/detach */
    fun invalidateCache() {
        cachedVolumes = null
        cacheTimestamp = 0L
    }

    fun getStorageVolumes(): List<StorageVolumeInfo> {
        val now = System.currentTimeMillis()
        cachedVolumes?.let { cached ->
            if (now - cacheTimestamp < CACHE_TTL_MS) return cached
        }

        val volumes = mutableListOf<StorageVolumeInfo>()
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageVolumes = storageManager.storageVolumes

        for (volume in storageVolumes) {
            val label = volume.getDescription(context) ?: "Storage"
            val uuid = volume.uuid
            val isRemovable = volume.isRemovable

            val path = getVolumePath(volume, uuid, isRemovable)
            val needsSaf = if (path != null) !File(path).canWrite() else isRemovable

            if (path == null) {
                EcosystemLogger.d(TAG, "Volume path=null: label=$label, uuid=$uuid, removable=$isRemovable, state=${volume.state}")
            }

            val state = volume.state
            EcosystemLogger.d(TAG, "StorageVolume: label=$label, uuid=$uuid, removable=$isRemovable, state=$state, path=$path, needsSaf=$needsSaf")
            volumes.add(
                StorageVolumeInfo(
                    label = label,
                    path = path,
                    uuid = uuid,
                    isRemovable = isRemovable,
                    needsSaf = needsSaf,
                    state = state
                )
            )
        }
        cachedVolumes = volumes
        cacheTimestamp = now
        return volumes
    }

    fun getRemovableVolumes(): List<StorageVolumeInfo> {
        return getStorageVolumes().filter { it.isRemovable }
    }

    /**
     * Try multiple strategies to resolve volume path:
     * 1. /storage/{uuid} direct check (most reliable for removable with uuid)
     * 2. StorageVolume.getDirectory() (API 30+)
     * 3. /mnt/media_rw/{uuid} — raw mount point before FUSE (some older devices)
     * 4. Reflection: StorageVolume.getPath() — but only if path matches uuid (skip on Sony where it returns wrong path)
     * 5. getExternalFilesDirs fallback — match by uuid in path
     * 6. Scan known alternative mount points
     */
    private fun getVolumePath(volume: StorageVolume, uuid: String?, isRemovable: Boolean): String? {
        val volLabel = volume.getDescription(context) ?: "?"
        val volState = volume.state

        // Strategy 1: /storage/{uuid}
        if (uuid != null) {
            val direct = File("/storage/$uuid")
            val exists = direct.exists()
            val canRead = exists && direct.canRead()
            val files = if (canRead) direct.listFiles()?.size ?: -1 else -1
            EcosystemLogger.d(TAG, "  [$volLabel] S1 /storage/$uuid: exists=$exists, canRead=$canRead, files=$files")
            if (exists && canRead) return direct.absolutePath
        }

        // Strategy 2: getDirectory() — API 30+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val dir = volume.directory
                val exists = dir?.exists() == true
                val canRead = exists && dir!!.canRead()
                EcosystemLogger.d(TAG, "  [$volLabel] S2 getDirectory(): dir=${dir?.absolutePath}, exists=$exists, canRead=$canRead")
                if (dir != null && exists) return dir.absolutePath
            } catch (e: Exception) {
                EcosystemLogger.d(TAG, "  [$volLabel] S2 getDirectory() exception: ${e.message}")
            }
        }

        // Strategy 3: /mnt/media_rw/{uuid}
        if (uuid != null) {
            val mntPath = File("/mnt/media_rw/$uuid")
            val exists = mntPath.exists()
            val canRead = exists && mntPath.canRead()
            EcosystemLogger.d(TAG, "  [$volLabel] S3 /mnt/media_rw/$uuid: exists=$exists, canRead=$canRead")
            if (exists && canRead) return mntPath.absolutePath
        }

        // Strategy 4: reflection getPath()
        try {
            val getPathMethod = volume.javaClass.getMethod("getPath")
            val path = getPathMethod.invoke(volume) as? String
            val exists = path != null && File(path).exists()
            val pathMatchesUuid = uuid == null || (path != null && path.contains(uuid, ignoreCase = true))
            EcosystemLogger.d(TAG, "  [$volLabel] S4 reflection getPath(): path=$path, exists=$exists, matchesUuid=$pathMatchesUuid")
            if (path != null && exists && pathMatchesUuid) return path
        } catch (e: Exception) {
            EcosystemLogger.d(TAG, "  [$volLabel] S4 reflection failed: ${e.message}")
        }

        // Strategy 5: getExternalFilesDirs fallback
        val dirs = ContextCompat.getExternalFilesDirs(context, null)
        EcosystemLogger.d(TAG, "  [$volLabel] S5 externalFilesDirs: ${dirs.map { it?.absolutePath }}")
        if (!isRemovable) {
            return dirs.firstOrNull()?.let { extractRootPath(it) }
        }
        for (dir in dirs.drop(1)) {
            if (dir == null) continue
            val root = extractRootPath(dir)
            if (root != null) {
                if (uuid != null && !root.contains(uuid, ignoreCase = true)) continue
                EcosystemLogger.d(TAG, "  [$volLabel] S5 matched: $root")
                return root
            }
        }

        // Strategy 6: scan alternative mount points
        if (uuid != null) {
            for (prefix in ALTERNATIVE_MOUNT_PREFIXES) {
                val alt = File("$prefix/$uuid")
                if (alt.exists() && alt.canRead()) {
                    EcosystemLogger.d(TAG, "  [$volLabel] S6 alt mount: ${alt.absolutePath}")
                    return alt.absolutePath
                }
            }
            EcosystemLogger.d(TAG, "  [$volLabel] S6 no alt mounts found for uuid=$uuid")
        }

        // All strategies failed — log diagnostic info
        EcosystemLogger.d(TAG, "  [$volLabel] ALL STRATEGIES FAILED: uuid=$uuid, state=$volState, removable=$isRemovable")
        logStorageDirectoryContents(uuid)
        return null
    }

    /** Log contents of /storage/ and /mnt/ for diagnostics when path resolution fails */
    private fun logStorageDirectoryContents(uuid: String?) {
        try {
            val storageDir = File("/storage")
            val storageListing = storageDir.listFiles()?.map { "${it.name}(${if (it.isDirectory) "dir" else "file"},r=${it.canRead()})" }
            EcosystemLogger.d(TAG, "  [DIAG] /storage/ contents: $storageListing")

            val mntDir = File("/mnt/media_rw")
            if (mntDir.exists()) {
                val mntListing = mntDir.listFiles()?.map { "${it.name}(${if (it.isDirectory) "dir" else "file"},r=${it.canRead()})" }
                EcosystemLogger.d(TAG, "  [DIAG] /mnt/media_rw/ contents: $mntListing")
            }

            if (uuid != null) {
                // Check all possible UUID locations
                val checks = listOf(
                    "/storage/$uuid", "/mnt/media_rw/$uuid", "/mnt/usb/$uuid",
                    "/storage/usbotg", "/mnt/usb_storage", "/mnt/sdcard"
                )
                for (check in checks) {
                    val f = File(check)
                    if (f.exists()) {
                        EcosystemLogger.d(TAG, "  [DIAG] EXISTS: $check (dir=${f.isDirectory}, r=${f.canRead()}, w=${f.canWrite()})")
                    }
                }
            }
        } catch (e: Exception) {
            EcosystemLogger.d(TAG, "  [DIAG] error: ${e.message}")
        }
    }

    /**
     * Scan /storage/ and /mnt/media_rw/ for removable volumes not reported by StorageManager.
     * Looks for UUID-pattern directories (XXXX-XXXX) that are readable.
     */
    fun scanStorageDirectory(): List<StorageVolumeInfo> {
        val knownVolumes = getStorageVolumes().mapNotNull { it.path }.toSet()
        val knownUuids = getStorageVolumes().mapNotNull { it.uuid }.toSet()
        val uuidPattern = Regex("^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$|^[0-9A-Fa-f]{8,}$")
        val extra = mutableListOf<StorageVolumeInfo>()
        val seenUuids = mutableSetOf<String>()

        val dirsToScan = listOf(File("/storage"), File("/mnt/media_rw"))

        for (scanRoot in dirsToScan) {
            if (!scanRoot.exists() || !scanRoot.canRead()) continue
            scanRoot.listFiles()?.forEach { dir ->
                if (!dir.isDirectory || !dir.canRead()) return@forEach
                if (dir.absolutePath in knownVolumes) return@forEach
                // Skip internal storage and self
                if (dir.name == "emulated" || dir.name == "self") return@forEach
                // Check if name matches UUID pattern
                if (!uuidPattern.matches(dir.name)) return@forEach
                // Skip if this UUID is already known through StorageManager
                if (dir.name in knownUuids) return@forEach
                // Avoid duplicates between /storage/ and /mnt/
                if (!seenUuids.add(dir.name)) return@forEach

                EcosystemLogger.d(TAG, "  [strategy:scan] found ${dir.absolutePath}")
                extra.add(
                    StorageVolumeInfo(
                        label = dir.name,
                        path = dir.absolutePath,
                        uuid = dir.name,
                        isRemovable = true,
                        needsSaf = !dir.canWrite()
                    )
                )
            }
        }
        if (extra.isNotEmpty()) {
            EcosystemLogger.d(TAG, "Scan found ${extra.size} extra volume(s): ${extra.map { it.path }}")
        }
        return extra
    }

    /**
     * Extract the storage root from an app-specific external files directory.
     * e.g., /storage/1234-5678/Android/data/com.vamp.haron/files -> /storage/1234-5678
     */
    private fun extractRootPath(dir: File): String? {
        var current = dir
        while (current.parent != null) {
            if (current.name == "Android") {
                return current.parent
            }
            current = current.parentFile ?: break
        }
        return null
    }

    companion object {
        private const val CACHE_TTL_MS = 5_000L

        /** Alternative mount point prefixes to try when standard paths fail */
        private val ALTERNATIVE_MOUNT_PREFIXES = listOf(
            "/mnt/media_rw",     // Raw mount point before FUSE (common on Android 6-9)
            "/mnt/usb_storage",  // Some Samsung/LG devices
            "/mnt/usb",          // Some older devices
            "/storage/usbotg",   // Some Qualcomm devices
            "/storage/UsbDriveA", // Some custom ROMs
            "/storage/UsbDriveB"
        )
    }
}
