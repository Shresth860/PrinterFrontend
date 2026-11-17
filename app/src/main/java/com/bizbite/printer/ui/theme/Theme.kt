package com.bizbite.printer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = AppBackground,
    primaryContainer = LightGreen,
    onPrimaryContainer = PrimaryText,
    secondary = SecondaryText,
    background = AppBackground,
    onBackground = PrimaryText,
    surface = AppSurface,
    onSurface = PrimaryText,
    surfaceVariant = AppSurface,
    onSurfaceVariant = SecondaryText,
    outline = BorderColor,
    error = ErrorColor
)

private val DarkColors = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = AppBackground,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF14532D),
    onPrimaryContainer = LightGreen,
    secondary = DarkSecondaryText,
    background = DarkBackground,
    onBackground = DarkPrimaryText,
    surface = DarkSurface,
    onSurface = DarkPrimaryText,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1F2937),
    onSurfaceVariant = DarkSecondaryText,
    outline = DarkBorder,
    error = androidx.compose.ui.graphics.Color(0xFFF87171)
)

@Composable
fun BizBiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
