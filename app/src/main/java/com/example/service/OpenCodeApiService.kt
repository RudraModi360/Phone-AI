package com.example.service

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

// --- OpenAI-Compatible Chat Completions Data Classes ---

data class OpenCodeToolCallFunction(
    @Json(name = "name") val name: String,
    @Json(name = "arguments") val arguments: String
)

data class OpenCodeToolCall(
    @Json(name = "id") val id: String? = null,
    @Json(name = "type") val type: String = "function",
    @Json(name = "function") val function: OpenCodeToolCallFunction
)

data class OpenCodeMessage(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String?,
    @Json(name = "tool_calls") val toolCalls: List<OpenCodeToolCall>? = null
)

data class ChatCompletionRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<OpenCodeMessage>,
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "stream") val stream: Boolean = false
)

data class ChatCompletionChoice(
    @Json(name = "index") val index: Int? = null,
    @Json(name = "message") val message: OpenCodeMessage? = null,
    @Json(name = "finish_reason") val finishReason: String? = null
)

data class ChatCompletionResponse(
    @Json(name = "id") val id: String? = null,
    @Json(name = "choices") val choices: List<ChatCompletionChoice>? = null,
    @Json(name = "message") val message: OpenCodeMessage? = null
)

fun ChatCompletionResponse.getContent(): String? {
    return this.message?.content ?: this.choices?.firstOrNull()?.message?.content
}

val ChatCompletionResponse.done: Boolean
    get() = true

val ChatCompletionResponse.error: String?
    get() = null

// --- Ollama Native API Types ---

data class OllamaChatChunk(
    @Json(name = "model") val model: String = "",
    @Json(name = "created_at") val createdAt: String = "",
    @Json(name = "message") val message: OllamaChatMessage = OllamaChatMessage(),
    @Json(name = "done") val done: Boolean = false,
    @Json(name = "done_reason") val doneReason: String? = null,
    @Json(name = "total_duration") val totalDuration: Long? = null,
    @Json(name = "load_duration") val loadDuration: Long? = null,
    @Json(name = "prompt_eval_count") val promptEvalCount: Int? = null,
    @Json(name = "prompt_eval_duration") val promptEvalDuration: Long? = null,
    @Json(name = "eval_count") val evalCount: Int? = null,
    @Json(name = "eval_duration") val evalDuration: Long? = null
)

data class OllamaChatMessage(
    @Json(name = "role") val role: String = "assistant",
    @Json(name = "content") val content: String? = null,
    @Json(name = "thinking") val thinking: String? = null,
    @Json(name = "tool_calls") val toolCalls: List<OllamaNativeToolCall>? = null
)

data class OllamaNativeToolCall(
    @Json(name = "function") val function: OllamaNativeToolCallFunction,
    @Json(name = "id") val id: String? = null,
    @Json(name = "type") val type: String = "function"
)

data class OllamaNativeToolCallFunction(
    @Json(name = "name") val name: String,
    @Json(name = "arguments") val arguments: Map<String, Any?>? = null
)

data class OllamaToolDef(
    @Json(name = "type") val type: String = "function",
    @Json(name = "function") val function: OllamaFunctionDef
)

data class OllamaFunctionDef(
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String,
    @Json(name = "parameters") val parameters: Map<String, Any?>
)

data class OllamaChatRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<OpenCodeMessage>,
    @Json(name = "tools") val tools: List<OllamaToolDef>? = null,
    @Json(name = "stream") val stream: Boolean = true,
    @Json(name = "think") val think: Boolean = false,
    @Json(name = "keep_alive") val keepAlive: String? = null,
    @Json(name = "format") val format: String? = null,
    @Json(name = "options") val options: OllamaRequestOptions? = null
)

data class OllamaRequestOptions(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "num_predict") val numPredict: Int? = null,
    @Json(name = "top_p") val topP: Float? = null,
    @Json(name = "top_k") val topK: Int? = null
)

// --- Retrofit Interface supporting Dynamic Base URL ---

interface OpenCodeApi {
    @POST
    suspend fun chatCompletions(
        @Url url: String,
        @Header("Authorization") bearerToken: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse

    // Ollama native streaming endpoint (NDJSON format)
    @POST
    @retrofit2.http.Streaming
    suspend fun ollamaChatStream(
        @Url url: String,
        @Header("Authorization") bearerToken: String,
        @Body request: OllamaChatRequest
    ): okhttp3.ResponseBody
}

object OpenCodeApiClient {
    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(300, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val requestBuffer = okio.Buffer()
            try {
                request.body?.writeTo(requestBuffer)
            } catch (e: Exception) {
                android.util.Log.e("OpenCodeApi", "Failed to write request body", e)
            }
            val requestBodyString = requestBuffer.readUtf8()
            android.util.Log.d("OpenCodeApi", "--> SENT REQUEST: ${request.method} ${request.url}\n$requestBodyString")
            
            val response = chain.proceed(request)
            android.util.Log.d("OpenCodeApi", "<-- RECEIVED RESPONSE: ${response.code} ${response.message}")
            
            // IMPORTANT: Only read response body for logging on non-streaming requests
            // Streaming endpoints return ResponseBody that must not be consumed here
            val isStreaming = request.header("Accept") == "text/event-stream" ||
                             request.url.encodedPath.contains("stream") ||
                             response.header("Content-Type")?.contains("application/x-ndjson") == true
            
            if (!isStreaming) {
                val responseBody = response.body
                if (responseBody != null) {
                    try {
                        val source = responseBody.source()
                        source.request(Long.MAX_VALUE)
                        val buffer = source.buffer
                        val responseBodyString = buffer.clone().readUtf8()
                        android.util.Log.d("OpenCodeApi", "Response Body:\n$responseBodyString")
                    } catch (e: Exception) {
                        android.util.Log.e("OpenCodeApi", "Failed to read response body", e)
                    }
                }
            } else {
                android.util.Log.d("OpenCodeApi", "Streaming response detected - skipping body read for logging")
            }
            response
        }
        .build()

    private val retrofit = Retrofit.Builder()
        // We use a dummy baseUrl since we'll pass fully qualified dynamic @Url to POST
        .baseUrl("https://api.opencode.ai/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: OpenCodeApi = retrofit.create(OpenCodeApi::class.java)
}
