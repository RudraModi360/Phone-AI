package com.example.runtime.loop.detectors

import com.example.runtime.loop.*

/**
 * Detects when no progress is being made across multiple turns.
 */
class StagnantDetector(private val threshold: Int = 5) {
    
    private data class SessionState(var turnsWithoutProgress: Int = 0, var lastKnownState: String? = null)
    private val sessionStates = java.util.concurrent.ConcurrentHashMap<String, SessionState>()
    
    fun analyze(event: AgentEvent, sessionId: String): LoopDetectionResult {
        if (event.type != AgentEventType.TURN_END) {
            return LoopDetectionResult(detected = false)
        }
        
        val state = sessionStates.computeIfAbsent(sessionId) { SessionState() }
        
        // Simple heuristic: if content hasn't meaningfully changed
        val currentState = event.content?.take(200) ?: ""
        
        if (currentState == state.lastKnownState) {
            state.turnsWithoutProgress++
        } else {
            state.turnsWithoutProgress = 0
            state.lastKnownState = currentState
        }
        
        return if (state.turnsWithoutProgress >= threshold) {
            LoopDetectionResult(
                detected = true,
                loopType = LoopType.STAGNANT_STATE,
                confidence = minOf(1f, state.turnsWithoutProgress / threshold.toFloat()),
                detail = "No progress detected for ${state.turnsWithoutProgress} turns",
                repetitionCount = state.turnsWithoutProgress,
                suggestedRecoveryAction = RecoveryAction.INTERRUPT_EXECUTION
            )
        } else {
            LoopDetectionResult(detected = false)
        }
    }
    
    fun resetSession(sessionId: String) {
        sessionStates.remove(sessionId)
    }
    
    fun reset() {
        sessionStates.clear()
    }
}
