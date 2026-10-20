package com.centimusic.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centimusic.player.data.media.Song
import com.centimusic.player.data.media.SongSource
import com.centimusic.player.data.media.pluralSongs
import com.centimusic.player.navigation.LocalAppContainer
import com.centimusic.player.navigation.LocalMessenger
import com.centimusic.player.navigation.LocalNavigator
import com.centimusic.player.navigation.SongMenuController
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Hosts the per-song bottom sheet and the "add to playlist" picker for the whole app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsHost(controller: SongMenuController) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val message = LocalMessenger.current
    val scope = rememberCoroutineScope()
    val colors = NmTheme.colors
    val liked by container.favorites.likedIds.collectAsStateWithLifecycle()

    val request = controller.request
    if (request != null) {
        val song = request.song
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        fun close(then: () -> Unit = {}) {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                controller.dismiss()
                then()
            }
        }
        ModalBottomSheet(
            onDismissRequest = controller::dismiss,
            sheetState = sheetState,
            containerColor = colors.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = Spacing.m, bottom = Spacing.s)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.divider),
                )
            },
        ) {
            Column(Modifier.navigationBarsPadding().padding(bottom = Spacing.l)) {
                Row(
                    Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(song, Modifier.size(52.dp), corner = Spacing.thumbCorner, sizeHint = 64.dp)
                    Spacer(Modifier.width(Spacing.m))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, style = NmTheme.type.title, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${song.artist} · ${song.album}", style = NmTheme.type.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                NmDivider(Modifier.padding(horizontal = Spacing.gutter))
                Spacer(Modifier.height(Spacing.xs))

                SheetAction(NmIcons.PlayNext, "Play next") {
                    container.player.playNext(listOf(song))
                    close { message("Playing next") }
                }
                SheetAction(NmIcons.Queue, "Add to queue") {
                    container.player.addToQueue(listOf(song))
                    close { message("Added to queue") }
                }
                SheetAction(NmIcons.PlaylistAdd, "Add to playlist") {
                    close { controller.pickPlaylistFor(listOf(song)) }
                }
                val isLiked = song.id in liked
                SheetAction(if (isLiked) NmIcons.HeartFilled else NmIcons.Heart, if (isLiked) "Remove from Liked Songs" else "Add to Liked Songs") {
                    scope.launch { container.favorites.setLiked(song.id, !isLiked) }
                    close()
                }
                request.playlistId?.let { playlistId ->
                    SheetAction(NmIcons.Minus, "Remove from this playlist") {
                        scope.launch { container.playlists.remove(playlistId, song.id) }
                        close { message("Removed from playlist") }
                    }
                }
                SheetAction(NmIcons.Disc, "Go to album") { close { navigator.album(song.albumKey) } }
                SheetAction(NmIcons.Person, "Go to artist") { close { navigator.artist(song.artist) } }
                if (song.source == SongSource.IMPORTED) {
                    SheetAction(NmIcons.Trash, "Remove from library") {
                        scope.launch { container.music.removeImported(song) }
                        close { message("Removed from library") }
                    }
                }
            }
        }
    }

    controller.addToPlaylist?.let { songs ->
        AddToPlaylistDialog(songs, onDismiss = controller::dismissPlaylistPicker)
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.gutter, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = NmTheme.colors.textPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Spacing.l))
        Text(label, style = NmTheme.type.title, color = NmTheme.colors.textPrimary)
    }
}

@Composable
fun AddToPlaylistDialog(songs: List<Song>, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val message = LocalMessenger.current
    val scope = rememberCoroutineScope()
    val colors = NmTheme.colors
    val playlists by container.playlists.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    var creating by remember { mutableStateOf(false) }
    val ids = songs.map { it.id }

    if (creating) {
        TextInputDialog(
            title = "New playlist",
            confirmLabel = "Create",
            placeholder = "Playlist name",
            onConfirm = { name ->
                scope.launch {
                    container.playlists.create(name, ids)
                    message("Created \"$name\"")
                }
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(20),
        title = { Text("Add to playlist", style = NmTheme.type.sectionTitle, color = colors.textPrimary) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    PickerRow(NmIcons.Plus, "New playlist", null) { creating = true }
                }
                items(playlists, key = { it.id }) { playlist ->
                    PickerRow(NmIcons.Note, playlist.name, playlist.songs.size.pluralSongs()) {
                        scope.launch {
                            val added = container.playlists.add(playlist.id, ids)
                            message(if (added == 0) "Already in \"${playlist.name}\"" else "Added to \"${playlist.name}\"")
                        }
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textSecondary) } },
    )
}

@Composable
private fun PickerRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIcon(icon, size = 40.dp)
        Spacer(Modifier.width(Spacing.m))
        Column {
            Text(title, style = NmTheme.type.title, color = NmTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = NmTheme.type.caption, color = NmTheme.colors.textSecondary)
        }
    }
}
