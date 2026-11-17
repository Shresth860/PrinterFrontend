package com.bizbite.printer.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navOptions
import com.bizbite.printer.ui.MainViewModel
import com.bizbite.printer.ui.billing.BillingScreen
import com.bizbite.printer.ui.login.LoginScreen
import com.bizbite.printer.ui.screens.DashboardScreen
import com.bizbite.printer.ui.screens.HistoryScreen
import com.bizbite.printer.ui.screens.PairingScreen
import com.bizbite.printer.ui.screens.PrinterSetupScreen
import com.bizbite.printer.ui.screens.SettingsScreen
import com.bizbite.printer.ui.screens.SellerOrderHistoryScreen
import com.bizbite.printer.ui.screens.UpdateDialog
import com.bizbite.printer.ui.screens.WelcomeScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    val context = LocalContext.current
    val uiPreferences = remember { context.getSharedPreferences("ui_preferences", android.content.Context.MODE_PRIVATE) }
    var hasSeenWelcome by remember { mutableStateOf(uiPreferences.getBoolean("welcome_seen", false)) }
    val session: SessionViewModel = hiltViewModel()
    val loggedIn by session.isLoggedIn.collectAsState()
    val model: MainViewModel = hiltViewModel()
    val paired by model.paired.collectAsState()
    val update by model.update.collectAsState()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val tabsVisible = loggedIn && current in appTabs.map { it.route }

    LaunchedEffect(loggedIn, current) {
        if (current == null) return@LaunchedEffect
        if (loggedIn && current == "login") {
            navController.navigate("dashboard") { popUpTo("login") { inclusive = true }; launchSingleTop = true }
        } else if (!loggedIn && current != "login") {
            navController.navigate("login") { popUpTo(navController.graph.id) { inclusive = true }; launchSingleTop = true }
        }
    }

    if (!hasSeenWelcome && !loggedIn) {
        WelcomeScreen(
            onGetStarted = {
                uiPreferences.edit().putBoolean("welcome_seen", true).apply()
                hasSeenWelcome = true
            },
            onSignIn = {
                uiPreferences.edit().putBoolean("welcome_seen", true).apply()
                hasSeenWelcome = true
            }
        )
    } else {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing, bottomBar = {
            if (tabsVisible) BottomBar(current) { route ->
                navController.navigate(route, navOptions {
                    popUpTo("dashboard") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                })
            }
        }) { padding ->
            NavHost(navController, startDestination = "login", modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
                composable("login") { LoginScreen(onLoggedIn = { }) }
                composable("dashboard") { DashboardScreen(model, onOpenPrinters = { navController.navigate("printers") { launchSingleTop = true } }, onOpenOrders = { navController.navigate("history") }, onOpenHistory = { navController.navigate("printer-history") }) }
                composable("billing") { BillingScreen(onOpenHistory = { navController.navigate("history") }, onOpenPrintSettings = { navController.navigate("settings") { launchSingleTop = true } }) }
                composable("settings") {
                    SettingsScreen(model, onUnpair = { model.unpair() }, onOpenPairing = { navController.navigate("pair") }, onLogout = { session.logout() }, onOpenPrinterHistory = { navController.navigate("printer-history") }, onOpenPrinters = { navController.navigate("printers") { launchSingleTop = true } })
                }
                composable("pair") {
                    PairingScreen(model)
                }
                composable("printers") { PrinterSetupScreen(model) }
                composable("printer-history") { HistoryScreen(model) }
                composable("history") { SellerOrderHistoryScreen() }
            }
        }

        update?.let { release -> UpdateDialog(release = release, onDismiss = { if (!release.mandatory) model.dismissUpdate() }, onUpdate = { model.downloadAndInstallUpdate(release) }) }
    }
}
