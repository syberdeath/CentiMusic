package com.hkm.carlauncher.map

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

const val UAE_MAP_URL = "https://github.com/syberdeath/CentiMusic/releases/download/uae-map/uae.pmtiles"
const val UAE_MAP_SHA_URL = "$UAE_MAP_URL.sha256"

sealed interface OfflineMapStatus {
    data object NotInstalled : OfflineMapStatus
    data class Downloading(val percent: Int) : OfflineMapStatus
    data class Installed(val sizeBytes: Long, val dateMillis: Long) : OfflineMapStatus
    data class Failed(val message: String) : OfflineMapStatus
}

/** Fetches [url] into [target], reporting 0..100. Throws on failure. */
interface MapDownloader {
    suspend fun fetch(url: String, target: File, onProgress: (Int) -> Unit)
}

/**
 * Keeps the UAE offline map in [dir]. A new file lands in `uae.pmtiles.part`, is verified, then is
 * renamed to `uae-<sha>.pmtiles`; only then does the marker `uae.ok` (file name, checksum, size)
 * switch to it, and the old file is removed. Each map has its own name, so the map view always
 * reloads after an update. A file the marker doesn't name, e.g. one left by a crash or reboot
 * mid-install, is never used.
 */
class OfflineMapStore(
    private val dir: File,
    private val sha256Fetcher: suspend () -> String?,
    private val downloader: MapDownloader,
) {
    private val part = File(dir, "uae.pmtiles.part")
    private val marker = File(dir, "uae.ok")
    private val markerTmp = File(dir, "uae.ok.tmp")

    private val _installedFile = MutableStateFlow(verifiedFile())
    val installedFile: StateFlow<File?> = _installedFile.asStateFlow()

    private val _status = MutableStateFlow(installedStatus())
    val status: StateFlow<OfflineMapStatus> = _status.asStateFlow()

    suspend fun download() {
        _status.value = OfflineMapStatus.Downloading(0)
        try {
            val expected = sha256Fetcher()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return fail("Couldn't reach the map server. Check the internet connection and try again.")
            dir.mkdirs()
            part.delete()
            downloader.fetch(UAE_MAP_URL, part) { _status.value = OfflineMapStatus.Downloading(it.coerceIn(0, 100)) }
            install(expected.lowercase(), checkMagic = false, damaged = "The downloaded map is damaged. Try again.")
        } catch (e: CancellationException) {
            part.delete()
            throw e
        } catch (e: Exception) {
            fail("Download failed: ${e.message ?: "unknown error"}")
        }
    }

    /**
     * Copies a map file chosen by the user (e.g. from USB); [open] may throw if the stick was removed.
     * Without a checksum, only the PMTiles header is checked.
     */
    suspend fun import(open: () -> InputStream, expectedSha256: String?) {
        try {
            withContext(Dispatchers.IO) {
                dir.mkdirs()
                part.delete()
                open().use { src -> part.outputStream().use { src.copyTo(it) } }
            }
            install(expectedSha256?.trim()?.lowercase(), checkMagic = expectedSha256 == null, damaged = "This file isn't the UAE map, or it is damaged.")
        } catch (e: CancellationException) {
            part.delete()
            throw e
        } catch (e: Exception) {
            fail("Couldn't load the map: ${e.message ?: "unknown error"}")
        }
    }

    fun delete() {
        marker.delete()
        dir.listFiles { f -> f.name.startsWith("uae") }?.forEach { it.delete() }
        _installedFile.value = null
        _status.value = OfflineMapStatus.NotInstalled
    }

    private suspend fun install(expectedSha: String?, checkMagic: Boolean, damaged: String) {
        val actual = withContext(Dispatchers.IO) { sha256Hex(part) }
        val ok = if (checkMagic) hasPmtilesHeader(part) else actual == expectedSha
        if (!ok) return fail(damaged)
        val previous = _installedFile.value
        val target = withContext(Dispatchers.IO) {
            val target = File(dir, "uae-${actual.take(12)}.pmtiles")
            if (target != previous) target.delete()
            if (target == previous || !part.renameTo(target)) {
                // Same map already installed (re-import of the same file): keep it as is.
                check(target == previous) { "couldn't store the map file" }
                part.delete()
            }
            markerTmp.writeText("${target.name} $actual ${target.length()}")
            check(markerTmp.renameTo(marker)) { "couldn't record the map file" }
            if (previous != null && previous != target) previous.delete()
            target
        }
        _installedFile.value = target
        _status.value = installedStatus()
    }

    private fun fail(message: String) {
        part.delete()
        _status.value = OfflineMapStatus.Failed(message)
    }

    private fun verifiedFile(): File? {
        if (!marker.exists()) return null
        val parts = marker.readText().trim().split(" ")
        val file = parts.getOrNull(0)?.let { File(dir, it) } ?: return null
        val size = parts.getOrNull(2)?.toLongOrNull() ?: return null
        return file.takeIf { it.exists() && it.length() == size }
    }

    private fun installedStatus(): OfflineMapStatus =
        verifiedFile()?.let { OfflineMapStatus.Installed(it.length(), it.lastModified()) }
            ?: OfflineMapStatus.NotInstalled

    private fun hasPmtilesHeader(file: File): Boolean {
        val head = ByteArray(8)
        val read = file.inputStream().use { it.read(head) }
        return read == 8 && String(head, 0, 7, Charsets.US_ASCII) == "PMTiles" && head[7] == 3.toByte()
    }
}

fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
