package com.centimusic.player.ui.library

import androidx.compose.foundation.background
import com.centimusic.player.ui.components.MarqueeText
import androidx.compose.foundation.layout.fillMaxHeight
import kotlinx.coroutines.launch
import com.centimusic.player.ui.components.rememberHaptics
import com.centimusic.player.ui.components.PillButton
import com.centimusic.player.ui.components.NmDivider
import com.centimusic.player.ui.components.LocalSongDeleter
import com.centimusic.player.data.media.Song
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centimusic.player.data.media.SongSort
import com.centimusic.player.navigation.LocalAppContainer
import com.centimusic.player.navigation.LocalChromePadding
import com.centimusic.player.navigation.LocalNavigator
import com.centimusic.player.navigation.LocalSongMenu
import com.centimusic.player.ui.appViewModel
import com.centimusic.player.ui.components.AccentPlayButton
import com.centimusic.player.ui.components.Artwork
import com.centimusic.player.ui.components.ChoiceDialog
import com.centimusic.player.ui.components.EmptyState
import com.centimusic.player.ui.components.LoadingState
import com.centimusic.player.ui.components.NmIconButton
import com.centimusic.player.ui.components.SongRow
import com.centimusic.player.ui.components.TopBar
import com.centimusic.player.ui.components.AlbumHeader
import com.centimusic.player.ui.components.MaybeRescanRefresh
import com.centimusic.player.ui.components.AlbumCard
import com.centimusic.player.ui.components.CollapsingTitleBar
import com.centimusic.player.ui.components.SectionHeader
import com.centimusic.player.ui.components.rememberHeaderCollapsed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.centimusic.player.ui.components.bottomContentPadding
import com.centimusic.player.ui.theme.NmIcons
import com.centimusic.player.ui.theme.NmTheme
import com.centimusic.player.ui.theme.Spacing

