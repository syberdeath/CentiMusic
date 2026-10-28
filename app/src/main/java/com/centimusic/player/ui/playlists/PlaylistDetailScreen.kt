package com.centimusic.player.ui.playlists

import androidx.compose.animation.core.animateDpAsState
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
import com.centimusic.player.ui.components.CollapsingTitleBar
import com.centimusic.player.ui.components.rememberHeaderCollapsed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.centimusic.player.AppContainer
import com.centimusic.player.data.media.Song
import com.centimusic.player.data.media.formatTotalDuration
import com.centimusic.player.data.media.pluralSongs
import com.centimusic.player.data.repository.Playlist
import com.centimusic.player.navigation.LocalAppContainer
import com.centimusic.player.navigation.LocalChromePadding
import com.centimusic.player.navigation.LocalNavigator
import com.centimusic.player.navigation.LocalSongMenu
import com.centimusic.player.ui.appViewModel
import com.centimusic.player.ui.components.AccentPlayButton
import com.centimusic.player.ui.components.ConfirmDialog
import com.centimusic.player.ui.components.EmptyState
import com.centimusic.player.ui.components.LoadingState
import com.centimusic.player.ui.components.NmIconButton
import com.centimusic.player.ui.components.PillButton
import com.centimusic.player.ui.components.SongRow
import com.centimusic.player.ui.components.TextInputDialog
import com.centimusic.player.ui.components.TopBar
import com.centimusic.player.ui.components.bottomContentPadding
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

sealed interface PlaylistUiState {
    data object Loading : PlaylistUiState
    data object Missing : PlaylistUiState
    data class Ready(val playlist: Playlist) : PlaylistUiState
}

class PlaylistViewModel(private val container: AppContainer, private val id: Long) : ViewModel() {
    val state: StateFlow<PlaylistUiState> = container.playlists.playlist(id)
        .map { if (it == null) PlaylistUiState.Missing else PlaylistUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistUiState.Loading)

    fun rename(name: String) = viewModelScope.launch { container.playlists.rename(id, name) }
    fun delete() = viewModelScope.launch { container.playlists.delete(id) }
    fun saveOrder(songs: List<Song>) = viewModelScope.launch { container.playlists.setOrder(id, songs.map { it.id }) }
}

@Composable
fun PlaylistDetailScreen(id: Long) {
    val vm = appViewModel(key = "playlist:$id") { PlaylistViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    when (val s = state) {
        PlaylistUiState.Loading -> Column {
            TopBar(onBack = navigator::back)
            LoadingState("Loading playlist")
        }
        PlaylistUiState.Missing -> {
            // Deleted (possibly from this screen): leave.
            LaunchedEffect(Unit) { navigator.back() }
        }
        is PlaylistUiState.Ready -> PlaylistContent(s.playlist, vm)
    }
}

@Composable
private fun PlaylistContent(playlist: Playlist, vm: PlaylistViewModel) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val menu = LocalSongMenu.current
    val haptics = LocalHapticFeedback.current
    val playback by container.player.state.collectAsStateWithLifecycle()
    val colors = NmTheme.colors

    // Local copy so dragging is instant; persisted when the drag ends.
    var songs by remember(playlist.songs) { mutableStateOf(playlist.songs) }
    var showMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = songs.indexOfFirst { it.id == from.key }
        val toIndex = songs.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            songs = songs.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val collapsed by rememberHeaderCollapsed(listState, headerIndex = 1)
    Box(Modifier.fillMaxSize()) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = bottomContentPadding(LocalChromePadding.current)) {
        item(key = "top") {
            TopBar(onBack = navigator::back) {
                Box {
                    NmIconButton(NmIcons.MoreVertical, "Playlist options", { showMenu = true })
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = colors.surface) {
                        DropdownMenuItem(
                            text = { Text("Rename", color = colors.textPrimary) },
                            leadingIcon = { Icon(NmIcons.Edit, null, tint = colors.textPrimary) },
                            onClick = { showMenu = false; renaming = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Add to queue", color = colors.textPrimary) },
                            leadingIcon = { Icon(NmIcons.Queue, null, tint = colors.textPrimary) },
                            onClick = { showMenu = false; container.player.addToQueue(songs) },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete playlist", color = colors.accent) },
                            leadingIcon = { Icon(NmIcons.Trash, null, tint = colors.accent) },
                            onClick = { showMenu = false; deleting = true },
                        )
                    }
                }
            }
        }
        item(key = "header") {
            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlaylistCover(playlist.copy(songs = songs), 120.dp, corner = 12.dp)
                    Spacer(Modifier.width(Spacing.l))
                    Column(Modifier.weight(1f)) {
                        Text(
                            playlist.name,
                            style = NmTheme.type.sectionTitle.copy(fontSize = NmTheme.type.sectionTitle.fontSize * 1.2f),
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            "Playlist · ${songs.size.pluralSongs()} · ${playlist.durationMs.formatTotalDuration()}",
                            style = NmTheme.type.bodySmall,
                            color = colors.textSecondary,
                        )
                        if (playlist.missingCount > 0) {
                            Text("${playlist.missingCount} unavailable", style = NmTheme.type.caption, color = colors.textTertiary)
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.l))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (songs.isNotEmpty()) {
                        AccentPlayButton({ container.player.play(songs, shuffle = false) })
                        Spacer(Modifier.width(Spacing.xs))
                        NmIconButton(NmIcons.Shuffle, "Shuffle playlist", { container.player.shuffle(songs) })
                    }
                    Spacer(Modifier.weight(1f))
                    PillButton("Add songs", { navigator.addToPlaylist(playlist.id) }, icon = NmIcons.Plus)
                }
                Spacer(Modifier.height(Spacing.m))
            }
        }
        if (songs.isEmpty()) {
            item(key = "empty") {
                EmptyState("Empty playlist", "Add songs from your library to get started.", icon = NmIcons.PlaylistAdd)
            }
        }
        items(songs, key = { it.id }) { song ->
            ReorderableItem(reorderState, key = song.id) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag")
                SongRow(
                    song = song,
                    onClick = { container.player.play(songs, songs.indexOf(song)) },
                    isCurrent = playback.current?.id == song.id,
                    modifier = Modifier
                        .shadow(elevation)
                        .background(colors.background),
                    onMore = { menu.open(song, playlist.id) },
                    trailing = {
                        Row {
                            NmIconButton(NmIcons.MoreVertical, "More options for ${song.title}", { menu.open(song, playlist.id) }, tint = colors.textSecondary, iconSize = 20.dp)
                            Box(
                                Modifier
                                    .draggableHandle(
                                        onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                        onDragStopped = { vm.saveOrder(songs) },
                                    )
                                    .size(Spacing.touchTarget),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(NmIcons.DragHandle, "Drag to reorder ${song.title}", tint = colors.textTertiary, modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                )
            }
        }
    }
    CollapsingTitleBar(
        visible = collapsed,
        title = playlist.name,
        onBack = navigator::back,
        onPlay = if (songs.isNotEmpty()) ({ container.player.play(songs, shuffle = false) }) else null,
        modifier = Modifier.align(Alignment.TopCenter),
    )
    }

    if (renaming) {
        TextInputDialog(
            title = "Rename playlist",
            confirmLabel = "Save",
            initial = playlist.name,
            onConfirm = { renaming = false; vm.rename(it) },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = "Delete \"${playlist.name}\"?",
            message = "The playlist will be removed. Your music files are not affected.",
            confirmLabel = "Delete",
            onConfirm = { deleting = false; vm.delete() },
            onDismiss = { deleting = false },
        )
    }
}
