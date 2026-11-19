package com.bizbite.printer.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bizbite.printer.R
import com.bizbite.printer.domain.model.ConnectionType
import com.bizbite.printer.domain.model.JobStatus
import com.bizbite.printer.domain.model.PrintJob
import com.bizbite.printer.domain.model.PrinterConfig
import com.bizbite.printer.domain.model.PrinterStatus
import com.bizbite.printer.data.remote.SellerOrderDto
import com.bizbite.printer.ui.MainViewModel
import com.bizbite.printer.ui.components.AccessibleIconButton
import com.bizbite.printer.ui.components.JobCard
import com.bizbite.printer.ui.components.PrimaryButton
import com.bizbite.printer.ui.components.PrinterCard
import com.bizbite.printer.ui.components.SecondaryButton
import com.bizbite.printer.ui.components.SectionHeader
import com.bizbite.printer.ui.components.StatCard
import com.bizbite.printer.ui.components.StatusChip
import com.bizbite.printer.ui.components.StatusKind
import com.bizbite.printer.ui.theme.BizBiteTheme
import com.bizbite.printer.ui.theme.LightGreen
import java.text.DateFormat
import java.util.Date

@Composable
fun DashboardScreen(
    model: MainViewModel,
    onOpenPrinters: () -> Unit,
    onOpenOrders: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    profile: SellerProfileViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val context = LocalContext.current
    val pendingCount by model.pendingCount.collectAsState()
    val lastPrint by model.lastPrintAt.collectAsState()
    val printers by model.printers.collectAsState()
    val jobs by model.jobs.collectAsState()
    val message by model.message.collectAsState()
    val lastPrintError by model.lastPrintError.collectAsState()
    val busy by model.busy.collectAsState()
    val internet by model.internet.collectAsState()
    val backendAvailable by model.backendAvailable.collectAsState()
    val status by model.printerStatus.collectAsState()
    val autoPairState by model.autoPairState.collectAsState()
    val storeName by profile.storeName.collectAsState()
    val recentOrders by profile.recentOrders.collectAsState()
    val batteryManager = remember { context.getSystemService(PowerManager::class.java) }
    val batteryExempt = remember { batteryManager?.isIgnoringBatteryOptimizations(context.packageName) == true }
    val printer = printers.firstOrNull { it.isDefault }

    LaunchedEffect(printer?.id) { model.refreshPrinterStatus() }
    val testPrintPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys
        if (denied.isEmpty()) model.testPrint()
        else model.showMessage(context.getString(R.string.bluetooth_permission_denied, denied.joinToString(), if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getString(R.string.nearby_devices_permission) else context.getString(R.string.location_permission)))
    }

    fun requestTestPrint() {
        val isBluetooth = printer?.type == ConnectionType.BLUETOOTH || printer?.type == ConnectionType.BLE
        if (!isBluetooth) {
            model.testPrint()
            return
        }
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        } else emptyArray()
        val missing = permissions.filter { ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) model.testPrint() else testPrintPermissionLauncher.launch(missing.toTypedArray())
    }

    DashboardContent(
        storeName = storeName,
        printer = printer,
        printerCount = printers.size,
        printerStatus = status,
        jobs = jobs,
        pendingCount = pendingCount,
        internet = internet,
        backendAvailable = backendAvailable,
        lastPrint = lastPrint,
        recentOrders = recentOrders,
        lastPrintError = lastPrintError,
        message = message,
        batteryExempt = batteryExempt,
        testPrintBusy = busy,
        onTestPrint = ::requestTestPrint,
        onOpenPrinters = onOpenPrinters,
        onOpenHistory = onOpenHistory,
        onOpenOrders = onOpenOrders,
        onReviewBattery = {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:${context.packageName}")))
            }
        },
        onOpenNotifications = {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            runCatching { context.startActivity(intent) }.onFailure {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${context.packageName}")))
            }
        },
        autoPairState = autoPairState,
        onRetryPairing = model::retryPairing
    )
}

