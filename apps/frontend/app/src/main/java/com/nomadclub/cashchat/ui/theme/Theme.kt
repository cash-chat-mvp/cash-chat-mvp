package com.nomadclub.cashchat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = WebPrimary,
    onPrimary = WebPrimaryForeground,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    secondary = WebAccent,
    onSecondary = Color.White,
    error = Color(0xFFCF6679),
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = WebPrimary,
    onPrimary = WebPrimaryForeground,
    primaryContainer = Color(0xFFE3E5FF),
    onPrimaryContainer = Color(0xFF1A1F7C),
    background = WebBackground,
    surface = WebCard,
    surfaceVariant = WebInputBackground,
    onBackground = Color(0xFF252525),
    onSurface = Color(0xFF1F2937),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE5E7EB),
    outlineVariant = Color(0xFFF0F0F2),
    secondary = WebAccent,
    onSecondary = Color.White,
    error = Color(0xFFB3261E),
    onError = Color.White
)

@Composable
fun CashChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
