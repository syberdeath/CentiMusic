package com.centimusic.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.centimusic.player.data.media.Album
import com.centimusic.player.data.media.Song
import com.centimusic.player.data.media.formatDuration
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing

/** Track row: artwork, title/artist, duration and an overflow menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    index: Int? = null,
    showArtwork: Boolean = true,
    onMore: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = NmTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(start = Spacing.gutter, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text(
                index.toString(),
                style = NmTheme.type.caption,
                color = colors.textTertiary,
                modifier = Modifier.width(24.dp),
            )
        }
        if (showArtwork) {
            Box {
                Artwork(song, Modifier.size(48.dp), corner = Spacing.thumbCorner, sizeHint = 48.dp)
                if (isCurrent) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                    )
                }
            }
            Spacer(Modifier.width(Spacing.m))
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = NmTheme.type.body,
                color = if (isCurrent) colors.accent else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                song.artist,
                style = NmTheme.type.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Spacing.s))
        Text(song.durationMs.formatDuration(), style = NmTheme.type.caption, color = colors.textTertiary)
        if (trailing != null) {
            trailing()
        } else if (onMore != null) {
            NmIconButton(NmIcons.MoreVertical, "More options for ${song.title}", onMore, tint = colors.textSecondary, iconSize = 20.dp)
        } else {
            Spacer(Modifier.width(Spacing.l))
        }
    }
}

/** Square cover with two lines of text below — the Recently Played / album grid card. */
@Composable
fun CoverCard(
    song: Song?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    seed: Int? = null,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(Spacing.artworkCorner))
            .clickable(onClick = onClick),
    ) {
        if (song != null || seed == null) {
            Artwork(song, Modifier.fillMaxWidth().aspectRatio(1f), sizeHint = 192.dp)
        } else {
            ArtworkPlaceholder(seed, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(Spacing.artworkCorner)))
        }
        Spacer(Modifier.height(Spacing.s))
        Text(title, style = NmTheme.type.bodySmall, color = NmTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = NmTheme.type.caption, color = NmTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(Spacing.xs))
    }
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) =
    CoverCard(album.cover, album.title, album.artist, onClick, modifier)

