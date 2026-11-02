package com.centimusic.files.data

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class StorageStats(val total: Long, val free: Long) {
    val used: Long get() = (total - free).coerceAtLeast(0)
    val usedFraction: Float get() = if (total <= 0) 0f else used.toFloat() / total
}

data class CleanReport(
    val junk: List<FileItem> = emptyList(),
    val duplicates: List<List<FileItem>> = emptyList(),
    val large: List<FileItem> = emptyList(),
    val oldDownloads: List<FileItem> = emptyList(),
    val oldScreenshots: List<FileItem> = emptyList(),
)

/** Progress of a copy/move: bytes done out of total. */
fun interface Progress { fun onProgress(done: Long, total: Long) }

/**
 * Everything that touches storage. Folders are read straight from the file system; categories,
 * search, recents and Clean read a whole-storage index built from MediaStore (fast, already
 * maintained by Android), falling back to walking the file system when MediaStore returns nothing.
 */
class FileRepository(private val context: Context) {

    val root: String = Environment.getExternalStorageDirectory().absolutePath
    private val downloadsDir = File(root, Environment.DIRECTORY_DOWNLOADS)

    private val indexLock = Mutex()
    @Volatile private var index: List<FileItem>? = null

    fun storageStats(): StorageStats = runCatching {
        val stat = StatFs(root)
        StorageStats(stat.totalBytes, stat.availableBytes)
    }.getOrDefault(StorageStats(0, 0))

    suspend fun list(path: String, showHidden: Boolean): List<FileItem> = withContext(Dispatchers.IO) {
        val dir = File(path)
        (dir.listFiles() ?: emptyArray())
            .asSequence()
            .filter { showHidden || !it.name.startsWith(".") }
            .map { it.toItem(countChildren = true, showHidden = showHidden) }
            .toList()
    }

    fun item(path: String): FileItem = File(path).toItem(countChildren = true, showHidden = true)

    /** Drops the cached index so the next read sees changes made outside an operation. */
    fun invalidate() { index = null }

    private suspend fun index(): List<FileItem> = indexLock.withLock {
        index ?: withContext(Dispatchers.IO) {
            val fromStore = queryMediaStore()
            (if (fromStore.isNotEmpty()) fromStore else walk()).also { index = it }
        }
    }

