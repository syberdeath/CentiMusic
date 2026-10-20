package com.centimusic.player.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.centimusic.player.AppContainer
import com.centimusic.player.data.media.Album
import com.centimusic.player.data.media.ScanState
import com.centimusic.player.data.media.pluralSongs
import com.centimusic.player.navigation.LocalChromePadding
import com.centimusic.player.navigation.LocalLibraryAccess
import com.centimusic.player.navigation.LocalNavigator
import com.centimusic.player.navigation.Routes
import com.centimusic.player.ui.appViewModel
import com.centimusic.player.ui.components.AlbumCard
import com.centimusic.player.ui.components.EmptyState
import com.centimusic.player.ui.components.NavigationRow
import com.centimusic.player.ui.components.NmIconButton
import com.centimusic.player.ui.components.ScreenTitle
import com.centimusic.player.ui.components.SectionHeader
import com.centimusic.player.ui.components.bottomContentPadding
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class LibraryUiState(
    val liked: Int = 0,
    val playlists: Int = 0,
    val albums: Int = 0,
    val artists: Int = 0,
    val songs: Int = 0,
    val folders: Int = 0,
    val recentAlbums: List<Album> = emptyList(),
    val scanning: Boolean = false,
)

class LibraryViewModel(container: AppContainer) : ViewModel() {
    val state: StateFlow<LibraryUiState> = combine(
        container.music.library,
        container.favorites.likedIds,
        container.playlists.playlists,
        container.music.scanState,
    ) { library, liked, playlists, scan ->
        LibraryUiState(
            liked = liked.mapNotNullTo(HashSet()) { library.byId[it]?.id }.size,
            playlists = playlists.size,
            albums = library.albums.size,
            artists = library.artists.size,
            songs = library.songs.size,
            folders = library.folders.size,
            recentAlbums = library.albums.sortedByDescending { album -> album.songs.maxOf { it.dateAddedSec } }.take(10),
            scanning = scan == ScanState.Scanning,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())
}

@Composable
fun LibraryScreen() {
    val vm = appViewModel { LibraryViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val access = LocalLibraryAccess.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomContentPadding(LocalChromePadding.current, Spacing.xxl)) {
        item {
            ScreenTitle("Library") {
                NmIconButton(NmIcons.Import, "Import audio files", access.importFiles)
                NmIconButton(NmIcons.Search, "Search", { navigator.tab(Routes.SEARCH) })
                NmIconButton(NmIcons.Settings, "Settings", navigator::settings)
            }
            Spacer(Modifier.height(Spacing.s))
        }
        if (!access.granted && state.songs == 0) {
            item {
                EmptyState(
                    title = "Library access needed",
                    message = "Grant audio access to see songs, albums and artists stored on this device.",
                    actionLabel = if (access.permanentlyDenied) "Open settings" else "Allow access",
                    onAction = access.request,
                    secondaryLabel = "Browse audio files",
                    onSecondary = access.importFiles,
                )
            }
        }
        item { NavigationRow(NmIcons.HeartFilled, "Liked Songs", navigator::liked, subtitle = state.liked.pluralSongs()) }
        item { NavigationRow(NmIcons.Note, "Playlists", navigator::playlists, subtitle = plural(state.playlists, "playlist")) }
        item { NavigationRow(NmIcons.Disc, "Albums", navigator::albums, subtitle = plural(state.albums, "album")) }
        item { NavigationRow(NmIcons.Person, "Artists", navigator::artists, subtitle = plural(state.artists, "artist")) }
        item { NavigationRow(NmIcons.Waveform, "All Songs", navigator::songs, subtitle = state.songs.pluralSongs()) }
        item { NavigationRow(NmIcons.Folder, "Folders", navigator::folders, subtitle = plural(state.folders, "folder")) }
        item { NavigationRow(NmIcons.Clock, "Recently Played", navigator::recentlyPlayed, subtitle = "Your listening history") }

        if (state.recentAlbums.isNotEmpty()) {
            item {
                Spacer(Modifier.height(Spacing.xxl))
                SectionHeader("Recently Added", action = "See all", onAction = navigator::recentlyAdded)
                Spacer(Modifier.height(Spacing.s))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.gutter),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    items(state.recentAlbums, key = { it.key }) { album ->
                        AlbumCard(album, onClick = { navigator.album(album.key) }, modifier = Modifier.width(112.dp))
                    }
                }
            }
        }
    }
}

internal fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"
