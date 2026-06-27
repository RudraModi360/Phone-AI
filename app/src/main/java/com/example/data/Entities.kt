package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey val id: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val model: String = "gemini-3.5-flash",
    val systemInstruction: String = "You are logy, a powerful AI assistant on your Android device.",
    val workspace: String = "My Projects"
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(index = true) val sessionId: String,
    val role: String, // "user", "model", "system", "tool"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    
    // For rich Tool Calls
    val toolName: String? = null,
    val toolArgs: String? = null, // JSON string of arguments
    val toolResult: String? = null, // JSON string of result
    val toolStatus: String? = null, // "pending", "approved", "denied", "success", "error"
    val riskLevel: String? = null, // "low", "medium", "high"
    val durationMs: Long? = null,
    
    // For single streaming response lifecycle
    val status: String = "Complete" // "Pending", "Streaming", "Complete", "Error"
)

@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "memory_entries")
data class MemoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val memoryType: String,
    val content: String,
    val projectId: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val usageCount: Int = 0
)

@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val status: String = "draft",  // draft, pending, approved, in_progress, completed, rejected
    val reason: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val approvedAt: Long? = null,
    val completedAt: Long? = null,
    val rejectionReason: String? = null
)

@Entity(tableName = "plan_steps")
data class PlanStepEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val description: String,
    val stepOrder: Int,
    val status: String = "pending",  // pending, in_progress, completed, skipped, failed
    val estimatedTurns: Int = 1,
    val actualTurns: Int = 0,
    val startedAt: Long? = null,
    val completedAt: Long? = null
)

@Entity(tableName = "tracker_tasks")
data class TrackerTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val taskType: String = "task",  // epic, task, subtask, bug
    val status: String = "open",    // open, in_progress, blocked, closed
    val priority: String = "medium", // low, medium, high, critical
    val parentId: String? = null,
    val dependencies: String = "[]", // JSON array
    val progressPercent: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

