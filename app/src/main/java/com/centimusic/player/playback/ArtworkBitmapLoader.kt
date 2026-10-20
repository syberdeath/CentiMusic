package com.centimusic.player.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.centimusic.player.data.media.ArtworkLoader
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.guava.future
import java.io.IOException

/**
 * Supplies artwork to the MediaSession (notification, lock screen, Bluetooth/AVRCP, system media
 * controls) using the same loader as the app UI: MediaStore thumbnails on Android 10+, album art
 * on older versions, and embedded pictures for imported files that have no artwork URI.
 */
@UnstableApi
class ArtworkBitmapLoader(
    private val context: Context,
    private val delegate: BitmapLoader,
    private val scope: CoroutineScope,
) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = delegate.supportsMimeType(mimeType)
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = delegate.decodeBitmap(data)
    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = delegate.loadBitmap(uri)

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        val mediaId = metadata.extras?.getString(EXTRA_MEDIA_ID) ?: return delegate.loadBitmapFromMetadata(metadata)
        return scope.future(Dispatchers.IO) {
            val song = MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(metadata).build().toSong()
            ArtworkLoader.load(context, song, SIZE_PX)
                ?: metadata.artworkUri?.let { delegate.loadBitmap(it).await() }
                ?: throw IOException("No artwork for $mediaId")
        }
    }

    private companion object {
        /** Large enough for lock-screen art, small enough to cross the binder comfortably. */
        const val SIZE_PX = 512
    }
}
