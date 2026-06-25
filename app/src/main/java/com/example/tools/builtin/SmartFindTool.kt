package com.example.tools.builtin

import com.example.service.ExaContents
import com.example.service.ExaFilters
import com.example.service.ExaSearchClient
import com.example.service.ExaSearchRequest
import com.example.service.Parameters
import com.example.service.PropertySchema
import com.example.tools.AndroidPathResolver
import com.example.tools.BaseTool
import com.example.tools.PermissionManager
import com.example.tools.PermissionType
import com.example.tools.RecursiveFileFinder
import com.example.tools.RiskLevel
import com.example.tools.ToolResult
import java.util.*

class SmartFindTool : BaseTool {
    override val name = "find"
    override val description = "Personalised file finder that understands natural language descriptions. Uses Exa neural search API to find files, documents, and information across the web and your device. Examples: 'budget pdfs from last week', 'React documentation', 'machine learning papers from arxiv', 'code snippets for sorting algorithms'"
    override val riskLevel = RiskLevel.SAFE

    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "description" to PropertySchema(
                type = "STRING",
                description = "What you're looking for in natural language. Example: 'pdf invoices from this month' or 'React hooks tutorial' or 'API documentation for Exa search'"
            ),
            "limit" to PropertySchema(
                type = "INTEGER",
                description = "Maximum results to show (default: 10, max: 25)"
            ),
            "type" to PropertySchema(
                type = "STRING",
                description = "Search scope: 'local' (device only), 'web' (internet only), 'both' (default: 'both')"
            ),
            "date_after" to PropertySchema(
                type = "STRING",
                description = "Only results after this date (YYYY-MM-DD)"
            ),
            "date_before" to PropertySchema(
                type = "STRING",
                description = "Only results before this date (YYYY-MM-DD)"
            ),
            "sites" to PropertySchema(
                type = "STRING",
                description = "Comma-separated sites to search within (e.g. 'github.com,stackoverflow.com')"
            )
        ),
        required = listOf("description")
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val description = (args["description"] as? String)
            ?: return ToolResult.error("Tell me what you're looking for!")

        val limit = minOf((args["limit"] as? Number)?.toInt() ?: 10, 25)
        val searchType = (args["type"] as? String) ?: "both"
        val dateAfter = args["date_after"] as? String
        val dateBefore = args["date_before"] as? String
        val sites = args["sites"] as? String

        val results = mutableListOf<SearchResult>()

        // 1. Local device search
        if (searchType == "local" || searchType == "both") {
            val ctx = AndroidPathResolver.getContext()
            if (ctx != null) {
                try {
                    PermissionManager.ensurePermissions(
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                            PermissionType.MEDIA_READ else PermissionType.STORAGE_READ
                    )
                } catch (_: Exception) {}
            }

            val localFiles = RecursiveFileFinder.findByVibe(description, limit)
            for (f in localFiles) {
                results.add(SearchResult(
                    title = f.name,
                    url = f.path,
                    snippet = "${f.category} | ${f.sizeFormatted} | ${f.lastModifiedFormatted}",
                    source = "device",
                    summary = null
                ))
            }
        }

        // 2. Exa web search
        if (searchType == "web" || searchType == "both") {
            try {
                val exaQuery = buildExaQuery(description, dateAfter, dateBefore, sites)
                val response = ExaSearchClient.api.search(
                    ExaSearchClient.API_KEY,
                    exaQuery
                )

                response.results?.forEach { r ->
                    results.add(SearchResult(
                        title = r.title ?: "Untitled",
                        url = r.url ?: "N/A",
                        snippet = r.highlights?.firstOrNull() ?: r.text?.take(300),
                        source = "web",
                        summary = r.summary
                    ))
                }
            } catch (e: Exception) {
                // Exa search failed - local results still available
            }
        }

        // 3. Format results
        if (results.isEmpty()) {
            return ToolResult.success(
                buildString {
                    appendLine("No results found for: \"$description\"")
                    appendLine()
                    appendLine("Tips:")
                    appendLine("  - Try broader terms")
                    appendLine("  - Use 'web' scope for internet results")
                    appendLine("  - Add date range: date_after, date_before")
                    appendLine("  - Specify sites: sites='github.com,stackoverflow.com'")
                }
            )
        }

        val output = formatResults(description, results)
        val metadata = mapOf(
            "count" to results.size,
            "query" to description,
            "local" to results.count { it.source == "device" },
            "web" to results.count { it.source == "web" }
        )

        return ToolResult.success(output, metadata)
    }

    private fun buildExaQuery(
        description: String, dateAfter: String?, dateBefore: String?, sites: String?
    ): ExaSearchRequest {
        val filters = ExaFilters(
            dateAfter = dateAfter,
            dateBefore = dateBefore,
            includeDomains = sites?.split(",")?.map { it.trim() }
        )

        return ExaSearchRequest(
            query = description,
            type = "auto",
            numResults = 15,
            contents = ExaContents(text = true, highlights = true, summary = true),
            filters = filters
        )
    }

    private fun formatResults(description: String, results: List<SearchResult>): String {
        return buildString {
            appendLine("Results for: \"$description\"")
            appendLine("Found ${results.size} results (${results.count { it.source == "device" }} local, ${results.count { it.source == "web" }} web):")
            appendLine()

            val grouped = results.groupBy { it.source }
            for ((source, sourceResults) in grouped) {
                val icon = if (source == "device") "Device" else "Web"
                appendLine("$icon (${sourceResults.size})")
                for ((i, r) in sourceResults.withIndex()) {
                    appendLine("  ${i + 1}. ${r.title}")
                    appendLine("     ${r.url}")
                    if (!r.snippet.isNullOrBlank()) {
                        appendLine("     ${r.snippet.take(200)}")
                    }
                    if (!r.summary.isNullOrBlank()) {
                        appendLine("     Summary: ${r.summary.take(200)}")
                    }
                    appendLine()
                }
            }
        }
    }

    private data class SearchResult(
        val title: String,
        val url: String,
        val snippet: String?,
        val source: String,  // "device" | "web"
        val summary: String?
    )
}
