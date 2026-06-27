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
    private val baseUrl: String = "https://ollama.com/api",
    private val model: String = "gpt-oss:20b-cloud"
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
        if (!surfaceTracker.hasBudget()) {
            Log.d(TAG, "Session budget exhausted")
            return@withContext emptyList()
        }

        try {
            val manifest = memoryService.getMemoryManifest()
            if (manifest.isEmpty()) return@withContext emptyList()

            val unsurfaced = manifest.filter { !surfaceTracker.isSurfaced(it.id) }
            if (unsurfaced.isEmpty()) return@withContext emptyList()

            val selectedIds = queryLLMForRelevance(userQuery, unsurfaced)
            if (selectedIds.isEmpty()) return@withContext emptyList()

            val selectedMemories = selectedIds.mapNotNull { id ->
                unsurfaced.find { it.id == id }
            }

            surfaceTracker.surface(selectedMemories)
        } catch (e: Exception) {
            Log.e(TAG, "Retrieval failed, falling back to search", e)
            val fallback = memoryService.search(userQuery.take(50), limit = 5)
            surfaceTracker.surface(fallback)
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

        val request = Request.Builder()
            .url("$baseUrl/chat")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: return emptyList()

        val content = JSONObject(responseBody)
            .getJSONArray("message")
            .getJSONObject(0)
            .getString("content")
            .trim()

        return parseIds(content)
    }

    private fun parseIds(content: String): List<Long> {
        return try {
            val jsonStr = content.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val array = JSONArray(jsonStr)
            (0 until array.length()).map { array.getLong(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse IDs: $content", e)
            emptyList()
        }
    }
}
