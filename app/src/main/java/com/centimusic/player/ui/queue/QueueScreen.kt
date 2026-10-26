package com.centimusic.player.ui.queue

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centimusic.player.data.media.formatDuration
import com.centimusic.player.navigation.LocalAppContainer
import com.centimusic.player.navigation.LocalNavigator
import com.centimusic.player.playback.QueueEntry
import com.centimusic.player.ui.components.Artwork
import com.centimusic.player.ui.components.ConfirmDialog
import com.centimusic.player.ui.components.EmptyState
import com.centimusic.player.ui.components.NmIconButton
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun QueueScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val haptics = LocalHapticFeedback.current
    val colors = NmTheme.colors
    val player = container.player
    val playback by player.state.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    // Local copy so drags are instant; the move is sent to the player when the drag ends.
    var upNext by remember(playback.upNext) { mutableStateOf(playback.upNext) }
    var dragging by remember { mutableStateOf<QueueEntry?>(null) }
    val reorderEnabled = !playback.shuffle

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = upNext.indexOfFirst { it.key == from.key }
        val toIndex = upNext.indexOfFirst { it.key == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            upNext = upNext.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.gutter, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(NmIcons.Queue, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Spacing.m))
            Text("Queue", style = NmTheme.type.sectionTitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
            if (upNext.isNotEmpty()) {
                Text(
                    "Clear",
                    style = NmTheme.type.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { confirmClear = true }
                        .padding(horizontal = Spacing.m, vertical = Spacing.s),
                )
            }
            NmIconButton(NmIcons.Close, "Close queue", navigator::back)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            val current = playback.current
            if (current != null) {
                item(key = "nowLabel") { Label("Now Playing") }
                item(key = "now") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            Artwork(current, Modifier.size(56.dp), corner = Spacing.thumbCorner, sizeHint = 64.dp)
                            if (playback.isPlaying) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(5.dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent),
                                )
                            }
                        }
                        Spacer(Modifier.width(Spacing.m))
                        Column(Modifier.weight(1f)) {
                            Text(current.title, style = NmTheme.type.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(current.artist, style = NmTheme.type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(playback.durationMs.formatDuration(), style = NmTheme.type.dotCaption, color = colors.textTertiary)
                    }
                    Spacer(Modifier.height(Spacing.m))
                }
            }

            item(key = "nextLabel") {
                Label(if (playback.shuffle) "Up Next · shuffled" else "Up Next")
                if (playback.shuffle && upNext.size > 1) {
                    Text(
                        "Turn off shuffle to reorder",
                        style = NmTheme.type.caption,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = Spacing.gutter),
                    )
                }
            }
            if (upNext.isEmpty()) {
                item(key = "empty") {
                    EmptyState("Nothing up next", "Use \"Play next\" or \"Add to queue\" from any song's menu.", icon = NmIcons.Queue)
                }
            }
            itemsIndexed(upNext, key = { _, entry -> entry.key }) { position, entry ->
                ReorderableItem(reorderState, key = entry.key, enabled = reorderEnabled) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .shadow(elevation)
                            .background(colors.background)
                            .clickable { player.playQueueItem(entry.index) }
                            .padding(start = Spacing.gutter, top = Spacing.xs, bottom = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (position + 2).toString(),
                            style = NmTheme.type.caption,
                            color = colors.textTertiary,
                            modifier = Modifier.width(28.dp),
                        )
                        Artwork(entry.song, Modifier.size(44.dp), corner = Spacing.thumbCorner, sizeHint = 48.dp)
                        Spacer(Modifier.width(Spacing.m))
                        Column(Modifier.weight(1f)) {
                            Text(entry.song.title, style = NmTheme.type.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entry.song.artist, style = NmTheme.type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(entry.song.durationMs.formatDuration(), style = NmTheme.type.dotCaption, color = colors.textTertiary)
                        NmIconButton(NmIcons.Close, "Remove ${entry.song.title} from queue", { player.removeQueueItem(entry.index) }, tint = colors.textTertiary, iconSize = 16.dp)
                        if (reorderEnabled) {
                            Box(
                                Modifier
                                    .draggableHandle(
                                        onDragStarted = {
                                            dragging = entry
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = {
                                            val moved = dragging
                                            val current = playback.currentIndex
                                            val target = moved?.let { e -> upNext.indexOfFirst { it.key == e.key } } ?: -1
                                            if (moved != null && target >= 0 && current >= 0) {
                                                // Without shuffle, play order is list order: slot `target` after the current item.
                                                player.moveQueueItem(moved.index, current + 1 + target)
                                            }
                                            dragging = null
                                        },
                                    )
                                    .size(Spacing.touchTarget),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(NmIcons.DragHandle, "Drag to reorder", tint = colors.textTertiary, modifier = Modifier.size(20.dp))
                            }
                        } else {
                            Spacer(Modifier.width(Spacing.m))
                        }
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(Spacing.xxl)) }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear queue?",
            message = "Everything except the current song will be removed from the queue.",
            confirmLabel = "Clear",
            onConfirm = {
                confirmClear = false
                player.clearUpNext()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = NmTheme.type.label,
        color = NmTheme.colors.textPrimary,
        modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s),
    )
}
