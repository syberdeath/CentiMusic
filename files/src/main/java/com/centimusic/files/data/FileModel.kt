package com.centimusic.files.data

import java.util.Locale

/** Pure Kotlin model and helpers (no Android imports), so they're covered by JVM unit tests. */

enum class FileKind { FOLDER, IMAGE, VIDEO, AUDIO, DOCUMENT, APK, ARCHIVE, OTHER }

data class FileItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long,
    /** Number of entries for folders, -1 when unknown. */
    val childCount: Int = -1,
) {
    val extension: String get() = extensionOf(name)
    val kind: FileKind get() = if (isDirectory) FileKind.FOLDER else kindOf(name)
    val isHidden: Boolean get() = name.startsWith(".")
    val mimeType: String get() = mimeOf(name)
}

enum class SortField { NAME, DATE, SIZE, TYPE }

data class SortOrder(val field: SortField = SortField.NAME, val ascending: Boolean = true)

enum class ViewMode { LIST, GRID }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Browse categories, like the Files app's Browse tab. */
enum class Category(val label: String) {
    DOWNLOADS("Downloads"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    APPS("Apps"),
}

private val imageExt = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "dng", "svg")
private val videoExt = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts", "wmv", "flv")
private val audioExt = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "amr", "mid", "midi")
private val docExt = setOf(
    "pdf", "doc", "docx", "odt", "rtf", "txt", "md", "xls", "xlsx", "ods", "csv", "ppt", "pptx", "odp",
    "epub", "json", "xml", "html", "htm", "log",
)
private val archiveExt = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")

fun extensionOf(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot <= 0 || dot == name.length - 1) "" else name.substring(dot + 1).lowercase(Locale.ROOT)
}

fun kindOf(name: String): FileKind = when (extensionOf(name)) {
    in imageExt -> FileKind.IMAGE
    in videoExt -> FileKind.VIDEO
    in audioExt -> FileKind.AUDIO
    in docExt -> FileKind.DOCUMENT
    in archiveExt -> FileKind.ARCHIVE
    "apk", "apks", "xapk" -> FileKind.APK
    else -> FileKind.OTHER
}

private val mimeOverrides = mapOf(
    "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif", "webp" to "image/webp",
    "heic" to "image/heic", "heif" to "image/heif", "bmp" to "image/bmp", "svg" to "image/svg+xml", "avif" to "image/avif",
    "mp4" to "video/mp4", "mkv" to "video/x-matroska", "webm" to "video/webm", "3gp" to "video/3gpp", "mov" to "video/quicktime",
    "avi" to "video/x-msvideo", "m4v" to "video/mp4",
    "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac", "flac" to "audio/flac", "wav" to "audio/wav",
    "ogg" to "audio/ogg", "opus" to "audio/opus", "amr" to "audio/amr", "mid" to "audio/midi", "midi" to "audio/midi",
    "pdf" to "application/pdf", "txt" to "text/plain", "md" to "text/markdown", "csv" to "text/csv", "log" to "text/plain",
    "html" to "text/html", "htm" to "text/html", "json" to "application/json", "xml" to "text/xml", "rtf" to "application/rtf",
    "doc" to "application/msword", "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "xls" to "application/vnd.ms-excel", "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "ppt" to "application/vnd.ms-powerpoint", "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "odt" to "application/vnd.oasis.opendocument.text", "ods" to "application/vnd.oasis.opendocument.spreadsheet",
    "odp" to "application/vnd.oasis.opendocument.presentation", "epub" to "application/epub+zip",
    "zip" to "application/zip", "rar" to "application/vnd.rar", "7z" to "application/x-7z-compressed",
    "tar" to "application/x-tar", "gz" to "application/gzip", "tgz" to "application/gzip",
    "apk" to "application/vnd.android.package-archive",
)

fun mimeOf(name: String): String = mimeOverrides[extensionOf(name)] ?: "*/*"

