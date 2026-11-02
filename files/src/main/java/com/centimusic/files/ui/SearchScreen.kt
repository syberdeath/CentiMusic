package com.centimusic.files.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.centimusic.files.data.FileItem
import com.centimusic.files.theme.FIcons
import com.centimusic.files.theme.NmTheme
import com.centimusic.files.theme.Spacing
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(vm: AppViewModel) {
    val c = NmTheme.colors
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val settings by vm.settings.collectAsState()
    val revision by vm.revision.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FileItem>?>(emptyList()) }
    var sheetFor by remember { mutableStateOf<FileItem?>(null) }
    var infoFor by remember { mutableStateOf<FileItem?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query, revision) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        results = null
        delay(220) // debounce typing
        results = vm.repo.search(query)
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(
                    Modifier.statusBarsPadding().padding(start = Spacing.s, end = Spacing.gutter, top = Spacing.l, bottom = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButtonNm(FIcons.Back, "Back", { vm.back() })
                    Spacer(Modifier.width(Spacing.xs))
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(50)).background(c.surface)
                            .padding(horizontal = Spacing.l, vertical = Spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(FIcons.Search, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.m))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("Search files", style = NmTheme.type.body, color = c.textTertiary)
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = NmTheme.type.body.copy(color = c.textPrimary),
                                cursorBrush = SolidColor(c.accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { vm.rememberSearch(query); focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            )
                        }
                        if (query.isNotEmpty()) {
                            Icon(
                                FIcons.Close, "Clear", tint = c.textSecondary,
                                modifier = Modifier.size(18.dp).clickable { query = "" },
                            )
                        }
                    }
                }
            }

            if (query.isBlank()) {
                if (settings.recentSearches.isNotEmpty()) {
                    item {
                        SectionLabel("Recent searches") {
                            Text(
                                "CLEAR", style = NmTheme.type.dotCaption, color = c.accent,
                                modifier = Modifier.clickable { vm.clearSearches() },
                            )
                        }
                    }
                    items(settings.recentSearches) { s ->
                        Row(
                            Modifier.fillMaxWidth().clickable { query = s }.padding(horizontal = Spacing.gutter).height(48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(FIcons.Clock, null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.l))
                            Text(s, style = NmTheme.type.title, color = c.textPrimary)
                        }
                    }
                } else {
                    item { EmptyState("Find anything", "Search by name across your whole phone.", FIcons.Search) }
                }
            } else {
                val r = results
                when {
                    r == null -> item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { DotLoader() } }
                    r.isEmpty() -> item { EmptyState("No results", "Nothing matches \"$query\".", FIcons.Search) }
                    else -> {
                        item { SectionLabel("${r.size} results") }
                        items(r, key = { it.path }) { f ->
                            FileRow(
                                f, vm.thumbnails, selected = false, selecting = false,
                                favourite = f.path in settings.favourites,
                                onClick = {
                                    vm.rememberSearch(query)
                                    if (f.isDirectory) vm.open(Screen.Folder(f.path))
                                    else if (!context.openFile(f)) vm.say("No app can open this file")
                                },
                                onLongClick = { sheetFor = f },
                                onMore = { sheetFor = f },
                                subtitle = f.path.removePrefix(vm.repo.root).substringBeforeLast('/').ifEmpty { "Internal storage" },
                            )
                        }
                    }
                }
            }
        }
    }

    sheetFor?.let { f ->
        ActionSheet(
            title = f.name, subtitle = f.subtitle(), onDismiss = { sheetFor = null },
            actions = buildList {
                if (!f.isDirectory) add(SheetAction("Open", FIcons.File) { if (!context.openFile(f)) vm.say("No app can open this file") })
                add(SheetAction("Show in folder", FIcons.Folder) { vm.open(Screen.Folder(f.path.substringBeforeLast('/'))) })
                if (!f.isDirectory) add(SheetAction("Share", FIcons.Share) { context.shareFiles(listOf(f)) })
                add(SheetAction("File info", FIcons.Info) { infoFor = f })
            },
        )
    }
    infoFor?.let { InfoDialog(it) { infoFor = null } }
}
