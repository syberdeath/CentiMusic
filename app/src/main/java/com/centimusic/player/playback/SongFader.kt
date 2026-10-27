package com.centimusic.player.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * "Fade between songs": lowers the volume over the last [fadeMs] of a track that will be followed
 * automatically, and raises it over the first [fadeMs] of the next one. Media3 plays one track at
 * a time, so the two fades follow each other rather than overlapping. Manual starts and skips play
 * at full volume immediately. With fading off, ExoPlayer's gapless playback is untouched.
 */
class SongFader(private val player: ExoPlayer, private val scope: CoroutineScope) : Player.Listener {
    var fadeMs: Long = 0
        set(value) {
            field = value
            if (value <= 0) {
                job?.cancel()
                player.volume = 1f
            } else if (player.isPlaying) {
                start()
            }
        }

    private var job: Job? = null
    /** True only right after an automatic transition, so manual starts aren't faded in. */
    private var fadeInActive = false

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying && fadeMs > 0) start() else job?.cancel()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        fadeInActive = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        if (!fadeInActive) player.volume = 1f
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        // A seek inside the track cancels any fade-in in progress.
        if (reason == Player.DISCONTINUITY_REASON_SEEK) fadeInActive = false
    }

    private fun start() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                player.volume = volumeNow()
                delay(TICK_MS)
            }
        }
    }

    private fun volumeNow(): Float {
        val fade = fadeMs
        val duration = player.duration
        if (fade <= 0 || duration == C.TIME_UNSET || duration < fade * 3) return 1f
        val position = player.currentPosition
        var volume = 1f
        val continues = player.hasNextMediaItem() || player.repeatMode != Player.REPEAT_MODE_OFF
        val remaining = duration - position
        if (continues && remaining < fade) volume = minOf(volume, remaining.toFloat() / fade)
        if (fadeInActive) {
            if (position < fade) volume = minOf(volume, position.toFloat() / fade) else fadeInActive = false
        }
        return volume.coerceIn(0f, 1f)
    }

    private companion object {
        const val TICK_MS = 40L
    }
}
