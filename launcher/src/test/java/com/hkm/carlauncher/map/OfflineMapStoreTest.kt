package com.hkm.carlauncher.map

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMapStoreTest {
    private val dir: File = Files.createTempDirectory("maps").toFile()
    private val good = "PMTiles".toByteArray() + byteArrayOf(3) + ByteArray(100) { it.toByte() }

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Writes [bytes] to the target, or throws when [fail] is set. */
    private class FakeDownloader(var bytes: ByteArray, var fail: Boolean = false) : MapDownloader {
        override suspend fun fetch(url: String, target: File, onProgress: (Int) -> Unit) {
            if (fail) throw IOException("network down")
            onProgress(50)
            target.writeBytes(bytes)
            onProgress(100)
        }
    }

    private fun store(expectedSha: String?, downloader: MapDownloader) =
        OfflineMapStore(dir, sha256Fetcher = { expectedSha }, downloader = downloader)

    @Test
    fun verifiedDownloadInstalls() = runTest {
        val s = store(sha(good), FakeDownloader(good))
        s.download()
        assertInstalled(s, good)
        assertTrue(s.status.value is OfflineMapStatus.Installed)
        assertEquals(good.size.toLong(), (s.status.value as OfflineMapStatus.Installed).sizeBytes)
    }

    @Test
    fun installedFileSurvivesNewInstance() = runTest {
        store(sha(good), FakeDownloader(good)).download()
        val again = store(null, FakeDownloader(ByteArray(0)))
        assertInstalled(again, good)
    }

    @Test
    fun unverifiedFileIsNotInstalled() {
        File(dir, "uae-0123456789ab.pmtiles").writeBytes(good) // e.g. crash before the marker was written
        val s = store(null, FakeDownloader(good))
        assertNull(s.installedFile.value)
        assertEquals(OfflineMapStatus.NotInstalled, s.status.value)
    }

    @Test
    fun truncatedFileIsNotInstalled() = runTest {
        val s = store(sha(good), FakeDownloader(good))
        s.download()
        s.installedFile.value!!.writeBytes(good.copyOf(10))
        assertNull(store(null, FakeDownloader(good)).installedFile.value)
    }

    @Test
    fun failedVerificationKeepsOldFile() = runTest {
        val s = store(sha(good), FakeDownloader(good))
        s.download()
        val other = good + byteArrayOf(9)
        val s2 = store("0".repeat(64), FakeDownloader(other))
        s2.download()
        assertTrue(s2.status.value is OfflineMapStatus.Failed)
        assertInstalled(s2, good)
        assertFalse(File(dir, "uae.pmtiles.part").exists())
    }

    @Test
    fun networkFailureReportsAndCleansUp() = runTest {
        val s = store(sha(good), FakeDownloader(good, fail = true))
        s.download()
        assertTrue(s.status.value is OfflineMapStatus.Failed)
        assertNull(s.installedFile.value)
        assertFalse(File(dir, "uae.pmtiles.part").exists())
    }

    @Test
    fun missingChecksumFailsDownload() = runTest {
        val s = store(null, FakeDownloader(good))
        s.download()
        assertTrue(s.status.value is OfflineMapStatus.Failed)
        assertNull(s.installedFile.value)
    }

    @Test
    fun importWithoutChecksumChecksMagic() = runTest {
        val s = store(null, FakeDownloader(good))
        s.import({ ByteArrayInputStream("hello".toByteArray()) }, expectedSha256 = null)
        assertTrue(s.status.value is OfflineMapStatus.Failed)
        assertNull(s.installedFile.value)
        s.import({ ByteArrayInputStream(good) }, expectedSha256 = null)
        assertInstalled(s, good)
    }

    @Test
    fun importWithChecksumVerifies() = runTest {
        val s = store(null, FakeDownloader(good))
        s.import({ ByteArrayInputStream(good) }, expectedSha256 = "f".repeat(64))
        assertNull(s.installedFile.value)
        s.import({ ByteArrayInputStream(good) }, expectedSha256 = sha(good).uppercase())
        assertInstalled(s, good)
    }

    @Test
    fun deleteRemovesEverything() = runTest {
        val s = store(sha(good), FakeDownloader(good))
        s.download()
        s.delete()
        assertNull(s.installedFile.value)
        assertEquals(OfflineMapStatus.NotInstalled, s.status.value)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun progressIsReported() = runTest {
        val seen = mutableListOf<OfflineMapStatus>()
        val s = store(sha(good), object : MapDownloader {
            override suspend fun fetch(url: String, target: File, onProgress: (Int) -> Unit) {
                onProgress(42)
                seen += statusOf()
                target.writeBytes(good)
            }
            lateinit var statusOf: () -> OfflineMapStatus
        }.also { d -> d.statusOf = { storeRef!!.status.value } })
        storeRef = s
        s.download()
        assertEquals(OfflineMapStatus.Downloading(42), seen.single())
    }

    private fun assertInstalled(s: OfflineMapStore, bytes: ByteArray) {
        val f = s.installedFile.value
        assertTrue("installed file", f != null && f.exists() && f.readBytes().contentEquals(bytes))
    }

    @Test
    fun updateInstallsUnderANewName() = runTest {
        // A new map must change the source so the map view reloads it (same path would be cached).
        val first = store(sha(good), FakeDownloader(good)).also { it.download() }.installedFile.value!!
        val other = good + byteArrayOf(7)
        val s = store(sha(other), FakeDownloader(other))
        s.download()
        assertInstalled(s, other)
        assertTrue(s.installedFile.value != first)
        assertFalse("old file removed", first.exists())
    }

    @Test
    fun unreadableImportSourceFailsWithoutCrash() = runTest {
        val s = store(null, FakeDownloader(good))
        s.import({ throw java.io.FileNotFoundException("USB removed") }, expectedSha256 = null)
        assertTrue(s.status.value is OfflineMapStatus.Failed)
    }

    private var storeRef: OfflineMapStore? = null

    @Test
    fun sha256HexIsLowercase() {
        val f = File(dir, "x").apply { writeBytes(good) }
        assertEquals(sha(good), sha256Hex(f))
    }
}
