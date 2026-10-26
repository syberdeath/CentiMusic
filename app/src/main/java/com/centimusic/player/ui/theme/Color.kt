package com.centimusic.player.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val AccentRed = Color(0xFFFF3030)

@Immutable
data class NmColors(
    val isDark: Boolean,
    val background: Color,
    /** Slightly raised surfaces: sheets, cards, search field. */
    val surface: Color,
    /** Icon circles, placeholders, pressed states. */
    val surfaceHigh: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val divider: Color,
    /** Strong contrast fill (the big play button): black on light, white on dark. */
    val inverse: Color,
    val onInverse: Color,
)

val DarkNmColors = NmColors(
    isDark = true,
    background = Color(0xFF080808),
    surface = Color(0xFF121212),
    surfaceHigh = Color(0xFF1B1B1B),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA0A0A0),
    textTertiary = Color(0xFF7E7E7E), // ≥4.5:1 on the dark background
    accent = AccentRed,
    onAccent = Color.White,
    divider = Color(0xFF252525),
    inverse = Color(0xFFFFFFFF),
    onInverse = Color(0xFF080808),
)

val LightNmColors = NmColors(
    isDark = false,
    background = Color(0xFFFAFAF8),
    surface = Color(0xFFF1F1EE),
    surfaceHigh = Color(0xFFE9E9E5),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF686868),
    textTertiary = Color(0xFF72726E), // ≥4.5:1 on the light background
    accent = AccentRed,
    onAccent = Color.White,
    divider = Color(0xFFE5E5E2),
    inverse = Color(0xFF111111),
    onInverse = Color(0xFFFAFAF8),
)

val LocalNmColors = staticCompositionLocalOf { DarkNmColors }
