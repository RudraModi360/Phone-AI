package com.example.runtime.loop.detectors

import com.example.runtime.loop.*

/**
 * Phase 3 Loop Detector: Checks operational rule boundaries such as consecutive tool failures.
 * This guarantees the agent breaks out of failing repetitive operations gracefully.
 */
class RuleDetector(private val maxConsecutiveFailures: Int = 3) {
    private data class SessionState(var consecutiveFailures: Int = 0, var lastFailedTool: String? = null)
    private val sessionStates = java.util.concurrent.ConcurrentHashMap<String, SessionState>()

    fun analyze(event: AgentEvent, sessionId: String): LoopDetectionResult {
        if (event.type == AgentEventType.TOOL_RESULT) {
            val isSuccess = event.toolSuccess ?: true
            val state = sessionStates.computeIfAbsent(sessionId) { SessionState() }
            if (!isSuccess) {
                val tool = event.toolName ?: "unknown"
                if (tool == state.lastFailedTool) {
                    state.consecutiveFailures++
                } else {
                    state.consecutiveFailures = 1
                    state.lastFailedTool = tool
                }
            } else {
                state.consecutiveFailures = 0
                state.lastFailedTool = null
            }

            if (state.consecutiveFailures >= maxConsecutiveFailures) {
                return LoopDetectionResult(
                    detected = true,
                    loopType = LoopType.STAGNANT_STATE,
                    confidence = 0.95f,
                    detail = "Rule policy triggered: Executed tool '${state.lastFailedTool}' has failed consecutively ${state.consecutiveFailures} times.",
                    repetitionCount = state.consecutiveFailures,
                    suggestedRecoveryAction = RecoveryAction.INTERRUPT_EXECUTION
                )
            }
        }
        return LoopDetectionResult(detected = false)
    }

    fun resetSession(sessionId: String) {
        sessionStates.remove(sessionId)
    }

    fun reset() {
        sessionStates.clear()
    }
}
