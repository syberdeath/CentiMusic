package com.centimusic.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    /** Long-press action; defaults to the overflow menu. */
    onLongClick: (() -> Unit)? = onMore,
    /** Non-null while a multi-selection is active: shows a check circle instead of the trailing actions. */
    selected: Boolean? = null,
) {
    val colors = NmTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected == true) colors.accent.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
            MarqueeText(song.title, style = NmTheme.type.body, color = if (isCurrent) colors.accent else colors.textPrimary)
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
        Text(song.durationMs.formatDuration(), style = NmTheme.type.dotCaption, color = colors.textTertiary)
        if (selected != null) {
            Box(Modifier.size(Spacing.touchTarget), contentAlignment = Alignment.Center) {
                if (selected) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                        contentAlignment = Alignment.Center,
                    ) { androidx.compose.material3.Icon(NmIcons.Check, "Selected", tint = colors.onAccent, modifier = Modifier.size(14.dp)) }
                } else {
                    Box(
                        Modifier
                            .size(22.dp)
                            .border(1.5.dp, colors.textTertiary, CircleShape),
                    )
                }
            }
        } else if (trailing != null) {
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
    titleStyle: androidx.compose.ui.text.TextStyle = NmTheme.type.bodySmall,
    onPlay: (() -> Unit)? = null,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(Spacing.artworkCorner))
            .clickable(onClick = onClick),
    ) {
        Box {
            if (song != null || seed == null) {
                Artwork(song, Modifier.fillMaxWidth().aspectRatio(1f), sizeHint = 192.dp)
            } else {
                ArtworkPlaceholder(seed, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(Spacing.artworkCorner)))
            }
            if (onPlay != null) {
                val haptics = rememberHaptics()
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Spacing.s)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NmTheme.colors.accent)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                            haptics.tick()
                            onPlay()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(NmIcons.Play, "Play $title", tint = NmTheme.colors.onAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(Spacing.s))
        MarqueeText(title, style = titleStyle, color = NmTheme.colors.textPrimary, edge = 10.dp)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = NmTheme.type.caption, color = NmTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(Spacing.xs))
    }
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier, onPlay: (() -> Unit)? = null) =
    CoverCard(album.cover, album.title, album.artist, onClick, modifier, titleStyle = NmTheme.type.label, onPlay = onPlay)

