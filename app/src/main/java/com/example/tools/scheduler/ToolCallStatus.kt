package com.example.tools.scheduler

enum class ToolCallStatus {
    SCHEDULED,     // Queued for execution
    VALIDATING,    // Validating arguments
    EXECUTING,     // Currently running
    SUCCESS,       // Completed successfully
    ERROR,         // Failed with error
    CANCELLED,     // Cancelled before completion
    TIMEOUT,       // Timed out
    DEDUPLICATED   // Skipped as duplicate
}
