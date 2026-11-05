package com.hkm.carlauncher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hkm.carlauncher.apps.DockEntry
import com.hkm.carlauncher.apps.DockTarget
import com.hkm.carlauncher.clock.ClockCard
import com.hkm.carlauncher.clock.ClockText
import com.hkm.carlauncher.media.NowPlaying
import com.hkm.carlauncher.media.NowPlayingCard
import com.hkm.carlauncher.ui.theme.LauncherColors

data class HomeCallbacks(
    val onLogoLongPress: () -> Unit,
    val onDock: (DockTarget) -> Unit,
    val onAllApps: () -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onOpenSource: () -> Unit,
    val onGrantMediaAccess: () -> Unit,
    val onOpenMusic: () -> Unit,
)

/** The approved layout: 300 dp left column (logo, clock, now playing), map on the right, dock below it. */
@Composable
fun HomeScreen(
    clock: ClockText,
    nowPlaying: NowPlaying,
    dock: List<Pair<DockEntry, DockTarget>>,
    callbacks: HomeCallbacks,
    mapContent: @Composable (Modifier) -> Unit,
) {
    Row(
        Modifier.fillMaxSize().background(LauncherColors.Background).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LogoCard(callbacks.onLogoLongPress, Modifier.fillMaxWidth())
            ClockCard(clock, Modifier.fillMaxWidth())
            NowPlayingCard(
                state = nowPlaying,
                onPlayPause = callbacks.onPlayPause,
                onNext = callbacks.onNext,
                onPrevious = callbacks.onPrevious,
                onOpenSource = callbacks.onOpenSource,
                onGrantAccess = callbacks.onGrantMediaAccess,
                onOpenMusic = callbacks.onOpenMusic,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            mapContent(Modifier.fillMaxWidth().weight(1f))
            Dock(dock, callbacks.onDock, callbacks.onAllApps, Modifier.fillMaxWidth().height(104.dp))
        }
    }
}
