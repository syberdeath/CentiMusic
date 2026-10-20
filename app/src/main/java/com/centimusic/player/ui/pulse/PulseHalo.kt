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
 * Drawn around circular artwork of diameter [artworkFraction] × this canvas: a thin red ring that
 * hugs the artwork and swells with the pulse, and outside it a ring of short white dot columns (one
 * per [ArtworkPulse.history] slot, newest at 12 o'clock).
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

        // Thin ring hugging the circular artwork; faint at rest, red and swelling while playing.
        val ringR = artHalf * (1.04f + 0.11f * level)
        drawCircle(restColor, radius = artHalf * 1.04f, center = c, alpha = 0.12f * (1f - activity), style = Stroke(width = 1f * density))
        if (activity <= 0.001f) return@Canvas
        drawCircle(
            accent,
            radius = ringR,
            center = c,
            alpha = activity * (0.45f + 0.5f * level),
            style = Stroke(width = (1.5f + 1.5f * level) * density),
        )

        // Dot columns around the artwork. Newest slice at 12 o'clock, older ones clockwise.
        val n = history.size
        if (n < 2) return@Canvas
        // Dots live in their own band outside the ring's full swing, so the two never overlap.
        val baseR = artHalf * 1.18f
        val spacing = (half * 0.99f - baseR) / 2.5f
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
