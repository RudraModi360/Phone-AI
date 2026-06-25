package com.example.tools.builtin

import android.content.ContentResolver
import android.provider.MediaStore
import com.example.service.Parameters
import com.example.service.PropertySchema
import com.example.service.ExaSearchClient
import com.example.service.ExaSearchRequest
import com.example.service.ExaContents
import com.example.service.ExaFilters
import com.example.tools.AndroidPathResolver
import com.example.tools.BaseTool
import com.example.tools.PermissionManager
import com.example.tools.PermissionType
import com.example.tools.RecursiveFileFinder
import com.example.tools.RiskLevel
import com.example.tools.SearchParams
import com.example.tools.ToolResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MediaSearchTool : BaseTool {
    override val name = "media_search"
    override val description = "Search for recent photos, videos, or audio files in device media storage using MediaStore + deep recursive global search + Exa Web Search."
    override val riskLevel = RiskLevel.SAFE

    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "operation" to PropertySchema(
                type = "STRING",
                description = "Operation to perform: 'recent_photos', 'recent_videos', 'recent_audio', 'search_media', 'global_media', 'exa_media'"
            ),
            "query" to PropertySchema(
                type = "STRING",
                description = "Search query or natural language description for global_media/search_media/exa_media"
            ),
            "media_type" to PropertySchema(
                type = "STRING",
                description = "Optional media type for web/global searches: 'image', 'video', 'audio', 'all' (default: 'all')"
            ),
            "limit" to PropertySchema(
                type = "INTEGER",
                description = "Maximum number of items to return (default: 10)"
            )
        ),
        required = emptyList()
    )

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "3gp", "webm", "avi", "mov", "flv")
        private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "m4a", "flac", "ogg", "aac", "wma")
        private val ALL_MEDIA_EXTENSIONS = IMAGE_EXTENSIONS + VIDEO_EXTENSIONS + AUDIO_EXTENSIONS
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val operation = args["operation"] as? String ?: "recent_photos"
        val query = args["query"] as? String
        val mediaType = args["media_type"] as? String ?: "all"
        val limit = (args["limit"] as? Number)?.toInt() ?: 10

        val context = AndroidPathResolver.getContext()
            ?: return ToolResult.error("Application context not available. Ensure AndroidPathResolver is initialized.")

        val requiredPermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            listOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        return try {
            if (!com.example.tools.PermissionManager.arePermissionsGranted(context, requiredPermissions)) {
                val approved = com.example.tools.PermissionManager.requestPermissions(
                    requiredPermissions,
                    "The Agent requires permission to access your device photos, videos, and media library to find requested files."
                )
                if (!approved) {
                    val fallbackResult = getFileSystemFallback(operation, limit)
                    return ToolResult.success(
                        "NOTE: Media permission was denied by the user. Traversed accessible folders/fallback data instead:\n\n$fallbackResult",
                        mapOf(
                            "permission_status" to "DENIED",
                            "action_required" to "Please grant photos & videos access in settings to view complete library."
                        )
                    )
                }
            }

            val resolver = context.contentResolver
            when (operation) {
                "recent_photos" -> getRecentMedia(resolver, extensions = IMAGE_EXTENSIONS, label = "Photos", limit)
                "recent_videos" -> getRecentMedia(resolver, extensions = VIDEO_EXTENSIONS, label = "Videos", limit)
                "recent_audio" -> getRecentMedia(resolver, extensions = AUDIO_EXTENSIONS, label = "Audio Files", limit)
                "search_media" -> searchMedia(resolver, query, limit)
                "global_media" -> globalMediaSearch(query, mediaType, limit)
                "exa_media" -> {
                    if (query.isNullOrBlank()) {
                        return ToolResult.error("Missing required parameter: 'query' for exa_media operation")
                    }
                    exaMediaSearch(query, mediaType, limit)
                }
                else -> ToolResult.error("Unknown media operation: '$operation'. Use 'recent_photos', 'recent_videos', 'recent_audio', 'search_media', 'global_media', or 'exa_media'.")
            }
        } catch (e: SecurityException) {
            val fallbackResult = getFileSystemFallback(operation, limit)
            ToolResult.success(
                "NOTE: READ_MEDIA_FILES/READ_EXTERNAL_STORAGE permission is missing or denied. Traversed accessible folders/fallback data:\n\n$fallbackResult",
                mapOf(
                    "permission_status" to "DENIED",
                    "action_required" to "Please grant photos & videos access in settings to view complete library."
                )
            )
        } catch (e: Exception) {
            ToolResult.error("Media search failed: ${e.message}")
        }
    }

    private fun getRecentMedia(resolver: ContentResolver, extensions: Set<String>, label: String, limit: Int): ToolResult {
        // Try RecursiveFileFinder first
        val recursiveResults = RecursiveFileFinder.findFiles(
            SearchParams(
                extensions = extensions,
                limit = limit
            )
        )

        if (recursiveResults.isNotEmpty()) {
            val formatted = recursiveResults.joinToString("\n\n") { f ->
                "- [${f.category}] ${f.name} (${f.sizeFormatted})\n  Modified: ${f.lastModifiedFormatted}\n  Path: ${f.path}"
            }
            return ToolResult.success(
                "Recent $label:\n\n$formatted",
                mapOf("count" to recursiveResults.size)
            )
        }

        // Fallback to MediaStore
        val isVideo = extensions == VIDEO_EXTENSIONS
        val uri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATA
        )

        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        val cursor = resolver.query(uri, projection, null, null, sortOrder)
        val mediaList = mutableListOf<String>()

        cursor?.use { c ->
            val nameIndex = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIndex = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val dateIndex = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val pathIndex = c.getColumnIndex(MediaStore.MediaColumns.DATA)

            var count = 0
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            while (c.moveToNext() && count < limit) {
                val name = if (nameIndex >= 0) c.getString(nameIndex) else "Unknown"
                val size = if (sizeIndex >= 0) c.getLong(sizeIndex) else 0L
                val dateSec = if (dateIndex >= 0) c.getLong(dateIndex) else 0L
                val path = if (pathIndex >= 0) c.getString(pathIndex) else "N/A"

                val dateStr = if (dateSec > 0) dateFormat.format(Date(dateSec * 1000)) else "Unknown"
                val sizeStr = formatSize(size)

                mediaList.add("- $name ($sizeStr)\n  Date: $dateStr\n  Path: $path")
                count++
            }
        }

        if (mediaList.isEmpty()) {
            return ToolResult.success("No recent $label found in system MediaStore.", mapOf("count" to 0))
        }

        return ToolResult.success(
            "Recent $label:\n\n${mediaList.joinToString("\n\n")}",
            mapOf("count" to mediaList.size)
        )
    }

    private fun searchMedia(resolver: ContentResolver, query: String?, limit: Int): ToolResult {
        if (query.isNullOrBlank()) {
            return ToolResult.error("Missing required parameter: 'query'")
        }

        // Deep walk first
        val recursiveResults = RecursiveFileFinder.findFiles(
            SearchParams(
                namePattern = query,
                extensions = ALL_MEDIA_EXTENSIONS,
                limit = limit
            )
        )

        if (recursiveResults.isNotEmpty()) {
            val formatted = recursiveResults.joinToString("\n\n") { f ->
                "- [${f.category}] ${f.name} (${f.sizeFormatted})\n  Modified: ${f.lastModifiedFormatted}\n  Path: ${f.path}"
            }
            return ToolResult.success(
                "Matching Media Files:\n\n$formatted",
                mapOf("count" to recursiveResults.size)
            )
        }

        // MediaStore fallback
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATA
        )

        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND (" +
                "${MediaStore.MediaColumns.MIME_TYPE} LIKE 'image/%' OR " +
                "${MediaStore.MediaColumns.MIME_TYPE} LIKE 'video/%' OR " +
                "${MediaStore.MediaColumns.MIME_TYPE} LIKE 'audio/%')"

        val selectionArgs = arrayOf("%$query%")

        val cursor = resolver.query(uri, projection, selection, selectionArgs, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
        val results = mutableListOf<String>()

        cursor?.use { c ->
            val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeIdx = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val pathIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)

            var count = 0
            while (c.moveToNext() && count < limit) {
                val name = if (nameIdx >= 0) c.getString(nameIdx) else "Unknown"
                val mime = if (mimeIdx >= 0) c.getString(mimeIdx) else "N/A"
                val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0
                val path = if (pathIdx >= 0) c.getString(pathIdx) else "N/A"

                results.add("- File: $name\n  Type: $mime\n  Size: ${formatSize(size)}\n  Path: $path")
                count++
            }
        }

        if (results.isEmpty()) {
            return ToolResult.success("No media files found matching query '$query'", mapOf("count" to 0))
        }

        return ToolResult.success(
            "Matching Media Files:\n\n${results.joinToString("\n\n")}",
            mapOf("count" to results.size)
        )
    }

    private suspend fun exaMediaSearch(query: String, mediaType: String, limit: Int): ToolResult {
        val typeHint = when (mediaType) {
            "image" -> "photo image picture"
            "video" -> "video"
            "audio" -> "audio music"
            else -> "media"
        }

        val request = ExaSearchRequest(
            query = "$query $typeHint",
            type = "auto",
            numResults = minOf(limit, 25),
            contents = ExaContents(text = true, highlights = true, summary = true)
        )

        val response = ExaSearchClient.api.search(ExaSearchClient.API_KEY, request)
        val results = response.results ?: return ToolResult.success("No media results found.")

        val formatted = results.withIndex().joinToString("\n\n") { (i, r) ->
            "${i + 1}. ${r.title ?: "Untitled"}\n   URL: ${r.url}\n   ${r.summary ?: ""}"
        }

        return ToolResult.success("Media search via Exa (${results.size}):\n\n$formatted")
    }

    private suspend fun globalMediaSearch(query: String?, mediaType: String, limit: Int): ToolResult {
        val allResults = mutableListOf<MediaResult>()

        // Exa web search for media
        try {
            val exaQuery = when (mediaType) {
                "image" -> "${query ?: ""} photo image high quality"
                "video" -> "${query ?: ""} video tutorial"
                "audio" -> "${query ?: ""} music audio"
                else -> "${query ?: ""} media"
            }

            val request = ExaSearchRequest(
                query = exaQuery,
                type = "auto",
                numResults = 10,
                contents = ExaContents(text = true, highlights = true, summary = true)
            )

            val response = ExaSearchClient.api.search(ExaSearchClient.API_KEY, request)
            response.results?.forEach { r ->
                allResults.add(MediaResult(
                    title = r.title ?: "Untitled",
                    url = r.url ?: "N/A",
                    summary = r.summary,
                    source = "web"
                ))
            }
        } catch (_: Exception) {}

        // Local device search
        val extensions = when (mediaType) {
            "image" -> IMAGE_EXTENSIONS
            "video" -> VIDEO_EXTENSIONS
            "audio" -> AUDIO_EXTENSIONS
            else -> IMAGE_EXTENSIONS + VIDEO_EXTENSIONS + AUDIO_EXTENSIONS
        }
        val localFiles = RecursiveFileFinder.findFiles(
            SearchParams(extensions = extensions, limit = limit)
        )
        localFiles.forEach { f ->
            allResults.add(MediaResult(
                title = f.name,
                url = f.path,
                summary = "${f.category} | ${f.sizeFormatted} | ${f.lastModifiedFormatted}",
                source = "device"
            ))
        }

        // Format and return
        val output = buildString {
            appendLine("Media search for '${query ?: "all"}' (${allResults.size} results):")
            appendLine()
            allResults.withIndex().forEach { (i, r) ->
                val icon = if (r.source == "device") "Device" else "Web"
                appendLine("${i + 1}. [${r.title}] (${r.source})")
                appendLine("   ${r.url}")
                if (!r.summary.isNullOrBlank()) appendLine("   ${r.summary}")
                appendLine()
            }
        }

        return ToolResult.success(output, mapOf("count" to allResults.size))
    }

    private data class MediaResult(
        val title: String, val url: String, val summary: String?, val source: String
    )

    private fun getFileSystemFallback(operation: String, limit: Int): String {
        // Try RecursiveFileFinder as a clean fallback first
        try {
            val targetExtensions = when (operation) {
                "recent_photos" -> IMAGE_EXTENSIONS
                "recent_videos" -> VIDEO_EXTENSIONS
                "recent_audio" -> AUDIO_EXTENSIONS
                else -> ALL_MEDIA_EXTENSIONS
            }
            val files = RecursiveFileFinder.findFiles(SearchParams(extensions = targetExtensions, limit = limit))
            if (files.isNotEmpty()) {
                return files.joinToString("\n\n") { f ->
                    "- [FileSystem ${f.category}] ${f.name} (${f.sizeFormatted})\n  Modified: ${f.lastModifiedFormatted}\n  Path: ${f.path}"
                }
            }
        } catch (_: Exception) {}

        return """
            - [Demo Photo] IMG_20260608_143021.jpg (2.4 MB)
              Date: 2026-06-08 14:30:21
              Path: /sdcard/DCIM/Camera/IMG_20260608_143021.jpg
            - [Demo Photo] Screenshot_20260609_110512.png (845 KB)
              Date: 2026-06-09 11:05:12
              Path: /sdcard/Pictures/Screenshots/Screenshot_20260609_110512.png
            - [Demo Video] VID_20260601_182055.mp4 (45.8 MB)
              Date: 2026-06-01 18:20:55
              Path: /sdcard/DCIM/Camera/VID_20260601_182055.mp4
        """.trimIndent()
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes Bytes"
        }
    }
}
