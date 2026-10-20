package com.centimusic.player.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.centimusic.player.ui.pulse.rememberIsVisible
import com.centimusic.player.ui.pulse.rememberMotionEnabled

/**
 * A clock for the Now Playing artwork animation. [phase] (in seconds) only advances while music is
 * playing, so pausing freezes the artwork in place instead of snapping back.
 */
@Stable
class ArtworkMotion(val enabled: Boolean) {
    var phase by mutableFloatStateOf(0f)
        internal set
}

@Composable
fun rememberArtworkMotion(playing: Boolean): ArtworkMotion {
    // Honour the system "Remove animations" setting, and stop ticking while the screen isn't visible.
    val enabled = rememberMotionEnabled()
    val visible = rememberIsVisible()
    val motion = remember(enabled) { ArtworkMotion(enabled) }
    LaunchedEffect(motion, playing, visible) {
        if (!playing || !enabled || !visible) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                motion.phase += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    return motion
}
