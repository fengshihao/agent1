package com.dynamicui.demo.productivity.ui.view

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF1C1915)
private val Paper = Color(0xFFF4F1EB)
private val Card = Color(0xFFFFFCF8)
private val Teal = Color(0xFF0E6B66)
private val TealContainer = Color(0xFFD7F3EF)
private val Sand = Color(0xFFE6E0D6)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = Color(0xFF042E2C),
    secondary = Color(0xFF5C564C),
    onSecondary = Color.White,
    secondaryContainer = Sand,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Sand,
    onSurfaceVariant = Color(0xFF5C564C),
    surfaceContainerLowest = Card,
    surfaceContainerLow = Color(0xFFF8F5F0),
    outline = Color(0xFFD2CBBF),
    error = Color(0xFFB42318),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8EDDD6),
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF0E4F4B),
    onPrimaryContainer = Color(0xFFD7F3EF),
    secondary = Color(0xFFD2CBBF),
    background = Color(0xFF141311),
    onBackground = Color(0xFFF4F1EB),
    surface = Color(0xFF141311),
    onSurface = Color(0xFFF4F1EB),
    surfaceVariant = Color(0xFF2C2924),
    onSurfaceVariant = Color(0xFFD2CBBF),
    surfaceContainerLowest = Color(0xFF1C1A17),
    surfaceContainerLow = Color(0xFF221F1B),
    outline = Color(0xFF4A453D),
)

@Composable
fun ProductivityTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
