package com.example.tools

import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class SearchParams(
    val namePattern: String? = null,
    val extensions: Set<String>? = null,
    val minSize: Long? = null,
    val maxSize: Long? = null,
    val dateAfter: Long? = null,
    val dateBefore: Long? = null,
    val maxDepth: Int = 50,
    val limit: Int = 30,
    val preferDirs: Set<String>? = null
)

data class FoundFile(
    val name: String,
    val path: String,
    val extension: String,
    val size: Long,
    val sizeFormatted: String,
    val lastModified: Long,
    val lastModifiedFormatted: String,
    val directory: String,
    val isMedia: Boolean,
    val category: String
)

object RecursiveFileFinder {

    private val MEDIA_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif",
        "mp4", "mkv", "3gp", "webm", "avi", "mov", "flv",
        "mp3", "wav", "m4a", "flac", "ogg", "aac", "wma"
    )

    private val DOCUMENT_EXTENSIONS = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "txt", "md", "json", "xml", "csv", "log", "rtf",
        "html", "htm", "css", "js", "ts", "kt", "java", "py"
    )

    private val SKIP_DIRS = setOf(
        "Android", ".thumbnails", ".cache", "cache", ".trash",
        "LOST.DIR", ".trashed", ".trash-1000", ".stfolder",
        ".stversions", ".tmp", "temp", "Music", "Alarms",
        "Notifications", "Ringtones", "Podcasts", "Audiobooks"
    )

    fun findAllRoots(): List<File> {
        val roots = mutableListOf<File>()
        val sdcard = try {
            Environment.getExternalStorageDirectory()
        } catch (_: Exception) { null }

        if (sdcard != null && sdcard.exists()) {
            roots.add(sdcard)
            roots.addAll(
                listOf(
                    File(sdcard, "Download"), File(sdcard, "Documents"),
                    File(sdcard, "DCIM"), File(sdcard, "Pictures"),
                    File(sdcard, "Music"), File(sdcard, "Movies"),
                    File(sdcard, "Podcasts"), File(sdcard, "Audiobooks"),
                    File(sdcard, "Books"), File(sdcard, "Projects"),
                    File(sdcard, "Work"), File(sdcard, "Backups"),
                    File(sdcard, "Notes")
                ).filter { it.exists() }
            )
        }

        for (path in arrayOf("/storage/emulated/0", "/sdcard")) {
            val dir = File(path)
            if (dir.exists() && dir !in roots) roots.add(dir)
        }

        val appDir = AndroidPathResolver.getAppExternalDir()
        if (appDir != null && appDir.exists() && appDir !in roots) roots.add(appDir)
        val appFiles = AndroidPathResolver.getAppFilesDir()
        if (appFiles != null && appFiles.exists() && appFiles !in roots) roots.add(appFiles)

        return roots.distinct()
    }

    fun findFiles(params: SearchParams): List<FoundFile> {
        val roots = findAllRoots()
        val results = mutableListOf<FoundFile>()
        val visited = mutableSetOf<String>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        fun shouldSkipDir(dir: File): Boolean {
            val name = dir.name
            return name in SKIP_DIRS || name.startsWith(".")
        }

        fun matchesExtension(name: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase()
            if (params.extensions == null) return true
            return ext in params.extensions
        }

        fun matchesName(name: String): Boolean {
            val pattern = params.namePattern ?: return true
            val lower = name.lowercase()
            val pat = pattern.lowercase()
            if (lower.contains(pat)) return true
            val words = pat.split(" ", "-", "_")
            return words.size > 1 && words.all { it in lower }
        }

        fun matchesSize(size: Long): Boolean {
            if (params.minSize != null && size < params.minSize) return false
            if (params.maxSize != null && size > params.maxSize) return false
            return true
        }

        fun matchesDate(modified: Long): Boolean {
            if (params.dateAfter != null && modified < params.dateAfter) return false
            if (params.dateBefore != null && modified > params.dateBefore) return false
            return true
        }

        fun walkDir(dir: File, depth: Int) {
            if (depth > params.maxDepth || results.size >= params.limit) return
            val canonical = try { dir.canonicalPath } catch (_: Exception) { dir.absolutePath }
            if (canonical in visited) return
            visited.add(canonical)
            if (shouldSkipDir(dir) && depth > 0) return

            val children = try { dir.listFiles() } catch (_: Exception) { null } ?: return
            for (child in children) {
                if (results.size >= params.limit) break
                try {
                    if (child.isDirectory) {
                        walkDir(child, depth + 1)
                    } else if (child.isFile) {
                        val name = child.name
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (!matchesExtension(name)) continue
                        if (!matchesName(name)) continue
                        if (!matchesSize(child.length())) continue
                        if (!matchesDate(child.lastModified())) continue

                        val category = when {
                            ext in MEDIA_EXTENSIONS -> when {
                                ext in setOf("jpg","jpeg","png","gif","webp","bmp","heic","heif") -> "Image"
                                ext in setOf("mp4","mkv","3gp","webm","avi","mov","flv") -> "Video"
                                else -> "Audio"
                            }
                            ext in DOCUMENT_EXTENSIONS -> "Document"
                            else -> "Other"
                        }

                        results.add(FoundFile(
                            name = name, path = child.absolutePath, extension = ext,
                            size = child.length(), sizeFormatted = formatSize(child.length()),
                            lastModified = child.lastModified(),
                            lastModifiedFormatted = dateFormat.format(Date(child.lastModified())),
                            directory = child.parentFile?.name ?: "Unknown",
                            isMedia = ext in MEDIA_EXTENSIONS, category = category
                        ))
                    }
                } catch (_: Exception) {}
            }
        }

        for (root in roots) {
            if (results.size >= params.limit) break
            try { walkDir(root, 0) } catch (_: Exception) {}
        }
        return results
    }

    fun findFilesByExtension(extensions: Set<String>, limit: Int = 30): List<FoundFile>
        = findFiles(SearchParams(extensions = extensions, limit = limit))

    fun findFilesByName(pattern: String, extensions: Set<String>? = null, limit: Int = 30): List<FoundFile>
        = findFiles(SearchParams(namePattern = pattern, extensions = extensions, limit = limit))

    fun findByVibe(description: String, limit: Int = 30): List<FoundFile> {
        val params = parseVibeDescription(description, limit)
        return findFiles(params)
    }

    fun formatResults(files: List<FoundFile>, header: String): String {
        if (files.isEmpty()) return "No matching files found."
        return buildString {
            appendLine("$header (${files.size}):")
            appendLine()
            files.forEachIndexed { i, f ->
                appendLine("${i + 1}. [${f.category}] ${f.name}")
                appendLine("   Path: ${f.path}")
                appendLine("   Size: ${f.sizeFormatted}  |  Modified: ${f.lastModifiedFormatted}")
                if (i < files.size - 1) appendLine()
            }
        }
    }

    fun parseVibeDescription(desc: String, limit: Int = 30): SearchParams {
        val lower = desc.lowercase(Locale.ROOT)
        return SearchParams(
            namePattern = extractNamePattern(lower),
            extensions = extractExtensions(lower),
            dateAfter = extractDateAfter(lower),
            dateBefore = extractDateBefore(lower),
            limit = limit,
            preferDirs = extractPreferredDirs(lower)
        )
    }

    private fun extractNamePattern(text: String): String? {
        val stopWords = setOf(
            "find","search","look","get","show","give","need","want","where",
            "is","are","the","a","an","some","any","all","those","these",
            "that","this","my","me","for","from","with","about",
            "file","files","document","documents","photo","photos",
            "picture","pictures","video","videos","audio","music","song",
            "pdf","pdfs","recent","last","this","old","new","large","small",
            "big","downloaded","screenshot","screenshots","folder","directory",
            "path","location","device","storage","day","days","week","weeks",
            "month","months","year","years","today","yesterday","tomorrow",
            "home","download","downloads","documents","document",
            "dcim","pictures","camera","screenshots","screen","shots",
            "music","movies","videos","podcasts","android","data","obb",
            "backup","backups","please","could","can","would","will","do",
            "know","tell","around","here","there","anywhere"
        )
        val extPatterns = setOf("pdf","txt","md","json","xml","csv","doc","docx",
            "xls","xlsx","ppt","pptx","jpg","jpeg","png","gif","webp","bmp",
            "mp4","mp3","wav","m4a","flac","ogg","html","css","js","kt","java","py")

        val words = text.split(" ", "\t", "\n", ",", ".", "!", "?", ";", ":")
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() && it.length > 1 && it !in stopWords }

        val meaningful = words.filter { it.length > 2 || it in extPatterns }
        if (meaningful.isEmpty()) return null

        val scored = meaningful.associateWith { w ->
            (if (w.length > 5) 2 else 0) +
            (if (w.all { it.isLetter() }) 1 else 0) +
            (if (w in extPatterns) -1 else 0)
        }
        return scored.maxByOrNull { it.value }?.key
    }

    private fun extractExtensions(text: String): Set<String>? {
        val extMap = mapOf(
            "pdf" to setOf("pdf"),
            "document" to setOf("doc", "docx", "txt", "md"),
            "documents" to setOf("doc", "docx", "txt", "md"),
            "word" to setOf("doc", "docx"),
            "excel" to setOf("xls", "xlsx"),
            "spreadsheet" to setOf("xls", "xlsx", "csv"),
            "powerpoint" to setOf("ppt", "pptx"),
            "presentation" to setOf("ppt", "pptx"),
            "text" to setOf("txt", "md"),
            "json" to setOf("json"), "csv" to setOf("csv"), "xml" to setOf("xml"),
            "image" to setOf("jpg", "jpeg", "png", "gif", "webp", "bmp"),
            "images" to setOf("jpg", "jpeg", "png", "gif", "webp", "bmp"),
            "photo" to setOf("jpg", "jpeg", "png", "gif", "webp"),
            "photos" to setOf("jpg", "jpeg", "png", "gif", "webp"),
            "picture" to setOf("jpg", "jpeg", "png", "gif", "webp"),
            "pictures" to setOf("jpg", "jpeg", "png", "gif", "webp"),
            "screenshot" to setOf("png", "jpg"),
            "screenshots" to setOf("png", "jpg"),
            "video" to setOf("mp4", "mkv", "3gp", "webm", "avi", "mov", "flv"),
            "videos" to setOf("mp4", "mkv", "3gp", "webm", "avi", "mov", "flv"),
            "audio" to setOf("mp3", "wav", "m4a", "flac", "ogg", "aac"),
            "music" to setOf("mp3", "wav", "m4a", "flac", "ogg", "aac"),
            "song" to setOf("mp3", "m4a", "flac", "ogg", "wav"),
            "code" to setOf("kt", "java", "py", "js", "ts", "html", "css", "xml", "json"),
            "markdown" to setOf("md"), "html" to setOf("html", "htm"),
            "log" to setOf("log", "txt"),
        )
        val found = mutableSetOf<String>()
        for ((key, exts) in extMap) {
            if (key in text) found.addAll(exts)
        }
        return found.ifEmpty { null }
    }

    private fun extractDateAfter(text: String): Long? {
        val cal = Calendar.getInstance()
        val now = System.currentTimeMillis()
        return when {
            "today" in text -> { cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis }
            "yesterday" in text -> { cal.add(Calendar.DAY_OF_YEAR, -1); cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis }
            "last 24 hours" in text || "past 24 hours" in text || "last day" in text -> now - 24*60*60*1000L
            "last week" in text || "past week" in text -> now - 7*24*60*60*1000L
            "last month" in text || "past month" in text -> now - 30*24*60*60*1000L
            "last 2 days" in text || "past 2 days" in text || "last two days" in text -> now - 2*24*60*60*1000L
            "last 3 days" in text || "past 3 days" in text || "last three days" in text -> now - 3*24*60*60*1000L
            "last 7 days" in text || "past 7 days" in text || "last seven days" in text -> now - 7*24*60*60*1000L
            "this week" in text -> { cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek); cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis }
            "this month" in text -> { cal.set(Calendar.DAY_OF_MONTH, 1); cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis }
            else -> null
        }
    }

    private fun extractDateBefore(text: String): Long? {
        val cal = Calendar.getInstance()
        return when {
            "today" in text -> cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
            "yesterday" in text -> cal.apply { add(Calendar.DAY_OF_YEAR, -1); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
            else -> null
        }
    }

    private fun extractPreferredDirs(text: String): Set<String>? {
        val dirMap = mapOf(
            "download" to "Download", "downloads" to "Download",
            "document" to "Documents", "documents" to "Documents",
            "dcim" to "DCIM", "camera" to "DCIM",
            "picture" to "Pictures", "pictures" to "Pictures",
            "screenshot" to "Screenshots", "screenshots" to "Screenshots",
            "screen" to "Screenshots",
            "music" to "Music",
            "movie" to "Movies", "movies" to "Movies",
            "video" to "Movies", "videos" to "Movies",
            "home" to "",
        )
        val found = mutableSetOf<String>()
        for ((key, dir) in dirMap) { if (key in text) found.add(dir) }
        return found.ifEmpty { null }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024*1024*1024 -> String.format("%.2f GB", bytes / (1024.0*1024.0*1024.0))
        bytes >= 1024*1024 -> String.format("%.2f MB", bytes / (1024.0*1024.0))
        bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
        else -> "$bytes Bytes"
    }
}
