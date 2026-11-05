package com.hkm.carlauncher.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hkm.carlauncher.apps.DockEntry
import com.hkm.carlauncher.apps.DockTarget
import com.hkm.carlauncher.ui.theme.LauncherCard
import com.hkm.carlauncher.ui.theme.LauncherColors
import com.hkm.carlauncher.ui.theme.LauncherIcons

@Composable
fun Dock(
    entries: List<Pair<DockEntry, DockTarget>>,
    onDock: (DockTarget) -> Unit,
    onAllApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LauncherCard(modifier) {
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            entries.forEach { (entry, target) ->
                DockItem(entry.title, { onDock(target) }, Modifier.weight(1f)) { iconFor(entry, it) }
            }
            DockItem("All apps", onAllApps, Modifier.weight(1f)) { LauncherIcons.Grid(it, LauncherColors.Accent) }
        }
    }
}

@Composable
private fun iconFor(entry: DockEntry, modifier: Modifier) {
    val c = LauncherColors.Accent
    when (entry) {
        DockEntry.BLUETOOTH -> LauncherIcons.Bluetooth(modifier, c)
        DockEntry.CARMATE -> LauncherIcons.Pin(modifier, c)
        DockEntry.MUSIC -> LauncherIcons.Note(modifier, c)
        DockEntry.VIDEO -> LauncherIcons.Video(modifier, c)
        DockEntry.GOOGLE_PLAY -> LauncherIcons.Store(modifier, c)
        DockEntry.SETTINGS -> LauncherIcons.Gear(modifier, c)
    }
}

@Composable
private fun DockItem(label: String, onClick: () -> Unit, modifier: Modifier, icon: @Composable (Modifier) -> Unit) {
    Column(
        modifier.fillMaxHeight().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon(Modifier.size(34.dp))
        Text(label, color = LauncherColors.Text.copy(alpha = 0.85f), fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
    }
}
