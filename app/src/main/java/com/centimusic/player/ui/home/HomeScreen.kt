package com.centimusic.player.ui.home

import androidx.compose.foundation.background
import com.centimusic.player.ui.components.rememberHaptics
import com.centimusic.player.ui.components.Artwork
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.map
import com.centimusic.player.ui.splash.timeOfDayGreeting
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centimusic.player.data.media.ScanState
import com.centimusic.player.data.media.Song
import com.centimusic.player.navigation.LocalAppContainer
import com.centimusic.player.navigation.LocalChromePadding
import com.centimusic.player.navigation.LocalLibraryAccess
import com.centimusic.player.navigation.LocalNavigator
import com.centimusic.player.ui.appViewModel
import com.centimusic.player.ui.components.AlbumCard
import com.centimusic.player.ui.components.CoverCard
import com.centimusic.player.ui.components.EmptyState
import com.centimusic.player.ui.components.HeroOrb
import com.centimusic.player.ui.components.rememberEmissionClock
import com.centimusic.player.ui.pulse.PulseMode
import com.centimusic.player.ui.pulse.rememberArtworkPulse
import com.centimusic.player.ui.components.LoadingState
import com.centimusic.player.ui.components.NmIconButton
import com.centimusic.player.ui.components.PillButton
import com.centimusic.player.ui.components.SectionHeader
import com.centimusic.player.ui.components.StatTile
import com.centimusic.player.ui.components.bottomContentPadding
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing

