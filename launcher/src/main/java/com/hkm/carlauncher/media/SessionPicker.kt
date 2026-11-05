package com.hkm.carlauncher.media

/** What the picker needs to know about one active media session. */
data class SessionInfo(val packageName: String, val isPlaying: Boolean, val lastActiveMillis: Long)

/** The first playing session in system order; otherwise the most recently active one. */
fun pickSession(sessions: List<SessionInfo>): SessionInfo? =
    sessions.firstOrNull { it.isPlaying } ?: sessions.maxByOrNull { it.lastActiveMillis }

/**
 * Fraction of the track played, 0..1. While playing, the position is extrapolated from the time
 * the player last reported it. Unknown or zero duration gives 0.
 */
fun progressFraction(
    positionMs: Long,
    updateTimeMs: Long,
    speed: Float,
    isPlaying: Boolean,
    durationMs: Long,
    nowMs: Long,
): Float {
    if (durationMs <= 0) return 0f
    val elapsed = if (isPlaying) ((nowMs - updateTimeMs).coerceAtLeast(0) * speed).toLong() else 0L
    val fraction = (positionMs + elapsed).toFloat() / durationMs.toFloat()
    return if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
}
