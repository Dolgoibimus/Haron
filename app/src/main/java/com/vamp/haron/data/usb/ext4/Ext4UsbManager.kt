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
        if (!mounted) return null

        val ext4Path = toExt4Path(path)
        val entries = Ext4Native.nativeListDir(ext4Path) ?: return null

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
     * Copy local file to ext4 filesystem.
     */
    fun copyFromLocal(localFile: File, ext4Path: String): Boolean {
        if (!mounted) return false
        val data = localFile.readBytes()
        return Ext4Native.nativeWriteFile(toExt4Path(ext4Path), data)
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
        return Ext4Native.nativeRemove(toExt4Path(ext4Path))
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
        // lwext4 mount point is "/usb/"
        val clean = path.removePrefix("/usb").removePrefix("/")
        return "/usb/$clean"
    }
}

data class Ext4Entry(
    val type: Ext4EntryType,
    val name: String,
    val size: Long,
    /** Unix timestamp (seconds since epoch) */
    val mtime: Long
)

enum class Ext4EntryType {
    FILE, DIRECTORY, SYMLINK
}
