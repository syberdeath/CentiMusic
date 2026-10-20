package com.centimusic.player.ui.search

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centimusic.player.data.media.pluralSongs
import com.centimusic.player.navigation.LocalAppContainer
import com.centimusic.player.navigation.LocalChromePadding
import com.centimusic.player.navigation.LocalNavigator
import com.centimusic.player.navigation.LocalSongMenu
import com.centimusic.player.ui.appViewModel
import com.centimusic.player.ui.components.AlbumCard
import com.centimusic.player.ui.components.EmptyState
import com.centimusic.player.ui.components.NavigationRow
import com.centimusic.player.ui.components.NmIconButton
import com.centimusic.player.ui.components.ScreenTitle
import com.centimusic.player.ui.components.SectionHeader
import com.centimusic.player.ui.components.SongRow
import com.centimusic.player.ui.components.bottomContentPadding
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen() {
    val vm = appViewModel { SearchViewModel(it) }
    val query by vm.query.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val menu = LocalSongMenu.current
    val focus = LocalFocusManager.current
    val colors = NmTheme.colors
    val results = state.results

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomContentPadding(LocalChromePadding.current)) {
        item(key = "title") { ScreenTitle("Search") }
        item(key = "field") {
            SearchField(
                value = query,
                onValueChange = vm::setQuery,
                onSearch = {
                    vm.commit()
                    focus.clearFocus()
                },
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s),
            )
            Spacer(Modifier.height(Spacing.l))
        }

        if (query.isBlank()) {
            if (state.topArtists.isNotEmpty()) {
                item(key = "artists") {
                    SectionHeader("Your artists")
                    Spacer(Modifier.height(Spacing.m))
                    FlowRow(
                        Modifier.padding(horizontal = Spacing.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        state.topArtists.forEach { name ->
                            Chip(name) { vm.setQuery(name); vm.commit(name) }
                        }
                    }
                    Spacer(Modifier.height(Spacing.xxl))
                }
            }
            if (state.recentSearches.isNotEmpty()) {
                item(key = "recentHeader") {
                    SectionHeader("Recent Searches", action = "Clear", onAction = { vm.clearRecent() })
                    Spacer(Modifier.height(Spacing.xs))
                }
                items(state.recentSearches, key = { "recent:$it" }) { recent ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.setQuery(recent) }
                            .padding(start = Spacing.gutter, end = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(NmIcons.Clock, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(Spacing.l))
                        Text(recent, style = NmTheme.type.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        NmIconButton(NmIcons.Close, "Remove $recent", { vm.deleteRecent(recent) }, tint = colors.textTertiary, iconSize = 16.dp)
                    }
                }
            }
            if (state.topArtists.isEmpty() && state.recentSearches.isEmpty()) {
                item(key = "hint") {
                    EmptyState(
                        "Search your library",
                        if (state.librarySize > 0) "Find songs, artists, albums and playlists among ${state.librarySize.pluralSongs()}." else "Your library is empty.",
                        icon = NmIcons.Search,
                    )
                }
            }
        } else if (results.query.isNotEmpty() && results.isEmpty) {
            item(key = "none") {
                EmptyState("No results", "Nothing matches \"${results.query}\". Check the spelling or try fewer words.", icon = NmIcons.Search)
            }
        } else {
            if (results.artists.isNotEmpty()) {
                item(key = "artistsHeader") { SectionHeader("Artists") }
                items(results.artists.take(5), key = { "artist:${it.name}" }) { artist ->
                    NavigationRow(
                        NmIcons.Person,
                        artist.name,
                        { vm.commit(); navigator.artist(artist.name) },
                        subtitle = artist.songs.size.pluralSongs(),
                        iconSize = 44.dp,
                    )
                }
                item(key = "artistsGap") { Spacer(Modifier.height(Spacing.l)) }
            }
            if (results.albums.isNotEmpty()) {
                item(key = "albums") {
                    SectionHeader("Albums")
                    Spacer(Modifier.height(Spacing.s))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Spacing.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        items(results.albums, key = { it.key }) { album ->
                            AlbumCard(album, { vm.commit(); navigator.album(album.key) }, Modifier.width(120.dp))
                        }
                    }
                    Spacer(Modifier.height(Spacing.l))
                }
            }
            if (results.playlists.isNotEmpty()) {
                item(key = "playlistsHeader") { SectionHeader("Playlists") }
                items(results.playlists, key = { "playlist:${it.id}" }) { playlist ->
                    NavigationRow(
                        NmIcons.Note,
                        playlist.name,
                        { vm.commit(); navigator.playlist(playlist.id) },
                        subtitle = playlist.songs.size.pluralSongs(),
                        iconSize = 44.dp,
                    )
                }
                item(key = "playlistsGap") { Spacer(Modifier.height(Spacing.l)) }
            }
            if (results.songs.isNotEmpty()) {
                item(key = "songsHeader") { SectionHeader("Songs") }
                itemsIndexed(results.songs, key = { _, song -> "song:${song.id}" }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = {
                            vm.commit()
                            container.player.play(results.songs, index)
                        },
                        onMore = { menu.open(song) },
                    )
                }
            }
        }
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search songs, artists, albums…",
    onSearch: () -> Unit = {},
) {
    val colors = NmTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceHigh)
            .padding(start = Spacing.l, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NmIcons.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.m))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = NmTheme.type.body, color = colors.textTertiary)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = NmTheme.type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            NmIconButton(NmIcons.Close, "Clear search", { onValueChange("") }, tint = colors.textSecondary, iconSize = 18.dp)
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = NmTheme.type.bodySmall,
        color = NmTheme.colors.textPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(NmTheme.colors.surfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}
