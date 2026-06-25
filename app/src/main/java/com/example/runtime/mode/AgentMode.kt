package com.example.runtime.mode

import com.example.runtime.reasoning.ReasoningLevel

/**
 * Agent execution modes that control how the AI processes requests.
 * 
 * Each mode has distinct behavior, system prompts, and tool access patterns.
 */
enum class AgentMode(
    val displayName: String,
    val icon: String,
    val description: String,
    val defaultReasoningLevel: ReasoningLevel,
    val color: Long // ARGB hex
) {
    /**
     * PLANNING Mode - Creates structured plans before execution.
     * - Analyzes the task requirements
     * - Breaks down complex tasks into steps
     * - Identifies dependencies and risks
     * - Outputs an approval-gated plan
     */
    PLANNING(
        displayName = "Plan",
        icon = "📋",
        description = "Analyze and create step-by-step plans",
        defaultReasoningLevel = ReasoningLevel.HIGH,
        color = 0xFF3B82F6 // Blue
    ),
    
    /**
     * EXECUTION Mode - Directly executes tasks and uses tools.
     * - Immediate action-oriented
     * - Uses tools to accomplish tasks
     * - Minimal planning overhead
     * - Best for well-defined tasks
     */
    EXECUTION(
        displayName = "Execute",
        icon = "⚡",
        description = "Take action and execute tasks directly",
        defaultReasoningLevel = ReasoningLevel.MEDIUM,
        color = 0xFF22C55E // Green
    ),
    
    /**
     * DEEP_THINKING Mode - Extended reasoning and analysis.
     * - Deep analysis of problems
     * - Multi-perspective evaluation
     * - Shows thinking process (<think> blocks)
     * - Best for complex decisions
     */
    DEEP_THINKING(
        displayName = "Think",
        icon = "🧠",
        description = "Deep reasoning and thorough analysis",
        defaultReasoningLevel = ReasoningLevel.DEEP,
        color = 0xFF8B5CF6 // Purple
    );
    
    companion object {
        fun fromDisplayName(name: String): AgentMode {
            return values().find { it.displayName.equals(name, ignoreCase = true) } ?: EXECUTION
        }
    }
}

/**
 * Configuration for each agent mode with extended behavior settings.
 */
data class AgentModeConfig(
    val mode: AgentMode,
    val reasoningLevel: ReasoningLevel = mode.defaultReasoningLevel,
    val maxIterations: Int = when(mode) {
        AgentMode.PLANNING -> 15
        AgentMode.EXECUTION -> 25
        AgentMode.DEEP_THINKING -> 10
    },
    val showThinkingProcess: Boolean = mode == AgentMode.DEEP_THINKING,
    val requirePlanApproval: Boolean = mode == AgentMode.PLANNING,
    val autoExecuteTools: Boolean = mode == AgentMode.EXECUTION
) {
    /**
     * Get the system prompt addon specific to this mode.
     */
    fun getSystemPromptAddon(): String = when (mode) {
        AgentMode.PLANNING -> PLANNING_SYSTEM_PROMPT
        AgentMode.EXECUTION -> EXECUTION_SYSTEM_PROMPT
        AgentMode.DEEP_THINKING -> DEEP_THINKING_SYSTEM_PROMPT
    }
    
    companion object {
        private const val PLANNING_SYSTEM_PROMPT = """
## PLANNING MODE ACTIVE

You are in PLANNING mode. Your role is to analyze tasks and create structured plans.

### Instructions:
1. **Analyze** the user's request thoroughly
2. **Identify** all sub-tasks and dependencies
3. **Create** a clear step-by-step plan
4. **Estimate** effort for each step
5. **Highlight** risks and considerations

### Output Format:
For any non-trivial task, output a plan using this structure:

```plan
PLAN: [Brief title]
GOAL: [What we're trying to achieve]

STEPS:
1. [Step description] (Est: X turns)
   - Dependencies: [None or step numbers]
   - Tools needed: [list tools]

2. [Step description] (Est: X turns)
   ...

RISKS:
- [Potential issue and mitigation]

TOTAL ESTIMATED TURNS: X
```

Wait for user approval before suggesting execution.
Do NOT execute tools in planning mode - only outline what would be done.
"""

        private const val EXECUTION_SYSTEM_PROMPT = """
## EXECUTION MODE ACTIVE

You are in EXECUTION mode. Your role is to take direct action.

### Instructions:
1. **Act** quickly and decisively
2. **Use tools** to accomplish tasks
3. **Report** results clearly
4. **Handle errors** gracefully

### Behavior:
- Execute tool calls immediately
- Keep explanations concise
- Focus on completing the task
- If a tool fails, try alternatives
- Summarize what was done after completion

You have full access to execute safe tools automatically.
Dangerous tools will prompt for user approval.
"""

        private const val DEEP_THINKING_SYSTEM_PROMPT = """
## DEEP THINKING MODE ACTIVE

You are in DEEP THINKING mode. Your role is thorough analysis.

### Instructions:
1. **Think deeply** about the problem
2. **Consider** multiple perspectives
3. **Evaluate** trade-offs explicitly
4. **Show your reasoning** process
5. **Conclude** with well-supported recommendations

### Thinking Format:
Use <think> tags to show your reasoning process:

<think>
Let me analyze this step by step...

First consideration: ...
Counter-argument: ...
Alternative approach: ...

After weighing the options, I believe...
</think>

Then provide your conclusion.

### Behavior:
- Take time to reason through problems
- Don't rush to conclusions
- Explore edge cases and implications
- Support recommendations with reasoning
- Ask clarifying questions if needed
"""
    }
}
