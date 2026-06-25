package com.example.memory

import com.example.data.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

enum class MemoryType(val value: String) {
    USER("user"),
    FEEDBACK("feedback"),
    PROJECT("project"),
    REFERENCE("reference")
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
    
    suspend fun getRelevantMemories(query: String, projectId: String? = null, limit: Int = 5): List<MemoryEntry> {
        val escapedQuery = escapeLikeQuery(query)
        return if (projectId != null) {
            memoryDao.searchMemoriesByProject(escapedQuery, projectId, limit)
        } else {
            memoryDao.searchMemories(escapedQuery, limit)
        }
    }
    
    fun formatForPrompt(memories: List<MemoryEntry>): String {
        if (memories.isEmpty()) return ""
        return buildString {
            appendLine("\n## Relevant Memories")
            memories.forEach { memory ->
                appendLine("- **${memory.title}** (${memory.memoryType}): ${memory.content}")
            }
        }
    }

    suspend fun getUserPreferences(): List<MemoryEntry> {
        return memoryDao.getMemoriesByType(MemoryType.USER.value)
    }

    /**
     * Save an extracted memory. If a memory with the same title and type exists,
     * update its content instead of creating a duplicate.
     */
    suspend fun saveExtractedMemory(
        type: MemoryType,
        title: String,
        content: String,
        confidence: Float
    ): Long {
        // Check for existing memory with same title and type
        val existing = memoryDao.findByTitleAndType(title, type.value)
        return if (existing != null) {
            // Update existing: keep higher relevance, update content
            val updated = existing.copy(
                content = content,
                relevanceScore = maxOf(existing.relevanceScore, confidence),
                usageCount = existing.usageCount + 1
            )
            memoryDao.updateMemory(updated)
            existing.id
        } else {
            // Insert new
            addMemory(type, title, content, listOf("auto_extracted"))
        }
    }
}
