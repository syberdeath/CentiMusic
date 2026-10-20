package com.centimusic.player.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.centimusic.player.data.media.Library
import com.centimusic.player.data.media.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/** One entry of the play queue. [index] is the item's position in the player's (unshuffled) list. */
@Immutable
data class QueueEntry(val key: String, val index: Int, val song: Song)

@Immutable
data class PlaybackState(
    val connected: Boolean = false,
    val current: Song? = null,
    val currentIndex: Int = C.INDEX_UNSET,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    /** The queue in play order (respecting shuffle). */
    val queue: List<QueueEntry> = emptyList(),
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
) {
    val queuePosition: Int get() = queue.indexOfFirst { it.index == currentIndex }
    val upNext: List<QueueEntry> get() = queuePosition.let { if (it < 0) queue else queue.drop(it + 1) }
}

/**
 * The app-side handle on [PlaybackService]: a MediaController whose state is mirrored into flows
 * for Compose. All playback goes through the service, so the UI, notification, lock screen and
 * headset buttons always agree.
 */
class PlayerConnection(
    private val context: Context,
    private val scope: CoroutineScope,
    private val library: StateFlow<Library>,
) {
    private var controller: MediaController? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Human-readable playback problems (missing files, unsupported formats). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        scope.launch { connect() }
        // Library rescans can update metadata for items already in the queue.
        library.onEach { publish(full = true) }.launchIn(scope)
    }

    private suspend fun connect() {
        try {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val c = MediaController.Builder(context, token).buildAsync().await()
            c.addListener(listener)
            controller = c
            publish(full = true)
            pending.forEach { it(c) }
            pending.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't connect to playback service", e)
        }
    }

    /** Runs [action] now if connected, or as soon as the controller is ready. */
    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else pending += action
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val structural = events.containsAny(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
            )
            publish(full = structural)
        }

        override fun onPlayerError(error: PlaybackException) {
            val title = controller?.currentMediaItem?.mediaMetadata?.title ?: "this track"
            val reason = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "the file is missing"
                PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "access to the file was revoked"
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "the format isn't supported"
                else -> "it couldn't be read"
            }
            _messages.tryEmit("Skipped \"$title\" — $reason")
        }
    }

    private fun publish(full: Boolean) {
        val c = controller ?: return
        val previous = _state.value
        val lib = library.value
        val currentItem = c.currentMediaItem
        val queue = if (full || previous.queue.size != c.mediaItemCount) buildQueue(c, lib) else previous.queue
        val currentSong = currentItem?.let { lib.byId[it.mediaId] ?: it.toSong() }
        val duration = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: currentSong?.durationMs ?: 0L
        _state.value = PlaybackState(
            connected = true,
            current = currentSong,
            currentIndex = c.currentMediaItemIndex,
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            durationMs = duration,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = queue,
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem() || c.currentPosition > 0,
        )
    }

    private fun buildQueue(c: MediaController, lib: Library): List<QueueEntry> {
        val timeline = c.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val shuffle = c.shuffleModeEnabled
        val seen = HashMap<String, Int>()
        val out = ArrayList<QueueEntry>(timeline.windowCount)
        var i = timeline.getFirstWindowIndex(shuffle)
        while (i != C.INDEX_UNSET && out.size < timeline.windowCount) {
            val item = c.getMediaItemAt(i)
            val occurrence = (seen[item.mediaId] ?: 0) + 1
            seen[item.mediaId] = occurrence
            out += QueueEntry("${item.mediaId}#$occurrence", i, lib.byId[item.mediaId] ?: item.toSong())
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle)
        }
        return out
    }

    /** Emits the playback position a few times per second while collected. */
    fun positionTicker(intervalMs: Long = 250): Flow<Long> = flow {
        while (true) {
            emit(controller?.currentPosition ?: 0L)
            delay(intervalMs)
        }
    }.distinctUntilChanged()

    // region Commands

    /** Replaces the queue with [songs]. [shuffle] = null keeps the current shuffle setting. */
    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean? = null) {
        if (songs.isEmpty()) return
        withController { c ->
            if (shuffle != null) c.shuffleModeEnabled = shuffle
            val start = if (shuffle == true) songs.indices.random() else startIndex.coerceIn(songs.indices)
            c.setMediaItems(songs.map { it.toMediaItem() }, start, 0L)
            c.prepare()
            c.play()
        }
    }

    fun shuffle(songs: List<Song>) = play(songs, shuffle = true)

    fun togglePlayPause() = withController { c ->
        when {
            c.mediaItemCount == 0 -> Unit
            c.playbackState == Player.STATE_IDLE -> { c.prepare(); c.play() }
            c.playbackState == Player.STATE_ENDED -> { c.seekToDefaultPosition(0); c.play() }
            c.playWhenReady -> c.pause()
            else -> c.play()
        }
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playNext(songs: List<Song>) = withController { c ->
        if (c.mediaItemCount == 0) {
            play(songs)
        } else {
            c.addMediaItems(c.currentMediaItemIndex + 1, songs.map { it.toMediaItem() })
        }
    }

    fun addToQueue(songs: List<Song>) = withController { c ->
        if (c.mediaItemCount == 0) play(songs) else c.addMediaItems(songs.map { it.toMediaItem() })
    }

    fun playQueueItem(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            c.prepare()
            c.play()
        }
    }

    fun moveQueueItem(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount && from != to) c.moveMediaItem(from, to)
    }

    fun removeQueueItem(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    /** Clears everything except the track that is currently playing. */
    fun clearUpNext() = withController { c ->
        val current = c.currentMediaItemIndex
        if (current == C.INDEX_UNSET) {
            c.clearMediaItems()
            return@withController
        }
        if (current + 1 < c.mediaItemCount) c.removeMediaItems(current + 1, c.mediaItemCount)
        if (current > 0) c.removeMediaItems(0, current)
    }

    // endregion

    companion object {
        private const val TAG = "PlayerConnection"
    }
}
