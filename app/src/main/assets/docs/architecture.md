# Logicore Mobile Backend Architecture

## Core Components

```
┌─────────────────────────────────────────────────────────────────┐
│                     MOBILE APP (Kotlin)                         │
├─────────────────────────────────────────────────────────────────┤
│  UI Layer  │  ViewModel  │  Repository  │  API Client          │
└─────────────────────────────────────────────────────────────────┘
                              │ HTTP/WebSocket
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    BACKEND API (FastAPI/Ktor)                   │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   Reasoning  │  │   Tracker    │  │   Planner    │          │
│  │  Controller  │  │   Service    │  │   Service    │          │
│  │  └──────────────┘  └──────────────┘  └──────────────┘          │
│         └─────────────────┼─────────────────┘                   │
│                           ▼                                     │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                    Agent Runtime                          │  │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────────────┐ │  │
│  │  │ Loop    │ │ Token   │ │ Progress│ │ Tool Scheduler  │ │  │
│  │  │Detector │ │ Budget  │ │ Service │ │                 │ │  │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────────────┘ │  │
│  └──────────────────────────────────────────────────────────┘  │
│                           ▼                                     │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              LLM Provider (OpenAI/Azure/Ollama)          │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## State Management

| Component      | Persistence | Scope       |
|----------------|-------------|-------------|
| ReasoningLevel | Memory      | Per-session |
| Tasks          | JSON file   | Per-project |
| Plans          | JSON file   | Per-project |
| Progress       | Memory      | Per-task    |
| TokenBudget    | Memory      | Per-session |

## API Endpoints

| Method | Endpoint                  | Purpose                    |
|--------|---------------------------|----------------------------|
| POST   | `/api/chat`               | Chat with auto-reasoning   |
| POST   | `/api/reasoning/level`    | Set level (1-5)            |
| POST   | `/api/tasks`              | Create task                |
| GET    | `/api/tasks`              | List tasks                 |
| POST   | `/api/tasks/{id}/close`   | Close task                 |
| POST   | `/api/plans`              | Create plan                |
| POST   | `/api/plans/{id}/approve` | Approve plan               |
| WS     | `/ws/progress`            | Real-time progress events  |
