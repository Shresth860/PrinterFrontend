package com.bizbite.printer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bizbite.printer.R
import com.bizbite.printer.data.auth.AutoPairState
import com.bizbite.printer.domain.model.ConnectionType
import com.bizbite.printer.domain.model.JobStatus
import com.bizbite.printer.domain.model.PrintJob
import com.bizbite.printer.domain.model.PrinterConfig
import com.bizbite.printer.domain.model.PrinterStatus
import com.bizbite.printer.ui.theme.ErrorColor
import com.bizbite.printer.ui.theme.OfflineColor
import com.bizbite.printer.ui.theme.OnlineColor
import com.bizbite.printer.ui.theme.QueueColor

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Int? = null,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 52.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) { Text(text) }
}

@Composable
fun AutoPairNotice(state: AutoPairState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        AutoPairState.NotPaired, AutoPairState.Pairing -> Text(
            stringResource(R.string.printer_setup_in_progress),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        AutoPairState.Paired -> Text(
            stringResource(R.string.printer_setup_connected),
            color = OnlineColor,
            modifier = modifier
        )
        is AutoPairState.Failed -> Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.printer_setup_failed, state.reason), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            if (state.retryable) TextButton(onClick = onRetry, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

enum class StatusKind { ONLINE, OFFLINE, ERROR, QUEUED, NEUTRAL }

@Composable
fun StatusChip(label: String, kind: StatusKind, modifier: Modifier = Modifier) {
    val color = when (kind) {
        StatusKind.ONLINE -> OnlineColor
        StatusKind.OFFLINE -> OfflineColor
        StatusKind.ERROR -> ErrorColor
        StatusKind.QUEUED -> QueueColor
        StatusKind.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.11f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(label, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: Int,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    badge: String? = null
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                badge?.let { Text(it, color = accent, style = MaterialTheme.typography.labelSmall) }
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PrinterCard(
    printer: PrinterConfig,
    status: PrinterStatus?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null
) {
    val statusKind = when (status) {
        PrinterStatus.CONNECTED -> StatusKind.ONLINE
        PrinterStatus.DISCONNECTED, PrinterStatus.CONNECTION_LOST -> StatusKind.OFFLINE
        PrinterStatus.BUSY -> StatusKind.QUEUED
        PrinterStatus.PAPER_OUT, PrinterStatus.COVER_OPEN, PrinterStatus.PRINT_TIMEOUT, PrinterStatus.PRINT_FAILURE -> StatusKind.ERROR
        else -> StatusKind.NEUTRAL
    }
    val statusLabel = when (statusKind) {
        StatusKind.ONLINE -> stringResource(R.string.printer_status_online)
        StatusKind.OFFLINE -> stringResource(R.string.printer_status_offline)
        StatusKind.ERROR -> stringResource(R.string.printer_status_error)
        StatusKind.QUEUED -> stringResource(R.string.status_busy)
        StatusKind.NEUTRAL -> stringResource(R.string.printer_status_unknown)
    }
    val typeLabel = when (printer.type) {
        ConnectionType.BLUETOOTH, ConnectionType.BLE -> stringResource(R.string.connection_bluetooth)
        ConnectionType.USB -> stringResource(R.string.connection_usb)
        ConnectionType.LAN -> stringResource(R.string.connection_wifi)
        ConnectionType.MOCK -> stringResource(R.string.connection_other)
    }
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_printer), contentDescription = stringResource(R.string.printer_icon_description), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(printer.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (printer.isDefault) Text(stringResource(R.string.printer_default), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                }
                Text("$typeLabel · ${printer.address}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusChip(statusLabel, statusKind)
            }
            actions?.invoke()
        }
    }
}

@Composable
fun JobCard(
    job: PrintJob,
    timeLabel: String,
    modifier: Modifier = Modifier,
    actions: @Composable (() -> Unit)? = null
) {
    val (icon, label, kind) = when (job.status) {
        JobStatus.SUCCESS, JobStatus.COMPLETED -> Triple(
            R.drawable.ic_check_circle,
            if (job.errorCode == "DELIVERY_UNCONFIRMED") stringResource(R.string.sent_unconfirmed) else stringResource(R.string.successful),
            if (job.errorCode == "DELIVERY_UNCONFIRMED") StatusKind.NEUTRAL else StatusKind.ONLINE
        )
        JobStatus.PENDING -> Triple(R.drawable.ic_clock, stringResource(R.string.in_queue), StatusKind.QUEUED)
        JobStatus.PRINTING -> Triple(R.drawable.ic_clock, stringResource(R.string.printing), StatusKind.QUEUED)
        JobStatus.FAILED -> Triple(R.drawable.ic_error_circle, stringResource(R.string.failed), StatusKind.ERROR)
        JobStatus.CANCELLED -> Triple(R.drawable.ic_error_circle, stringResource(R.string.cancelled), StatusKind.NEUTRAL)
    }
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), contentDescription = label, tint = when (kind) { StatusKind.ONLINE -> OnlineColor; StatusKind.QUEUED -> QueueColor; StatusKind.ERROR -> ErrorColor; else -> MaterialTheme.colorScheme.onSurfaceVariant }, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.job_number, job.id.takeLast(8)), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.job_type_receipt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(label, kind)
            }
            Text(timeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actions?.invoke()
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        if (actionText != null && onAction != null) {
            androidx.compose.material3.TextButton(onClick = onAction, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text(actionText) }
        }
    }
}

@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.search_icon_description)) },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun AccessibleIconButton(icon: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(48.dp).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
    }
}
