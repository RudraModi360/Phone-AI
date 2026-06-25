# Quick Reference Card

## Reasoning Levels
```
1=MINIMAL  2=LOW  3=MEDIUM  4=HIGH  5=DEEP
⚡         Ref        📊        🔶      🧠
```

## Task Status Icons
```
OPEN=⬚  IN_PROGRESS=▶  BLOCKED=⛔  CLOSED=✓
```

## Plan Status Icons
```
DRAFT=📝  PENDING=⏳  APPROVED=✅  IN_PROGRESS=🔄  COMPLETED=🏁
```

## Essential API Calls

```bash
# Chat with reasoning
POST /api/chat {"message": "...", "sessionId": "..."}

# Set reasoning level
POST /api/reasoning/level {"level": 4}

# Create task
POST /api/tasks {"title": "Build login", "type": "task"}

# Create and approve plan
POST /api/plans {"title": "...", "steps": [...]}
POST /api/plans/{id}/approve
```

## File Locations
```
.logicore/
├── tracker/tasks.json   # Task persistence
└── plans/               # Plan files (one per plan)
```
