package com.centimusic.player.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.centimusic.player.ui.theme.NmTheme

/**
 * Thin red progress line with a round thumb. Tap or drag to seek; while dragging the parent is
 * told the scrub fraction so the time label can follow the finger.
 */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onScrub: (Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NmTheme.colors
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration by rememberUpdatedState(durationMs)
    val seek by rememberUpdatedState(onSeek)
    val scrub by rememberUpdatedState(onScrub)
    val played = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val fraction = dragFraction ?: played

    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics {
                contentDescription = "Playback position"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { target ->
                    seek((target.coerceIn(0f, 1f) * duration).toLong())
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (duration > 0) seek((offset.x / size.width).coerceIn(0f, 1f).times(duration).toLong())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        scrub(dragFraction)
                    },
                    onDragEnd = {
                        dragFraction?.let { if (duration > 0) seek((it * duration).toLong()) }
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
        val y = size.height / 2
        val stroke = 2.dp.toPx()
        val x = size.width * fraction
        drawLine(colors.divider, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(colors.accent, Offset(0f, y), Offset(x, y), strokeWidth = stroke, cap = StrokeCap.Round)
        val thumb = if (dragFraction != null) 8.dp.toPx() else 6.dp.toPx()
        drawCircle(colors.textPrimary, radius = thumb, center = Offset(x, y))
    }
}
