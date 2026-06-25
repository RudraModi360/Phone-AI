package com.example.tools.builtin

import com.example.tracker.TrackerService
import com.example.tracker.TaskType
import com.example.tracker.TaskStatus
import com.example.tracker.TaskPriority
import com.example.service.Parameters
import com.example.service.PropertySchema
import com.example.tools.BaseTool
import com.example.tools.RiskLevel
import com.example.tools.ToolResult
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

/**
 * Tool for managing tasks/todos during agentic execution.
 * Supports hierarchical tasks (epics → tasks → subtasks).
 */
class TrackerTool(private val trackerService: TrackerService) : BaseTool {
    override val name = "tracker"
    override val description = "Create, update, and manage tasks/todos for tracking work progress"
    override val riskLevel = RiskLevel.SAFE
    
    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "action" to PropertySchema(
                type = "STRING",
                description = "Action: create, list, view, update_status, update_progress, close, delete"
            ),
            "title" to PropertySchema(
                type = "STRING",
                description = "Task title (for create action)"
            ),
            "description" to PropertySchema(
                type = "STRING",
                description = "Task description"
            ),
            "type" to PropertySchema(
                type = "STRING",
                description = "Task type: epic, task, subtask, bug (default: task)"
            ),
            "priority" to PropertySchema(
                type = "STRING",
                description = "Priority: low, medium, high, critical (default: medium)"
            ),
            "parent_id" to PropertySchema(
                type = "STRING",
                description = "Parent task ID for subtasks"
            ),
            "task_id" to PropertySchema(
                type = "STRING",
                description = "Task ID (for update/close/delete actions)"
            ),
            "status" to PropertySchema(
                type = "STRING",
                description = "New status: open, in_progress, blocked, closed"
            ),
            "progress" to PropertySchema(
                type = "INTEGER",
                description = "Progress percentage (0-100)"
            ),
            "filter" to PropertySchema(
                type = "STRING",
                description = "Filter for list: all, open (default: open)"
            )
        ),
        required = listOf("action")
    )
    
    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val action = args["action"] as? String
            ?: return ToolResult.error("Missing 'action' argument")
        
        return try {
            when (action.lowercase()) {
                "create" -> createTask(args)
                "list" -> listTasks(args)
                "view" -> viewTask(args)
                "update_status" -> updateStatus(args)
                "update_progress" -> updateProgress(args)
                "close" -> closeTask(args)
                "delete" -> deleteTask(args)
                else -> ToolResult.error("Unknown action: $action")
            }
        } catch (e: Exception) {
            ToolResult.error("Tracker operation failed: ${e.message}")
        }
    }
    
    private suspend fun createTask(args: Map<String, Any?>): ToolResult {
        val title = args["title"] as? String
            ?: return ToolResult.error("Missing 'title' for create action")
        
        val description = args["description"] as? String ?: ""
        val typeStr = args["type"] as? String ?: "task"
        val priorityStr = args["priority"] as? String ?: "medium"
        val parentId = args["parent_id"] as? String
        
        val type = TaskType.values().find { it.value == typeStr.lowercase() } ?: TaskType.TASK
        val priority = TaskPriority.values().find { it.value == priorityStr.lowercase() } ?: TaskPriority.MEDIUM
        
        val task = trackerService.createTask(title, description, type, priority, parentId)
        
        val output = buildString {
            appendLine("✅ Task created!")
            appendLine("ID: ${task.id}")
            appendLine("Title: ${task.title}")
            appendLine("Type: ${task.taskType} | Priority: ${task.priority}")
            if (parentId != null) {
                appendLine("Parent: $parentId")
            }
        }
        
        return ToolResult.success(output, mapOf("task_id" to task.id))
    }
    
    private suspend fun listTasks(args: Map<String, Any?>): ToolResult {
        val filter = args["filter"] as? String ?: "open"
        
        val tasks = when (filter.lowercase()) {
            "all" -> trackerService.getAllTasks().first()
            "open" -> trackerService.getOpenTasks().first()
            else -> trackerService.getOpenTasks().first()
        }
        
        if (tasks.isEmpty()) {
            return ToolResult.success("No tasks found.", mapOf("count" to 0))
        }
        
        val output = buildString {
            appendLine("📋 Tasks (${tasks.size}):")
            appendLine()
            
            // Group by type for better organization
            val grouped = tasks.groupBy { it.taskType }
            
            for ((type, typeTasks) in grouped) {
                val typeIcon = when (type) {
                    "epic" -> "🎯"
                    "task" -> "📌"
                    "subtask" -> "  └─"
                    "bug" -> "🐛"
                    else -> "•"
                }
                
                typeTasks.forEach { task ->
                    val statusIcon = when (task.status) {
                        "open" -> "○"
                        "in_progress" -> "◐"
                        "blocked" -> "⊘"
                        "closed" -> "●"
                        else -> "?"
                    }
                    val priorityIcon = when (task.priority) {
                        "critical" -> "🔴"
                        "high" -> "🟠"
                        "medium" -> "🟡"
                        "low" -> "🟢"
                        else -> ""
                    }
                    
                    appendLine("$typeIcon $statusIcon [${task.id}] ${task.title} $priorityIcon")
                    if (task.progressPercent > 0 && task.progressPercent < 100) {
                        appendLine("   Progress: ${task.progressPercent}%")
                    }
                }
            }
        }
        
        return ToolResult.success(output, mapOf("count" to tasks.size))
    }
    
    private suspend fun viewTask(args: Map<String, Any?>): ToolResult {
        val taskId = args["task_id"] as? String
            ?: return ToolResult.error("Missing 'task_id' for view action")
        
        val allTasks = trackerService.getAllTasks().first()
        val task = allTasks.find { it.id == taskId }
            ?: return ToolResult.error("Task not found: $taskId")
        
        // Get child tasks
        val children = trackerService.getChildTasks(taskId).first()
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val output = buildString {
            appendLine("📌 Task: ${task.title}")
            appendLine("─".repeat(40))
            appendLine("ID: ${task.id}")
            appendLine("Type: ${task.taskType}")
            appendLine("Status: ${task.status}")
            appendLine("Priority: ${task.priority}")
            appendLine("Progress: ${task.progressPercent}%")
            appendLine("Created: ${dateFormat.format(Date(task.createdAt))}")
            
            if (task.description.isNotEmpty()) {
                appendLine()
                appendLine("Description:")
                appendLine(task.description)
            }
            
            if (task.parentId != null) {
                appendLine()
                appendLine("Parent Task: ${task.parentId}")
            }
            
            if (children.isNotEmpty()) {
                appendLine()
                appendLine("Subtasks (${children.size}):")
                children.forEach { child ->
                    val statusIcon = when (child.status) {
                        "closed" -> "●"
                        "in_progress" -> "◐"
                        else -> "○"
                    }
                    appendLine("  $statusIcon [${child.id}] ${child.title}")
                }
            }
        }
        
        return ToolResult.success(output, mapOf("task_id" to taskId))
    }
    
    private suspend fun updateStatus(args: Map<String, Any?>): ToolResult {
        val taskId = args["task_id"] as? String
            ?: return ToolResult.error("Missing 'task_id' for update_status action")
        
        val statusStr = args["status"] as? String
            ?: return ToolResult.error("Missing 'status' for update_status action")
        
        val status = TaskStatus.values().find { it.value == statusStr.lowercase() }
            ?: return ToolResult.error("Invalid status: $statusStr. Valid: open, in_progress, blocked, closed")
        
        trackerService.updateStatus(taskId, status)
        return ToolResult.success("✅ Task $taskId status updated to: ${status.value}", mapOf("task_id" to taskId))
    }
    
    private suspend fun updateProgress(args: Map<String, Any?>): ToolResult {
        val taskId = args["task_id"] as? String
            ?: return ToolResult.error("Missing 'task_id' for update_progress action")
        
        val progress = (args["progress"] as? Number)?.toInt()
            ?: return ToolResult.error("Missing 'progress' for update_progress action")
        
        trackerService.updateProgress(taskId, progress)
        return ToolResult.success("✅ Task $taskId progress updated to: $progress%", mapOf("task_id" to taskId))
    }
    
    private suspend fun closeTask(args: Map<String, Any?>): ToolResult {
        val taskId = args["task_id"] as? String
            ?: return ToolResult.error("Missing 'task_id' for close action")
        
        trackerService.closeTask(taskId)
        return ToolResult.success("✅ Task $taskId closed.", mapOf("task_id" to taskId))
    }
    
    private suspend fun deleteTask(args: Map<String, Any?>): ToolResult {
        val taskId = args["task_id"] as? String
            ?: return ToolResult.error("Missing 'task_id' for delete action")
        
        trackerService.deleteTask(taskId)
        return ToolResult.success("🗑️ Task $taskId deleted.", mapOf("task_id" to taskId))
    }
}
