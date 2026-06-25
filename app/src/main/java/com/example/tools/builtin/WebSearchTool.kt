package com.example.tools.builtin

import com.example.service.Parameters
import com.example.service.PropertySchema
import com.example.tools.BaseTool
import com.example.tools.RiskLevel
import com.example.tools.ToolResult
import com.example.tools.AndroidPathResolver
import com.example.data.AppDatabase
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WebSearchTool : BaseTool {
    override val name = "web_search"
    override val description = "Perform internet search using a 3-tiered system based on query complexity (DDG for simple, Google for medium, GROQ for complex synthesized answers)."
    override val riskLevel = RiskLevel.SAFE

    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "query" to PropertySchema(
                type = "STRING",
                description = "The internet search query"
            ),
            "complexity" to PropertySchema(
                type = "STRING",
                description = "Search tier to enforce: 'auto', 'simple', 'medium', 'complex' (default: 'auto')"
            )
        ),
        required = listOf("query")
    )

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val query = args["query"] as? String ?: return ToolResult.error("Query parameter is required")
        val requestedComplexity = args["complexity"] as? String ?: "auto"

        val complexity = if (requestedComplexity == "auto" || requestedComplexity.isBlank()) {
            detectComplexity(query)
        } else {
            requestedComplexity.lowercase()
        }

        val context = AndroidPathResolver.getContext()
            ?: return ToolResult.error("Application context not available to load search settings.")

        val db = AppDatabase.getDatabase(context)

        return try {
            when (complexity) {
                "simple" -> executeDuckDuckGo(query)
                "medium" -> {
                    val googleKey = db.settingDao().getSettingValue("google_api_key") ?: ""
                    val googleCx = db.settingDao().getSettingValue("google_cx") ?: ""
                    if (googleKey.isBlank() || googleCx.isBlank()) {
                        executeDuckDuckGo(query, "NOTE: Google CX or API Key not configured. Falling back to DuckDuckGo.\n\n")
                    } else {
                        executeGoogleSearch(query, googleKey, googleCx)
                    }
                }
                "complex" -> {
                    val groqKey = db.settingDao().getSettingValue("groq_api_key") ?: ""
                    if (groqKey.isBlank()) {
                        val googleKey = db.settingDao().getSettingValue("google_api_key") ?: ""
                        val googleCx = db.settingDao().getSettingValue("google_cx") ?: ""
                        if (googleKey.isBlank() || googleCx.isBlank()) {
                            executeDuckDuckGo(query, "NOTE: GROQ and Google Search are not configured. Falling back to DuckDuckGo.\n\n")
                        } else {
                            executeGoogleSearch(query, googleKey, googleCx, "NOTE: GROQ API Key not configured. Falling back to Google Custom Search.\n\n")
                        }
                    } else {
                        executeGroqSearch(query, groqKey)
                    }
                }
                else -> executeDuckDuckGo(query)
            }
        } catch (e: Exception) {
            ToolResult.error("Web search failed: ${e.message}")
        }
    }

    private fun detectComplexity(query: String): String {
        val trimmed = query.trim()
        val words = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val wordCount = words.size
        val lowerQuery = trimmed.lowercase()
        val pattern = "compare|vs|versus|difference|explain|why|how|analyze|advantages|disadvantages".toRegex()
        val hasKeywords = pattern.containsMatchIn(lowerQuery)

        return when {
            wordCount < 5 && !hasKeywords -> "simple"
            wordCount > 15 || hasKeywords -> "complex"
            else -> "medium"
        }
    }

    private fun executeDuckDuckGo(query: String, prefixNote: String = ""): ToolResult {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return ToolResult.error("DuckDuckGo request failed with code ${response.code}")
            }
            val body = response.body?.string() ?: ""
            if (body.isBlank()) {
                return ToolResult.error("DuckDuckGo returned an empty response")
            }

            val json = JSONObject(body)
            val abstractText = json.optString("AbstractText", "")
            val abstractSource = json.optString("AbstractSource", "")
            val abstractUrl = json.optString("AbstractURL", "")

            val builder = StringBuilder(prefixNote)
            builder.append("### DuckDuckGo Instant Answer\n\n")
            if (abstractText.isNotBlank()) {
                builder.append(abstractText).append("\n\n")
                if (abstractSource.isNotBlank()) {
                    builder.append("*Source: $abstractSource*")
                    if (abstractUrl.isNotBlank()) {
                        builder.append(" ($abstractUrl)")
                    }
                    builder.append("\n\n")
                }
            } else {
                builder.append("No direct instant answer abstract found. Showing related topics:\n\n")
            }

            val relatedTopics = json.optJSONArray("RelatedTopics")
            if (relatedTopics != null && relatedTopics.length() > 0) {
                builder.append("#### Related Topics:\n")
                var count = 0
                for (i in 0 until relatedTopics.length()) {
                    if (count >= 5) break
                    val topic = relatedTopics.optJSONObject(i)
                    if (topic != null) {
                        val text = topic.optString("Text", "")
                        val firstUrl = topic.optString("FirstURL", "")
                        if (text.isNotBlank()) {
                            builder.append("- $text")
                            if (firstUrl.isNotBlank()) {
                                builder.append(" ($firstUrl)")
                            }
                            builder.append("\n")
                            count++
                        }
                    }
                }
            }

            val resultText = builder.toString().trim()
            return if (resultText.isNotBlank()) {
                ToolResult.success(resultText, mapOf("source" to "duckduckgo", "query" to query))
            } else {
                ToolResult.success("No results found on DuckDuckGo for query: '$query'", mapOf("source" to "duckduckgo"))
            }
        }
    }

    private fun executeGoogleSearch(query: String, apiKey: String, cx: String, prefixNote: String = ""): ToolResult {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.googleapis.com/customsearch/v1?key=$apiKey&cx=$cx&q=$encodedQuery"

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                return ToolResult.error("Google Custom Search API error (${response.code}): $errBody")
            }
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val items = json.optJSONArray("items")

            val builder = StringBuilder(prefixNote)
            builder.append("### Google Custom Search Results\n\n")

            if (items == null || items.length() == 0) {
                builder.append("No results found for '$query'.")
                return ToolResult.success(builder.toString(), mapOf("source" to "google", "count" to 0))
            }

            for (i in 0 until minOf(items.length(), 5)) {
                val item = items.getJSONObject(i)
                val title = item.optString("title", "Untitled")
                val link = item.optString("link", "")
                val snippet = item.optString("snippet", "")

                builder.append("${i + 1}. **$title**\n")
                if (link.isNotBlank()) {
                    builder.append("   *URL: $link*\n")
                }
                if (snippet.isNotBlank()) {
                    builder.append("   $snippet\n")
                }
                builder.append("\n")
            }

            return ToolResult.success(builder.toString().trim(), mapOf("source" to "google", "count" to items.length()))
        }
    }

    private fun executeGroqSearch(query: String, apiKey: String): ToolResult {
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        
        val systemPrompt = """
            You are a helpful AI assistant with synthesized web knowledge. 
            Analyze the user's question carefully, organize verified facts, explain concepts in detail, 
            and compare any related aspects clearly. Provide a structured and readable markdown response.
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            put("model", "groq/compound")
            put("temperature", 0.2)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", query)
                })
            })
        }

        val requestBody = jsonBody.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                return ToolResult.error("GROQ API error (${response.code}): $errBody")
            }
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return ToolResult.error("GROQ returned an empty choice list")
            }

            val messageObj = choices.getJSONObject(0).optJSONObject("message")
            val answer = messageObj?.optString("content", "") ?: ""

            if (answer.isBlank()) {
                return ToolResult.error("GROQ returned an empty content answer")
            }

            val formattedAnswer = "### GROQ AI Deep Synthesis Search\n\n$answer"
            return ToolResult.success(formattedAnswer, mapOf("source" to "groq"))
        }
    }
}
