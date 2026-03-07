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
    background = Color(0xFF1C1C1E),
    surface = Color(0xFF2C2C2E),
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = WebPrimary,
    onPrimary = WebPrimaryForeground,
    background = WebBackground,
    surface = WebCard,
    onBackground = Color(0xFF252525),
    onSurface = Color(0xFF252525)
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