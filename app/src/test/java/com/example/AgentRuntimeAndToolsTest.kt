package com.example

import com.example.runtime.AgentRuntime
import com.example.runtime.config.RuntimeConfig
import com.example.runtime.loop.AgentEvent
import com.example.runtime.loop.AgentEventType
import com.example.runtime.loop.LoopType
import com.example.runtime.loop.RecoveryAction
import com.example.runtime.reasoning.ReasoningLevel
import com.example.runtime.turn.TurnBudgetExceededException
import com.example.runtime.turn.TurnTimeoutException
import com.example.tools.RiskLevel
import com.example.tools.ToolRegistry
import com.example.tools.builtin.DateTimeTool
import com.example.tools.scheduler.ToolCallRequest
import com.example.tools.scheduler.ToolCallStatus
import com.example.tools.scheduler.ToolScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentRuntimeAndToolsTest {

    // --- Phase 1: TurnManager & Runtime Tests ---

    @Test
    fun testDefaultConfigHasSensibleValues() {
        val config = RuntimeConfig.default()
        assertEquals(40, config.maxTurns)
        assertEquals(ReasoningLevel.MEDIUM, config.defaultReasoningLevel)
        assertTrue(config.loopDetectionEnabled)
    }

    @Test
    fun testMinimalConfigDisablesAdvancedFeatures() {
        val config = RuntimeConfig.minimal()
        assertEquals(10, config.maxTurns)
        assertFalse(config.loopDetectionEnabled)
        assertFalse(config.autoEscalate)
    }

    @Test
    fun testTurnEnforcementAndBudget() = runTest {
        val runtime = AgentRuntime.create(RuntimeConfig(maxTurns = 2))
        
        // Turn 1
        runtime.executeTurn("session_1") {
            assertEquals(1, turn.turnNumber)
        }
        
        // Turn 2
        runtime.executeTurn("session_1") {
            assertEquals(2, turn.turnNumber)
        }

        // Turn 3 should fail
        var budgetExceeded = false
        try {
            runtime.executeTurn("session_1") {
                fail("Should not execute")
            }
        } catch (e: TurnBudgetExceededException) {
            budgetExceeded = true
        }
        assertTrue("Budget should have been exceeded", budgetExceeded)
    }

    // --- Phase 1: Loop Detection Tests ---

    @Test
    fun testLoopDetectionOnConsecutiveToolCalls() = runTest {
        val runtime = AgentRuntime.create(RuntimeConfig(toolCallThreshold = 3))
        val event = AgentEvent(
            type = AgentEventType.TOOL_CALL,
            toolName = "shell_exec",
            toolArgs = mapOf("command" to "ls")
        )

        runtime.executeTurn("session_2") {
            // First check
            var result = checkLoop(event)
            assertFalse(result.detected)

            // Second check
            result = checkLoop(event)
            assertFalse(result.detected)

            // Third check -> should trigger CONSECUTIVE_TOOL_CALLS
            result = checkLoop(event)
            assertTrue(result.detected)
            assertEquals(LoopType.CONSECUTIVE_TOOL_CALLS, result.loopType)
            assertEquals(3, result.repetitionCount)
        }
    }

    @Test
    fun testContentRepetitionLoopDetection() = runTest {
        val runtime = AgentRuntime.create(RuntimeConfig(contentRepetitionThreshold = 3))
        val event = AgentEvent(
            type = AgentEventType.CONTENT,
            content = "This is a duplicated segment. "
        )

        runtime.executeTurn("session_3") {
            // Check 1
            assertFalse(checkLoop(event).detected)
            // Check 2
            assertFalse(checkLoop(event).detected)
            // Check 3 -> loop
            val result = checkLoop(event)
            assertTrue(result.detected)
            assertEquals(LoopType.CONTENT_REPETITION, result.loopType)
        }
    }

    // --- Phase 1: Reasoning Tests ---

    @Test
    fun testReasoningLevelEscalation() {
        val runtime = AgentRuntime.create()
        val controller = runtime.reasoningController

        assertEquals(ReasoningLevel.MEDIUM, controller.currentLevel)

        // Escalate
        controller.escalate()
        assertEquals(ReasoningLevel.HIGH, controller.currentLevel)

        // Escalate again
        controller.escalate()
        assertEquals(ReasoningLevel.DEEP, controller.currentLevel)

        // Cannot escalate further
        controller.escalate()
        assertEquals(ReasoningLevel.DEEP, controller.currentLevel)
    }

    @Test
    fun testReasoningAutoAdjustment() {
        val runtime = AgentRuntime.create()
        val controller = runtime.reasoningController

        // Complex debug query
        controller.adjustForQuery("Can you help me debug a NullPointerException in my MainActivity?")
        assertEquals(ReasoningLevel.HIGH, controller.currentLevel)

        // Simple prompt de-escalates or stays minimal
        controller.setLevel(ReasoningLevel.MEDIUM)
        controller.adjustForQuery("hello")
        assertEquals(ReasoningLevel.LOW, controller.currentLevel)
    }

    // --- Phase 2: Tool Registry & Tool Scheduler Tests ---

    @Before
    fun setupTools() {
        ToolRegistry.clear()
        ToolRegistry.initializeDefaults()
    }

    @Test
    fun testToolRegistry() {
        val dtTool = ToolRegistry.get("datetime")
        assertNotNull(dtTool)
        assertEquals("datetime", dtTool?.name)
        assertEquals(RiskLevel.SAFE, dtTool?.riskLevel)

        val shellTool = ToolRegistry.get("shell_exec")
        assertNotNull(shellTool)
        assertEquals(RiskLevel.DANGEROUS, shellTool?.riskLevel)
    }

    @Test
    fun testSchedulerExecutionAndDeduplication() = runTest {
        val scheduler = ToolScheduler(RuntimeConfig(deduplicationTtlMs = 10000))
        val request = ToolCallRequest(
            name = "datetime",
            args = mapOf("operation" to "now")
        )

        // Execution 1
        val result1 = scheduler.execute(request)
        assertTrue(result1.success)
        assertEquals(ToolCallStatus.SUCCESS, result1.status)

        // Execution 2 -> should be deduplicated
        val result2 = scheduler.execute(request)
        assertEquals(ToolCallStatus.DEDUPLICATED, result2.status)
        assertEquals(result1.callId, result2.reusedFrom)
    }

    @Test
    fun testSchedulerCooldown() = runTest {
        val scheduler = ToolScheduler()
        scheduler.setCooldown("datetime", 5000)

        val request = ToolCallRequest(
            name = "datetime",
            args = mapOf("operation" to "now")
        )

        val result = scheduler.execute(request)
        assertFalse(result.success)
        assertEquals(ToolCallStatus.ERROR, result.status)
        assertTrue(result.error!!.contains("cooldown"))
    }
}
