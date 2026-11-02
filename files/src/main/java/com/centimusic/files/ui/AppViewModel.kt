package com.centimusic.files.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.centimusic.files.data.CleanReport
import com.centimusic.files.data.Category
import com.centimusic.files.data.FileItem
import com.centimusic.files.data.FileRepository
import com.centimusic.files.data.Prefs
import com.centimusic.files.data.Settings
import com.centimusic.files.data.StorageStats
import com.centimusic.files.data.Thumbnails
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Tab { BROWSE, CLEAN }

enum class CleanKind(val title: String, val blurb: String) {
    JUNK("Junk files", "Thumbnail caches, empty folders and temporary files"),
    DUPLICATES("Duplicate files", "Identical copies. The oldest copy of each is kept unselected"),
    LARGE("Large files", "Files over 100 MB"),
    OLD_DOWNLOADS("Old downloads", "Downloaded more than 90 days ago"),
    OLD_SCREENSHOTS("Old screenshots", "Taken more than 30 days ago"),
}

sealed interface Screen {
    data class Root(val tab: Tab) : Screen
    data class Folder(val path: String) : Screen
    data class CategoryList(val category: Category) : Screen
    data object Favourites : Screen
    data object Search : Screen
    data object Settings : Screen
    data class CleanDetail(val kind: CleanKind) : Screen
}

data class Clipboard(val paths: List<String>, val move: Boolean)

data class Operation(val label: String, val fraction: Float?)

data class BrowseState(
    val storage: StorageStats = StorageStats(0, 0),
    val categories: Map<Category, Pair<Int, Long>> = emptyMap(),
    val recent: List<FileItem> = emptyList(),
    val loading: Boolean = true,
)

