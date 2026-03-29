package com.vamp.haron.data.usb.ext4

import kotlinx.coroutines.sync.Mutex

/**
 * Coordinates USB I/O between file operations (copy/move/delete) and thumbnail loading.
 * File operations have priority — thumbnails skip if USB is busy.
 *
 * USB block device is single-threaded (LibaumsBlockDevice.usbLock).
 * This scheduler prevents thumbnail reads from starving file operations.
 */
object Ext4IoScheduler {

    private val thumbMutex = Mutex()

    /** Set to true when file operation is in progress. Thumbnails skip immediately. */
    @Volatile
    var fileOpActive = false

    /**
     * Mark file operation start/end. Call from Ext4FileOperations.
     */
    fun beginFileOp() { fileOpActive = true }
    fun endFileOp() { fileOpActive = false }

    /**
     * Read thumbnail data. Waits up to 3 sec if another thumbnail is loading.
     * Returns null immediately if file operation is active.
     */
    suspend fun <T> withThumbnailRead(block: suspend () -> T): T? {
        if (fileOpActive) return null
        // Wait for other thumbnails (sequential loading), but skip if file op starts
        return try {
            kotlinx.coroutines.withTimeoutOrNull(3000) {
                thumbMutex.lock()
                try {
                    if (fileOpActive) null
                    else block()
                } finally {
                    thumbMutex.unlock()
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
