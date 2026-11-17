package com.bizbite.printer.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.net.Uri
import com.bizbite.printer.ui.MainViewModel

@Composable
fun PairingScreen(model: MainViewModel) {
    var code by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    var showCameraRationale by remember { mutableStateOf(false) }
    val busy by model.busy.collectAsState()
    val message by model.message.collectAsState()
    val backendAvailable by model.backendAvailable.collectAsState()
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showScanner = true else showCameraRationale = true
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Connect your seller account")
        Text("Enter the pairing code shown in your BizBite dashboard, or scan its QR code.")
        Text(
            when (backendAvailable) {
                true -> "Print service: connected"
                false -> "Print service unavailable. Check your connection and retry shortly; the server may be starting."
                null -> "Connecting to print service…"
            },
            modifier = Modifier.padding(top = 8.dp)
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isLetterOrDigit).take(8).uppercase() },
            label = { Text("8-character pairing code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        )
        Button(
            onClick = { model.pair(code) },
            enabled = !busy && code.length == 8,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) { Text(if (busy) "Connecting…" else "Pair device") }
        OutlinedButton(
            onClick = { cameraPermission.launch(Manifest.permission.CAMERA) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("Scan pairing QR") }
        message?.let { Text(it, modifier = Modifier.padding(top = 12.dp)) }
    }

    if (showScanner) {
        Dialog(onDismissRequest = { showScanner = false }) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Scan the pairing QR code")
                    CameraQrScanner(
                        onCode = { scanned ->
                            val parsed = Uri.parse(scanned)
                            val suppliedCode = parsed.getQueryParameter("code")
                                ?: parsed.getQueryParameter("pairing_code")
                                ?: scanned
                            code = suppliedCode.filter(Char::isLetterOrDigit).take(8).uppercase()
                            showScanner = false
                            if (code.length == 8) model.pair(code)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                    OutlinedButton(onClick = { showScanner = false }) { Text("Cancel") }
                }
            }
        }
    }
    if (showCameraRationale) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCameraRationale = false },
            title = { Text("Camera permission required") },
            text = { Text("Allow camera access to scan a pairing QR code, or enter the code manually.") },
            confirmButton = { Button(onClick = { showCameraRationale = false }) { Text("OK") } }
        )
    }
}
