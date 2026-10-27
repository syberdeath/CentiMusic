package com.centimusic.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.centimusic.player.AppContainer
import com.centimusic.player.data.media.Album
import com.centimusic.player.data.media.Artist
import com.centimusic.player.data.media.Song
import com.centimusic.player.data.media.formatTotalDuration
import com.centimusic.player.data.media.pluralSongs
import com.centimusic.player.navigation.LocalAppContainer
import com.centimusic.player.navigation.LocalChromePadding
import com.centimusic.player.navigation.LocalNavigator
import com.centimusic.player.navigation.LocalSongMenu
import com.centimusic.player.ui.appViewModel
import com.centimusic.player.ui.components.AccentPlayButton
import com.centimusic.player.ui.components.AlbumCard
import com.centimusic.player.ui.components.Artwork
import com.centimusic.player.ui.components.EmptyState
import com.centimusic.player.ui.components.LoadingState
import com.centimusic.player.ui.components.NmIconButton
import com.centimusic.player.ui.components.PillButton
import com.centimusic.player.ui.components.SectionHeader
import com.centimusic.player.ui.components.SongRow
import com.centimusic.player.ui.components.TopBar
import com.centimusic.player.ui.components.bottomContentPadding
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class ArtistUiState(
    val loading: Boolean = true,
    val artist: Artist? = null,
    val topSongs: List<Song> = emptyList(),
    /** True when top songs come from your play counts rather than album order. */
    val rankedByPlays: Boolean = false,
    val albums: List<Album> = emptyList(),
)

class ArtistViewModel(container: AppContainer, private val name: String) : ViewModel() {
    val state: StateFlow<ArtistUiState> = combine(container.music.library, container.history.playCounts) { library, plays ->
        val artist = library.artist(name) ?: return@combine ArtistUiState(loading = library.isEmpty)
        val played = artist.songs.filter { (plays[it.id] ?: 0) > 0 }
        val top = artist.songs
            .sortedWith(compareByDescending<Song> { plays[it.id] ?: 0 }.thenBy { it.album.lowercase() }.thenBy { it.trackNumber })
            .take(5)
        val albums = library.albums
            .filter { album -> album.artist == name || album.songs.any { it.artist == name } }
            .sortedByDescending { it.year }
        ArtistUiState(loading = false, artist = artist, topSongs = top, rankedByPlays = played.isNotEmpty(), albums = albums)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtistUiState())
}

/** Artist page: circular photo, top songs by your plays, albums in a grid, and a link to every song. */
@Composable
fun ArtistScreen(name: String) {
    val vm = appViewModel(key = "artist:$name") { ArtistViewModel(it, name) }
    val state by vm.state.collectAsStateWithLifecycle()
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val menu = LocalSongMenu.current
    val playback by container.player.state.collectAsStateWithLifecycle()
    val colors = NmTheme.colors
    val artist = state.artist

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomContentPadding(LocalChromePadding.current, Spacing.xxl)) {
        item(key = "top") { TopBar(onBack = navigator::back) }
        when {
            state.loading -> item(key = "loading") { LoadingState("Loading artist") }
            artist == null -> item(key = "missing") {
                EmptyState("Artist unavailable", "These songs are no longer in your library.", icon = NmIcons.Person)
            }
            else -> {
                item(key = "header") {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Artwork(artist.cover, Modifier.size(184.dp).clip(CircleShape), corner = 92.dp, sizeHint = 512.dp)
                            Box(
                                Modifier
                                    .padding(top = 14.dp, end = 14.dp)
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent),
                            )
                        }
                        Spacer(Modifier.height(Spacing.l))
                        Text(
                            artist.name,
                            style = NmTheme.type.screenTitle,
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = Spacing.gutter),
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            "${artist.songs.size.pluralSongs()} · ${plural(artist.albumCount, "album")} · " +
                                artist.songs.sumOf { it.durationMs }.formatTotalDuration(),
                            style = NmTheme.type.bodySmall,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(Spacing.l))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            NmIconButton(NmIcons.Shuffle, "Shuffle ${artist.name}", { container.player.shuffle(artist.songs) })
                            AccentPlayButton({ container.player.play(artist.songs, shuffle = false) }, size = 60.dp)
                            NmIconButton(NmIcons.PlaylistAdd, "Add ${artist.name} to playlist", { menu.pickPlaylistFor(artist.songs) })
                        }
                        Spacer(Modifier.height(Spacing.xxl))
                    }
                }

                item(key = "topHeader") {
                    SectionHeader(
                        if (state.rankedByPlays) "Top songs" else "Songs",
                        action = "All ${artist.songs.size}",
                        onAction = { navigator.artistSongs(artist.name) },
                    )
                    if (state.rankedByPlays) {
                        Text(
                            "Your most played",
                            style = NmTheme.type.caption,
                            color = colors.textTertiary,
                            modifier = Modifier.padding(horizontal = Spacing.gutter),
                        )
                    }
                    Spacer(Modifier.height(Spacing.xs))
                }
                itemsIndexed(state.topSongs, key = { _, song -> "top:${song.id}" }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { container.player.play(state.topSongs, index) },
                        isCurrent = playback.current?.id == song.id,
                        index = index + 1,
                        onMore = { menu.open(song) },
                    )
                }
                if (artist.songs.size > state.topSongs.size) {
                    item(key = "allSongs") {
                        Box(Modifier.fillMaxWidth().padding(vertical = Spacing.s), contentAlignment = Alignment.Center) {
                            PillButton("See all ${artist.songs.size} songs", { navigator.artistSongs(artist.name) }, trailingIcon = NmIcons.ChevronRight)
                        }
                    }
                }

                if (state.albums.isNotEmpty()) {
                    item(key = "albumsHeader") {
                        Spacer(Modifier.height(Spacing.xl))
                        SectionHeader("Albums")
                        Spacer(Modifier.height(Spacing.s))
                    }
                    // Two-column grid inside the list.
                    items(state.albums.chunked(2), key = { row -> "albums:" + row.joinToString { it.key } }) { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
                        ) {
                            row.forEach { album ->
                                AlbumCard(
                                    album,
                                    onClick = { navigator.album(album.key) },
                                    modifier = Modifier.weight(1f),
                                    onPlay = { container.player.play(album.songs) },
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
