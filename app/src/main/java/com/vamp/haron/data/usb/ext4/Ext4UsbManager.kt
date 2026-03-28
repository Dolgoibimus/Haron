package com.vamp.haron.data.usb.ext4

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.vamp.core.logger.EcosystemLogger
import me.jahnen.libaums.core.UsbMassStorageDevice
import java.io.File
import java.io.FileOutputStream

private const val TAG = "Ext4UsbManager"

/**
 * High-level manager for ext4 USB drives.
 * Detects ext4 partitions on USB devices that Android can't mount,
 * and provides filesystem operations via lwext4.
 *
 * Usage flow:
 * 1. tryMount(usbDevice) — init libaums + lwext4, scan MBR, mount ext4
 * 2. listDir("/usb/path") — list files
 * 3. copyToLocal(ext4Path, localPath) — copy file from ext4 to local storage
 * 4. unmount() — cleanup
 */
class Ext4UsbManager(private val context: Context) {

    private var massStorageDevice: UsbMassStorageDevice? = null
    private var blockDevice: LibaumsBlockDevice? = null
    private var mounted = false

    val isMounted: Boolean get() = mounted

    /**
     * Try to mount ext4 on a USB device.
     * @param usbDevice the USB device to probe
     * @param readOnly mount read-only (safer) or read-write
     * @return true if ext4 was found and mounted successfully
     */
    fun tryMount(usbDevice: UsbDevice, readOnly: Boolean = false): Boolean {
        if (mounted) {
            EcosystemLogger.d(TAG, "tryMount: already mounted, unmount first")
            return false
        }

        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        if (!usbManager.hasPermission(usbDevice)) {
            EcosystemLogger.e(TAG, "tryMount: no USB permission for ${usbDevice.deviceName}")
            return false
        }

        // Find the matching UsbMassStorageDevice
        val devices = UsbMassStorageDevice.getMassStorageDevices(context)
        val device = devices.firstOrNull { it.usbDevice == usbDevice }
        if (device == null) {
            EcosystemLogger.e(TAG, "tryMount: device not found in mass storage list")
            return false
        }

        try {
            device.init()
            EcosystemLogger.d(TAG, "tryMount: libaums init OK, partitions=${device.partitions.size}")

            val bd = LibaumsBlockDevice(device)
            massStorageDevice = device
            blockDevice = bd

            // Initialize JNI bridge
            if (!Ext4Native.nativeInit(bd)) {
                EcosystemLogger.e(TAG, "tryMount: nativeInit failed")
                cleanup()
                return false
            }

            // Scan MBR for partitions
            val partIdx = Ext4Native.nativeScanMbr()
            EcosystemLogger.d(TAG, "tryMount: MBR scan result=$partIdx")

            if (partIdx < 0) {
                // No MBR — try mounting the whole device as ext4
                EcosystemLogger.d(TAG, "tryMount: no MBR, trying whole device as ext4")
            }

            // Try mounting the auto-selected partition
            var mountOk = Ext4Native.nativeMount(readOnly)

            // If failed, try each partition
            if (!mountOk && partIdx >= 0) {
                for (i in 0..3) {
                    if (i == partIdx) continue
                    EcosystemLogger.d(TAG, "tryMount: retrying partition $i...")
                    if (Ext4Native.nativeSelectPartition(i)) {
                        mountOk = Ext4Native.nativeMount(readOnly)
                        if (mountOk) {
                            EcosystemLogger.d(TAG, "tryMount: partition $i mounted OK")
                            break
                        }
                    }
                }
            }

            if (!mountOk) {
                EcosystemLogger.e(TAG, "tryMount: all partitions failed")
                cleanup()
                return false
            }

            mounted = true
            EcosystemLogger.i(TAG, "tryMount: ext4 mounted successfully (readOnly=$readOnly)")
            return true

        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "tryMount: exception: ${e.message}")
            cleanup()
            return false
        }
    }

    /**
     * List directory on ext4.
     * @param path path relative to mount point, e.g. "/usb/" for root
     * @return list of Ext4Entry or null on error
     */
    fun listDir(path: String): List<Ext4Entry>? {
        if (!mounted) {
            EcosystemLogger.e(TAG, "listDir: not mounted!")
            return null
        }

        val ext4Path = toExt4Path(path)
        EcosystemLogger.d(TAG, "listDir: input=$path → ext4Path=$ext4Path, mounted=$mounted")
        val entries = Ext4Native.nativeListDir(ext4Path)
        EcosystemLogger.d(TAG, "listDir: result=${entries?.size ?: "null"} entries")
        if (entries == null) return null

        return entries.mapNotNull { raw ->
            val parts = raw.split("|", limit = 4)
            if (parts.size < 4) return@mapNotNull null

            Ext4Entry(
                type = when (parts[0]) {
                    "d" -> Ext4EntryType.DIRECTORY
                    "l" -> Ext4EntryType.SYMLINK
                    else -> Ext4EntryType.FILE
                },
                name = parts[1],
                size = parts[2].toLongOrNull() ?: 0L,
                mtime = parts[3].toLongOrNull() ?: 0L
            )
        }
    }

    /**
     * Copy file from ext4 to local storage.
     * @param ext4Path path on ext4 filesystem
     * @param localFile destination file on local storage
     * @param onProgress callback (bytesCopied, totalSize)
     */
    fun copyToLocal(
        ext4Path: String,
        localFile: File,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean {
        if (!mounted) return false

        val path = toExt4Path(ext4Path)
        val totalSize = Ext4Native.nativeFileSize(path)
        if (totalSize < 0) {
            EcosystemLogger.e(TAG, "copyToLocal: can't get size of $path")
            return false
        }

        // Small files: read at once
        if (totalSize <= 4 * 1024 * 1024) {
            val data = Ext4Native.nativeReadFile(path, 0) ?: return false
            FileOutputStream(localFile).use { it.write(data) }
            onProgress?.invoke(totalSize, totalSize)
            return true
        }

        // Large files: chunked copy
        val chunkSize = 1024 * 1024 // 1MB chunks
        val buffer = ByteArray(chunkSize)
        var offset = 0L

        FileOutputStream(localFile).use { fos ->
            while (offset < totalSize) {
                val read = Ext4Native.nativeReadFileChunk(path, offset, buffer, chunkSize)
                if (read <= 0) break
                fos.write(buffer, 0, read)
                offset += read
                onProgress?.invoke(offset, totalSize)
            }
        }

        EcosystemLogger.d(TAG, "copyToLocal: $path → ${localFile.absolutePath}, $offset bytes")
        return offset >= totalSize
    }

    /**
     * Copy local file to ext4 filesystem. Chunked to avoid OOM on large files.
     */
    fun copyFromLocal(localFile: File, ext4Path: String): Boolean {
        if (!mounted) return false
        val internal = toExt4Path(ext4Path)
        val size = localFile.length()

        // Small files (<4MB): read at once
        if (size <= 4 * 1024 * 1024) {
            val data = localFile.readBytes()
            return Ext4Native.nativeWriteFile(internal, data)
        }

        // Large files: chunked write via nativeWriteFileChunked
        try {
            val chunkSize = 1 * 1024 * 1024 // 1MB chunks
            val buffer = ByteArray(chunkSize)
            var isFirst = true
            localFile.inputStream().buffered().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    val data = if (read == chunkSize) buffer else buffer.copyOf(read)
                    val mode = if (isFirst) "wb" else "ab" // wb = create/truncate, ab = append
                    if (!Ext4Native.nativeWriteFileMode(internal, data, mode)) return false
                    isFirst = false
                }
            }
            EcosystemLogger.d("Ext4UsbManager", "copyFromLocal chunked: ${localFile.name}, $size bytes")
            return true
        } catch (e: Exception) {
            EcosystemLogger.e("Ext4UsbManager", "copyFromLocal failed: ${e.message}")
            return false
        }
    }

    /**
     * Create directory on ext4.
     */
    fun mkdir(ext4Path: String): Boolean {
        if (!mounted) return false
        return Ext4Native.nativeMkdir(toExt4Path(ext4Path))
    }

    /**
     * Delete file or empty directory on ext4.
     */
    fun remove(ext4Path: String): Boolean {
        if (!mounted) return false
        val internal = toExt4Path(ext4Path)
        EcosystemLogger.d("Ext4UsbManager", "remove: input=$ext4Path → internal=$internal")
        return Ext4Native.nativeRemove(internal)
    }

    /**
     * Rename/move on ext4.
     */
    fun rename(oldPath: String, newPath: String): Boolean {
        if (!mounted) return false
        return Ext4Native.nativeRename(toExt4Path(oldPath), toExt4Path(newPath))
    }

    /**
     * Unmount and release all resources.
     */
    fun unmount() {
        if (!mounted) return
        try {
            Ext4Native.nativeUnmount()
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "unmount error: ${e.message}")
        }
        mounted = false
        cleanup()
        EcosystemLogger.i(TAG, "unmounted")
    }

    private fun cleanup() {
        try {
            massStorageDevice?.close()
        } catch (_: Exception) {}
        massStorageDevice = null
        blockDevice = null
    }

    private fun toExt4Path(path: String): String {
        // Convert any path format to lwext4 internal path "/usb/..."
        val internal = if (Ext4PathUtils.isExt4Path(path)) {
            Ext4PathUtils.toInternalPath(path)
        } else {
            val clean = path.removePrefix("/usb").removePrefix("/")
            "/usb/$clean"
        }
        // Ensure ends with / for root dir
        return if (internal == "/usb" || internal == "/usb/") "/usb/" else internal
    }
}

