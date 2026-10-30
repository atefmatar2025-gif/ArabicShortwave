package com.arabicsw.kiwi.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A warm "valve radio" palette: deep teal cabinet, amber dial light.
val Amber = Color(0xFFE7A33E)
val OnAir = Color(0xFFD9452B)

private val Light = lightColorScheme(
    primary = Color(0xFF155E5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE8E4),
    onPrimaryContainer = Color(0xFF07302E),
    secondary = Color(0xFF9A5B13),
    secondaryContainer = Color(0xFFFFE2BC),
    onSecondaryContainer = Color(0xFF341B00),
    background = Color(0xFFF7F3EA),
    surface = Color(0xFFFFFCF6),
    surfaceVariant = Color(0xFFEDE6D8),
    onSurfaceVariant = Color(0xFF4C463C),
    outline = Color(0xFFB9AF9E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD0C8),
    onPrimary = Color(0xFF00201E),
    primaryContainer = Color(0xFF124845),
    onPrimaryContainer = Color(0xFFCDE8E4),
    secondary = Amber,
    secondaryContainer = Color(0xFF4F3200),
    onSecondaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF0F1515),
    surface = Color(0xFF151C1C),
    surfaceVariant = Color(0xFF243030),
    onSurfaceVariant = Color(0xFFC0C9C7),
    outline = Color(0xFF5B6766),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
