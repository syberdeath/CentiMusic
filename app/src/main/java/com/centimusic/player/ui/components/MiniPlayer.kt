package com.centimusic.player.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centimusic.player.playback.PlaybackState
import com.centimusic.player.playback.PlayerConnection
import com.centimusic.player.ui.pulse.rememberArtworkPulse
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing

@Composable
fun MiniPlayer(
    state: PlaybackState,
    player: PlayerConnection,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.current ?: return
    val colors = NmTheme.colors
    val position by remember(player) { player.positionTicker(500) }.collectAsStateWithLifecycle(0L)
    val pulse = rememberArtworkPulse(playing = state.isPlaying, trackKey = song.id)
    val progress = if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    Column(modifier.background(colors.background)) {
        NmDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable(onClick = onOpen)
                .padding(start = Spacing.l, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp)) {
                AnimatedContent(song, transitionSpec = { fadeIn() togetherWith fadeOut() }, contentKey = { it.id }, label = "miniArt") { s ->
                    Artwork(
                        s,
                        Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                // Restrained: at most 4% larger at full loudness.
                                val k = 1f + 0.04f * pulse.level
                                scaleX = k
                                scaleY = k
                            },
                        corner = Spacing.thumbCorner,
                        sizeHint = 48.dp,
                    )
                }
                if (state.isPlaying) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(6.dp)
                            .graphicsLayer { alpha = 0.45f + 0.55f * pulse.level }
                            .clip(CircleShape)
                            .background(colors.accent),
                    )
                }
            }
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = NmTheme.type.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = NmTheme.type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            NmIconButton(
                if (state.isPlaying) NmIcons.Pause else NmIcons.Play,
                if (state.isPlaying) "Pause" else "Play",
                player::togglePlayPause,
                iconSize = 22.dp,
            )
            NmIconButton(NmIcons.Next, "Next track", player::next, enabled = state.hasNext, iconSize = 20.dp)
        }
        // Thin progress line along the bottom edge.
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(colors.divider),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(colors.accent),
            )
        }
    }
}
