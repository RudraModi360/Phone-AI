package com.example.memory

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ExtractionService(
    private val memoryService: MemoryService,
    private val baseUrl: String = "https://ollama.com/api",
    private val model: String = "gpt-oss:20b-cloud"
) {
    companion object {
        private const val TAG = "ExtractionService"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun extractAsync(userMessage: String, aiResponse: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val manifest = memoryService.getMemoryManifest()
                val prompt = ExtractionPromptBuilder.build(userMessage, aiResponse, manifest)
                val response = callLLM(prompt) ?: return@launch

                val memories = parseResponse(response)
                for (mem in memories) {
                    memoryService.saveMemory(
                        name = mem.name,
                        description = mem.description,
                        type = mem.type,
                        content = mem.content
                    )
                    Log.d(TAG, "Saved: [${mem.type.value}] ${mem.name}")
                }
                Log.d(TAG, "Extracted ${memories.size} memories")
            } catch (e: Exception) {
                Log.e(TAG, "Extraction failed", e)
            }
        }
    }

    private suspend fun callLLM(prompt: String): String? {
        return try {
            val messagesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a memory extraction assistant. Respond with JSON only.")
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
                    put("temperature", 0.3)
                    put("num_predict", 1024)
                })
            }

            val request = Request.Builder()
                .url("$baseUrl/chat")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            response.body?.string()?.let { responseBody ->
                JSONObject(responseBody)
                    .getJSONArray("message")
                    .getJSONObject(0)
                    .getString("content")
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM call failed", e)
            null
        }
    }

    private fun parseResponse(response: String): List<ExtractedMemory> {
        return try {
            val jsonStr = response.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            val array = JSONArray(jsonStr)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                val name = obj.optString("name", "")
                val content = obj.optString("content", "")
                if (name.isBlank() || content.isBlank()) return@mapNotNull null

                ExtractedMemory(
                    id = if (obj.has("id")) obj.optLong("id", 0) else 0,
                    name = name,
                    description = obj.optString("description", ""),
                    type = MemoryType.fromString(obj.optString("type", "project")),
                    content = content
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse failed", e)
            emptyList()
        }
    }

    private data class ExtractedMemory(
        val id: Long,
        val name: String,
        val description: String,
        val type: MemoryType,
        val content: String
    )
}
