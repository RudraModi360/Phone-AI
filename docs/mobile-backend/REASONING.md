# Reasoning Levels

## 5-Level System

| Level   | Value | Thinking Budget | Use Case                    |
|---------|-------|-----------------|------------------------------|
| MINIMAL | 1     | 512 tokens      | Quick lookups, simple Q&A   |
| LOW     | 2     | 1024 tokens     | Light analysis              |
| MEDIUM  | 3     | 2048 tokens     | Balanced (default)          |
| HIGH    | 4     | 4096 tokens     | Deep analysis, debugging    |
| DEEP    | 5     | 8192 tokens     | Architecture, complex tasks |

## Auto-Escalation Keywords

```kotlin
val ESCALATION_KEYWORDS = listOf(
    "analyze", "debug", "architect", "design", "complex",
    "investigate", "optimize", "refactor", "security"
)
```

When user input contains these → auto-escalate reasoning level.

## Kotlin Data Model

```kotlin
enum class ReasoningLevel(val value: Int, val budget: Int) {
    MINIMAL(1, 512),
    LOW(2, 1024),
    MEDIUM(3, 2048),
    HIGH(4, 4096),
    DEEP(5, 8192)
}

data class ReasoningState(
    var currentLevel: ReasoningLevel = ReasoningLevel.MEDIUM,
    val originalLevel: ReasoningLevel = ReasoningLevel.MEDIUM,
    var escalationCount: Int = 0,
    var autoEscalate: Boolean = true
)
```

## UI Component: Reasoning Slider

```kotlin
// 5-position slider matching reasoning levels
Slider(
    value = reasoningLevel.value.toFloat(),
    onValueChange = { viewModel.setReasoningLevel(it.toInt()) },
    valueRange = 1f..5f,
    steps = 3  // Creates 5 discrete positions
)
```
