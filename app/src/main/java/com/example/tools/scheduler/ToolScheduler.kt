package com.example.tools.scheduler

import com.example.runtime.config.RuntimeConfig
import com.example.tools.ToolRegistry
import com.example.tools.ToolResult
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates tool execution with deduplication, retry, and cooldowns.
 */
class ToolScheduler(
    private val config: RuntimeConfig = RuntimeConfig()
) {
    // Deduplication cache: signature -> (callId, result, timestamp)
    private val dedupCache = ConcurrentHashMap<String, Triple<String, String, Long>>()
    
    // Cooldowns: tool_name -> cooldown_until timestamp
    private val cooldowns = ConcurrentHashMap<String, Long>()
    
    // Active executions
    private val activeExecutions = ConcurrentHashMap<String, Job>()
    
    // Execution history
    private val history = java.util.concurrent.CopyOnWriteArrayList<ToolCallResult>()
    
    // Concurrency limit
    private val semaphore = kotlinx.coroutines.sync.Semaphore(10)
    
    private fun cleanupExpiredCache() {
        val now = System.currentTimeMillis()
        dedupCache.entries.removeIf { now - it.value.third > config.deduplicationTtlMs }
        cooldowns.entries.removeIf { now > it.value }
    }
    
    /**
     * Execute a single tool call.
     */
    suspend fun execute(request: ToolCallRequest): ToolCallResult {
        cleanupExpiredCache()
        // Check cooldown
        val cooldownUntil = cooldowns[request.name]
        if (cooldownUntil != null && System.currentTimeMillis() < cooldownUntil) {
            return ToolCallResult(
                callId = request.callId,
                name = request.name,
                status = ToolCallStatus.ERROR,
                error = "Tool on cooldown until ${Instant.ofEpochMilli(cooldownUntil)}"
            )
        }
        
        // Check deduplication
        val signature = request.getSignature()
        val cached = dedupCache[signature]
        if (cached != null && 
            System.currentTimeMillis() - cached.third < config.deduplicationTtlMs) {
            return ToolCallResult(
                callId = request.callId,
                name = request.name,
                status = ToolCallStatus.DEDUPLICATED,
                result = cached.second,
                reusedFrom = cached.first
            )
        }
        
        // Get tool
        val tool = ToolRegistry.get(request.name)
            ?: return ToolCallResult(
                callId = request.callId,
                name = request.name,
                status = ToolCallStatus.ERROR,
                error = "Tool '${request.name}' not found"
            )
        
        // Execute with timeout and retry
        var attempts = 0
        var lastError: String? = null
        var lastErrorType: String? = null
        val startedAt = Instant.now()
        
        return semaphore.withPermit {
            while (attempts < config.maxRetryAttempts) {
                attempts++
                
                try {
                    val toolResult = withTimeout(config.toolTimeoutMs) {
                        tool.execute(request.args)
                    }
                    
                    val endedAt = Instant.now()
                    
                    val result = if (toolResult.success) {
                        // Cache successful result
                        dedupCache[signature] = Triple(
                            request.callId,
                            toolResult.content ?: "",
                            System.currentTimeMillis()
                        )
                        
                        ToolCallResult(
                            callId = request.callId,
                            name = request.name,
                            status = ToolCallStatus.SUCCESS,
                            result = toolResult.content,
                            startedAt = startedAt,
                            endedAt = endedAt,
                            attempts = attempts
                        )
                    } else {
                        ToolCallResult(
                            callId = request.callId,
                            name = request.name,
                            status = ToolCallStatus.ERROR,
                            error = toolResult.error,
                            startedAt = startedAt,
                            endedAt = endedAt,
                            attempts = attempts
                        )
                    }
                    
                    history.add(result)
                    return@withPermit result
                    
                } catch (e: TimeoutCancellationException) {
                    lastError = "Timeout after ${config.toolTimeoutMs}ms"
                    lastErrorType = "Timeout"
                } catch (e: Exception) {
                    lastError = e.message
                    lastErrorType = e::class.simpleName
                    
                    if (!request.allowRetry) break
                    
                    // Exponential backoff
                    delay(1000L * attempts)
                }
            }
            
            // All attempts failed
            ToolCallResult(
                callId = request.callId,
                name = request.name,
                status = if (lastErrorType == "Timeout") 
                    ToolCallStatus.TIMEOUT else ToolCallStatus.ERROR,
                error = lastError,
                errorType = lastErrorType,
                startedAt = startedAt,
                endedAt = Instant.now(),
                attempts = attempts
            ).also { history.add(it) }
        }
    }
    
    /**
     * Execute multiple tool calls.
     */
    suspend fun executeAll(requests: List<ToolCallRequest>): List<ToolCallResult> {
        return coroutineScope {
            requests.map { request ->
                async { execute(request) }
            }.awaitAll()
        }
    }
    
    /**
     * Set cooldown for a tool.
     */
    fun setCooldown(toolName: String, durationMs: Long) {
        cooldowns[toolName] = System.currentTimeMillis() + durationMs
    }
    
    /**
     * Clear deduplication cache.
     */
    fun clearCache() {
        dedupCache.clear()
    }
    
    /**
     * Get execution history.
     */
    fun getHistory(): List<ToolCallResult> = history.toList()
}
