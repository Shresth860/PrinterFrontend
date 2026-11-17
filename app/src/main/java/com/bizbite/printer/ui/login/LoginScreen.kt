package com.bizbite.printer.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bizbite.printer.R
import com.bizbite.printer.ui.components.PrimaryButton
import com.bizbite.printer.ui.theme.BizBiteTheme

@Composable
fun LoginScreen(onLoggedIn: () -> Unit, viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    androidx.compose.runtime.LaunchedEffect(state.step) { if (state.step == LoginStep.SUCCESS) onLoggedIn() }
    LoginContent(
        phone = state.phone, pin = state.pin, loading = state.loading, error = state.error,
        onPhone = viewModel::phoneChanged, onPin = viewModel::pinChanged, onLogin = viewModel::login
    )
}

@Composable
private fun LoginContent(
    phone: String,
    pin: String,
    loading: Boolean,
    error: String?,
    onPhone: (String) -> Unit,
    onPin: (String) -> Unit,
    onLogin: () -> Unit
) {
    val focus = LocalFocusManager.current
    var pinVisible by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp)) {
        IconButton(onClick = { focus.clearFocus() }, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back), tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            Text("B", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.welcome_back), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.sign_in_continue), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 28.dp))
        OutlinedTextField(
            value = phone, onValueChange = onPhone, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.phone_number)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })
        )
        OutlinedTextField(
            value = pin, onValueChange = onPin, modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            label = { Text(stringResource(R.string.pin)) }, singleLine = true,
            visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { pinVisible = !pinVisible }) {
                    Text(stringResource(if (pinVisible) R.string.hide else R.string.show))
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); onLogin() })
        )
        TextButton(onClick = {}, modifier = Modifier.align(Alignment.End).height(48.dp)) {
            Text(stringResource(R.string.forgot_pin), color = MaterialTheme.colorScheme.primary)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp)) }
        PrimaryButton(
            text = stringResource(R.string.sign_in), onClick = onLogin, enabled = !loading,
            loading = loading, modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(name = "Login Light")
@Composable
private fun LoginLightPreview() = BizBiteTheme(darkTheme = false) {
    LoginContent("9876543210", "1234", false, null, {}, {}, {})
}

@Preview(name = "Login Dark")
@Composable
private fun LoginDarkPreview() = BizBiteTheme(darkTheme = true) {
    LoginContent("9876543210", "1234", false, null, {}, {}, {})
}
