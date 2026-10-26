package com.centimusic.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.centimusic.player.ui.pulse.rememberIsVisible
import com.centimusic.player.ui.pulse.rememberMotionEnabled
import com.centimusic.player.ui.theme.NmTheme
import kotlin.math.pow

/** Clock for the hero's emitted rings: one unit = one ring travelling from the disc to the edge. */
@Stable
class EmissionClock {
    var phase by mutableFloatStateOf(0f)
        internal set
}

/**
 * Advances only while Home is visible and system animations are on. Rings drift out slowly when
 * idle and faster while music plays.
 */
@Composable
fun rememberEmissionClock(playing: Boolean): EmissionClock {
    val clock = remember { EmissionClock() }
    val visible = rememberIsVisible()
    val motion = rememberMotionEnabled()
    val speed by rememberUpdatedState(if (playing) PLAYING_RINGS_PER_SEC else IDLE_RINGS_PER_SEC)
    LaunchedEffect(visible, motion) {
        if (!visible || !motion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                clock.phase += ((now - last) / 1_000_000_000f).coerceAtMost(0.1f) * speed
                last = now
            }
        }
    }
    return clock
}

private const val IDLE_RINGS_PER_SEC = 0.10f
private const val PLAYING_RINGS_PER_SEC = 0.26f
private const val WAVES = 6

/**
 * The home screen's planet-like disc, partially off the right edge, emitting rings that expand
 * and fade like sound leaving a speaker. [level] (0..1, read during draw) brightens the rings.
 */
@Composable
fun HeroOrb(
    modifier: Modifier = Modifier,
    clock: EmissionClock? = null,
    level: () -> Float = { 0f },
) {
    val colors = NmTheme.colors
    val grain = Grain.brush()
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w * 0.86f, h * 0.44f)
        val radius = minOf(w * 0.62f, h * 0.62f)
        val ring = colors.textPrimary
        val phase = clock?.phase ?: 0f
        val loud = level().coerceIn(0f, 1f)

        // Emitted waves: each starts at the disc's edge, travels outward and fades away.
        for (i in 0 until WAVES) {
            val t = ((phase + i.toFloat() / WAVES) % 1f + 1f) % 1f
            val fade = (1f - t).pow(1.6f)
            val base = if (colors.isDark) 0.11f else 0.13f
            drawCircle(
                ring,
                radius = radius * (1.02f + 0.9f * t),
                center = center,
                alpha = fade * (base + 0.22f * loud),
                style = Stroke(width = (1f + 1.6f * (1f - t)) * density * 0.8f),
            )
        }
        // A red echo of the newest wave, only when the music is actually loud.
        if (loud > 0.05f) {
            val t = ((phase % 1f) + 1f) % 1f
            drawCircle(
                colors.accent,
                radius = radius * (1.02f + 0.9f * t),
                center = center,
                alpha = (1f - t).pow(2f) * 0.35f * loud,
                style = Stroke(width = 1.5f * density),
            )
        }

        // The disc breathes very slightly with the music.
        val r = radius * (1f + 0.012f * loud)
        val (lit, shade) = if (colors.isDark) Color(0xFF3A3A3A) to Color(0xFF090909) else Color(0xFFFFFFFF) to Color(0xFFCFCFCA)
        drawCircle(
            Brush.radialGradient(listOf(lit, shade), center = Offset(center.x - r * 0.45f, center.y - r * 0.35f), radius = r * 1.5f),
            radius = r,
            center = center,
        )
        drawCircle(grain, radius = r, center = center, alpha = if (colors.isDark) 0.35f else 0.25f)
        // Inner label of the "record".
        drawCircle(ring.copy(alpha = 0.06f), radius = r * 0.32f, center = center, style = Stroke(width = 1.2f))
    }
}
