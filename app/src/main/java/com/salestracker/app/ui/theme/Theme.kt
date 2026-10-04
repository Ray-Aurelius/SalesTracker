package com.salestracker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B5E5A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBCEBE5),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFFB4651A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC0),
    onSecondaryContainer = Color(0xFF2E1500),
    tertiary = Color(0xFF3A5BA0),
    background = Color(0xFFF7FAF9),
    surface = Color(0xFFF7FAF9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD3CB),
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504B),
    onPrimaryContainer = Color(0xFFBCEBE5),
    secondary = Color(0xFFF2B36B),
    onSecondary = Color(0xFF4B2800),
    secondaryContainer = Color(0xFF6B3B00),
    onSecondaryContainer = Color(0xFFFFDCC0),
    tertiary = Color(0xFFA8C0F0),
    background = Color(0xFF101413),
    surface = Color(0xFF101413),
)

@Composable
fun SalesTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
