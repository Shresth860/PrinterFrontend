package com.bizbite.printer.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bizbite.printer.R
import com.bizbite.printer.ui.components.PrimaryButton
import com.bizbite.printer.ui.components.SecondaryButton
import com.bizbite.printer.ui.theme.BizBiteTheme
import com.bizbite.printer.ui.theme.LightGreen

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, onSignIn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(12.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(224.dp).background(LightGreen.copy(alpha = if (androidx.compose.foundation.isSystemInDarkTheme()) 0.18f else 0.8f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_printer), contentDescription = stringResource(R.string.welcome_illustration_description), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp))
                    Icon(painterResource(R.drawable.ic_billing), contentDescription = stringResource(R.string.welcome_illustration_description), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                }
            }
            Spacer(Modifier.height(34.dp))
            Icon(painterResource(R.drawable.ic_printer), contentDescription = stringResource(R.string.login_logo_description), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.brand_name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.welcome_tagline), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp, 8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
            }
            PrimaryButton(stringResource(R.string.welcome_get_started), onGetStarted, Modifier.fillMaxWidth())
            SecondaryButton(stringResource(R.string.welcome_sign_in), onSignIn, Modifier.fillMaxWidth())
        }
    }
}

@Preview(name = "Welcome Light")
@Composable
private fun WelcomeLightPreview() = BizBiteTheme(darkTheme = false) { WelcomeScreen({}, {}) }

@Preview(name = "Welcome Dark")
@Composable
private fun WelcomeDarkPreview() = BizBiteTheme(darkTheme = true) { WelcomeScreen({}, {}) }
