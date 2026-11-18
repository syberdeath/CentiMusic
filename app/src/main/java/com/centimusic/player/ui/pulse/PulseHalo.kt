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
            PulseStyle.ORBIT -> orbit(g, dotColor, accent)
            PulseStyle.SONAR -> sonar(g, dotColor, accent)
            PulseStyle.GALAXY -> galaxy(g, dotColor, accent)
            PulseStyle.WAVE -> wave(g, dotColor, accent)
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
    ripples(g, accent, count = 2, period = 2.4f)
    ring(g, accent)
    // A small red dot circles the ring.
    val orbitA = g.time * 0.9f - PI.toFloat() / 2f
    val orbitR = g.artHalf * (1.04f + 0.11f * g.level)
    drawCircle(accent, radius = (2.6f + 1.6f * g.level) * density, center = Offset(center.x + cos(orbitA) * orbitR, center.y + sin(orbitA) * orbitR), alpha = g.activity)
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

/** Loudness with a floor, so every style keeps moving during quiet passages. */
private val Geometry.drive get() = 0.25f + 0.75f * level

private fun DrawScope.dotAt(color: Color, angle: Float, r: Float, radius: Float, alpha: Float) =
    drawCircle(color, radius = radius, center = Offset(center.x + cos(angle) * r, center.y + sin(angle) * r), alpha = alpha.coerceIn(0f, 1f))

/** Thin rings that leave the artwork and fade as they spread, one every [period] seconds. */
private fun DrawScope.ripples(g: Geometry, color: Color, count: Int, period: Float) {
    for (k in 0 until count) {
        val p = ((g.time / period) + k.toFloat() / count) % 1f
        val r = g.bandStart + (g.bandEnd - g.bandStart) * p
        drawCircle(color, radius = r, center = center, alpha = g.activity * (1f - p) * (0.15f + 0.35f * g.level), style = Stroke(width = 1.2f * density))
    }
}

/** Three orbits of dots turning at different speeds and directions; the orbits swell with the music. */
private fun DrawScope.orbit(g: Geometry, dotColor: Color, accent: Color) {
    val orbits = 3
    for (o in 0 until orbits) {
        val frac = (o + 0.5f) / orbits
        val baseR = g.bandStart + (g.bandEnd - g.bandStart) * frac * 0.92f
        val r = baseR + (g.bandEnd - g.bandStart) * 0.06f * g.level
        val n = 10 + o * 6
        val speed = (if (o % 2 == 0) 1f else -1f) * (0.55f - o * 0.12f) * (0.7f + 0.6f * g.drive)
        val rot = g.time * speed
        // Faint track
        drawCircle(dotColor, radius = r, center = center, alpha = g.activity * 0.06f, style = Stroke(width = 1f))
        for (i in 0 until n) {
            val a = angle(i, n, rot)
            val v = g.value(i, n)
            // Each orbit leads with a bright "comet" dot followed by a fading tail.
            val tail = (i.toFloat() / n)
            val size = (1.2f + 1.8f * v + 1.4f * (1f - tail)) * density * (1f - o * 0.12f)
            dotAt(if (o == 0 && i == 0) accent else dotColor, a, r, size, g.activity * (0.25f + 0.75f * (1f - tail) * (0.5f + 0.5f * v)))
        }
    }
}

/** Sonar: rings of dots keep spreading outward; they leave faster and brighter on loud parts. */
private fun DrawScope.sonar(g: Geometry, dotColor: Color, accent: Color) {
    val rings = 4
    val period = 2.2f - 0.8f * g.level
    val n = 48
    for (k in 0 until rings) {
        val p = ((g.time / period) + k.toFloat() / rings) % 1f
        val r = g.bandStart + (g.bandEnd - g.bandStart) * p
        val fade = (1f - p) * (0.35f + 0.65f * g.drive)
        val leading = k == 0
        val spin = g.time * 0.15f * (if (k % 2 == 0) 1f else -1f)
        for (i in 0 until n) {
            val a = angle(i, n, spin)
            val v = g.value(i, n)
            dotAt(
                if (leading) accent else dotColor,
                a,
                r + (g.bandEnd - g.bandStart) * 0.05f * v,
                (1.1f + 1.6f * v) * density * (1f - 0.4f * p),
                g.activity * fade,
            )
        }
    }
    ring(g, accent)
}

/** Galaxy: spiral arms of dots spin around the artwork and stretch outward with loudness. */
private fun DrawScope.galaxy(g: Geometry, dotColor: Color, accent: Color) {
    val arms = 4
    val perArm = 16
    val rot = g.time * (0.35f + 0.4f * g.drive)
    val reach = 0.55f + 0.45f * g.drive
    for (arm in 0 until arms) {
        val armOffset = 2f * PI.toFloat() * arm / arms
        for (j in 0 until perArm) {
            val t = j.toFloat() / (perArm - 1)
            val r = g.bandStart + (g.bandEnd - g.bandStart) * t * reach
            val a = rot + armOffset + t * 2.2f
            val v = g.value(arm * perArm + j, arms * perArm)
            val twinkle = 0.75f + 0.25f * sin(g.time * 3f + j * 0.8f + arm)
            dotAt(
                if (j == 0 && arm == 0) accent else dotColor,
                a,
                r,
                (2.4f - 1.4f * t + 1.2f * v) * density,
                g.activity * (1f - t * 0.75f) * twinkle,
            )
        }
    }
    ring(g, accent)
}

/** Wave: two dotted waves roll around the cover in opposite directions; loud parts make them taller. */
private fun DrawScope.wave(g: Geometry, dotColor: Color, accent: Color) {
    val n = 96
    val mid = (g.bandStart + g.bandEnd) / 2f
    val amp = (g.bandEnd - g.bandStart) * 0.42f
    for (layer in 0 until 2) {
        val dir = if (layer == 0) 1f else -1f
        val lobes = if (layer == 0) 6f else 4f
        val phase = g.time * 1.4f * dir
        for (i in 0 until n) {
            val th = 2f * PI.toFloat() * i / n
            val v = g.value(i, n)
            val h = sin(th * lobes + phase) * amp * (0.35f + 0.65f * g.drive) * (0.6f + 0.4f * v)
            dotAt(
                if (layer == 0) accent else dotColor,
                th - PI.toFloat() / 2f,
                mid + h,
                (if (layer == 0) 1.6f else 1.3f) * density * (0.8f + 0.6f * v),
                g.activity * (if (layer == 0) 0.9f else 0.55f),
            )
        }
    }
}
