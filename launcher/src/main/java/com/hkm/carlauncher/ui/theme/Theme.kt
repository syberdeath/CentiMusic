package com.hkm.carlauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The launcher's dark, high-contrast palette (accent is the blue from the HKM logo). */
object LauncherColors {
    val Background = Color(0xFF07090C)
    val Card = Color(0xFF12161C)
    val Raised = Color(0xFF181D25)
    val Outline = Color(0xFF232A34)
    val Text = Color(0xFFF2F5F8)
    val Muted = Color(0xFF8A95A3)
    val Accent = Color(0xFF4F86F7)
    val Ok = Color(0xFF7EE2A8)
}

private val scheme = darkColorScheme(
    primary = LauncherColors.Accent,
    onPrimary = Color.White,
    background = LauncherColors.Background,
    onBackground = LauncherColors.Text,
    surface = LauncherColors.Card,
    onSurface = LauncherColors.Text,
    surfaceVariant = LauncherColors.Raised,
    onSurfaceVariant = LauncherColors.Muted,
    outline = LauncherColors.Outline,
)

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
