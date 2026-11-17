package com.bizbite.printer.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bizbite.printer.R
import com.bizbite.printer.ui.theme.LightGreen

data class AppTab(val route: String, val labelRes: Int, val iconRes: Int)

val appTabs = listOf(
    AppTab("dashboard", R.string.home_tab, R.drawable.ic_home),
    AppTab("printers", R.string.printers_tab, R.drawable.ic_printer),
    AppTab("billing", R.string.billing_tab, R.drawable.ic_billing),
    AppTab("printer-history", R.string.history_tab, R.drawable.ic_history),
    AppTab("settings", R.string.settings_tab, R.drawable.ic_settings)
)

@Composable
fun BottomBar(selectedRoute: String?, onSelect: (String) -> Unit) {
    NavigationBar(tonalElevation = 0.dp) {
        appTabs.forEach { tab ->
            val label = stringResource(tab.labelRes)
            NavigationBarItem(
                selected = selectedRoute == tab.route,
                onClick = { onSelect(tab.route) },
                icon = {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = label
                    )
                },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    indicatorColor = LightGreen.copy(alpha = if (androidx.compose.foundation.isSystemInDarkTheme()) 0.2f else 1f)
                )
            )
        }
    }
}
