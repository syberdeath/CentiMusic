package com.centimusic.player.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centimusic.player.data.media.formatTotalDuration
import com.centimusic.player.data.media.pluralSongs
import com.centimusic.player.data.repository.Playlist
import com.centimusic.player.navigation.LocalAppContainer
import com.centimusic.player.navigation.LocalChromePadding
import com.centimusic.player.navigation.LocalNavigator
import com.centimusic.player.ui.components.Artwork
import com.centimusic.player.ui.components.ArtworkPlaceholder
import com.centimusic.player.ui.components.CircleIcon
import com.centimusic.player.ui.components.EmptyState
import com.centimusic.player.ui.components.TextInputDialog
import com.centimusic.player.ui.components.TopBar
import com.centimusic.player.ui.components.bottomContentPadding
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun PlaylistsScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val playlists by container.playlists.playlists.collectAsStateWithLifecycle(initialValue = null)
    var creating by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomContentPadding(LocalChromePadding.current)) {
        item {
            TopBar(onBack = navigator::back)
            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                Text("Playlists", style = NmTheme.type.screenTitle, color = NmTheme.colors.textPrimary)
                Spacer(Modifier.height(Spacing.l))
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { creating = true }
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIcon(NmIcons.Plus, size = 56.dp)
                Spacer(Modifier.width(Spacing.l))
                Text("New playlist", style = NmTheme.type.title, color = NmTheme.colors.textPrimary)
            }
        }
        val list = playlists
        if (list != null && list.isEmpty()) {
            item { EmptyState("No playlists yet", "Create a playlist, then add songs from any song's menu.", icon = NmIcons.PlaylistAdd) }
        }
        items(list.orEmpty(), key = { it.id }) { playlist ->
            PlaylistRow(playlist) { navigator.playlist(playlist.id) }
        }
    }

    if (creating) {
        TextInputDialog(
            title = "New playlist",
            confirmLabel = "Create",
            placeholder = "Playlist name",
            onConfirm = { name ->
                creating = false
                scope.launch { navigator.playlist(container.playlists.create(name)) }
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistCover(playlist, 56.dp)
        Spacer(Modifier.width(Spacing.l))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = NmTheme.type.title, color = NmTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${playlist.songs.size.pluralSongs()} · ${playlist.durationMs.formatTotalDuration()}",
                style = NmTheme.type.bodySmall,
                color = NmTheme.colors.textSecondary,
            )
        }
        Icon(NmIcons.ChevronRight, contentDescription = null, tint = NmTheme.colors.textTertiary, modifier = Modifier.size(18.dp))
    }
}

/** First track's artwork, or a placeholder seeded by the playlist id. Red dot marks it as a playlist. */
@Composable
fun PlaylistCover(playlist: Playlist, size: Dp, corner: Dp = Spacing.thumbCorner * (size.value / 48f)) {
    Box(Modifier.size(size)) {
        val first = playlist.songs.firstOrNull()
        if (first != null) {
            Artwork(first, Modifier.size(size), corner = corner, sizeHint = size)
        } else {
            ArtworkPlaceholder(playlist.id.toInt(), Modifier.size(size).clip(RoundedCornerShape(corner)))
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(size * 0.08f)
                .size(size * 0.12f)
                .clip(RoundedCornerShape(50))
                .background(NmTheme.colors.accent),
        )
    }
}
