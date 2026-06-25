package com.example.tools.builtin

import com.example.service.ExaAnswerRequest
import com.example.service.ExaContents
import com.example.service.ExaFilters
import com.example.service.ExaSearchClient
import com.example.service.ExaSearchRequest
import com.example.service.Parameters
import com.example.service.PropertySchema
import com.example.tools.BaseTool
import com.example.tools.RiskLevel
import com.example.tools.ToolResult
import java.text.SimpleDateFormat
import java.util.*

class ExaSearchTool : BaseTool {
    override val name = "search"
    override val description = "Neural web search powered by Exa AI. Search the internet for anything using natural language. Understands context, intent, and semantic meaning. Returns relevant web pages with content, highlights, and summaries."
    override val riskLevel = RiskLevel.SAFE

    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "query" to PropertySchema(
                type = "STRING",
                description = "Natural language search query. Be specific for better results."
            ),
            "mode" to PropertySchema(
                type = "STRING",
                description = "Search mode: 'search' (default), 'answer' (RAG grounded answer), 'similar' (find similar to a URL)"
            ),
            "num_results" to PropertySchema(
                type = "INTEGER",
                description = "Number of results to return (1-25, default: 10)"
            ),
            "category" to PropertySchema(
                type = "STRING",
                description = "Focus search: 'company', 'research paper', 'news', 'personal site', 'financial report'"
            ),
            "date_after" to PropertySchema(
                type = "STRING",
                description = "Only results after this date (YYYY-MM-DD format)"
            ),
            "date_before" to PropertySchema(
                type = "STRING",
                description = "Only results before this date (YYYY-MM-DD format)"
            ),
            "include_sites" to PropertySchema(
                type = "STRING",
                description = "Comma-separated domains to include (e.g. 'github.com,stackoverflow.com')"
            ),
            "exclude_sites" to PropertySchema(
                type = "STRING",
                description = "Comma-separated domains to exclude"
            )
        ),
        required = listOf("query")
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val query = (args["query"] as? String)
            ?: return ToolResult.error("Missing required parameter: 'query'")

        val mode = (args["mode"] as? String) ?: "search"
        val numResults = minOf((args["num_results"] as? Number)?.toInt() ?: 10, 25)
        val category = args["category"] as? String
        val dateAfter = args["date_after"] as? String
        val dateBefore = args["date_before"] as? String
        val includeSites = args["include_sites"] as? String
        val excludeSites = args["exclude_sites"] as? String

        return try {
            when (mode) {
                "answer" -> performAnswer(query)
                "similar" -> {
                    val url = args["url"] as? String
                    if (url.isNullOrBlank()) {
                        ToolResult.error("URL parameter required for 'similar' mode")
                    } else {
                        performFindSimilar(url, numResults)
                    }
                }
                else -> performSearch(query, numResults, category, dateAfter, dateBefore, includeSites, excludeSites)
            }
        } catch (e: Exception) {
            ToolResult.error("Exa search failed: ${e.message}")
        }
    }

    private suspend fun performSearch(
        query: String, numResults: Int, category: String?,
        dateAfter: String?, dateBefore: String?,
        includeSites: String?, excludeSites: String?
    ): ToolResult {
        val request = ExaSearchRequest(
            query = query,
            type = "auto",
            numResults = numResults,
            contents = ExaContents(text = true, highlights = true, summary = true),
            filters = ExaFilters(
                category = category,
                dateAfter = dateAfter,
                dateBefore = dateBefore,
                includeDomains = includeSites?.split(",")?.map { it.trim() },
                excludeDomains = excludeSites?.split(",")?.map { it.trim() }
            )
        )

        val response = ExaSearchClient.api.search(ExaSearchClient.API_KEY, request)

        val results = response.results
        if (results.isNullOrEmpty()) {
            return ToolResult.success("No results found for: $query")
        }

        val output = StringBuilder()
        output.appendLine("Search results for: \"$query\"")
        output.appendLine("Found ${results.size} results:")
        output.appendLine()

        for ((i, result) in results.withIndex()) {
            output.appendLine("${i + 1}. ${result.title ?: "Untitled"}")
            output.appendLine("   URL: ${result.url ?: "N/A"}")
            if (!result.publishedDate.isNullOrBlank()) {
                output.appendLine("   Published: ${result.publishedDate}")
            }
            if (!result.author.isNullOrBlank()) {
                output.appendLine("   Author: ${result.author}")
            }
            if (!result.summary.isNullOrBlank()) {
                output.appendLine("   Summary: ${result.summary}")
            }
            if (!result.highlights.isNullOrEmpty()) {
                output.appendLine("   Highlights:")
                result.highlights.take(3).forEach { highlight ->
                    output.appendLine("     - $highlight")
                }
            }
            if (!result.text.isNullOrBlank() && result.summary.isNullOrBlank()) {
                val preview = result.text.take(500).trim()
                output.appendLine("   Preview: $preview...")
            }
            output.appendLine()
        }

        return ToolResult.success(
            output.toString(),
            mapOf(
                "count" to results.size,
                "query" to query,
                "results" to results.map { r ->
                    mapOf("title" to (r.title ?: ""), "url" to (r.url ?: ""), "summary" to (r.summary ?: ""))
                }
            )
        )
    }

    private suspend fun performAnswer(query: String): ToolResult {
        val request = ExaAnswerRequest(query = query, text = true)
        val response = ExaSearchClient.api.answer(ExaSearchClient.API_KEY, request)

        val output = StringBuilder()
        output.appendLine("Answer: $query")
        output.appendLine()
        output.appendLine(response.answer ?: "No answer available.")

        if (!response.citations.isNullOrEmpty()) {
            output.appendLine()
            output.appendLine("Sources:")
            for ((i, citation) in response.citations.withIndex()) {
                output.appendLine("${i + 1}. ${citation.title ?: "Untitled"}")
                output.appendLine("   ${citation.url ?: ""}")
            }
        }

        return ToolResult.success(
            output.toString(),
            mapOf(
                "answer" to (response.answer ?: ""),
                "citations" to (response.citations?.map { mapOf("title" to (it.title ?: ""), "url" to (it.url ?: "")) } ?: emptyList())
            )
        )
    }

    private suspend fun performFindSimilar(url: String, numResults: Int): ToolResult {
        val request = com.example.service.ExaFindSimilarRequest(
            url = url,
            numResults = numResults,
            contents = ExaContents(text = true, highlights = true, summary = true)
        )
        val response = ExaSearchClient.api.findSimilar(ExaSearchClient.API_KEY, request)

        val results = response.results
        if (results.isNullOrEmpty()) {
            return ToolResult.success("No similar results found for: $url")
        }

        val output = StringBuilder()
        output.appendLine("Pages similar to: $url")
        output.appendLine("Found ${results.size} results:")
        output.appendLine()

        for ((i, result) in results.withIndex()) {
            output.appendLine("${i + 1}. ${result.title ?: "Untitled"}")
            output.appendLine("   URL: ${result.url ?: "N/A"}")
            if (!result.summary.isNullOrBlank()) {
                output.appendLine("   Summary: ${result.summary}")
            }
            output.appendLine()
        }

        return ToolResult.success(
            output.toString(),
            mapOf("count" to results.size, "results" to results.map { mapOf("title" to (it.title ?: ""), "url" to (it.url ?: "")) })
        )
    }
}
