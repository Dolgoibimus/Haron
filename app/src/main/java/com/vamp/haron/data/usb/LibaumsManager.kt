package com.vamp.haron.data.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.vamp.core.logger.EcosystemLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import me.jahnen.libaums.core.UsbMassStorageDevice
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "LibaumsManager"
private const val ACTION_USB_PERMISSION = "com.vamp.haron.USB_PERMISSION"

data class LibaumsProbeResult(
    val vendorId: Int,
    val productId: Int,
    val deviceName: String,
    val label: String,
    val fileSystemType: String,
    val supported: Boolean,
    val totalSpace: Long = 0L,
    val freeSpace: Long = 0L
)

@Singleton
class LibaumsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var cachedResults: List<LibaumsProbeResult> = emptyList()

    /**
     * Probe all USB Mass Storage devices via libaums.
     * For each device: request permission → init → check if FAT32 readable.
     * IOException on init → unsupported FS (likely NTFS).
     */
    suspend fun probeAllDevices(): List<LibaumsProbeResult> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val massStorageDevices = try {
            UsbMassStorageDevice.getMassStorageDevices(context)
        } catch (e: Exception) {
            EcosystemLogger.w(TAG, "getMassStorageDevices failed: ${e.message}")
            return emptyList()
        }

        if (massStorageDevices.isEmpty()) {
            EcosystemLogger.d(TAG, "No USB Mass Storage devices found")
            cachedResults = emptyList()
            return emptyList()
        }

        EcosystemLogger.d(TAG, "Found ${massStorageDevices.size} USB Mass Storage device(s)")

        val results = mutableListOf<LibaumsProbeResult>()
        for (device in massStorageDevices) {
            val usbDevice = device.usbDevice
            val result = probeDevice(usbManager, device, usbDevice)
            if (result != null) {
                results.add(result)
            }
        }

        cachedResults = results
        EcosystemLogger.d(TAG, "Probe results: ${results.size} device(s)")
        for (r in results) {
            EcosystemLogger.d(TAG, "  ${r.deviceName}: fs=${r.fileSystemType}, supported=${r.supported}, label=${r.label}")
        }
        return results
    }

    private suspend fun probeDevice(
        usbManager: UsbManager,
        massStorageDevice: UsbMassStorageDevice,
        usbDevice: UsbDevice
    ): LibaumsProbeResult? {
        val hwLabel = usbDevice.productName
            ?: usbDevice.manufacturerName?.let { "$it USB" }
            ?: "USB"

        // Request USB permission if not granted
        if (!usbManager.hasPermission(usbDevice)) {
            val granted = requestPermission(usbManager, usbDevice)
            if (!granted) {
                EcosystemLogger.d(TAG, "Permission denied for ${usbDevice.deviceName}")
                return LibaumsProbeResult(
                    vendorId = usbDevice.vendorId,
                    productId = usbDevice.productId,
                    deviceName = usbDevice.deviceName,
                    label = hwLabel,
                    fileSystemType = "unknown",
                    supported = false
                )
            }
        }

        EcosystemLogger.d(TAG, "probeDevice: ${usbDevice.deviceName} vendor=${usbDevice.vendorId} product=${usbDevice.productId} hwLabel=$hwLabel hasPermission=${usbManager.hasPermission(usbDevice)}")

        return try {
            EcosystemLogger.d(TAG, "${usbDevice.deviceName}: calling init()...")
            massStorageDevice.init()

            // If init() succeeds, libaums can read the device → FAT32/FAT16/FAT12
            val partitions = massStorageDevice.partitions
            EcosystemLogger.d(TAG, "${usbDevice.deviceName}: init OK, partitions=${partitions.size}")
            if (partitions.isEmpty()) {
                massStorageDevice.close()
                EcosystemLogger.d(TAG, "${usbDevice.deviceName}: no partitions")
                return LibaumsProbeResult(
                    vendorId = usbDevice.vendorId,
                    productId = usbDevice.productId,
                    deviceName = usbDevice.deviceName,
                    label = hwLabel,
                    fileSystemType = "unknown",
                    supported = false
                )
            }

            val partition = partitions[0]
            val fs = partition.fileSystem
            val volumeLabel = fs.volumeLabel.ifEmpty { hwLabel }
            val totalSpace = fs.capacity
            val freeSpace = fs.freeSpace
            val fsType = fs.javaClass.simpleName

            massStorageDevice.close()

            EcosystemLogger.d(TAG, "${usbDevice.deviceName}: FS=$fsType, label=$volumeLabel, total=${totalSpace / 1_000_000}MB, free=${freeSpace / 1_000_000}MB")

            LibaumsProbeResult(
                vendorId = usbDevice.vendorId,
                productId = usbDevice.productId,
                deviceName = usbDevice.deviceName,
                label = volumeLabel,
                fileSystemType = fsType,
                supported = true,
                totalSpace = totalSpace,
                freeSpace = freeSpace
            )
        } catch (e: Exception) {
            // init() failed → unsupported FS (NTFS, exFAT without kernel support, etc.)
            try { massStorageDevice.close() } catch (_: Exception) {}
            EcosystemLogger.d(TAG, "${usbDevice.deviceName}: init FAILED (${e.javaClass.simpleName}: ${e.message}) → unsupported FS")
            // Log full stack trace for debugging
            EcosystemLogger.e(TAG, "${usbDevice.deviceName}: stack: ${e.stackTraceToString().take(500)}")

            LibaumsProbeResult(
                vendorId = usbDevice.vendorId,
                productId = usbDevice.productId,
                deviceName = usbDevice.deviceName,
                label = hwLabel,
                fileSystemType = "NTFS",
                supported = false
            )
        }
    }

    private suspend fun requestPermission(
        usbManager: UsbManager,
        usbDevice: UsbDevice
    ): Boolean = suspendCancellableCoroutine { cont ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == ACTION_USB_PERMISSION) {
                    try { context.unregisterReceiver(this) } catch (_: Exception) {}
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (cont.isActive) cont.resume(granted)
                }
            }
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        // Explicit intent (with package) — required on API 34+ to avoid
        // "disallows creating PendingIntent with FLAG_MUTABLE, an implicit Intent"
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
            flags
        )
        usbManager.requestPermission(usbDevice, permissionIntent)

        cont.invokeOnCancellation {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    /**
     * Check if there are any physically connected USB Mass Storage devices.
     */
    fun hasPhysicalDevices(): Boolean {
        return try {
            UsbMassStorageDevice.getMassStorageDevices(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun clearProbeResults() {
        cachedResults = emptyList()
        EcosystemLogger.d(TAG, "Probe results cleared")
    }

    fun getCachedResults(): List<LibaumsProbeResult> = cachedResults
}
