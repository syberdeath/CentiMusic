package com.centimusic.player.ui.player

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.centimusic.player.data.media.formatDuration
import com.centimusic.player.navigation.LocalAppContainer
import com.centimusic.player.navigation.LocalNavigator
import com.centimusic.player.navigation.LocalSongMenu
import com.centimusic.player.playback.OutputKind
import com.centimusic.player.ui.components.Artwork
import com.centimusic.player.ui.pulse.PulseHalo
import com.centimusic.player.ui.pulse.rememberArtworkPulse
import com.centimusic.player.ui.components.NmIconButton
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val menu = LocalSongMenu.current
    val context = LocalContext.current
    val colors = NmTheme.colors
    val player = container.player
    val playback by player.state.collectAsStateWithLifecycle()
    val liked by container.favorites.likedIds.collectAsStateWithLifecycle()
    val output by container.audioOutput.device.collectAsStateWithLifecycle()
    val position by remember(player) { player.positionTicker() }.collectAsStateWithLifecycle(0L)
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val motion = rememberArtworkMotion(playing = playback.isPlaying)
    val pulse = rememberArtworkPulse(playing = playback.isPlaying, trackKey = playback.current?.id, historySize = 72)
    // Shrinks a little on pause and springs back when playback resumes.
    val cardScale by animateFloatAsState(
        targetValue = if (playback.isPlaying) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "cardScale",
    )

    val song = playback.current
    if (song == null) {
        // Queue was cleared (or playback never started): nothing to show.
        LaunchedEffect(Unit) { navigator.back() }
        return
    }
    val isLiked = song.id in liked
    val shownPosition = scrubFraction?.let { (it * playback.durationMs).toLong() } ?: position

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NmIconButton(NmIcons.ChevronDown, "Close player", navigator::back)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NOW PLAYING", style = NmTheme.type.caption, color = colors.textTertiary)
                if (playback.queue.size > 1 && playback.queuePosition >= 0) {
                    Text(
                        "${playback.queuePosition + 1} of ${playback.queue.size}",
                        style = NmTheme.type.caption,
                        color = colors.textSecondary,
                    )
                }
            }
            NmIconButton(NmIcons.Waveform, "Open visualizer", navigator::visualizer)
            NmIconButton(NmIcons.MoreVertical, "More options", { menu.open(song) })
        }

        // Artwork takes all the room that's left, as a square.
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = Spacing.l),
            contentAlignment = Alignment.Center,
        ) {
            val area = minOf(maxWidth, maxHeight)
            val side = area * ARTWORK_FRACTION
            Box(Modifier.size(area), contentAlignment = Alignment.Center) {
                // Behind the artwork: red ring + dot halo, driven by the audible audio level.
                PulseHalo(
                    pulse = pulse,
                    artworkFraction = ARTWORK_FRACTION,
                    rotationDeg = { motion.phase * 3f },
                    modifier = Modifier.fillMaxSize(),
                    dotColor = colors.textPrimary,
                    accent = colors.accent,
                    restColor = colors.textPrimary,
                )
                AnimatedContent(
                    targetState = song,
                    transitionSpec = {
                        (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.94f)) togetherWith
                            (fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.98f))
                    },
                    contentKey = { it.id },
                    label = "artwork",
                ) { current ->
                    Box(
                        Modifier
                            .size(side)
                            .graphicsLayer {
                                // Shrinks on pause; breathes very slightly with the pulse while playing.
                                val s = cardScale * (1f + 0.025f * pulse.level)
                                scaleX = s
                                scaleY = s
                            },
                    ) {
                        Artwork(
                            current,
                            Modifier.fillMaxSize(),
                            corner = 16.dp,
                            sizeHint = 1024.dp,
                            contentModifier = Modifier.graphicsLayer {
                                // Ken Burns drift: always zoomed in enough that the edges never show.
                                if (!motion.enabled) return@graphicsLayer
                                val t = motion.phase
                                val zoom = 1.10f + 0.05f * sin(t * 0.21f)
                                scaleX = zoom
                                scaleY = zoom
                                translationX = size.width * 0.035f * sin(t * 0.13f)
                                translationY = size.height * 0.035f * cos(t * 0.17f)
                            },
                        )
                        if (playback.isPlaying) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(14.dp)
                                    .size(16.dp)
                                    .graphicsLayer {
                                        val l = pulse.level
                                        val s = 0.85f + 0.45f * l
                                        scaleX = s
                                        scaleY = s
                                        alpha = 0.6f + 0.4f * l
                                    }
                                    .clip(CircleShape)
                                    .background(colors.accent),
                            )
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AnimatedContent(song, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, contentKey = { it.id }, label = "title") { s ->
                        Column {
                            Text(
                                s.title,
                                style = NmTheme.type.trackTitle,
                                color = colors.textPrimary,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                s.artist,
                                style = NmTheme.type.title,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                NmIconButton(
                    if (isLiked) NmIcons.HeartFilled else NmIcons.Heart,
                    if (isLiked) "Remove from Liked Songs" else "Add to Liked Songs",
                    { scope.launch { container.favorites.setLiked(song.id, !isLiked) } },
                    tint = if (isLiked) colors.accent else colors.textPrimary,
                )
            }

            Spacer(Modifier.height(Spacing.l))
            SeekBar(
                positionMs = position,
                durationMs = playback.durationMs,
                onSeek = player::seekTo,
                onScrub = { scrubFraction = it },
            )
            Row(Modifier.fillMaxWidth()) {
                Text(shownPosition.formatDuration(), style = NmTheme.type.caption, color = colors.textSecondary)
                Spacer(Modifier.weight(1f))
                Text(playback.durationMs.formatDuration(), style = NmTheme.type.caption, color = colors.textSecondary)
            }

            Spacer(Modifier.height(Spacing.l))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NmIconButton(
                    NmIcons.Shuffle,
                    if (playback.shuffle) "Shuffle on" else "Shuffle off",
                    player::toggleShuffle,
                    tint = if (playback.shuffle) colors.accent else colors.textPrimary,
                )
                NmIconButton(NmIcons.Previous, "Previous track", player::previous, iconSize = 28.dp)
                PlayPauseButton(playback.isPlaying, player::togglePlayPause)
                NmIconButton(NmIcons.Next, "Next track", player::next, enabled = playback.hasNext, iconSize = 28.dp)
                NmIconButton(
                    if (playback.repeatMode == Player.REPEAT_MODE_ONE) NmIcons.RepeatOne else NmIcons.Repeat,
                    when (playback.repeatMode) {
                        Player.REPEAT_MODE_ALL -> "Repeat all"
                        Player.REPEAT_MODE_ONE -> "Repeat one"
                        else -> "Repeat off"
                    },
                    player::cycleRepeat,
                    tint = if (playback.repeatMode != Player.REPEAT_MODE_OFF) colors.accent else colors.textPrimary,
                )
            }

            Spacer(Modifier.height(Spacing.l))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NmIconButton(NmIcons.PlaylistAdd, "Add to playlist", { menu.pickPlaylistFor(listOf(song)) }, iconSize = 22.dp)
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .clickable(role = Role.Button) {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                        .padding(vertical = Spacing.s),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        when (output.kind) {
                            OutputKind.BLUETOOTH -> NmIcons.Bluetooth
                            OutputKind.WIRED, OutputKind.USB -> NmIcons.Headphones
                            else -> NmIcons.Phone
                        },
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Spacing.s))
                    Text(output.name, style = NmTheme.type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                NmIconButton(NmIcons.Queue, "Queue", navigator::queue, iconSize = 22.dp)
            }
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    val colors = NmTheme.colors
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(colors.inverse)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(isPlaying, transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) }, label = "playPause") { playing ->
            Icon(
                if (playing) NmIcons.Pause else NmIcons.Play,
                contentDescription = if (playing) "Pause" else "Play",
                tint = colors.onInverse,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

/** Artwork side relative to the square area it sits in; the rest is room for the pulse halo. */
private const val ARTWORK_FRACTION = 0.84f
