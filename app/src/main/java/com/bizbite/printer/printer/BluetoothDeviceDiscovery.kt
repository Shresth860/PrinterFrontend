package com.bizbite.printer.printer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.bizbite.printer.domain.model.ConnectionType

class BluetoothDeviceDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private var receiverRegistered = false
    private var scanCallback: ScanCallback? = null
    private var onDevice: ((BluetoothDevice, ConnectionType) -> Unit)? = null
    private var onFinished: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var classicStarted = false
    private var bleStarted = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { onDevice?.invoke(it, ConnectionType.BLUETOOTH) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    classicStarted = false
                    if (!bleStarted) onFinished?.invoke()
                }
            }
        }
    }

    fun bondedDevices(): List<BluetoothDevice> {
        requireConnectPermission()
        return adapter?.bondedDevices?.toList().orEmpty()
    }

    fun bondedTransports(device: BluetoothDevice): Set<ConnectionType> {
        requireConnectPermission()
        return when (device.type) {
            BluetoothDevice.DEVICE_TYPE_LE -> setOf(ConnectionType.BLE)
            BluetoothDevice.DEVICE_TYPE_DUAL -> setOf(ConnectionType.BLUETOOTH, ConnectionType.BLE)
            else -> setOf(ConnectionType.BLUETOOTH)
        }
    }

    fun isBluetoothEnabled(): Boolean {
        requireConnectPermission()
        return adapter?.isEnabled == true
    }

    fun start(
        onDevice: (BluetoothDevice, ConnectionType) -> Unit,
        onFinished: () -> Unit,
        onError: (String) -> Unit
    ) {
        requireScanPermissions()
        val activeAdapter = adapter ?: run {
            onError("This device does not support Bluetooth.")
            return
        }
        if (!activeAdapter.isEnabled) {
            onError("Bluetooth is turned off. Turn it on and try again.")
            return
        }
        stop()
        this.onDevice = onDevice
        this.onFinished = onFinished
        this.onError = onError
        try {
            if (!receiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    appContext.registerReceiver(
                        receiver,
                        filter,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            android.Manifest.permission.BLUETOOTH_SCAN
                        } else {
                            android.Manifest.permission.BLUETOOTH
                        },
                        null,
                        Context.RECEIVER_EXPORTED
                    )
                } else {
                    ContextCompat.registerReceiver(
                        appContext,
                        receiver,
                        filter,
                        android.Manifest.permission.BLUETOOTH,
                        null,
                        ContextCompat.RECEIVER_EXPORTED
                    )
                }
                receiverRegistered = true
            }
            classicStarted = activeAdapter.isDiscovering || activeAdapter.startDiscovery()
            val scanner = activeAdapter.bluetoothLeScanner
            if (scanner != null) {
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        onDevice(result.device, ConnectionType.BLE)
                    }

                    override fun onBatchScanResults(results: MutableList<ScanResult>) {
                        results.forEach { onDevice(it.device, ConnectionType.BLE) }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        bleStarted = false
                        this@BluetoothDeviceDiscovery.onError?.invoke(
                            "BLE scan failed (error $errorCode). Classic discovery will continue."
                        )
                        if (!classicStarted) this@BluetoothDeviceDiscovery.onFinished?.invoke()
                    }
                }
                scanCallback = callback
                bleStarted = true
                scanner.startScan(callback)
            } else if (!classicStarted) {
                onError("Bluetooth scanning is unavailable on this device.")
                onFinished()
            }
        } catch (failure: SecurityException) {
            stop()
            onError("Bluetooth permission was denied. Allow Nearby devices access in Android Settings.")
        } catch (failure: Exception) {
            stop()
            onError(failure.message ?: "Bluetooth scanning could not be started.")
        }
    }

    fun stop(notifyFinished: Boolean = true) {
        val activeAdapter = adapter
        val callback = scanCallback
        if (callback != null) {
            try {
                activeAdapter?.bluetoothLeScanner?.stopScan(callback)
            } catch (_: SecurityException) {
                onError?.invoke("Bluetooth permission was revoked; scanning has stopped.")
            } catch (_: IllegalStateException) {
                onError?.invoke("Bluetooth scanning had already stopped.")
            } catch (_: IllegalArgumentException) {
                onError?.invoke("Bluetooth scan callback was already stopped.")
            }
        }
        scanCallback = null
        bleStarted = false
        if (classicStarted) {
            try {
                activeAdapter?.cancelDiscovery()
            } catch (_: SecurityException) {
                onError?.invoke("Bluetooth permission was revoked; discovery has stopped.")
            }
        }
        classicStarted = false
        if (receiverRegistered) {
            try {
                appContext.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                onError?.invoke("Bluetooth discovery receiver was already released.")
            }
            receiverRegistered = false
        }
        if (notifyFinished) onFinished?.invoke()
        onDevice = null
        onFinished = null
        onError = null
    }

    private fun requireScanPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.Manifest.permission.BLUETOOTH_SCAN
        } else {
            android.Manifest.permission.ACCESS_FINE_LOCATION
        }
        check(
            ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
        ) { "Bluetooth scan permission is required." }
        requireConnectPermission()
    }

    private fun requireConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            check(
                ContextCompat.checkSelfPermission(
                    appContext, android.Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) { "Nearby devices permission is required." }
        }
    }
}
