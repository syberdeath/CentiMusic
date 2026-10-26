package com.centimusic.player.ui.pulse

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import com.centimusic.player.data.preferences.PulseStyle

/**
 * Drawn around circular artwork of diameter [artworkFraction] × this canvas. [timeSec] only
 * advances while music plays; [ArtworkPulse.level] / [ArtworkPulse.history] carry loudness (live
 * when measurable, otherwise the slow decorative breath).
 */
@Composable
fun PulseHalo(
    pulse: ArtworkPulse,
    artworkFraction: Float,
    timeSec: () -> Float,
    modifier: Modifier = Modifier,
    style: PulseStyle = PulseStyle.HALO,
    dotColor: Color = Color.White,
    accent: Color = Color(0xFFFF3030),
    restColor: Color = Color.White,
) {
    Canvas(modifier) {
        val g = Geometry(
            activity = pulse.activity,
            level = pulse.level,
            history = pulse.history,
            half = size.minDimension / 2f,
            artHalf = size.minDimension / 2f * artworkFraction,
            time = timeSec(),
        )
        // Faint outline at rest so the shape is still there while paused.
        drawCircle(restColor, radius = g.artHalf * 1.04f, center = center, alpha = 0.12f * (1f - g.activity), style = Stroke(width = 1f * density))
        if (g.activity <= 0.001f) return@Canvas
        when (style) {
            PulseStyle.HALO -> halo(g, dotColor, accent)
            PulseStyle.RIPPLE -> ripple(g, dotColor, accent)
            PulseStyle.SUNBURST -> sunburst(g, dotColor, accent)
            PulseStyle.ORBIT -> orbit(g, dotColor, accent)
            PulseStyle.GLOW -> glow(g, dotColor, accent)
            PulseStyle.MINIMAL -> ring(g, accent)
        }
    }
}

private class Geometry(
    val activity: Float,
    val level: Float,
    val history: FloatArray,
    val half: Float,
    val artHalf: Float,
    val time: Float,
) {
    /** Radial room between the artwork and the canvas edge. */
    val bandStart get() = artHalf * 1.06f
    val bandEnd get() = half * 0.99f

    /** Loudness for slot [i] of [n] around the circle, newest at 12 o'clock. */
    fun value(i: Int, n: Int): Float {
        if (history.size < 2) return level
        val idx = history.size - 1 - (i * history.size / n)
        return history[idx.coerceIn(0, history.lastIndex)]
    }
}

private fun angle(i: Int, n: Int, offsetRad: Float = 0f) = -PI.toFloat() / 2f + offsetRad + 2f * PI.toFloat() * i / n

private fun DrawScope.ring(g: Geometry, accent: Color) {
    drawCircle(
        accent,
        radius = g.artHalf * (1.04f + 0.11f * g.level),
        center = center,
        alpha = g.activity * (0.45f + 0.5f * g.level),
        style = Stroke(width = (1.5f + 1.5f * g.level) * density),
    )
}

private fun DrawScope.halo(g: Geometry, dotColor: Color, accent: Color) {
    ring(g, accent)
    val n = 72
    val baseR = g.artHalf * 1.18f
    val spacing = (g.bandEnd - baseR) / 2.5f
    val dotR = (spacing * 0.28f).coerceAtMost(2.2f * density)
    val rot = g.time * 3f * (PI / 180.0).toFloat()
    for (i in 0 until n) {
        val a = angle(i, n, rot)
        val lit = g.value(i, n) * 3f
        for (k in 0 until 3) {
            val on = (lit - k).coerceIn(0f, 1f)
            val r = baseR + spacing * k
            drawCircle(dotColor, radius = dotR, center = Offset(center.x + cos(a) * r, center.y + sin(a) * r), alpha = g.activity * (0.10f + 0.80f * on))
        }
    }
}

private fun DrawScope.ripple(g: Geometry, dotColor: Color, accent: Color) {
    val waves = 4
    for (w in 0 until waves) {
        val t = ((g.time * 0.45f + w.toFloat() / waves) % 1f + 1f) % 1f
        val fade = (1f - t).pow(1.5f)
        drawCircle(
            if (w == 0) accent else dotColor,
            radius = g.bandStart + (g.bandEnd - g.bandStart) * t,
            center = center,
            alpha = g.activity * fade * (0.18f + 0.6f * g.level),
            style = Stroke(width = (1f + 2.5f * g.level * (1f - t)) * density),
        )
    }
    ring(g, accent)
}

private fun DrawScope.sunburst(g: Geometry, dotColor: Color, accent: Color) {
    val n = 96
    val band = g.bandEnd - g.bandStart
    val width = (2f * PI.toFloat() * g.bandStart / n * 0.45f).coerceIn(1.5f * density, 3.5f * density)
    for (i in 0 until n) {
        val a = angle(i, n)
        val v = g.value(i, n)
        val inner = g.bandStart
        val outer = inner + band * (0.08f + 0.92f * v)
        drawLine(
            if (i == 0) accent else dotColor,
            start = Offset(center.x + cos(a) * inner, center.y + sin(a) * inner),
            end = Offset(center.x + cos(a) * outer, center.y + sin(a) * outer),
            strokeWidth = width,
            cap = StrokeCap.Round,
            alpha = g.activity * (0.25f + 0.7f * v),
        )
    }
}

private fun DrawScope.orbit(g: Geometry, dotColor: Color, accent: Color) {
    val rings = 3
    val band = g.bandEnd - g.bandStart
    for (k in 0 until rings) {
        val n = 36 + k * 12
        val baseR = g.bandStart + band * (0.2f + 0.3f * k)
        val dir = if (k % 2 == 0) 1f else -1f
        val rot = dir * g.time * (12f + 8f * k) * (PI / 180.0).toFloat()
        val dotR = (1.4f + 0.5f * k) * density
        for (i in 0 until n) {
            val a = angle(i, n, rot)
            val v = g.value(i, n)
            val r = baseR + band * 0.08f * v
            val leader = k == rings - 1 && i == 0
            drawCircle(
                if (leader) accent else dotColor,
                radius = if (leader) dotR * 1.8f else dotR,
                center = Offset(center.x + cos(a) * r, center.y + sin(a) * r),
                alpha = g.activity * if (leader) 1f else (0.12f + 0.8f * v),
            )
        }
    }
}

private fun DrawScope.glow(g: Geometry, dotColor: Color, accent: Color) {
    val outer = g.artHalf + (g.half - g.artHalf) * (0.55f + 0.45f * g.level)
    drawCircle(
        Brush.radialGradient(
            0f to accent.copy(alpha = 0f),
            g.artHalf / outer to accent.copy(alpha = 0.55f * g.activity * (0.3f + 0.7f * g.level)),
            1f to accent.copy(alpha = 0f),
            center = center,
            radius = outer,
        ),
        radius = outer,
        center = center,
    )
    drawCircle(dotColor, radius = g.artHalf * 1.03f, center = center, alpha = g.activity * 0.35f, style = Stroke(width = 1f * density))
}
