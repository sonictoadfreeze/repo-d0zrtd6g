package com.inkvpn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// InkVPN palette (dark OLED + cyberpunk)
val InkBackground = Color(0xFF0A0E1A)
val InkSurface = Color(0xFF111827)
val InkSurfaceVariant = Color(0xFF1E293B)
val InkPrimary = Color(0xFF6C63FF)
val InkSecondary = Color(0xFF00D4FF)
val InkSuccess = Color(0xFF00FF88)
val InkWarning = Color(0xFFFFB800)
val InkDanger = Color(0xFFFF4757)
val InkText = Color(0xFFE2E8F0)
val InkSubtext = Color(0xFF64748B)

private val InkColorScheme = darkColorScheme(
    primary = InkPrimary,
    secondary = InkSecondary,
    tertiary = InkSuccess,
    background = InkBackground,
    surface = InkSurface,
    surfaceVariant = InkSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = InkText,
    onSurface = InkText,
    error = InkDanger,
)

@Composable
fun InkVPNTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = InkColorScheme,
        typography = InkTypography,
        content = content,
    )
}
