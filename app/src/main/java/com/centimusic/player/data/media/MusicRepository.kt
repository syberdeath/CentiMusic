package com.centimusic.player.data.media

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
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
            // Keep write access when the provider granted it, so the file can be deleted from the app later.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.recoverCatching {
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

    /**
     * Deletes [song]'s file from the device. MediaStore files on Android 11+ (and some on 10) need
     * the user's confirmation in a system dialog, returned as [DeleteResult.NeedsConfirmation].
     */
    suspend fun delete(song: Song): DeleteResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        if (song.source == SongSource.IMPORTED) {
            val deleted = runCatching { DocumentsContract.deleteDocument(resolver, song.uri) }.getOrDefault(false)
            removeImported(song)
            return@withContext if (deleted) DeleteResult.Deleted else DeleteResult.RemovedFromLibraryOnly
        }
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                DeleteResult.NeedsConfirmation(MediaStore.createDeleteRequest(resolver, listOf(song.uri)).intentSender)
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> try {
                deleteRow(song)
            } catch (e: RecoverableSecurityException) {
                DeleteResult.NeedsConfirmation(e.userAction.actionIntent.intentSender)
            }
            !hasLegacyWritePermission() -> DeleteResult.NeedsWritePermission
            else -> runCatching { deleteRow(song) }.getOrElse { DeleteResult.Failed(it.message ?: "Couldn't delete the file") }
        }
    }

    /** Android 11+: one system dialog to delete several MediaStore files at once. */
    fun batchDeleteRequest(songs: List<Song>): IntentSender {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        return MediaStore.createDeleteRequest(context.contentResolver, songs.map { it.uri }).intentSender
    }

    /** Call after the user approved the system delete dialog. */
    suspend fun completeConfirmedDelete(song: Song): DeleteResult = withContext(Dispatchers.IO) {
        // Android 11+ deletes in the dialog itself; Android 10 granted access, so delete now.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) DeleteResult.Deleted
        else runCatching { deleteRow(song) }.getOrElse { DeleteResult.Failed(it.message ?: "Couldn't delete the file") }
    }

    private fun deleteRow(song: Song): DeleteResult =
        if (context.contentResolver.delete(song.uri, null, null) > 0) DeleteResult.Deleted
        else DeleteResult.Failed("The file was already gone")

    private fun hasLegacyWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Builds a Song for an arbitrary content URI (e.g. "Open with" from a file manager). */
    suspend fun songForExternalUri(uri: Uri): Song? = withContext(Dispatchers.IO) {
        library.value.byId[uri.toString()] ?: reader.read(uri)?.toSong()
    }

    companion object {
        private const val TAG = "MusicRepository"
    }
}

sealed interface DeleteResult {
    data object Deleted : DeleteResult
    /** An imported file whose provider doesn't allow deleting: it was removed from the library only. */
    data object RemovedFromLibraryOnly : DeleteResult
    data class NeedsConfirmation(val sender: IntentSender) : DeleteResult
    /** Android 8–9: WRITE_EXTERNAL_STORAGE must be granted first. */
    data object NeedsWritePermission : DeleteResult
    data class Failed(val reason: String) : DeleteResult
}
