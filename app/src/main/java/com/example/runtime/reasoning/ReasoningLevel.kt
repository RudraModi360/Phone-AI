package com.example.runtime.reasoning

enum class ReasoningLevel(
    val value: Int,
    val thinkingBudgetMs: Long,
    val tokenBudget: Int,
    val systemPromptAddon: String
) {
    MINIMAL(
        value = 1,
        thinkingBudgetMs = 0,
        tokenBudget = 512,
        systemPromptAddon = "Be concise. Give direct answers."
    ),
    LOW(
        value = 2,
        thinkingBudgetMs = 5_000,
        tokenBudget = 1024,
        systemPromptAddon = "Think briefly before answering."
    ),
    MEDIUM(
        value = 3,
        thinkingBudgetMs = 15_000,
        tokenBudget = 2048,
        systemPromptAddon = "Think step by step. Consider alternatives."
    ),
    HIGH(
        value = 4,
        thinkingBudgetMs = 60_000,
        tokenBudget = 4096,
        systemPromptAddon = "Think deeply. Analyze from multiple angles. Consider edge cases."
    ),
    DEEP(
        value = 5,
        thinkingBudgetMs = 300_000,
        tokenBudget = 8192,
        systemPromptAddon = "Think exhaustively. Explore all possibilities. Verify your reasoning."
    );
    
    companion object {
        fun fromValue(value: Int): ReasoningLevel {
            return values().find { it.value == value } ?: MEDIUM
        }
    }
}
