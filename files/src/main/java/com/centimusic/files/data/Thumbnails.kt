package com.centimusic.files.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Thumbnails for images, videos, audio artwork and APK icons, cached in memory. */
class Thumbnails(private val context: Context) {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val missing = HashSet<String>()

    fun cached(item: FileItem): Bitmap? = cache.get(key(item))

    suspend fun load(item: FileItem, px: Int): Bitmap? {
        val key = key(item)
        cache.get(key)?.let { return it }
        synchronized(missing) { if (key in missing) return null }
        val bmp = withContext(Dispatchers.IO) { runCatching { decode(item, px) }.getOrNull() }
        if (bmp != null) cache.put(key, bmp) else synchronized(missing) { missing += key }
        return bmp
    }

    private fun key(item: FileItem) = "${item.path}:${item.modified}"

    @Suppress("DEPRECATION")
    private fun decode(item: FileItem, px: Int): Bitmap? {
        val file = File(item.path)
        return when (item.kind) {
            FileKind.IMAGE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createImageThumbnail(file, Size(px, px), null)
            } else {
                sampled(item.path, px)
            }
            FileKind.VIDEO -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createVideoThumbnail(file, Size(px, px), null)
            } else {
                ThumbnailUtils.createVideoThumbnail(item.path, MediaStore.Images.Thumbnails.MINI_KIND)
            }
            FileKind.AUDIO -> MediaMetadataRetriever().run {
                try {
                    setDataSource(item.path)
                    embeddedPicture?.let { bytes ->
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                        opts.inSampleSize = sampleSize(opts.outWidth, opts.outHeight, px)
                        opts.inJustDecodeBounds = false
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    }
                } finally {
                    release()
                }
            }
            FileKind.APK -> {
                val pm = context.packageManager
                val info = pm.getPackageArchiveInfo(item.path, 0) ?: return null
                val app = info.applicationInfo ?: return null
                app.sourceDir = item.path
                app.publicSourceDir = item.path
                val d = app.loadIcon(pm)
                val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, px, px)
                d.draw(Canvas(bmp))
                bmp
            }
            else -> null
        }
    }

    private fun sampled(path: String, px: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        opts.inSampleSize = sampleSize(opts.outWidth, opts.outHeight, px)
        opts.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, opts)
    }

    private fun sampleSize(w: Int, h: Int, px: Int): Int {
        var s = 1
        while (w / (s * 2) >= px && h / (s * 2) >= px) s *= 2
        return s
    }
}
