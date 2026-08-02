package com.norvexa.clearup.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = NorvexaBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = NorvexaMint,
    background = Cloud,
    onBackground = Ink,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = Ink,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE9EEF6),
    onSurfaceVariant = Slate,
    error = RiskRed,
)

private val DarkColors = darkColorScheme(
    primary = NorvexaBlueDark,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF0A1C55),
    secondary = NorvexaMintDark,
    background = Night,
    onBackground = androidx.compose.ui.graphics.Color(0xFFF2F4F7),
    surface = NightSurface,
    onSurface = androidx.compose.ui.graphics.Color(0xFFF2F4F7),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1A2638),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB8C3D6),
    error = RiskRed,
)

private val AmoledColors = darkColorScheme(
    primary = NorvexaBlueDark,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF081846),
    secondary = NorvexaMintDark,
    background = AmoledBlack,
    onBackground = androidx.compose.ui.graphics.Color.White,
    surface = AmoledSurface,
    onSurface = androidx.compose.ui.graphics.Color.White,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFC8C8C8),
    error = RiskRed,
)

@Composable
fun ClearUpTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val colors = when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDark) DarkColors else LightColors
        ThemeMode.LIGHT -> LightColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.AMOLED -> AmoledColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = ClearUpTypography,
        content = content,
    )
}
