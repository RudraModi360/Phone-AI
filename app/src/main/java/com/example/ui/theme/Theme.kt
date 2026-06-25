package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PremiumPrimaryDark,
    background = PremiumBgDark,
    surface = PremiumSurfaceDark,
    surfaceVariant = PremiumCardSurfaceDark,
    onPrimary = Color.Black,
    onBackground = PremiumTextPrimaryDark,
    onSurface = PremiumTextPrimaryDark,
    secondary = PremiumTextSecondaryDark,
    outline = PremiumBorderDark,
    outlineVariant = PremiumBorderDark.copy(alpha = 0.5f)
)

private val LightColorScheme = lightColorScheme(
    primary = PremiumPrimaryLight,
    background = PremiumBgLight,
    surface = PremiumSurfaceLight,
    surfaceVariant = PremiumSurfaceLight,
    onPrimary = Color.White,
    onBackground = PremiumTextPrimaryLight,
    onSurface = PremiumTextPrimaryLight,
    secondary = PremiumTextSecondaryLight,
    outline = PremiumBorderLight,
    outlineVariant = PremiumBorderLight.copy(alpha = 0.5f)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: String = "Dark", // "Light", "Dark", "Adaptive"
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> darkTheme // Adaptive / System defaults
    }

    val colorScheme = if (isDark) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
