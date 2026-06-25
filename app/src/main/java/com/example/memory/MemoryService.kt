package com.example.memory

import com.example.data.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

enum class MemoryType(val value: String) {
    USER("user"),           // Who the user is (name, role, preferences, habits)
    FEEDBACK("feedback"),   // What the user likes/dislikes (corrections, preferences)
    PROJECT("project"),     // Project-specific context (architecture, conventions)
    REFERENCE("reference")  // Reference material (API docs, patterns, examples)
}

class MemoryService(private val memoryDao: MemoryDao) {
    
    fun getAllMemories(): Flow<List<MemoryEntry>> = memoryDao.getAllMemories()
    
    fun getMemoriesForProject(projectId: String?): Flow<List<MemoryEntry>> = 
        memoryDao.getMemoriesForProject(projectId)
    
    private fun escapeLikeQuery(query: String): String {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }

    suspend fun search(query: String, limit: Int = 10): List<MemoryEntry> {
        val escapedQuery = escapeLikeQuery(query)
        return memoryDao.searchMemories(escapedQuery, limit)
    }
    
    suspend fun addMemory(
        type: MemoryType,
        title: String,
        content: String,
        tags: List<String> = emptyList(),
        projectId: String? = null
    ): Long {
        val memory = MemoryEntry(
            memoryType = type.value,
            title = title,
            content = content,
            tags = JSONArray(tags).toString(),
            projectId = projectId
        )
        return memoryDao.insertMemory(memory)
    }
    
    suspend fun recordUsage(id: Long) {
        memoryDao.incrementUsageCount(id)
    }
    
    suspend fun deleteMemory(id: Long) {
        memoryDao.deleteMemory(id)
    }
    
    /**
     * Get relevant memories for a query (for RAG).
     */
    suspend fun getRelevantMemories(query: String, projectId: String? = null, limit: Int = 5): List<MemoryEntry> {
        val escapedQuery = escapeLikeQuery(query)
        return if (projectId != null) {
            memoryDao.searchMemoriesByProject(escapedQuery, projectId, limit)
        } else {
            memoryDao.searchMemories(escapedQuery, limit)
        }
    }
    
    /**
     * Format memories for injection into system prompt.
     */
    fun formatForPrompt(memories: List<MemoryEntry>): String {
        if (memories.isEmpty()) return ""
        
        return buildString {
            appendLine("\n## Relevant Memories")
            memories.forEach { memory ->
                appendLine("- **${memory.title}** (${memory.memoryType}): ${memory.content}")
            }
        }
    }

    /**
     * Get user preferences from memory.
     */
    suspend fun getUserPreferences(): List<MemoryEntry> {
        return memoryDao.getMemoriesByType(MemoryType.USER.value)
    }

    /**
     * Replace a preference: delete old version, insert new.
     * Used by processing pipeline to update deduplicated entries.
     */
    suspend fun replacePreference(title: String, newContent: String, tags: List<String> = emptyList()) {
        memoryDao.deleteByTypeAndTitle(MemoryType.USER.value, title)
        addMemory(MemoryType.USER, title, newContent, tags)
    }
}
