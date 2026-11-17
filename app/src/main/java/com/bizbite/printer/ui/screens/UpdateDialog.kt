package com.bizbite.printer.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.bizbite.printer.data.remote.ReleaseInfo

@Composable
fun UpdateDialog(release: ReleaseInfo, onDismiss: () -> Unit, onUpdate: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (release.mandatory) "Required app update" else "Update available") },
        text = { Text("Version ${release.versionName} is available.${if (release.mandatory) " Install this update to continue." else ""}") },
        confirmButton = { Button(onClick = onUpdate) { Text("Download and install") } },
        dismissButton = if (release.mandatory) null else ({ TextButton(onClick = onDismiss) { Text("Later") } })
    )
}
