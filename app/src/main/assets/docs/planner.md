# Plan Mode

## Plan Lifecycle

```
DRAFT → PENDING → APPROVED → IN_PROGRESS → COMPLETED
                     ↓
                  REJECTED
```

## Kotlin Data Model

```kotlin
enum class PlanStatus { DRAFT, PENDING, APPROVED, IN_PROGRESS, COMPLETED, REJECTED }
enum class StepStatus { PENDING, IN_PROGRESS, COMPLETED, SKIPPED, FAILED }

data class PlanStep(
    val id: String,
    val description: String,
    val order: Int,
    var status: StepStatus = StepStatus.PENDING,
    val dependencies: List<String> = emptyList(),
    val estimatedTurns: Int = 1,
    var actualTurns: Int = 0
)

data class Plan(
    val id: String,
    val title: String,
    val description: String = "",
    var status: PlanStatus = PlanStatus.DRAFT,
    val steps: List<PlanStep>,
    val reason: String = "",
    val createdAt: Instant = Instant.now(),
    var approvedAt: Instant? = null
)
```

## Workflow

```kotlin
// 1. Enter plan mode
planService.enterPlanMode(reason = "Complex refactoring task")

// 2. Create and submit plan
val plan = planService.createPlan(
    title = "Refactor Auth",
    steps = listOf(
        mapOf("description" to "Audit current code"),
        mapOf("description" to "Extract service layer"),
        mapOf("description" to "Add error handling"),
        mapOf("description" to "Write tests")
    )
)
planService.submitPlan(plan.id)

// 3. Approve (can be user-triggered or auto)
planService.approvePlan(plan.id)

// 4. Execute steps
planService.startStep(plan.id, stepId)
planService.completeStep(plan.id, stepId)

// 5. Exit
planService.exitPlanMode()
```

## UI Indicators

| State          | UI Element                        |
|----------------|-----------------------------------|
| Plan Mode On   | Show `📋` icon, change input prompt |
| Step Progress  | Progress bar per step             |
| Pending Approval | Show approve/reject buttons     |
