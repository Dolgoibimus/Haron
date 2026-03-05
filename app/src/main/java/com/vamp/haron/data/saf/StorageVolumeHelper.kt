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
    val needsSaf: Boolean
)

@Singleton
class StorageVolumeHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getStorageVolumes(): List<StorageVolumeInfo> {
        val volumes = mutableListOf<StorageVolumeInfo>()
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

        val storageVolumes = storageManager.storageVolumes
        EcosystemLogger.d(TAG, "StorageManager returned ${storageVolumes.size} volume(s)")

        for (volume in storageVolumes) {
            val label = volume.getDescription(context) ?: "Storage"
            val uuid = volume.uuid
            val isRemovable = volume.isRemovable
            val state = volume.state

            EcosystemLogger.d(TAG, "Volume: label=$label, uuid=$uuid, removable=$isRemovable, state=$state")

            // Try multiple strategies to resolve path
            val path = getVolumePath(volume, uuid, isRemovable)
            val needsSaf = if (path != null) !File(path).canWrite() else isRemovable

            if (path != null) {
                val dir = File(path)
                EcosystemLogger.d(TAG, "  path=$path, exists=${dir.exists()}, canRead=${dir.canRead()}, canWrite=${dir.canWrite()}")
            } else {
                EcosystemLogger.d(TAG, "  path=null (could not resolve via any method)")
            }

            volumes.add(
                StorageVolumeInfo(
                    label = label,
                    path = path,
                    uuid = uuid,
                    isRemovable = isRemovable,
                    needsSaf = needsSaf
                )
            )
        }
        return volumes
    }

    fun getRemovableVolumes(): List<StorageVolumeInfo> {
        val removable = getStorageVolumes().filter { it.isRemovable }
        EcosystemLogger.d(TAG, "Removable volumes: ${removable.size} — ${removable.map { "${it.label}(${it.path})" }}")
        return removable
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
        // Strategy 1: /storage/{uuid} — most reliable for removable volumes
        // Must be BEFORE reflection: some devices (Sony) return wrong path from getPath()
        if (uuid != null) {
            val direct = File("/storage/$uuid")
            if (direct.exists() && direct.canRead()) {
                EcosystemLogger.d(TAG, "  [strategy:direct] /storage/$uuid")
                return direct.absolutePath
            } else {
                EcosystemLogger.d(TAG, "  [strategy:direct] /storage/$uuid — exists=${direct.exists()}, canRead=${direct.canRead()}")
            }
        }

        // Strategy 2: getDirectory() — API 30+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val dir = volume.directory
                if (dir != null && dir.exists()) {
                    EcosystemLogger.d(TAG, "  [strategy:getDirectory] ${dir.absolutePath}")
                    return dir.absolutePath
                } else {
                    EcosystemLogger.d(TAG, "  [strategy:getDirectory] returned null (state=${volume.state})")
                }
            } catch (e: Exception) {
                EcosystemLogger.d(TAG, "  [strategy:getDirectory] failed: ${e.message}")
            }
        }

        // Strategy 3: /mnt/media_rw/{uuid} — raw mount point on older Android
        if (uuid != null) {
            val mntPath = File("/mnt/media_rw/$uuid")
            if (mntPath.exists() && mntPath.canRead()) {
                EcosystemLogger.d(TAG, "  [strategy:mnt_media_rw] /mnt/media_rw/$uuid")
                return mntPath.absolutePath
            }
        }

        // Strategy 4: reflection getPath() — hidden API
        // On Sony API 28, getPath() returns the WRONG path (SD card path for all removable volumes).
        // Only trust it if the path contains the volume's UUID, or if there's no UUID.
        try {
            val getPathMethod = volume.javaClass.getMethod("getPath")
            val path = getPathMethod.invoke(volume) as? String
            if (path != null && File(path).exists()) {
                val pathMatchesUuid = uuid == null || path.contains(uuid, ignoreCase = true)
                if (pathMatchesUuid) {
                    EcosystemLogger.d(TAG, "  [strategy:reflection] $path")
                    return path
                } else {
                    EcosystemLogger.d(TAG, "  [strategy:reflection] SKIP $path — doesn't match uuid=$uuid (Sony bug?)")
                }
            }
        } catch (e: Exception) {
            EcosystemLogger.d(TAG, "  [strategy:reflection] failed: ${e.message}")
        }

        // Strategy 5: getExternalFilesDirs fallback — match by uuid
        val dirs = ContextCompat.getExternalFilesDirs(context, null)
        EcosystemLogger.d(TAG, "  [strategy:extFilesDirs] ${dirs.map { it?.absolutePath }}")
        if (!isRemovable) {
            return dirs.firstOrNull()?.let { extractRootPath(it) }
        }
        for (dir in dirs.drop(1)) {
            if (dir == null) continue
            val root = extractRootPath(dir)
            if (root != null) {
                // If we have a UUID, only accept paths containing it (avoid returning SD card path for USB)
                if (uuid != null && !root.contains(uuid, ignoreCase = true)) {
                    EcosystemLogger.d(TAG, "  [strategy:extFilesDirs] SKIP $root — doesn't match uuid=$uuid")
                    continue
                }
                EcosystemLogger.d(TAG, "  [strategy:extFilesDirs] root=$root")
                return root
            }
        }

        // Strategy 6: scan alternative mount points
        if (uuid != null) {
            for (prefix in ALTERNATIVE_MOUNT_PREFIXES) {
                val alt = File("$prefix/$uuid")
                if (alt.exists() && alt.canRead()) {
                    EcosystemLogger.d(TAG, "  [strategy:altMount] $prefix/$uuid")
                    return alt.absolutePath
                }
            }
        }

        // All strategies failed — log diagnostic info
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
