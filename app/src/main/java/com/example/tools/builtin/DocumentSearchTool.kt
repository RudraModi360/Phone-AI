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

class DocumentSearchTool : BaseTool {
    override val name = "document_search"
    override val description = "Search for documents (PDFs, Office files, text files, code files) in device storage using MediaStore + deep recursive global search + Exa Neural Search."
    override val riskLevel = RiskLevel.SAFE

    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "operation" to PropertySchema(
                type = "STRING",
                description = "Operation: 'recent_documents', 'search_by_name', 'search_by_type', 'global_search', 'exa_search'"
            ),
            "query" to PropertySchema(
                type = "STRING",
                description = "Search query or natural language description for global_search / exa_search"
            ),
            "file_type" to PropertySchema(
                type = "STRING",
                description = "Filter by type: 'pdf', 'word', 'excel', 'powerpoint', 'text', 'code', 'all'"
            ),
            "limit" to PropertySchema(
                type = "INTEGER",
                description = "Maximum number of documents to return (default: 15)"
            )
        ),
        required = emptyList()
    )

    companion object {
        private val FILE_TYPE_EXTENSIONS = mapOf(
            "pdf" to setOf("pdf"),
            "word" to setOf("doc", "docx"),
            "excel" to setOf("xls", "xlsx", "csv"),
            "powerpoint" to setOf("ppt", "pptx"),
            "text" to setOf("txt", "md", "json", "xml", "csv", "log"),
            "code" to setOf("kt", "java", "py", "js", "ts", "html", "css", "xml", "json", "gradle", "kts"),
            "all" to setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "json", "xml", "csv", "log", "kt", "java", "py", "js", "ts", "html", "css", "gradle", "kts")
        )
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val operation = args["operation"] as? String ?: "recent_documents"
        val query = args["query"] as? String
        val fileType = args["file_type"] as? String ?: "all"
        val limit = (args["limit"] as? Number)?.toInt() ?: 15

        val context = AndroidPathResolver.getContext()
            ?: return ToolResult.error("Application context not available. Ensure AndroidPathResolver is initialized.")

        val permissionError = PermissionManager.ensurePermissions(PermissionType.STORAGE_READ)
        if (permissionError != null) {
            return ToolResult.error(permissionError)
        }

        val resolver = context.contentResolver

        return try {
            when (operation) {
                "recent_documents" -> getRecentDocuments(resolver, fileType, limit)
                "search_by_name" -> {
                    if (query.isNullOrBlank()) {
                        return ToolResult.error("Missing required parameter: 'query' for search_by_name operation")
                    }
                    searchByName(resolver, query, fileType, limit)
                }
                "search_by_type" -> {
                    searchByType(resolver, fileType, limit)
                }
                "global_search" -> {
                    if (query.isNullOrBlank()) {
                        return ToolResult.error("Missing required parameter: 'query' for global_search operation")
                    }
                    globalSearch(query, fileType, limit)
                }
                "exa_search" -> {
                    if (query.isNullOrBlank()) {
                        return ToolResult.error("Missing required parameter: 'query' for exa_search operation")
                    }
                    exaDocumentSearch(query, fileType, limit)
                }
                else -> ToolResult.error("Unknown operation: '$operation'. Use: recent_documents, search_by_name, search_by_type, global_search, exa_search")
            }
        } catch (e: SecurityException) {
            ToolResult.error("Permission denied: ${PermissionManager.getPermissionDeniedMessage(context, PermissionType.STORAGE_READ)}")
        } catch (e: Exception) {
            ToolResult.error("Document search failed: ${e.message}")
        }
    }

    private fun searchByName(resolver: ContentResolver, query: String, fileType: String, limit: Int): ToolResult {
        // Try MediaStore search first
        val mediaStoreResults = mutableListOf<Map<String, Any>>()
        try {
            val results = searchDocumentsViaMediaStore(resolver, query, fileType, limit)
            mediaStoreResults.addAll(results)
        } catch (_: Exception) {}

        // Walk deep with RecursiveFileFinder
        val extensions = FILE_TYPE_EXTENSIONS[fileType.lowercase()] ?: FILE_TYPE_EXTENSIONS["all"]!!
        val recursiveResults = RecursiveFileFinder.findFiles(
            SearchParams(
                namePattern = query,
                extensions = extensions,
                limit = limit
            )
        )

        // Merge and deduplicate by path
        val merged = mutableListOf<Map<String, Any>>()
        val paths = mutableSetOf<String>()

        for (doc in mediaStoreResults) {
            val path = doc["path"] as? String ?: continue
            if (path !in paths) {
                paths.add(path)
                merged.add(doc)
            }
        }

        for (f in recursiveResults) {
            if (f.path !in paths) {
                paths.add(f.path)
                merged.add(mapOf(
                    "id" to f.hashCode().toString(),
                    "name" to f.name,
                    "type" to f.category,
                    "mime_type" to "application/octet-stream",
                    "size" to f.size,
                    "size_formatted" to f.sizeFormatted,
                    "date_added" to f.lastModifiedFormatted,
                    "date_modified" to f.lastModifiedFormatted,
                    "path" to f.path
                ))
            }
        }

        val limited = merged.take(limit)
        return formatDocumentResults(limited, "Documents matching '$query'")
    }

    private suspend fun globalSearch(query: String, fileType: String, limit: Int): ToolResult {
        // Try Exa Search first
        val exaRequest = ExaSearchRequest(
            query = "$query $fileType document file",
            type = "auto",
            numResults = minOf(limit, 25),
            contents = ExaContents(text = true, highlights = true, summary = true)
        )

        try {
            val response = ExaSearchClient.api.search(ExaSearchClient.API_KEY, exaRequest)
            val exaResults = response.results

            if (!exaResults.isNullOrEmpty()) {
                val output = buildString {
                    appendLine("Global search for '$query' (via Exa):")
                    appendLine()
                    exaResults.withIndex().forEach { (i, r) ->
                        appendLine("${i + 1}. ${r.title ?: "Untitled"}")
                        appendLine("   URL: ${r.url}")
                        if (!r.summary.isNullOrBlank()) appendLine("   Summary: ${r.summary}")
                        if (!r.highlights.isNullOrEmpty()) {
                            appendLine("   Highlights:")
                            r.highlights.take(2).forEach { appendLine("     - $it") }
                        }
                        appendLine()
                    }
                }
                return ToolResult.success(output)
            }
        } catch (_: Exception) {}

        // Fallback to local search
        val extensions = FILE_TYPE_EXTENSIONS[fileType.lowercase()] ?: FILE_TYPE_EXTENSIONS["all"]!!
        val localFiles = RecursiveFileFinder.findFilesByName(query, extensions, limit)
        val mappedLocal = localFiles.map { f ->
            mapOf(
                "id" to f.hashCode().toString(),
                "name" to f.name,
                "type" to f.category,
                "mime_type" to "application/octet-stream",
                "size" to f.size,
                "size_formatted" to f.sizeFormatted,
                "date_added" to f.lastModifiedFormatted,
                "date_modified" to f.lastModifiedFormatted,
                "path" to f.path
            )
        }
        return formatDocumentResults(mappedLocal, "Global search for '$query'")
    }

    private suspend fun exaDocumentSearch(query: String, fileType: String, limit: Int): ToolResult {
        val exaQuery = "$query $fileType document"  // enhance query with type
        val request = ExaSearchRequest(
            query = exaQuery,
            type = "auto",
            numResults = minOf(limit, 25),
            contents = ExaContents(text = true, highlights = true, summary = true),
            filters = ExaFilters(
                category = "research paper"
            )
        )

        val response = ExaSearchClient.api.search(ExaSearchClient.API_KEY, request)
        val results = response.results ?: return ToolResult.success("No results found.")

        val formatted = results.withIndex().joinToString("\n\n") { (i, r) ->
            "${i + 1}. ${r.title ?: "Untitled"}\n   URL: ${r.url}\n   ${r.summary ?: r.highlights?.firstOrNull() ?: ""}"
        }

        return ToolResult.success("Documents found via Exa Search (${results.size}):\n\n$formatted")
    }

    private fun getRecentDocuments(resolver: ContentResolver, fileType: String, limit: Int): ToolResult {
        val extensions = FILE_TYPE_EXTENSIONS[fileType.lowercase()] ?: FILE_TYPE_EXTENSIONS["all"]!!
        val recursiveResults = RecursiveFileFinder.findFiles(
            SearchParams(
                extensions = extensions,
                limit = limit
            )
        )

        if (recursiveResults.isNotEmpty()) {
            val docs = recursiveResults.map { f ->
                mapOf(
                    "id" to f.hashCode().toString(),
                    "name" to f.name,
                    "type" to f.category,
                    "mime_type" to "application/octet-stream",
                    "size" to f.size,
                    "size_formatted" to f.sizeFormatted,
                    "date_added" to f.lastModifiedFormatted,
                    "date_modified" to f.lastModifiedFormatted,
                    "path" to f.path
                )
            }
            return formatDocumentResults(docs, "Recent Documents ($fileType)")
        }

        // Fallback to media store
        return queryDocumentsLegacy(resolver, null, getMimeTypesForFileType(fileType), limit, "Recent Documents", fileType)
    }

    private fun searchByType(resolver: ContentResolver, fileType: String, limit: Int): ToolResult {
        val extensions = FILE_TYPE_EXTENSIONS[fileType.lowercase()] ?: FILE_TYPE_EXTENSIONS["all"]!!
        val recursiveResults = RecursiveFileFinder.findFiles(
            SearchParams(
                extensions = extensions,
                limit = limit
            )
        )

        if (recursiveResults.isNotEmpty()) {
            val docs = recursiveResults.map { f ->
                mapOf(
                    "id" to f.hashCode().toString(),
                    "name" to f.name,
                    "type" to f.category,
                    "mime_type" to "application/octet-stream",
                    "size" to f.size,
                    "size_formatted" to f.sizeFormatted,
                    "date_added" to f.lastModifiedFormatted,
                    "date_modified" to f.lastModifiedFormatted,
                    "path" to f.path
                )
            }
            return formatDocumentResults(docs, "Documents of type: $fileType")
        }

        // Fallback
        return queryDocumentsLegacy(resolver, null, getMimeTypesForFileType(fileType), limit, "Documents of type: $fileType", fileType)
    }

    private fun searchDocumentsViaMediaStore(
        resolver: ContentResolver,
        nameQuery: String,
        fileType: String,
        limit: Int
    ): List<Map<String, Any>> {
        val documents = mutableListOf<Map<String, Any>>()
        try {
            val mimeTypes = getMimeTypesForFileType(fileType)
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATA
            )

            val mimeSelection = mimeTypes.joinToString(" OR ") {
                "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
            }

            val selection = "(${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?) AND ($mimeSelection)"
            val selectionArgs = arrayOf("%$nameQuery%") + mimeTypes.toTypedArray()
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

            val cursor = resolver.query(uri, projection, selection, selectionArgs, sortOrder)
            cursor?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateAddedIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val dateModifiedIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val pathIdx = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                var count = 0

                while (c.moveToNext() && count < limit) {
                    val id = c.getLong(idIdx)
                    val name = c.getString(nameIdx) ?: "Unknown"
                    val mime = c.getString(mimeIdx) ?: "unknown"
                    val size = c.getLong(sizeIdx)
                    val dateAdded = c.getLong(dateAddedIdx)
                    val dateModified = c.getLong(dateModifiedIdx)
                    val path = if (pathIdx >= 0) c.getString(pathIdx) else null

                    val typeLabel = getDocTypeLabel(mime, name)

                    documents.add(mapOf(
                        "id" to id.toString(),
                        "name" to name,
                        "type" to typeLabel,
                        "mime_type" to mime,
                        "size" to size,
                        "size_formatted" to formatSize(size),
                        "date_added" to dateFormat.format(Date(dateAdded * 1000)),
                        "date_modified" to dateFormat.format(Date(dateModified * 1000)),
                        "path" to (path ?: "N/A")
                    ))
                    count++
                }
            }
        } catch (_: Exception) {}
        return documents
    }

    private fun queryDocumentsLegacy(
        resolver: ContentResolver,
        nameQuery: String?,
        mimeTypes: List<String>,
        limit: Int,
        resultLabel: String,
        fileType: String = "all"
    ): ToolResult {
        val documents = mutableListOf<Map<String, Any>>()
        try {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATA
            )

            val mimeSelection = mimeTypes.joinToString(" OR ") {
                "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
            }

            val selection = if (!nameQuery.isNullOrBlank()) {
                "(${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?) AND ($mimeSelection)"
            } else {
                mimeSelection
            }

            val selectionArgs = if (!nameQuery.isNullOrBlank()) {
                arrayOf("%$nameQuery%") + mimeTypes.toTypedArray()
            } else {
                mimeTypes.toTypedArray()
            }

            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            val cursor = resolver.query(uri, projection, selection, selectionArgs, sortOrder)

            cursor?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateAddedIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val dateModifiedIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val pathIdx = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                var count = 0

                while (c.moveToNext() && count < limit) {
                    val id = c.getLong(idIdx)
                    val name = c.getString(nameIdx) ?: "Unknown"
                    val mime = c.getString(mimeIdx) ?: "unknown"
                    val size = c.getLong(sizeIdx)
                    val dateAdded = c.getLong(dateAddedIdx)
                    val dateModified = c.getLong(dateModifiedIdx)
                    val path = if (pathIdx >= 0) c.getString(pathIdx) else null

                    val typeLabel = getDocTypeLabel(mime, name)

                    documents.add(mapOf(
                        "id" to id.toString(),
                        "name" to name,
                        "type" to typeLabel,
                        "mime_type" to mime,
                        "size" to size,
                        "size_formatted" to formatSize(size),
                        "date_added" to dateFormat.format(Date(dateAdded * 1000)),
                        "date_modified" to dateFormat.format(Date(dateModified * 1000)),
                        "path" to (path ?: "N/A")
                    ))
                    count++
                }
            }
        } catch (_: Exception) {}

        return formatDocumentResults(documents, resultLabel)
    }

    private fun formatDocumentResults(documents: List<Map<String, Any>>, resultLabel: String): ToolResult {
        if (documents.isEmpty()) {
            return ToolResult.success(
                "No matching documents found in storage.",
                mapOf("count" to 0, "documents" to emptyList<Map<String, Any>>())
            )
        }

        val formattedResult = documents.joinToString("\n\n") { doc ->
            "- [${doc["type"]}] ${doc["name"]} (${doc["size_formatted"]})\n  ID: ${doc["id"]}\n  Modified: ${doc["date_modified"]}\n  Path: ${doc["path"]}"
        }

        return ToolResult.success(
            "$resultLabel (${documents.size}):\n\n$formattedResult",
            mapOf("count" to documents.size, "documents" to documents)
        )
    }

    private fun getMimeTypesForFileType(fileType: String): List<String> {
        val pdfMime = listOf("application/pdf")
        val wordMime = listOf(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
        val excelMime = listOf(
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/csv"
        )
        val powerpointMime = listOf(
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )
        val textMime = listOf(
            "text/plain",
            "text/markdown",
            "application/json",
            "application/xml",
            "text/xml"
        )
        val codeMime = listOf(
            "text/javascript",
            "application/javascript",
            "text/css",
            "text/html"
        )

        return when (fileType.lowercase()) {
            "pdf" -> pdfMime
            "word" -> wordMime
            "excel" -> excelMime
            "powerpoint" -> powerpointMime
            "text" -> textMime
            "code" -> codeMime
            else -> pdfMime + wordMime + excelMime + powerpointMime + textMime + codeMime
        }
    }

    private fun getDocTypeLabel(mimeType: String, fileName: String): String {
        return when {
            mimeType.contains("pdf") -> "PDF"
            mimeType.contains("word") || mimeType.contains("msword") -> "Word"
            mimeType.contains("excel") || mimeType.contains("spreadsheet") -> "Excel"
            mimeType.contains("powerpoint") || mimeType.contains("presentation") -> "PowerPoint"
            mimeType == "text/plain" -> "Text"
            mimeType == "text/markdown" -> "Markdown"
            mimeType == "text/csv" -> "CSV"
            mimeType == "application/json" -> "JSON"
            mimeType.contains("xml") -> "XML"
            else -> {
                val ext = fileName.substringAfterLast('.', "").uppercase()
                if (ext.isNotEmpty()) ext else "Document"
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes Bytes"
        }
    }
}
