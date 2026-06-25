package com.example.runtime.reasoning

import com.example.runtime.config.RuntimeConfig
import java.time.Instant

/**
 * Controls reasoning level dynamically during agent execution.
 * 
 * Features:
 * - Set reasoning level programmatically
 * - Auto-escalate on complex queries
 * - Track reasoning adjustments
 * - Generate appropriate system prompt addons
 */
class ReasoningController(
    private val config: RuntimeConfig = RuntimeConfig()
) {
    private var state = ReasoningState(
        currentLevel = config.defaultReasoningLevel,
        originalLevel = config.defaultReasoningLevel
    )
    
    private val levelChangeCallbacks = java.util.concurrent.CopyOnWriteArrayList<(ReasoningLevel, ReasoningLevel) -> Unit>()
    
    val currentLevel: ReasoningLevel
        get() = state.currentLevel
    
    val thinkingBudget: Long
        get() = state.currentLevel.thinkingBudgetMs
    
    val tokenBudget: Int
        get() = state.currentLevel.tokenBudget
    
    fun onLevelChange(callback: (ReasoningLevel, ReasoningLevel) -> Unit) {
        levelChangeCallbacks.add(callback)
    }
    
    /**
     * Set reasoning level explicitly.
     */
    fun setLevel(level: ReasoningLevel, reason: String = "manual") {
        val oldLevel = state.currentLevel
        if (oldLevel == level) return
        
        state.currentLevel = level
        state.lastAdjustment = Instant.now()
        state.adjustmentHistory.add(ReasoningAdjustment(
            from = oldLevel,
            to = level,
            reason = reason
        ))
        
        levelChangeCallbacks.forEach { it(oldLevel, level) }
    }
    
    /**
     * Escalate reasoning level by one step.
     */
    fun escalate(reason: String = "complexity_detected"): ReasoningLevel {
        val levels = ReasoningLevel.values()
        val currentIdx = levels.indexOf(state.currentLevel)
        
        if (currentIdx < levels.size - 1) {
            val newLevel = levels[currentIdx + 1]
            setLevel(newLevel, reason)
            state.escalationCount++
        }
        
        return state.currentLevel
    }
    
    /**
     * De-escalate reasoning level by one step.
     */
    fun deEscalate(reason: String = "simple_query"): ReasoningLevel {
        val levels = ReasoningLevel.values()
        val currentIdx = levels.indexOf(state.currentLevel)
        
        if (currentIdx > 0) {
            val newLevel = levels[currentIdx - 1]
            setLevel(newLevel, reason)
            state.deEscalationCount++
        }
        
        return state.currentLevel
    }
    
    /**
     * Reset to original reasoning level.
     */
    fun reset() {
        setLevel(state.originalLevel, "reset")
        state.escalationCount = 0
        state.deEscalationCount = 0
        state.adjustmentHistory.clear()
    }
    
    /**
     * Automatically adjust reasoning level based on query complexity.
     */
    fun adjustForQuery(query: String): ReasoningLevel {
        if (!config.autoEscalate) return state.currentLevel
        
        if (shouldEscalate(query)) {
            return escalate("auto_escalation_query_complexity")
        }
        
        if (shouldDeEscalate(query)) {
            return deEscalate("auto_de_escalation_simple_query")
        }
        
        return state.currentLevel
    }
    
    /**
     * Get system prompt addon for current reasoning level.
     */
    fun getSystemPromptAddon(): String {
        return state.currentLevel.systemPromptAddon
    }
    
    /**
     * Get current state for telemetry/debugging.
     */
    fun getState(): ReasoningState = state.copy(
        adjustmentHistory = state.adjustmentHistory.toMutableList()
    )
    
    // --- Private Helpers ---
    
    private fun shouldEscalate(query: String): Boolean {
        val queryLower = query.lowercase()
        
        // Check configured keywords
        if (config.autoEscalateKeywords.any { it in queryLower }) {
            return true
        }
        
        // Check for complexity patterns
        val complexPatterns = listOf(
            Regex("why does.+not work"),
            Regex("how (can|do|should) (i|we).+multiple"),
            Regex("what('s| is) the (best|optimal|right) (way|approach)"),
            Regex("debug.+(error|issue|problem|bug)"),
            Regex("(analyze|investigate|diagnose)"),
            Regex("step.?by.?step"),
            Regex("(comprehensive|thorough|detailed) (analysis|review|audit)")
        )
        
        return complexPatterns.any { it.containsMatchIn(queryLower) }
    }
    
    private fun shouldDeEscalate(query: String): Boolean {
        val queryLower = query.lowercase()
        
        val simplePatterns = listOf(
            Regex("^(what|who|when|where) is \\w+\\??$"),
            Regex("^(hi|hello|hey|thanks|thank you)"),
            Regex("^yes$|^no$|^ok$|^okay$"),
            Regex("^\\d+$")
        )
        
        return simplePatterns.any { it.matches(queryLower) }
    }
}
