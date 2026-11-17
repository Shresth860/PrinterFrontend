package com.bizbite.printer.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bizbite.printer.BuildConfig
import com.bizbite.printer.R
import com.bizbite.printer.ui.MainViewModel
import com.bizbite.printer.ui.components.PrimaryButton
import com.bizbite.printer.ui.components.SecondaryButton
import com.bizbite.printer.ui.components.SectionHeader
import com.bizbite.printer.ui.theme.BizBiteTheme
import com.bizbite.printer.ui.theme.ErrorColor
import com.bizbite.printer.ui.theme.LightGreen
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    model: MainViewModel,
    onUnpair: () -> Unit,
    onOpenPairing: () -> Unit = {},
    onLogout: () -> Unit = {},
    onOpenPrinterHistory: () -> Unit = {},
    onOpenPrinters: () -> Unit = {},
    profile: SellerProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copies by model.settings.copies.collectAsState(initial = 1)
    val autoCut by model.settings.autoCut.collectAsState(initial = true)
    val cashDrawer by model.settings.cashDrawer.collectAsState(initial = false)
    val paired by model.paired.collectAsState()
    val storeName by profile.storeName.collectAsState()
    var copiesText by remember(copies) { mutableStateOf(copies.toString()) }
    var showUnpair by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }

    SettingsContent(
        accountName = storeName,
        copies = copiesText,
        autoCut = autoCut,
        cashDrawer = cashDrawer,
        paired = paired,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        onCopiesChanged = { input ->
            copiesText = input.filter(Char::isDigit).take(2)
            copiesText.toIntOrNull()?.let { scope.launch { model.settings.setCopies(it) } }
        },
        onAutoCutChanged = { scope.launch { model.settings.setAutoCut(it) } },
        onCashDrawerChanged = { scope.launch { model.settings.setCashDrawer(it) } },
        onOpenPrinters = onOpenPrinters,
        onOpenPairing = onOpenPairing,
        onOpenPrinterHistory = onOpenPrinterHistory,
        onCheckUpdates = { model.checkForUpdate() },
        onNotifications = { openSystemSettings(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) },
        onSecurityPrivacy = { openSystemSettings(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:${context.packageName}"))) },
        onExportDiagnostics = {
            val details = buildString {
                appendLine(context.getString(R.string.diagnostics_title))
                appendLine(context.getString(R.string.about_app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                appendLine(context.getString(R.string.diagnostics_device, android.os.Build.MANUFACTURER, android.os.Build.MODEL))
                appendLine(context.getString(R.string.diagnostics_android, android.os.Build.VERSION.RELEASE))
                appendLine(context.getString(R.string.diagnostics_privacy_note))
            }
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, details)
            context.startActivity(Intent.createChooser(send, context.getString(R.string.export_diagnostics)))
        },
        onUnpairRequest = { showUnpair = true },
        onLogoutRequest = { showLogout = true }
    )

    if (showUnpair) {
        AlertDialog(
            onDismissRequest = { showUnpair = false },
            title = { Text(stringResource(R.string.unpair_title)) },
            text = { Text(stringResource(R.string.unpair_message)) },
            confirmButton = { PrimaryButton(stringResource(R.string.unpair_action), { showUnpair = false; onUnpair() }) },
            dismissButton = { TextButton(onClick = { showUnpair = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text(stringResource(R.string.logout_title)) },
            text = { Text(stringResource(R.string.logout_message)) },
            confirmButton = { TextButton(onClick = { showLogout = false; onLogout() }) { Text(stringResource(R.string.logout), color = ErrorColor) } },
            dismissButton = { TextButton(onClick = { showLogout = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun SettingsContent(
    accountName: String?,
    copies: String,
    autoCut: Boolean,
    cashDrawer: Boolean,
    paired: Boolean?,
    versionName: String,
    versionCode: Int,
    onCopiesChanged: (String) -> Unit,
    onAutoCutChanged: (Boolean) -> Unit,
    onCashDrawerChanged: (Boolean) -> Unit,
    onOpenPrinters: () -> Unit,
    onOpenPairing: () -> Unit,
    onOpenPrinterHistory: () -> Unit,
    onCheckUpdates: () -> Unit,
    onNotifications: () -> Unit,
    onSecurityPrivacy: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onUnpairRequest: () -> Unit,
    onLogoutRequest: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_account), contentDescription = stringResource(R.string.profile_icon_description), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.account_title), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(accountName?.takeIf(String::isNotBlank) ?: stringResource(R.string.empty_profile_name), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        SectionHeader(stringResource(R.string.account_and_device))
        SettingsRow(R.drawable.ic_printer, stringResource(R.string.printer_settings), stringResource(R.string.manage_printers_description), onOpenPrinters)
        SettingsRow(R.drawable.ic_account, stringResource(if (paired == true) R.string.manage_printer_account else R.string.pair_printer_account), stringResource(R.string.pair_account_description), onOpenPairing)
        SettingsRow(R.drawable.ic_history, stringResource(R.string.print_history_title), stringResource(R.string.print_history_description), onOpenPrinterHistory)

        SectionHeader(stringResource(R.string.app_preferences))
        Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.default_copies), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(copies, onCopiesChanged, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.default_copies)) })
                PreferenceToggle(stringResource(R.string.auto_cut), autoCut, onAutoCutChanged)
                PreferenceToggle(stringResource(R.string.cash_drawer), cashDrawer, onCashDrawerChanged)
            }
        }
        SettingsRow(R.drawable.ic_notifications, stringResource(R.string.notifications_settings), stringResource(R.string.open_system_notifications), onNotifications)
        SettingsRow(R.drawable.ic_security, stringResource(R.string.security_privacy), stringResource(R.string.open_system_app_privacy), onSecurityPrivacy)

        SectionHeader(stringResource(R.string.about_app))
        Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.ic_info), contentDescription = stringResource(R.string.about_app), tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(stringResource(R.string.about_app), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.about_app_version, versionName, versionCode), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                SecondaryButton(stringResource(R.string.check_updates), onCheckUpdates, Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.export_diagnostics), onExportDiagnostics, Modifier.fillMaxWidth())
                if (paired == true) TextButton(onClick = onUnpairRequest, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.unpair_action), color = ErrorColor) }
            }
        }
        TextButton(onClick = onLogoutRequest, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.logout), color = ErrorColor, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun SettingsRow(icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    val openAction = stringResource(R.string.open_action)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).semantics { contentDescription = title },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(painterResource(icon), contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("â€º", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { contentDescription = openAction })
        }
    }
}

