package com.centimusic.player.data.media

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Loads embedded or MediaStore album art with an in-memory LRU cache. Misses are cached too, so
 * songs without artwork don't hit the disk on every recomposition.
 */
object ArtworkLoader {
    private object Missing

    private val cache = object : LruCache<String, Any>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Any): Int = (value as? Bitmap)?.allocationByteCount ?: 64
    }
    private val permits = Semaphore(4)

    /** Album art URI understood by MediaStore on all supported versions; used for notification artwork. */
    fun albumArtUri(albumId: Long): Uri =
        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

    fun cached(song: Song, sizePx: Int): Bitmap? = cache.get(cacheKey(song, sizePx)) as? Bitmap

    /** Any already-decoded size, largest first: a stand-in while the right size loads. */
    fun cachedAny(song: Song): Bitmap? =
        listOf(1024, 512, 192).firstNotNullOfOrNull { cache.get("${song.artworkKey}@$it") as? Bitmap }

    suspend fun load(context: Context, song: Song, sizePx: Int): Bitmap? {
        val key = cacheKey(song, sizePx)
        when (val hit = cache.get(key)) {
            is Bitmap -> return hit
            Missing -> return null
        }
        return withContext(Dispatchers.IO) {
            permits.withPermit {
                (cache.get(key) as? Bitmap)?.let { return@withPermit it }
                val bitmap = runCatching { decode(context.applicationContext, song, bucket(sizePx)) }.getOrNull()
                cache.put(key, bitmap ?: Missing)
                bitmap
            }
        }
    }

    fun clear() = cache.evictAll()

    private fun bucket(sizePx: Int): Int = when {
        sizePx <= 192 -> 192
        sizePx <= 512 -> 512
        else -> 1024
    }

    private fun cacheKey(song: Song, sizePx: Int) = "${song.artworkKey}@${bucket(sizePx)}"

    private fun decode(context: Context, song: Song, size: Int): Bitmap? {
        song.artworkPath?.let { path -> decodeFile(path, size)?.let { return it } }
        val resolver = context.contentResolver
        if (song.source == SongSource.MEDIA_STORE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (song.albumId > 0) {
                    val albumUri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, song.albumId)
                    runCatching { resolver.loadThumbnail(albumUri, Size(size, size), null) }.getOrNull()?.let { return it }
                }
                runCatching { resolver.loadThumbnail(song.uri, Size(size, size), null) }.getOrNull()?.let { return it }
            } else if (song.albumId > 0) {
                decodeStream(context, albumArtUri(song.albumId), size)?.let { return it }
            }
        }
        return embeddedPicture(context, song.uri, size)
    }

    private fun decodeFile(path: String, size: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, size) })
    }

    private fun decodeStream(context: Context, uri: Uri, size: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, size) }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun embeddedPicture(context: Context, uri: Uri, size: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, size) }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
        return sample
    }
}
