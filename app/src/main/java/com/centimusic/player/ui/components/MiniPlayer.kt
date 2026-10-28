package com.centimusic.player.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Icon
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
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val swipe = remember { Animatable(0f) }

    Column(
        modifier
            .padding(horizontal = Spacing.m)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable(onClick = onOpen)
                // Swipe left/right to skip, up to open Now Playing.
                .pointerInput(Unit) {
                    val threshold = 56.dp.toPx()
                    var dy = 0f
                    detectDragGestures(
                        onDragStart = { dy = 0f },
                        onDragEnd = {
                            val dx = swipe.value
                            when {
                                -dy > threshold && -dy > kotlin.math.abs(dx) -> { haptics.gesture(); onOpen() }
                                dx < -threshold -> { haptics.gesture(); player.next() }
                                dx > threshold -> { haptics.gesture(); player.previous() }
                            }
                            scope.launch { swipe.animateTo(0f, spring(dampingRatio = 0.7f)) }
                        },
                        onDragCancel = { scope.launch { swipe.animateTo(0f) } },
                    ) { change, drag ->
                        change.consume()
                        dy += drag.y
                        scope.launch { swipe.snapTo(swipe.value + drag.x) }
                    }
                }
                .graphicsLayer {
                    translationX = swipe.value * 0.4f
                    alpha = 1f - (kotlin.math.abs(swipe.value) / (size.width * 0.8f)).coerceIn(0f, 0.6f)
                }
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
                        corner = 22.dp,
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
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) {
                        haptics.tick()
                        player.togglePlayPause()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.isPlaying) NmIcons.Pause else NmIcons.Play,
                        if (state.isPlaying) "Pause" else "Play",
                        tint = colors.onAccent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            NmIconButton(NmIcons.Next, "Next track", { haptics.tick(); player.next() }, enabled = state.hasNext, iconSize = 20.dp)
        }
        // Dotted progress along the bottom edge: played dots red, the rest faint.
        Canvas(
            Modifier
                .padding(horizontal = 18.dp)
                .padding(bottom = 6.dp)
                .fillMaxWidth()
                .height(4.dp),
        ) {
            val step = 6.dp.toPx()
            val r = 1.2.dp.toPx()
            val count = (size.width / step).toInt().coerceAtLeast(1)
            val playedDots = (progress * count).toInt()
            for (i in 0 until count) {
                drawCircle(
                    if (i < playedDots) colors.accent else colors.textPrimary,
                    radius = r,
                    center = Offset(step * (i + 0.5f), size.height / 2),
                    alpha = if (i < playedDots) 1f else 0.15f,
                )
            }
        }
    }
}
