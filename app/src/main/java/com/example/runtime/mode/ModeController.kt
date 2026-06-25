package com.example.runtime.mode

import android.util.Log
import com.example.runtime.reasoning.ReasoningLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * Mode transition event for UI updates.
 */
data class ModeTransitionEvent(
    val fromMode: AgentMode?,
    val toMode: AgentMode,
    val reason: String,
    val timestamp: Instant = Instant.now()
)

/**
 * Controls agent mode transitions and mode-specific behaviors.
 * 
 * Manages:
 * - Mode switching with validation
 * - Reasoning level overrides per mode
 * - Mode-specific system prompt generation
 * - Transition events for UI
 */
class ModeController {
    
    private val _currentMode = MutableStateFlow(AgentMode.EXECUTION)
    val currentMode: StateFlow<AgentMode> = _currentMode.asStateFlow()
    
    private val _currentConfig = MutableStateFlow(AgentModeConfig(AgentMode.EXECUTION))
    val currentConfig: StateFlow<AgentModeConfig> = _currentConfig.asStateFlow()
    
    private val _reasoningLevelOverride = MutableStateFlow<ReasoningLevel?>(null)
    val reasoningLevelOverride: StateFlow<ReasoningLevel?> = _reasoningLevelOverride.asStateFlow()
    
    private val _lastTransition = MutableStateFlow<ModeTransitionEvent?>(null)
    val lastTransition: StateFlow<ModeTransitionEvent?> = _lastTransition.asStateFlow()
    
    private val modeHistory = java.util.concurrent.CopyOnWriteArrayList<ModeTransitionEvent>()
    
    /**
     * Get the effective reasoning level (override or mode default).
     */
    val effectiveReasoningLevel: ReasoningLevel
        get() = _reasoningLevelOverride.value ?: _currentMode.value.defaultReasoningLevel
    
    /**
     * Switch to a new agent mode.
     */
    fun setMode(mode: AgentMode, reason: String = "user_selection") {
        val oldMode = _currentMode.value
        if (oldMode == mode) return
        
        Log.d("ModeController", "Mode transition: $oldMode -> $mode (reason: $reason)")
        
        _currentMode.value = mode
        _currentConfig.value = AgentModeConfig(
            mode = mode,
            reasoningLevel = _reasoningLevelOverride.value ?: mode.defaultReasoningLevel
        )
        
        val event = ModeTransitionEvent(
            fromMode = oldMode,
            toMode = mode,
            reason = reason
        )
        modeHistory.add(event)
        _lastTransition.value = event
    }
    
    /**
     * Override reasoning level independently of mode.
     */
    fun setReasoningLevel(level: ReasoningLevel?) {
        _reasoningLevelOverride.value = level
        
        // Update config with new reasoning level
        _currentConfig.value = _currentConfig.value.copy(
            reasoningLevel = level ?: _currentMode.value.defaultReasoningLevel
        )
        
        Log.d("ModeController", "Reasoning level override: $level")
    }
    
    /**
     * Clear reasoning level override, reverting to mode default.
     */
    fun clearReasoningOverride() {
        setReasoningLevel(null)
    }
    
    /**
     * Get the complete system prompt for current mode and reasoning level.
     */
    fun getSystemPrompt(basePrompt: String): String {
        val modeAddon = _currentConfig.value.getSystemPromptAddon()
        val reasoningAddon = effectiveReasoningLevel.systemPromptAddon
        
        return buildString {
            append(basePrompt)
            append("\n\n")
            append(modeAddon)
            append("\n\n")
            append("## REASONING LEVEL: ${effectiveReasoningLevel.name}\n")
            append(reasoningAddon)
        }
    }
    
    /**
     * Check if current mode allows automatic tool execution.
     */
    fun canAutoExecuteTools(): Boolean {
        return _currentConfig.value.autoExecuteTools
    }
    
    /**
     * Check if current mode requires plan approval.
     */
    fun requiresPlanApproval(): Boolean {
        return _currentConfig.value.requirePlanApproval
    }
    
    /**
     * Check if thinking process should be shown.
     */
    fun shouldShowThinking(): Boolean {
        return _currentConfig.value.showThinkingProcess
    }
    
    /**
     * Get max iterations for current mode.
     */
    fun getMaxIterations(): Int {
        return _currentConfig.value.maxIterations
    }
    
    /**
     * Get transition history for telemetry.
     */
    fun getTransitionHistory(): List<ModeTransitionEvent> {
        return modeHistory.toList()
    }
    
    /**
     * Reset to default mode (EXECUTION).
     */
    fun reset() {
        setMode(AgentMode.EXECUTION, "reset")
        clearReasoningOverride()
    }
    
    /**
     * Suggest a mode based on query analysis.
     */
    fun suggestModeForQuery(query: String): AgentMode {
        val queryLower = query.lowercase()
        
        // Planning indicators
        val planningPatterns = listOf(
            "plan", "how should i", "what steps", "break down",
            "strategy", "approach for", "roadmap", "outline"
        )
        if (planningPatterns.any { queryLower.contains(it) }) {
            return AgentMode.PLANNING
        }
        
        // Deep thinking indicators
        val thinkingPatterns = listOf(
            "why", "explain", "analyze", "compare", "evaluate",
            "pros and cons", "trade-off", "best approach", "deep dive",
            "reasoning", "implications", "consider"
        )
        if (thinkingPatterns.any { queryLower.contains(it) }) {
            return AgentMode.DEEP_THINKING
        }
        
        // Execution indicators (default)
        val executionPatterns = listOf(
            "do", "run", "execute", "create", "make", "build",
            "write", "fix", "install", "deploy", "show me"
        )
        if (executionPatterns.any { queryLower.contains(it) }) {
            return AgentMode.EXECUTION
        }
        
        // Default to current mode
        return _currentMode.value
    }
}
