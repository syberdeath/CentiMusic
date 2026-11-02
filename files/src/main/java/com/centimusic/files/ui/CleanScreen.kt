package com.centimusic.files.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.centimusic.files.data.CleanReport
import com.centimusic.files.data.FileItem
import com.centimusic.files.data.formatSize
import com.centimusic.files.theme.FIcons
import com.centimusic.files.theme.NmTheme
import com.centimusic.files.theme.Spacing

fun CleanKind.icon(): ImageVector = when (this) {
    CleanKind.JUNK -> FIcons.Clean
    CleanKind.DUPLICATES -> FIcons.Duplicate
    CleanKind.LARGE -> FIcons.Large
    CleanKind.OLD_DOWNLOADS -> FIcons.Download
    CleanKind.OLD_SCREENSHOTS -> FIcons.Screenshot
}

fun CleanReport.files(kind: CleanKind): List<FileItem> = when (kind) {
    CleanKind.JUNK -> junk
    CleanKind.DUPLICATES -> duplicates.flatten()
    CleanKind.LARGE -> large
    CleanKind.OLD_DOWNLOADS -> oldDownloads
    CleanKind.OLD_SCREENSHOTS -> oldScreenshots
}

/** What a kind can free: for duplicates only the extra copies count. */
fun CleanReport.reclaimable(kind: CleanKind): Long = when (kind) {
    CleanKind.DUPLICATES -> duplicates.sumOf { g -> g.drop(1).sumOf { it.size } }
    else -> files(kind).sumOf { it.size }
}

/** Default selection: all junk, every duplicate except the oldest copy, nothing else. */
fun CleanReport.defaultSelection(kind: CleanKind): Set<String> = when (kind) {
    CleanKind.JUNK -> junk.map { it.path }.toSet()
    CleanKind.DUPLICATES -> duplicates.flatMap { g -> g.dropLast(1).map { it.path } }.toSet()
    else -> emptySet()
}

@Composable
fun CleanScreen(vm: AppViewModel) {
    val state by vm.clean.collectAsState()
    val browse by vm.browse.collectAsState()
    val c = NmTheme.colors
    var confirmJunk by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (state.report == null) vm.scan(); vm.refreshBrowse() }
    val report = state.report

    LazyColumn(Modifier.fillMaxSize().background(c.background), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = Spacing.gutter).padding(top = Spacing.l)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CLEAN", style = NmTheme.type.screenTitle, color = c.textPrimary, modifier = Modifier.weight(1f))
                    if (state.scanning) DotLoader()
                    else Text(
                        "RESCAN", style = NmTheme.type.label, color = c.accent,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { vm.scan() }.padding(Spacing.m),
                    )
                }
                Spacer(Modifier.height(Spacing.xxl))
                Text("${formatSize(browse.storage.free)} FREE", style = NmTheme.type.hero, color = c.textPrimary)
                Spacer(Modifier.height(Spacing.m))
                DotGauge(browse.storage.usedFraction, dots = 24, dotSize = 9.dp)
                Spacer(Modifier.height(Spacing.m))
                val total = report?.let { r -> CleanKind.entries.sumOf { r.reclaimable(it) } }
                Text(
                    when {
                        report == null -> "Looking for things you can clear…"
                        total == 0L -> "Nothing to clean. Your storage is tidy."
                        else -> "Up to ${formatSize(total!!)} can be freed"
                    },
                    style = NmTheme.type.body, color = c.textSecondary,
                )
            }
        }

        if (report != null) {
            item { Spacer(Modifier.height(Spacing.xl)) }
            items(CleanKind.entries.toList(), key = { it.name }) { kind ->
                val files = report.files(kind)
                if (files.isEmpty()) return@items
                CleanCard(
                    kind = kind,
                    count = if (kind == CleanKind.DUPLICATES) report.duplicates.size else files.size,
                    size = report.reclaimable(kind),
                    onClean = if (kind == CleanKind.JUNK) ({ confirmJunk = true }) else null,
                    onOpen = { vm.open(Screen.CleanDetail(kind)) },
                )
            }
        }
    }

    if (confirmJunk && report != null) {
        ConfirmDialog(
            title = "Clear junk?",
            body = "Deletes ${report.junk.size} junk items (${formatSize(report.reclaimable(CleanKind.JUNK))}). Thumbnail caches are rebuilt by apps when needed.",
            confirm = "Clear",
            onConfirm = { vm.delete(report.junk.map { it.path }); confirmJunk = false },
            onDismiss = { confirmJunk = false },
        )
    }
}

