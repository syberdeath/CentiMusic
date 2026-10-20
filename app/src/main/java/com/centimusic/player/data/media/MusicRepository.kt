package com.centimusic.player.data.media

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.centimusic.player.data.database.ImportedTrackDao
import com.centimusic.player.data.preferences.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface ScanState {
    /** Nothing scanned yet in this process. */
    data object Idle : ScanState
    data object Scanning : ScanState
    data object Done : ScanState
    data object PermissionDenied : ScanState
    data class Failed(val message: String) : ScanState
}

/**
 * Single source of truth for the local library: MediaStore tracks plus files imported through the
 * system picker. Rescans automatically when MediaStore reports changes.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MusicRepository(
    private val context: Context,
    private val importedDao: ImportedTrackDao,
    private val preferences: PreferencesRepository,
    private val scope: CoroutineScope,
) {
    private val scanner = MediaStoreScanner(context.contentResolver)
    private val reader = ImportedTrackReader(context)
    private val scanMutex = Mutex()
    private var pendingRescan: Job? = null

    private val mediaStoreSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _permissionGranted = MutableStateFlow(AudioPermission.isGranted(context))
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    val library: StateFlow<Library> = combine(
        mediaStoreSongs,
        importedDao.observeAll().map { rows -> rows.map { it.toSong() } },
        preferences.preferences.map { it.hideDuplicates }.distinctUntilChanged(),
    ) { stored, imported, hideDuplicates -> Library.from(stored + imported, hideDuplicates) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, Library())

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = scheduleRescan()
    }

    init {
        context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        // Changing the short-track filter changes what the scanner returns.
        preferences.preferences.map { it.ignoreShortTracks }.distinctUntilChanged().drop(1)
            .onEach { rescan() }
            .launchIn(scope)
    }

    /** Called when returning to the app; the user may have granted or revoked access in Settings. */
    fun refreshPermission() {
        val granted = AudioPermission.isGranted(context)
        val changed = granted != _permissionGranted.value
        _permissionGranted.value = granted
        if (changed || _scanState.value == ScanState.Idle) scope.launch { rescan() }
    }

    suspend fun rescan() = scanMutex.withLock {
        if (!AudioPermission.isGranted(context)) {
            _permissionGranted.value = false
            mediaStoreSongs.value = emptyList()
            _scanState.value = ScanState.PermissionDenied
            return@withLock
        }
        _permissionGranted.value = true
        _scanState.value = ScanState.Scanning
        try {
            val minDuration = if (preferences.current().ignoreShortTracks) 30_000L else 1L
            val songs = withContext(Dispatchers.IO) { scanner.scan(minDuration) }
            mediaStoreSongs.value = songs
            ArtworkLoader.clear()
            _scanState.value = ScanState.Done
        } catch (e: SecurityException) {
            _scanState.value = ScanState.PermissionDenied
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed", e)
            _scanState.value = ScanState.Failed(e.message ?: "Couldn't read your music library")
        }
    }

    private fun scheduleRescan() {
        pendingRescan?.cancel()
        pendingRescan = scope.launch {
            delay(1_500) // MediaStore fires bursts of notifications while files are being copied.
            rescan()
        }
    }

    /** Waits for the first completed scan so playback restoration can resolve media ids. */
    suspend fun awaitLibrary(): Library {
        if (_scanState.value == ScanState.Idle) rescan()
        scanState.first { it != ScanState.Scanning && it != ScanState.Idle }
        // Let the combine() above publish the result of the scan.
        return library.filter { !it.isEmpty || mediaStoreSongs.value.isEmpty() }.first()
    }

    /** Imports files picked with the system file picker. Returns how many were added. */
    suspend fun importDocuments(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val known = library.value.byId.keys
        val entities = uris.distinct().mapNotNull { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (uri.toString() in known) null else reader.read(uri)
        }
        if (entities.isNotEmpty()) importedDao.upsert(entities)
        entities.size
    }

    /** Drops imported files whose read permission was lost (deleted or revoked). */
    suspend fun pruneImported() = withContext(Dispatchers.IO) {
        val held = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
        val lost = importedDao.observeAll().first().map { it.uri }.filterNot { it in held }
        if (lost.isNotEmpty()) importedDao.delete(lost)
    }

    suspend fun removeImported(song: Song) = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.releasePersistableUriPermission(song.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        importedDao.delete(listOf(song.id))
    }

    /** Builds a Song for an arbitrary content URI (e.g. "Open with" from a file manager). */
    suspend fun songForExternalUri(uri: Uri): Song? = withContext(Dispatchers.IO) {
        library.value.byId[uri.toString()] ?: reader.read(uri)?.toSong()
    }

    companion object {
        private const val TAG = "MusicRepository"
    }
}
