package com.example.memory

import com.example.data.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

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

    suspend fun getMemoryManifest(limit: Int = 200): List<MemoryEntry> {
        return memoryDao.getRecentMemories(limit)
    }

    suspend fun saveMemory(
        name: String,
        description: String,
        type: MemoryType,
        content: String,
        projectId: String? = null
    ): Long {
        val existing = memoryDao.findByNameAndType(name, type.value)
        return if (existing != null) {
            val updated = existing.copy(
                description = description,
                content = content,
                updatedAt = System.currentTimeMillis(),
                usageCount = existing.usageCount + 1
            )
            memoryDao.updateMemory(updated)
            existing.id
        } else {
            memoryDao.insertMemory(
                MemoryEntry(
                    name = name,
                    description = description,
                    memoryType = type.value,
                    content = content,
                    projectId = projectId
                )
            )
        }
    }

    suspend fun deleteMemory(id: Long) {
        memoryDao.deleteMemory(id)
    }

    suspend fun updateMemory(memory: MemoryEntry) {
        memoryDao.updateMemory(memory)
    }

    suspend fun recordUsage(id: Long) {
        memoryDao.incrementUsageCount(id)
    }

    suspend fun getRelevantMemories(query: String, projectId: String? = null, limit: Int = 5): List<MemoryEntry> {
        val escapedQuery = escapeLikeQuery(query)
        return memoryDao.searchMemories(escapedQuery, limit)
    }

    fun formatForPrompt(memories: List<MemoryEntry>): String {
        if (memories.isEmpty()) return ""
        return buildString {
            appendLine("\n## Relevant Memories")
            memories.forEach { memory ->
                appendLine("- **${memory.name}** (${memory.memoryType}): ${memory.content}")
            }
        }
    }
}
