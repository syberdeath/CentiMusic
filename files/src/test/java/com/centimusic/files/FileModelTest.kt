package com.centimusic.files

import com.centimusic.files.data.FileItem
import com.centimusic.files.data.FileKind
import com.centimusic.files.data.SortField
import com.centimusic.files.data.SortOrder
import com.centimusic.files.data.breadcrumbs
import com.centimusic.files.data.findDuplicates
import com.centimusic.files.data.formatSize
import com.centimusic.files.data.isValidName
import com.centimusic.files.data.kindOf
import com.centimusic.files.data.sortFiles
import com.centimusic.files.data.uniqueName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileModelTest {
    private fun file(name: String, size: Long = 1, modified: Long = 0, dir: Boolean = false, path: String = "/s/$name") =
        FileItem(path, name, dir, size, modified)

    @Test fun formatsSizes() {
        assertEquals("0 B", formatSize(0))
        assertEquals("999 B", formatSize(999))
        assertEquals("1 KB", formatSize(1000))
        assertEquals("1.5 KB", formatSize(1500))
        assertEquals("12.3 MB", formatSize(12_340_000))
        assertEquals("100 MB", formatSize(100_000_000))
        assertEquals("256 GB", formatSize(256_000_000_000))
    }

    @Test fun detectsKinds() {
        assertEquals(FileKind.IMAGE, kindOf("IMG_001.JPG"))
        assertEquals(FileKind.VIDEO, kindOf("clip.mp4"))
        assertEquals(FileKind.AUDIO, kindOf("song.flac"))
        assertEquals(FileKind.DOCUMENT, kindOf("report.pdf"))
        assertEquals(FileKind.APK, kindOf("app.apk"))
        assertEquals(FileKind.ARCHIVE, kindOf("backup.tar.gz"))
        assertEquals(FileKind.OTHER, kindOf(".nomedia"))
        assertEquals(FileKind.OTHER, kindOf("README"))
    }

    @Test fun foldersSortFirst() {
        val items = listOf(file("b.txt", size = 5), file("Z", dir = true), file("a.txt", size = 9), file("c", dir = true))
        assertEquals(listOf("c", "Z", "a.txt", "b.txt"), items.sortFiles(SortOrder()).map { it.name })
        assertEquals(listOf("Z", "c", "b.txt", "a.txt"), items.sortFiles(SortOrder(SortField.NAME, false)).map { it.name })
        assertEquals(listOf("a.txt", "b.txt"), items.sortFiles(SortOrder(SortField.SIZE, false)).filter { !it.isDirectory }.map { it.name })
    }

    @Test fun groupsDuplicatesKeepingOldestLast() {
        val a1 = file("a.jpg", size = 10, modified = 1, path = "/1/a.jpg")
        val a2 = file("a copy.jpg", size = 10, modified = 5, path = "/2/a.jpg")
        val b = file("b.jpg", size = 10, modified = 3, path = "/3/b.jpg")
        val c = file("c.jpg", size = 99, path = "/4/c.jpg")
        val hashes = mapOf(a1.path to "x", a2.path to "x", b.path to "y", c.path to "x")
        val groups = findDuplicates(listOf(a1, a2, b, c)) { hashes[it.path] }
        assertEquals(1, groups.size)
        assertEquals(listOf(a2.path, a1.path), groups.single().map { it.path })
    }

    @Test fun buildsBreadcrumbs() {
        val crumbs = breadcrumbs("/storage/0/DCIM/Camera", "/storage/0", "Internal storage")
        assertEquals(listOf("Internal storage", "DCIM", "Camera"), crumbs.map { it.first })
        assertEquals("/storage/0/DCIM", crumbs[1].second)
    }

    @Test fun makesUniqueNames() {
        assertEquals("a.jpg", uniqueName("a.jpg", setOf("b.jpg")))
        assertEquals("a (1).jpg", uniqueName("a.jpg", setOf("a.jpg")))
        assertEquals("a (2).jpg", uniqueName("a.jpg", setOf("a.jpg", "a (1).jpg")))
        assertEquals("Folder (1)", uniqueName("Folder", setOf("Folder")))
    }

    @Test fun validatesNames() {
        assertTrue(isValidName("notes.txt"))
        assertFalse(isValidName(""))
        assertFalse(isValidName(".."))
        assertFalse(isValidName("a/b"))
    }
}
