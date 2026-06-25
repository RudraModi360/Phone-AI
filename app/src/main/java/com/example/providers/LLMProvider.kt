package com.example.providers

import kotlinx.coroutines.flow.Flow

/**
 * Capabilities that an LLM provider may support.
 */
enum class ProviderCapability {
    CHAT,           // Basic chat completions
    STREAMING,      // Streaming responses
    TOOLS,          // Function/tool calling
    VISION,         // Image understanding
    EMBEDDINGS,     // Text embeddings
    JSON_MODE       // Structured JSON output
}

/**
 * A message in the chat conversation.
 */
data class ChatMessage(
    val role: String,           // user, assistant, system, tool
    val content: String,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val name: String? = null    // For tool results
)

/**
 * A tool call requested by the model.
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, Any?>
)

/**
 * Response from a chat completion.
 */
data class ChatResponse(
    val content: String?,
    val toolCalls: List<ToolCall>?,
    val finishReason: String?,
    val tokensUsed: TokenUsage?
)

/**
 * Token usage statistics.
 */
data class TokenUsage(
    val input: Int,
    val output: Int
) {
    val total: Int get() = input + output
}

/**
 * A chunk of streamed response.
 */
data class StreamChunk(
    val content: String?,
    val toolCalls: List<ToolCall>?,
    val isComplete: Boolean
)

/**
 * Base interface for all LLM providers.
 * Implementations handle the specifics of each API (Ollama, Gemini, OpenAI, etc.)
 */
interface LLMProvider {
    /** Provider name (e.g., "ollama", "gemini") */
    val name: String
    
    /** Capabilities this provider supports */
    val capabilities: Set<ProviderCapability>
    
    /**
     * Send a chat completion request.
     * 
     * @param messages The conversation history
     * @param tools Optional list of tool definitions
     * @param systemInstruction Optional system prompt
     * @return The model's response
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any>>? = null,
        systemInstruction: String? = null
    ): ChatResponse
    
    /**
     * Send a streaming chat completion request.
     * 
     * @param messages The conversation history
     * @param tools Optional list of tool definitions
     * @param systemInstruction Optional system prompt
     * @return Flow of response chunks
     */
    fun chatStream(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any>>? = null,
        systemInstruction: String? = null
    ): Flow<StreamChunk>
    
    /**
     * Check if the provider is available and responding.
     */
    suspend fun healthCheck(): Boolean
    
    /**
     * Check if a capability is supported.
     */
    fun supports(capability: ProviderCapability): Boolean = capability in capabilities
}
