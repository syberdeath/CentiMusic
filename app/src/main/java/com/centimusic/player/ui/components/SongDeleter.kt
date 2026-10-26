package com.centimusic.player.ui.components

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.centimusic.player.data.media.DeleteResult
import com.centimusic.player.data.media.Song
import com.centimusic.player.data.media.SongSource
import com.centimusic.player.navigation.LocalAppContainer
import com.centimusic.player.navigation.LocalMessenger
import kotlinx.coroutines.launch

/** Entry point for "Delete from device" (one song or a selection); the flow runs in [SongDeleteHost]. */
@Stable
class SongDeleter {
    /** Songs waiting for CentiMusic's own confirmation dialog. */
    var confirming by mutableStateOf<List<Song>?>(null)
        internal set
    internal var start: (List<Song>) -> Unit = {}

    fun request(song: Song) = request(listOf(song))

    fun request(songs: List<Song>) {
        if (songs.isEmpty()) return
        // Android 11+ shows its own system confirmation for MediaStore files, so don't ask twice.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && songs.all { it.source == SongSource.MEDIA_STORE }) start(songs)
        else confirming = songs
    }
}

val LocalSongDeleter = staticCompositionLocalOf { SongDeleter() }

private class DeleteFlow {
    val queue = ArrayDeque<Song>()
    var awaiting: List<Song> = emptyList()
    var total = 0
    var deleted = 0
    var removedOnly = 0
    var lastFailure: String? = null
    var firstTitle = ""
    var next: () -> Unit = {}
    var handle: (Song, DeleteResult) -> Unit = { _, _ -> }

    fun reset(songs: List<Song>) {
        queue.clear()
        awaiting = emptyList()
        total = songs.size
        deleted = 0
        removedOnly = 0
        lastFailure = null
        firstTitle = songs.firstOrNull()?.title.orEmpty()
    }
}

/**
 * Deletes songs the way each Android version requires: one system delete dialog for the whole
 * batch on 11+, RecoverableSecurityException's dialog per file on 10, and WRITE_EXTERNAL_STORAGE
 * on 8–9. Deleted songs are removed from the queue, playlists, likes and history.
 */
@Composable
fun SongDeleteHost(deleter: SongDeleter) {
    val container = LocalAppContainer.current
    val message = LocalMessenger.current
    val scope = rememberCoroutineScope()
    val flow = remember { DeleteFlow() }

    fun finish() {
        val text = when {
            flow.deleted == 0 && flow.removedOnly == 0 -> flow.lastFailure ?: "Nothing was deleted"
            flow.total == 1 && flow.deleted == 1 -> "Deleted \"${flow.firstTitle}\""
            flow.total == 1 -> "This file can't be deleted from here, so it was removed from your library"
            else -> buildString {
                append("Deleted ${flow.deleted} ${if (flow.deleted == 1) "song" else "songs"}")
                if (flow.removedOnly > 0) append(" · ${flow.removedOnly} removed from library only")
            }
        }
        message(text)
    }

    val systemDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val songs = flow.awaiting
        flow.awaiting = emptyList()
        if (songs.isEmpty()) return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) {
            flow.queue.clear()
            message("Not deleted")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // The system dialog already deleted every file in the batch.
                songs.forEach {
                    flow.deleted++
                    container.forgetDeletedSong(it.id)
                }
                flow.next()
            } else {
                val song = songs.first()
                flow.handle(song, container.music.completeConfirmedDelete(song))
            }
        }
    }
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            flow.next()
        } else {
            flow.queue.clear()
            message("Storage permission is needed to delete files")
        }
    }

    flow.next = {
        scope.launch {
            val song = flow.queue.removeFirstOrNull()
            if (song == null) finish() else flow.handle(song, container.music.delete(song))
        }
    }
    flow.handle = { song, result ->
        when (result) {
            DeleteResult.Deleted -> {
                flow.deleted++
                scope.launch { container.forgetDeletedSong(song.id) }
                flow.next()
            }
            DeleteResult.RemovedFromLibraryOnly -> {
                flow.removedOnly++
                scope.launch { container.forgetDeletedSong(song.id) }
                flow.next()
            }
            is DeleteResult.NeedsConfirmation -> {
                flow.awaiting = listOf(song)
                systemDialog.launch(IntentSenderRequest.Builder(result.sender).build())
            }
            DeleteResult.NeedsWritePermission -> {
                flow.queue.addFirst(song)
                writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            is DeleteResult.Failed -> {
                flow.lastFailure = result.reason
                flow.next()
            }
        }
    }
    val start: (List<Song>) -> Unit = { songs ->
        flow.reset(songs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val (mediaStore, imported) = songs.partition { it.source == SongSource.MEDIA_STORE }
            flow.queue.addAll(imported)
            if (mediaStore.isNotEmpty()) {
                flow.awaiting = mediaStore
                systemDialog.launch(IntentSenderRequest.Builder(container.music.batchDeleteRequest(mediaStore)).build())
            } else {
                flow.next()
            }
        } else {
            flow.queue.addAll(songs)
            flow.next()
        }
    }
    deleter.start = start

    deleter.confirming?.let { songs ->
        val single = songs.singleOrNull()
        ConfirmDialog(
            title = if (single != null) "Delete \"${single.title}\"?" else "Delete ${songs.size} songs?",
            message = "This permanently deletes ${if (single != null) "the file" else "these files"} from your device. " +
                "${if (single != null) "It" else "They"} will also be removed from your playlists and likes.",
            confirmLabel = "Delete",
            onConfirm = {
                deleter.confirming = null
                start(songs)
            },
            onDismiss = { deleter.confirming = null },
        )
    }
}