@Composable
private fun PreferenceToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.semantics { contentDescription = label })
    }
}

private fun openSystemSettings(context: android.content.Context, intent: Intent) {
    runCatching { context.startActivity(intent) }.onFailure {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:${context.packageName}")))
    }
}

@Preview(name = "Settings Light")
@Composable
private fun SettingsLightPreview() = BizBiteTheme(darkTheme = false) {
    SettingsContent(accountName = "BizBite Cafe", copies = "1", autoCut = true, cashDrawer = false, paired = true, versionName = "1.0.0", versionCode = 1, onCopiesChanged = {}, onAutoCutChanged = {}, onCashDrawerChanged = {}, onOpenPrinters = {}, onOpenPairing = {}, onOpenPrinterHistory = {}, onCheckUpdates = {}, onNotifications = {}, onSecurityPrivacy = {}, onExportDiagnostics = {}, onUnpairRequest = {}, onLogoutRequest = {})
}

@Preview(name = "Settings Dark")
@Composable
private fun SettingsDarkPreview() = BizBiteTheme(darkTheme = true) {
    SettingsContent(accountName = "BizBite Cafe", copies = "1", autoCut = true, cashDrawer = false, paired = true, versionName = "1.0.0", versionCode = 1, onCopiesChanged = {}, onAutoCutChanged = {}, onCashDrawerChanged = {}, onOpenPrinters = {}, onOpenPairing = {}, onOpenPrinterHistory = {}, onCheckUpdates = {}, onNotifications = {}, onSecurityPrivacy = {}, onExportDiagnostics = {}, onUnpairRequest = {}, onLogoutRequest = {})
}
