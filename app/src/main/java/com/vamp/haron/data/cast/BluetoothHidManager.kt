package com.vamp.haron.data.cast

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.HidConnectionState
import com.vamp.haron.domain.model.RemoteInputEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothHidManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private var isRegistered = false
    private val keyboardMutex = Mutex()
    private var isRussianMode = false

    private val _connectionState = MutableStateFlow<HidConnectionState>(HidConnectionState.Disconnected)
    val connectionState: StateFlow<HidConnectionState> = _connectionState.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    // HID Report Descriptor: combo keyboard (Report ID 1) + mouse (Report ID 2)
    private val hidReportDescriptor = byteArrayOf(
        // --- Keyboard (Report ID 1) ---
        0x05.toByte(), 0x01.toByte(),       // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(),       // Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(),       // Collection (Application)
        0x85.toByte(), 0x01.toByte(),       //   Report ID (1)
        // Modifier keys
        0x05.toByte(), 0x07.toByte(),       //   Usage Page (Keyboard/Keypad)
        0x19.toByte(), 0xE0.toByte(),       //   Usage Minimum (Left Control)
        0x29.toByte(), 0xE7.toByte(),       //   Usage Maximum (Right GUI)
        0x15.toByte(), 0x00.toByte(),       //   Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),       //   Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),       //   Report Size (1)
        0x95.toByte(), 0x08.toByte(),       //   Report Count (8)
        0x81.toByte(), 0x02.toByte(),       //   Input (Data, Variable, Absolute)
        // Reserved byte
        0x95.toByte(), 0x01.toByte(),       //   Report Count (1)
        0x75.toByte(), 0x08.toByte(),       //   Report Size (8)
        0x81.toByte(), 0x01.toByte(),       //   Input (Constant)
        // Key codes (6 keys)
        0x95.toByte(), 0x06.toByte(),       //   Report Count (6)
        0x75.toByte(), 0x08.toByte(),       //   Report Size (8)
        0x15.toByte(), 0x00.toByte(),       //   Logical Minimum (0)
        0x25.toByte(), 0x65.toByte(),       //   Logical Maximum (101)
        0x05.toByte(), 0x07.toByte(),       //   Usage Page (Keyboard/Keypad)
        0x19.toByte(), 0x00.toByte(),       //   Usage Minimum (0)
        0x29.toByte(), 0x65.toByte(),       //   Usage Maximum (101)
        0x81.toByte(), 0x00.toByte(),       //   Input (Data, Array)
        0xC0.toByte(),                      // End Collection

        // --- Mouse (Report ID 2) ---
        0x05.toByte(), 0x01.toByte(),       // Usage Page (Generic Desktop)
        0x09.toByte(), 0x02.toByte(),       // Usage (Mouse)
        0xA1.toByte(), 0x01.toByte(),       // Collection (Application)
        0x85.toByte(), 0x02.toByte(),       //   Report ID (2)
        0x09.toByte(), 0x01.toByte(),       //   Usage (Pointer)
        0xA1.toByte(), 0x00.toByte(),       //   Collection (Physical)
        // Buttons (3)
        0x05.toByte(), 0x09.toByte(),       //     Usage Page (Button)
        0x19.toByte(), 0x01.toByte(),       //     Usage Minimum (Button 1)
        0x29.toByte(), 0x03.toByte(),       //     Usage Maximum (Button 3)
        0x15.toByte(), 0x00.toByte(),       //     Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),       //     Logical Maximum (1)
        0x95.toByte(), 0x03.toByte(),       //     Report Count (3)
        0x75.toByte(), 0x01.toByte(),       //     Report Size (1)
        0x81.toByte(), 0x02.toByte(),       //     Input (Data, Variable, Absolute)
        // Padding (5 bits)
        0x95.toByte(), 0x01.toByte(),       //     Report Count (1)
        0x75.toByte(), 0x05.toByte(),       //     Report Size (5)
        0x81.toByte(), 0x01.toByte(),       //     Input (Constant)
        // X, Y movement
        0x05.toByte(), 0x01.toByte(),       //     Usage Page (Generic Desktop)
        0x09.toByte(), 0x30.toByte(),       //     Usage (X)
        0x09.toByte(), 0x31.toByte(),       //     Usage (Y)
        0x15.toByte(), 0x81.toByte(),       //     Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(),       //     Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(),       //     Report Size (8)
        0x95.toByte(), 0x02.toByte(),       //     Report Count (2)
        0x81.toByte(), 0x06.toByte(),       //     Input (Data, Variable, Relative)
        // Wheel
        0x09.toByte(), 0x38.toByte(),       //     Usage (Wheel)
        0x15.toByte(), 0x81.toByte(),       //     Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(),       //     Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(),       //     Report Size (8)
        0x95.toByte(), 0x01.toByte(),       //     Report Count (1)
        0x81.toByte(), 0x06.toByte(),       //     Input (Data, Variable, Relative)
        0xC0.toByte(),                      //   End Collection
        0xC0.toByte()                       // End Collection
    )

    fun isSupported(): Boolean {
        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && adapter != null
        EcosystemLogger.d(HaronConstants.TAG, "BT HID: isSupported=$supported, SDK=${Build.VERSION.SDK_INT}, adapter=${adapter != null}, btEnabled=${adapter?.isEnabled}")
        return supported
    }

    /** Returns true when HID profile proxy is connected and app is registered */
    fun isReady(): Boolean {
        val ready = hidDevice != null && isRegistered
        EcosystemLogger.d(HaronConstants.TAG, "BT HID: isReady=$ready, hidDevice=${hidDevice != null}, isRegistered=$isRegistered")
        return ready
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        if (adapter == null) {
            EcosystemLogger.d(HaronConstants.TAG, "BT HID: refreshPairedDevices — adapter is null")
            return
        }
        try {
            val devices = adapter.bondedDevices?.toList() ?: emptyList()
            _pairedDevices.value = devices
            EcosystemLogger.d(HaronConstants.TAG, "BT HID: refreshPairedDevices — found ${devices.size} paired devices: ${devices.map { try { it.name } catch (_: Exception) { it.address } }}")
        } catch (e: SecurityException) {
            EcosystemLogger.e(HaronConstants.TAG, "BT HID: refreshPairedDevices security error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun init() {
        EcosystemLogger.d(HaronConstants.TAG, "BT HID: init() called, SDK=${Build.VERSION.SDK_INT}, adapter=${adapter != null}, btEnabled=${adapter?.isEnabled}, hidDevice=${hidDevice != null}")
        if (!isSupported()) {
            EcosystemLogger.e(HaronConstants.TAG, "BT HID: init() — NOT SUPPORTED")
            _connectionState.value = HidConnectionState.NotSupported
            return
        }
        if (hidDevice != null) {
            EcosystemLogger.d(HaronConstants.TAG, "BT HID: init() — already initialized, isRegistered=$isRegistered")
            if (!isRegistered) {
                EcosystemLogger.d(HaronConstants.TAG, "BT HID: init() — re-registering app")
                registerApp()
            }
            return
        }

        try {
            EcosystemLogger.d(HaronConstants.TAG, "BT HID: requesting HID_DEVICE profile proxy...")
            val proxyResult = adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    hidDevice = proxy as? BluetoothHidDevice
                    EcosystemLogger.d(HaronConstants.TAG, "BT HID: profile proxy connected, hidDevice=${hidDevice != null}, profile=$profile")
                    if (hidDevice != null) {
                        registerApp()
                    } else {
                        EcosystemLogger.e(HaronConstants.TAG, "BT HID: profile proxy connected but cast to BluetoothHidDevice failed, proxy=${proxy?.javaClass?.name}")
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    hidDevice = null
                    isRegistered = false
                    EcosystemLogger.d(HaronConstants.TAG, "BT HID: profile proxy disconnected, profile=$profile")
                }
            }, BluetoothProfile.HID_DEVICE)
            EcosystemLogger.d(HaronConstants.TAG, "BT HID: getProfileProxy returned $proxyResult")
        } catch (e: SecurityException) {
            EcosystemLogger.e(HaronConstants.TAG, "BT HID: init security error: ${e.message}")
            _connectionState.value = HidConnectionState.Error(e.message ?: "Security error")
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val hid = hidDevice
        if (hid == null) {
            EcosystemLogger.e(HaronConstants.TAG, "BT HID: registerApp — hidDevice is null")
            return
        }
        if (isRegistered) {
            EcosystemLogger.d(HaronConstants.TAG, "BT HID: registerApp — already registered")
            return
        }

        try {
            EcosystemLogger.d(HaronConstants.TAG, "BT HID: registerApp — creating SDP settings, descriptor size=${hidReportDescriptor.size}")
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "Haron Remote",
                "Haron File Manager Remote",
                "Haron",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                hidReportDescriptor
            )

            val callback = object : BluetoothHidDevice.Callback() {
                override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                    isRegistered = registered
                    val deviceName = try { pluggedDevice?.name } catch (_: Exception) { null }
                    EcosystemLogger.d(HaronConstants.TAG, "BT HID: onAppStatusChanged registered=$registered, pluggedDevice=$deviceName (${pluggedDevice?.address})")
                    if (registered) {
                        refreshPairedDevices()
                    }
                }

                @SuppressLint("MissingPermission")
                override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                    val stateName = when (state) {
                        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
                        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
                        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
                        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
                        else -> "UNKNOWN($state)"
                    }
                    val name = try { device?.name } catch (_: SecurityException) { null } ?: device?.address ?: "null"
                    EcosystemLogger.d(HaronConstants.TAG, "BT HID: onConnectionStateChanged state=$stateName, device=$name (${device?.address})")

                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            connectedDevice = device
                            _connectionState.value = HidConnectionState.Connected(name)
                            EcosystemLogger.i(HaronConstants.TAG, "BT HID: *** CONNECTED to $name ***")
                        }
                        BluetoothProfile.STATE_CONNECTING -> {
                            _connectionState.value = HidConnectionState.Connecting
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            connectedDevice = null
                            _connectionState.value = HidConnectionState.Disconnected
                            EcosystemLogger.i(HaronConstants.TAG, "BT HID: *** DISCONNECTED ***")
                        }
                    }
                }

                @SuppressLint("MissingPermission")
                override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
                    val name = try { device?.name } catch (_: Exception) { device?.address }
                    EcosystemLogger.d(HaronConstants.TAG, "BT HID: onGetReport device=$name, type=$type, id=$id, bufferSize=$bufferSize")
                    // Must reply — host disconnects if no response
                    val report = when (id.toInt()) {
                        1 -> byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0) // keyboard: no keys
                        2 -> byteArrayOf(0, 0, 0, 0)              // mouse: no movement
                        else -> byteArrayOf()
                    }
                    try {
                        val ok = hid.replyReport(device, type, id, report)
                        EcosystemLogger.d(HaronConstants.TAG, "BT HID: replyReport id=$id, ok=$ok")
                    } catch (e: Exception) {
                        EcosystemLogger.e(HaronConstants.TAG, "BT HID: replyReport error: ${e.message}")
                    }
                }

                @SuppressLint("MissingPermission")
                override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
                    val name = try { device?.name } catch (_: Exception) { device?.address }
                    EcosystemLogger.d(HaronConstants.TAG, "BT HID: onSetReport device=$name, type=$type, id=$id, dataSize=${data?.size}")
                    // Acknowledge the SET_REPORT
                    try {
                        hid.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
                    } catch (e: Exception) {
                        EcosystemLogger.e(HaronConstants.TAG, "BT HID: reportError error: ${e.message}")
                    }
                }

                override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) {
                    val name = try { device?.name } catch (_: Exception) { device?.address }
                    EcosystemLogger.d(HaronConstants.TAG, "BT HID: onInterruptData device=$name, reportId=$reportId, dataSize=${data?.size}")
                }
            }

            EcosystemLogger.d(HaronConstants.TAG, "BT HID: calling registerApp()...")
            val result = hid.registerApp(sdp, null, null, { it.run() }, callback)
            EcosystemLogger.d(HaronConstants.TAG, "BT HID: registerApp() returned $result")
        } catch (e: SecurityException) {
            EcosystemLogger.e(HaronConstants.TAG, "BT HID: registerApp security error: ${e.message}")
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "BT HID: registerApp error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        val hid = hidDevice
        val name = try { device.name } catch (_: Exception) { device.address }
        EcosystemLogger.d(HaronConstants.TAG, "BT HID: connect() called, device=$name (${device.address}), hidDevice=${hid != null}, isRegistered=$isRegistered")
        if (hid == null || !isRegistered) {
            val reason = if (hid == null) "hidDevice is null" else "not registered"
            EcosystemLogger.e(HaronConstants.TAG, "BT HID: connect failed — $reason")
            _connectionState.value = HidConnectionState.Error("HID not ready: $reason")
            return
        }
        _connectionState.value = HidConnectionState.Connecting
        try {
            val result = hid.connect(device)
            EcosystemLogger.d(HaronConstants.TAG, "BT HID: connect() returned $result for $name")
            if (!result) {
                _connectionState.value = HidConnectionState.Error("connect() returned false")
                EcosystemLogger.e(HaronConstants.TAG, "BT HID: connect() returned false for $name — device may not support HID or is already connected")
            }
        } catch (e: SecurityException) {
            _connectionState.value = HidConnectionState.Error(e.message ?: "Security error")
            EcosystemLogger.e(HaronConstants.TAG, "BT HID: connect security error: ${e.message}")
        } catch (e: Exception) {
            _connectionState.value = HidConnectionState.Error(e.message ?: "Error")
            EcosystemLogger.e(HaronConstants.TAG, "BT HID: connect error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val hid = hidDevice
        val device = connectedDevice
        val name = try { device?.name } catch (_: Exception) { device?.address }
        EcosystemLogger.d(HaronConstants.TAG, "BT HID: disconnect() called, device=$name, hidDevice=${hid != null}")
        if (hid != null && device != null) {
            try {
                hid.disconnect(device)
                EcosystemLogger.d(HaronConstants.TAG, "BT HID: disconnect() sent for $name")
            } catch (e: SecurityException) {
                EcosystemLogger.e(HaronConstants.TAG, "BT HID: disconnect security error: ${e.message}")
            }
        }
        connectedDevice = null
        _connectionState.value = HidConnectionState.Disconnected
    }

    fun sendMouseMove(dx: Float, dy: Float) {
        val hid = hidDevice
        val device = connectedDevice
        if (hid == null) { EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendMouseMove — hidDevice null"); return }
        if (device == null) { EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendMouseMove — connectedDevice null"); return }
        val clampedDx = dx.toInt().coerceIn(-127, 127).toByte()
        val clampedDy = dy.toInt().coerceIn(-127, 127).toByte()
        val report = byteArrayOf(0x00, clampedDx, clampedDy, 0x00)
        try {
            val ok = hid.sendReport(device, 2, report)
            if (!ok) EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendMouseMove — sendReport returned false")
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendMouseMove error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun sendMouseClick(button: Int = 0) {
        val hid = hidDevice
        val device = connectedDevice
        if (hid == null) { EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendMouseClick — hidDevice null"); return }
        if (device == null) { EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendMouseClick — connectedDevice null"); return }
        val buttonByte = (1 shl button).toByte()
        EcosystemLogger.d(HaronConstants.TAG, "BT HID: sendMouseClick button=$button (byte=$buttonByte)")
        scope.launch {
            try {
                val ok1 = hid.sendReport(device, 2, byteArrayOf(buttonByte, 0x00, 0x00, 0x00))
                delay(50)
                val ok2 = hid.sendReport(device, 2, byteArrayOf(0x00, 0x00, 0x00, 0x00))
                if (!ok1 || !ok2) EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendMouseClick — sendReport returned false (down=$ok1 up=$ok2)")
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendMouseClick error: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    fun sendScroll(dy: Float) {
        val hid = hidDevice
        val device = connectedDevice
        if (hid == null) { EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendScroll — hidDevice null"); return }
        if (device == null) { EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendScroll — connectedDevice null"); return }
        val clampedDy = dy.toInt().coerceIn(-127, 127).toByte()
        val report = byteArrayOf(0x00, 0x00, 0x00, clampedDy)
        try {
            val ok = hid.sendReport(device, 2, report)
            if (!ok) EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendScroll — sendReport returned false")
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendScroll error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun sendKeyPress(hidUsageCode: Int) {
        val hid = hidDevice
        val device = connectedDevice
        if (hid == null) { EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendKeyPress — hidDevice null"); return }
        if (device == null) { EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendKeyPress — connectedDevice null"); return }
        EcosystemLogger.d(HaronConstants.TAG, "BT HID: sendKeyPress code=0x${hidUsageCode.toString(16)}")
        scope.launch {
            keyboardMutex.withLock {
                try {
                    val h = hidDevice ?: return@withLock
                    val d = connectedDevice ?: return@withLock
                    val report = byteArrayOf(0x00, 0x00, hidUsageCode.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00)
                    val ok1 = h.sendReport(d, 1, report)
                    delay(30)
                    val ok2 = h.sendReport(d, 1, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                    delay(20)
                    if (!ok1 || !ok2) EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendKeyPress — sendReport returned false (down=$ok1 up=$ok2)")
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendKeyPress error: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
    }

    fun sendTextInput(text: String) {
        EcosystemLogger.d(HaronConstants.TAG, "BT HID: sendTextInput text='$text' (${text.length} chars)")
        scope.launch {
            keyboardMutex.withLock {
                for (char in text) {
                    val pair = charToHid(char)
                    if (pair == null) {
                        EcosystemLogger.d(HaronConstants.TAG, "BT HID: sendTextInput — no HID mapping for '$char' (${char.code})")
                        continue
                    }
                    val (modifier, usageCode) = pair
                    val h = hidDevice ?: run {
                        EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendTextInput — hidDevice became null mid-send")
                        return@withLock
                    }
                    val d = connectedDevice ?: run {
                        EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendTextInput — connectedDevice became null mid-send")
                        return@withLock
                    }
                    try {
                        val report = byteArrayOf(modifier, 0x00, usageCode, 0x00, 0x00, 0x00, 0x00, 0x00)
                        h.sendReport(d, 1, report)
                        delay(30)
                        h.sendReport(d, 1, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                        delay(20)
                    } catch (e: Exception) {
                        EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendTextInput error at '$char': ${e.javaClass.simpleName}: ${e.message}")
                        break
                    }
                }
            }
        }
    }

    fun sendRemoteInput(event: RemoteInputEvent) {
        EcosystemLogger.d(HaronConstants.TAG, "BT HID: sendRemoteInput event=${event.javaClass.simpleName}")
        when (event) {
            is RemoteInputEvent.MouseMove -> sendMouseMove(event.dx, event.dy)
            is RemoteInputEvent.MouseClick -> sendMouseClick(event.button)
            is RemoteInputEvent.Scroll -> sendScroll(event.dy)
            is RemoteInputEvent.KeyPress -> {
                if (event.char != null) {
                    sendTextInput(event.char.toString())
                } else {
                    val hidCode = androidKeyToHid(event.keyCode)
                    EcosystemLogger.d(HaronConstants.TAG, "BT HID: KeyPress keyCode=${event.keyCode} → hidCode=0x${hidCode.toString(16)}")
                    if (hidCode != 0) sendKeyPress(hidCode)
                    else EcosystemLogger.d(HaronConstants.TAG, "BT HID: no HID mapping for Android keyCode=${event.keyCode}")
                }
            }
            is RemoteInputEvent.TextInput -> sendTextInput(event.text)
            is RemoteInputEvent.ClearAll -> sendClearAll()
            else -> {
                EcosystemLogger.d(HaronConstants.TAG, "BT HID: ignoring media event ${event.javaClass.simpleName}")
            }
        }
    }

    /** Convert char to (modifier, HID usage code).
     *  Supports ASCII + Cyrillic (ЙЦУКЕН → QWERTY physical key mapping).
     *  Tracks language mode to send punctuation via correct layout positions. */
    private fun charToHid(char: Char): Pair<Byte, Byte>? {
        // Track mode: Cyrillic → Russian layout, Latin → English layout
        if (char in 'а'..'я' || char in 'А'..'Я' || char == 'ё' || char == 'Ё') {
            isRussianMode = true
        } else if (char in 'a'..'z' || char in 'A'..'Z') {
            isRussianMode = false
        }

        // Check Cyrillic first (ЙЦУКЕН layout → QWERTY physical key positions)
        val cyrillicResult = cyrillicToHid(char)
        if (cyrillicResult != null) return cyrillicResult

        // Punctuation in Russian mode — use Russian layout key positions
        if (isRussianMode) {
            val ruPunct = russianPunctuationToHid(char)
            if (ruPunct != null) return ruPunct
        }

        val isUpper = char.isUpperCase()
        val modifier: Byte = if (isUpper) 0x02 else 0x00 // Left Shift
        val lower = char.lowercaseChar()
        val usageCode: Byte? = when (lower) {
            in 'a'..'z' -> (0x04 + (lower - 'a')).toByte()
            in '1'..'9' -> (0x1E + (lower - '1')).toByte()
            '0' -> 0x27.toByte()
            '\n' -> return Pair(0x00, 0x28.toByte()) // Enter
            '\t' -> return Pair(0x00, 0x2B.toByte()) // Tab
            ' ' -> return Pair(0x00, 0x2C.toByte()) // Space
            '-' -> return Pair(0x00, 0x2D.toByte())
            '=' -> return Pair(0x00, 0x2E.toByte())
            '[' -> return Pair(0x00, 0x2F.toByte())
            ']' -> return Pair(0x00, 0x30.toByte())
            '\\' -> return Pair(0x00, 0x31.toByte())
            ';' -> return Pair(0x00, 0x33.toByte())
            '\'' -> return Pair(0x00, 0x34.toByte())
            '`' -> return Pair(0x00, 0x35.toByte())
            ',' -> return Pair(0x00, 0x36.toByte())
            '.' -> return Pair(0x00, 0x37.toByte())
            '/' -> return Pair(0x00, 0x38.toByte())
            // Shifted symbols
            '!' -> return Pair(0x02, 0x1E.toByte())
            '@' -> return Pair(0x02, 0x1F.toByte())
            '#' -> return Pair(0x02, 0x20.toByte())
            '$' -> return Pair(0x02, 0x21.toByte())
            '%' -> return Pair(0x02, 0x22.toByte())
            '^' -> return Pair(0x02, 0x23.toByte())
            '&' -> return Pair(0x02, 0x24.toByte())
            '*' -> return Pair(0x02, 0x25.toByte())
            '(' -> return Pair(0x02, 0x26.toByte())
            ')' -> return Pair(0x02, 0x27.toByte())
            '_' -> return Pair(0x02, 0x2D.toByte())
            '+' -> return Pair(0x02, 0x2E.toByte())
            '{' -> return Pair(0x02, 0x2F.toByte())
            '}' -> return Pair(0x02, 0x30.toByte())
            '|' -> return Pair(0x02, 0x31.toByte())
            ':' -> return Pair(0x02, 0x33.toByte())
            '"' -> return Pair(0x02, 0x34.toByte())
            '~' -> return Pair(0x02, 0x35.toByte())
            '<' -> return Pair(0x02, 0x36.toByte())
            '>' -> return Pair(0x02, 0x37.toByte())
            '?' -> return Pair(0x02, 0x38.toByte())
            else -> null
        }
        return if (usageCode != null) Pair(modifier, usageCode) else null
    }

    /** Map Cyrillic char (ЙЦУКЕН) to HID scan code via QWERTY physical key position.
     *  Host must have Russian layout active for correct output. */
    private fun cyrillicToHid(char: Char): Pair<Byte, Byte>? {
        val isUpper = char.isUpperCase()
        val modifier: Byte = if (isUpper) 0x02 else 0x00
        val lower = char.lowercaseChar()
        val hid: Byte = when (lower) {
            'й' -> 0x14 // q
            'ц' -> 0x1A // w
            'у' -> 0x08 // e
            'к' -> 0x15 // r
            'е' -> 0x17 // t
            'н' -> 0x1C // y
            'г' -> 0x18 // u
            'ш' -> 0x0C // i
            'щ' -> 0x12 // o
            'з' -> 0x13 // p
            'х' -> 0x2F // [
            'ъ' -> 0x30 // ]
            'ф' -> 0x04 // a
            'ы' -> 0x16 // s
            'в' -> 0x07 // d
            'а' -> 0x09 // f
            'п' -> 0x0A // g
            'р' -> 0x0B // h
            'о' -> 0x0D // j
            'л' -> 0x0E // k
            'д' -> 0x0F // l
            'ж' -> 0x33 // ;
            'э' -> 0x34 // '
            'я' -> 0x1D // z
            'ч' -> 0x1B // x
            'с' -> 0x06 // c
            'м' -> 0x19 // v
            'и' -> 0x05 // b
            'т' -> 0x11 // n
            'ь' -> 0x10 // m
            'б' -> 0x36 // ,
            'ю' -> 0x37 // .
            'ё' -> 0x35 // `
            else -> return null
        }
        return Pair(modifier, hid)
    }

    /** Send Ctrl+A (select all) then Backspace to clear all text on remote */
    private fun sendClearAll() {
        val hid = hidDevice
        val device = connectedDevice
        if (hid == null || device == null) return
        EcosystemLogger.d(HaronConstants.TAG, "BT HID: sendClearAll — Ctrl+A + Backspace")
        scope.launch {
            keyboardMutex.withLock {
                try {
                    val h = hidDevice ?: return@withLock
                    val d = connectedDevice ?: return@withLock
                    // Ctrl+A (select all): modifier=0x01 (Left Ctrl), key=0x04 (a)
                    h.sendReport(d, 1, byteArrayOf(0x01, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00))
                    delay(50)
                    h.sendReport(d, 1, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                    delay(30)
                    // Backspace
                    h.sendReport(d, 1, byteArrayOf(0x00, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x00, 0x00))
                    delay(30)
                    h.sendReport(d, 1, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendClearAll error: ${e.message}")
                }
            }
        }
    }

    /** Map punctuation to Russian keyboard layout key positions.
     *  On Russian layout: . = / key, , = Shift+/ key, ? = Shift+7, etc. */
    private fun russianPunctuationToHid(char: Char): Pair<Byte, Byte>? {
        return when (char) {
            '.' -> Pair(0x00, 0x38)       // / key = . in Russian
            ',' -> Pair(0x02, 0x38)       // Shift + / key = , in Russian
            '?' -> Pair(0x02, 0x24)       // Shift + 7 = ? in Russian
            '!' -> Pair(0x02, 0x1E)       // Shift + 1 = ! in Russian
            ':' -> Pair(0x02, 0x23)       // Shift + 6 = : in Russian
            ';' -> Pair(0x02, 0x21)       // Shift + 4 = ; in Russian
            '"' -> Pair(0x02, 0x1F)       // Shift + 2 = " in Russian
            '№' -> Pair(0x02, 0x20)       // Shift + 3 = № in Russian
            '%' -> Pair(0x02, 0x22)       // Shift + 5 = % (same)
            '*' -> Pair(0x02, 0x25)       // Shift + 8 = * (same)
            '(' -> Pair(0x02, 0x26)       // Shift + 9 = (
            ')' -> Pair(0x02, 0x27)       // Shift + 0 = )
            '-' -> Pair(0x00, 0x2D)       // - key (same in both)
            '=' -> Pair(0x00, 0x2E)       // = key (same in both)
            ' ' -> Pair(0x00, 0x2C)       // Space
            '\n' -> Pair(0x00, 0x28)      // Enter
            '\t' -> Pair(0x00, 0x2B)      // Tab
            else -> null
        }
    }

    /** Convert Android KeyEvent keyCode to HID Usage Code */
    private fun androidKeyToHid(keyCode: Int): Int {
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_ENTER -> 0x28
            android.view.KeyEvent.KEYCODE_ESCAPE, android.view.KeyEvent.KEYCODE_BACK -> 0x29
            android.view.KeyEvent.KEYCODE_DEL -> 0x2A // Backspace
            android.view.KeyEvent.KEYCODE_TAB -> 0x2B
            android.view.KeyEvent.KEYCODE_SPACE -> 0x2C
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> 0x4F
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> 0x50
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> 0x51
            android.view.KeyEvent.KEYCODE_DPAD_UP -> 0x52
            android.view.KeyEvent.KEYCODE_HOME -> 0x4A // Home
            android.view.KeyEvent.KEYCODE_FORWARD_DEL -> 0x4C // Delete
            android.view.KeyEvent.KEYCODE_PAGE_UP -> 0x4B
            android.view.KeyEvent.KEYCODE_PAGE_DOWN -> 0x4E
            in android.view.KeyEvent.KEYCODE_A..android.view.KeyEvent.KEYCODE_Z ->
                0x04 + (keyCode - android.view.KeyEvent.KEYCODE_A)
            in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> {
                if (keyCode == android.view.KeyEvent.KEYCODE_0) 0x27
                else 0x1E + (keyCode - android.view.KeyEvent.KEYCODE_1)
            }
            else -> 0
        }
    }
}
