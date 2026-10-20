package com.centimusic.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.centimusic.player.ui.theme.NmTheme

/** The home screen's planet-like disc with record grooves, partially off the right edge. */
@Composable
fun HeroOrb(modifier: Modifier = Modifier) {
    val colors = NmTheme.colors
    val grain = Grain.brush()
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w * 0.86f, h * 0.44f)
        val radius = minOf(w * 0.62f, h * 0.62f)
        val ring = colors.textPrimary

        for (i in 0..7) {
            drawCircle(
                ring.copy(alpha = if (colors.isDark) 0.06f - i * 0.005f else 0.07f - i * 0.006f),
                radius = radius * (1.04f + i * 0.09f),
                center = center,
                style = Stroke(width = 1.2f),
            )
        }
        val (lit, shade) = if (colors.isDark) Color(0xFF3A3A3A) to Color(0xFF090909) else Color(0xFFFFFFFF) to Color(0xFFCFCFCA)
        drawCircle(
            Brush.radialGradient(listOf(lit, shade), center = Offset(center.x - radius * 0.45f, center.y - radius * 0.35f), radius = radius * 1.5f),
            radius = radius,
            center = center,
        )
        drawCircle(grain, radius = radius, center = center, alpha = if (colors.isDark) 0.35f else 0.25f)
        // Inner label of the "record".
        drawCircle(ring.copy(alpha = 0.06f), radius = radius * 0.32f, center = center, style = Stroke(width = 1.2f))
    }
}
