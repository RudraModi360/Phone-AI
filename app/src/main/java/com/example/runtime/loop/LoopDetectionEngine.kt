package com.example.runtime.loop

import com.example.runtime.config.RuntimeConfig
import com.example.runtime.loop.detectors.*

/**
 * Multi-layer loop detection engine with pluggable detectors.
 * 
 * Combines multiple detection strategies:
 * 1. Hash-based: Identical tool calls (fast, exact)
 * 2. Content-based: Repeated output chunks (streaming-safe)
 * 3. Stagnant: No progress detection (state-based)
 */
class LoopDetectionEngine(private val config: RuntimeConfig) {
    
    private val hashDetector = HashDetector(config.toolCallThreshold)
    private val semanticDetector = SemanticDetector()
    private val ruleDetector = RuleDetector()
    private val contentDetector = ContentDetector(
        threshold = config.contentRepetitionThreshold,
        chunkSize = config.contentChunkSize,
        maxHistory = config.maxContentHistory
    )
    private val stagnantDetector = StagnantDetector(config.stagnantTurnsThreshold)
    
    private val disabledSessions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val onDetectionCallbacks = java.util.concurrent.CopyOnWriteArrayList<suspend (LoopDetectionResult) -> Unit>()
    
    fun registerOnDetection(callback: suspend (LoopDetectionResult) -> Unit) {
        onDetectionCallbacks.add(callback)
    }
    
    fun disableForSession(sessionId: String) {
        disabledSessions.add(sessionId)
    }
    
    fun enableForSession(sessionId: String) {
        disabledSessions.remove(sessionId)
    }
    
    fun isDisabled(sessionId: String): Boolean {
        return !config.loopDetectionEnabled || sessionId in disabledSessions
    }
    
    /**
     * Check an event for loop patterns.
     * Returns the highest-confidence detection result.
     */
    suspend fun check(event: AgentEvent, sessionId: String): LoopDetectionResult {
        if (isDisabled(sessionId)) {
            return LoopDetectionResult(detected = false)
        }
        
        // Run all detectors including Phase 2 (Semantic) and Phase 3 (Rule)
        val results = listOf(
            hashDetector.analyze(event, sessionId),
            semanticDetector.analyze(event, sessionId),
            ruleDetector.analyze(event, sessionId),
            contentDetector.analyze(event, sessionId),
            stagnantDetector.analyze(event, sessionId)
        )
        
        // Find highest confidence detection
        val detected = results
            .filter { it.detected }
            .maxByOrNull { it.confidence }
        
        if (detected != null) {
            onDetectionCallbacks.forEach { it(detected) }
        }
        
        return detected ?: LoopDetectionResult(detected = false)
    }
    
    /**
     * Get appropriate recovery action for a detection result.
     */
    fun getRecoveryAction(result: LoopDetectionResult): RecoveryAction {
        return result.suggestedRecoveryAction ?: when (result.loopType) {
            LoopType.CONSECUTIVE_TOOL_CALLS -> RecoveryAction.ESCALATE_REASONING
            LoopType.CONTENT_REPETITION -> RecoveryAction.PROVIDE_CONTEXT_HINT
            LoopType.SEMANTIC_LOOP -> RecoveryAction.CLEAR_STATE
            LoopType.STAGNANT_STATE -> RecoveryAction.INTERRUPT_EXECUTION
            LoopType.TOOL_RESULT_SIMILARITY -> RecoveryAction.SUGGEST_ALTERNATIVES
            null -> RecoveryAction.INTERRUPT_EXECUTION
        }
    }
    
    fun resetSession(sessionId: String) {
        hashDetector.resetSession(sessionId)
        semanticDetector.resetSession(sessionId)
        ruleDetector.resetSession(sessionId)
        contentDetector.resetSession(sessionId)
        stagnantDetector.resetSession(sessionId)
    }
}
