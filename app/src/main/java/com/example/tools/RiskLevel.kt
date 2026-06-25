package com.example.tools

enum class RiskLevel {
    SAFE,              // Read-only operations, no approval needed
    APPROVAL_REQUIRED, // May modify state, user approval needed
    DANGEROUS          // Can delete/execute code, always confirm
}
