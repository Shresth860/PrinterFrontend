package com.bizbite.printer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.bizbite.printer.R

private val GoogleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val Inter = GoogleFont("Inter")
val AppFontFamily = FontFamily(
    Font(googleFont = Inter, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = Inter, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = Inter, fontProvider = GoogleFontsProvider, weight = FontWeight.SemiBold),
    Font(googleFont = Inter, fontProvider = GoogleFontsProvider, weight = FontWeight.Bold)
)

val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold),
        displayMedium = displayMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold),
        headlineLarge = headlineLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(fontFamily = AppFontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = AppFontFamily),
        bodySmall = bodySmall.copy(fontFamily = AppFontFamily),
        labelLarge = labelLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(fontFamily = AppFontFamily)
    )
}
