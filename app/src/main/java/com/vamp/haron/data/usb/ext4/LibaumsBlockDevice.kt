package com.vamp.haron.data.usb.ext4

import com.vamp.core.logger.EcosystemLogger
import me.jahnen.libaums.core.UsbMassStorageDevice
import me.jahnen.libaums.core.driver.BlockDeviceDriver
import java.nio.ByteBuffer

private const val TAG = "LibaumsBlockDev"

/**
 * Adapts libaums raw BlockDeviceDriver to IBlockDevice interface
 * for use with lwext4 via JNI.
 *
 * Gets the BlockDeviceDriver via reflection from UsbMassStorageDevice
 * (it's created internally during init() but not directly exposed).
 */
class LibaumsBlockDevice(
    massStorageDevice: UsbMassStorageDevice
) : IBlockDevice {

    private val driver: BlockDeviceDriver
    private val bSize: Int
    private val bCount: Long

    init {
        // Extract BlockDeviceDriver via reflection
        // UsbMassStorageDevice.setupDevice() creates BlockDeviceDriver and passes to Partition
        // We need the raw driver (whole disk, not partition-scoped)
        driver = extractBlockDeviceDriver(massStorageDevice)
        bSize = driver.blockSize
        bCount = driver.blocks
        EcosystemLogger.d(TAG, "init: blockSize=$bSize, blockCount=$bCount, " +
                "totalMB=${bCount * bSize / 1024 / 1024}")
    }

    override fun readBlocks(blockId: Long, blockCount: Int, buffer: ByteArray): Boolean {
        return try {
            val byteOffset = blockId * bSize
            val byteCount = blockCount * bSize
            val bb = ByteBuffer.allocate(byteCount)
            driver.read(byteOffset, bb)
            bb.flip()
            bb.get(buffer, 0, byteCount)
            true
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "readBlocks failed: blk=$blockId, cnt=$blockCount, err=${e.message}")
            false
        }
    }

    override fun writeBlocks(blockId: Long, blockCount: Int, buffer: ByteArray): Boolean {
        return try {
            val byteOffset = blockId * bSize
            val byteCount = blockCount * bSize
            val bb = ByteBuffer.wrap(buffer, 0, byteCount)
            driver.write(byteOffset, bb)
            true
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "writeBlocks failed: blk=$blockId, cnt=$blockCount, err=${e.message}")
            false
        }
    }

    override fun getBlockSize(): Int = bSize

    override fun getBlockCount(): Long = bCount

    companion object {
        /**
         * Extract raw BlockDeviceDriver from UsbMassStorageDevice via reflection.
         *
         * libaums flow: init() → setupDevice() → BlockDeviceDriverFactory.createBlockDevice()
         * The driver is passed to Partition constructor which extends ByteBlockDevice.
         * We access it through Partition → ByteBlockDevice.blockDevice field.
         *
         * If no partitions exist (ext4 not recognized by libaums), we recreate the driver
         * using the same factory method.
         */
        private fun extractBlockDeviceDriver(device: UsbMassStorageDevice): BlockDeviceDriver {
            // Try getting driver from existing partitions
            val partitions = device.partitions
            if (partitions.isNotEmpty()) {
                try {
                    // Partition extends ByteBlockDevice which has BlockDeviceDriver
                    val byteBlockDeviceClass = partitions[0].javaClass.superclass
                    val field = byteBlockDeviceClass?.getDeclaredField("blockDevice")
                        ?: throw NoSuchFieldException("blockDevice field not found in ByteBlockDevice")
                    field.isAccessible = true
                    val driver = field.get(partitions[0]) as? BlockDeviceDriver
                    if (driver != null) {
                        EcosystemLogger.d(TAG, "extractDriver: from partition[0], blockSize=${driver.blockSize}")
                        return driver
                    }
                } catch (e: Exception) {
                    EcosystemLogger.d(TAG, "extractDriver: partition reflection failed: ${e.message}")
                }
            }

            // No partitions or reflection failed — create driver directly
            // Access UsbMassStorageDevice internal usbCommunication + create ScsiBlockDevice
            try {
                val commField = device.javaClass.getDeclaredField("usbCommunication")
                commField.isAccessible = true
                val usbComm = commField.get(device)
                    ?: throw IllegalStateException("usbCommunication is null")

                // BlockDeviceDriverFactory.createBlockDevice(usbCommunication, lun: Byte)
                val factoryClass = Class.forName("me.jahnen.libaums.core.driver.BlockDeviceDriverFactory")
                val createMethod = factoryClass.getDeclaredMethod(
                    "createBlockDevice",
                    Class.forName("me.jahnen.libaums.core.usb.UsbCommunication"),
                    Byte::class.java
                )
                createMethod.isAccessible = true
                val lun: Byte = 0
                val driver = createMethod.invoke(factoryClass.kotlin.objectInstance, usbComm, lun) as BlockDeviceDriver
                driver.init()
                EcosystemLogger.d(TAG, "extractDriver: created via factory, blockSize=${driver.blockSize}, blocks=${driver.blocks}")
                return driver
            } catch (e: Exception) {
                EcosystemLogger.e(TAG, "extractDriver: factory creation failed: ${e.javaClass.simpleName}: ${e.message}")
                throw IllegalStateException("Cannot access BlockDeviceDriver: ${e.message}", e)
            }
        }
    }
}
