package com.example.runtime.loop.detectors

import com.example.runtime.loop.*

/**
 * Detects repeated content chunks in LLM output.
 */
class ContentDetector(
    private val threshold: Int = 3,
    private val chunkSize: Int = 100,
    private val maxHistory: Int = 20
) {
    private data class ContentChunk(val hash: Int, val content: String)
    
    private val sessionChunks = java.util.concurrent.ConcurrentHashMap<String, MutableList<ContentChunk>>()
    
    fun analyze(event: AgentEvent, sessionId: String): LoopDetectionResult {
        if (event.type != AgentEventType.CONTENT || event.content.isNullOrBlank()) {
            return LoopDetectionResult(detected = false)
        }
        
        val content = event.content
        
        // Extract chunks
        val chunks = content.chunked(chunkSize).map { chunk ->
            ContentChunk(chunk.hashCode(), chunk)
        }
        
        val recentChunks = sessionChunks.computeIfAbsent(sessionId) { mutableListOf() }
        
        // Check for repetition
        for (chunk in chunks) {
            val occurrences = recentChunks.count { it.hash == chunk.hash }
            
            if (occurrences >= threshold) {
                return LoopDetectionResult(
                    detected = true,
                    loopType = LoopType.CONTENT_REPETITION,
                    confidence = minOf(1f, (occurrences + 1) / threshold.toFloat()),
                    detail = "Content chunk repeated ${occurrences + 1} times",
                    repetitionCount = occurrences + 1,
                    suggestedRecoveryAction = RecoveryAction.PROVIDE_CONTEXT_HINT
                )
            }
        }
        
        // Add chunks to history
        recentChunks.addAll(chunks)
        while (recentChunks.size > maxHistory * chunkSize / 100) {
            recentChunks.removeAt(0)
        }
        
        return LoopDetectionResult(detected = false)
    }
    
    fun resetSession(sessionId: String) {
        sessionChunks.remove(sessionId)
    }
    
    fun reset() {
        sessionChunks.clear()
    }
}
