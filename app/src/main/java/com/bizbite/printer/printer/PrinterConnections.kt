package com.bizbite.printer.printer

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.hardware.usb.UsbManager
import com.bizbite.printer.domain.model.ConnectionType
import com.bizbite.printer.domain.model.PrinterConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Singleton
class PrinterConnectionFactoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PrinterConnectionFactory {
    private val bluetoothConnections = ConcurrentHashMap<String, BluetoothSppConnection>()

    override suspend fun create(printer: PrinterConfig): PrinterConnection {
        if (printer.type == ConnectionType.MOCK) {
            throw IllegalStateException("Virtual printers are not supported. Select a physical printer.")
        }
        return when (printer.type) {
            ConnectionType.MOCK -> error("Mock connection routing is not supported.")
            ConnectionType.BLUETOOTH -> {
                val adapter = context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
                    ?: throw IllegalStateException("Bluetooth is not available on this device.")
                bluetoothConnections.getOrPut(printer.address) {
                    BluetoothSppConnection(context, adapter, adapter.getRemoteDevice(printer.address))
                }
            }
            ConnectionType.BLE -> {
                val adapter = context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
                    ?: throw IllegalStateException("Bluetooth is not available on this device.")
                BluetoothLeConnection(context, adapter.getRemoteDevice(printer.address))
            }
            ConnectionType.USB -> {
                val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val device = manager.deviceList[printer.address]
                    ?: throw IllegalStateException("USB printer is not connected.")
                UsbConnection(context, device)
            }
            ConnectionType.LAN -> TcpConnection(printer.address, printer.port)
        }
    }
}
