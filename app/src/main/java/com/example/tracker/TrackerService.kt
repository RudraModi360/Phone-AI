package com.example.tracker

import com.example.data.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

enum class TaskType(val value: String) {
    EPIC("epic"),
    TASK("task"),
    SUBTASK("subtask"),
    BUG("bug")
}

enum class TaskStatus(val value: String) {
    OPEN("open"),
    IN_PROGRESS("in_progress"),
    BLOCKED("blocked"),
    CLOSED("closed")
}

enum class TaskPriority(val value: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical")
}

class TrackerService(private val trackerDao: TrackerDao) {
    
    fun getAllTasks(): Flow<List<TrackerTaskEntity>> = trackerDao.getAllTasks()
    
    fun getOpenTasks(): Flow<List<TrackerTaskEntity>> = trackerDao.getOpenTasks()
    
    fun getChildTasks(parentId: String): Flow<List<TrackerTaskEntity>> = 
        trackerDao.getChildTasks(parentId)
    
    suspend fun createTask(
        title: String,
        description: String = "",
        type: TaskType = TaskType.TASK,
        priority: TaskPriority = TaskPriority.MEDIUM,
        parentId: String? = null
    ): TrackerTaskEntity {
        // Validate parent exists if specified
        if (parentId != null) {
            val parent = trackerDao.getTaskById(parentId)
                ?: throw IllegalArgumentException("Parent task not found: $parentId")
        }
        
        val task = TrackerTaskEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            taskType = type.value,
            priority = priority.value,
            parentId = parentId
        )
        
        trackerDao.insertTask(task)
        return task
    }
    
    suspend fun updateStatus(taskId: String, status: TaskStatus) {
        val task = trackerDao.getTaskById(taskId) ?: return
        trackerDao.updateTask(task.copy(status = status.value))
    }
    
    suspend fun updateProgress(taskId: String, percent: Int) {
        val task = trackerDao.getTaskById(taskId) ?: return
        trackerDao.updateTask(task.copy(progressPercent = percent.coerceIn(0, 100)))
    }
    
    suspend fun closeTask(taskId: String) {
        val task = trackerDao.getTaskById(taskId) ?: return
        
        trackerDao.updateTask(task.copy(
            status = TaskStatus.CLOSED.value,
            progressPercent = 100
        ))
    }
    
    suspend fun deleteTask(taskId: String) {
        trackerDao.deleteTask(taskId)
    }
}
