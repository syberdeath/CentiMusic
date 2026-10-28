package com.centimusic.player.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.centimusic.player.data.media.Album
import com.centimusic.player.data.media.formatTotalDuration
import com.centimusic.player.data.media.pluralSongs
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing

/** True once the list has scrolled past the item at [headerIndex] (the big header). */
@Composable
fun rememberHeaderCollapsed(listState: LazyListState, headerIndex: Int): State<Boolean> =
    remember(listState, headerIndex) { derivedStateOf { listState.firstVisibleItemIndex > headerIndex } }

/** Compact bar that slides in at the top once the big header has scrolled away. */
@Composable
fun CollapsingTitleBar(
    visible: Boolean,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPlay: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = NmTheme.colors
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().background(colors.background)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = Spacing.xs, end = Spacing.m, top = Spacing.xs, bottom = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NmIconButton(NmIcons.Back, "Back", onBack)
                Text(
                    title.uppercase(),
                    style = NmTheme.type.label.copy(fontSize = NmTheme.type.label.fontSize * 1.2f),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                actions()
                if (onPlay != null) {
                    Spacer(Modifier.width(Spacing.xs))
                    AccentPlayButton(onPlay, size = 38.dp, contentDescription = "Play $title")
                }
            }
            NmDivider()
        }
    }
}

/**
 * Album page header: the cover blurred into a dotted backdrop, the cover itself (shared with the card
 * that opened it), a dot-matrix title, the artist as a link, and play controls.
 */
@Composable
fun AlbumHeader(
    album: Album?,
    albumKey: String,
    onBack: () -> Unit,
    onArtist: (String) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddToPlaylist: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = NmTheme.colors
    Box(Modifier.fillMaxWidth()) {
        // Backdrop: the cover, heavily blurred where supported, fading into the page.
        Box(Modifier.matchParentSize()) {
            Artwork(
                album?.cover,
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.55f else 0.22f }
                    .blur(48.dp),
                corner = 0.dp,
                sizeHint = 192.dp,
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(colors.background.copy(alpha = 0.35f), colors.background.copy(alpha = 0.8f), colors.background)))
                    .dotGrid(colors.textPrimary, spacing = 10.dp, alpha = 0.12f),
            )
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            TopBar(onBack = onBack, actions = actions)
            Spacer(Modifier.height(Spacing.s))
            Artwork(
                album?.cover,
                Modifier
                    .size(232.dp)
                    .sharedArt(albumArtKey(albumKey)),
                sizeHint = 512.dp,
            )
            Spacer(Modifier.height(Spacing.xl))
            Text("ALBUM", style = NmTheme.type.dotCaption, color = colors.accent)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                album?.title.orEmpty(),
                style = NmTheme.type.screenTitle,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
            if (album != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    album.artist,
                    style = NmTheme.type.title,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onArtist(album.artist) }
                        .padding(horizontal = Spacing.s, vertical = 2.dp),
                )
                Spacer(Modifier.height(Spacing.s))
                val meta = listOfNotNull(
                    album.year.takeIf { it > 0 }?.toString(),
                    album.songs.size.pluralSongs(),
                    album.durationMs.formatTotalDuration(),
                ).joinToString("  ·  ")
                Text(meta.uppercase(), style = NmTheme.type.dotCaption, color = colors.textTertiary)
                Spacer(Modifier.height(Spacing.l))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    NmIconButton(NmIcons.Shuffle, "Shuffle ${album.title}", onShuffle)
                    AccentPlayButton(onPlay, size = 64.dp, contentDescription = "Play ${album.title}")
                    NmIconButton(NmIcons.PlaylistAdd, "Add ${album.title} to playlist", onAddToPlaylist)
                }
            }
            Spacer(Modifier.height(Spacing.l))
        }
    }
}

/** Shared-element key for an album's cover (album card ⇄ album page). */
fun albumArtKey(albumKey: String) = "album-$albumKey"
