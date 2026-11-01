package com.centimusic.calculator.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import com.centimusic.calculator.R

/** Doto (SIL OFL): the dot-matrix display face shared with CentiMusic. */
val DotFamily = FontFamily(
    Font(R.font.doto_semibold, FontWeight.SemiBold),
    Font(R.font.doto_bold, FontWeight.Bold),
)

val AccentRed = Color(0xFFFF3030)

/** CentiMusic's monochrome palette, plus key colours. */
@Immutable
data class CalcColors(
    val isDark: Boolean,
    val background: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val dotGrid: Color,
    val digitKey: Color,
    val functionKey: Color,
    val sciKey: Color,
    val operatorKey: Color,
    val onOperatorKey: Color,
    val accent: Color = AccentRed,
    val onAccent: Color = Color.White,
)

val DarkCalcColors = CalcColors(
    isDark = true,
    background = Color(0xFF080808),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA0A0A0),
    textTertiary = Color(0xFF7E7E7E),
    divider = Color(0xFF252525),
    dotGrid = Color(0x14FFFFFF),
    digitKey = Color(0xFF161616),
    functionKey = Color(0xFF262626),
    sciKey = Color(0xFF101010),
    operatorKey = Color(0xFFFFFFFF),
    onOperatorKey = Color(0xFF080808),
)

val LightCalcColors = CalcColors(
    isDark = false,
    background = Color(0xFFFAFAF8),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF686868),
    textTertiary = Color(0xFF72726E),
    divider = Color(0xFFE5E5E2),
    dotGrid = Color(0x16000000),
    digitKey = Color(0xFFEFEFEC),
    functionKey = Color(0xFFE0E0DC),
    sciKey = Color(0xFFF4F4F1),
    operatorKey = Color(0xFF111111),
    onOperatorKey = Color(0xFFFAFAF8),
)

val LocalCalcColors = staticCompositionLocalOf { DarkCalcColors }

@Composable
fun CentiCalcTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
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
    CompositionLocalProvider(LocalCalcColors provides if (dark) DarkCalcColors else LightCalcColors, content = content)
}
