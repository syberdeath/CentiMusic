package com.centimusic.files.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.centimusic.files.data.FileItem
import com.centimusic.files.data.ViewMode
import com.centimusic.files.data.breadcrumbs
import com.centimusic.files.data.sortFiles
import com.centimusic.files.theme.FIcons
import com.centimusic.files.theme.NmTheme
import com.centimusic.files.theme.Spacing

/**
 * The shared list/grid of files used by folders, categories, favourites and search: selection,
 * per-file menu, rename/info/delete dialogs, and the copy/move "paste here" flow.
 */
@Composable
fun FileCollection(
    vm: AppViewModel,
    items: List<FileItem>?,
    title: String,
    onBack: (() -> Unit)?,
    emptyTitle: String,
    emptyBody: String,
    kicker: String? = null,
    currentFolder: String? = null,
    sortable: Boolean = true,
    headerExtra: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val selection by vm.selection.collectAsState()
    val clipboard by vm.clipboard.collectAsState()
    val c = NmTheme.colors

    val shown = remember(items, settings.sort, sortable) { if (sortable) items?.sortFiles(settings.sort) else items }
    val grid = settings.viewMode == ViewMode.GRID
    val selecting = selection.isNotEmpty()

    var sheetFor by remember { mutableStateOf<FileItem?>(null) }
    var renameFor by remember { mutableStateOf<FileItem?>(null) }
    var infoFor by remember { mutableStateOf<FileItem?>(null) }
    var deleteFor by remember { mutableStateOf<List<String>?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }

    fun openItem(item: FileItem) {
        if (selecting) { vm.toggleSelect(item.path); return }
        if (item.isDirectory) vm.open(Screen.Folder(item.path))
        else if (!context.openFile(item)) vm.say("No app can open this file")
    }

    fun requestDelete(paths: List<String>) {
        if (settings.confirmDelete) deleteFor = paths else vm.delete(paths)
    }

    val byPath = remember(shown) { shown.orEmpty().associateBy { it.path } }
    val selectedItems = selection.mapNotNull { byPath[it] }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.statusBarsPadding()) {
                if (selecting) {
                    SelectionHeader(
                        count = selection.size,
                        onClose = vm::clearSelection,
                        onSelectAll = { vm.selectAll(shown.orEmpty().map { it.path }) },
                    )
                } else {
                    Header(title = title, onBack = onBack, kicker = kicker) {
                        IconButtonNm(if (grid) FIcons.ListView else FIcons.Grid, if (grid) "List view" else "Grid view", {
                            vm.updateSettings { it.copy(viewMode = if (grid) ViewMode.LIST else ViewMode.GRID) }
                        })
                        if (sortable) IconButtonNm(FIcons.Sort, "Sort", { sortOpen = true })
                        IconButtonNm(FIcons.More, "More options", { moreOpen = true })
                    }
                }
            }

            val bottomPad = PaddingValues(bottom = 140.dp)
            when {
                shown == null -> Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) { DotLoader() }
                grid -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(108.dp),
                    contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = 140.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) { headerExtra() }
                    if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(emptyTitle, emptyBody) }
                    items(shown, key = { it.path }) { item ->
                        FileGridCell(
                            item, vm.thumbnails, selected = item.path in selection,
                            onClick = { openItem(item) }, onLongClick = { vm.toggleSelect(item.path) },
                        )
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPad) {
                    item { headerExtra() }
                    if (shown.isEmpty()) item { EmptyState(emptyTitle, emptyBody) }
                    items(shown, key = { it.path }) { item ->
                        FileRow(
                            item, vm.thumbnails,
                            selected = item.path in selection,
                            selecting = selecting,
                            favourite = item.path in settings.favourites,
                            onClick = { openItem(item) },
                            onLongClick = { vm.toggleSelect(item.path) },
                            onMore = { sheetFor = item },
                        )
                    }
                }
            }
        }

        // Bottom bars: selection actions, or paste-here while something is on the clipboard.
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(Spacing.l)) {
            if (selecting) {
                SelectionBar(
                    onCopy = { vm.copy(selection) ; vm.say("Open a folder, then tap Paste") },
                    onMove = { vm.cut(selection); vm.say("Open a folder, then tap Move here") },
                    onShare = { context.shareFiles(selectedItems) },
                    onDelete = { requestDelete(selection.toList()) },
                    onMore = {
                        val single = selectedItems.singleOrNull()
                        if (single != null) sheetFor = single else vm.toggleFavourite(selection)
                    },
                )
            } else if (clipboard != null) {
                val clip = clipboard!!
                PasteBar(
                    label = "${if (clip.move) "Move" else "Copy"} ${clip.paths.size} ${if (clip.paths.size == 1) "item" else "items"}",
                    action = if (currentFolder != null) (if (clip.move) "Move here" else "Paste") else null,
                    onAction = { currentFolder?.let(vm::paste) },
                    onCancel = vm::cancelClipboard,
                )
            }
        }
    }

    sheetFor?.let { item ->
        val fav = item.path in settings.favourites
        ActionSheet(
            title = item.name,
            subtitle = item.subtitle(),
            onDismiss = { sheetFor = null },
            actions = buildList {
                if (!item.isDirectory) add(SheetAction("Open", FIcons.File) { openItem(item) })
                add(SheetAction("Select", FIcons.Check) { vm.toggleSelect(item.path) })
                if (!item.isDirectory) add(SheetAction("Share", FIcons.Share) { context.shareFiles(listOf(item)) })
                add(SheetAction(if (fav) "Remove from favourites" else "Add to favourites", if (fav) FIcons.StarFilled else FIcons.Star) {
                    vm.toggleFavourite(listOf(item.path))
                })
                add(SheetAction("Copy to…", FIcons.Copy) { vm.copy(listOf(item.path)); vm.say("Open a folder, then tap Paste") })
                add(SheetAction("Move to…", FIcons.Move) { vm.cut(listOf(item.path)); vm.say("Open a folder, then tap Move here") })
                add(SheetAction("Rename", FIcons.Rename) { renameFor = item })
                add(SheetAction("File info", FIcons.Info) { infoFor = item })
                add(SheetAction("Delete", FIcons.Delete, destructive = true) { requestDelete(listOf(item.path)) })
            },
        )
    }

    if (moreOpen) {
        ActionSheet(
            title = title,
            subtitle = currentFolder,
            onDismiss = { moreOpen = false },
            actions = buildList {
                if (currentFolder != null) add(SheetAction("New folder", FIcons.FolderPlus) { newFolder = true })
                add(SheetAction("Select all", FIcons.Check) { vm.selectAll(shown.orEmpty().map { it.path }) })
                add(SheetAction(if (settings.showHidden) "Hide hidden files" else "Show hidden files", FIcons.Eye) {
                    vm.updateSettings { it.copy(showHidden = !it.showHidden) }
                })
            },
        )
    }

    if (sortOpen) SortSheet(settings.sort, onChange = { s -> vm.updateSettings { it.copy(sort = s) } }, onDismiss = { sortOpen = false })

    renameFor?.let { item ->
        NameDialog("Rename", item.name, "Rename", onConfirm = { vm.rename(item.path, it); renameFor = null }, onDismiss = { renameFor = null })
    }
    if (newFolder && currentFolder != null) {
        NameDialog("New folder", "", "Create", onConfirm = { vm.createFolder(currentFolder, it); newFolder = false }, onDismiss = { newFolder = false })
    }
    infoFor?.let { InfoDialog(it) { infoFor = null } }
    deleteFor?.let { paths ->
        val n = paths.size
        ConfirmDialog(
            title = if (n == 1) "Delete 1 item?" else "Delete $n items?",
            body = "This permanently deletes ${if (n == 1) "it" else "them"} from your device. Folders are deleted with everything inside.",
            confirm = "Delete",
            onConfirm = { vm.delete(paths); deleteFor = null },
            onDismiss = { deleteFor = null },
        )
    }
}

