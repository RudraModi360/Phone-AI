package com.example.runtime

import com.example.runtime.config.RuntimeConfig
import com.example.runtime.turn.*
import com.example.runtime.loop.*
import com.example.runtime.reasoning.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Production-grade orchestrator for agent execution.
 * 
 * Combines all runtime components into a unified interface:
 * - Bounded turn execution
 * - Multi-layer loop detection with recovery
 * - Dynamic reasoning control
 * - Comprehensive telemetry
 */
class AgentRuntime(
    val config: RuntimeConfig = RuntimeConfig()
) {
    // Components
    val turnManager = TurnManager(config)
    val loopEngine = LoopDetectionEngine(config)
    val reasoningController = ReasoningController(config)
    
    // Event stream for UI
    private val _events = MutableSharedFlow<RuntimeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RuntimeEvent> = _events.asSharedFlow()
    
    init {
        setupTelemetryHooks()
    }
    
    private fun setupTelemetryHooks() {
        // Turn lifecycle events
        turnManager.registerOnTurnStart { turn ->
            _events.emit(RuntimeEvent.TurnStarted(turn))
        }
        
        turnManager.registerOnTurnEnd { turn ->
            _events.emit(RuntimeEvent.TurnCompleted(turn))
        }
        
        turnManager.registerOnBudgetExceeded { turn ->
            _events.emit(RuntimeEvent.BudgetExceeded(turn))
        }
        
        // Loop detection events
        loopEngine.registerOnDetection { result ->
            _events.emit(RuntimeEvent.LoopDetected(result))
        }
        
        // Reasoning change events
        reasoningController.onLevelChange { from, to ->
            _events.tryEmit(RuntimeEvent.ReasoningLevelChanged(from, to))
        }
    }
    
    /**
     * Execute an agentic turn with full orchestration.
     */
    suspend fun <T> executeTurn(
        sessionId: String,
        block: suspend TurnExecutionContext.() -> T
    ): T {
        return turnManager.withTurn(sessionId) { turn ->
            val context = TurnExecutionContext(
                turn = turn,
                runtime = this@AgentRuntime
            )
            block(context)
        }
    }
    
    /**
     * Check for loop patterns in an event.
     */
    suspend fun checkLoop(event: AgentEvent, sessionId: String): LoopDetectionResult {
        return loopEngine.check(event, sessionId)
    }
    
    /**
     * Adjust reasoning based on query.
     */
    fun adjustReasoningForQuery(query: String): ReasoningLevel {
        return reasoningController.adjustForQuery(query)
    }
    
    /**
     * Get system prompt with reasoning addon.
     */
    fun getEnhancedSystemPrompt(basePrompt: String): String {
        val addon = reasoningController.getSystemPromptAddon()
        return "$basePrompt\n\n$addon"
    }
    
    /**
     * Get remaining turn budget.
     */
    suspend fun getRemainingTurns(sessionId: String): Int {
        return turnManager.getRemainingTurns(sessionId)
    }
    
    /**
     * Reset session state.
     */
    fun resetSession(sessionId: String) {
        loopEngine.resetSession(sessionId)
        reasoningController.reset()
    }
    
    companion object {
        fun create(config: RuntimeConfig = RuntimeConfig()): AgentRuntime {
            return AgentRuntime(config)
        }
    }
}

/**
 * Context available during turn execution.
 */
class TurnExecutionContext(
    val turn: TurnContext,
    private val runtime: AgentRuntime
) {
    fun recordToolCall() {
        turn.toolCalls++
    }
    
    fun recordTokens(input: Int, output: Int) {
        turn.tokensInput += input
        turn.tokensOutput += output
    }
    
    suspend fun checkLoop(event: AgentEvent): LoopDetectionResult {
        return runtime.checkLoop(event, turn.sessionId)
    }
    
    val currentReasoningLevel: ReasoningLevel
        get() = runtime.reasoningController.currentLevel
}

/**
 * Events emitted by the runtime for UI/telemetry.
 */
sealed class RuntimeEvent {
    data class TurnStarted(val turn: TurnContext) : RuntimeEvent()
    data class TurnCompleted(val turn: TurnContext) : RuntimeEvent()
    data class BudgetExceeded(val turn: TurnContext) : RuntimeEvent()
    data class LoopDetected(val result: LoopDetectionResult) : RuntimeEvent()
    data class ReasoningLevelChanged(
        val from: ReasoningLevel,
        val to: ReasoningLevel
    ) : RuntimeEvent()
    data class ToolExecuted(
        val toolName: String,
        val success: Boolean,
        val durationMs: Long
    ) : RuntimeEvent()
    data class ProgressUpdate(
        val message: String,
        val percent: Int
    ) : RuntimeEvent()
}
