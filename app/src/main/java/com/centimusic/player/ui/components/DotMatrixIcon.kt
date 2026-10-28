package com.centimusic.player.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.centimusic.player.ui.theme.NmTheme
import kotlin.math.PI
import kotlin.math.sin

/**
 * An icon redrawn as a dot-matrix display: the vector is rasterised onto a small grid, lit cells
 * become dots with a slow shimmer sweeping across, unlit cells stay as a faint matrix, and the
 * top-right dot is red.
 */
@Composable
fun DotMatrixIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    grid: Int = 16,
    color: Color = NmTheme.colors.textPrimary,
    accent: Color = NmTheme.colors.accent,
) {
    val painter = rememberVectorPainter(icon)
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val lit = remember(icon, grid) {
        // Draw at 4x and keep a cell if any of its pixels is solid enough, so thin strokes survive.
        val scale = 4
        val res = grid * scale
        val bitmap = ImageBitmap(res, res)
        val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
        // Leave a one-cell margin so the shape doesn't touch the edge of the matrix.
        val inset = scale.toFloat()
        CanvasDrawScope().draw(density, direction, canvas, Size(res.toFloat(), res.toFloat())) {
            drawContext.transform.translate(inset, inset)
            with(painter) { draw(Size(res - 2 * inset, res - 2 * inset)) }
        }
        val pixels = IntArray(res * res)
        bitmap.readPixels(pixels)
        BooleanArray(grid * grid) { cell ->
            val cx = cell % grid
            val cy = cell / grid
            var hit = false
            for (y in cy * scale until (cy + 1) * scale) {
                for (x in cx * scale until (cx + 1) * scale) {
                    if ((pixels[y * res + x] ushr 24) > 110) hit = true
                }
            }
            hit
        }
    }
    val phase by rememberInfiniteTransition(label = "matrix").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing)),
        label = "sweep",
    )
    Canvas(modifier.size(size)) {
        val step = this.size.width / grid
        val r = step * 0.34f
        for (cell in 0 until grid * grid) {
            val cx = cell % grid
            val cy = cell / grid
            val center = Offset(step * (cx + 0.5f), step * (cy + 0.5f))
            if (lit[cell]) {
                // Diagonal shimmer: each dot brightens as the sweep passes over it.
                val wave = 0.5f + 0.5f * sin(2f * PI.toFloat() * (phase - (cx + cy) / (2f * grid)))
                drawCircle(color, radius = r, center = center, alpha = 0.55f + 0.45f * wave)
            } else {
                drawCircle(color, radius = r * 0.7f, center = center, alpha = 0.07f)
            }
        }
        drawCircle(accent, radius = r * 1.25f, center = Offset(step * (grid - 0.5f), step * 0.5f))
    }
}

/** Tiny 3-column dot equalizer marking the song that's playing, in place of a static dot. */
@Composable
fun PlayingDots(modifier: Modifier = Modifier, color: Color = NmTheme.colors.accent, dot: Dp = 3.dp) {
    val phase by rememberInfiniteTransition(label = "playing").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "phase",
    )
    val rows = 4
    Canvas(modifier.size(width = dot * 5, height = dot * (rows * 2 - 1))) {
        val d = dot.toPx()
        for (c in 0 until 3) {
            val wave = 0.5f + 0.5f * sin(2f * PI.toFloat() * (phase + c * 0.33f))
            val lit = 1 + (wave * (rows - 1)).toInt()
            for (r in 0 until rows) {
                drawCircle(
                    color,
                    radius = d / 2,
                    center = Offset(d / 2 + c * 2 * d, size.height - d / 2 - r * 2 * d),
                    alpha = if (r < lit) 1f else 0.18f,
                )
            }
        }
    }
}
