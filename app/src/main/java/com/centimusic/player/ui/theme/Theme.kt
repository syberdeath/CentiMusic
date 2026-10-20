package com.centimusic.player.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.centimusic.player.data.preferences.ThemeMode

object NmTheme {
    val colors: NmColors
        @Composable get() = LocalNmColors.current
    val type: NmType
        @Composable get() = LocalNmType.current
}

@Composable
fun NothingMusicTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkNmColors else LightNmColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    // Material components (dialogs, sliders, text fields, sheets) pick up the same palette.
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            secondary = colors.textPrimary, onSecondary = colors.background,
            background = colors.background, onBackground = colors.textPrimary,
            surface = colors.background, onSurface = colors.textPrimary,
            surfaceVariant = colors.surface, onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface,
            surfaceContainerHighest = colors.surfaceHigh, surfaceContainerLow = colors.surface,
            outline = colors.divider, outlineVariant = colors.divider,
            error = colors.accent,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            secondary = colors.textPrimary, onSecondary = colors.background,
            background = colors.background, onBackground = colors.textPrimary,
            surface = colors.background, onSurface = colors.textPrimary,
            surfaceVariant = colors.surface, onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface,
            surfaceContainerHighest = colors.surfaceHigh, surfaceContainerLow = colors.surface,
            outline = colors.divider, outlineVariant = colors.divider,
            error = colors.accent,
        )
    }

    CompositionLocalProvider(LocalNmColors provides colors, LocalNmType provides DefaultNmType) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
