package com.example.providers

import android.util.Log
import com.example.service.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject

/**
 * LLMProvider implementation for Ollama-compatible API endpoints.
 * Supports both Ollama native API and OpenAI-compatible endpoints.
 */
class OllamaProvider(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String = "",
    private val thinkMode: Boolean = false
) : LLMProvider {
    
    companion object {
        private const val TAG = "OllamaProvider"
        
        /**
         * Create provider with default Ollama local settings.
         */
        fun local(model: String = "llama3"): OllamaProvider {
            return OllamaProvider(
                baseUrl = "http://localhost:11434",
                model = model
            )
        }
    }
    
    override val name = "ollama"
    override val capabilities = setOf(
        ProviderCapability.CHAT,
        ProviderCapability.STREAMING,
        ProviderCapability.TOOLS
    )
    
    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any>>?,
        systemInstruction: String?
    ): ChatResponse {
        Log.d(TAG, "chat() called with ${messages.size} messages, model=$model")
        
        try {
            // Build message list with system instruction
            val allMessages = buildMessageList(messages, systemInstruction)
            Log.d(TAG, "Built ${allMessages.size} messages for API")
            
            val request = ChatCompletionRequest(
                model = model,
                messages = allMessages,
                temperature = 0.7f,
                stream = false
            )
            
            // Build full URL for chat endpoint
            val chatUrl = buildChatUrl(baseUrl)
            val bearerToken = if (apiKey.isNotEmpty()) "Bearer $apiKey" else ""
            
            Log.d(TAG, "Calling API at: $chatUrl")
            
            val response = OpenCodeApiClient.api.chatCompletions(chatUrl, bearerToken, request)
            
            Log.d(TAG, "API Response received: choices=${response.choices?.size}, message=${response.message != null}")
            
            return parseResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "chat() error", e)
            throw OllamaProviderException("Chat request failed: ${e.message}", e)
        }
    }
    
    override fun chatStream(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any>>?,
        systemInstruction: String?
    ): Flow<StreamChunk> = flow {
        Log.d(TAG, "chatStream() called with ${messages.size} messages, model=$model")
        
        try {
            val allMessages = buildMessageList(messages, systemInstruction)
            val toolDefs = buildToolDefinitions(tools)
            
            val request = OllamaChatRequest(
                model = model,
                messages = allMessages,
                tools = toolDefs,
                stream = true,
                think = thinkMode
            )
            
            val chatUrl = buildChatUrl(baseUrl)
            val bearerToken = if (apiKey.isNotEmpty()) "Bearer $apiKey" else ""
            
            Log.d(TAG, "Starting streaming request to: $chatUrl")
            
            val responseBody = OpenCodeApiClient.api.ollamaChatStream(chatUrl, bearerToken, request)
            
            // Accumulate content and tool calls across chunks
            val contentBuilder = StringBuilder()
            val thinkingBuilder = StringBuilder()
            val toolCallsMap = mutableMapOf<String, OllamaNativeToolCall>()
            
            OllamaStreamParser.parseStream(responseBody).collect { chunk ->
                // Handle content
                chunk.message.content?.let { content ->
                    contentBuilder.append(content)
                    emit(StreamChunk(
                        content = content,
                        toolCalls = null,
                        isComplete = false
                    ))
                }
                
                // Handle thinking (for models like gpt-oss)
                chunk.message.thinking?.let { thinking ->
                    thinkingBuilder.append(thinking)
                    // Emit thinking as content for UI display
                    emit(StreamChunk(
                        content = "<think>$thinking</think>",
                        toolCalls = null,
                        isComplete = false
                    ))
                }
                
                // Handle tool calls (accumulate partial calls)
                chunk.message.toolCalls?.forEach { toolCall ->
                    val id = toolCall.id ?: toolCall.function.name
                    toolCallsMap[id] = toolCall
                }
                
                // If stream is done, emit final chunk with accumulated tool calls
                if (chunk.done) {
                    val finalToolCalls = if (toolCallsMap.isNotEmpty()) {
                        toolCallsMap.values.map { tc ->
                            val args = tc.function.arguments ?: emptyMap()
                            ToolCall(
                                id = tc.id ?: java.util.UUID.randomUUID().toString(),
                                name = tc.function.name,
                                arguments = args
                            )
                        }
                    } else null
                    
                    // Parse tool calls from content if not in native format
                    val contentToolCalls = if (finalToolCalls == null) {
                        parseToolCallsFromContent(contentBuilder.toString())
                    } else null
                    
                    val cleanContent = if (finalToolCalls != null || (contentToolCalls?.isNotEmpty() == true)) {
                        contentBuilder.toString()
                            .replace(Regex("""<tool_call>.*?</tool_call>""", RegexOption.DOT_MATCHES_ALL), "")
                            .replace(Regex("""\{"tool"\s*:\s*"[^"]+"[^}]*\}""", RegexOption.DOT_MATCHES_ALL), "")
                            .trim()
                    } else {
                        contentBuilder.toString()
                    }
                    
                    emit(StreamChunk(
                        content = cleanContent.ifEmpty { null },
                        toolCalls = finalToolCalls ?: contentToolCalls?.ifEmpty { null },
                        isComplete = true
                    ))
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "chatStream() error", e)
            emit(StreamChunk(
                content = "Error: ${e.message}",
                toolCalls = null,
                isComplete = true
            ))
        }
    }
    
    override suspend fun healthCheck(): Boolean {
        return try {
            val testMessages = listOf(ChatMessage("user", "hi"))
            val response = chat(testMessages, null, null)
            !response.content.isNullOrEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Health check failed", e)
            false
        }
    }
    
    private fun buildMessageList(
        messages: List<ChatMessage>,
        systemInstruction: String?
    ): List<OpenCodeMessage> {
        val result = mutableListOf<OpenCodeMessage>()
        
        // Add system instruction first if present
        if (!systemInstruction.isNullOrEmpty()) {
            result.add(OpenCodeMessage(role = "system", content = systemInstruction))
            Log.d(TAG, "Added system instruction (${systemInstruction.length} chars)")
        }
        
        // Convert provider ChatMessage to OpenCodeMessage
        for (msg in messages) {
            when (msg.role) {
                "user", "system" -> {
                    result.add(OpenCodeMessage(role = msg.role, content = msg.content))
                }
                "assistant" -> {
                    // Include tool calls in content if present
                    val content = if (msg.toolCalls != null && msg.toolCalls.isNotEmpty()) {
                        formatToolCallsInContent(msg.content, msg.toolCalls)
                    } else {
                        msg.content
                    }
                    result.add(OpenCodeMessage(role = "assistant", content = content))
                }
                "tool" -> {
                    // Format tool result as a user message for non-native tool support
                    val toolResultContent = "Tool result for ${msg.toolCallId}:\n${msg.content}"
                    result.add(OpenCodeMessage(role = "user", content = toolResultContent))
                }
            }
        }
        
        return result
    }
    
    private fun formatToolCallsInContent(content: String, toolCalls: List<ToolCall>): String {
        val sb = StringBuilder(content)
        for (tc in toolCalls) {
            sb.append("\n<tool_call>")
            sb.append("\n{\"name\": \"${tc.name}\", \"arguments\": ${JSONObject(tc.arguments)}}")
            sb.append("\n</tool_call>")
        }
        return sb.toString()
    }
    
    private fun buildChatUrl(base: String): String {
        val cleanBase = base.trimEnd('/')
        
        // Handle various URL formats
        return when {
            cleanBase.contains("/chat/completions") -> cleanBase
            cleanBase.contains("/chat") && !cleanBase.endsWith("/chat") -> cleanBase
            cleanBase.endsWith("/chat") -> cleanBase
            cleanBase.contains("/v1") -> "$cleanBase/chat/completions"
            cleanBase.contains("/api") -> "$cleanBase/chat"
            cleanBase.endsWith("/api") -> "$cleanBase/chat"
            else -> "$cleanBase/v1/chat/completions"
        }
    }
    
    private fun parseResponse(response: ChatCompletionResponse): ChatResponse {
        // Try to get message from choices first
        val message = response.choices?.firstOrNull()?.message
            ?: response.message
        
        val rawContent = message?.content ?: ""
        
        Log.d(TAG, "parseResponse: rawContent length=${rawContent.length}")
        
        if (rawContent.isEmpty()) {
            Log.w(TAG, "Model returned empty content!")
            return ChatResponse(
                content = null,
                toolCalls = null,
                finishReason = response.choices?.firstOrNull()?.finishReason ?: "empty",
                tokensUsed = null
            )
        }
        
        // Check for native tool calls in message (OpenAI format)
        val nativeToolCalls = message?.toolCalls?.map { tc ->
            val args = try {
                val json = JSONObject(tc.function.arguments)
                json.keys().asSequence().associateWith { key -> json.opt(key) }
            } catch (e: Exception) {
                emptyMap<String, Any?>()
            }
            
            ToolCall(
                id = tc.id ?: java.util.UUID.randomUUID().toString(),
                name = tc.function.name,
                arguments = args
            )
        }
        
        // Parse tool calls from content (regex fallback)
        val contentToolCalls = if (nativeToolCalls.isNullOrEmpty()) {
            parseToolCallsFromContent(rawContent)
        } else null
        
        val toolCalls = nativeToolCalls ?: contentToolCalls
        
        val cleanContent = if (!toolCalls.isNullOrEmpty()) {
            rawContent
                .replace(Regex("""<tool_call>.*?</tool_call>""", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("""\{"tool"\s*:\s*"[^"]+"[^}]*\}""", RegexOption.DOT_MATCHES_ALL), "")
                .trim()
        } else {
            rawContent
        }
        
        Log.d(TAG, "parseResponse: toolCalls=${toolCalls?.size ?: 0}, cleanContent=${cleanContent.take(100)}")
        
        return ChatResponse(
            content = cleanContent.ifEmpty { null },
            toolCalls = toolCalls?.ifEmpty { null },
            finishReason = response.choices?.firstOrNull()?.finishReason,
            tokensUsed = null
        )
    }
    
    /**
     * Parse tool calls from content using various formats.
     * Supports: <tool_call>...</tool_call>, {"tool": "..."}, ```json...```
     */
    private fun parseToolCallsFromContent(content: String): List<ToolCall> {
        val toolCalls = mutableListOf<ToolCall>()
        
        // Pattern 1: <tool_call> tags
        val toolCallTagPattern = Regex("""<tool_call>\s*(\{.*?\})\s*</tool_call>""", RegexOption.DOT_MATCHES_ALL)
        toolCallTagPattern.findAll(content).forEach { match ->
            try {
                val json = JSONObject(match.groupValues[1])
                val name = json.optString("name", json.optString("tool", ""))
                val args = parseArguments(json.opt("arguments") ?: json)
                if (name.isNotEmpty()) {
                    toolCalls.add(ToolCall(
                        id = java.util.UUID.randomUUID().toString(),
                        name = name,
                        arguments = args
                    ))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse tool_call tag: ${e.message}")
            }
        }
        
        // Pattern 2: {"tool": "..."} format
        if (toolCalls.isEmpty()) {
            val toolJsonPattern = Regex("""\{"tool"\s*:\s*"([^"]+)"[^}]*\}""", RegexOption.DOT_MATCHES_ALL)
            toolJsonPattern.findAll(content).forEach { match ->
                try {
                    val jsonStr = match.value
                    val json = JSONObject(jsonStr)
                    val name = json.optString("tool", "")
                    
                    // Extract all non-tool keys as arguments
                    val args = mutableMapOf<String, Any?>()
                    json.keys().forEach { key ->
                        if (key != "tool") {
                            args[key] = json.opt(key)
                        }
                    }
                    
                    if (name.isNotEmpty()) {
                        toolCalls.add(ToolCall(
                            id = java.util.UUID.randomUUID().toString(),
                            name = name,
                            arguments = args
                        ))
                        Log.d(TAG, "Parsed tool call: $name with args: $args")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse tool JSON: ${e.message}")
                }
            }
        }
        
        // Pattern 3: function_call or tool_call JSON blocks in code fences
        if (toolCalls.isEmpty()) {
            val jsonBlockPattern = Regex("""```(?:json)?\s*(\{.*?\})\s*```""", RegexOption.DOT_MATCHES_ALL)
            jsonBlockPattern.findAll(content).forEach { match ->
                try {
                    val json = JSONObject(match.groupValues[1])
                    val name = json.optString("function_call", "")
                        .ifEmpty { json.optString("tool_call", "") }
                        .ifEmpty { json.optString("name", "") }
                        .ifEmpty { json.optString("tool", "") }
                    val args = parseArguments(json.opt("arguments") ?: json.opt("parameters") ?: json)
                    if (name.isNotEmpty()) {
                        toolCalls.add(ToolCall(
                            id = java.util.UUID.randomUUID().toString(),
                            name = name,
                            arguments = args
                        ))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse code fence JSON: ${e.message}")
                }
            }
        }
        
        return toolCalls
    }
    
    private fun parseArguments(argsObj: Any?): Map<String, Any?> {
        return when (argsObj) {
            is JSONObject -> {
                val map = mutableMapOf<String, Any?>()
                argsObj.keys().forEach { key ->
                    // Skip 'tool' and 'name' keys when extracting args
                    if (key !in listOf("tool", "name", "function_call", "tool_call")) {
                        map[key] = argsObj.get(key)
                    }
                }
                map
            }
            is String -> {
                try {
                    val json = JSONObject(argsObj)
                    val map = mutableMapOf<String, Any?>()
                    json.keys().forEach { key ->
                        if (key !in listOf("tool", "name", "function_call", "tool_call")) {
                            map[key] = json.get(key)
                        }
                    }
                    map
                } catch (_: Exception) {
                    emptyMap()
                }
            }
            else -> emptyMap()
        }
    }

    private fun buildToolDefinitions(tools: List<Map<String, Any>>?): List<OllamaToolDef>? {
        if (tools.isNullOrEmpty()) return null
        
        return tools.mapNotNull { toolDef ->
            try {
                val name = toolDef["name"] as? String ?: return@mapNotNull null
                val description = toolDef["description"] as? String ?: ""
                @Suppress("UNCHECKED_CAST")
                val parameters = toolDef["parameters"] as? Map<String, Any?> ?: emptyMap()
                
                OllamaToolDef(
                    function = OllamaFunctionDef(
                        name = name,
                        description = description,
                        parameters = parameters
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to convert tool definition: ${e.message}")
                null
            }
        }
    }
}

/**
 * Exception thrown by OllamaProvider operations.
 */
class OllamaProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)
