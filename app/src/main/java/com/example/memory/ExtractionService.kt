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
    private val baseUrl: String,
    private val model: String
) {
    companion object {
        private const val TAG = "ExtractionService"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun extractAsync(userMessage: String, aiResponse: String) {
        Log.d(TAG, "=== EXTRACTION START ===")
        Log.d(TAG, "Base URL: $baseUrl")
        Log.d(TAG, "Model: $model")
        Log.d(TAG, "User message length: ${userMessage.length}")
        Log.d(TAG, "AI response length: ${aiResponse.length}")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val manifest = memoryService.getMemoryManifest()
                Log.d(TAG, "Current manifest size: ${manifest.size}")

                val prompt = ExtractionPromptBuilder.build(userMessage, aiResponse, manifest)
                Log.d(TAG, "Extraction prompt length: ${prompt.length}")
                Log.d(TAG, "Extraction prompt (first 500 chars): ${prompt.take(500)}")

                Log.d(TAG, "Calling LLM...")
                val response = callLLM(prompt)
                if (response == null) {
                    Log.e(TAG, "LLM returned null - extraction FAILED")
                    Log.e(TAG, "=== EXTRACTION END (FAILED) ===")
                    return@launch
                }

                Log.d(TAG, "LLM response length: ${response.length}")
                Log.d(TAG, "LLM response (first 1000 chars): ${response.take(1000)}")

                val memories = parseResponse(response)
                Log.d(TAG, "Parsed ${memories.size} memories from response")

                for (mem in memories) {
                    Log.d(TAG, "  - Saving: [${mem.type.value}] ${mem.name} = ${mem.content.take(100)}")
                    memoryService.saveMemory(
                        name = mem.name,
                        description = mem.description,
                        type = mem.type,
                        content = mem.content
                    )
                }
                Log.d(TAG, "Successfully extracted ${memories.size} memories")
                Log.d(TAG, "=== EXTRACTION END (SUCCESS) ===")
            } catch (e: Exception) {
                Log.e(TAG, "Extraction FAILED with exception", e)
                Log.e(TAG, "=== EXTRACTION END (EXCEPTION) ===")
            }
        }
    }

    private suspend fun callLLM(prompt: String): String? {
        return try {
            val url = "$baseUrl/chat"
            Log.d(TAG, "callLLM - URL: $url")
            Log.d(TAG, "callLLM - Model: $model")

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

            val bodyStr = body.toString()
            Log.d(TAG, "callLLM - Request body length: ${bodyStr.length}")

            val request = Request.Builder()
                .url(url)
                .post(bodyStr.toRequestBody("application/json".toMediaType()))
                .build()

            Log.d(TAG, "callLLM - Executing request...")
            val response = client.newCall(request).execute()
            val code = response.code
            Log.d(TAG, "callLLM - HTTP status: $code")

            if (code != 200) {
                val errorBody = response.body?.string() ?: "no body"
                Log.e(TAG, "callLLM - HTTP $code error. Body (first 500 chars): ${errorBody.take(500)}")
                return null
            }

            val responseBody = response.body?.string()
            if (responseBody == null) {
                Log.e(TAG, "callLLM - Response body is null")
                return null
            }

            Log.d(TAG, "callLLM - Response body length: ${responseBody.length}")
            Log.d(TAG, "callLLM - Response body (first 500 chars): ${responseBody.take(500)}")

            val json = JSONObject(responseBody)
            val content = json
                .getJSONArray("message")
                .getJSONObject(0)
                .getString("content")

            Log.d(TAG, "callLLM - Extracted content length: ${content.length}")
            content
        } catch (e: Exception) {
            Log.e(TAG, "callLLM - EXCEPTION: ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    private fun parseResponse(response: String): List<ExtractedMemory> {
        return try {
            val jsonStr = response.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            Log.d(TAG, "parseResponse - Cleaned JSON (first 500 chars): ${jsonStr.take(500)}")

            val array = JSONArray(jsonStr)
            Log.d(TAG, "parseResponse - Array length: ${array.length()}")

            (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                val name = obj.optString("name", "")
                val content = obj.optString("content", "")
                if (name.isBlank() || content.isBlank()) {
                    Log.w(TAG, "parseResponse - Skipping entry $i: name='$name', content blank=${content.isBlank()}")
                    return@mapNotNull null
                }

                ExtractedMemory(
                    id = if (obj.has("id")) obj.optLong("id", 0) else 0,
                    name = name,
                    description = obj.optString("description", ""),
                    type = MemoryType.fromString(obj.optString("type", "project")),
                    content = content
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseResponse - EXCEPTION: ${e.javaClass.simpleName}: ${e.message}", e)
            Log.e(TAG, "parseResponse - Raw response (first 1000 chars): ${response.take(1000)}")
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
