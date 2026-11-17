package com.bizbite.printer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

/** Backwards-compatible entry point retained for MainActivity. */
@Composable
fun PrinterNavigation(navController: NavHostController) = AppNavHost(navController)