@Composable
private fun DashboardContent(
    storeName: String?,
    printer: PrinterConfig?,
    printerCount: Int,
    printerStatus: PrinterStatus,
    jobs: List<PrintJob>,
    pendingCount: Int,
    internet: Boolean,
    backendAvailable: Boolean?,
    lastPrint: Long?,
    recentOrders: List<SellerOrderDto>,
    lastPrintError: String?,
    message: String?,
    batteryExempt: Boolean,
    testPrintBusy: Boolean,
    onTestPrint: () -> Unit,
    onOpenPrinters: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenOrders: () -> Unit,
    onReviewBattery: () -> Unit,
    onOpenNotifications: () -> Unit,
    autoPairState: com.bizbite.printer.data.auth.AutoPairState = com.bizbite.printer.data.auth.AutoPairState.NotPaired,
    onRetryPairing: () -> Unit = {}
) {
    val connectedCount = if (printer != null && printerStatus == PrinterStatus.CONNECTED) 1 else 0
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_tab), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.greeting, storeName?.takeIf(String::isNotBlank) ?: stringResource(R.string.seller_fallback_name)), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AccessibleIconButton(R.drawable.ic_notifications, stringResource(R.string.notification_icon_description), onOpenNotifications)
        }
        com.bizbite.printer.ui.components.AutoPairNotice(autoPairState, onRetryPairing)

        Card(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.connected_printers), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("$connectedCount", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimary)
                    Text("/ $printerCount", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), modifier = Modifier.padding(bottom = 5.dp))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    val online = connectedCount > 0
                    StatusChip(stringResource(if (online) R.string.printer_status_online else if (printerCount > 0) R.string.printer_status_offline else R.string.no_default_printer), if (online) StatusKind.ONLINE else StatusKind.OFFLINE)
                    Text(stringResource(R.string.default_printer_status_only), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(stringResource(R.string.test_print), onTestPrint, Modifier.weight(1f), enabled = !testPrintBusy, icon = R.drawable.ic_billing)
                    SecondaryButton(stringResource(R.string.printer_settings), onOpenPrinters, Modifier.weight(1f))
                }
            }
        }

        val successful = jobs.count { it.status == JobStatus.SUCCESS || it.status == JobStatus.COMPLETED }
        val failed = jobs.count { it.status == JobStatus.FAILED }
        val queued = jobs.count { it.status == JobStatus.PENDING || it.status == JobStatus.PRINTING }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(stringResource(R.string.print_jobs), jobs.size.toString(), R.drawable.ic_billing, Modifier.weight(1f))
            StatCard(stringResource(R.string.successful), successful.toString(), R.drawable.ic_check_circle, Modifier.weight(1f), accent = com.bizbite.printer.ui.theme.OnlineColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(stringResource(R.string.failed), failed.toString(), R.drawable.ic_error_circle, Modifier.weight(1f), accent = com.bizbite.printer.ui.theme.ErrorColor)
            StatCard(stringResource(R.string.in_queue), queued.toString(), R.drawable.ic_clock, Modifier.weight(1f), accent = com.bizbite.printer.ui.theme.QueueColor)
        }

        SectionHeader(stringResource(R.string.recent_activity), actionText = stringResource(R.string.view_all), onAction = onOpenHistory)
        val recentJobs = jobs.sortedByDescending { it.createdAt }.take(3)
        if (recentJobs.isEmpty()) {
            Text(stringResource(R.string.empty_job_history), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            recentJobs.forEach { job ->
                JobCard(job, DateUtils.getRelativeTimeSpanString(job.createdAt).toString())
            }
        }

        if (printer != null) {
            PrinterCard(printer, printerStatus)
        }
        Card(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.network_status), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(stringResource(if (internet) R.string.internet_connected else R.string.internet_unavailable), if (internet) StatusKind.ONLINE else StatusKind.OFFLINE)
                    StatusChip(stringResource(when (backendAvailable) { true -> R.string.print_service_connected; false -> R.string.print_service_unavailable; null -> R.string.print_service_checking }), when (backendAvailable) { true -> StatusKind.ONLINE; false -> StatusKind.OFFLINE; null -> StatusKind.NEUTRAL })
                }
                Text(stringResource(R.string.pending_jobs_count, pendingCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.last_print, lastPrint?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: stringResource(R.string.never_printed)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                lastPrintError?.let { Text(stringResource(R.string.last_print_error, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }

        if (recentOrders.isNotEmpty()) {
            SectionHeader(stringResource(R.string.recent_orders), actionText = stringResource(R.string.view_all), onAction = onOpenOrders)
            recentOrders.forEach { order ->
                Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(order.invoiceNumber ?: order.id?.takeLast(8).orEmpty(), style = MaterialTheme.typography.titleSmall)
                        Text("₹${order.totalAmount ?: 0.0}", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

        if (!batteryExempt) {
            Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.battery_reliability_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.battery_reliability_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryButton(stringResource(R.string.review_battery_settings), onReviewBattery, Modifier.fillMaxWidth())
                    AutoStartSettingsButton()
                }
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun AutoStartSettingsButton() {
    val context = LocalContext.current
    val manufacturer = Build.MANUFACTURER.lowercase()
    val supported = listOf("xiaomi", "oppo", "vivo", "samsung", "huawei").any(manufacturer::contains)
    if (supported) SecondaryButton(stringResource(R.string.open_autostart_settings), onClick = {
        val candidates = when {
            manufacturer.contains("xiaomi") -> listOf(Intent().setComponent(android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> listOf(Intent().setComponent(android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
            manufacturer.contains("vivo") -> listOf(Intent().setComponent(android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")))
            manufacturer.contains("huawei") -> listOf(Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
            else -> listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${context.packageName}")))
        }
        val intent = candidates.firstOrNull { it.resolveActivity(context.packageManager) != null }
        if (intent != null) runCatching { context.startActivity(intent) }
        else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${context.packageName}")))
    }, modifier = Modifier.fillMaxWidth())
}

@Preview(name = "Home Light")
@Composable
private fun DashboardLightPreview() = BizBiteTheme(darkTheme = false) {
    DashboardContent(
        storeName = "BizBite Cafe",
        printer = PrinterConfig("p1", "Counter Printer", ConnectionType.BLUETOOTH, "00:11:22:33", isDefault = true),
        printerCount = 2,
        printerStatus = PrinterStatus.CONNECTED,
        jobs = listOf(PrintJob("job-100", "", JobStatus.SUCCESS), PrintJob("job-099", "", JobStatus.PENDING)),
        pendingCount = 1,
        internet = true,
        backendAvailable = true,
        lastPrint = System.currentTimeMillis(),
        recentOrders = emptyList(),
        lastPrintError = null,
        message = null,
        batteryExempt = true,
        testPrintBusy = false,
        onTestPrint = {}, onOpenPrinters = {}, onOpenHistory = {}, onOpenOrders = {}, onReviewBattery = {}, onOpenNotifications = {}
    )
}

@Preview(name = "Home Dark")
@Composable
private fun DashboardDarkPreview() = BizBiteTheme(darkTheme = true) {
    DashboardContent(
        storeName = "BizBite Cafe",
        printer = PrinterConfig("p1", "Counter Printer", ConnectionType.BLUETOOTH, "00:11:22:33", isDefault = true),
        printerCount = 2,
        printerStatus = PrinterStatus.CONNECTED,
        jobs = listOf(PrintJob("job-100", "", JobStatus.SUCCESS), PrintJob("job-099", "", JobStatus.PENDING)),
        pendingCount = 1,
        internet = true,
        backendAvailable = true,
        lastPrint = System.currentTimeMillis(),
        recentOrders = emptyList(),
        lastPrintError = null,
        message = null,
        batteryExempt = true,
        testPrintBusy = false,
        onTestPrint = {}, onOpenPrinters = {}, onOpenHistory = {}, onOpenOrders = {}, onReviewBattery = {}, onOpenNotifications = {}
    )
}
