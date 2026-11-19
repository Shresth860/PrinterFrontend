package com.bizbite.printer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bizbite.printer.R
import com.bizbite.printer.data.auth.AutoPairState
import com.bizbite.printer.ui.MainViewModel

@Composable
fun PairingScreen(model: MainViewModel) {
    val paired by model.paired.collectAsState()
    val state by model.autoPairState.collectAsState()

    LaunchedEffect(Unit) {
        if (paired != true) model.retryPairing()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.pair_printer_account),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.auto_pair_description),
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )

        when (state) {
            AutoPairState.NotPaired, AutoPairState.Pairing -> {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.auto_pair_in_progress),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            AutoPairState.Paired -> {
                Text(
                    text = stringResource(R.string.auto_pair_success),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is AutoPairState.Failed -> {
                val failure = state as AutoPairState.Failed
                Text(
                    text = stringResource(R.string.auto_pair_failed, failure.reason),
                    color = MaterialTheme.colorScheme.error
                )
                if (failure.retryable) {
                    Button(
                        onClick = { model.retryPairing() },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text(stringResource(R.string.retry_automatic_pairing))
                    }
                }
            }
        }
    }
}
