package com.example.memory

object MemoryPromptBuilder {

    fun buildSystemPromptSection(): String {
        return """
# Memory System

You have persistent memory across sessions. Memories are stored and recalled automatically.

## Memory Types
- **user**: User's role, goals, responsibilities, knowledge. Always private.
- **feedback**: Corrections and confirmations from the user. Default private.
- **project**: Ongoing work not derivable from code. Bias toward team.
- **reference**: Pointers to external systems. Usually team.

## When to Access Memory
- When the user asks about something you should know from past conversations
- When the user's question relates to their role, preferences, or project context
- When you need to recall previous decisions or corrections

## When to Save Memory
- When the user corrects you and you learn the "right way"
- When the user shares personal context (name, role, family, preferences)
- When the user describes project conventions or tech stack
- When you discover a pattern in user behavior

## Trusting Recall
Memories are surfaced automatically when relevant. You don't need to ask the user to repeat information.
""".trim()
    }
}
