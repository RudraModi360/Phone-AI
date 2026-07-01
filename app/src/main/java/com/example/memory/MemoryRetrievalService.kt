package com.example.memory

import android.util.Log
import com.example.data.MemoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MemoryRetrievalService(
    private val memoryService: MemoryService,
    private val surfaceTracker: MemorySurfaceTracker = MemorySurfaceTracker(),
    private val baseUrl: String,
    private val model: String
) {
    companion object {
        private const val TAG = "MemoryRetrieval"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun findRelevantMemories(
        userQuery: String,
        projectId: String? = null
    ): List<MemoryEntry> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== RETRIEVAL START ===")
        Log.d(TAG, "User query: $userQuery")
        Log.d(TAG, "Base URL: $baseUrl")
        Log.d(TAG, "Model: $model")

        if (!surfaceTracker.hasBudget()) {
            Log.d(TAG, "Session budget exhausted — skipping retrieval")
            Log.d(TAG, "=== RETRIEVAL END (BUDGET) ===")
            return@withContext emptyList()
        }

        try {
            val manifest = memoryService.getMemoryManifest()
            Log.d(TAG, "Manifest size: ${manifest.size}")
            if (manifest.isEmpty()) {
                Log.d(TAG, "Manifest empty — nothing to retrieve")
                Log.d(TAG, "=== RETRIEVAL END (EMPTY) ===")
                return@withContext emptyList()
            }

            val unsurfaced = manifest.filter { !surfaceTracker.isSurfaced(it.id) }
            Log.d(TAG, "Unsurfaced candidates: ${unsurfaced.size}")
            if (unsurfaced.isEmpty()) {
                Log.d(TAG, "All memories already surfaced this session")
                Log.d(TAG, "=== RETRIEVAL END (ALL SURFACED) ===")
                return@withContext emptyList()
            }

            Log.d(TAG, "Calling LLM for relevance ranking...")
            val selectedIds = queryLLMForRelevance(userQuery, unsurfaced)
            Log.d(TAG, "LLM returned ${selectedIds.size} IDs: $selectedIds")

            if (selectedIds.isEmpty()) {
                Log.d(TAG, "LLM returned no relevant IDs")
                Log.d(TAG, "=== RETRIEVAL END (NO MATCHES) ===")
                return@withContext emptyList()
            }

            val selectedMemories = selectedIds.mapNotNull { id ->
                unsurfaced.find { it.id == id }
            }
            Log.d(TAG, "Matched ${selectedMemories.size} memories")
            for (mem in selectedMemories) {
                Log.d(TAG, "  - [${mem.memoryType}] ${mem.name}: ${mem.description.take(80)}")
            }

            surfaceTracker.surface(selectedMemories)
            Log.d(TAG, "=== RETRIEVAL END (SUCCESS) ===")
            selectedMemories
        } catch (e: Exception) {
            Log.e(TAG, "Retrieval FAILED, falling back to keyword search", e)
            Log.e(TAG, "=== RETRIEVAL END (EXCEPTION) ===")
            val fallback = memoryService.search(userQuery.take(50), limit = 5)
            Log.d(TAG, "Fallback returned ${fallback.size} results")
            surfaceTracker.surface(fallback)
            fallback
        }
    }

    private suspend fun queryLLMForRelevance(
        userQuery: String,
        candidates: List<MemoryEntry>
    ): List<Long> {
        val manifestStr = buildString {
            for (mem in candidates) {
                appendLine("[${mem.id}] ${mem.name} (${mem.memoryType}): ${mem.description}")
            }
        }

        val prompt = """You are a memory retrieval assistant. Given a user query and a list of available memories, pick the ≤5 most relevant memories.

USER QUERY: $userQuery

AVAILABLE MEMORIES:
$manifestStr

Respond with ONLY a JSON array of memory IDs (numbers), ordered by relevance.
Example: [3, 1, 7]
If no memories are relevant, respond with: []"""

        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", "You are a memory retrieval assistant. Respond with JSON only.")
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("stream", false)
            put("options", JSONObject().apply {
                put("temperature", 0.1)
                put("num_predict", 128)
            })
        }

        val url = "$baseUrl/chat"
        Log.d(TAG, "queryLLMForRelevance - URL: $url")
        Log.d(TAG, "queryLLMForRelevance - Prompt length: ${prompt.length}")

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        Log.d(TAG, "queryLLMForRelevance - Executing request...")
        val response = client.newCall(request).execute()
        val code = response.code
        Log.d(TAG, "queryLLMForRelevance - HTTP status: $code")

        if (code != 200) {
            val errorBody = response.body?.string() ?: "no body"
            Log.e(TAG, "queryLLMForRelevance - HTTP $code error. Body: ${errorBody.take(500)}")
            return emptyList()
        }

        val responseBody = response.body?.string()
        if (responseBody == null) {
            Log.e(TAG, "queryLLMForRelevance - Response body is null")
            return emptyList()
        }

        Log.d(TAG, "queryLLMForRelevance - Response length: ${responseBody.length}")
        Log.d(TAG, "queryLLMForRelevance - Response (first 500 chars): ${responseBody.take(500)}")

        val content = JSONObject(responseBody)
            .getJSONArray("message")
            .getJSONObject(0)
            .getString("content")
            .trim()

        Log.d(TAG, "queryLLMForRelevance - Content: $content")
        return parseIds(content)
    }

    private fun parseIds(content: String): List<Long> {
        return try {
            val jsonStr = content.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            Log.d(TAG, "parseIds - Cleaned: $jsonStr")
            val array = JSONArray(jsonStr)
            val ids = (0 until array.length()).map { array.getLong(it) }
            Log.d(TAG, "parseIds - Parsed IDs: $ids")
            ids
        } catch (e: Exception) {
            Log.e(TAG, "parseIds - Failed to parse IDs from: $content", e)
            emptyList()
        }
    }
}
