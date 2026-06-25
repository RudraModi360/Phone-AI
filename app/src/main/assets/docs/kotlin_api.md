# Kotlin Backend Integration

## Ktor Server Setup

```kotlin
fun Application.module() {
    install(WebSockets)
    install(ContentNegotiation) { json() }
    
    val agentService = AgentService()
    
    routing {
        route("/api") {
            post("/chat") { agentService.chat(call) }
            route("/reasoning") {
                post("/level") { agentService.setLevel(call) }
                get("/status") { agentService.getStatus(call) }
            }
            route("/tasks") {
                post { agentService.createTask(call) }
                get { agentService.listTasks(call) }
                post("/{id}/close") { agentService.closeTask(call) }
            }
            route("/plans") {
                post { agentService.createPlan(call) }
                post("/{id}/approve") { agentService.approvePlan(call) }
            }
        }
        webSocket("/ws/progress") {
            agentService.progressStream(this)
        }
    }
}
```

## Request/Response Models

```kotlin
@Serializable
data class ChatRequest(
    val message: String,
    val sessionId: String,
    val reasoningLevel: Int? = null  // Override for this request
)

@Serializable
data class ChatResponse(
    val response: String,
    val reasoningLevel: String,
    val toolsUsed: List<String>,
    val turnCount: Int
)

@Serializable
data class ProgressEvent(
    val taskId: String,
    val percent: Int,
    val message: String,
    val eta: Long?  // milliseconds
)
```

## Android Client (Retrofit)

```kotlin
interface AgentApi {
    @POST("api/chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse
    
    @POST("api/reasoning/level")
    suspend fun setLevel(@Body level: LevelRequest): ReasoningState
    
    @POST("api/tasks")
    suspend fun createTask(@Body task: CreateTaskRequest): TrackerTask
    
    @GET("api/tasks")
    suspend fun listTasks(@Query("status") status: String?): List<TrackerTask>
}
```

## WebSocket Progress Stream

```kotlin
class ProgressRepository(private val client: OkHttpClient) {
    fun progressFlow(sessionId: String): Flow<ProgressEvent> = callbackFlow {
        val request = Request.Builder()
            .url("ws://server/ws/progress?session=$sessionId")
            .build()
        
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = Json.decodeFromString<ProgressEvent>(text)
                trySend(event)
            }
        })
        
        awaitClose { ws.close(1000, "Done") }
    }
}
```
