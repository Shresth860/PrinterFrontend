package com.bizbite.printer.printer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BluetoothSppConnection(
    context: Context,
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice
) : PrinterConnection {
    private val appContext = context.applicationContext
    private val connectionMutex = Mutex()
    private val resources = SocketResourceSlot<BluetoothSocket, OutputStream>(
        closeOutput = { stream, reason ->
            try {
                stream.close()
            } catch (failure: IOException) {
                Log.e(TAG, "Failed to close RFCOMM output stream ($reason).", failure)
            }
        },
        closeSocket = { active, reason ->
            try {
                active.close()
                Log.i(TAG, "Closed RFCOMM socket ($reason).")
            } catch (failure: IOException) {
                Log.e(TAG, "Failed to close RFCOMM socket ($reason).", failure)
            }
        }
    )

    override val isConnected: Boolean
        get() {
            val (socket, output) = resources.snapshot()
            return socket?.isConnected == true && output != null
        }

    override suspend fun connect() = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            if (isConnected) {
                Log.d(TAG, "Reusing connected RFCOMM socket.")
                return@withLock
            }
            ensurePermissions()
            Log.i(TAG, "Starting RFCOMM connection for ${safeAddress()}.")
            if (resources.hasResources()) closeSocket("stale socket before reconnect")
            if (!adapter.isEnabled) throw IOException("Bluetooth is turned off.")

            var lastFailure: IOException? = null
            repeat(CONNECT_ATTEMPTS) { attempt ->
                Log.d(TAG, "SPP connect attempt ${attempt + 1}/$CONNECT_ATTEMPTS.")
                cancelDiscoveryBeforeConnect()

                val secureSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                try {
                    secureSocket.connect()
                    holdSocket(secureSocket)
                    Log.i(TAG, "Secure RFCOMM socket connected=${isConnected}.")
                    return@withLock
                } catch (failure: IOException) {
                    closeCandidate(secureSocket)
                    lastFailure = failure
                    Log.e(TAG, "Secure RFCOMM connection failed; trying insecure SPP.", failure)
                } catch (failure: SecurityException) {
                    closeCandidate(secureSocket)
                    Log.e(TAG, "Bluetooth permission was denied while connecting.", failure)
                    throw failure
                }

                val insecureSocket = try {
                    device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                } catch (failure: SecurityException) {
                    Log.e(TAG, "Bluetooth permission was denied while creating the insecure SPP socket.", failure)
                    throw failure
                } catch (failure: Exception) {
                    lastFailure = failure as? IOException ?: IOException("Could not create insecure SPP socket.", failure)
                    Log.e(TAG, "Could not create insecure SPP socket; trying channel 1.", failure)
                    null
                }
                if (insecureSocket != null) {
                    try {
                        cancelDiscoveryBeforeConnect()
                        insecureSocket.connect()
                        holdSocket(insecureSocket)
                        Log.i(TAG, "Insecure RFCOMM socket connected=${isConnected}.")
                        return@withLock
                    } catch (failure: IOException) {
                        closeCandidate(insecureSocket)
                        lastFailure = failure
                        Log.e(TAG, "Insecure RFCOMM connection failed; trying channel 1.", failure)
                    } catch (failure: SecurityException) {
                        closeCandidate(insecureSocket)
                        Log.e(TAG, "Bluetooth permission was denied on insecure connection.", failure)
                        throw failure
                    }
                }

                val channelOneSocket = try {
                    createChannelOneSocket()
                } catch (failure: Exception) {
                    val cause = (failure as? InvocationTargetException)?.targetException
                    if (cause is SecurityException) throw cause
                    lastFailure = cause as? IOException ?: IOException("Could not create RFCOMM channel 1 socket.", cause ?: failure)
                    Log.e(TAG, "Could not create RFCOMM channel 1 socket.", failure)
                    null
                }
                if (channelOneSocket != null) {
                    try {
                        cancelDiscoveryBeforeConnect()
                        channelOneSocket.connect()
                        holdSocket(channelOneSocket)
                        Log.i(TAG, "RFCOMM channel 1 connected=${isConnected}.")
                        return@withLock
                    } catch (failure: IOException) {
                        closeCandidate(channelOneSocket)
                        lastFailure = failure
                        Log.e(TAG, "RFCOMM channel 1 connection failed.", failure)
                    } catch (failure: SecurityException) {
                        closeCandidate(channelOneSocket)
                        Log.e(TAG, "Bluetooth permission was denied on channel 1 connection.", failure)
                        throw failure
                    }
                }
            }
            val failure = lastFailure ?: IOException("Unable to connect to the Bluetooth printer.")
            Log.e(TAG, "RFCOMM connection failed for ${safeAddress()}.", failure)
            throw failure
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        connectionMutex.withLock { closeSocket("disconnect requested") }
    }

    override suspend fun write(bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            val (active, stream) = resources.snapshot()
            if (active == null || stream == null || !isConnected) {
                throw IOException("Bluetooth printer is disconnected.")
            }
            var written = 0
            var transferStarted = false
            try {
                while (written < bytes.size) {
                    val count = minOf(CHUNK_SIZE, bytes.size - written)
                    transferStarted = true
                    stream.write(bytes, written, count)
                    stream.flush()
                    written += count
                    if (written < bytes.size) delay(CHUNK_DELAY_MS)
                }
                Log.i(TAG, "Wrote and flushed ${bytes.size} bytes; socket connected=${isConnected}.")
                Unit
            } catch (failure: IOException) {
                Log.e(TAG, "RFCOMM write failed after the transfer began.", failure)
                closeSocket("write failure")
                if (transferStarted) throw PartialWriteException(maxOf(written, 1), failure)
                throw failure
            }
        }
    }

    override suspend fun read(maxBytes: Int, timeoutMillis: Long): ByteArray? = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            val active = resources.snapshot().first ?: return@withLock null
            if (!isConnected) return@withLock null
            val input = active.inputStream
            val until = System.currentTimeMillis() + timeoutMillis
            while (System.currentTimeMillis() < until) {
                if (input.available() > 0) {
                    val buffer = ByteArray(maxBytes)
                    val size = input.read(buffer)
                    return@withLock if (size > 0) buffer.copyOf(size) else null
                }
                delay(20)
            }
            null
        }
    }

    private fun holdSocket(active: BluetoothSocket) {
        val stream = active.outputStream
        resources.replace(active, stream, "replacing active socket")
    }

    private suspend fun cancelDiscoveryBeforeConnect() {
        adapter.cancelDiscovery()
        repeat(DISCOVERY_STOP_CHECKS) {
            if (!adapter.isDiscovering) return
            delay(DISCOVERY_STOP_INTERVAL_MS)
        }
        if (adapter.isDiscovering) throw IOException("Bluetooth discovery is still running; connection was not started.")
    }

    private fun closeSocket(reason: String) {
        resources.clear(reason)
    }

    private fun safeAddress(): String = try { device.address } catch (_: SecurityException) { "<permission denied>" }

    private fun closeCandidate(candidate: BluetoothSocket) {
        try {
            candidate.close()
        } catch (failure: IOException) {
            Log.e(TAG, "Failed to close an unsuccessful RFCOMM socket.", failure)
        }
    }

    private fun createChannelOneSocket(): BluetoothSocket {
        return try {
            val method = BluetoothDevice::class.java.getMethod(
                "createRfcommSocket",
                Int::class.javaPrimitiveType
            )
            method.invoke(device, 1) as BluetoothSocket
        } catch (failure: InvocationTargetException) {
            when (val cause = failure.targetException) {
                is SecurityException -> throw cause
                is IOException -> throw cause
                else -> throw IOException("Could not create the RFCOMM channel 1 socket.", cause)
            }
        }
    }

    private fun ensurePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    appContext,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                throw SecurityException("Allow Nearby devices permission to connect to the Bluetooth printer.")
            }
            if (ContextCompat.checkSelfPermission(
                    appContext,
                    android.Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                throw SecurityException("Allow Nearby devices scan permission to connect to the Bluetooth printer.")
            }
        }
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val CHUNK_SIZE = 512
        const val CHUNK_DELAY_MS = 20L
        private const val DISCOVERY_STOP_CHECKS = 10
        private const val DISCOVERY_STOP_INTERVAL_MS = 100L
        private const val CONNECT_ATTEMPTS = 1
        private const val TAG = "BizBiteSPP"
    }
}

fun BluetoothAdapter?.bondedPrinters(): Set<BluetoothDevice> = this?.bondedDevices.orEmpty()
