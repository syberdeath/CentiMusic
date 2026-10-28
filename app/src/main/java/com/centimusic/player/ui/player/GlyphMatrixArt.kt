package com.centimusic.player.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import com.centimusic.player.data.media.ArtworkLoader
import com.centimusic.player.data.media.Song
import com.centimusic.player.ui.theme.NmTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin

/** Cells across the round matrix, like the 25×25 Glyph Matrix on the back of Phone (3). */
private const val GRID = 25

/**
 * The artwork redrawn as a round Glyph-style dot matrix: each dot's size follows the brightness of
 * that part of the cover, dots swell with the music, and the outer ring fills with red dots as the
 * song plays. When the track changes, the matrix lights up from the centre outwards.
 */
@Composable
fun GlyphMatrixArt(
    song: Song,
    level: () -> Float,
    progress: () -> Float,
    timeSec: () -> Float,
    modifier: Modifier = Modifier,
) {
    val colors = NmTheme.colors
    val context = LocalContext.current
    val invert = !colors.isDark
    val cells by produceState<FloatArray?>(null, song.artworkKey) {
        value = withContext(Dispatchers.Default) {
            val bmp = ArtworkLoader.load(context, song, 192)
            bmp?.let { sample(it) } ?: placeholderCells(song.albumKey.hashCode())
        }
    }
    val reveal = remember(song.id) { Animatable(0f) }
    LaunchedEffect(song.id) { reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    Canvas(modifier) {
        val data = cells ?: return@Canvas
        val step = size.minDimension / GRID
        val maxR = step * 0.46f
        val c = (GRID - 1) / 2f
        val radius = GRID / 2f
        val lvl = level().coerceIn(0f, 1f)
        val prog = progress().coerceIn(0f, 1f)
        val t = timeSec()
        val shown = reveal.value
        for (y in 0 until GRID) {
            for (x in 0 until GRID) {
                val dx = x - c
                val dy = y - c
                val d = hypot(dx, dy)
                if (d > radius - 0.15f) continue
                val center = Offset(step * (x + 0.5f), step * (y + 0.5f))
                // Light up from the middle outwards after a track change.
                val appear = ((shown * (radius + 3f) - d) / 3f).coerceIn(0f, 1f)
                if (appear <= 0f) continue
                val outer = d > radius - 1.3f
                if (outer) {
                    // Progress ring: red dots clockwise from 12 o'clock.
                    val angle = ((atan2(dx, -dy) / (2 * PI).toFloat()) + 1f) % 1f
                    val played = angle <= prog
                    drawCircle(
                        if (played) colors.accent else colors.textPrimary,
                        radius = maxR * (if (played) 0.62f else 0.32f) * appear,
                        center = center,
                        alpha = if (played) 1f else 0.25f,
                    )
                    continue
                }
                var b = data[y * GRID + x]
                if (invert) b = 1f - b
                // Music makes the matrix breathe, more in the middle than at the edge.
                val swell = 1f + 0.35f * lvl * (1f - d / radius)
                val shimmer = 0.92f + 0.08f * sin(t * 2.2f + (x + y) * 0.5f)
                val r = maxR * (0.12f + 0.88f * b) * swell * shimmer * appear
                drawCircle(colors.textPrimary, radius = r.coerceAtMost(maxR * 1.1f), center = center, alpha = 0.25f + 0.75f * b)
            }
        }
    }
}

/** Brightness (0–1) of each cell, from a small centred square crop of the cover. */
private fun sample(source: Bitmap): FloatArray {
    val side = minOf(source.width, source.height)
    val cropped = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
    val small = Bitmap.createScaledBitmap(cropped, GRID, GRID, true)
    val px = IntArray(GRID * GRID)
    small.getPixels(px, 0, GRID, 0, 0, GRID, GRID)
    val lum = FloatArray(px.size) { i ->
        val p = px[i]
        val r = (p shr 16 and 0xFF) / 255f
        val g = (p shr 8 and 0xFF) / 255f
        val b = (p and 0xFF) / 255f
        0.2126f * r + 0.7152f * g + 0.0722f * b
    }
    // Stretch contrast so dull covers still read as an image.
    val lo = lum.min()
    val hi = lum.max()
    val span = (hi - lo).coerceAtLeast(0.08f)
    return FloatArray(lum.size) { ((lum[it] - lo) / span).coerceIn(0f, 1f) }
}

/** For covers without art: concentric rings, offset per album so each looks different. */
private fun placeholderCells(seed: Int): FloatArray {
    val ox = (seed and 0x7) - 3.5f
    val oy = (seed shr 3 and 0x7) - 3.5f
    val c = (GRID - 1) / 2f
    return FloatArray(GRID * GRID) { i ->
        val d = hypot(i % GRID - c - ox * 0.6f, i / GRID - c - oy * 0.6f)
        0.5f + 0.5f * sin(d * 0.9f)
    }
}
