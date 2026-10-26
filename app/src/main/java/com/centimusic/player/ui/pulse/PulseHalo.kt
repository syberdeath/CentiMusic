package com.centimusic.player.ui.pulse

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
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
            PulseStyle.GLYPH -> glyph(g, dotColor, accent)
            PulseStyle.DOT_RING -> dotRing(g, dotColor, accent)
            PulseStyle.ECHO -> echo(g, dotColor, accent)
            PulseStyle.MATRIX -> matrix(g, dotColor, accent)
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

/** Nothing Glyph-style: 16 arc segments that light up with the loudness of their slice. */
private fun DrawScope.glyph(g: Geometry, dotColor: Color, accent: Color) {
    val n = 16
    val r = g.artHalf * 1.13f
    val gapDeg = 7f
    val sweep = 360f / n - gapDeg
    for (i in 0 until n) {
        var sum = 0f
        val slots = 4
        for (k in 0 until slots) sum += g.value(i * slots + k, n * slots)
        val v = sum / slots
        val startDeg = -90f - 180f / n + gapDeg / 2f + i * 360f / n
        drawArc(
            color = if (i == 0) accent else dotColor,
            startAngle = startDeg,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2f, r * 2f),
            alpha = g.activity * (0.12f + 0.88f * v),
            style = Stroke(width = (3f + 2f * v) * density, cap = StrokeCap.Round),
        )
    }
}

/** A single ring of dots; each dot's size and brightness follow the loudness at its angle. */
private fun DrawScope.dotRing(g: Geometry, dotColor: Color, accent: Color) {
    val n = 60
    val r = g.artHalf * 1.15f
    for (i in 0 until n) {
        val a = angle(i, n)
        val v = g.value(i, n)
        drawCircle(
            if (i == 0) accent else dotColor,
            radius = (1.2f + 2.6f * v) * density * if (i == 0) 1.3f else 1f,
            center = Offset(center.x + cos(a) * r, center.y + sin(a) * r),
            alpha = g.activity * (0.3f + 0.7f * v),
        )
    }
}

/** Three thin concentric rings that spread a little with loudness; the inner one is red. */
private fun DrawScope.echo(g: Geometry, dotColor: Color, accent: Color) {
    val rings = listOf(1.05f to 0.9f, 1.13f to 0.5f, 1.21f to 0.25f)
    rings.forEachIndexed { k, (base, strength) ->
        drawCircle(
            if (k == 0) accent else dotColor,
            radius = g.artHalf * (base + 0.035f * (k + 1) * g.level),
            center = center,
            alpha = g.activity * strength * (0.4f + 0.6f * g.level),
            style = Stroke(width = (if (k == 0) 1.8f else 1.4f) * density),
        )
    }
}

/** Square dot lattice around the artwork, lit outward by the loudness at each angle. */
private fun DrawScope.matrix(g: Geometry, dotColor: Color, accent: Color) {
    val step = 7f * density
    val dot = 1.5f * density
    val r0 = g.artHalf * 1.07f
    val r1 = g.bandEnd
    val n = (r1 / step).toInt() + 1
    for (gx in -n..n) {
        for (gy in -n..n) {
            val x = gx * step
            val y = gy * step
            val r = kotlin.math.sqrt(x * x + y * y)
            if (r < r0 || r > r1) continue
            val a = (kotlin.math.atan2(y, x) + PI.toFloat() / 2f + 2f * PI.toFloat()) % (2f * PI.toFloat())
            val slot = (a / (2f * PI.toFloat()) * 72).toInt().coerceIn(0, 71)
            val v = g.value(slot, 72)
            val on = (r - r0) / (r1 - r0) < v
            drawCircle(dotColor, radius = dot, center = Offset(center.x + x, center.y + y), alpha = g.activity * if (on) 0.85f else 0.08f)
        }
    }
}
