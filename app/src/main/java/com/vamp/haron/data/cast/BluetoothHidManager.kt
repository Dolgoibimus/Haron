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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return adapter != null
    }

    /** Returns true when HID profile proxy is connected and app is registered */
    fun isReady(): Boolean = hidDevice != null && isRegistered

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        if (adapter == null) return
        try {
            _pairedDevices.value = adapter.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            EcosystemLogger.e(HaronConstants.TAG, "BT HID: refreshPairedDevices security error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun init() {
        if (!isSupported()) {
            _connectionState.value = HidConnectionState.NotSupported
            return
        }
        if (hidDevice != null) return

        try {
            adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    hidDevice = proxy as? BluetoothHidDevice
                    EcosystemLogger.d(HaronConstants.TAG, "BT HID: profile proxy connected")
                    registerApp()
                }

                override fun onServiceDisconnected(profile: Int) {
                    hidDevice = null
                    isRegistered = false
                    EcosystemLogger.d(HaronConstants.TAG, "BT HID: profile proxy disconnected")
                }
            }, BluetoothProfile.HID_DEVICE)
        } catch (e: SecurityException) {
            EcosystemLogger.e(HaronConstants.TAG, "BT HID: init security error: ${e.message}")
            _connectionState.value = HidConnectionState.Error(e.message ?: "Security error")
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val hid = hidDevice ?: return
        if (isRegistered) return

        try {
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
                    EcosystemLogger.d(HaronConstants.TAG, "BT HID: app registered=$registered")
                }

                @SuppressLint("MissingPermission")
                override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            connectedDevice = device
                            val name = try { device?.name } catch (_: SecurityException) { null } ?: device?.address ?: "Unknown"
                            _connectionState.value = HidConnectionState.Connected(name)
                            EcosystemLogger.d(HaronConstants.TAG, "BT HID: connected to $name")
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            connectedDevice = null
                            _connectionState.value = HidConnectionState.Disconnected
                            EcosystemLogger.d(HaronConstants.TAG, "BT HID: disconnected")
                        }
                    }
                }
            }

            hid.registerApp(sdp, null, null, { it.run() }, callback)
        } catch (e: SecurityException) {
            EcosystemLogger.e(HaronConstants.TAG, "BT HID: registerApp security error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        val hid = hidDevice
        if (hid == null || !isRegistered) {
            _connectionState.value = HidConnectionState.Error("HID not ready")
            return
        }
        _connectionState.value = HidConnectionState.Connecting
        try {
            val result = hid.connect(device)
            if (!result) {
                _connectionState.value = HidConnectionState.Error("Connection failed")
                EcosystemLogger.e(HaronConstants.TAG, "BT HID: connect() returned false")
            }
        } catch (e: SecurityException) {
            _connectionState.value = HidConnectionState.Error(e.message ?: "Security error")
            EcosystemLogger.e(HaronConstants.TAG, "BT HID: connect security error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val hid = hidDevice
        val device = connectedDevice
        if (hid != null && device != null) {
            try {
                hid.disconnect(device)
            } catch (e: SecurityException) {
                EcosystemLogger.e(HaronConstants.TAG, "BT HID: disconnect security error: ${e.message}")
            }
        }
        connectedDevice = null
        _connectionState.value = HidConnectionState.Disconnected
    }

    fun sendMouseMove(dx: Float, dy: Float) {
        val hid = hidDevice ?: return
        val device = connectedDevice ?: return
        val clampedDx = dx.toInt().coerceIn(-127, 127).toByte()
        val clampedDy = dy.toInt().coerceIn(-127, 127).toByte()
        // Report ID 2: buttons(0) + dx + dy + wheel(0)
        val report = byteArrayOf(0x00, clampedDx, clampedDy, 0x00)
        try {
            hid.sendReport(device, 2, report)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendMouseMove error: ${e.message}")
        }
    }

    fun sendMouseClick(button: Int = 0) {
        val hid = hidDevice ?: return
        val device = connectedDevice ?: return
        val buttonByte = (1 shl button).toByte()
        scope.launch {
            try {
                // Button down
                hid.sendReport(device, 2, byteArrayOf(buttonByte, 0x00, 0x00, 0x00))
                delay(50)
                // Button up
                hid.sendReport(device, 2, byteArrayOf(0x00, 0x00, 0x00, 0x00))
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendMouseClick error: ${e.message}")
            }
        }
    }

    fun sendScroll(dy: Float) {
        val hid = hidDevice ?: return
        val device = connectedDevice ?: return
        val clampedDy = dy.toInt().coerceIn(-127, 127).toByte()
        // Report ID 2: buttons(0) + dx(0) + dy(0) + wheel
        val report = byteArrayOf(0x00, 0x00, 0x00, clampedDy)
        try {
            hid.sendReport(device, 2, report)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendScroll error: ${e.message}")
        }
    }

    fun sendKeyPress(hidUsageCode: Int) {
        val hid = hidDevice ?: return
        val device = connectedDevice ?: return
        scope.launch {
            try {
                // Key down: modifier(0) + reserved(0) + keycode + padding
                val report = byteArrayOf(0x00, 0x00, hidUsageCode.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00)
                hid.sendReport(device, 1, report)
                delay(30)
                // Key up: all zeros
                hid.sendReport(device, 1, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendKeyPress error: ${e.message}")
            }
        }
    }

    fun sendTextInput(text: String) {
        scope.launch {
            for (char in text) {
                val (modifier, usageCode) = charToHid(char) ?: continue
                val hid = hidDevice ?: return@launch
                val device = connectedDevice ?: return@launch
                try {
                    val report = byteArrayOf(modifier, 0x00, usageCode, 0x00, 0x00, 0x00, 0x00, 0x00)
                    hid.sendReport(device, 1, report)
                    delay(30)
                    hid.sendReport(device, 1, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                    delay(20)
                } catch (e: Exception) {
                    EcosystemLogger.e(HaronConstants.TAG, "BT HID: sendTextInput error: ${e.message}")
                    break
                }
            }
        }
    }

    fun sendRemoteInput(event: RemoteInputEvent) {
        when (event) {
            is RemoteInputEvent.MouseMove -> sendMouseMove(event.dx, event.dy)
            is RemoteInputEvent.MouseClick -> sendMouseClick(event.button)
            is RemoteInputEvent.Scroll -> sendScroll(event.dy)
            is RemoteInputEvent.KeyPress -> {
                if (event.char != null) {
                    sendTextInput(event.char.toString())
                } else {
                    val hidCode = androidKeyToHid(event.keyCode)
                    if (hidCode != 0) sendKeyPress(hidCode)
                }
            }
            is RemoteInputEvent.TextInput -> sendTextInput(event.text)
            else -> { /* media events not handled by HID */ }
        }
    }

    /** Convert ASCII char to (modifier, HID usage code) */
    private fun charToHid(char: Char): Pair<Byte, Byte>? {
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
