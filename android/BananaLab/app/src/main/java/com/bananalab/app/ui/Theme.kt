package com.bananalab.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BananaLight = lightColorScheme(
    primary = Color(0xFFE7B73A),
    onPrimary = Color(0xFF17120C),
    primaryContainer = Color(0xFFFFE7A3),
    onPrimaryContainer = Color(0xFF3F2C00),
    secondary = Color(0xFF2C7A7B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB7ECEC),
    onSecondaryContainer = Color(0xFF002020),
    tertiary = Color(0xFF8B5CF6),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F3E8),
    onBackground = Color(0xFF1F1A12),
    surface = Color(0xFFFFFBF2),
    onSurface = Color(0xFF1F1A12),
    surfaceVariant = Color(0xFFE7E0D3),
    onSurfaceVariant = Color(0xFF4B4336),
    outline = Color(0xFF8E8371),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val BananaDark = darkColorScheme(
    primary = Color(0xFFFFC94D),
    onPrimary = Color(0xFF201700),
    primaryContainer = Color(0xFF5A4300),
    onPrimaryContainer = Color(0xFFFFE7A3),
    secondary = Color(0xFF6CE0D2),
    onSecondary = Color(0xFF003737),
    secondaryContainer = Color(0xFF004F4F),
    onSecondaryContainer = Color(0xFFB7ECEC),
    tertiary = Color(0xFFC4B5FD),
    onTertiary = Color(0xFF311A63),
    background = Color(0xFF12100D),
    onBackground = Color(0xFFF4EEDF),
    surface = Color(0xFF1A1713),
    onSurface = Color(0xFFF4EEDF),
    surfaceVariant = Color(0xFF4A4237),
    onSurfaceVariant = Color(0xFFE8DBC6),
    outline = Color(0xFF9B8F7C),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

@Composable
fun BananaLabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) BananaDark else BananaLight,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        content = content,
    )
}

