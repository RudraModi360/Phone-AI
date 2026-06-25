package com.example.tools.scheduler

import java.time.Instant

data class ToolCallResult(
    val callId: String,
    val name: String,
    val status: ToolCallStatus,
    val result: String? = null,
    val error: String? = null,
    val errorType: String? = null,
    val startedAt: Instant? = null,
    val endedAt: Instant? = null,
    val attempts: Int = 1,
    val reusedFrom: String? = null  // callId if deduplicated
) {
    val durationMs: Long?
        get() = if (startedAt != null && endedAt != null) {
            java.time.Duration.between(startedAt, endedAt).toMillis()
        } else null
    
    val success: Boolean
        get() = status == ToolCallStatus.SUCCESS
}
