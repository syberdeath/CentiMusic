package com.centimusic.player.data.media

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.centimusic.player.data.database.SongEditDao
import com.centimusic.player.data.database.SongEditEntity
import java.io.File
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
    private val editDao: SongEditDao,
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

    private val importedSongs = importedDao.observeAll().map { rows -> rows.map { it.toSong() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Every folder that contains music, hidden or not, with its song count (for "Hidden folders"). */
    val allFolders: StateFlow<List<Pair<String, Int>>> = combine(mediaStoreSongs, importedSongs) { stored, imported ->
        (stored + imported).groupingBy { it.folder }.eachCount().toList().sortedBy { it.first.lowercase() }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, emptyList())

    val library: StateFlow<Library> = combine(
        mediaStoreSongs,
        importedSongs,
        preferences.preferences.map { it.hideDuplicates to it.hiddenFolders }.distinctUntilChanged(),
        editDao.observeAll(),
    ) { stored, imported, (hideDuplicates, hiddenFolders), edits ->
        val byId = edits.associateBy { it.mediaId }
        val visible = (stored + imported)
            .filterNot { it.folder in hiddenFolders }
            .map { song -> byId[song.id]?.let { song.withEdit(it) } ?: song }
        Library.from(visible, hideDuplicates)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, Library())

    /** The song as tagged in the file, before any edit made in CentiMusic. */
    fun original(id: String): Song? =
        mediaStoreSongs.value.firstOrNull { it.id == id } ?: importedSongs.value.firstOrNull { it.id == id }

    suspend fun edit(id: String): SongEditEntity? = editDao.get(id)

    /**
     * Saves info edits for [id]. Blank fields fall back to the file's tags. [newCover] is copied
     * (downscaled) into app storage; [removeCover] drops a previously chosen cover.
     */
    suspend fun saveEdit(id: String, title: String, artist: String, album: String, newCover: Uri?, removeCover: Boolean) =
        withContext(Dispatchers.IO) {
            val previous = editDao.get(id)
            var artworkPath = previous?.artworkPath
            if (removeCover || newCover != null) {
                artworkPath?.let { File(it).delete() }
                artworkPath = null
            }
            if (newCover != null) artworkPath = copyCover(id, newCover)
            val original = original(id)
            fun keep(value: String, originalValue: String?) = value.trim().takeIf { it.isNotEmpty() && it != originalValue }
            val entity = SongEditEntity(
                mediaId = id,
                title = keep(title, original?.title),
                artist = keep(artist, original?.artist),
                album = keep(album, original?.album),
                artworkPath = artworkPath,
                updatedAt = System.currentTimeMillis(),
            )
            if (entity.title == null && entity.artist == null && entity.album == null && entity.artworkPath == null) {
                editDao.delete(id)
            } else {
                editDao.upsert(entity)
            }
            ArtworkLoader.clear()
        }

    suspend fun resetEdit(id: String) = withContext(Dispatchers.IO) {
        editDao.get(id)?.artworkPath?.let { File(it).delete() }
        editDao.delete(id)
        ArtworkLoader.clear()
    }

    private fun copyCover(id: String, source: Uri): String? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1024 && bounds.outHeight / (sample * 2) >= 1024) sample *= 2
        val bitmap = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        val dir = File(context.filesDir, "artwork").apply { mkdirs() }
        val file = File(dir, "${id.hashCode().toUInt()}-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        file.absolutePath
    }.getOrNull()

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

/** Applies a CentiMusic edit on top of the file's tags. A new album name regroups the song by name. */
internal fun Song.withEdit(edit: SongEditEntity): Song = copy(
    title = edit.title ?: title,
    artist = edit.artist ?: artist,
    album = edit.album ?: album,
    albumId = if (edit.album != null) 0 else albumId,
    artworkPath = edit.artworkPath ?: artworkPath,
)