@Composable
private fun SelectionHeader(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit) {
    val c = NmTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.s, end = Spacing.s, top = Spacing.l, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButtonNm(FIcons.Close, "Clear selection", onClose)
        Spacer(Modifier.width(Spacing.s))
        Text("$count SELECTED", style = NmTheme.type.sectionTitle, color = c.textPrimary, modifier = Modifier.weight(1f))
        Text(
            "ALL", style = NmTheme.type.label, color = c.accent,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onSelectAll).padding(horizontal = Spacing.l, vertical = Spacing.m),
        )
    }
}

@Composable
private fun SelectionBar(onCopy: () -> Unit, onMove: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit, onMore: () -> Unit) {
    val c = NmTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.inverse).padding(horizontal = Spacing.s, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        BarAction(FIcons.Copy, "Copy", onCopy)
        BarAction(FIcons.Move, "Move", onMove)
        BarAction(FIcons.Share, "Share", onShare)
        BarAction(FIcons.Delete, "Delete", onDelete, accent = true)
        BarAction(FIcons.More, "More", onMore)
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit, accent: Boolean = false) {
    val c = NmTheme.colors
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (accent) c.accent else c.onInverse, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label.uppercase(), style = NmTheme.type.dotCaption, color = if (accent) c.accent else c.onInverse)
    }
}

