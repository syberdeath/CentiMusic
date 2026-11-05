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
 * Keeps the UAE offline map in [dir]. New files land in `uae.pmtiles.part`, are verified, then
 * replace `uae.pmtiles`; the marker `uae.pmtiles.ok` (checksum and size) is written last. A file
 * without a matching marker, such as one left by a crash or reboot mid-install, is never used.
 */
class OfflineMapStore(
    private val dir: File,
    private val sha256Fetcher: suspend () -> String?,
    private val downloader: MapDownloader,
) {
    private val final = File(dir, "uae.pmtiles")
    private val part = File(dir, "uae.pmtiles.part")
    private val marker = File(dir, "uae.pmtiles.ok")

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

    /** Copies a map file chosen by the user (e.g. from USB). Without a checksum, only the PMTiles header is checked. */
    suspend fun import(input: InputStream, expectedSha256: String?) {
        try {
            withContext(Dispatchers.IO) {
                dir.mkdirs()
                part.delete()
                input.use { src -> part.outputStream().use { src.copyTo(it) } }
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
        final.delete()
        part.delete()
        _installedFile.value = null
        _status.value = OfflineMapStatus.NotInstalled
    }

    private suspend fun install(expectedSha: String?, checkMagic: Boolean, damaged: String) {
        val actual = withContext(Dispatchers.IO) { sha256Hex(part) }
        val ok = if (checkMagic) hasPmtilesHeader(part) else actual == expectedSha
        if (!ok) return fail(damaged)
        withContext(Dispatchers.IO) {
            marker.delete()
            if (!part.renameTo(final)) {
                final.delete()
                check(part.renameTo(final)) { "couldn't replace the map file" }
            }
            marker.writeText("$actual ${final.length()}")
        }
        _installedFile.value = final
        _status.value = installedStatus()
    }

    private fun fail(message: String) {
        part.delete()
        _status.value = OfflineMapStatus.Failed(message)
    }

    private fun verifiedFile(): File? {
        if (!final.exists() || !marker.exists()) return null
        val size = marker.readText().trim().split(" ").getOrNull(1)?.toLongOrNull() ?: return null
        return final.takeIf { it.length() == size }
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
