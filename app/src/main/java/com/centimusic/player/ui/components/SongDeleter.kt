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

/** Entry point for "Delete from device"; the flow itself runs in [SongDeleteHost]. */
@Stable
class SongDeleter {
    /** Song waiting for CentiMusic's own confirmation dialog. */
    var confirming by mutableStateOf<Song?>(null)
        internal set
    internal var start: (Song) -> Unit = {}

    fun request(song: Song) {
        // Android 11+ shows its own system confirmation for MediaStore files, so don't ask twice.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && song.source == SongSource.MEDIA_STORE) start(song)
        else confirming = song
    }
}

val LocalSongDeleter = staticCompositionLocalOf { SongDeleter() }

private class DeleteFlow {
    var awaiting: Song? = null
    var perform: (Song) -> Unit = {}
    var handle: (Song, DeleteResult) -> Unit = { _, _ -> }
}

/**
 * Deletes a song's file the way each Android version requires: the system delete dialog on 11+,
 * RecoverableSecurityException's dialog on 10, and WRITE_EXTERNAL_STORAGE on 8–9. Afterwards the
 * song is removed from the queue, playlists, likes and history.
 */
@Composable
fun SongDeleteHost(deleter: SongDeleter) {
    val container = LocalAppContainer.current
    val message = LocalMessenger.current
    val scope = rememberCoroutineScope()
    val flow = remember { DeleteFlow() }

    val systemDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val song = flow.awaiting ?: return@rememberLauncherForActivityResult
        flow.awaiting = null
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch { flow.handle(song, container.music.completeConfirmedDelete(song)) }
        } else {
            message("Not deleted")
        }
    }
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val song = flow.awaiting ?: return@rememberLauncherForActivityResult
        flow.awaiting = null
        if (granted) flow.perform(song) else message("Storage permission is needed to delete files")
    }

    flow.handle = { song, result ->
        when (result) {
            DeleteResult.Deleted -> {
                message("Deleted \"${song.title}\"")
                scope.launch { container.forgetDeletedSong(song.id) }
            }
            DeleteResult.RemovedFromLibraryOnly -> {
                message("This file can't be deleted from here, so it was removed from your library")
                scope.launch { container.forgetDeletedSong(song.id) }
            }
            is DeleteResult.NeedsConfirmation -> {
                flow.awaiting = song
                systemDialog.launch(IntentSenderRequest.Builder(result.sender).build())
            }
            DeleteResult.NeedsWritePermission -> {
                flow.awaiting = song
                writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            is DeleteResult.Failed -> message(result.reason)
        }
    }
    flow.perform = { song -> scope.launch { flow.handle(song, container.music.delete(song)) } }
    deleter.start = flow.perform

    deleter.confirming?.let { song ->
        ConfirmDialog(
            title = "Delete \"${song.title}\"?",
            message = "This permanently deletes the file from your device. It will also be removed from your playlists and likes.",
            confirmLabel = "Delete",
            onConfirm = {
                deleter.confirming = null
                flow.perform(song)
            },
            onDismiss = { deleter.confirming = null },
        )
    }
}
