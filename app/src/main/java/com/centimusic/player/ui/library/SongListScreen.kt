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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centimusic.player.data.media.SongSort
import com.centimusic.player.navigation.LocalAppContainer
import com.centimusic.player.navigation.LocalChromePadding
import com.centimusic.player.navigation.LocalNavigator
import com.centimusic.player.navigation.LocalSongMenu
import com.centimusic.player.ui.appViewModel
import com.centimusic.player.ui.components.AccentPlayButton
import com.centimusic.player.ui.components.Artwork
import com.centimusic.player.ui.components.ChoiceDialog
import com.centimusic.player.ui.components.EmptyState
import com.centimusic.player.ui.components.LoadingState
import com.centimusic.player.ui.components.NmIconButton
import com.centimusic.player.ui.components.SongRow
import com.centimusic.player.ui.components.TopBar
import com.centimusic.player.ui.components.bottomContentPadding
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing

@Composable
fun SongListScreen(source: SongListSource) {
    val vm = appViewModel(key = source.key) { SongListViewModel(it, source) }
    val state by vm.state.collectAsStateWithLifecycle()
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val menu = LocalSongMenu.current
    val playback by container.player.state.collectAsStateWithLifecycle()
    var showSort by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomContentPadding(LocalChromePadding.current)) {
        item(key = "top") {
            TopBar(onBack = navigator::back) {
                if (state.sortable) NmIconButton(NmIcons.Sort, "Sort songs", { showSort = true })
            }
        }
        item(key = "header") {
            CollectionHeader(
                state = state,
                onPlay = { container.player.play(state.songs, shuffle = false) },
                onShuffle = { container.player.shuffle(state.songs) },
                onAddToPlaylist = { menu.pickPlaylistFor(state.songs) },
            )
        }
        when {
            state.loading -> item(key = "loading") { LoadingState("Loading songs") }
            state.songs.isEmpty() -> item(key = "empty") { EmptyState(state.emptyTitle, state.emptyMessage) }
            else -> itemsIndexed(state.songs, key = { _, song -> song.id }) { index, song ->
                val liked = song.id in state.likedIds
                SongRow(
                    song = song,
                    onClick = { container.player.play(state.songs, index) },
                    isCurrent = playback.current?.id == song.id,
                    index = if (state.showTrackNumbers) song.trackNumber.takeIf { it > 0 } ?: (index + 1) else null,
                    showArtwork = !state.showTrackNumbers,
                    onMore = { menu.open(song) },
                    trailing = if (source == SongListSource.Liked) {
                        {
                            NmIconButton(
                                if (liked) NmIcons.HeartFilled else NmIcons.Heart,
                                "Remove ${song.title} from Liked Songs",
                                { vm.unlike(song) },
                                tint = if (liked) NmTheme.colors.accent else NmTheme.colors.textSecondary,
                                iconSize = 20.dp,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }

    if (showSort) {
        ChoiceDialog(
            title = "Sort by",
            options = SongSort.entries,
            selected = state.sort,
            label = { it.label },
            onSelect = {
                vm.setSort(it)
                showSort = false
            },
            onDismiss = { showSort = false },
        )
    }
}

@Composable
private fun CollectionHeader(
    state: SongListUiState,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    val colors = NmTheme.colors
    Column(Modifier.padding(horizontal = Spacing.gutter)) {
        when (state.headerStyle) {
            HeaderStyle.TITLE -> {
                Text(state.title, style = NmTheme.type.screenTitle, color = colors.textPrimary)
                Spacer(Modifier.height(Spacing.xs))
                Text(state.subtitle, style = NmTheme.type.bodySmall, color = colors.textSecondary)
            }
            HeaderStyle.ARTWORK, HeaderStyle.ARTIST -> Row(verticalAlignment = Alignment.CenterVertically) {
                val shape = if (state.headerStyle == HeaderStyle.ARTIST) CircleShape else null
                Box {
                    if (shape != null) {
                        Artwork(state.cover, Modifier.size(120.dp).clip(shape), corner = 60.dp, sizeHint = 256.dp)
                    } else {
                        Artwork(state.cover, Modifier.size(120.dp), sizeHint = 256.dp)
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                    )
                }
                Spacer(Modifier.width(Spacing.l))
                Column(Modifier.weight(1f)) {
                    Text(state.kind.uppercase(), style = NmTheme.type.caption, color = colors.textTertiary)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        state.title,
                        style = NmTheme.type.sectionTitle.copy(fontSize = NmTheme.type.sectionTitle.fontSize * 1.15f),
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(state.subtitle, style = NmTheme.type.bodySmall, color = colors.textSecondary, maxLines = 3)
                }
            }
        }
        Spacer(Modifier.height(Spacing.l))
        if (state.songs.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                AccentPlayButton(onPlay)
                Spacer(Modifier.width(Spacing.xs))
                NmIconButton(NmIcons.Shuffle, "Shuffle", onShuffle)
                NmIconButton(NmIcons.PlaylistAdd, "Add all to playlist", onAddToPlaylist)
            }
            Spacer(Modifier.height(Spacing.m))
        }
    }
}
