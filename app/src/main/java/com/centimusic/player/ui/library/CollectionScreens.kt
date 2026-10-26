package com.centimusic.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centimusic.player.data.media.Album
import com.centimusic.player.data.media.pluralSongs
import com.centimusic.player.navigation.LocalAppContainer
import com.centimusic.player.navigation.LocalChromePadding
import com.centimusic.player.navigation.LocalNavigator
import com.centimusic.player.ui.components.AlbumCard
import com.centimusic.player.ui.components.Artwork
import com.centimusic.player.ui.components.ChoiceDialog
import com.centimusic.player.ui.components.EmptyState
import com.centimusic.player.ui.components.NavigationRow
import com.centimusic.player.ui.components.NmIconButton
import com.centimusic.player.ui.components.TopBar
import com.centimusic.player.ui.components.bottomContentPadding
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing

private enum class AlbumSort(val label: String, val comparator: Comparator<Album>) {
    TITLE("Title", compareBy { it.title.lowercase() }),
    ARTIST("Artist", compareBy<Album>({ it.artist.lowercase() }, { it.year })),
    YEAR("Year", compareByDescending<Album> { it.year }.thenBy { it.title.lowercase() }),
    RECENT("Recently added", compareByDescending { album -> album.songs.maxOf { it.dateAddedSec } }),
}

@Composable
private fun ListTitle(title: String, subtitle: String, inset: Boolean = true) {
    Column(Modifier.padding(horizontal = if (inset) Spacing.gutter else 0.dp)) {
        Text(title, style = NmTheme.type.screenTitle, color = NmTheme.colors.textPrimary)
        Spacer(Modifier.height(Spacing.xs))
        Text(subtitle, style = NmTheme.type.bodySmall, color = NmTheme.colors.textSecondary)
        Spacer(Modifier.height(Spacing.l))
    }
}

@Composable
fun AlbumsScreen() {
    val library by LocalAppContainer.current.music.library.collectAsStateWithLifecycle()
    val player = LocalAppContainer.current.player
    val navigator = LocalNavigator.current
    var sort by rememberSaveable { mutableStateOf(AlbumSort.TITLE) }
    var showSort by remember { mutableStateOf(false) }
    val albums = remember(library, sort) { library.albums.sortedWith(sort.comparator) }

    Column(Modifier.fillMaxSize()) {
        TopBar(onBack = navigator::back) { NmIconButton(NmIcons.Sort, "Sort albums", { showSort = true }) }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = LocalChromePadding.current + Spacing.l),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ListTitle("Albums", "${plural(albums.size, "album")} · sorted by ${sort.label.lowercase()}", inset = false)
            }
            if (albums.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("No albums", "Albums appear once your library has music.", icon = NmIcons.Disc) }
            }
            items(albums, key = { it.key }) { album ->
                AlbumCard(album, onClick = { navigator.album(album.key) }, onPlay = { player.play(album.songs) })
            }
        }
    }

    if (showSort) {
        ChoiceDialog("Sort albums by", AlbumSort.entries, sort, { it.label }, { sort = it; showSort = false }, { showSort = false })
    }
}

@Composable
fun ArtistsScreen() {
    val library by LocalAppContainer.current.music.library.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomContentPadding(LocalChromePadding.current)) {
        item {
            TopBar(onBack = navigator::back)
            ListTitle("Artists", plural(library.artists.size, "artist"))
        }
        if (library.artists.isEmpty()) {
            item { EmptyState("No artists", "Artists appear once your library has music.", icon = NmIcons.Person) }
        }
        items(library.artists, key = { it.name }) { artist ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { navigator.artist(artist.name) }
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(artist.cover, Modifier.size(52.dp).clip(CircleShape), corner = 26.dp, sizeHint = 64.dp)
                Spacer(Modifier.width(Spacing.l))
                Column(Modifier.weight(1f)) {
                    Text(artist.name, style = NmTheme.type.title, color = NmTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${artist.songs.size.pluralSongs()} · ${plural(artist.albumCount, "album")}",
                        style = NmTheme.type.bodySmall,
                        color = NmTheme.colors.textSecondary,
                    )
                }
                Icon(NmIcons.ChevronRight, contentDescription = null, tint = NmTheme.colors.textTertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun FoldersScreen() {
    val library by LocalAppContainer.current.music.library.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomContentPadding(LocalChromePadding.current)) {
        item {
            TopBar(onBack = navigator::back)
            ListTitle("Folders", plural(library.folders.size, "folder"))
        }
        if (library.folders.isEmpty()) {
            item { EmptyState("No folders", "Folders containing music will be listed here.", icon = NmIcons.Folder) }
        }
        items(library.folders, key = { it.path }) { folder ->
            NavigationRow(
                icon = NmIcons.Folder,
                title = folder.name,
                subtitle = "${folder.songs.size.pluralSongs()} · ${folder.path.ifEmpty { "/" }}",
                onClick = { navigator.folder(folder.path) },
            )
        }
    }
}