    @Suppress("DEPRECATION") // DATA is the only way to get real paths; it's valid with all-files access.
    private fun queryMediaStore(): List<FileItem> {
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
        )
        val out = ArrayList<FileItem>()
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                val dataCol = c.getColumnIndexOrThrow(projection[0])
                val sizeCol = c.getColumnIndexOrThrow(projection[1])
                val dateCol = c.getColumnIndexOrThrow(projection[2])
                while (c.moveToNext()) {
                    val path = c.getString(dataCol) ?: continue
                    if (!path.startsWith(root) || isUnderHiddenOrPrivate(path)) continue
                    val file = File(path)
                    if (!file.isFile) continue
                    out += FileItem(
                        path = path,
                        name = file.name,
                        isDirectory = false,
                        size = c.getLong(sizeCol).takeIf { it > 0 } ?: file.length(),
                        modified = c.getLong(dateCol).takeIf { it > 0 }?.times(1000) ?: file.lastModified(),
                    )
                }
            }
        }
        return out
    }

    private fun walk(): List<FileItem> =
        File(root).walkTopDown()
            .onEnter { !it.name.startsWith(".") && !isPrivate(it.absolutePath) }
            .filter { it.isFile && !it.name.startsWith(".") }
            .map { it.toItem(countChildren = false, showHidden = false) }
            .toList()

    private fun isPrivate(path: String) =
        path.startsWith("$root/Android/data") || path.startsWith("$root/Android/obb")

    private fun isUnderHiddenOrPrivate(path: String) =
        isPrivate(path) || path.removePrefix(root).split('/').any { it.startsWith(".") }

    suspend fun category(category: Category): List<FileItem> = withContext(Dispatchers.IO) {
        if (category == Category.DOWNLOADS) {
            val dl = downloadsDir.absolutePath
            index().filter { it.path.startsWith("$dl/") }.sortedByDescending { it.modified }
        } else {
            index().filter { category.matches(it) }.sortedByDescending { it.modified }
        }
    }

    /** Count and total size for each category, for the Browse grid. */
    suspend fun categorySummary(): Map<Category, Pair<Int, Long>> = withContext(Dispatchers.IO) {
        Category.entries.associateWith { cat -> category(cat).let { files -> files.size to files.sumOf { it.size } } }
    }

    suspend fun recent(limit: Int = 12): List<FileItem> = withContext(Dispatchers.IO) {
        index().asSequence()
            .filter { it.kind == FileKind.IMAGE || it.kind == FileKind.VIDEO || it.kind == FileKind.DOCUMENT }
            .sortedByDescending { it.modified }
            .take(limit)
            .toList()
    }

    suspend fun search(query: String): List<FileItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val files = index().filter { it.name.contains(q, ignoreCase = true) }
        // Folders aren't in the file index; walk folder names (cheap: directories only).
        val folders = File(root).walkTopDown()
            .onEnter { !it.name.startsWith(".") && !isPrivate(it.absolutePath) }
            .filter { it.isDirectory && it.absolutePath != root && it.name.contains(q, ignoreCase = true) }
            .take(50)
            .map { it.toItem(countChildren = true, showHidden = false) }
            .toList()
        (folders + files.sortedByDescending { it.modified }).take(300)
    }

    suspend fun files(paths: Collection<String>): List<FileItem> = withContext(Dispatchers.IO) {
        paths.map { File(it) }.filter { it.exists() }.map { it.toItem(countChildren = true, showHidden = true) }
    }

    // ---- Clean ---------------------------------------------------------------------------------

    suspend fun analyse(): CleanReport = withContext(Dispatchers.IO) {
        val all = index()
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val dl = downloadsDir.absolutePath
        val large = all.filter { it.size >= LARGE_FILE }.sortedByDescending { it.size }
        val oldDownloads = all.filter { it.path.startsWith("$dl/") && now - it.modified > 90 * day }
            .sortedBy { it.modified }
        val screenshots = all.filter {
            it.kind == FileKind.IMAGE && it.path.contains("/Screenshots/", ignoreCase = true) && now - it.modified > 30 * day
        }.sortedBy { it.modified }
        val duplicates = findDuplicates(all.filter { it.size in 1..MAX_HASHED }, ::hash)
        CleanReport(
            junk = junk(),
            duplicates = duplicates,
            large = large,
            oldDownloads = oldDownloads,
            oldScreenshots = screenshots,
        )
    }

    /** Leftover thumbnail caches, empty folders and this app's own cache. */
    private fun junk(): List<FileItem> {
        val out = ArrayList<FileItem>()
        File(root).walkTopDown()
            .onEnter { !isPrivate(it.absolutePath) }
            .maxDepth(6)
            .forEach { f ->
                if (!f.isDirectory || f.absolutePath == root) return@forEach
                when {
                    f.name == ".thumbnails" -> out += f.toItem(countChildren = false, showHidden = true).copy(size = dirSize(f))
                    f.listFiles()?.isEmpty() == true && !f.name.startsWith(".") && f.parentFile?.absolutePath != root ->
                        out += f.toItem(countChildren = true, showHidden = true)
                }
            }
        context.cacheDir.takeIf { dirSize(it) > 0 }?.let {
            out += FileItem(it.absolutePath, "CentiFiles cache", true, dirSize(it), it.lastModified(), childCount = -1)
        }
        return out
    }

    private fun dirSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun hash(item: FileItem): String? = runCatching {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(item.path).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    // ---- Operations ----------------------------------------------------------------------------

    suspend fun createFolder(parent: String, name: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(isValidName(name)) { "That name isn't allowed" }
            val dir = File(parent, name)
            require(!dir.exists()) { "\"$name\" already exists" }
            check(dir.mkdirs()) { "Couldn't create the folder" }
            dir.absolutePath
        }
    }

    suspend fun rename(path: String, newName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(isValidName(newName)) { "That name isn't allowed" }
            val from = File(path)
            val to = File(from.parentFile, newName)
            require(!to.exists()) { "\"$newName\" already exists" }
            check(from.renameTo(to)) { "Couldn't rename" }
            changed(listOf(from, to))
            to.absolutePath
        }
    }

    suspend fun delete(paths: Collection<String>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var deleted = 0
            val files = paths.map { File(it) }
            files.forEach { f ->
                val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
                if (ok) deleted++
            }
            changed(files)
            check(deleted > 0 || paths.isEmpty()) { "Couldn't delete" }
            deleted
        }
    }

    /** Copies (or moves) [paths] into [destDir]. Name clashes get " (1)" style names, never overwrite. */
    suspend fun transfer(paths: Collection<String>, destDir: String, move: Boolean, progress: Progress): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dest = File(destDir)
                require(dest.isDirectory) { "Destination isn't a folder" }
                val sources = paths.map { File(it) }.filter { it.exists() }
                sources.forEach { src ->
                    require(!(src.isDirectory && dest.canonicalPath.startsWith(src.canonicalPath))) {
                        "Can't put \"${src.name}\" inside itself"
                    }
                }
                val total = sources.sumOf { if (it.isDirectory) dirSize(it) else it.length() }
                var done = 0L
                val touched = ArrayList<File>()
                sources.forEach { src ->
                    val existing = dest.list()?.toSet() ?: emptySet()
                    val target = File(dest, uniqueName(src.name, existing))
                    if (move && src.renameTo(target)) {
                        done += if (target.isDirectory) dirSize(target) else target.length()
                        progress.onProgress(done, total)
                    } else {
                        copyRecursively(src, target) { n -> done += n; progress.onProgress(done, total) }
                        if (move) src.deleteRecursively()
                    }
                    touched += src
                    touched += target
                }
                changed(touched)
                sources.size
            }
        }

    private fun copyRecursively(src: File, target: File, onBytes: (Long) -> Unit) {
        if (src.isDirectory) {
            check(target.mkdirs() || target.isDirectory) { "Couldn't create ${target.name}" }
            src.listFiles()?.forEach { copyRecursively(it, File(target, it.name), onBytes) }
        } else {
            src.inputStream().use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        onBytes(n.toLong())
                    }
                }
            }
            target.setLastModified(src.lastModified())
        }
    }

    /** Tells MediaStore about changes so galleries and players stay in sync, and refreshes the index. */
    private fun changed(files: List<File>) {
        index = null
        val paths = files.flatMap { f ->
            if (f.isDirectory) f.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toList() else listOf(f.absolutePath)
        }.distinct()
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
    }

    private fun File.toItem(countChildren: Boolean, showHidden: Boolean): FileItem {
        val dir = isDirectory
        return FileItem(
            path = absolutePath,
            name = name,
            isDirectory = dir,
            size = if (dir) 0 else length(),
            modified = lastModified(),
            childCount = if (dir && countChildren) {
                (list()?.count { showHidden || !it.startsWith(".") } ?: 0)
            } else -1,
        )
    }

    companion object {
        const val LARGE_FILE = 100L * 1000 * 1000
        private const val MAX_HASHED = 512L * 1000 * 1000

        fun hasAllFilesAccess(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
    }
}
