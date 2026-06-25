# Agent Tools

## Tool Categories

| Category    | Tools                                    |
|-------------|------------------------------------------|
| **Tracker** | Create, Update, List, Close, Visualize   |
| **Planner** | Enter, Submit, Approve, View, Exit       |
| **Think**   | Deep reasoning with configurable depth   |
| **Web**     | Search, fetch web content                |
| **Files**   | Read, write, list files                  |
| **Git**     | Status, diff, commit                     |

## Tool Schema (OpenAI Function Format)

```kotlin
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject  // JSON Schema
)

// Example: TrackerCreateTool
val trackerCreate = ToolDefinition(
    name = "tracker_create",
    description = "Create a new task for tracking work",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("title") {
                put("type", "string")
                put("description", "Task title")
            }
            putJsonObject("task_type") {
                put("type", "string")
                put("enum", listOf("epic", "task", "subtask", "bug"))
            }
        }
        put("required", listOf("title"))
    }
)
```

## Tool Execution Flow

```
Agent Response → Parse tool_calls → ToolScheduler.execute()
              → Deduplicate → Execute → Return results
              → Feed back to Agent
```

## Think Tool Depths

| Depth   | Analysis Type                |
|---------|------------------------------|
| minimal | Quick check                  |
| low     | Surface analysis             |
| medium  | Standard step-by-step        |
| high    | Multiple perspectives        |
| deep    | Exhaustive, all angles       |
