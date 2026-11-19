package com.bizbite.printer.printer

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BluetoothLeConnection(
    private val context: Context,
    private val device: BluetoothDevice
) : PrinterConnection {
    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var output: BluetoothGattCharacteristic? = null
    @Volatile private var pendingWrite: ((Result<Unit>) -> Unit)? = null

    override val isConnected: Boolean
        get() = gatt != null && output != null

    override suspend fun connect() {
        if (isConnected) return
        requireConnectPermission()
        try {
            withTimeout(CONNECT_TIMEOUT_MS) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val callback = object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(
                            bluetoothGatt: BluetoothGatt,
                            status: Int,
                            newState: Int
                        ) {
                            if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                                val failure = IOException(
                                    if (status != BluetoothGatt.GATT_SUCCESS) "BLE connection failed (status $status)."
                                    else "BLE printer disconnected."
                                )
                                if (gatt === bluetoothGatt) {
                                    gatt = null
                                    output = null
                                }
                                val write = pendingWrite
                                pendingWrite = null
                                write?.invoke(Result.failure(failure))
                                if (continuation.isActive) {
                                    continuation.resumeWithException(failure)
                                }
                                bluetoothGatt.close()
                                return
                            }
                            when (newState) {
                                BluetoothProfile.STATE_CONNECTED -> {
                                    if (!bluetoothGatt.discoverServices() && continuation.isActive) {
                                        continuation.resumeWithException(IOException("Could not discover BLE printer services."))
                                    }
                                }
                            }
                        }

                        override fun onServicesDiscovered(bluetoothGatt: BluetoothGatt, status: Int) {
                            if (!continuation.isActive) return
                            if (status != BluetoothGatt.GATT_SUCCESS) {
                                continuation.resumeWithException(IOException("Could not read BLE printer services."))
                                return
                            }
                            val writable = bluetoothGatt.services
                                .asSequence()
                                .flatMap { it.characteristics.asSequence() }
                                .firstOrNull {
                                    it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                                        it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
                                }
                            if (writable == null) {
                                continuation.resumeWithException(IOException("BLE device has no writable printer characteristic."))
                                return
                            }
                            gatt = bluetoothGatt
                            output = writable
                            continuation.resume(Unit)
                        }

                        override fun onCharacteristicWrite(
                            bluetoothGatt: BluetoothGatt,
                            characteristic: BluetoothGattCharacteristic,
                            status: Int
                        ) {
                            val complete = pendingWrite ?: return
                            pendingWrite = null
                            if (status == BluetoothGatt.GATT_SUCCESS) complete(Result.success(Unit))
                            else complete(Result.failure(IOException("BLE printer rejected data (status $status).")))
                        }
                    }

                    try {
                        val active = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                        } else {
                            @Suppress("DEPRECATION")
                            device.connectGatt(context, false, callback)
                        } ?: throw IOException("Could not start the BLE connection.")
                        gatt = active
                        continuation.invokeOnCancellation { closeGatt(active) }
                    } catch (failure: SecurityException) {
                        continuation.resumeWithException(failure)
                    } catch (failure: Exception) {
                        continuation.resumeWithException(failure)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            disconnect()
            throw cancelled
        } catch (failure: Exception) {
            disconnect()
            throw failure
        }
    }

    override suspend fun disconnect() {
        val active = gatt
        gatt = null
        output = null
        pendingWrite?.invoke(Result.failure(IOException("BLE printer disconnected.")))
        pendingWrite = null
        if (active != null) closeGatt(active)
    }

    override suspend fun write(bytes: ByteArray) {
        val active = gatt ?: throw IOException("BLE printer is disconnected.")
        val characteristic = output ?: throw IOException("BLE printer has no writable characteristic.")
        requireConnectPermission()
        val supportsResponse =
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        val writeType = if (supportsResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        var offset = 0
        var transferStarted = false
        try {
            while (offset < bytes.size) {
                val chunk = bytes.copyOfRange(offset, minOf(offset + MAX_CHUNK_BYTES, bytes.size))
                if (supportsResponse) {
                    withTimeout(WRITE_ACK_TIMEOUT_MS) {
                        suspendCancellableCoroutine<Unit> { continuation ->
                            pendingWrite = { result ->
                                if (continuation.isActive) {
                                    result.fold(
                                        onSuccess = { continuation.resume(Unit) },
                                        onFailure = continuation::resumeWithException
                                    )
                                }
                            }
                            try {
                                val started = if (Build.VERSION.SDK_INT >= 33) {
                                    active.writeCharacteristic(characteristic, chunk, writeType) == BluetoothGatt.GATT_SUCCESS
                                } else {
                                    @Suppress("DEPRECATION")
                                    characteristic.value = chunk
                                    @Suppress("DEPRECATION")
                                    characteristic.writeType = writeType
                                    @Suppress("DEPRECATION")
                                    active.writeCharacteristic(characteristic)
                                }
                                transferStarted = started
                                if (!started) {
                                    pendingWrite = null
                                    continuation.resumeWithException(IOException("Could not write to the BLE printer."))
                                }
                                continuation.invokeOnCancellation { pendingWrite = null }
                            } catch (failure: Exception) {
                                pendingWrite = null
                                continuation.resumeWithException(failure)
                            }
                        }
                    }
                } else {
                    val started = if (Build.VERSION.SDK_INT >= 33) {
                        active.writeCharacteristic(characteristic, chunk, writeType) == BluetoothGatt.GATT_SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        characteristic.value = chunk
                        @Suppress("DEPRECATION")
                        characteristic.writeType = writeType
                        @Suppress("DEPRECATION")
                        active.writeCharacteristic(characteristic)
                    }
                    if (!started) throw IOException("Could not write to the BLE printer.")
                    transferStarted = true
                    delay(NO_RESPONSE_CHUNK_DELAY_MS)
                }
                offset += chunk.size
            }
        } catch (cancelled: CancellationException) {
            if (cancelled is TimeoutCancellationException) {
                disconnect()
                throw PartialWriteException(
                    maxOf(offset, 1),
                    IOException("Timed out waiting for a BLE printer write acknowledgement.", cancelled)
                )
            }
            throw cancelled
        } catch (failure: Exception) {
            if (offset > 0 || transferStarted) {
                throw PartialWriteException(maxOf(offset, 1), failure)
            }
            throw failure
        }
    }

    override suspend fun read(maxBytes: Int, timeoutMillis: Long): ByteArray? = null

    private fun requireConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Allow Nearby devices permission to connect to a BLE printer.")
        }
    }

    private fun closeGatt(active: BluetoothGatt) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                active.disconnect()
            }
        } catch (_: SecurityException) {
            // Permission may have been revoked while the GATT connection was active.
        } finally {
            active.close()
            if (gatt === active) {
                gatt = null
                output = null
            }
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val WRITE_ACK_TIMEOUT_MS = 10_000L
        private const val MAX_CHUNK_BYTES = 20
        private const val NO_RESPONSE_CHUNK_DELAY_MS = 10L
    }
}
