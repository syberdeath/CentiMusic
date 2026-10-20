package com.centimusic.player.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centimusic.player.data.media.pluralSongs
import com.centimusic.player.navigation.LocalAppContainer
import com.centimusic.player.navigation.LocalMessenger
import com.centimusic.player.navigation.LocalNavigator
import com.centimusic.player.ui.components.EmptyState
import com.centimusic.player.ui.components.NmDivider
import com.centimusic.player.ui.components.PillButton
import com.centimusic.player.ui.components.SongRow
import com.centimusic.player.ui.components.TopBar
import com.centimusic.player.ui.search.SearchField
import com.centimusic.player.ui.search.matches
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun AddSongsScreen(playlistId: Long) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val message = LocalMessenger.current
    val scope = rememberCoroutineScope()
    val colors = NmTheme.colors
    val library by container.music.library.collectAsStateWithLifecycle()
    val playlist by remember(playlistId) { container.playlists.playlist(playlistId) }.collectAsStateWithLifecycle(initialValue = null)
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptyList<String>()) }

    val existing = remember(playlist) { playlist?.songs.orEmpty().mapTo(HashSet()) { it.id } }
    val songs = remember(library, query) {
        if (query.isBlank()) library.songs else library.songs.filter { it.matches(query) }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(onBack = navigator::back, title = playlist?.let { "Add to ${it.name}" } ?: "Add songs")
        SearchField(query, { query = it }, placeholder = "Filter songs", modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s))
        LazyColumn(Modifier.weight(1f)) {
            if (songs.isEmpty()) {
                item { EmptyState("No matches", "Try a different title, artist or album.", icon = NmIcons.Search) }
            }
            items(songs, key = { it.id }) { song ->
                val already = song.id in existing
                val isSelected = song.id in selected
                SongRow(
                    song = song,
                    onClick = {
                        if (!already) selected = if (isSelected) selected - song.id else selected + song.id
                    },
                    trailing = {
                        Box(Modifier.size(Spacing.touchTarget), contentAlignment = Alignment.Center) {
                            when {
                                already -> Text("Added", style = NmTheme.type.caption, color = colors.textTertiary)
                                isSelected -> Box(
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(NmIcons.Check, "Selected", tint = colors.onAccent, modifier = Modifier.size(16.dp)) }
                                else -> Box(
                                    Modifier
                                        .size(24.dp)
                                        .border(1.5.dp, colors.divider, CircleShape),
                                )
                            }
                        }
                    },
                )
            }
        }
        NmDivider()
        Box(
            Modifier
                .fillMaxWidth()
                .background(colors.background)
                .navigationBarsPadding()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.m),
            contentAlignment = Alignment.CenterEnd,
        ) {
            PillButton(
                text = if (selected.isEmpty()) "Select songs" else "Add ${selected.size.pluralSongs()}",
                filled = selected.isNotEmpty(),
                icon = NmIcons.Plus,
                onClick = {
                    if (selected.isNotEmpty()) {
                        scope.launch {
                            val added = container.playlists.add(playlistId, selected)
                            message("Added ${added.pluralSongs()}")
                            navigator.back()
                        }
                    }
                },
            )
        }
        Spacer(Modifier.height(0.dp))
    }
}
