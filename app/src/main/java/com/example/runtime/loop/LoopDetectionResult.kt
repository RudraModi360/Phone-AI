package com.example.runtime.loop

data class LoopDetectionResult(
    val detected: Boolean = false,
    val loopType: LoopType? = null,
    val confidence: Float = 0f,
    val detail: String? = null,
    val repetitionCount: Int = 0,
    val suggestedRecoveryAction: RecoveryAction? = null
)

enum class RecoveryAction {
    ESCALATE_REASONING,    // Request deeper thinking
    PROVIDE_CONTEXT_HINT,  // Supply additional information
    SUGGEST_ALTERNATIVES,  // Try different approach
    INTERRUPT_EXECUTION,   // Ask user for guidance
    CLEAR_STATE           // Reset conversation context
}
