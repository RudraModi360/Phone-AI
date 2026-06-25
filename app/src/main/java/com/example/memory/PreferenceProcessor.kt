package com.example.memory

import com.example.data.MemoryEntry

/**
 * Processes user preferences using Claude Code's pipeline:
 * Deduplicate → Categorize → Rank → Summarize
 *
 * Memory budget: max 500 chars for context block injection.
 */
object PreferenceProcessor {

    private const val MAX_CONTEXT_CHARS = 800

    /**
     * Full processing pipeline. Call this before injecting into system prompt.
     */
    fun process(preferences: List<MemoryEntry>): List<ProcessedPreference> {
        val deduplicated = deduplicate(preferences)
        val categorized = categorize(deduplicated)
        val ranked = rank(categorized)
        return summarize(ranked)
    }

    /**
     * Step 1: Remove duplicate preferences (same title = same preference).
     * Keep the one with highest relevanceScore or most recent.
     */
    private fun deduplicate(preferences: List<MemoryEntry>): List<MemoryEntry> {
        return preferences.groupBy { it.title.lowercase().trim() }
            .map { (_, group) ->
                group.maxByOrNull { it.relevanceScore } ?: group.first()
            }
    }

    /**
     * Step 2: Categorize by priority tier.
     * Tier 1 (critical): explicit corrections, "always/never" patterns
     * Tier 2 (important): stated preferences, recurring patterns
     * Tier 3 (contextual): implicit preferences, situational
     */
    private fun categorize(preferences: List<MemoryEntry>): List<CategorizedPreference> {
        return preferences.map { pref ->
            val tier = when {
                pref.content.contains("always", ignoreCase = true) ||
                pref.content.contains("never", ignoreCase = true) ||
                pref.content.contains("must", ignoreCase = true) ||
                pref.content.contains("do not", ignoreCase = true) -> Tier.CRITICAL

                pref.content.contains("prefer", ignoreCase = true) ||
                pref.content.contains("like", ignoreCase = true) ||
                pref.content.contains("want", ignoreCase = true) ||
                pref.usageCount >= 3 -> Tier.IMPORTANT

                else -> Tier.CONTEXTUAL
            }
            CategorizedPreference(entry = pref, tier = tier)
        }
    }

    /**
     * Step 3: Rank by tier priority, then relevanceScore, then usageCount.
     */
    private fun rank(preferences: List<CategorizedPreference>): List<CategorizedPreference> {
        return preferences.sortedWith(
            compareByDescending<CategorizedPreference> { it.tier.priority }
                .thenByDescending { it.entry.relevanceScore }
                .thenByDescending { it.entry.usageCount }
        )
    }

    /**
     * Step 4: Summarize to fit within memory budget.
     * Truncates content to 80 chars per preference, caps total at MAX_CONTEXT_CHARS.
     */
    private fun summarize(preferences: List<CategorizedPreference>): List<ProcessedPreference> {
        val result = mutableListOf<ProcessedPreference>()
        var totalChars = 0

        for (cat in preferences) {
            val summarized = cat.entry.content.take(80).let {
                if (cat.entry.content.length > 80) "$it..." else it
            }
            val entry = ProcessedPreference(
                title = cat.entry.title,
                content = summarized,
                tier = cat.tier,
                memoryType = cat.entry.memoryType,
                relevanceScore = cat.entry.relevanceScore
            )
            val entryChars = entry.title.length + entry.content.length + 4 // ": " + "\n"
            if (totalChars + entryChars > MAX_CONTEXT_CHARS) break
            totalChars += entryChars
            result.add(entry)
        }
        return result
    }

    /**
     * Format processed preferences for system prompt injection.
     * Compact format: one line per preference.
     */
    fun formatForPrompt(processed: List<ProcessedPreference>): String {
        if (processed.isEmpty()) return ""
        return buildString {
            appendLine("### User Preferences")
            processed.forEach { pref ->
                val tierIcon = when (pref.tier) {
                    Tier.CRITICAL -> "!"
                    Tier.IMPORTANT -> "*"
                    Tier.CONTEXTUAL -> "-"
                }
                appendLine("$tierIcon **${pref.title}**: ${pref.content}")
            }
        }
    }
}

enum class Tier(val priority: Int) {
    CRITICAL(3),
    IMPORTANT(2),
    CONTEXTUAL(1)
}

data class CategorizedPreference(
    val entry: MemoryEntry,
    val tier: Tier
)

data class ProcessedPreference(
    val title: String,
    val content: String,
    val tier: Tier,
    val memoryType: String,
    val relevanceScore: Float
)