@Composable
fun SongListScreen(source: SongListSource) {
    val vm = appViewModel(key = source.key) { SongListViewModel(it, source) }
    val state by vm.state.collectAsStateWithLifecycle()
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val menu = LocalSongMenu.current
    val deleter = LocalSongDeleter.current
    val haptics = rememberHaptics()
    val playback by container.player.state.collectAsStateWithLifecycle()
    var showSort by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Multi-select: long-press a song to start, tap to toggle.
    var selection by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val selecting = selection.isNotEmpty()
    val selectedSongs = remember(selection, state.songs) { state.songs.filter { it.id in selection } }
    fun toggle(song: Song) {
        haptics.confirm()
        selection = if (song.id in selection) selection - song.id else selection + song.id
    }
    BackHandler(enabled = selecting) { selection = emptyList() }

    // A–Z index: only meaningful when sorted alphabetically and the list is long.
    val letterField: ((Song) -> String)? = when {
        source != SongListSource.AllSongs || state.songs.size < 25 -> null
        state.sort == SongSort.TITLE -> { s -> s.title }
        state.sort == SongSort.ARTIST -> { s -> s.artist }
        state.sort == SongSort.ALBUM -> { s -> s.album }
        else -> null
    }
    val letterIndex = remember(state.songs, state.sort, source) {
        letterField?.let { field ->
            val map = LinkedHashMap<Char, Int>()
            state.songs.forEachIndexed { i, song -> map.getOrPut(indexLetter(field(song))) { i } }
            map
        }
    }
    val headerItems = 2 + if (state.duplicates != null) 1 else 0
    val albumSource = source as? SongListSource.Album
    val library by container.music.library.collectAsStateWithLifecycle()
    // Read straight from the library so the cover is there on the first frame of the shared transition.
    val album = remember(library, albumSource) { albumSource?.let { library.album(it.albumKey) } }
    val moreByArtist = remember(library, album) {
        album?.let { a -> library.albums.filter { it.artist == a.artist && it.key != a.key }.sortedByDescending { it.year } }.orEmpty()
    }
    val collapsed by rememberHeaderCollapsed(listState, headerIndex = if (albumSource != null) 0 else 1)
    val sortAction: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        if (state.sortable) NmIconButton(NmIcons.Sort, "Sort songs", { showSort = true })
    }

    Box(Modifier.fillMaxSize()) {
        MaybeRescanRefresh(enabled = source == SongListSource.AllSongs) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = bottomContentPadding(LocalChromePadding.current)) {
            if (albumSource != null) {
                item(key = "header") {
                    AlbumHeader(
                        album = album,
                        albumKey = albumSource.albumKey,
                        onBack = navigator::back,
                        onArtist = navigator::artist,
                        onPlay = { container.player.play(state.songs, shuffle = false) },
                        onShuffle = { container.player.shuffle(state.songs) },
                        onAddToPlaylist = { menu.pickPlaylistFor(state.songs) },
                    )
                }
            } else {
            item(key = "top") {
                TopBar(onBack = navigator::back, actions = sortAction)
            }
            item(key = "header") {
                CollectionHeader(
                    state = state,
                    onPlay = { container.player.play(state.songs, shuffle = false) },
                    onShuffle = { container.player.shuffle(state.songs) },
                    onAddToPlaylist = { menu.pickPlaylistFor(state.songs) },
                )
            }
            }
            state.duplicates?.let { dup ->
                item(key = "duplicates") {
                    DuplicateNote(dup, onToggle = { vm.setHideDuplicates(!dup.enabled) })
                }
            }
            when {
                state.loading -> item(key = "loading") { LoadingState("Loading songs") }
                state.songs.isEmpty() -> item(key = "empty") { EmptyState(state.emptyTitle, state.emptyMessage) }
                else -> itemsIndexed(state.songs, key = { _, song -> song.id }) { index, song ->
                    val liked = song.id in state.likedIds
                    SongRow(
                        song = song,
                        onClick = { if (selecting) toggle(song) else container.player.play(state.songs, index) },
                        isCurrent = playback.current?.id == song.id,
                        index = if (state.showTrackNumbers) song.trackNumber.takeIf { it > 0 } ?: (index + 1) else null,
                        showArtwork = !state.showTrackNumbers,
                        onMore = { menu.open(song) },
                        onLongClick = { toggle(song) },
                        selected = if (selecting) song.id in selection else null,
                        trailing = if (source == SongListSource.Liked) {
                            {
                                NmIconButton(
                                    if (liked) NmIcons.HeartFilled else NmIcons.Heart,
                                    "Remove ${song.title} from Liked Songs",
                                    { vm.unlike(song) },
                                    tint = if (liked) NmTheme.colors.accent else NmTheme.colors.textSecondary,
                                    iconSize = 20.dp,
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.padding(end = if (letterIndex != null && !selecting) 26.dp else 0.dp),
                    )
                }
            }
            if (moreByArtist.isNotEmpty() && album != null) {
                item(key = "more") {
                    Spacer(Modifier.height(Spacing.xxl))
                    SectionHeader("More by ${album.artist}", action = "Artist", onAction = { navigator.artist(album.artist) })
                    Spacer(Modifier.height(Spacing.s))
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
                    ) {
                        items(moreByArtist, key = { it.key }) { other ->
                            AlbumCard(other, onClick = { navigator.album(other.key) }, modifier = Modifier.width(140.dp), onPlay = { container.player.play(other.songs) })
                        }
                    }
                }
            }
        }
        }

        CollapsingTitleBar(
            visible = collapsed && !selecting,
            title = album?.title ?: state.title,
            onBack = navigator::back,
            onPlay = if (state.songs.isNotEmpty()) ({ container.player.play(state.songs, shuffle = false) }) else null,
            actions = sortAction,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (letterIndex != null && !selecting && !state.loading) {
            AlphabetScroller(
                letters = letterIndex.keys.toList(),
                onLetter = { letter ->
                    letterIndex[letter]?.let { i -> scope.launch { listState.scrollToItem(headerItems + i) } }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .padding(top = 56.dp, bottom = LocalChromePadding.current + Spacing.s),
            )
        }

        AnimatedVisibility(
            visible = selecting,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            SelectionBar(
                count = selection.size,
                allSelected = selection.size == state.songs.size,
                onClose = { selection = emptyList() },
                onSelectAll = { selection = if (selection.size == state.songs.size) emptyList() else state.songs.map { it.id } },
                onPlay = {
                    container.player.play(selectedSongs, shuffle = false)
                    selection = emptyList()
                },
                onQueue = {
                    container.player.addToQueue(selectedSongs)
                    selection = emptyList()
                },
                onPlaylist = {
                    menu.pickPlaylistFor(selectedSongs)
                    selection = emptyList()
                },
                onDelete = {
                    deleter.request(selectedSongs)
                    selection = emptyList()
                },
            )
        }
    }

    if (showSort) {
        ChoiceDialog(
            title = "Sort by",
            options = SongSort.entries,
            selected = state.sort,
            label = { it.label },
            onSelect = {
                vm.setSort(it)
                showSort = false
            },
            onDismiss = { showSort = false },
        )
    }
}

/** First letter for the A–Z index; digits and symbols go under '#'. */
private fun indexLetter(text: String): Char {
    val c = java.text.Normalizer.normalize(text.trim(), java.text.Normalizer.Form.NFD).firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c else '#'
}

/** Fixed bar shown while songs are selected. */
@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onPlaylist: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = NmTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            NmIconButton(NmIcons.Close, "Clear selection", onClose)
            Text("$count selected", style = NmTheme.type.sectionTitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(
                if (allSelected) "None" else "All",
                style = NmTheme.type.dotCaption,
                color = colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onSelectAll)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            PillButton("Play", onPlay, icon = NmIcons.Play, filled = true)
            PillButton("Queue", onQueue, icon = NmIcons.Queue)
            NmIconButton(NmIcons.PlaylistAdd, "Add selection to playlist", onPlaylist)
            NmIconButton(NmIcons.Trash, "Delete selection from device", onDelete, tint = colors.accent)
        }
        NmDivider()
    }
}

