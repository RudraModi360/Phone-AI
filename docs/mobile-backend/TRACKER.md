# Task Tracker

## Task Types

| Type    | Use Case                           |
|---------|-------------------------------------|
| EPIC    | Large feature spanning multiple tasks |
| TASK    | Standard work item                  |
| SUBTASK | Child of a TASK                    |
| BUG     | Defect/issue tracking              |

## Task Status Flow

```
OPEN → IN_PROGRESS → CLOSED
         ↓
      BLOCKED
```

## Kotlin Data Model

```kotlin
enum class TaskType { EPIC, TASK, SUBTASK, BUG }
enum class TaskStatus { OPEN, IN_PROGRESS, BLOCKED, CLOSED }
enum class TaskPriority { LOW, MEDIUM, HIGH, CRITICAL }

data class TrackerTask(
    val id: String,              // 6-char hex
    val title: String,
    val description: String = "",
    val type: TaskType = TaskType.TASK,
    var status: TaskStatus = TaskStatus.OPEN,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val parentId: String? = null,
    val dependencies: List<String> = emptyList(),
    var progressPercent: Int = 0,
    val notes: MutableList<String> = mutableListOf(),
    val createdAt: Instant = Instant.now()
)
```

## API Operations

| Operation      | Endpoint                | Body                            |
|----------------|-------------------------|----------------------------------|
| Create         | `POST /api/tasks`       | `{ title, type?, priority? }`   |
| List           | `GET /api/tasks?status=`| —                               |
| Update         | `PATCH /api/tasks/{id}` | `{ status?, progress? }`        |
| Add Dependency | `POST /api/tasks/{id}/deps` | `{ dependsOn: [id] }`       |
| Close          | `POST /api/tasks/{id}/close` | —                          |

## Dependency Rules

1. Cannot close task with open dependencies
2. Circular dependencies are rejected
3. Closing parent task auto-checks children
```
