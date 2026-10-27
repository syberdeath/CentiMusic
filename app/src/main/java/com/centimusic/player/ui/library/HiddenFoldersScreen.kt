package com.centimusic.player.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centimusic.player.data.media.pluralSongs
import com.centimusic.player.data.preferences.UserPreferences
import com.centimusic.player.navigation.LocalAppContainer
import com.centimusic.player.navigation.LocalChromePadding
import com.centimusic.player.navigation.LocalNavigator
import com.centimusic.player.ui.components.CircleIcon
import com.centimusic.player.ui.components.EmptyState
import com.centimusic.player.ui.components.TopBar
import com.centimusic.player.ui.components.bottomContentPadding
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Choose folders to leave out of the library (WhatsApp audio, call recordings, ringtones…). */
@Composable
fun HiddenFoldersScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val colors = NmTheme.colors
    val folders by container.music.allFolders.collectAsStateWithLifecycle()
    val prefs by container.preferences.preferences.collectAsStateWithLifecycle(initialValue = UserPreferences())
    val hidden = prefs.hiddenFolders

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomContentPadding(LocalChromePadding.current)) {
        item {
            TopBar(onBack = navigator::back)
            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                Text("Hidden folders", style = NmTheme.type.screenTitle, color = colors.textPrimary)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Turn a folder off to leave its songs out of your library, search and shuffle. Files aren't touched.",
                    style = NmTheme.type.bodySmall,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(Spacing.l))
            }
        }
        if (folders.isEmpty()) {
            item { EmptyState("No folders", "Folders that contain music will be listed here.", icon = NmIcons.Folder) }
        }
        items(folders, key = { it.first }) { (path, count) ->
            val shown = path !in hidden
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Switch) { scope.launch { container.preferences.setFolderHidden(path, shown) } }
                    .semantics { stateDescription = if (shown) "Shown" else "Hidden" }
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIcon(NmIcons.Folder, size = 40.dp, tint = if (shown) colors.textPrimary else colors.textTertiary)
                Spacer(Modifier.width(Spacing.l))
                Column(Modifier.weight(1f)) {
                    Text(
                        path.substringAfterLast('/').ifBlank { path.ifBlank { "Internal storage" } },
                        style = NmTheme.type.title,
                        color = if (shown) colors.textPrimary else colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${count.pluralSongs()} · ${path.ifEmpty { "/" }}",
                        style = NmTheme.type.caption,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(Spacing.m))
                FolderSwitch(shown)
            }
        }
    }
}

@Composable
private fun FolderSwitch(on: Boolean) {
    val colors = NmTheme.colors
    val track by animateColorAsState(if (on) colors.accent else colors.surfaceHigh, label = "track")
    val thumb by animateColorAsState(if (on) colors.onAccent else colors.textSecondary, label = "thumb")
    val offset by animateDpAsState(if (on) 18.dp else 0.dp, label = "offset")
    Box(
        Modifier
            .size(width = 42.dp, height = 24.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(16.dp)
                .clip(CircleShape)
                .background(thumb),
        )
    }
}