@Composable
fun HomeScreen() {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val access = LocalLibraryAccess.current
    val navigator = LocalNavigator.current
    val player = LocalAppContainer.current.player
    val playback by player.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // Once there's listening history, the big headline steps back so music comes first.
    val compactHero = state.recentlyPlayed.isNotEmpty()

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = bottomContentPadding(LocalChromePadding.current, Spacing.xxl),
    ) {
        item(key = "hero") {
            Hero(
                compact = compactHero,
                onSettings = navigator::settings,
                modifier = Modifier.graphicsLayer {
                    // Parallax: the hero drifts up slower than the list and fades out.
                    if (listState.firstVisibleItemIndex == 0) {
                        val offset = listState.firstVisibleItemScrollOffset.toFloat()
                        translationY = offset * 0.45f
                        alpha = (1f - offset / size.height.coerceAtLeast(1f) * 1.2f).coerceIn(0f, 1f)
                    }
                },
            )
        }

        when {
            !access.granted && state.songCount == 0 -> item(key = "permission") {
                EmptyState(
                    title = "Your music, offline",
                    message = "Allow access to audio on this device to build your library. Your music never leaves your phone.",
                    actionLabel = if (access.permanentlyDenied) "Open settings" else "Allow access",
                    onAction = access.request,
                    secondaryLabel = "Browse audio files",
                    onSecondary = access.importFiles,
                )
            }
            state.loading -> item(key = "loading") { LoadingState("Scanning your music") }
            state.scanState is ScanState.Failed && state.songCount == 0 -> item(key = "failed") {
                EmptyState(
                    title = "Couldn't read your library",
                    message = (state.scanState as ScanState.Failed).message,
                    actionLabel = "Try again",
                    onAction = { vm.rescan() },
                )
            }
            state.songCount == 0 -> item(key = "empty") {
                EmptyState(
                    title = "No music yet",
                    message = "Copy audio files to your phone, or pick them from any folder or cloud provider.",
                    actionLabel = "Browse audio files",
                    onAction = access.importFiles,
                    secondaryLabel = "Scan again",
                    onSecondary = { vm.rescan() },
                )
            }
            else -> {
                item(key = "quick") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        PillButton("Shuffle all", { player.shuffle(state.allSongs) }, icon = NmIcons.Shuffle, filled = true)
                        PillButton("All ${state.songCount} songs", navigator::songs, trailingIcon = NmIcons.ChevronRight)
                    }
                    Spacer(Modifier.height(Spacing.m))
                    Row(
                        Modifier.padding(horizontal = Spacing.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        StatTile(NmIcons.Waveform, state.songCount.toString(), "Songs", navigator::songs, Modifier.weight(1f), height = 104.dp)
                        StatTile(
                            NmIcons.HeartFilled,
                            state.likedCount.toString(),
                            "Liked",
                            navigator::liked,
                            Modifier.weight(1f),
                            iconTint = NmTheme.colors.accent,
                            height = 104.dp,
                        )
                    }
                    Spacer(Modifier.height(Spacing.m))
                }
                val resumeSong = playback.current ?: state.recentlyPlayed.firstOrNull()
                if (resumeSong != null) {
                    item(key = "continue") {
                        ContinueCard(
                            song = resumeSong,
                            isPlaying = playback.isPlaying && playback.current != null,
                            onOpen = {
                                if (playback.current == null) player.play(state.recentlyPlayed, 0)
                                navigator.player()
                            },
                            onPlayPause = {
                                if (playback.current != null) player.togglePlayPause() else player.play(state.recentlyPlayed, 0)
                            },
                        )
                        Spacer(Modifier.height(Spacing.xxl))
                    }
                }
                if (state.recentlyPlayed.isNotEmpty()) {
                    item(key = "played") {
                        SongShelf("Recently Played", state.recentlyPlayed, onSeeAll = navigator::recentlyPlayed) { index ->
                            player.play(state.recentlyPlayed, index)
                        }
                    }
                }
                if (state.mostPlayed.isNotEmpty()) {
                    item(key = "most") {
                        SongShelf("Most Played", state.mostPlayed, onSeeAll = null) { index ->
                            player.play(state.mostPlayed, index)
                        }
                    }
                }
                if (state.liked.isNotEmpty()) {
                    item(key = "liked") {
                        SongShelf("Liked Songs", state.liked, onSeeAll = navigator::liked) { index ->
                            player.play(state.liked, index)
                        }
                    }
                }
                item(key = "added") {
                    SongShelf("Recently Added", state.recentlyAdded, onSeeAll = navigator::recentlyAdded) { index ->
                        player.play(state.recentlyAdded, index)
                    }
                }
                if (state.albums.size > 1) {
                    item(key = "albums") {
                        SectionHeader("Albums", action = "See all", onAction = navigator::albums)
                        Spacer(Modifier.height(Spacing.s))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Spacing.gutter),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                        ) {
                            items(state.albums, key = { it.key }) { album ->
                                AlbumCard(album, onClick = { navigator.album(album.key) }, modifier = Modifier.width(140.dp), onPlay = { player.play(album.songs) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(compact: Boolean, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val playback by LocalAppContainer.current.player.state.collectAsStateWithLifecycle()
    val clock = rememberEmissionClock(playing = playback.isPlaying)
    val pulse = rememberArtworkPulse(playing = playback.isPlaying, trackKey = playback.current?.id)
    val colors = NmTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(if (compact) 320.dp else 460.dp),
    ) {
        HeroOrb(Modifier.fillMaxSize(), clock = clock, level = { if (pulse.mode == PulseMode.LIVE) pulse.level else 0f })
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = Spacing.gutter, end = Spacing.xs, top = Spacing.l),
            verticalAlignment = Alignment.Top,
        ) {
            Text("CentiMusic", style = NmTheme.type.brand, color = colors.textPrimary, modifier = Modifier.weight(1f))
            NmIconButton(NmIcons.MoreVertical, "Settings", onSettings)
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = Spacing.gutter, bottom = Spacing.xxl),
        ) {
            if (compact) {
                // Once there's history, greet by name instead of the tagline.
                val prefsFlow = LocalAppContainer.current.preferences.preferences
                val name by remember(prefsFlow) { prefsFlow.map { it.greetingName } }.collectAsStateWithLifecycle(initialValue = "")
                val today = remember { java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(java.util.Date()) }
                Text(today.uppercase(), style = NmTheme.type.dotCaption, color = colors.textTertiary)
                Spacer(Modifier.height(Spacing.s))
                Text(
                    (if (name.isBlank()) timeOfDayGreeting() else "${timeOfDayGreeting()},\n${name.trim()}").uppercase(),
                    style = NmTheme.type.hero.copy(fontSize = 30.sp, lineHeight = 33.sp),
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(Spacing.l))
            } else {
                Text("MUSIC\nFOR A\nCLEARER\nMIND", style = NmTheme.type.hero, color = colors.textPrimary)
                Spacer(Modifier.height(Spacing.xl))
            }
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
            )
        }
    }
}

@Composable
private fun SongShelf(title: String, songs: List<Song>, onSeeAll: (() -> Unit)?, onPlay: (Int) -> Unit) {
    SectionHeader(title, action = if (onSeeAll != null) "See all" else null, onAction = onSeeAll)
    Spacer(Modifier.height(Spacing.s))
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            CoverCard(song, song.title, song.artist, onClick = { onPlay(index) }, modifier = Modifier.width(120.dp))
        }
    }
    Spacer(Modifier.height(Spacing.xxl))
}

/** "Continue listening": the current (or last played) song with a red play/pause disc. */
@Composable
private fun ContinueCard(song: Song, isPlaying: Boolean, onOpen: () -> Unit, onPlayPause: () -> Unit) {
    val colors = NmTheme.colors
    val haptics = rememberHaptics()
    Row(
        Modifier
            .padding(horizontal = Spacing.gutter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .clickable(onClick = onOpen)
            .padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song, Modifier.size(56.dp), corner = 28.dp, sizeHint = 64.dp)
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(if (isPlaying) "NOW PLAYING" else "CONTINUE LISTENING", style = NmTheme.type.dotCaption, color = colors.accent)
            Spacer(Modifier.height(2.dp))
            Text(song.title, style = NmTheme.type.title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = NmTheme.type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.accent)
                .clickable(role = Role.Button) {
                    haptics.tick()
                    onPlayPause()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (isPlaying) NmIcons.Pause else NmIcons.Play, if (isPlaying) "Pause" else "Play", tint = colors.onAccent, modifier = Modifier.size(20.dp))
        }
    }
}
