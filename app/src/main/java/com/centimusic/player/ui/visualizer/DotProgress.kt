package com.centimusic.player.ui.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import com.centimusic.player.playback.AudioLevelMeter
import kotlin.math.max

/**
 * Dotted progress line driven by the real playback position: played dots are white, the current
 * position is a red dot. Tap or drag to seek.
 */
@Composable
fun DotProgress(
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    dots: Int = 48,
    onScrub: (Float?) -> Unit = {},
    playedColor: Color = Color.White,
    restColor: Color = Color.White,
    accent: Color = Color(0xFFFF3030),
) {
    val duration by rememberUpdatedState(durationMs)
    val scrub by rememberUpdatedState(onScrub)
    val seek by rememberUpdatedState(onSeek)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // Gentle breathing of the red dot while playing (frozen when paused).
    var glow by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { glow = (((it - start) / 1_000_000_000f) % 1.6f) / 1.6f }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .semantics {
                contentDescription = "Playback position"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { seek((it.coerceIn(0f, 1f) * duration).toLong()); true }
            }
            .pointerInput(Unit) {
                detectTapGestures { o -> if (duration > 0) seek(((o.x / size.width).coerceIn(0f, 1f) * duration).toLong()) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragFraction = (it.x / size.width).coerceIn(0f, 1f)
                        scrub(dragFraction)
                    },
                    onDragEnd = {
                        dragFraction?.let { f -> if (duration > 0) seek((f * duration).toLong()) }
                        dragFraction = null
                        scrub(null)
                    },
                    onDragCancel = {
                        dragFraction = null
                        scrub(null)
                    },
                ) { change, _ ->
                    change.consume()
                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    scrub(dragFraction)
                }
            },
    ) {
        val step = size.width / dots
        val r = minOf(step * 0.22f, 2.2.dp.toPx())
        val y = size.height / 2
        val current = (fraction * (dots - 1)).toInt()
        for (i in 0 until dots) {
            val x = step * (i + 0.5f)
            when {
                i == current -> {
                    val pulse = if (playing) 1f - kotlin.math.abs(glow * 2f - 1f) else 0f
                    drawCircle(accent, radius = r * (2.4f + 1.6f * pulse), center = Offset(x, y), alpha = 0.18f * pulse)
                    drawCircle(accent, radius = r * 1.9f, center = Offset(x, y))
                }
                i < current -> drawCircle(playedColor, radius = r, center = Offset(x, y), alpha = 0.9f)
                else -> drawCircle(restColor, radius = r, center = Offset(x, y), alpha = 0.18f)
            }
        }
    }
}