data class CleanState(val report: CleanReport? = null, val scanning: Boolean = false)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = FileRepository(app)
    val prefs = Prefs(app)
    val thumbnails = Thumbnails(app)

    val settings: StateFlow<Settings> = prefs.settings

    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Root(Tab.BROWSE)))
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _clipboard = MutableStateFlow<Clipboard?>(null)
    val clipboard: StateFlow<Clipboard?> = _clipboard.asStateFlow()

    private val _operation = MutableStateFlow<Operation?>(null)
    val operation: StateFlow<Operation?> = _operation.asStateFlow()

    /** Bumped after every change on disk; screens reload when it changes. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _browse = MutableStateFlow(BrowseState())
    val browse: StateFlow<BrowseState> = _browse.asStateFlow()

    private val _clean = MutableStateFlow(CleanState())
    val clean: StateFlow<CleanState> = _clean.asStateFlow()

    // ---- Navigation ----------------------------------------------------------------------------

    fun open(screen: Screen) {
        clearSelection()
        _stack.value = _stack.value + screen
    }

    fun selectTab(tab: Tab) {
        clearSelection()
        _stack.value = listOf(Screen.Root(tab))
        if (tab == Tab.CLEAN && _clean.value.report == null) scan()
    }

    /** Returns false when there's nothing left to go back to. */
    fun back(): Boolean {
        if (_selection.value.isNotEmpty()) { clearSelection(); return true }
        val s = _stack.value
        if (s.size <= 1) {
            if ((s.firstOrNull() as? Screen.Root)?.tab == Tab.CLEAN) { selectTab(Tab.BROWSE); return true }
            return false
        }
        _stack.value = s.dropLast(1)
        return true
    }

    /** Replaces the folder chain so breadcrumb taps jump straight to an ancestor. */
    fun jumpTo(path: String) {
        clearSelection()
        val s = _stack.value
        val idx = s.indexOfLast { it is Screen.Folder && it.path == path }
        _stack.value = if (idx >= 0) s.take(idx + 1) else s.dropLast(1) + Screen.Folder(path)
    }

    // ---- Data ----------------------------------------------------------------------------------

    fun refreshBrowse() {
        viewModelScope.launch {
            _browse.value = _browse.value.copy(storage = repo.storageStats())
            val cats = repo.categorySummary()
            val recent = repo.recent()
            _browse.value = BrowseState(repo.storageStats(), cats, recent, loading = false)
        }
    }

    fun scan() {
        if (_clean.value.scanning) return
        viewModelScope.launch {
            _clean.value = _clean.value.copy(scanning = true)
            val report = repo.analyse()
            _clean.value = CleanState(report, scanning = false)
        }
    }

    fun reloadAll() {
        repo.invalidate()
        _revision.value++
        refreshBrowse()
        if (_clean.value.report != null) scan()
    }

    // ---- Selection -----------------------------------------------------------------------------

    fun toggleSelect(path: String) {
        val s = _selection.value
        _selection.value = if (path in s) s - path else s + path
    }

    fun selectAll(paths: Collection<String>) { _selection.value = _selection.value + paths }
    fun setSelection(paths: Set<String>) { _selection.value = paths }
    fun clearSelection() { _selection.value = emptySet() }

    // ---- Settings ------------------------------------------------------------------------------

    fun updateSettings(transform: (Settings) -> Settings) = prefs.update(transform)

    fun toggleFavourite(paths: Collection<String>) {
        val favs = settings.value.favourites
        val allFav = paths.all { it in favs }
        prefs.update { it.copy(favourites = if (allFav) favs - paths.toSet() else favs + paths) }
        say(if (allFav) "Removed from favourites" else "Added to favourites")
    }

    fun rememberSearch(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        prefs.update { it.copy(recentSearches = (listOf(t) + it.recentSearches.filter { s -> s != t }).take(8)) }
    }

    fun clearSearches() = prefs.update { it.copy(recentSearches = emptyList()) }

    // ---- Operations ----------------------------------------------------------------------------

    fun say(message: String) { _messages.tryEmit(message) }

    fun cut(paths: Collection<String>) { _clipboard.value = Clipboard(paths.toList(), move = true); clearSelection() }
    fun copy(paths: Collection<String>) { _clipboard.value = Clipboard(paths.toList(), move = false); clearSelection() }
    fun cancelClipboard() { _clipboard.value = null }

    fun paste(destDir: String) {
        val clip = _clipboard.value ?: return
        _clipboard.value = null
        val verb = if (clip.move) "Moving" else "Copying"
        runOp("$verb ${count(clip.paths.size)}") { report ->
            repo.transfer(clip.paths, destDir, clip.move) { done, total ->
                report(if (total > 0) done.toFloat() / total else null)
            }.fold(
                onSuccess = { n -> "${if (clip.move) "Moved" else "Copied"} ${count(n)}" },
                onFailure = { it.message ?: "Something went wrong" },
            )
        }
    }

    fun delete(paths: Collection<String>) {
        val list = paths.toList()
        clearSelection()
        runOp("Deleting ${count(list.size)}") {
            repo.delete(list).fold(
                onSuccess = { n ->
                    prefs.update { s -> s.copy(favourites = s.favourites - list.toSet()) }
                    "Deleted ${count(n)}"
                },
                onFailure = { it.message ?: "Couldn't delete" },
            )
        }
    }

    fun rename(path: String, name: String) {
        clearSelection()
        runOp("Renaming") {
            repo.rename(path, name).fold(
                onSuccess = { newPath ->
                    val favs = settings.value.favourites
                    if (path in favs) prefs.update { it.copy(favourites = favs - path + newPath) }
                    "Renamed"
                },
                onFailure = { it.message ?: "Couldn't rename" },
            )
        }
    }

    fun createFolder(parent: String, name: String) {
        runOp("Creating folder") {
            repo.createFolder(parent, name).fold(onSuccess = { "Folder created" }, onFailure = { it.message ?: "Couldn't create" })
        }
    }

    private fun runOp(label: String, block: suspend (report: (Float?) -> Unit) -> String) {
        viewModelScope.launch {
            _operation.value = Operation(label, null)
            val msg = block { f -> _operation.value = Operation(label, f) }
            _operation.value = null
            reloadAll()
            say(msg)
        }
    }

    private fun count(n: Int) = if (n == 1) "1 item" else "$n items"
}
