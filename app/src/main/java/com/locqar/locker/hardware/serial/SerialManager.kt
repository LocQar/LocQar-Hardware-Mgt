package com.locqar.locker.hardware.serial

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.locqar.locker.hardware.codec.WinnsenCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/**
 * Manages USB-to-RS485 serial connection lifecycle.
 * Handles permission requests, connect/disconnect, and raw byte I/O.
 */
class SerialManager(private val context: Context) {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.locqar.locker.USB_PERMISSION"
        private const val BAUD_RATE = 9600
        private const val DATA_BITS = 8
        private const val READ_TIMEOUT_MS = 1000
        private const val WRITE_TIMEOUT_MS = 1000
    }

    enum class ConnectionState {
        DISCONNECTED,
        REQUESTING_PERMISSION,
        CONNECTED,
        ERROR
    }

    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var serialPort: UsbSerialPort? = null
    private var connection: UsbDeviceConnection? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private var permissionCallback: ((Boolean) -> Unit)? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    permissionCallback?.invoke(granted)
                    permissionCallback = null
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    disconnect()
                    _errorMessage.value = "USB device detached"
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
    }

    /**
     * Find available USB serial devices.
     */
    fun findDevices(): List<UsbSerialDriver> {
        return UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
    }

    /**
     * Connect to the first available USB serial device (or a specific one).
     * @return true if connected successfully
     */
    fun connect(driver: UsbSerialDriver? = null): Boolean {
        try {
            val selectedDriver = driver ?: findDevices().firstOrNull()
            if (selectedDriver == null) {
                _errorMessage.value = "No USB serial device found"
                _connectionState.value = ConnectionState.ERROR
                return false
            }

            val device = selectedDriver.device
            if (!usbManager.hasPermission(device)) {
                requestPermission(device)
                return false
            }

            val conn = usbManager.openDevice(device) ?: run {
                _errorMessage.value = "Failed to open USB device"
                _connectionState.value = ConnectionState.ERROR
                return false
            }

            val port = selectedDriver.ports.firstOrNull() ?: run {
                conn.close()
                _errorMessage.value = "No serial ports on device"
                _connectionState.value = ConnectionState.ERROR
                return false
            }

            port.open(conn)
            port.setParameters(
                BAUD_RATE,
                DATA_BITS,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )

            this.connection = conn
            this.serialPort = port
            _connectedDeviceName.value = "${device.manufacturerName ?: "Unknown"} ${device.productName ?: device.deviceName}"
            _connectionState.value = ConnectionState.CONNECTED
            _errorMessage.value = null
            return true
        } catch (e: Exception) {
            _errorMessage.value = "Connect error: ${e.message}"
            _connectionState.value = ConnectionState.ERROR
            return false
        }
    }

    /**
     * Disconnect the serial port.
     */
    fun disconnect() {
        try {
            serialPort?.close()
        } catch (_: IOException) { }
        try {
            connection?.close()
        } catch (_: Exception) { }
        serialPort = null
        connection = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
    }

    /**
     * Send raw bytes and read response.
     * @param txData Bytes to transmit
     * @param expectedResponseLen Expected number of response bytes
     * @return Response bytes, or null on timeout/error
     */
    @Throws(IOException::class, SerialNotConnectedException::class)
    fun sendAndReceive(txData: ByteArray, expectedResponseLen: Int): ByteArray? {
        val port = serialPort ?: throw SerialNotConnectedException()

        // Write
        port.write(txData, WRITE_TIMEOUT_MS)

        // Read with accumulation (RS485 may deliver bytes in chunks)
        val buffer = ByteArray(256)
        val result = ByteArray(expectedResponseLen)
        var totalRead = 0
        val deadline = System.currentTimeMillis() + READ_TIMEOUT_MS

        while (totalRead < expectedResponseLen && System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(1)
            val len = port.read(buffer, remaining.coerceAtMost(READ_TIMEOUT_MS))
            if (len > 0) {
                val copyLen = minOf(len, expectedResponseLen - totalRead)
                System.arraycopy(buffer, 0, result, totalRead, copyLen)
                totalRead += copyLen
            }
        }

        return if (totalRead >= expectedResponseLen) result else null
    }

    val isConnected: Boolean
        get() = _connectionState.value == ConnectionState.CONNECTED && serialPort != null

    private fun requestPermission(device: UsbDevice) {
        _connectionState.value = ConnectionState.REQUESTING_PERMISSION
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val pi = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)

        permissionCallback = { granted ->
            if (granted) {
                connect()
            } else {
                _errorMessage.value = "USB permission denied"
                _connectionState.value = ConnectionState.ERROR
            }
        }
        usbManager.requestPermission(device, pi)
    }

    fun destroy() {
        disconnect()
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Exception) { }
    }
}

class SerialNotConnectedException : Exception("Serial port not connected")
