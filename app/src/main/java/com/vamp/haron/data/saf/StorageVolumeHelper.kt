package com.vamp.haron.data.saf

import android.content.Context
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

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

        for (volume in storageManager.storageVolumes) {
            val label = volume.getDescription(context) ?: "Storage"
            val uuid = volume.uuid
            val isRemovable = volume.isRemovable

            // Try to get path from external files dirs
            val path = findVolumePath(uuid, isRemovable)
            val needsSaf = if (path != null) !File(path).canWrite() else isRemovable

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
        return getStorageVolumes().filter { it.isRemovable }
    }

    private fun findVolumePath(uuid: String?, isRemovable: Boolean): String? {
        val dirs = ContextCompat.getExternalFilesDirs(context, null)
        if (!isRemovable) {
            return dirs.firstOrNull()?.let { extractRootPath(it) }
        }
        // For removable storage, find the one that's not the primary
        for (dir in dirs.drop(1)) {
            if (dir == null) continue
            val root = extractRootPath(dir)
            if (root != null) return root
        }
        return null
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
}
