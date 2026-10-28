package com.centimusic.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.centimusic.player.navigation.LocalAppContainer
import com.centimusic.player.navigation.LocalMessenger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pull down to rescan the device; a dot-matrix equalizer drops in while it runs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescanRefreshBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val container = LocalAppContainer.current
    val message = LocalMessenger.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val state = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            haptics.confirm()
            refreshing = true
            scope.launch {
                val before = container.music.library.value.songs.size
                val started = System.currentTimeMillis()
                runCatching { container.music.rescan() }
                // Long enough to read as a deliberate action even when the scan is instant.
                delay((900 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
                refreshing = false
                val diff = container.music.library.value.songs.size - before
                message(
                    when {
                        diff > 0 -> "Found $diff new ${if (diff == 1) "song" else "songs"}"
                        diff < 0 -> "${-diff} ${if (diff == -1) "song" else "songs"} removed"
                        else -> "Library up to date"
                    },
                )
            }
        },
        state = state,
        modifier = modifier.fillMaxSize(),
        indicator = {
            val pull = state.distanceFraction.coerceIn(0f, 1.4f)
            if (refreshing || pull > 0f) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 8.dp)
                        .graphicsLayer {
                            val p = if (refreshing) 1f else pull.coerceAtMost(1f)
                            translationY = (p - 1f) * 48.dp.toPx() + (if (refreshing) 0f else (pull - 1f).coerceAtLeast(0f) * 24.dp.toPx())
                            alpha = p
                            scaleX = 0.6f + 0.4f * p
                            scaleY = 0.6f + 0.4f * p
                        }
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(com.centimusic.player.ui.theme.NmTheme.colors.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    DotLoader()
                }
            }
        },
        content = content,
    )
}

/** [RescanRefreshBox] only where [enabled]; otherwise the content as is. */
@Composable
fun MaybeRescanRefresh(enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) RescanRefreshBox { content() } else content()
}
