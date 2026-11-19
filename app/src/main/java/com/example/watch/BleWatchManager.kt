package com.example.watch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

private const val TAG = "BleWatchManager"

class BleWatchManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    private var scanner: BluetoothLeScanner? = null
    private var activeGatt: BluetoothGatt? = null
    private var isScanning = false

    private val _connectionStatus = MutableStateFlow(
        if (!hasBleHardware()) BleConnectionStatus.UNAVAILABLE else BleConnectionStatus.DISCONNECTED
    )
    val connectionStatus: StateFlow<BleConnectionStatus> = _connectionStatus.asStateFlow()

    private val _discoveredWatches = MutableStateFlow<List<BleWatchDevice>>(emptyList())
    val discoveredWatches: StateFlow<List<BleWatchDevice>> = _discoveredWatches.asStateFlow()

    private val _connectedWatchName = MutableStateFlow<String?>(null)
    val connectedWatchName: StateFlow<String?> = _connectedWatchName.asStateFlow()

    private val _connectedWatchAddress = MutableStateFlow<String?>(null)
    val connectedWatchAddress: StateFlow<String?> = _connectedWatchAddress.asStateFlow()

    private val _liveHeartRateBpm = MutableStateFlow<Int?>(null)
    val liveHeartRateBpm: StateFlow<Int?> = _liveHeartRateBpm.asStateFlow()

    private val _batteryPercent = MutableStateFlow<Int?>(null)
    val batteryPercent: StateFlow<Int?> = _batteryPercent.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun hasBleHardware(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
            bluetoothAdapter != null

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasBleHardware()) {
            _connectionStatus.value = BleConnectionStatus.UNAVAILABLE
            _statusMessage.value = "Bluetooth Low Energy hardware is not available on this device."
            return
        }
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            _connectionStatus.value = BleConnectionStatus.BLUETOOTH_OFF
            _statusMessage.value = "Turn on Bluetooth to scan for nearby smartwatches."
            return
        }
        if (!hasRequiredPermissions()) {
            _statusMessage.value = "Grant Nearby Devices permission to scan for smartwatches."
            return
        }

        val bleScanner = adapter.bluetoothLeScanner
        if (bleScanner == null) {
            _connectionStatus.value = BleConnectionStatus.UNAVAILABLE
            _statusMessage.value = "BLE scanner is unavailable right now."
            return
        }
        scanner = bleScanner

        stopScanInternal()
        _discoveredWatches.value = emptyList()
        _connectionStatus.value = BleConnectionStatus.SCANNING
        _statusMessage.value = "Scanning for BLE smartwatches & heart rate monitors..."
        isScanning = true

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bleScanner.startScan(null, settings, scanCallback)
            mainHandler.postDelayed({
                if (isScanning) {
                    stopScanInternal()
                    if (_connectionStatus.value == BleConnectionStatus.SCANNING) {
                        _connectionStatus.value = BleConnectionStatus.DISCONNECTED
                        _statusMessage.value = if (_discoveredWatches.value.isEmpty()) {
                            "No nearby BLE smartwatches found. Ensure watch pairing mode is on or sync via Health Connect."
                        } else {
                            "Found ${_discoveredWatches.value.size} device(s). Tap to connect."
                        }
                    }
                }
            }, 12_000L)
        } catch (e: SecurityException) {
            Log.e(TAG, "BLE scan permission error", e)
            isScanning = false
            _connectionStatus.value = BleConnectionStatus.ERROR
            _statusMessage.value = "Bluetooth scan permission denied."
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        stopScanInternal()
        if (_connectionStatus.value == BleConnectionStatus.SCANNING) {
            _connectionStatus.value = BleConnectionStatus.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanInternal() {
        if (!isScanning) return
        isScanning = false
        try {
            if (hasRequiredPermissions()) {
                scanner?.stopScan(scanCallback)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping BLE scan", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToWatch(deviceAddress: String) {
        if (!hasBleHardware() || !hasRequiredPermissions()) {
            _statusMessage.value = "Missing Bluetooth permission or hardware."
            return
        }
        stopScanInternal()
        disconnect()

        val adapter = bluetoothAdapter ?: return
        try {
            val device = adapter.getRemoteDevice(deviceAddress)
            _connectionStatus.value = BleConnectionStatus.CONNECTING
            _connectedWatchName.value = device.name ?: "BLE Watch"
            _connectedWatchAddress.value = deviceAddress
            _statusMessage.value = "Connecting to ${device.name ?: deviceAddress}..."
            activeGatt = device.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect GATT", e)
            _connectionStatus.value = BleConnectionStatus.ERROR
            _statusMessage.value = e.localizedMessage ?: "Failed to connect to watch."
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            if (hasRequiredPermissions()) {
                activeGatt?.disconnect()
                activeGatt?.close()
            } else {
                activeGatt?.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error closing GATT", e)
        } finally {
            activeGatt = null
            _liveHeartRateBpm.value = null
            _batteryPercent.value = null
            _connectedWatchName.value = null
            _connectedWatchAddress.value = null
            if (_connectionStatus.value == BleConnectionStatus.CONNECTED ||
                _connectionStatus.value == BleConnectionStatus.CONNECTING
            ) {
                _connectionStatus.value = BleConnectionStatus.DISCONNECTED
                _statusMessage.value = "Smartwatch disconnected."
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val deviceName = try {
                device.name ?: result.scanRecord?.deviceName
            } catch (_: SecurityException) {
                null
            }
            if (deviceName.isNullOrBlank()) return

            val entry = BleWatchDevice(
                name = deviceName,
                address = device.address,
                rssi = result.rssi
            )
            val current = _discoveredWatches.value.toMutableList()
            val idx = current.indexOfFirst { it.address == entry.address }
            if (idx >= 0) {
                current[idx] = entry
            } else {
                current.add(entry)
            }
            _discoveredWatches.value = current.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            _connectionStatus.value = BleConnectionStatus.ERROR
            _statusMessage.value = "BLE scan failed (code $errorCode)."
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS && newState != BluetoothProfile.STATE_CONNECTED) {
                _connectionStatus.value = BleConnectionStatus.ERROR
                _statusMessage.value = "Watch connection dropped (status $status)."
                gatt.close()
                if (activeGatt == gatt) activeGatt = null
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionStatus.value = BleConnectionStatus.CONNECTED
                _statusMessage.value = "Connected! Reading watch services..."
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionStatus.value = BleConnectionStatus.DISCONNECTED
                _liveHeartRateBpm.value = null
                _batteryPercent.value = null
                _statusMessage.value = "Watch disconnected."
                gatt.close()
                if (activeGatt == gatt) activeGatt = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            val batteryChar = gatt.getService(BATTERY_SERVICE_UUID)
                ?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
            if (batteryChar != null) {
                gatt.readCharacteristic(batteryChar)
            }

            val hrChar = gatt.getService(HEART_RATE_SERVICE_UUID)
                ?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
            if (hrChar != null) {
                gatt.setCharacteristicNotification(hrChar, true)
                val descriptor = hrChar.getDescriptor(CCCD_UUID)
                if (descriptor != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(descriptor)
                    }
                }
                _statusMessage.value = "Receiving live heart rate & battery from watch."
            } else {
                _statusMessage.value = "Connected to watch. Use Health Connect sync for steps & sleep."
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristicBytes(characteristic.uuid, value)
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                val bytes = characteristic.value ?: return
                handleCharacteristicBytes(characteristic.uuid, bytes)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicBytes(characteristic.uuid, value)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                val bytes = characteristic.value ?: return
                handleCharacteristicBytes(characteristic.uuid, bytes)
            }
        }
    }

    private fun handleCharacteristicBytes(uuid: UUID, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        when (uuid) {
            BATTERY_LEVEL_CHAR_UUID -> {
                val pct = bytes[0].toInt() and 0xFF
                _batteryPercent.value = pct.coerceIn(0, 100)
            }
            HEART_RATE_MEASUREMENT_UUID -> {
                val flags = bytes[0].toInt() and 0xFF
                val isUint16 = (flags and 0x01) != 0
                val bpm = if (isUint16 && bytes.size >= 3) {
                    ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
                } else if (bytes.size >= 2) {
                    bytes[1].toInt() and 0xFF
                } else {
                    return
                }
                if (bpm in 25..240) {
                    _liveHeartRateBpm.value = bpm
                }
            }
        }
    }

    companion object {
        private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val HEART_RATE_MEASUREMENT_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_CHAR_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
