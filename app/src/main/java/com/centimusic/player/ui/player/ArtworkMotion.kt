package com.centimusic.player.ui.player

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.withFrameNanos

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
    val context = LocalContext.current
    // Honour the system "Remove animations" setting.
    val enabled = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val motion = remember { ArtworkMotion(enabled) }
    LaunchedEffect(playing, enabled) {
        if (!playing || !enabled) return@LaunchedEffect
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