private val ALPHABET = listOf('#') + ('A'..'Z').toList()

/**
 * Full-height # + A–Z strip on the right edge; drag or tap to jump, with a big dot-matrix letter
 * bubble. Letters without songs are dimmed and jump to the next letter that has some.
 */
@Composable
private fun AlphabetScroller(letters: List<Char>, onLetter: (Char) -> Unit, modifier: Modifier = Modifier) {
    val colors = NmTheme.colors
    val haptics = rememberHaptics()
    val available = remember(letters) { letters.toSet() }
    var active by remember { mutableStateOf<Char?>(null) }
    var heightPx by remember { mutableIntStateOf(1) }
    fun pick(y: Float) {
        val i = ((y / heightPx) * ALPHABET.size).toInt().coerceIn(0, ALPHABET.lastIndex)
        val letter = ALPHABET[i]
        if (letter != active) {
            active = letter
            haptics.gesture()
            // Jump to this letter, or the next one that has songs (or the last one if none follow).
            val target = ALPHABET.drop(i).firstOrNull { it in available } ?: letters.lastOrNull()
            target?.let(onLetter)
        }
    }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(30.dp)
                .align(Alignment.CenterEnd)
                .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            pick(offset.y)
                            tryAwaitRelease()
                            active = null
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { pick(it.y) },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                    ) { change, _ ->
                        change.consume()
                        pick(change.position.y)
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            ALPHABET.forEach { letter ->
                Text(
                    letter.toString(),
                    style = NmTheme.type.dotCaption.copy(fontSize = 13.sp, lineHeight = 14.sp),
                    color = when {
                        letter == active -> colors.accent
                        letter in available -> colors.textPrimary
                        else -> colors.textTertiary.copy(alpha = 0.5f)
                    },
                )
            }
        }
        active?.let { letter ->
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 48.dp)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(letter.toString(), style = NmTheme.type.screenTitle.copy(fontSize = 40.sp, lineHeight = 44.sp), color = colors.onAccent)
            }
        }
    }
}

@Composable
private fun CollectionHeader(
    state: SongListUiState,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    val colors = NmTheme.colors
    Column(Modifier.padding(horizontal = Spacing.gutter)) {
        when (state.headerStyle) {
            HeaderStyle.TITLE -> {
                Text(state.title, style = NmTheme.type.screenTitle, color = colors.textPrimary)
                Spacer(Modifier.height(Spacing.xs))
                Text(state.subtitle, style = NmTheme.type.bodySmall, color = colors.textSecondary)
            }
            HeaderStyle.ARTWORK, HeaderStyle.ARTIST -> Row(verticalAlignment = Alignment.CenterVertically) {
                val shape = if (state.headerStyle == HeaderStyle.ARTIST) CircleShape else null
                Box {
                    if (shape != null) {
                        Artwork(state.cover, Modifier.size(120.dp).clip(shape), corner = 60.dp, sizeHint = 256.dp)
                    } else {
                        Artwork(state.cover, Modifier.size(120.dp), sizeHint = 256.dp)
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                    )
                }
                Spacer(Modifier.width(Spacing.l))
                Column(Modifier.weight(1f)) {
                    Text(state.kind.uppercase(), style = NmTheme.type.dotCaption, color = colors.textTertiary)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        state.title,
                        style = NmTheme.type.sectionTitle.copy(fontSize = NmTheme.type.sectionTitle.fontSize * 1.15f),
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(state.subtitle, style = NmTheme.type.bodySmall, color = colors.textSecondary, maxLines = 3)
                }
            }
        }
        Spacer(Modifier.height(Spacing.l))
        if (state.songs.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                AccentPlayButton(onPlay)
                Spacer(Modifier.width(Spacing.xs))
                NmIconButton(NmIcons.Shuffle, "Shuffle", onShuffle)
                NmIconButton(NmIcons.PlaylistAdd, "Add all to playlist", onAddToPlaylist)
            }
            Spacer(Modifier.height(Spacing.m))
        }
    }
}

/** "12 duplicates hidden · Show all" — or an offer to hide them when the filter is off. */
@Composable
private fun DuplicateNote(filter: DuplicateFilter, onToggle: () -> Unit) {
    if (filter.enabled && filter.hidden == 0) return
    val colors = NmTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter)
            .padding(bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (filter.enabled) colors.accent else colors.textTertiary),
        )
        Spacer(Modifier.width(Spacing.s))
        Text(
            if (filter.enabled) "${filter.hidden} duplicate ${if (filter.hidden == 1) "song" else "songs"} hidden" else "Showing duplicates",
            style = NmTheme.type.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (filter.enabled) "Show all" else "Hide duplicates",
            style = NmTheme.type.dotCaption,
            color = colors.accent,
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = Spacing.xs, vertical = Spacing.s),
        )
    }
}
