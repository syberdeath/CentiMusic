package com.centimusic.player.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.centimusic.player.data.media.ArtworkLoader
import com.centimusic.player.data.media.Song
import com.centimusic.player.ui.theme.NmColors
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing
import kotlin.math.abs

/**
 * Square album artwork with a rounded crop. Falls back to a generated monochrome placeholder,
 * chosen deterministically per album so the library still looks varied without artwork.
 */
@Composable
fun Artwork(
    song: Song?,
    modifier: Modifier = Modifier,
    corner: Dp = Spacing.artworkCorner,
    sizeHint: Dp = 64.dp,
    /** Applied to the image inside the rounded clip, e.g. a slow zoom that stays within the frame. */
    contentModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { sizeHint.roundToPx() }
    val bitmap by produceState(initialValue = song?.let { ArtworkLoader.cached(it, px) ?: ArtworkLoader.cachedAny(it) }, song?.artworkKey, px) {
        value = song?.let { ArtworkLoader.load(context, it, px) }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(NmTheme.colors.surfaceHigh),
    ) {
        Crossfade(targetState = bitmap, animationSpec = tween(220), label = "artwork") { bmp ->
            if (bmp != null) {
                val image = remember(bmp) { bmp.asImageBitmap() }
                Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().then(contentModifier))
            } else {
                ArtworkPlaceholder(seed = song?.albumKey?.hashCode() ?: 0, modifier = Modifier.fillMaxSize().then(contentModifier))
            }
        }
    }
}

/** Geometric, grainy monochrome artwork used when a track has none. */
@Composable
fun ArtworkPlaceholder(seed: Int, modifier: Modifier = Modifier) {
    val colors = NmTheme.colors
    val style = abs(seed) % 4
    Canvas(modifier.grain(if (colors.isDark) 0.22f else 0.18f)) {
        val w = size.width
        val h = size.height
        val (base, light, dark) = placeholderTones(colors)
        drawRect(base)
        when (style) {
            0 -> { // Off-centre sphere.
                val c = Offset(w * 0.58f, h * 0.46f)
                drawCircle(
                    Brush.radialGradient(listOf(light, dark), center = Offset(c.x - w * 0.12f, c.y - h * 0.14f), radius = w * 0.55f),
                    radius = w * 0.34f,
                    center = c,
                )
            }
            1 -> { // Split sphere with a vertical cut, like a vinyl being pulled from its sleeve.
                val c = Offset(w * 0.5f, h * 0.5f)
                clipRect(right = w * 0.55f) { drawRect(light.copy(alpha = 0.35f)) }
                drawCircle(Brush.radialGradient(listOf(light, dark), center = c, radius = w * 0.4f), radius = w * 0.3f, center = c)
                drawLine(dark, Offset(w * 0.55f, 0f), Offset(w * 0.55f, h), strokeWidth = w * 0.025f)
            }
            2 -> { // Concentric grooves.
                val c = Offset(w * 0.5f, h * 0.5f)
                for (i in 1..6) {
                    drawCircle(light.copy(alpha = 0.55f - i * 0.06f), radius = w * 0.07f * i, center = c, style = Stroke(width = w * 0.008f))
                }
                drawCircle(dark, radius = w * 0.06f, center = c)
            }
            else -> { // Horizon: half disc rising from the bottom.
                drawCircle(
                    Brush.verticalGradient(listOf(light, base), startY = h * 0.35f, endY = h),
                    radius = w * 0.42f,
                    center = Offset(w * 0.5f, h * 0.86f),
                )
                drawCircle(Color(0xFFFF3030), radius = w * 0.035f, center = Offset(w * 0.8f, h * 0.2f))
            }
        }
    }
}

private fun placeholderTones(colors: NmColors): Triple<Color, Color, Color> =
    if (colors.isDark) {
        Triple(Color(0xFF151515), Color(0xFF4A4A4A), Color(0xFF0B0B0B))
    } else {
        Triple(Color(0xFFE4E4E0), Color(0xFFFFFFFF), Color(0xFF9D9D98))
    }
