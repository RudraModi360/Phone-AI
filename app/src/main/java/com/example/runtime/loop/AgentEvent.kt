package com.example.runtime.loop

import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant

enum class AgentEventType {
    TOOL_CALL,
    TOOL_RESULT,
    CONTENT,
    TURN_START,
    TURN_END
}

data class AgentEvent(
    val type: AgentEventType,
    val timestamp: Instant = Instant.now(),
    
    // Tool call event data
    val toolName: String? = null,
    val toolArgs: Map<String, Any?>? = null,
    val toolResult: String? = null,
    val toolSuccess: Boolean? = null,
    
    // Content event data
    val content: String? = null,
    
    // Turn event data
    val turnId: String? = null,
    val turnNumber: Int? = null
) {
    fun getToolCallHash(): String? {
        if (type != AgentEventType.TOOL_CALL || toolName == null) return null
        
        val argsJson = JSONObject(toolArgs ?: emptyMap<String, Any?>()).toString()
        val key = "$toolName:$argsJson"
        return MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
