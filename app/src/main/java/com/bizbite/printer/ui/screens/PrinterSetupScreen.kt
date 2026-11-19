package com.bizbite.printer.ui.screens

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bizbite.printer.domain.model.ConnectionType
import com.bizbite.printer.domain.model.PrinterConfig
import com.bizbite.printer.domain.model.PrinterStatus
import com.bizbite.printer.printer.BluetoothDeviceDiscovery
import com.bizbite.printer.ui.MainViewModel
import com.bizbite.printer.ui.components.AccessibleIconButton
import com.bizbite.printer.ui.components.AppSearchField
import com.bizbite.printer.ui.components.PrimaryButton
import com.bizbite.printer.ui.components.PrinterCard
import com.bizbite.printer.ui.components.SecondaryButton
import com.bizbite.printer.ui.components.SectionHeader
import com.bizbite.printer.ui.theme.BizBiteTheme
import java.util.UUID
import kotlinx.coroutines.delay

private data class BluetoothCandidate(
    val name: String,
    val address: String,
    val transports: Set<ConnectionType>,
    val bonded: Boolean
)

@Composable
fun PrinterSetupScreen(model: MainViewModel) {
    val context = LocalContext.current
    val printers by model.printers.collectAsState()
    val message by model.message.collectAsState()
    val printerStatus by model.printerStatus.collectAsState()
    val autoPairState by model.autoPairState.collectAsState()
    var type by remember { mutableStateOf(ConnectionType.LAN) }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("9100") }
    var search by remember { mutableStateOf("") }
    var showAddForm by remember(printers.isEmpty()) { mutableStateOf(printers.isEmpty()) }
    var width by remember { mutableStateOf(58) }
    var makeDefault by remember { mutableStateOf(true) }
    var scanning by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var showPermanentPermissionHelp by remember { mutableStateOf(false) }
    var bluetoothDisabled by remember { mutableStateOf(false) }
    val candidates = remember { mutableStateMapOf<String, BluetoothCandidate>() }
    val usbDevices = remember { mutableStateOf(emptyList<android.hardware.usb.UsbDevice>()) }
    val discovery = remember(context) { BluetoothDeviceDiscovery(context) }

    fun neededBluetoothPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            emptyArray()
        }

    fun hasBluetoothPermissions(): Boolean = neededBluetoothPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun mergeDevice(device: BluetoothDevice, transport: ConnectionType, bonded: Boolean = false) {
        val mac = try {
            device.address
        } catch (_: SecurityException) {
            null
        } ?: return
        val deviceName = try {
            device.name?.takeIf(String::isNotBlank)
        } catch (_: SecurityException) {
            null
        } ?: context.getString(com.bizbite.printer.R.string.unknown_bluetooth_device)
        candidates[mac] = candidates[mac]?.let { previous ->
            previous.copy(
                name = if (deviceName == "Unknown Bluetooth device") previous.name else deviceName,
                transports = previous.transports + transport,
                bonded = previous.bonded || bonded
            )
        } ?: BluetoothCandidate(deviceName, mac, setOf(transport), bonded)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { !it }.keys
        if (denied.isEmpty()) {
            permissionMessage = null
            showPermanentPermissionHelp = false
            scanning = true
        } else {
            permissionMessage = context.getString(com.bizbite.printer.R.string.bluetooth_permission_request_denied)
            val activity = context as? Activity
            showPermanentPermissionHelp = permissionRequested && activity != null &&
                denied.any { !ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        }
    }

    fun requestScan() {
        if (hasBluetoothPermissions()) {
            permissionMessage = null
            scanning = true
        } else {
            permissionRequested = true
            permissionLauncher.launch(neededBluetoothPermissions())
        }
    }

    DisposableEffect(discovery) {
        onDispose { discovery.stop(notifyFinished = false) }
    }

    LaunchedEffect(type, context) {
        if (type == ConnectionType.USB) {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            usbDevices.value = manager.deviceList.values.toList()
        }
    }

    LaunchedEffect(scanning) {
        if (!scanning) return@LaunchedEffect
        if (!hasBluetoothPermissions()) {
            scanning = false
            permissionMessage = context.getString(com.bizbite.printer.R.string.bluetooth_permission_required_scan)
            return@LaunchedEffect
        }
        try {
            bluetoothDisabled = !discovery.isBluetoothEnabled()
        } catch (failure: SecurityException) {
            bluetoothDisabled = false
            scanning = false
            permissionMessage = failure.message ?: context.getString(com.bizbite.printer.R.string.bluetooth_permission_required)
            return@LaunchedEffect
        }
        if (bluetoothDisabled) {
            scanning = false
            return@LaunchedEffect
        }

        candidates.clear()
        try {
            discovery.bondedDevices().forEach { device ->
                discovery.bondedTransports(device).forEach { transport ->
                    mergeDevice(device, transport, bonded = true)
                }
            }
            discovery.start(
                onDevice = { device, transport -> mergeDevice(device, transport) },
                onFinished = { scanning = false },
                onError = { error ->
                    permissionMessage = error
                    if (error.contains("turned off", ignoreCase = true)) bluetoothDisabled = true
                    if (error.contains("permission", ignoreCase = true)) scanning = false
                }
            )
        } catch (failure: SecurityException) {
            scanning = false
            permissionMessage = failure.message ?: context.getString(com.bizbite.printer.R.string.bluetooth_permission_required)
        }
        delay(15_000)
        if (scanning) {
            discovery.stop()
            scanning = false
        }
    }

    val savedPrinters = printers.filter { it.name.contains(search, true) || it.address.contains(search, true) }
    val discoveredCandidates = candidates.values.filterNot(BluetoothCandidate::bonded).sortedBy(BluetoothCandidate::name).toList()
    val pairedCandidates = candidates.values.filter(BluetoothCandidate::bonded).sortedBy(BluetoothCandidate::name).toList()

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(com.bizbite.printer.R.string.printer_setup_title), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            AccessibleIconButton(com.bizbite.printer.R.drawable.ic_add, stringResource(com.bizbite.printer.R.string.add_printer), { showAddForm = !showAddForm })
        }
        AppSearchField(search, { search = it }, stringResource(com.bizbite.printer.R.string.search_printers))
        com.bizbite.printer.ui.components.AutoPairNotice(autoPairState, model::retryPairing)
        if (savedPrinters.isEmpty()) Text(stringResource(if (printers.isEmpty()) com.bizbite.printer.R.string.no_saved_printers else com.bizbite.printer.R.string.no_printers_found), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        savedPrinters.forEach { saved ->
            PrinterCard(
                printer = saved,
                status = if (saved.isDefault) printerStatus else null,
                actions = if (!saved.isDefault) ({
                    SecondaryButton(stringResource(com.bizbite.printer.R.string.set_default_printer), { model.savePrinter(saved.copy(isDefault = true)) })
                }) else null
            )
        }
        message?.takeUnless { it.startsWith("Printer configuration sync failed") }?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.primary) }
        if (showAddForm) {
            androidx.compose.material3.Card(shape = androidx.compose.material3.MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(stringResource(com.bizbite.printer.R.string.printer_setup_add_title))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConnectionType.entries.filter { it != ConnectionType.MOCK }.forEach { option ->
                            val label = when (option) {
                                ConnectionType.BLUETOOTH -> com.bizbite.printer.R.string.connection_bluetooth_classic
                                ConnectionType.BLE -> com.bizbite.printer.R.string.connection_ble
                                ConnectionType.USB -> com.bizbite.printer.R.string.connection_usb
                                else -> com.bizbite.printer.R.string.connection_lan
                            }
                            FilterChip(selected = type == option, onClick = { type = option; name = ""; address = "" }, label = { Text(stringResource(label)) })
                        }
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = makeDefault, onCheckedChange = { makeDefault = it })
                        Text(stringResource(com.bizbite.printer.R.string.set_default_printer))
                    }
                    when (type) {
                        ConnectionType.MOCK -> Unit
                        ConnectionType.LAN -> {
                            OutlinedTextField(address, { address = it }, label = { Text(stringResource(com.bizbite.printer.R.string.printer_ip_address)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(portText, { portText = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(com.bizbite.printer.R.string.printer_port_default)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        }
                        ConnectionType.BLUETOOTH, ConnectionType.BLE -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryButton(stringResource(com.bizbite.printer.R.string.scan_nearby_devices), ::requestScan, Modifier.weight(1f), enabled = !scanning)
                                if (scanning) SecondaryButton(stringResource(com.bizbite.printer.R.string.stop_scan), { discovery.stop(); scanning = false })
                            }
                            if (scanning) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                CircularProgressIndicator()
                                Text(stringResource(com.bizbite.printer.R.string.scanning_devices))
                            }
                            if (bluetoothDisabled) {
                                Text(stringResource(com.bizbite.printer.R.string.bluetooth_off))
                                SecondaryButton(stringResource(com.bizbite.printer.R.string.open_bluetooth_settings), { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) })
                            }
                            if (showPermanentPermissionHelp) {
                                Text(stringResource(com.bizbite.printer.R.string.bluetooth_permission_help))
                                SecondaryButton(stringResource(com.bizbite.printer.R.string.open_app_permission_settings), {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:${context.packageName}")))
                                })
                            }
                            permissionMessage?.let { Text(it) }
                            if (!scanning && !bluetoothDisabled && discoveredCandidates.isEmpty() && pairedCandidates.isEmpty()) Text(stringResource(com.bizbite.printer.R.string.no_devices_found))
                            if (pairedCandidates.isNotEmpty()) {
                                SectionHeader(stringResource(com.bizbite.printer.R.string.previously_paired_devices))
                                pairedCandidates.forEach { candidate ->
                                    BluetoothCandidateCard(candidate, candidate.address == address && type in candidate.transports) {
                                        name = candidate.name; address = candidate.address
                                        type = when { type in candidate.transports -> type; ConnectionType.BLUETOOTH in candidate.transports -> ConnectionType.BLUETOOTH; else -> ConnectionType.BLE }
                                    }
                                }
                            }
                            if (discoveredCandidates.isNotEmpty()) {
                                SectionHeader(stringResource(com.bizbite.printer.R.string.nearby_devices))
                                discoveredCandidates.forEach { candidate ->
                                    BluetoothCandidateCard(candidate, candidate.address == address && type in candidate.transports) {
                                        name = candidate.name; address = candidate.address
                                        type = when { type in candidate.transports -> type; ConnectionType.BLUETOOTH in candidate.transports -> ConnectionType.BLUETOOTH; else -> ConnectionType.BLE }
                                    }
                                }
                            }
                        }
                        ConnectionType.USB -> {
                            if (usbDevices.value.isEmpty()) Text(stringResource(com.bizbite.printer.R.string.no_usb_devices))
                            usbDevices.value.forEach { device ->
                                SecondaryButton(stringResource(com.bizbite.printer.R.string.usb_printer_selection, device.productName ?: device.deviceId, device.deviceName), {
                                    address = device.deviceName
                                    name = context.getString(com.bizbite.printer.R.string.usb_printer_name, device.productName ?: device.deviceId)
                                }, Modifier.fillMaxWidth())
                            }
                        }
                    }
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(com.bizbite.printer.R.string.printer_name_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(58, 80).forEach { mm -> FilterChip(selected = width == mm, onClick = { width = mm }, label = { Text(stringResource(com.bizbite.printer.R.string.paper_width, mm)) }) }
                    }
                    PrimaryButton(
                        stringResource(com.bizbite.printer.R.string.save_set_default),
                        onClick = {
                            model.savePrinter(PrinterConfig(
                                id = UUID.nameUUIDFromBytes("${type.name}:$address".toByteArray()).toString(),
                                name = name.trim(), type = type, address = address.trim(),
                                port = portText.toIntOrNull() ?: 9100, paperWidthMm = width, isDefault = makeDefault
                            ))
                            showAddForm = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = name.isNotBlank() && address.isNotBlank() && (type != ConnectionType.LAN || (portText.toIntOrNull() ?: 0) in 1..65535)
                    )
                }
            }
        }
    }
}
@Composable
private fun BluetoothCandidateCard(
    candidate: BluetoothCandidate,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val transportNames = candidate.transports.map { stringResource(if (it == ConnectionType.BLUETOOTH) com.bizbite.printer.R.string.connection_bluetooth_classic else com.bizbite.printer.R.string.connection_ble) }.joinToString(" + ")
    Card(Modifier.fillMaxWidth(), shape = androidx.compose.material3.MaterialTheme.shapes.medium, border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(candidate.name)
            Text(candidate.address)
            Text(stringResource(com.bizbite.printer.R.string.printer_type_line, transportNames), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(com.bizbite.printer.R.string.printer_pairing_line, stringResource(if (candidate.bonded) com.bizbite.printer.R.string.printer_paired else com.bizbite.printer.R.string.printer_not_paired)), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            SecondaryButton(stringResource(if (selected) com.bizbite.printer.R.string.selected else com.bizbite.printer.R.string.select_device), onSelect)
        }
    }
}

@Preview(name = "Printers Light")
@Composable
private fun PrinterSetupLightPreview() = BizBiteTheme(darkTheme = false) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(com.bizbite.printer.R.string.printer_setup_title), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        AppSearchField("", {}, stringResource(com.bizbite.printer.R.string.search_printers))
        PrinterCard(PrinterConfig("preview", "Counter Printer", ConnectionType.BLUETOOTH, "00:11:22:33", isDefault = true), PrinterStatus.CONNECTED)
    }
}

@Preview(name = "Printers Dark")
@Composable
private fun PrinterSetupDarkPreview() = BizBiteTheme(darkTheme = true) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(com.bizbite.printer.R.string.printer_setup_title), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        AppSearchField("", {}, stringResource(com.bizbite.printer.R.string.search_printers))
        PrinterCard(PrinterConfig("preview", "Counter Printer", ConnectionType.BLUETOOTH, "00:11:22:33", isDefault = true), PrinterStatus.CONNECTED)
    }
}
