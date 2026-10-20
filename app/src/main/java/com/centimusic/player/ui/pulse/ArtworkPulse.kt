package com.centimusic.player.ui.pulse

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import com.centimusic.player.playback.AudioLevelMeter

/**
 * Per-screen handle on [PulseEngine]. Read [level]/[history] only inside draw or graphicsLayer
 * blocks (after reading [tick]) so per-frame updates redraw without recomposing.
 */
@Stable
class ArtworkPulse internal constructor(historySize: Int, val motionEnabled: Boolean) {
    internal val engine = PulseEngine(historySize)
    internal var tick by mutableIntStateOf(0)
    var mode by mutableStateOf(PulseMode.IDLE)
        internal set
    /** 1 while playing, easing to 0 on pause so effects settle instead of freezing mid-pulse. */
    internal var activityState: State<Float>? = null
    private var notBeforeUs = Long.MIN_VALUE

    val activity: Float get() = activityState?.value ?: 0f

    /** Current pulse strength, already faded by [activity]. Read in draw phase. */
    val level: Float
        get() {
            tick
            return engine.level * activity
        }

    val history: FloatArray
        get() {
            tick
            return engine.history
        }

    internal fun reset() {
        engine.reset()
        mode = PulseMode.IDLE
        notBeforeUs = AudioLevelMeter.audibleUs.takeIf { it != C.TIME_UNSET } ?: Long.MIN_VALUE
        tick++
    }

    internal fun step(dtSec: Float) {
        engine.step(dtSec) { out -> AudioLevelMeter.snapshot(out, notBeforeUs) }
        if (mode != engine.mode) mode = engine.mode
        tick++
    }
}

/**
 * Drives an [ArtworkPulse] from ExoPlayer's playback state ([playing]) and the audible audio
 * levels. The frame loop and audio metering run only while playing, the screen is visible
 * (lifecycle ≥ STARTED) and system animations are enabled; otherwise nothing ticks.
 */
@Composable
fun rememberArtworkPulse(playing: Boolean, trackKey: Any?, historySize: Int = 1): ArtworkPulse {
    val motionEnabled = rememberMotionEnabled()
    val visible = rememberIsVisible()
    val pulse = remember(historySize, motionEnabled) { ArtworkPulse(historySize, motionEnabled) }
    pulse.activityState = animateFloatAsState(
        targetValue = if (playing && motionEnabled) 1f else 0f,
        animationSpec = tween(if (playing) 600 else 450),
        label = "pulseActivity",
    )
    LaunchedEffect(pulse, trackKey) { pulse.reset() }

    val running = playing && visible && motionEnabled
    DisposableEffect(running) {
        if (running) AudioLevelMeter.acquire()
        onDispose { if (running) AudioLevelMeter.release() }
    }
    LaunchedEffect(pulse, running) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                pulse.step(((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f))
                last = now
            }
        }
    }
    return pulse
}

/** False when the user turned off animations (Developer options / Accessibility "Remove animations"). */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    val visible = rememberIsVisible()
    // Re-read when returning to the app, in case the setting changed meanwhile.
    return remember(visible) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/** True while the hosting screen is at least STARTED (on screen, not in the background). */
@Composable
fun rememberIsVisible(): Boolean {
    if (LocalView.current.isInEditMode) return true
    // collectAsState (not ...WithLifecycle): we must keep observing while the screen is stopped.
    val state by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    return state.isAtLeast(Lifecycle.State.STARTED)
}