@Composable
private fun CleanCard(kind: CleanKind, count: Int, size: Long, onClean: (() -> Unit)?, onOpen: () -> Unit) {
    val c = NmTheme.colors
    Column(
        Modifier
            .padding(horizontal = Spacing.gutter, vertical = Spacing.s)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Spacing.artworkCorner + 4.dp))
            .background(c.surface)
            .clickable(onClick = onOpen)
            .padding(Spacing.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
                Icon(kind.icon(), null, tint = c.textPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Spacing.l))
            Column(Modifier.weight(1f)) {
                Text(kind.title.uppercase(), style = NmTheme.type.label, color = c.textPrimary)
                Text(kind.blurb, style = NmTheme.type.bodySmall, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(Spacing.l))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatSize(size), style = NmTheme.type.sectionTitle, color = c.textPrimary)
                Text(
                    if (kind == CleanKind.DUPLICATES) "$count sets of copies" else if (count == 1) "1 item" else "$count items",
                    style = NmTheme.type.caption, color = c.textTertiary,
                )
            }
            if (onClean != null) PillButton("Clear", onClean, primary = true)
            else PillButton("Review", onOpen)
        }
    }
}

@Composable
fun CleanDetailScreen(vm: AppViewModel, kind: CleanKind) {
    val state by vm.clean.collectAsState()
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val c = NmTheme.colors
    val report = state.report
    var selected by remember(report, kind) { mutableStateOf(report?.defaultSelection(kind) ?: emptySet()) }
    var confirm by remember { mutableStateOf(false) }

    val all = report?.files(kind).orEmpty()
    val selectedSize = all.filter { it.path in selected }.sumOf { it.size }

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp)) {
            item {
                Box(Modifier.statusBarsPadding()) {
                    Header(kind.title, onBack = { vm.back() }, kicker = kind.blurb) {
                        Text(
                            if (selected.size == all.size && all.isNotEmpty()) "NONE" else "ALL",
                            style = NmTheme.type.label, color = c.accent,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                                selected = if (selected.size == all.size) emptySet() else all.map { it.path }.toSet()
                            }.padding(Spacing.m),
                        )
                    }
                }
            }
            if (report == null) item { Box(Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) { DotLoader() } }
            else if (all.isEmpty()) item { EmptyState("All clear", "Nothing left here.", FIcons.Clean) }

            val toggle: (FileItem) -> Unit = { f -> selected = if (f.path in selected) selected - f.path else selected + f.path }
            if (kind == CleanKind.DUPLICATES && report != null) {
                report.duplicates.forEachIndexed { gi, group ->
                    item(key = "g$gi") {
                        SectionLabel("${group.size} copies  ·  ${formatSize(group.first().size)} each")
                    }
                    items(group, key = { "g$gi:${it.path}" }) { f ->
                        FileRow(
                            f, vm.thumbnails, selected = f.path in selected, selecting = true,
                            favourite = f.path in settings.favourites,
                            onClick = { toggle(f) }, onLongClick = { context.openFile(f) }, onMore = {},
                            subtitle = f.path.removePrefix(vm.repo.root).substringBeforeLast('/').ifEmpty { "/" },
                        )
                    }
                }
            } else {
                items(all, key = { it.path }) { f ->
                    FileRow(
                        f, vm.thumbnails, selected = f.path in selected, selecting = true,
                        favourite = f.path in settings.favourites,
                        onClick = { toggle(f) }, onLongClick = { context.openFile(f) }, onMore = {},
                    )
                }
            }
        }

        if (selected.isNotEmpty()) {
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(Spacing.l)) {
                PillButton(
                    "Delete ${selected.size}  ·  ${formatSize(selectedSize)}",
                    onClick = { confirm = true }, primary = true, icon = FIcons.Delete,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = "Delete ${selected.size} ${if (selected.size == 1) "item" else "items"}?",
            body = "This frees ${formatSize(selectedSize)} and can't be undone.",
            confirm = "Delete",
            onConfirm = { vm.delete(selected); confirm = false; selected = emptySet() },
            onDismiss = { confirm = false },
        )
    }
}

