package com.centimusic.files.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.centimusic.files.data.ThemeMode
import com.centimusic.files.data.ViewMode
import com.centimusic.files.theme.NmTheme
import com.centimusic.files.theme.Spacing

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s by vm.settings.collectAsState()
    val c = NmTheme.colors
    LazyColumn(Modifier.fillMaxSize().background(c.background), contentPadding = PaddingValues(bottom = 48.dp)) {
        item { Box(Modifier.statusBarsPadding()) { Header("Settings", onBack = { vm.back() }) } }

        item { SectionLabel("Appearance") }
        item {
            Segmented(
                options = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
                selected = s.theme,
                onSelect = { t -> vm.updateSettings { it.copy(theme = t) } },
            )
        }
        item { Spacer(Modifier.height(Spacing.m)) }
        item {
            Segmented(
                options = listOf(ViewMode.LIST to "List", ViewMode.GRID to "Grid"),
                selected = s.viewMode,
                onSelect = { v -> vm.updateSettings { it.copy(viewMode = v) } },
            )
        }

        item { SectionLabel("Files") }
        item {
            ToggleRow("Show hidden files", "Files and folders whose names start with a dot", s.showHidden) {
                vm.updateSettings { it.copy(showHidden = !it.showHidden) }
            }
        }
        item {
            ToggleRow("Confirm before deleting", "Ask before files are permanently deleted", s.confirmDelete) {
                vm.updateSettings { it.copy(confirmDelete = !it.confirmDelete) }
            }
        }
        item {
            ActionRow("Clear recent searches", "${s.recentSearches.size} saved") { vm.clearSearches(); vm.say("Cleared") }
        }
        item { ActionRow("Refresh file index", "Re-read storage for categories, search and Clean") { vm.reloadAll(); vm.say("Refreshed") } }

        item { SectionLabel("About") }
        item {
            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                Row {
                    Text("CENTI", style = NmTheme.type.sectionTitle, color = c.textPrimary)
                    Text("FILES", style = NmTheme.type.sectionTitle, color = c.accent)
                }
                Spacer(Modifier.height(Spacing.s))
                Text(
                    "A calm, offline file manager in the same design language as CentiMusic. No accounts, no network, no tracking.",
                    style = NmTheme.type.body, color = c.textSecondary,
                )
                Spacer(Modifier.height(Spacing.s))
                Text(
                    "An independent project, not affiliated with Nothing Technology Ltd. or Google.",
                    style = NmTheme.type.caption, color = c.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val c = NmTheme.colors
    Row(
        Modifier.padding(horizontal = Spacing.gutter).fillMaxWidth().clip(RoundedCornerShape(50)).background(c.surface).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) c.inverse else c.surface)
                    .clickable { onSelect(value) }.padding(vertical = Spacing.m),
                contentAlignment = Alignment.Center,
            ) {
                Text(label.uppercase(), style = NmTheme.type.label, color = if (on) c.onInverse else c.textSecondary)
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, detail: String, on: Boolean, onToggle: () -> Unit) {
    val c = NmTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = Spacing.gutter, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = NmTheme.type.title, color = c.textPrimary)
            Text(detail, style = NmTheme.type.bodySmall, color = c.textSecondary)
        }
        Spacer(Modifier.width(Spacing.l))
        DotSwitch(on)
    }
}

/** A Nothing-style switch: pill track with a single dot that turns red when on. */
@Composable
private fun DotSwitch(on: Boolean) {
    val c = NmTheme.colors
    Box(
        Modifier.size(width = 44.dp, height = 26.dp).clip(RoundedCornerShape(50)).background(if (on) c.inverse else c.surfaceHigh),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.offset(x = if (on) 22.dp else 4.dp).size(18.dp).clip(CircleShape)
                .background(if (on) c.accent else c.textTertiary),
        )
    }
}

@Composable
private fun ActionRow(title: String, detail: String, onClick: () -> Unit) {
    val c = NmTheme.colors
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.gutter, vertical = Spacing.m)) {
        Text(title, style = NmTheme.type.title, color = c.textPrimary)
        Text(detail, style = NmTheme.type.bodySmall, color = c.textSecondary)
    }
}
