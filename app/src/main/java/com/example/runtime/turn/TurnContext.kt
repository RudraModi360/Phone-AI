package com.example.runtime.turn

import java.time.Instant
import java.util.UUID

data class TurnContext(
    val turnId: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val turnNumber: Int,
    var status: TurnStatus = TurnStatus.PENDING,
    val parentTurnId: String? = null,
    
    // Timing
    val createdAt: Instant = Instant.now(),
    var startedAt: Instant? = null,
    var endedAt: Instant? = null,
    
    // Execution metadata
    var toolCalls: Int = 0,
    var tokensInput: Int = 0,
    var tokensOutput: Int = 0,
    
    // Error information
    var error: String? = null,
    var errorType: String? = null,
    
    // Recovery
    var recoveryAttempts: Int = 0,
    val recoveryActions: MutableList<String> = mutableListOf()
) {
    val durationMs: Long?
        get() = if (startedAt != null && endedAt != null) {
            java.time.Duration.between(startedAt, endedAt).toMillis()
        } else null
    
    val isTerminal: Boolean
        get() = status in listOf(
            TurnStatus.COMPLETED,
            TurnStatus.FAILED,
            TurnStatus.CANCELLED,
            TurnStatus.TIMEOUT
        )
    
    fun start() {
        status = TurnStatus.ACTIVE
        startedAt = Instant.now()
    }
    
    fun complete() {
        status = TurnStatus.COMPLETED
        endedAt = Instant.now()
    }
    
    fun fail(errorMessage: String, type: String? = null) {
        status = TurnStatus.FAILED
        error = errorMessage
        errorType = type
        endedAt = Instant.now()
    }
    
    fun timeout() {
        status = TurnStatus.TIMEOUT
        endedAt = Instant.now()
    }
}
