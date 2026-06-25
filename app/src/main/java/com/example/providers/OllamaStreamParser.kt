package com.example.providers

import android.util.Log
import com.example.service.OllamaChatChunk
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.ResponseBody

/**
 * Parses Ollama's NDJSON (newline-delimited JSON) streaming responses.
 * Each line is a separate JSON object representing a chunk of the response.
 */
object OllamaStreamParser {
    private const val TAG = "OllamaStreamParser"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val chunkAdapter = moshi.adapter(OllamaChatChunk::class.java)

    /**
     * Parse streaming response body into Flow of chunks.
     * Ollama sends one JSON object per line, each terminated by \n.
     */
    fun parseStream(responseBody: ResponseBody): Flow<OllamaChatChunk> = flow {
        val source = responseBody.source()
        var lineCount = 0

        try {
            while (true) {
                val line = source.readUtf8Line()?.trim() ?: break
                if (line.isEmpty()) continue

                try {
                    val chunk = chunkAdapter.fromJson(line)
                    if (chunk != null) {
                        emit(chunk)
                        lineCount++

                        if (chunk.done) {
                            Log.d(TAG, "Stream complete after $lineCount chunks, " +
                                "tokens: prompt=${chunk.promptEvalCount}, output=${chunk.evalCount}")
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse line: ${line.take(100)}...", e)
                    // Continue to next line - don't break the stream
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stream reading error", e)
            throw e
        } finally {
            responseBody.close()
        }
    }.flowOn(Dispatchers.IO)
}
