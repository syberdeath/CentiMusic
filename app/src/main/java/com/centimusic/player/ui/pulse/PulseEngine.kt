package com.centimusic.player.ui.pulse

import kotlin.math.PI
import kotlin.math.sin

enum class PulseMode {
    /** Nothing measured yet for this track (brief, while the first audio reaches the speaker). */
    IDLE,
    /** Driven by measured loudness of the audio that is currently audible. */
    LIVE,
    /** No measurement available: a slow, uniform breath that deliberately ignores the music. */
    DECORATIVE,
}

/**
 * Turns audible loudness slices into the values the artwork animations draw: one smoothed
 * [level] for pulses/rings and a smoothed [history] for the dot halo. Pure Kotlin so the
 * play/pause/track-change behaviour is unit-testable; Compose only calls [step] once per frame
 * while music plays and the screen is visible.
 */
class PulseEngine(val historySize: Int) {
    /** Smoothed loudness per halo dot, oldest first. */
    val history = FloatArray(historySize)
    private val raw = FloatArray(historySize)

    /** 0..1 pulse strength right now. */
    var level = 0f
        private set
    var mode = PulseMode.IDLE
        private set

    private var sinceReset = 0f
    private var sinceSignal = 0f
    private var breath = 0f

    fun reset() {
        history.fill(0f)
        raw.fill(0f)
        level = 0f
        mode = PulseMode.IDLE
        sinceReset = 0f
        sinceSignal = 0f
        breath = 0f
    }

    /**
     * Advances by [dtSec]. [snapshot] fills its argument with audible levels (oldest first) and
     * returns how many it filled; 0 means no measurement is available.
     */
    fun step(dtSec: Float, snapshot: (FloatArray) -> Int) {
        sinceReset += dtSec
        val filled = snapshot(raw)
        if (filled > 0) {
            mode = PulseMode.LIVE
            sinceSignal = 0f
            follow(raw[historySize - 1], dtSec)
            for (i in 0 until historySize) history[i] = smooth(history[i], raw[i], dtSec)
            return
        }
        sinceSignal += dtSec
        // Wait a moment before deciding there is no signal: a new track takes a few hundred ms to be heard.
        val giveUp = if (mode == PulseMode.LIVE) sinceSignal > SIGNAL_TIMEOUT else sinceReset > SIGNAL_TIMEOUT
        if (!giveUp) {
            follow(0f, dtSec)
            for (i in 0 until historySize) history[i] = smooth(history[i], 0f, dtSec)
            return
        }
        mode = PulseMode.DECORATIVE
        breath += dtSec
        val value = DECORATIVE_BASE + DECORATIVE_DEPTH * (0.5f + 0.5f * sin(2f * PI.toFloat() * DECORATIVE_HZ * breath))
        follow(value, dtSec)
        history.fill(level * 0.6f)
    }

    private fun follow(target: Float, dt: Float) {
        level = smooth(level, target, dt)
    }

    /** Fast attack, slower release — smooths frame jitter without inventing motion. */
    private fun smooth(current: Float, target: Float, dt: Float): Float {
        val rate = if (target > current) ATTACK else RELEASE
        return current + (target - current) * (dt * rate).coerceAtMost(1f)
    }

    companion object {
        const val SIGNAL_TIMEOUT = 1.0f
        /** One breath every five seconds: far slower than any musical beat, so it can't pass for one. */
        const val DECORATIVE_HZ = 0.2f
        const val DECORATIVE_BASE = 0.08f
        const val DECORATIVE_DEPTH = 0.14f
        private const val ATTACK = 24f
        private const val RELEASE = 7f
    }
}
