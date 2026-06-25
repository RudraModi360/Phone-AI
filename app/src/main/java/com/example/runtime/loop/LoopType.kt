package com.example.runtime.loop

enum class LoopType {
    CONSECUTIVE_TOOL_CALLS,  // Same tool called repeatedly
    CONTENT_REPETITION,      // Same output text repeated
    SEMANTIC_LOOP,           // LLM detects conversation cycling
    STAGNANT_STATE,          // No progress for N turns
    TOOL_RESULT_SIMILARITY   // Identical tool results
}
