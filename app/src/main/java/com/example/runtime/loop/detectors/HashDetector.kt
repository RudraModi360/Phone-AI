package com.example.runtime.loop.detectors

import com.example.runtime.loop.*

/**
 * Detects consecutive identical tool calls by hashing tool name + args.
 */
class HashDetector(private val threshold: Int = 3) {
    
    private val sessionHashes = java.util.concurrent.ConcurrentHashMap<String, MutableList<String>>()
    private val maxHistory = 20
    
    fun analyze(event: AgentEvent, sessionId: String): LoopDetectionResult {
        if (event.type != AgentEventType.TOOL_CALL) {
            return LoopDetectionResult(detected = false)
        }
        
        val hash = event.getToolCallHash() ?: return LoopDetectionResult(detected = false)
        
        // Get list for this session
        val recentHashes = sessionHashes.computeIfAbsent(sessionId) { mutableListOf() }
        
        // Add to history
        recentHashes.add(hash)
        if (recentHashes.size > maxHistory) {
            recentHashes.removeAt(0)
        }
        
        // Count consecutive occurrences
        var consecutiveCount = 0
        for (i in recentHashes.indices.reversed()) {
            if (recentHashes[i] == hash) {
                consecutiveCount++
            } else {
                break
            }
        }
        
        return if (consecutiveCount >= threshold) {
            LoopDetectionResult(
                detected = true,
                loopType = LoopType.CONSECUTIVE_TOOL_CALLS,
                confidence = minOf(1f, consecutiveCount / threshold.toFloat()),
                detail = "Tool '${event.toolName}' called $consecutiveCount times consecutively",
                repetitionCount = consecutiveCount,
                suggestedRecoveryAction = RecoveryAction.ESCALATE_REASONING
            )
        } else {
            LoopDetectionResult(detected = false)
        }
    }
    
    fun resetSession(sessionId: String) {
        sessionHashes.remove(sessionId)
    }
    
    fun reset() {
        sessionHashes.clear()
    }
}
