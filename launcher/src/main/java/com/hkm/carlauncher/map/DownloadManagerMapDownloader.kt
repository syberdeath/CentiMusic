package com.hkm.carlauncher.map

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Downloads with the system DownloadManager (handles retries and slow links), polling progress every 500 ms. */
class DownloadManagerMapDownloader(context: Context) : MapDownloader {
    private val dm = context.getSystemService(DownloadManager::class.java)

    override suspend fun fetch(url: String, target: File, onProgress: (Int) -> Unit) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("UAE offline map")
            .setDestinationUri(Uri.fromFile(target))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        val id = dm.enqueue(request)
        try {
            while (true) {
                val (status, done, total, reason) = query(id) ?: throw IOException("download was cancelled")
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> { onProgress(100); return }
                    DownloadManager.STATUS_FAILED -> throw IOException("error $reason")
                    else -> if (total > 0) onProgress((done * 100 / total).toInt())
                }
                delay(500)
            }
        } catch (e: CancellationException) {
            dm.remove(id)
            throw e
        } catch (e: IOException) {
            dm.remove(id)
            throw e
        }
    }

    private data class Progress(val status: Int, val done: Long, val total: Long, val reason: Int)

    private fun query(id: Long): Progress? =
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return null
            Progress(
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
            )
        }
}

/** Reads a small text file (the published checksum) or a size; null when offline or not found. */
object MapRelease {
    suspend fun fetchSha256(): String? = withContext(Dispatchers.IO) {
        runCatching {
            open(UAE_MAP_SHA_URL).run {
                if (responseCode != 200) null else inputStream.bufferedReader().use { it.readText() }.trim().take(64)
            }
        }.getOrNull()?.takeIf { it.length == 64 }
    }

    /** Size of the published map file, for the download offer; null if unknown. */
    suspend fun fetchSizeBytes(): Long? = withContext(Dispatchers.IO) {
        runCatching {
            open(UAE_MAP_URL, method = "HEAD").run { contentLengthLong.takeIf { responseCode == 200 && it > 0 } }
        }.getOrNull()
    }

    private fun open(url: String, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = true
            connectTimeout = 10_000
            readTimeout = 10_000
        }
}
