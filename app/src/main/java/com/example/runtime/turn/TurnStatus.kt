package com.example.runtime.turn

enum class TurnStatus {
    PENDING,    // Turn created but not started
    ACTIVE,     // Turn currently executing
    COMPLETED,  // Turn finished successfully
    FAILED,     // Turn finished with error
    CANCELLED,  // Turn was cancelled
    TIMEOUT     // Turn exceeded time limit
}
