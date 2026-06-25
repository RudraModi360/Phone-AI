package com.example.runtime.config

import com.example.runtime.reasoning.ReasoningLevel

data class RuntimeConfig(
    // Turn Management
    val maxTurns: Int = 40,
    val turnTimeoutMs: Long = 120_000L, // 2 minutes per turn
    
    // Loop Detection
    val loopDetectionEnabled: Boolean = true,
    val toolCallThreshold: Int = 3,  // Same tool called 3x = loop
    val contentRepetitionThreshold: Int = 3,
    val contentChunkSize: Int = 100,
    val maxContentHistory: Int = 20,
    val stagnantTurnsThreshold: Int = 5,
    
    // Reasoning
    val defaultReasoningLevel: ReasoningLevel = ReasoningLevel.MEDIUM,
    val autoEscalate: Boolean = true,
    val autoEscalateKeywords: List<String> = listOf(
        "analyze", "debug", "architect", "design", "complex",
        "investigate", "optimize", "refactor", "security"
    ),
    
    // Tool Execution
    val toolTimeoutMs: Long = 30_000L,
    val maxRetryAttempts: Int = 3,
    val deduplicationTtlMs: Long = 300_000L, // 5 minutes
    
    // Telemetry
    val telemetryEnabled: Boolean = true
) {
    companion object {
        fun default() = RuntimeConfig()
        
        fun minimal() = RuntimeConfig(
            maxTurns = 10,
            loopDetectionEnabled = false,
            autoEscalate = false
        )
        
        fun deep() = RuntimeConfig(
            maxTurns = 100,
            defaultReasoningLevel = ReasoningLevel.HIGH,
            autoEscalate = true
        )
    }
}
