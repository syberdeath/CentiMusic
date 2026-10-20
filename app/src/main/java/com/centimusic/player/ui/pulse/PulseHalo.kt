package com.centimusic.player.ui.pulse

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Drawn *behind* a rounded-square artwork of side [artworkFraction] × this canvas: a thin red ring
 * that swells with the pulse, and a ring of short white dot columns (one per [ArtworkPulse.history]
 * slot) that peek out around the edges. The artwork's corners naturally hide part of the halo.
 *
 * [rotationDeg] is supplied by the caller (it only advances while playing).
 */
@Composable
fun PulseHalo(
    pulse: ArtworkPulse,
    artworkFraction: Float,
    rotationDeg: () -> Float,
    modifier: Modifier = Modifier,
    dotColor: Color = Color.White,
    accent: Color = Color(0xFFFF3030),
    restColor: Color = Color.White,
) {
    Canvas(modifier) {
        val activity = pulse.activity
        val level = pulse.level
        val history = pulse.history
        val half = size.minDimension / 2f
        val artHalf = half * artworkFraction
        val c = center

        // Static hairline so the geometry is visible (faintly) even when paused.
        drawCircle(restColor, radius = half * 0.985f, center = c, alpha = 0.06f, style = Stroke(width = 1f))
        if (activity <= 0.001f) return@Canvas

        // Pulsing red ring: rests just outside the artwork's edges and swells with loudness.
        val ringR = artHalf * 1.05f + (half * 0.97f - artHalf * 1.05f) * level
        drawCircle(
            accent,
            radius = ringR,
            center = c,
            alpha = activity * (0.25f + 0.55f * level),
            style = Stroke(width = 1.5f * density + 1.5f * density * level),
        )

        // Dot columns around the artwork. Newest slice at 12 o'clock, older ones clockwise.
        val n = history.size
        if (n < 2) return@Canvas
        val baseR = artHalf * 1.09f
        val spacing = (half - baseR) / 3.2f
        val dotR = (spacing * 0.28f).coerceAtMost(2.2f * density)
        val rot = rotationDeg() * (PI / 180.0).toFloat()
        for (i in 0 until n) {
            val v = history[n - 1 - i]
            val angle = -PI.toFloat() / 2f + rot + 2f * PI.toFloat() * i / n
            val ca = cos(angle)
            val sa = sin(angle)
            val lit = v * 3f
            for (k in 0 until 3) {
                val on = (lit - k).coerceIn(0f, 1f)
                val r = baseR + spacing * k
                drawCircle(
                    dotColor,
                    radius = dotR,
                    center = Offset(c.x + ca * r, c.y + sa * r),
                    alpha = activity * (0.10f + 0.80f * on),
                )
            }
        }
    }
}
