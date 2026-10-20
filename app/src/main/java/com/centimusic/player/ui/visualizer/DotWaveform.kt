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
 * Display state for [DotWaveform]: the last [columns] measured loudness slices, smoothed. Only
 * advances while [running]; while paused the waveform holds its last frame.
 */
@Stable
class WaveformState(val columns: Int) {
    internal val display = FloatArray(columns)
    private val raw = FloatArray(columns)
    /** Bumped every frame so the Canvas redraws without recomposing. */
    internal var tick by mutableIntStateOf(0)
    /** True once real audio levels have arrived for the current track. */
    var hasSignal by mutableStateOf(false)
        private set

    private var notBeforeUs = Long.MIN_VALUE

    /** Called on track change: clear the display and ignore levels from the previous track. */
    fun reset() {
        display.fill(0f)
        notBeforeUs = AudioLevelMeter.audibleUs.takeIf { it != C.TIME_UNSET } ?: Long.MIN_VALUE
        hasSignal = false
        tick++
    }

    internal fun step(dtSec: Float) {
        val filled = AudioLevelMeter.snapshot(raw, notBeforeUs)
        if (filled > 0 && !hasSignal) hasSignal = true
        // Fast attack, slower release: smooths frame jitter without inventing motion.
        val attack = (dtSec * 30f).coerceAtMost(1f)
        val release = (dtSec * 9f).coerceAtMost(1f)
        for (i in 0 until columns) {
            val target = raw[i]
            val current = display[i]
            display[i] = current + (target - current) * if (target > current) attack else release
        }
        tick++
    }
}

/**
 * Runs the waveform's frame loop only while [running] (playing *and* visible). Each frame reads
 * already-audible levels from [AudioLevelMeter]; nothing is synthesised.
 */
@Composable
fun rememberWaveformState(columns: Int, running: Boolean, trackKey: Any?): WaveformState {
    val state = remember(columns) { WaveformState(columns) }
    LaunchedEffect(trackKey) { state.reset() }
    // Only meter audio while someone is looking at it.
    DisposableEffect(running) {
        if (running) AudioLevelMeter.acquire()
        onDispose { if (running) AudioLevelMeter.release() }
    }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                state.step(((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f))
                last = now
            }
        }
    }
    return state
}

/**
 * Dot-matrix waveform: each column is one ~25 ms slice of the music's loudness, newest on the
 * right, mirrored around the centre line. Unlit dots stay faintly visible, like an LED matrix.
 */
@Composable
fun DotWaveform(
    state: WaveformState,
    modifier: Modifier = Modifier,
    rows: Int = 13,
    dotColor: Color = Color.White,
    accent: Color = Color(0xFFFF3030),
    height: Dp = 200.dp,
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = "Audio waveform" },
    ) {
        @Suppress("UNUSED_VARIABLE") val frame = state.tick // redraw trigger
        val cols = state.columns
        val stepX = size.width / cols
        val half = rows / 2
        val stepY = size.height / rows
        val radius = minOf(stepX, stepY) * 0.32f
        val cy = size.height / 2
        for (c in 0 until cols) {
            val x = stepX * (c + 0.5f)
            val level = state.display[c]
            val lit = level * half // dots lit above (and below) the centre dot
            val topDot = kotlin.math.ceil(lit).toInt()
            for (r in -half..half) {
                val d = kotlin.math.abs(r)
                val on = if (d == 0) max(level * 4f, 0.35f).coerceAtMost(1f) else (lit - (d - 1)).coerceIn(0f, 1f)
                val y = cy + r * stepY
                // The peak dot of the newest column carries the red accent.
                val isAccent = c == cols - 1 && topDot >= 1 && r == -topDot
                drawCircle(
                    color = if (isAccent) accent else dotColor,
                    radius = radius,
                    center = Offset(x, y),
                    alpha = 0.10f + 0.90f * on,
                )
            }
        }
    }
}

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
) {
    val duration by rememberUpdatedState(durationMs)
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
                    onDragStart = { dragFraction = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragFraction?.let { f -> if (duration > 0) seek((f * duration).toLong()) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                ) { change, _ ->
                    change.consume()
                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
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
                    drawCircle(Color(0xFFFF3030), radius = r * (2.4f + 1.6f * pulse), center = Offset(x, y), alpha = 0.18f * pulse)
                    drawCircle(Color(0xFFFF3030), radius = r * 1.9f, center = Offset(x, y))
                }
                i < current -> drawCircle(Color.White, radius = r, center = Offset(x, y), alpha = 0.9f)
                else -> drawCircle(Color.White, radius = r, center = Offset(x, y), alpha = 0.18f)
            }
        }
    }
}
