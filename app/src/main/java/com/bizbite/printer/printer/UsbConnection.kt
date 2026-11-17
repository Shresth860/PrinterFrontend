package com.bizbite.printer.printer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class UsbConnection(
    @ApplicationContext private val context: Context,
    private val device: UsbDevice
) : PrinterConnection {
    private val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var outEndpoint: UsbEndpoint? = null
    private var inEndpoint: UsbEndpoint? = null
    override val isConnected: Boolean get() = connection != null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        if (!manager.hasPermission(device)) requestPermission()
        val candidate = manager.openDevice(device) ?: throw IOException("Unable to open USB printer.")
        val selectedInterface = (0 until device.interfaceCount)
            .map(device::getInterface)
            .firstOrNull { it.endpointCount > 0 }
            ?: throw IOException("USB printer has no data interface.")
        if (!candidate.claimInterface(selectedInterface, true)) {
            candidate.close()
            throw IOException("Unable to claim USB printer interface.")
        }
        usbInterface = selectedInterface
        outEndpoint = (0 until selectedInterface.endpointCount)
            .map(selectedInterface::getEndpoint)
            .firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
        inEndpoint = (0 until selectedInterface.endpointCount)
            .map(selectedInterface::getEndpoint)
            .firstOrNull { it.direction == UsbConstants.USB_DIR_IN }
        if (outEndpoint == null) {
            candidate.releaseInterface(selectedInterface)
            candidate.close()
            throw IOException("USB printer has no output endpoint.")
        }
        connection = candidate
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        connection?.let { active -> usbInterface?.let(active::releaseInterface); active.close() }
        connection = null
        usbInterface = null
        outEndpoint = null
        inEndpoint = null
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val active = connection ?: throw IOException("USB printer is disconnected.")
        val endpoint = outEndpoint ?: throw IOException("USB output endpoint unavailable.")
        var offset = 0
        while (offset < bytes.size) {
            val size = minOf(CHUNK_SIZE, bytes.size - offset)
            val transferred = active.bulkTransfer(endpoint, bytes.copyOfRange(offset, offset + size), size, USB_TIMEOUT_MS)
            if (transferred <= 0) {
                if (offset > 0) throw PartialWriteException(offset, IOException("USB transfer failed."))
                throw IOException("USB transfer failed.")
            }
            offset += transferred
            if (offset < bytes.size) delay(CHUNK_DELAY_MS)
        }
    }

    override suspend fun read(maxBytes: Int, timeoutMillis: Long): ByteArray? = withContext(Dispatchers.IO) {
        val active = connection ?: return@withContext null
        val endpoint = inEndpoint ?: return@withContext null
        val buffer = ByteArray(maxBytes)
        val count = active.bulkTransfer(endpoint, buffer, buffer.size, timeoutMillis.toInt())
        if (count <= 0) null else buffer.copyOf(count)
    }

    private suspend fun requestPermission() = suspendCancellableCoroutine { continuation ->
        val action = "${context.packageName}.USB_PERMISSION"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != action) return
                context.unregisterReceiver(this)
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (!continuation.isActive) return
                if (granted) continuation.resume(Unit)
                else continuation.resumeWith(Result.failure(SecurityException("USB printer permission was denied.")))
            }
        }
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        continuation.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            device.deviceId,
            Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.requestPermission(device, permissionIntent)
    }

    companion object {
        const val CHUNK_SIZE = 512
        const val CHUNK_DELAY_MS = 20L
        const val USB_TIMEOUT_MS = 5_000
    }
}

fun UsbManager.attachedPrinters(): List<UsbDevice> = deviceList.values.toList()