@Composable
private fun PasteBar(label: String, action: String?, onAction: () -> Unit, onCancel: () -> Unit) {
    val c = NmTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.inverse).padding(start = Spacing.xl, end = Spacing.s, top = Spacing.s, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), style = NmTheme.type.label, color = c.onInverse)
            if (action == null) Text("Open a folder to drop them in", style = NmTheme.type.caption, color = c.onInverse.copy(alpha = 0.6f))
        }
        Text(
            "CANCEL", style = NmTheme.type.label, color = c.onInverse.copy(alpha = 0.7f),
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onCancel).padding(horizontal = Spacing.m, vertical = Spacing.m),
        )
        if (action != null) {
            Text(
                action.uppercase(), style = NmTheme.type.label, color = c.onAccent,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accent).clickable(onClick = onAction)
                    .padding(horizontal = Spacing.l, vertical = Spacing.m),
            )
        }
    }
}

// ---- Screens built on FileCollection ------------------------------------------------------------

@Composable
fun FolderScreen(vm: AppViewModel, path: String) {
    val settings by vm.settings.collectAsState()
    val revision by vm.revision.collectAsState()
    var items by remember(path) { mutableStateOf<List<FileItem>?>(null) }
    LaunchedEffect(path, revision, settings.showHidden) { items = vm.repo.list(path, settings.showHidden) }
    val isRoot = path == vm.repo.root
    val crumbs = remember(path) { breadcrumbs(path, vm.repo.root, "Internal storage") }
    FileCollection(
        vm = vm,
        items = items,
        title = if (isRoot) "Internal storage" else path.substringAfterLast('/'),
        onBack = { vm.back() },
        emptyTitle = "Empty folder",
        emptyBody = "Nothing in here yet.",
        currentFolder = path,
        headerExtra = { Breadcrumbs(crumbs, onJump = vm::jumpTo) },
    )
}

@Composable
private fun Breadcrumbs(crumbs: List<Pair<String, String>>, onJump: (String) -> Unit) {
    val c = NmTheme.colors
    val scroll = rememberScrollState()
    LaunchedEffect(crumbs) { scroll.scrollTo(scroll.maxValue) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = Spacing.gutter - Spacing.s, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { i, (label, path) ->
            val last = i == crumbs.lastIndex
            Text(
                label, style = NmTheme.type.bodySmall,
                color = if (last) c.textPrimary else c.textSecondary,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = !last) { onJump(path) }.padding(horizontal = Spacing.s, vertical = Spacing.s),
            )
            if (!last) Icon(FIcons.ChevronRight, null, tint = c.textTertiary, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun CategoryScreen(vm: AppViewModel, category: com.centimusic.files.data.Category) {
    val revision by vm.revision.collectAsState()
    var items by remember(category) { mutableStateOf<List<FileItem>?>(null) }
    LaunchedEffect(category, revision) { items = vm.repo.category(category) }
    FileCollection(
        vm = vm,
        items = items,
        title = category.label,
        kicker = items?.let { "${it.size} files  ·  ${com.centimusic.files.data.formatSize(it.sumOf { f -> f.size })}" },
        onBack = { vm.back() },
        emptyTitle = "Nothing here",
        emptyBody = "No ${category.label.lowercase()} found on this device.",
        currentFolder = if (category == com.centimusic.files.data.Category.DOWNLOADS) "${vm.repo.root}/Download" else null,
    )
}

@Composable
fun FavouritesScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsState()
    val revision by vm.revision.collectAsState()
    var items by remember { mutableStateOf<List<FileItem>?>(null) }
    LaunchedEffect(settings.favourites, revision) { items = vm.repo.files(settings.favourites) }
    FileCollection(
        vm = vm,
        items = items,
        title = "Favourites",
        onBack = { vm.back() },
        emptyTitle = "No favourites",
        emptyBody = "Star files and folders from their menu to keep them here.",
    )
}
