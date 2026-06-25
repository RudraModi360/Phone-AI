package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    suspend fun getAllSessionsList(): List<ChatSession>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): ChatSession?

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSessionList(sessionId: String): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Update
    suspend fun updateMessage(message: ChatMessage)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Transaction
    suspend fun deleteSessionWithMessages(sessionId: String) {
        deleteMessagesForSession(sessionId)
        deleteSession(sessionId)
    }

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM app_settings")
    fun getAllSettings(): Flow<List<AppSetting>>

    @Query("SELECT * FROM app_settings")
    suspend fun getAllSettingsList(): List<AppSetting>

    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSettingValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: AppSetting)

    @Query("DELETE FROM app_settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_entries ORDER BY relevanceScore DESC")
    fun getAllMemories(): Flow<List<MemoryEntry>>
    
    @Query("SELECT * FROM memory_entries WHERE projectId = :projectId OR projectId IS NULL ORDER BY relevanceScore DESC")
    fun getMemoriesForProject(projectId: String?): Flow<List<MemoryEntry>>
    
    @Query("SELECT * FROM memory_entries WHERE content LIKE '%' || :query || '%' ESCAPE '\\' OR title LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY relevanceScore DESC LIMIT :limit")
    suspend fun searchMemories(query: String, limit: Int = 10): List<MemoryEntry>
    
    @Query("SELECT * FROM memory_entries WHERE (content LIKE '%' || :query || '%' ESCAPE '\\' OR title LIKE '%' || :query || '%' ESCAPE '\\') AND projectId = :projectId ORDER BY relevanceScore DESC LIMIT :limit")
    suspend fun searchMemoriesByProject(query: String, projectId: String, limit: Int = 10): List<MemoryEntry>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntry): Long
    
    @Update
    suspend fun updateMemory(memory: MemoryEntry)
    
    @Query("UPDATE memory_entries SET usageCount = usageCount + 1 WHERE id = :id")
    suspend fun incrementUsageCount(id: Long)
    
    @Query("DELETE FROM memory_entries WHERE id = :id")
    suspend fun deleteMemory(id: Long)

    @Query("SELECT * FROM memory_entries WHERE memoryType = :type ORDER BY relevanceScore DESC")
    suspend fun getMemoriesByType(type: String): List<MemoryEntry>

    @Query("DELETE FROM memory_entries WHERE memoryType = :type AND title = :title")
    suspend fun deleteByTypeAndTitle(type: String, title: String)

    @Query("SELECT * FROM memory_entries WHERE title = :title AND memoryType = :type LIMIT 1")
    suspend fun findByTitleAndType(title: String, type: String): MemoryEntry?
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plans ORDER BY createdAt DESC")
    fun getAllPlans(): Flow<List<PlanEntity>>
    
    @Query("SELECT * FROM plans WHERE id = :planId")
    suspend fun getPlanById(planId: String): PlanEntity?
    
    @Query("SELECT * FROM plans WHERE status = :status")
    fun getPlansByStatus(status: String): Flow<List<PlanEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: PlanEntity)
    
    @Update
    suspend fun updatePlan(plan: PlanEntity)
    
    @Query("DELETE FROM plans WHERE id = :planId")
    suspend fun deletePlan(planId: String)
    
    // Steps
    @Query("SELECT * FROM plan_steps WHERE planId = :planId ORDER BY stepOrder")
    fun getStepsForPlan(planId: String): Flow<List<PlanStepEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: PlanStepEntity)
    
    @Update
    suspend fun updateStep(step: PlanStepEntity)
}

@Dao
interface TrackerDao {
    @Query("SELECT * FROM tracker_tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<TrackerTaskEntity>>
    
    @Query("SELECT * FROM tracker_tasks WHERE status != 'closed' ORDER BY priority DESC")
    fun getOpenTasks(): Flow<List<TrackerTaskEntity>>
    
    @Query("SELECT * FROM tracker_tasks WHERE parentId = :parentId")
    fun getChildTasks(parentId: String): Flow<List<TrackerTaskEntity>>
    
    @Query("SELECT * FROM tracker_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): TrackerTaskEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TrackerTaskEntity)
    
    @Update
    suspend fun updateTask(task: TrackerTaskEntity)
    
    @Query("DELETE FROM tracker_tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: String)
}

