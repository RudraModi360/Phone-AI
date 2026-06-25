package com.example.runtime.loop.detectors

import com.example.runtime.loop.*
import java.util.Locale

/**
 * Phase 2 Loop Detector: Checks for semantic repetition and cycles in LLM responses.
 * Detects circular sentence models, continuous repetitive apology chunks, and
 * uses Jaccard similarity to calculate word-overlap over a sliding history window.
 */
class SemanticDetector(private val threshold: Float = 0.70f) {
    private val sessionTexts = java.util.concurrent.ConcurrentHashMap<String, MutableList<String>>()
    private val maxHistory = 5

    // Circular loop key phrasing lists
    private val cycleIndicators = listOf(
        listOf("apologize", "sorry for the confusion", "let me try"),
        listOf("not available", "cannot find"),
        listOf("i am an ai", "as a language model")
    )

    fun analyze(event: AgentEvent, sessionId: String): LoopDetectionResult {
        if (event.type != AgentEventType.CONTENT || event.content.isNullOrBlank()) {
            return LoopDetectionResult(detected = false)
        }

        val text = event.content.lowercase(Locale.ROOT).trim()
        if (text.length < 15) return LoopDetectionResult(detected = false)

        // Part A: Search for continuous apology excuses or circular structures
        for (indicatorGroup in cycleIndicators) {
            val containsAll = indicatorGroup.all { text.contains(it) }
            if (containsAll) {
                return LoopDetectionResult(
                    detected = true,
                    loopType = LoopType.SEMANTIC_LOOP,
                    confidence = 0.85f,
                    detail = "Semantic loop flagged: Found high repetition of cycle transition keywords like '${indicatorGroup.first()}'",
                    repetitionCount = 1,
                    suggestedRecoveryAction = RecoveryAction.CLEAR_STATE
                )
            }
        }

        val recentTexts = sessionTexts.computeIfAbsent(sessionId) { mutableListOf() }

        // Part B: Evaluate lexical Jaccard index overlap
        for (prevText in recentTexts) {
            val similarity = computeJaccardSimilarity(text, prevText)
            if (similarity >= threshold) {
                return LoopDetectionResult(
                    detected = true,
                    loopType = LoopType.SEMANTIC_LOOP,
                    confidence = similarity,
                    detail = "Semantic loop flagged: Sentence vocabulary overlap reaches Jaccard similarity of ${(similarity * 100).toInt()}%",
                    repetitionCount = 2,
                    suggestedRecoveryAction = RecoveryAction.CLEAR_STATE
                )
            }
        }

        recentTexts.add(text)
        if (recentTexts.size > maxHistory) {
            recentTexts.removeAt(0)
        }

        return LoopDetectionResult(detected = false)
    }

    private fun computeJaccardSimilarity(s1: String, s2: String): Float {
        val w1 = s1.split("\\s+".toRegex()).filter { it.length > 2 }.toSet()
        val w2 = s2.split("\\s+".toRegex()).filter { it.length > 2 }.toSet()
        if (w1.isEmpty() || w2.isEmpty()) return 0.0f
        
        val intersection = w1.intersect(w2).size.toFloat()
        val union = w1.union(w2).size.toFloat()
        return intersection / union
    }

    fun resetSession(sessionId: String) {
        sessionTexts.remove(sessionId)
    }

    fun reset() {
        sessionTexts.clear()
    }
}
