package com.example.tools.scheduler

import java.time.Instant
import java.util.UUID

data class ToolCallRequest(
    val callId: String = UUID.randomUUID().toString(),
    val name: String,
    val args: Map<String, Any?>,
    val sessionId: String = "default",
    val turnId: String? = null,
    val createdAt: Instant = Instant.now(),
    val timeoutSeconds: Int = 30,
    val allowRetry: Boolean = true
) {
    /**
     * Get signature for deduplication.
     */
    fun getSignature(): String {
        val argsJson = args.entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}=${it.value}" }
        return "$name:$argsJson".hashCode().toString(16)
    }
}
