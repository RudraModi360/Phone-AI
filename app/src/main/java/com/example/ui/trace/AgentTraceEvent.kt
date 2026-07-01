package com.example.ui.trace

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AgentTraceEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val type: EventType,
    val title: String,
    val content: String,
    val metadata: Map<String, String> = emptyMap()
) {
    enum class EventType(val label: String, val icon: String) {
        SYSTEM_PROMPT("System Prompt", "\uD83D\uDCCB"),
        USER_MESSAGE("User Message", "\uD83D\uDC64"),
        API_REQUEST("API Request", "\u2B06\uFE0F"),
        API_RESPONSE("API Response", "\u2B07\uFE0F"),
        TOOL_CALL("Tool Call", "\u2699\uFE0F"),
        TOOL_RESULT("Tool Result", "\u2705"),
        MEMORY_RETRIEVAL("Memory Retrieval", "\uD83E\uDDE0"),
        MEMORY_EXTRACTION("Memory Extraction", "\uD83D\uDCBE"),
        MEMORY_CONTEXT("Memory Context", "\uD83D\uDD17"),
        ASSISTANT_REPLY("Assistant Reply", "\uD83D\uDD35"),
        ERROR("Error", "\u26A0\uFE0F"),
        PHASE("Phase", "\u23F3")
    }

    val timeString: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))

    val timeStringShort: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestamp))
}
