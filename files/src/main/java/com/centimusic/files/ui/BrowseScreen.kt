package com.centimusic.files.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.centimusic.files.data.Category
import com.centimusic.files.data.formatSize
import com.centimusic.files.theme.FIcons
import com.centimusic.files.theme.NmTheme
import com.centimusic.files.theme.Spacing

fun Category.icon(): ImageVector = when (this) {
    Category.DOWNLOADS -> FIcons.Download
    Category.IMAGES -> FIcons.Image
    Category.VIDEOS -> FIcons.Video
    Category.AUDIO -> FIcons.Audio
    Category.DOCUMENTS -> FIcons.Document
    Category.APPS -> FIcons.Apk
}

@Composable
fun BrowseScreen(vm: AppViewModel) {
    val state by vm.browse.collectAsState()
    val settings by vm.settings.collectAsState()
    val revision by vm.revision.collectAsState()
    val context = LocalContext.current
    val c = NmTheme.colors
    LaunchedEffect(revision) { vm.refreshBrowse() }

    LazyColumn(Modifier.fillMaxSize().background(c.background), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = Spacing.gutter, end = Spacing.s, top = Spacing.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("CENTI", style = NmTheme.type.brand, color = c.textPrimary)
                    Text("FILES", style = NmTheme.type.brand, color = c.accent)
                    Spacer(Modifier.weight(1f))
                    IconButtonNm(FIcons.Settings, "Settings", { vm.open(Screen.Settings) })
                }
                Spacer(Modifier.height(Spacing.l))
                SearchPill { vm.open(Screen.Search) }
            }
        }

        if (state.recent.isNotEmpty()) {
            item { SectionLabel("Recent") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.gutter),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    items(state.recent, key = { it.path }) { f ->
                        Column(Modifier.width(112.dp).clickable {
                            if (!context.openFile(f)) vm.say("No app can open this file")
                        }) {
                            FileThumb(f, vm.thumbnails, 112.dp, corner = Spacing.artworkCorner)
                            Spacer(Modifier.height(Spacing.s))
                            Text(f.name, style = NmTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        item { SectionLabel("Categories") }
        item {
            Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Category.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        row.forEach { cat ->
                            val summary = state.categories[cat]
                            CategoryCard(
                                cat,
                                detail = when {
                                    summary == null -> "…"
                                    else -> "${summary.first} · ${formatSize(summary.second)}"
                                },
                                modifier = Modifier.weight(1f),
                            ) { vm.open(Screen.CategoryList(cat)) }
                        }
                    }
                }
            }
        }

        item { SectionLabel("Collections") }
        item {
            ListCard(FIcons.Star, "Favourites", "${settings.favourites.size} items") { vm.open(Screen.Favourites) }
        }

        item { SectionLabel("Storage devices") }
        item { StorageCard(vm, onClick = { vm.open(Screen.Folder(vm.repo.root)) }) }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    val c = NmTheme.colors
    Row(
        Modifier
            .padding(horizontal = Spacing.gutter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(c.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(FIcons.Search, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.m))
        Text("Search files", style = NmTheme.type.body, color = c.textSecondary)
    }
}

@Composable
private fun CategoryCard(cat: Category, detail: String, modifier: Modifier, onClick: () -> Unit) {
    val c = NmTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(Spacing.artworkCorner + 4.dp))
            .background(c.surface)
            .clickable(onClick = onClick)
            .padding(Spacing.l),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
            Icon(cat.icon(), null, tint = c.textPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(Spacing.l))
        Text(cat.label.uppercase(), style = NmTheme.type.label, color = c.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(detail, style = NmTheme.type.caption, color = c.textSecondary, maxLines = 1)
    }
}

@Composable
fun ListCard(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    val c = NmTheme.colors
    Row(
        Modifier
            .padding(horizontal = Spacing.gutter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Spacing.artworkCorner + 4.dp))
            .background(c.surface)
            .clickable(onClick = onClick)
            .padding(Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.textPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Spacing.l))
        Column(Modifier.weight(1f)) {
            Text(title, style = NmTheme.type.title, color = c.textPrimary)
            Text(detail, style = NmTheme.type.bodySmall, color = c.textSecondary)
        }
        Icon(FIcons.ChevronRight, null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun StorageCard(vm: AppViewModel, onClick: () -> Unit) {
    val state by vm.browse.collectAsState()
    val c = NmTheme.colors
    val s = state.storage
    Column(
        Modifier
            .padding(horizontal = Spacing.gutter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Spacing.artworkCorner + 4.dp))
            .border(1.dp, c.divider, RoundedCornerShape(Spacing.artworkCorner + 4.dp))
            .clickable(onClick = onClick)
            .padding(Spacing.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(FIcons.Storage, null, tint = c.textPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.m))
            Text("INTERNAL STORAGE", style = NmTheme.type.label, color = c.textPrimary, modifier = Modifier.weight(1f))
            Icon(FIcons.ChevronRight, null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(Spacing.l))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatSize(s.free), style = NmTheme.type.screenTitle, color = c.textPrimary)
            Spacer(Modifier.width(Spacing.s))
            Text("FREE", style = NmTheme.type.dotCaption, color = c.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(Spacing.m))
        DotGauge(s.usedFraction)
        Spacer(Modifier.height(Spacing.m))
        Text(
            "${formatSize(s.used)} used of ${formatSize(s.total)}",
            style = NmTheme.type.bodySmall, color = c.textSecondary,
        )
    }
}
