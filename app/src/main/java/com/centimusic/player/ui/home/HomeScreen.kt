package com.centimusic.player.ui.home

import androidx.compose.foundation.background
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
import com.centimusic.player.ui.components.LoadingState
import com.centimusic.player.ui.components.NmIconButton
import com.centimusic.player.ui.components.PillButton
import com.centimusic.player.ui.components.SectionHeader
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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = bottomContentPadding(LocalChromePadding.current, Spacing.xxl),
    ) {
        item(key = "hero") { Hero(onSettings = navigator::settings) }

        when {
            !access.granted && state.songCount == 0 -> item(key = "permission") {
                EmptyState(
                    title = "Your music, offline",
                    message = "Allow access to audio on this device to build your library. Nothing leaves your phone.",
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
                        PillButton("${state.songCount} songs", navigator::songs, icon = NmIcons.Note)
                    }
                    Spacer(Modifier.height(Spacing.l))
                }
                if (state.recentlyPlayed.isNotEmpty()) {
                    item(key = "played") {
                        SongShelf("Recently Played", state.recentlyPlayed, onSeeAll = navigator::recentlyPlayed) { index ->
                            player.play(state.recentlyPlayed, index)
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
                                AlbumCard(album, onClick = { navigator.album(album.key) }, modifier = Modifier.width(140.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(onSettings: () -> Unit) {
    val colors = NmTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(460.dp),
    ) {
        HeroOrb(Modifier.fillMaxSize())
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = Spacing.gutter, end = Spacing.xs, top = Spacing.l),
            verticalAlignment = Alignment.Top,
        ) {
            Text("NOTHING\nMUSIC", style = NmTheme.type.brand, color = colors.textPrimary, modifier = Modifier.weight(1f))
            NmIconButton(NmIcons.MoreVertical, "Settings", onSettings)
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = Spacing.gutter, bottom = Spacing.xxl),
        ) {
            Text("MUSIC\nFOR A\nCLEARER\nMIND", style = NmTheme.type.hero, color = colors.textPrimary)
            Spacer(Modifier.height(Spacing.xl))
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
private fun SongShelf(title: String, songs: List<Song>, onSeeAll: () -> Unit, onPlay: (Int) -> Unit) {
    SectionHeader(title, action = "See all", onAction = onSeeAll)
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
