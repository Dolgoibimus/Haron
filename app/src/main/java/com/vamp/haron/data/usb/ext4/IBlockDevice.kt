package com.vamp.haron.data.usb.ext4

/**
 * Interface for raw block I/O — implemented by libaums USB adapter.
 * Called from C (lwext4) via JNI callbacks.
 */
interface IBlockDevice {
    /** Read [blockCount] blocks starting at [blockId] into [buffer]. */
    fun readBlocks(blockId: Long, blockCount: Int, buffer: ByteArray): Boolean

    /** Write [blockCount] blocks starting at [blockId] from [buffer]. */
    fun writeBlocks(blockId: Long, blockCount: Int, buffer: ByteArray): Boolean

    /** Physical block size in bytes (typically 512). */
    fun getBlockSize(): Int

    /** Total number of physical blocks on the device. */
    fun getBlockCount(): Long
}
