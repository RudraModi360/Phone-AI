package com.example.memory

import com.example.data.MemoryEntry

/**
 * Extracts user preferences, identity, and corrections from messages.
 * Pattern-based extraction — no LLM call needed, runs instantly.
 */
object UserMessageExtractor {

    data class ExtractionResult(
        val type: MemoryType,
        val title: String,
        val content: String,
        val confidence: Float
    )

    /**
     * Analyze a user message and return any extractable memories.
     * Returns empty list if nothing worth remembering is found.
     */
    fun extract(userMessage: String): List<ExtractionResult> {
        val results = mutableListOf<ExtractionResult>()
        val msg = userMessage.trim()

        // === IDENTITY EXTRACTION ===
        // "My name is X" / "Call me X" / "I'm X"
        extractPattern(msg, listOf(
            Regex("(?:my name is|call me|i'm|i am) ([A-Z][a-z]+(?: [A-Z][a-z]+)*)", RegexOption.IGNORE_CASE)
        ))?.let { name ->
            results.add(ExtractionResult(
                type = MemoryType.USER,
                title = "User Name",
                content = name.trim(),
                confidence = 0.95f
            ))
        }

        // "I work as X" / "I'm a X" / "My role is X"
        extractPattern(msg, listOf(
            Regex("(?:i work as|i'm a|i am a|my role is|my job is|profession[?:]?\\s+) ([a-zA-Z ]{2,30})", RegexOption.IGNORE_CASE)
        ))?.let { role ->
            results.add(ExtractionResult(
                type = MemoryType.USER,
                title = "User Role",
                content = role.trim(),
                confidence = 0.9f
            ))
        }

        // === PREFERENCE EXTRACTION ===
        // "I prefer X" / "I like X" / "I love X"
        extractPattern(msg, listOf(
            Regex("(?:i prefer|i like|i love|i enjoy|favorite|favourite) (.+?)(?:\\.|!|$)", RegexOption.IGNORE_CASE)
        ))?.let { pref ->
            if (pref.length in 3..80) {
                results.add(ExtractionResult(
                    type = MemoryType.USER,
                    title = "Preference: ${pref.take(25)}",
                    content = pref.trim(),
                    confidence = 0.85f
                ))
            }
        }

        // "I use X" / "I always X" / "I never X"
        extractPattern(msg, listOf(
            Regex("(?:i always|i usually|i typically|i never|i often) (.+?)(?:\\.|!|$)", RegexOption.IGNORE_CASE)
        ))?.let { habit ->
            if (habit.length in 3..80) {
                results.add(ExtractionResult(
                    type = MemoryType.USER,
                    title = "Habit: ${habit.take(25)}",
                    content = habit.trim(),
                    confidence = 0.8f
                ))
            }
        }

        // === CORRECTION / FEEDBACK EXTRACTION ===
        // "No, ..." / "Don't ..." / "Stop ..." / "Actually, ..."
        if (msg.startsWith("no,", ignoreCase = true) ||
            msg.startsWith("no.", ignoreCase = true) ||
            msg.startsWith("don't ", ignoreCase = true) ||
            msg.startsWith("do not ", ignoreCase = true) ||
            msg.startsWith("stop ", ignoreCase = true) ||
            msg.startsWith("actually,", ignoreCase = true) ||
            msg.contains("i meant ", ignoreCase = true) ||
            msg.contains("i don't like", ignoreCase = true)) {
            results.add(ExtractionResult(
                type = MemoryType.FEEDBACK,
                title = "Correction: ${msg.take(30)}",
                content = msg.take(150),
                confidence = 0.9f
            ))
        }

        // === PROJECT CONTEXT EXTRACTION ===
        // "My project uses X" / "We're building X" / "I'm working on X"
        extractPattern(msg, listOf(
            Regex("(?:my project (?:uses|is built with|uses tech)|we(?:'re| are) (?:building|using|working with)|i(?:'m| am) (?:building|using|working with)) (.+?)(?:\\.|!|$)", RegexOption.IGNORE_CASE)
        ))?.let { tech ->
            if (tech.length in 2..60) {
                results.add(ExtractionResult(
                    type = MemoryType.PROJECT,
                    title = "Tech Stack: ${tech.take(25)}",
                    content = tech.trim(),
                    confidence = 0.85f
                ))
            }
        }

        return results.filter { it.confidence >= 0.7f }
    }

    /**
     * Try multiple regex patterns, return first match group 1.
     */
    private fun extractPattern(text: String, patterns: List<Regex>): String? {
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1].trim()
            }
        }
        return null
    }
}
