package com.example.runtime.turn

import com.example.runtime.config.RuntimeConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.time.Instant

class TurnManager(private val config: RuntimeConfig) {
    
    private val sessions = java.util.concurrent.ConcurrentHashMap<String, SessionState>()
    private val mutex = ReentrantMutex()
    
    // Lifecycle hooks
    private val onTurnStartCallbacks = mutableListOf<suspend (TurnContext) -> Unit>()
    private val onTurnEndCallbacks = mutableListOf<suspend (TurnContext) -> Unit>()
    private val onBudgetExceededCallbacks = mutableListOf<suspend (TurnContext) -> Unit>()
    
    data class SessionState(
        val sessionId: String,
        var turnsUsed: Int = 0,
        var budgetAdjustments: Int = 0,
        val createdAt: Instant = Instant.now(),
        var lastActivity: Instant = Instant.now(),
        var activeTurn: TurnContext? = null,
        val turnHistory: MutableList<TurnContext> = mutableListOf()
    )
    
    // --- Public API ---
    
    fun registerOnTurnStart(callback: suspend (TurnContext) -> Unit) {
        onTurnStartCallbacks.add(callback)
    }
    
    fun registerOnTurnEnd(callback: suspend (TurnContext) -> Unit) {
        onTurnEndCallbacks.add(callback)
    }
    
    fun registerOnBudgetExceeded(callback: suspend (TurnContext) -> Unit) {
        onBudgetExceededCallbacks.add(callback)
    }
    
    suspend fun getRemainingTurns(sessionId: String): Int {
        return mutex.withLock {
            val session = getOrCreateSession(sessionId)
            val effectiveBudget = config.maxTurns + session.budgetAdjustments
            maxOf(0, effectiveBudget - session.turnsUsed)
        }
    }
    
    private suspend fun isBudgetExceeded(sessionId: String): Boolean {
        return getRemainingTurns(sessionId) <= 0
    }
    
    suspend fun adjustBudget(sessionId: String, delta: Int): Int {
        return mutex.withLock {
            val session = getOrCreateSession(sessionId)
            session.budgetAdjustments += delta
            getRemainingTurns(sessionId)
        }
    }
    
    /**
     * Execute a block within a managed turn context.
     */
    suspend fun <T> withTurn(
        sessionId: String,
        parentTurnId: String? = null,
        block: suspend (TurnContext) -> T
    ): T {
        val turn = mutex.withLock {
            // Check budget INSIDE the lock
            if (isBudgetExceeded(sessionId)) {
                val errTurn = createTurn(sessionId, parentTurnId)
                errTurn.fail("Turn budget exceeded", "BudgetExceeded")
                onBudgetExceededCallbacks.forEach { it(errTurn) }
                throw TurnBudgetExceededException(
                    sessionId, config.maxTurns, getOrCreateSession(sessionId).turnsUsed
                )
            }
            
            val session = getOrCreateSession(sessionId)
            val turnNumber = session.turnsUsed + 1
            TurnContext(
                sessionId = sessionId,
                turnNumber = turnNumber,
                parentTurnId = parentTurnId
            ).also {
                session.activeTurn = it
            }
        }
        
        // Notify start
        turn.start()
        onTurnStartCallbacks.forEach { it(turn) }
        
        return try {
            // Execute with timeout
            withTimeout(config.turnTimeoutMs) {
                block(turn)
            }.also {
                turn.complete()
            }
        } catch (e: TimeoutCancellationException) {
            turn.timeout()
            throw TurnTimeoutException(turn.turnId, config.turnTimeoutMs)
        } catch (e: Exception) {
            turn.fail(e.message ?: "Unknown error", e::class.simpleName)
            throw e
        } finally {
            // Record turn completion
            mutex.withLock {
                val session = getOrCreateSession(sessionId)
                session.turnsUsed++
                session.lastActivity = Instant.now()
                session.activeTurn = null
                session.turnHistory.add(turn)
            }
            
            // Notify end
            onTurnEndCallbacks.forEach { it(turn) }
        }
    }
    
    // --- Private Helpers ---
    
    private fun getOrCreateSession(sessionId: String): SessionState {
        return sessions.getOrPut(sessionId) { SessionState(sessionId) }
    }
    
    private fun createTurn(sessionId: String, parentTurnId: String?): TurnContext {
        val session = getOrCreateSession(sessionId)
        return TurnContext(
            sessionId = sessionId,
            turnNumber = session.turnsUsed + 1,
            parentTurnId = parentTurnId
        )
    }
}

// Custom exceptions
class TurnBudgetExceededException(
    val sessionId: String, 
    val maxTurns: Int,
    val turnsUsed: Int? = null
) : Exception("Session $sessionId exceeded turn budget of $maxTurns")

class TurnTimeoutException(
    val turnId: String,
    val timeoutMs: Long
) : Exception("Turn $turnId timed out after ${timeoutMs}ms")

class ReentrantMutex {
    private val delegate = Mutex()
    private val localOwner = java.util.concurrent.atomic.AtomicReference<Any?>(null)
    
    suspend fun <T> withLock(action: suspend () -> T): T {
        val currentOwner = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job] ?: Any()
        if (localOwner.get() === currentOwner) {
            return action()
        }
        return delegate.withLock {
            val previous = localOwner.getAndSet(currentOwner)
            try {
                action()
            } finally {
                localOwner.set(previous)
            }
        }
    }
}