data class Ext4Entry(
    val type: Ext4EntryType,
    val name: String,
    val size: Long,
    /** Unix timestamp (seconds since epoch) */
    val mtime: Long
) {
    fun toFileEntry(parentPath: String): com.vamp.haron.domain.model.FileEntry {
        val fullPath = if (parentPath.endsWith("/")) "$parentPath$name" else "$parentPath/$name"
        return com.vamp.haron.domain.model.FileEntry(
            name = name,
            path = fullPath,
            isDirectory = type == Ext4EntryType.DIRECTORY,
            size = size,
            lastModified = mtime * 1000, // seconds → millis
            extension = if (type == Ext4EntryType.FILE) name.substringAfterLast('.', "") else "",
            isHidden = name.startsWith("."),
            childCount = if (type == Ext4EntryType.DIRECTORY) -1 else 0
        )
    }
}

enum class Ext4EntryType {
    FILE, DIRECTORY, SYMLINK
}

object Ext4PathUtils {
    const val PREFIX = "ext4://"

    fun isExt4Path(path: String): Boolean = path.startsWith(PREFIX)

    /** "ext4:///usb/somedir" → "/usb/somedir" */
    fun toInternalPath(path: String): String = path.removePrefix(PREFIX).ifEmpty { "/usb/" }

    /** "/usb/somedir" → "ext4:///usb/somedir" */
    fun toExt4Path(internalPath: String): String = "$PREFIX$internalPath"

    /** Display path: "ext4:///usb/dir/sub" → "USB (ext4): /dir/sub" */
    fun toDisplayPath(path: String, label: String): String {
        val internal = toInternalPath(path)
        val relative = internal.removePrefix("/usb/").removePrefix("/usb")
        return "$label: /$relative"
    }

    /** Parent: "ext4:///usb/a/b" → "ext4:///usb/a" */
    fun parentPath(path: String): String? {
        val internal = toInternalPath(path)
        if (internal == "/usb/" || internal == "/usb") return null
        val parent = internal.substringBeforeLast('/')
        return if (parent.isEmpty() || parent == "/usb") toExt4Path("/usb/") else toExt4Path(parent)
    }
}
