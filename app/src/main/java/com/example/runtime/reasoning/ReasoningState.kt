package com.example.runtime.reasoning

import java.time.Instant

data class ReasoningState(
    var currentLevel: ReasoningLevel,
    val originalLevel: ReasoningLevel,
    var escalationCount: Int = 0,
    var deEscalationCount: Int = 0,
    var lastAdjustment: Instant? = null,
    val adjustmentHistory: MutableList<ReasoningAdjustment> = mutableListOf()
)

data class ReasoningAdjustment(
    val from: ReasoningLevel,
    val to: ReasoningLevel,
    val reason: String,
    val timestamp: Instant = Instant.now()
)
