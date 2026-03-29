package com.vamp.haron.data.usb.ext4

/**
 * JNI bridge to lwext4 C library.
 * Provides ext2/3/4 filesystem operations over raw USB block devices.
 */
object Ext4Native {

    init {
        System.loadLibrary("haron_ext4")
    }

    /**
     * Initialize with a block device. Must be called before any other operation.
     */
    external fun nativeInit(blockDevice: IBlockDevice): Boolean

    /**
     * Scan MBR partition table. Returns partition index (0-3) or -1.
     * Automatically selects the first usable partition.
     */
    external fun nativeScanMbr(): Int

    /**
     * Select a specific partition by index (from MBR scan).
     */
    external fun nativeSelectPartition(index: Int): Boolean

    /**
     * Mount the selected partition.
     * @param readOnly true for read-only mount
     */
    external fun nativeMount(readOnly: Boolean): Boolean

    /**
     * Unmount and release resources.
     */
    external fun nativeUnmount()

    /**
     * List directory entries.
     * Returns array of "type|name|size|mtime" strings.
     * type: 'd' = directory, 'f' = file, 'l' = symlink
     */
    external fun nativeListDir(path: String): Array<String>?

    /**
     * Read entire file (up to maxSize bytes). For small files.
     * @param maxSize max bytes to read, 0 = unlimited (capped at 64MB internally)
     */
    external fun nativeReadFile(path: String, maxSize: Long): ByteArray?

    /**
     * Read a chunk of file at offset into buffer. Returns bytes read or -1.
     */
    external fun nativeReadFileChunk(
        path: String, offset: Long, buffer: ByteArray, bufLen: Int
    ): Int

    /**
     * Write data to file (creates or overwrites).
     */
    external fun nativeWriteFile(path: String, data: ByteArray): Boolean

    /**
     * Write data to file with specific mode ("wb" = create/truncate, "ab" = append).
     */
    external fun nativeWriteFileMode(path: String, data: ByteArray, mode: String): Boolean

    /**
     * Create directory (including parents).
     */
    external fun nativeMkdir(path: String): Boolean

    /**
     * Remove file or empty directory.
     */
    external fun nativeRemove(path: String): Boolean

    /**
     * Rename/move file or directory.
     */
    external fun nativeRename(oldPath: String, newPath: String): Boolean

    /**
     * Get file size in bytes.
     */
    external fun nativeFileSize(path: String): Long

    /**
     * Flush cache to disk — call after write operations.
     */
    external fun nativeCacheFlush()

    /**
     * Check if path is a directory.
     */
    external fun nativeIsDirectory(path: String): Boolean

    /**
     * Get filesystem stats: total bytes and free bytes.
     * @return LongArray[2] = {totalBytes, freeBytes}, or null if not mounted.
     */
    external fun nativeGetStats(): LongArray?
}
