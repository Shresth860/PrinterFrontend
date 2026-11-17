package com.bizbite.printer.printer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var output: OutputStream? = null

    override val isConnected: Boolean
        get() = socket?.isConnected == true

    private val aclReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_ACL_DISCONNECTED &&
                intent.action != BluetoothDevice.ACTION_ACL_CONNECTED
            ) return
            val remote = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            val remoteAddress = try {
                remote?.address
            } catch (failure: SecurityException) {
                Log.e(TAG, "Cannot inspect ACL device address without Bluetooth permission.", failure)
                closeSocket()
                return
            }
            val expectedAddress = try {
                device.address
            } catch (failure: SecurityException) {
                Log.e(TAG, "Cannot inspect configured printer address without Bluetooth permission.", failure)
                closeSocket()
                return
            }
            if (remoteAddress != expectedAddress) return

            if (intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                Log.w(TAG, "ACL disconnected; closing the active printer socket.")
                closeSocket()
            } else {
                Log.d(TAG, "ACL connected; RFCOMM socket state remains ${isConnected}.")
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(
            appContext,
            aclReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override suspend fun connect() = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            if (isConnected) {
                Log.d(TAG, "Reusing connected RFCOMM socket.")
                return@withLock
            }
            ensurePermissions()

            var lastFailure: IOException? = null
            repeat(CONNECT_ATTEMPTS) { attempt ->
                Log.d(TAG, "SPP connect attempt ${attempt + 1}/$CONNECT_ATTEMPTS.")
                adapter.cancelDiscovery()

                val secureSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                try {
                    secureSocket.connect()
                    holdSocket(secureSocket)
                    Log.d(TAG, "Secure RFCOMM socket connected=${isConnected}.")
                    return@withLock
                } catch (failure: IOException) {
                    closeCandidate(secureSocket)
                    lastFailure = failure
                    Log.e(TAG, "Secure RFCOMM connection failed; trying channel 1.", failure)
                } catch (failure: SecurityException) {
                    closeCandidate(secureSocket)
                    Log.e(TAG, "Bluetooth permission was denied while connecting.", failure)
                    throw failure
                }

                val fallbackSocket = try {
                    createChannelOneSocket()
                } catch (failure: SecurityException) {
                    Log.e(TAG, "Bluetooth permission was denied while creating the fallback socket.", failure)
                    throw failure
                } catch (failure: Exception) {
                    val cause = (failure as? InvocationTargetException)?.targetException
                    val resolved = cause as? IOException ?: IOException(
                        "Could not create the RFCOMM channel 1 socket.",
                        cause ?: failure
                    )
                    lastFailure = resolved
                    Log.e(TAG, "Could not create fallback RFCOMM socket.", failure)
                    null
                }
                if (fallbackSocket != null) {
                    try {
                        adapter.cancelDiscovery()
                        fallbackSocket.connect()
                        holdSocket(fallbackSocket)
                        Log.d(TAG, "Fallback RFCOMM socket connected=${isConnected}.")
                        return@withLock
                    } catch (failure: IOException) {
                        closeCandidate(fallbackSocket)
                        lastFailure = failure
                        Log.e(TAG, "Fallback RFCOMM connection failed.", failure)
                    } catch (failure: SecurityException) {
                        closeCandidate(fallbackSocket)
                        Log.e(TAG, "Bluetooth permission was denied on fallback connection.", failure)
                        throw failure
                    }
                }
            }
            throw lastFailure ?: IOException("Unable to connect to the Bluetooth printer.")
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        connectionMutex.withLock { closeSocket() }
    }

    override suspend fun write(bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            val active = socket
            val stream = output
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
                Log.d(TAG, "Wrote and flushed ${bytes.size} bytes; socket connected=${isConnected}.")
                Unit
            } catch (failure: IOException) {
                Log.e(TAG, "RFCOMM write failed after the transfer began.", failure)
                closeSocket()
                if (transferStarted) throw PartialWriteException(maxOf(written, 1), failure)
                throw failure
            }
        }
    }

    override suspend fun read(maxBytes: Int, timeoutMillis: Long): ByteArray? = withContext(Dispatchers.IO) {
        val active = socket ?: return@withContext null
        if (!isConnected) return@withContext null
        val input = active.inputStream
        val until = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < until) {
            if (input.available() > 0) {
                val buffer = ByteArray(maxBytes)
                val size = input.read(buffer)
                return@withContext if (size > 0) buffer.copyOf(size) else null
            }
            delay(20)
        }
        null
    }

    private fun holdSocket(active: BluetoothSocket) {
        val stream = active.outputStream
        output = stream
        socket = active
    }

    private fun closeSocket() {
        val active = socket
        socket = null
        output = null
        if (active != null) {
            try {
                active.close()
            } catch (failure: IOException) {
                Log.e(TAG, "Failed to close RFCOMM socket.", failure)
            }
        }
    }

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
        private const val CONNECT_ATTEMPTS = 1
        private const val TAG = "BizBiteSPP"
    }
}

fun BluetoothAdapter?.bondedPrinters(): Set<BluetoothDevice> = this?.bondedDevices.orEmpty()
