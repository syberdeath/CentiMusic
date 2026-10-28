package com.centimusic.player.ui.player

import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centimusic.player.data.media.Song
import com.centimusic.player.data.media.SongSource
import com.centimusic.player.data.media.formatDuration
import com.centimusic.player.navigation.LocalAppContainer
import com.centimusic.player.playback.PlaybackState
import com.centimusic.player.ui.components.Artwork
import com.centimusic.player.ui.components.MarqueeText
import com.centimusic.player.ui.components.NmIconButton
import com.centimusic.player.ui.components.rememberHaptics
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Swipeable strip under the Now Playing artwork: the track (title, artist, like), what's up next,
 * and the file's details. Three dots below show which page is open.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingPages(
    song: Song,
    playback: PlaybackState,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(pageCount = { 3 })
    Column(modifier) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().height(PAGE_HEIGHT),
            pageSpacing = Spacing.l,
            verticalAlignment = Alignment.Bottom,
        ) { page ->
            when (page) {
                0 -> TrackPage(song, isLiked, onToggleLike)
                1 -> UpNextPage(playback, onPlayQueueItem)
                else -> DetailsPage(song)
            }
        }
        Spacer(Modifier.height(Spacing.s))
        PageDots(count = 3, current = pager.currentPage, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun TrackPage(song: Song, isLiked: Boolean, onToggleLike: () -> Unit) {
    val colors = NmTheme.colors
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            AnimatedContent(song, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, contentKey = { it.id }, label = "title") { s ->
                Column {
                    MarqueeText(s.title, style = NmTheme.type.trackTitle, color = colors.textPrimary)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(s.artist, style = NmTheme.type.title, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        NmIconButton(
            if (isLiked) NmIcons.HeartFilled else NmIcons.Heart,
            if (isLiked) "Remove from Liked Songs" else "Add to Liked Songs",
            onToggleLike,
            tint = if (isLiked) colors.accent else colors.textPrimary,
        )
    }
}

@Composable
private fun UpNextPage(playback: PlaybackState, onPlayQueueItem: (Int) -> Unit) {
    val colors = NmTheme.colors
    val haptics = rememberHaptics()
    val next = playback.upNext.take(3)
    Column(Modifier.fillMaxSize()) {
        Text("UP NEXT", style = NmTheme.type.dotCaption, color = colors.textTertiary)
        Spacer(Modifier.height(Spacing.xs))
        if (next.isEmpty()) {
            Text("Nothing queued after this song", style = NmTheme.type.bodySmall, color = colors.textSecondary)
        }
        next.forEachIndexed { i, entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { haptics.tick(); onPlayQueueItem(entry.index) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", style = NmTheme.type.dotCaption, color = if (i == 0) colors.accent else colors.textTertiary, modifier = Modifier.width(18.dp))
                Artwork(entry.song, Modifier.size(20.dp), corner = 10.dp, sizeHint = 48.dp)
                Spacer(Modifier.width(Spacing.s))
                Text(
                    entry.song.title,
                    style = NmTheme.type.bodySmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    "  ·  ${entry.song.artist}",
                    style = NmTheme.type.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(0.01f))
                Text(entry.song.durationMs.formatDuration(), style = NmTheme.type.dotCaption, color = colors.textTertiary)
            }
        }
    }
}

private data class FileDetails(val format: String, val bitrate: String, val sampleRate: String)

@Composable
private fun DetailsPage(song: Song) {
    val colors = NmTheme.colors
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val counts by remember(container) { container.history.playCounts }.collectAsStateWithLifecycle(initialValue = emptyMap())
    val details by produceState<FileDetails?>(null, song.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, song.uri)
                    val mime = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                        ?: context.contentResolver.getType(song.uri)
                    val kbps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.div(1000)
                    val hz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
                    } else {
                        null
                    }
                    FileDetails(
                        format = mime?.substringAfter('/')?.removePrefix("x-")?.uppercase()?.let { if (it == "MPEG") "MP3" else it } ?: "—",
                        bitrate = kbps?.let { "$it kbps" } ?: "—",
                        sampleRate = hz?.let { "%.1f kHz".format(it / 1000f) } ?: "—",
                    )
                } finally {
                    r.release()
                }
            }.getOrNull() ?: FileDetails("—", "—", "—")
        }
    }
    val plays = counts[song.id] ?: 0
    Column(Modifier.fillMaxSize()) {
        Text("DETAILS", style = NmTheme.type.dotCaption, color = colors.textTertiary)
        Spacer(Modifier.height(Spacing.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Detail("FORMAT", details?.format ?: "…")
            Detail("BITRATE", details?.bitrate ?: "…")
            Detail("SAMPLE", details?.sampleRate ?: "…")
            Detail("PLAYS", plays.toString(), highlight = plays > 0)
        }
        Spacer(Modifier.height(Spacing.s))
        Text(
            when (song.source) {
                SongSource.IMPORTED -> "Imported file"
                SongSource.MEDIA_STORE -> song.folder.ifEmpty { "/" }
            },
            style = NmTheme.type.caption,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Detail(label: String, value: String, highlight: Boolean = false) {
    val colors = NmTheme.colors
    Column {
        Text(label, style = NmTheme.type.caption, color = colors.textTertiary)
        Text(value, style = NmTheme.type.label, color = if (highlight) colors.accent else colors.textPrimary, maxLines = 1)
    }
}

/** Nothing-style page indicator: small dots, the open page in red and slightly wider. */
@Composable
fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val colors = NmTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val active = i == current
            val width by animateDpAsState(if (active) 14.dp else 5.dp, label = "dotWidth")
            val color by animateColorAsState(if (active) colors.accent else colors.textTertiary, label = "dotColor")
            Box(
                Modifier
                    .size(width = width, height = 5.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

private val PAGE_HEIGHT = 92.dp