fun Category.matches(item: FileItem): Boolean = when (this) {
    Category.DOWNLOADS -> true
    Category.IMAGES -> item.kind == FileKind.IMAGE
    Category.VIDEOS -> item.kind == FileKind.VIDEO
    Category.AUDIO -> item.kind == FileKind.AUDIO
    Category.DOCUMENTS -> item.kind == FileKind.DOCUMENT
    Category.APPS -> item.kind == FileKind.APK
}

/** Folders first (like the Files app), then by the chosen field; names compare case-insensitively. */
fun List<FileItem>.sortFiles(order: SortOrder): List<FileItem> {
    val byName = compareBy<FileItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
    val field: Comparator<FileItem> = when (order.field) {
        SortField.NAME -> byName
        SortField.DATE -> compareBy<FileItem> { it.modified }.then(byName)
        SortField.SIZE -> compareBy<FileItem> { if (it.isDirectory) it.childCount.toLong() else it.size }.then(byName)
        SortField.TYPE -> compareBy<FileItem> { it.extension }.then(byName)
    }
    val directed = if (order.ascending) field else field.reversed()
    return sortedWith(compareBy<FileItem> { !it.isDirectory }.then(directed))
}

/** "0 B", "980 B", "1.2 KB", "34 MB", "1.56 GB" — decimal units, as Android's own storage screens use. */
fun formatSize(bytes: Long): String {
    if (bytes < 1000) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes / 1000.0
    var unit = 0
    while (value >= 1000 && unit < units.lastIndex) {
        value /= 1000
        unit++
    }
    val text = when {
        value >= 100 -> String.format(Locale.ROOT, "%.0f", value)
        value >= 10 -> String.format(Locale.ROOT, "%.1f", value)
        else -> String.format(Locale.ROOT, "%.2f", value)
    }.let { if ('.' in it) it.trimEnd('0').trimEnd('.') else it }
    return "$text ${units[unit]}"
}

/**
 * Groups files that are byte-for-byte duplicates. Files are first bucketed by size (cheap); only
 * same-size files are hashed. Each returned group has two or more files, newest first, so the
 * oldest copy is treated as the original.
 */
fun findDuplicates(files: List<FileItem>, hash: (FileItem) -> String?): List<List<FileItem>> =
    files.asSequence()
        .filter { !it.isDirectory && it.size > 0 }
        .groupBy { it.size }
        .values
        .filter { it.size > 1 }
        .flatMap { sameSize -> sameSize.groupBy { hash(it) }.filterKeys { it != null }.values }
        .filter { it.size > 1 }
        .map { group -> group.sortedByDescending { it.modified } }
        .sortedByDescending { group -> group.first().size * (group.size - 1) }

/** Breadcrumb segments for [path] relative to [root]: ("Internal storage", root), ("DCIM", root/DCIM)… */
fun breadcrumbs(path: String, root: String, rootLabel: String): List<Pair<String, String>> {
    val crumbs = mutableListOf(rootLabel to root)
    if (!path.startsWith(root)) return listOf(path.substringAfterLast('/') to path)
    var current = root
    path.removePrefix(root).split('/').filter { it.isNotEmpty() }.forEach { segment ->
        current = "$current/$segment"
        crumbs += segment to current
    }
    return crumbs
}

/** A name that doesn't collide with [existing]: "photo.jpg" → "photo (1).jpg". */
fun uniqueName(name: String, existing: Set<String>): String {
    if (name !in existing) return name
    val ext = extensionOf(name)
    val base = if (ext.isEmpty()) name else name.removeSuffix(".${name.substringAfterLast('.')}")
    val suffix = if (ext.isEmpty()) "" else ".${name.substringAfterLast('.')}"
    var n = 1
    while ("$base ($n)$suffix" in existing) n++
    return "$base ($n)$suffix"
}

/** Whether [name] is a valid single file name. */
fun isValidName(name: String): Boolean =
    name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\u0000' } && name.length <= 255
