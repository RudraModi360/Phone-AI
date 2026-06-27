package com.example.tools.builtin

import com.example.memory.MemoryService
import com.example.memory.MemoryType
import com.example.service.Parameters
import com.example.service.PropertySchema
import com.example.tools.BaseTool
import com.example.tools.RiskLevel
import com.example.tools.ToolResult
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

/**
 * Tool for managing persistent memories.
 * Allows the agent to remember learnings, patterns, and decisions.
 */
class MemoryTool(private val memoryService: MemoryService) : BaseTool {
    override val name = "memory"
    override val description = "Store, search, and retrieve persistent memories (user info, feedback, project context, references)"
    override val riskLevel = RiskLevel.SAFE
    
    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "action" to PropertySchema(
                type = "STRING",
                description = "Action: save, search, list, delete"
            ),
            "type" to PropertySchema(
                type = "STRING",
                description = "Memory type: user, feedback, project, reference"
            ),
            "title" to PropertySchema(
                type = "STRING",
                description = "Memory title (for save action)"
            ),
            "content" to PropertySchema(
                type = "STRING",
                description = "Memory content (for save action)"
            ),
            "tags" to PropertySchema(
                type = "STRING",
                description = "Comma-separated tags"
            ),
            "query" to PropertySchema(
                type = "STRING",
                description = "Search query (for search action)"
            ),
            "memory_id" to PropertySchema(
                type = "INTEGER",
                description = "Memory ID (for delete action)"
            ),
            "project_id" to PropertySchema(
                type = "STRING",
                description = "Project/session ID to scope memories"
            ),
            "limit" to PropertySchema(
                type = "INTEGER",
                description = "Max results for search/list (default: 10)"
            )
        ),
        required = listOf("action")
    )
    
    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val action = args["action"] as? String
            ?: return ToolResult.error("Missing 'action' argument")
        
        return try {
            when (action.lowercase()) {
                "save" -> saveMemory(args)
                "search" -> searchMemories(args)
                "list" -> listMemories(args)
                "delete" -> deleteMemory(args)
                else -> ToolResult.error("Unknown action: $action. Valid: save, search, list, delete")
            }
        } catch (e: Exception) {
            ToolResult.error("Memory operation failed: ${e.message}")
        }
    }
    
    private suspend fun saveMemory(args: Map<String, Any?>): ToolResult {
        val title = args["title"] as? String
            ?: return ToolResult.error("Missing 'title' for save action")
        
        val content = args["content"] as? String
            ?: return ToolResult.error("Missing 'content' for save action")
        
        val typeStr = args["type"] as? String ?: "project"
        val tagsStr = args["tags"] as? String ?: ""
        val projectId = args["project_id"] as? String
        
        val type = when (typeStr.lowercase()) {
            "user", "preference" -> MemoryType.USER
            "feedback" -> MemoryType.FEEDBACK
            "project", "learning", "approach", "key_step", "pattern", "decision", "context" -> MemoryType.PROJECT
            "reference" -> MemoryType.REFERENCE
            else -> MemoryType.entries.find { it.value == typeStr.lowercase() } ?: MemoryType.PROJECT
        }
        
        val memoryId = memoryService.saveMemory(
            name = title,
            description = tagsStr.ifBlank { "Memory entry" },
            type = type,
            content = content,
            projectId = projectId
        )
        
        return ToolResult.success(
            "✅ Memory saved!\nID: $memoryId\nTitle: $title\nType: ${type.value}",
            mapOf("memory_id" to memoryId)
        )
    }
    
    private suspend fun searchMemories(args: Map<String, Any?>): ToolResult {
        val query = args["query"] as? String
            ?: return ToolResult.error("Missing 'query' for search action")
        
        val limit = (args["limit"] as? Number)?.toInt() ?: 10
        
        val memories = memoryService.search(query, limit)
        
        if (memories.isEmpty()) {
            return ToolResult.success("No memories found for: $query", mapOf("count" to 0))
        }
        
        val output = buildString {
            appendLine("🔍 Search results for: \"$query\" (${memories.size} found)")
            appendLine()
            memories.forEach { memory ->
                appendLine("📌 [${memory.id}] ${memory.name}")
                appendLine("   Type: ${memory.memoryType} | Uses: ${memory.usageCount}")
                appendLine("   ${memory.content.take(100)}${if (memory.content.length > 100) "..." else ""}")
                appendLine()
            }
        }
        
        return ToolResult.success(output, mapOf("count" to memories.size))
    }
    
    private suspend fun listMemories(args: Map<String, Any?>): ToolResult {
        val projectId = args["project_id"] as? String
        
        val memories = if (projectId != null) {
            memoryService.getMemoriesForProject(projectId).first()
        } else {
            memoryService.getAllMemories().first()
        }
        
        if (memories.isEmpty()) {
            return ToolResult.success("No memories stored.", mapOf("count" to 0))
        }
        
        val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.US)
        val output = buildString {
            appendLine("📚 Memories (${memories.size}):")
            appendLine()
            
            // Group by type
            val grouped = memories.groupBy { it.memoryType }
            
            for ((type, typeMemories) in grouped) {
                val icon = when (type) {
                    "user" -> "👤"
                    "feedback" -> "💬"
                    "project" -> "📁"
                    "reference" -> "📚"
                    else -> "📌"
                }
                
                appendLine("$icon ${type.uppercase()} (${typeMemories.size})")
                typeMemories.take(5).forEach { memory ->
                    appendLine("  [${memory.id}] ${memory.name}")
                }
                if (typeMemories.size > 5) {
                    appendLine("  ... and ${typeMemories.size - 5} more")
                }
                appendLine()
            }
        }
        
        return ToolResult.success(output, mapOf("count" to memories.size))
    }
    
    private suspend fun deleteMemory(args: Map<String, Any?>): ToolResult {
        val memoryId = (args["memory_id"] as? Number)?.toLong()
            ?: return ToolResult.error("Missing 'memory_id' for delete action")
        
        memoryService.deleteMemory(memoryId)
        return ToolResult.success("🗑️ Memory $memoryId deleted.", mapOf("memory_id" to memoryId))
    }
}
