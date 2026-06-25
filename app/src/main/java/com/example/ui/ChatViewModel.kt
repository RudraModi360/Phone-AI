package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.service.*
import com.example.runtime.AgentRuntime
import com.example.runtime.RuntimeEvent
import com.example.runtime.config.RuntimeConfig
import com.example.runtime.loop.AgentEvent
import com.example.runtime.loop.AgentEventType
import com.example.runtime.mode.AgentMode
import com.example.runtime.mode.ModeController
import com.example.runtime.reasoning.ReasoningLevel
import com.example.providers.ProviderRegistry
import com.example.providers.OllamaProvider
import com.example.providers.GeminiProvider
import com.example.skills.SkillRegistry
import com.example.context.UserContextBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

enum class AgentState(val label: String, val icon: String) {
    IDLE("Ready", "⚡"),
    THINKING("Thinking...", "❖"),
    EXECUTING_TOOL("Executing tool...", "⚙️"),
    TYPING("logy is typing...", "✍️"),
    WAITING_APPROVAL("Awaiting approval...", "🔑"),
    ERROR("Error", "⚠️")
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val chatDao = db.chatDao()
    private val settingDao = db.settingDao()
    private val memoryService = com.example.memory.MemoryService(db.memoryDao())
    private val planService = com.example.planner.PlanService(db.planDao())
    private val trackerService = com.example.tracker.TrackerService(db.trackerDao())
    private val skillRegistry = SkillRegistry(application)
    private val daemonService = DaemonService()
    
    // Mode Controller for Planning/Execution/DeepThinking modes
    private val modeController = ModeController()

    // Agent Runtime for orchestrated execution
    private val agentRuntime = AgentRuntime(
        RuntimeConfig(
            maxTurns = 25,
            toolTimeoutMs = 30_000L
        )
    )
    
    // Agent Mode State (exposed from ModeController)
    val currentAgentMode: StateFlow<AgentMode> = modeController.currentMode
    val currentReasoningLevel: StateFlow<ReasoningLevel> = modeController.currentConfig
        .map { it.reasoningLevel }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReasoningLevel.MEDIUM)

    // UI States
    val sessions = chatDao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedWorkspace = MutableStateFlow("My Projects")
    val selectedWorkspace: StateFlow<String> = _selectedWorkspace

    val filteredSessions = combine(sessions, _selectedWorkspace) { sessionList, workspace ->
        sessionList.filter { it.workspace == workspace }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId

    val messages = _currentSessionId.flatMapLatest { sessionId ->
        if (sessionId == null) flowOf(emptyList())
        else chatDao.getMessagesForSession(sessionId)
            .distinctUntilChanged()
            .debounce(30L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // App Preferences / Settings
    private val _isLocalMode = MutableStateFlow(true)
    val isLocalMode: StateFlow<Boolean> = _isLocalMode

    private val _apiKey = MutableStateFlow("292882765cd3482d97e4c50b55fcf88d.6oTmCXafba5UpomUnFFYQbtP")
    val apiKey: StateFlow<String> = _apiKey

    private val _daemonHost = MutableStateFlow("ws://localhost:3005")
    val daemonHost: StateFlow<String> = _daemonHost

    private val _daemonApiKey = MutableStateFlow("")
    val daemonApiKey: StateFlow<String> = _daemonApiKey

    private val _googleApiKey = MutableStateFlow("")
    val googleApiKey: StateFlow<String> = _googleApiKey

    private val _googleCx = MutableStateFlow("")
    val googleCx: StateFlow<String> = _googleCx

    private val _groqApiKey = MutableStateFlow("")
    val groqApiKey: StateFlow<String> = _groqApiKey

    private val _opencodeBaseUrl = MutableStateFlow("https://ollama.com/api")
    val opencodeBaseUrl: StateFlow<String> = _opencodeBaseUrl

    private val _selectedModel = MutableStateFlow("ministral-3:8b")
    val selectedModel: StateFlow<String> = _selectedModel

    private val _themeMode = MutableStateFlow("Dark") // Light, Dark, Adaptive
    val themeMode: StateFlow<String> = _themeMode

    private val _fontSize = MutableStateFlow(14) // 12, 14, 16, 18
    val fontSize: StateFlow<Int> = _fontSize

    private val _profileName = MutableStateFlow("Aman Verma")
    val profileName: StateFlow<String> = _profileName

    private val _profileRole = MutableStateFlow("Local Developer")
    val profileRole: StateFlow<String> = _profileRole

    private val _reasoningLevel = MutableStateFlow(3)
    val reasoningLevel: StateFlow<Int> = _reasoningLevel

    // System prompt for agent instructions
    private val _systemPrompt = MutableStateFlow("")
    val systemPrompt: StateFlow<String> = _systemPrompt

    // Operational States
    private val _daemonConnection = MutableStateFlow(ConnectionState.DISCONNECTED)
    val daemonConnection: StateFlow<ConnectionState> = _daemonConnection

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    // New: Tool execution state for UI display
    private val _isExecutingTool = MutableStateFlow(false)
    val isExecutingTool: StateFlow<Boolean> = _isExecutingTool

    private val _currentToolName = MutableStateFlow<String?>(null)
    val currentToolName: StateFlow<String?> = _currentToolName

    private val _activeToolApproval = MutableStateFlow<ChatMessage?>(null)
    val activeToolApproval: StateFlow<ChatMessage?> = _activeToolApproval

    val agentState: StateFlow<AgentState> = combine(
        _isThinking,
        _isExecutingTool,
        _isTyping,
        _activeToolApproval
    ) { thinking, executing, typing, approval ->
        when {
            approval != null -> AgentState.WAITING_APPROVAL
            executing -> AgentState.EXECUTING_TOOL
            thinking -> AgentState.THINKING
            typing -> AgentState.TYPING
            else -> AgentState.IDLE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AgentState.IDLE)

    private val _showSlashPalette = MutableStateFlow(false)
    val showSlashPalette: StateFlow<Boolean> = _showSlashPalette

    private val _statusText = MutableStateFlow("Operational")
    val statusText: StateFlow<String> = _statusText
    
    // Onboarding state - tracks if user has completed initial setup
    private val _isOnboardingComplete = MutableStateFlow(true) // Default true, will be set false if new user
    val isOnboardingComplete: StateFlow<Boolean> = _isOnboardingComplete
    
    private val _isLoadingSettings = MutableStateFlow(true)
    val isLoadingSettings: StateFlow<Boolean> = _isLoadingSettings

    private val _settingUpdates = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 50)
    private val _providerUpdateTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val telemetry = TelemetryManager.getTelemetryFlow(application)
        .stateIn(
            viewModelScope, 
            SharingStarted.WhileSubscribed(5000), 
            SystemTelemetry(1.8, 4.0, 18.2, 64.0)
        )

    init {
        // Initialize tool registry with default tools
        com.example.tools.ToolRegistry.initializeDefaults()
        
        // Initialize service-dependent tools (Plan, Tracker, Memory, Skills)
        com.example.tools.ToolRegistry.initializeWithServices(
            memoryService = memoryService,
            planService = planService,
            trackerService = trackerService,
            skillRegistry = skillRegistry
        )
        
        // Load skills from assets
        skillRegistry.loadAll()
        
        // Load default system prompt from assets
        loadDefaultSystemPrompt()

        // Load settings from Room Database on launch in a single non-blocking IO background fetch
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val targetKey = "292882765cd3482d97e4c50b55fcf88d.6oTmCXafba5UpomUnFFYQbtP"
                _apiKey.value = targetKey
                settingDao.insertSetting(AppSetting("api_key", targetKey))

                // Load all settings in a single DB fetch
                val settings = settingDao.getAllSettingsList().associate { it.key to it.value }

                val loadedDaemonHost = settings["daemon_host"] ?: "ws://localhost:3005"
                _daemonHost.value = loadedDaemonHost

                val loadedDaemonApiKey = settings["daemon_api_key"] ?: ""
                _daemonApiKey.value = loadedDaemonApiKey

                var gKey = settings["google_api_key"] ?: ""
                if (gKey.isBlank()) {
                    val defaultKey = com.example.BuildConfig.GOOGLE_API_KEY
                    gKey = if (defaultKey == "YOUR_GOOGLE_API_KEY" || defaultKey == "MY_GOOGLE_API_KEY" || defaultKey.isBlank()) "" else defaultKey
                    settingDao.insertSetting(AppSetting("google_api_key", gKey))
                }
                _googleApiKey.value = gKey

                var gCx = settings["google_cx"] ?: ""
                if (gCx.isBlank()) {
                    val defaultCx = com.example.BuildConfig.GOOGLE_CX
                    gCx = if (defaultCx == "YOUR_GOOGLE_CX" || defaultCx.isBlank()) "" else defaultCx
                    settingDao.insertSetting(AppSetting("google_cx", gCx))
                }
                _googleCx.value = gCx

                var groqKey = settings["groq_api_key"] ?: ""
                if (groqKey.isBlank()) {
                    val defaultGroq = com.example.BuildConfig.GROQ_API_KEY
                    groqKey = if (defaultGroq == "YOUR_GROQ_API_KEY" || defaultGroq.isBlank()) "" else defaultGroq
                    settingDao.insertSetting(AppSetting("groq_api_key", groqKey))
                }
                _groqApiKey.value = groqKey

                val savedUrl = settings["opencode_base_url"] ?: ""
                if (savedUrl.trim().isEmpty() || savedUrl.contains("opencode.ai") || savedUrl.contains("v1/")) {
                    _opencodeBaseUrl.value = "https://ollama.com/api"
                    settingDao.insertSetting(AppSetting("opencode_base_url", "https://ollama.com/api"))
                } else {
                    _opencodeBaseUrl.value = savedUrl
                }
                _isLocalMode.value = true

                val savedModel = settings["selected_model"] ?: ""
                // Use reliable model - ministral-3:8b or gemma3:27b tested to work well
                val unreliableModels = listOf("gpt-oss", "opencode", "llama3")
                if (savedModel.trim().isEmpty() || unreliableModels.any { savedModel.contains(it) }) {
                    _selectedModel.value = "ministral-3:8b"
                    settingDao.insertSetting(AppSetting("selected_model", "ministral-3:8b"))
                } else {
                    _selectedModel.value = savedModel
                }

                _themeMode.value = settings["theme_mode"] ?: "Dark"
                _fontSize.value = (settings["font_size"] ?: "14").toIntOrNull() ?: 14
                
                // Check if onboarding was completed
                val onboardingComplete = settings["onboarding_complete"] == "true"
                val savedProfileName = settings["profile_name"]
                
                if (!onboardingComplete || savedProfileName.isNullOrBlank()) {
                    // New user - show onboarding
                    _isOnboardingComplete.value = false
                    _profileName.value = ""
                    _profileRole.value = ""
                } else {
                    // Existing user - load saved profile
                    _isOnboardingComplete.value = true
                    _profileName.value = savedProfileName
                    _profileRole.value = settings["profile_role"] ?: "General User"
                }
                
                _reasoningLevel.value = (settings["reasoning_level"] ?: "3").toIntOrNull() ?: 3
                _systemPrompt.value = settings["system_prompt"] ?: ""

                val lastSavedWorkspace = settings["selected_workspace"] ?: "My Projects"
                val lastSavedSessionId = settings["current_session_id"]

                // Create initial session if none exists exactly once on launch by loading actual DB state first
                val dbSessions = try {
                    chatDao.getAllSessionsList()
                } catch (e: Exception) {
                    emptyList()
                }
                if (dbSessions.isEmpty() && _currentSessionId.value == null) {
                    insertNewSession(lastSavedWorkspace)
                } else if (_currentSessionId.value == null && dbSessions.isNotEmpty()) {
                    val matchedSession = dbSessions.find { it.id == lastSavedSessionId }
                    if (matchedSession != null) {
                        _selectedWorkspace.value = matchedSession.workspace
                        _currentSessionId.value = matchedSession.id
                    } else {
                        val workspaceSessions = dbSessions.filter { it.workspace == lastSavedWorkspace }
                        if (workspaceSessions.isNotEmpty()) {
                            _selectedWorkspace.value = lastSavedWorkspace
                            _currentSessionId.value = workspaceSessions.first().id
                        } else {
                            val firstSession = dbSessions.first()
                            _selectedWorkspace.value = firstSession.workspace
                            _currentSessionId.value = firstSession.id
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to launch initial settings or session loads", e)
                if (_currentSessionId.value == null) {
                    val fallbackId = UUID.randomUUID().toString()
                    _currentSessionId.value = fallbackId
                }
                // New user if settings failed to load
                _isOnboardingComplete.value = false
            }

            // Mark settings as loaded
            _isLoadingSettings.value = false
            
            // Initialize provider registry with current settings
            initializeProviders()
        }

        // Connect Daemon Daemon listener in background if WS mode
        viewModelScope.launch {
            daemonService.connectionState.collect {
                _daemonConnection.value = it
            }
        }

        viewModelScope.launch {
            daemonService.events.collect { event ->
                handleDaemonEvent(event)
            }
        }

        // Collect AgentRuntime events for UI updates
        viewModelScope.launch {
            agentRuntime.events.collect { event ->
                handleRuntimeEvent(event)
            }
        }

        // Debounced settings persistence and provider re-initialization
        viewModelScope.launch {
            _settingUpdates
                .debounce(500L)
                .collect { (key, value) ->
                    persistSetting(key, value)
                }
        }

        viewModelScope.launch {
            _providerUpdateTrigger
                .debounce(1500L)
                .collect {
                    initializeProviders()
                }
        }
    }
    
    /**
     * Load default system prompt from assets/system_prompt.md
     */
    private fun loadDefaultSystemPrompt() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Check if user has a custom system prompt saved
                val savedPrompt = settingDao.getSettingValue("system_prompt")
                
                if (savedPrompt.isNullOrBlank()) {
                    // Load default from assets
                    val context = getApplication<Application>()
                    context.assets.open("system_prompt.md").use { inputStream ->
                        val defaultPrompt = inputStream.bufferedReader().readText()
                        _systemPrompt.value = defaultPrompt
                        settingDao.insertSetting(AppSetting("system_prompt", defaultPrompt))
                        Log.d("ChatViewModel", "Loaded default system prompt from assets (${defaultPrompt.length} chars)")
                    }
                } else {
                    _systemPrompt.value = savedPrompt
                    Log.d("ChatViewModel", "Using saved system prompt (${savedPrompt.length} chars)")
                }
            } catch (e: Exception) {
                Log.w("ChatViewModel", "Failed to load system prompt from assets: ${e.message}")
                // Use minimal fallback
                _systemPrompt.value = "You are Logy, an AI assistant running on Android."
            }
        }
    }
    
    /**
     * Auto-generate a session title based on the first user message.
     */
    private suspend fun autoRenameSession(sessionId: String, userMessage: String) {
        try {
            val session = chatDao.getSessionById(sessionId) ?: return
            
            // Only auto-rename if it's still the default title format
            if (session.title.matches(Regex(".* Session \\d+$"))) {
                // Generate a short title from the user message
                val title = generateSessionTitle(userMessage)
                val updatedSession = session.copy(title = title)
                chatDao.insertSession(updatedSession)
                Log.d("ChatViewModel", "Auto-renamed session to: $title")
            }
        } catch (e: Exception) {
            Log.w("ChatViewModel", "Failed to auto-rename session: ${e.message}")
        }
    }
    
    /**
     * Generate a concise session title from user message.
     */
    private fun generateSessionTitle(message: String): String {
        // Clean and truncate the message for a title
        val cleaned = message
            .replace(Regex("[\\n\\r\\t]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        // Extract first meaningful part (up to 40 chars)
        val title = when {
            cleaned.length <= 40 -> cleaned
            else -> {
                // Try to break at word boundary
                val truncated = cleaned.take(40)
                val lastSpace = truncated.lastIndexOf(' ')
                if (lastSpace > 20) {
                    truncated.take(lastSpace) + "..."
                } else {
                    truncated + "..."
                }
            }
        }
        
        return title.ifBlank { "New Chat" }
    }

    // Handle AgentRuntime events for UI feedback
    private fun handleRuntimeEvent(event: RuntimeEvent) {
        when (event) {
            is RuntimeEvent.TurnStarted -> {
                Log.d("ChatViewModel", "Turn started: ${event.turn.turnNumber}")
                _statusText.value = "Turn ${event.turn.turnNumber} started..."
            }
            is RuntimeEvent.TurnCompleted -> {
                Log.d("ChatViewModel", "Turn completed: ${event.turn.turnNumber}")
                _statusText.value = "Turn ${event.turn.turnNumber} completed"
            }
            is RuntimeEvent.BudgetExceeded -> {
                Log.w("ChatViewModel", "Budget exceeded!")
                _statusText.value = "Turn budget exceeded"
                _isThinking.value = false
            }
            is RuntimeEvent.LoopDetected -> {
                Log.w("ChatViewModel", "Loop detected: ${event.result.loopType}")
                _statusText.value = "Loop detected - recovering..."
            }
            is RuntimeEvent.ReasoningLevelChanged -> {
                Log.d("ChatViewModel", "Reasoning level changed: ${event.from} -> ${event.to}")
            }
            is RuntimeEvent.ToolExecuted -> {
                Log.d("ChatViewModel", "Tool executed: ${event.toolName}, success=${event.success}")
                _statusText.value = if (event.success) "Tool completed" else "Tool failed"
            }
            is RuntimeEvent.ProgressUpdate -> {
                _statusText.value = event.message
            }
        }
    }

    /**
     * Initialize LLM providers based on current settings.
     * Called on startup and when provider settings change.
     */
    private suspend fun initializeProviders() {
        try {
            ProviderRegistry.clear()
            
            val baseUrl = _opencodeBaseUrl.value
            val model = _selectedModel.value
            val key = _apiKey.value
            
            // Register Ollama provider (primary)
            val ollamaProvider = OllamaProvider(
                baseUrl = baseUrl,
                model = model,
                apiKey = key,
                thinkMode = model.contains("gpt-oss") || model.contains("qwen")
            )
            ProviderRegistry.register(ollamaProvider)
            ProviderRegistry.setActive("ollama")
            
            // Optionally register Gemini provider if API key is available
            if (key.isNotEmpty() && !key.contains(".")) {
                try {
                    val geminiProvider = GeminiProvider(
                        apiKey = key,
                        model = "gemini-1.5-flash"
                    )
                    ProviderRegistry.register(geminiProvider)
                } catch (e: Exception) {
                    Log.w("ChatViewModel", "Failed to initialize Gemini provider: ${e.message}")
                }
            }
            
            Log.d("ChatViewModel", "Providers initialized: ${ProviderRegistry.names()}")
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Failed to initialize providers", e)
        }
    }

    // --- Actions ---

    private suspend fun insertNewSession(workspaceName: String): String {
        val id = UUID.randomUUID().toString()
        val dbSessions = try {
            chatDao.getAllSessionsList()
        } catch (e: Exception) {
            emptyList()
        }
        val localCount = dbSessions.filter { it.workspace == workspaceName }.size
        val newSession = ChatSession(
            id = id,
            title = "$workspaceName Session ${localCount + 1}",
            model = _selectedModel.value,
            workspace = workspaceName
        )
        try {
            chatDao.insertSession(newSession)
            _currentSessionId.value = id
            updateSetting("current_session_id", id)
            updateSetting("selected_workspace", workspaceName)
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Failed to insert session in DB", e)
        }
        return id
    }

    private suspend fun persistSetting(key: String, value: String) {
        try {
            withContext(Dispatchers.IO) {
                settingDao.insertSetting(AppSetting(key, value))
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Failed to persist setting: $key", e)
        }
    }

    fun updateSetting(key: String, value: String) {
        _settingUpdates.tryEmit(key to value)
        
        when (key) {
            "api_key" -> {
                _apiKey.value = value
                _providerUpdateTrigger.tryEmit(Unit)
            }
            "daemon_host" -> _daemonHost.value = value
            "daemon_api_key" -> _daemonApiKey.value = value
            "google_api_key" -> _googleApiKey.value = value
            "google_cx" -> _googleCx.value = value
            "groq_api_key" -> _groqApiKey.value = value
            "opencode_base_url" -> {
                _opencodeBaseUrl.value = value
                _providerUpdateTrigger.tryEmit(Unit)
            }
            "is_local_mode" -> _isLocalMode.value = value.toBoolean()
            "selected_model" -> {
                _selectedModel.value = value
                _providerUpdateTrigger.tryEmit(Unit)
            }
            "theme_mode" -> _themeMode.value = value
            "font_size" -> _fontSize.value = value.toIntOrNull() ?: 14
            "profile_name" -> _profileName.value = value
            "profile_role" -> _profileRole.value = value
            "reasoning_level" -> {
                val level = value.toIntOrNull() ?: 3
                _reasoningLevel.value = level
                // Sync with ModeController
                modeController.setReasoningLevel(ReasoningLevel.fromValue(level))
            }
            "system_prompt" -> _systemPrompt.value = value
        }
    }

    // ============== ONBOARDING ==============
    
    /**
     * Complete onboarding for a new user.
     * Saves their profile information and marks onboarding as complete.
     */
    fun completeOnboarding(name: String, role: String, systemPrompt: String) {
        viewModelScope.launch {
            try {
                // Save all profile settings
                settingDao.insertSetting(AppSetting("profile_name", name))
                settingDao.insertSetting(AppSetting("profile_role", role))
                settingDao.insertSetting(AppSetting("system_prompt", systemPrompt))
                settingDao.insertSetting(AppSetting("onboarding_complete", "true"))
                
                // Update in-memory state
                _profileName.value = name
                _profileRole.value = role
                _systemPrompt.value = systemPrompt
                _isOnboardingComplete.value = true
                
                Log.d("ChatViewModel", "Onboarding completed for user: $name ($role)")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to save onboarding data", e)
            }
        }
    }
    
    // ============== AGENT MODE CONTROL ==============
    
    /**
     * Set the agent mode (Planning, Execution, DeepThinking).
     */
    fun setAgentMode(mode: AgentMode) {
        Log.d("ChatViewModel", "Setting agent mode: $mode")
        modeController.setMode(mode, "user_selection")
        // Update reasoning level to mode default
        _reasoningLevel.value = mode.defaultReasoningLevel.value
    }
    
    /**
     * Set reasoning level independently (1-5).
     */
    fun setReasoningLevel(level: Int) {
        val reasoningLevel = ReasoningLevel.fromValue(level.coerceIn(1, 5))
        Log.d("ChatViewModel", "Setting reasoning level: $reasoningLevel")
        modeController.setReasoningLevel(reasoningLevel)
        _reasoningLevel.value = level
    }
    
    /**
     * Get current mode's display info for UI.
     */
    fun getModeDisplayInfo(): Triple<String, String, Long> {
        val mode = currentAgentMode.value
        return Triple(mode.displayName, mode.icon, mode.color)
    }
    
    /**
     * Get suggested mode based on query analysis.
     */
    fun suggestModeForQuery(query: String): AgentMode {
        return modeController.suggestModeForQuery(query)
    }

    fun selectSession(sessionId: String) {
        _currentSessionId.value = sessionId
        viewModelScope.launch {
            val session = chatDao.getSessionById(sessionId)
            if (session != null) {
                _selectedWorkspace.value = session.workspace
                updateSetting("selected_workspace", session.workspace)
            }
            updateSetting("current_session_id", sessionId)
        }
    }

    fun selectWorkspace(workspaceName: String) {
        _selectedWorkspace.value = workspaceName
        updateSetting("selected_workspace", workspaceName)
        
        val wsSessions = sessions.value.filter { it.workspace == workspaceName }
        if (wsSessions.isNotEmpty()) {
            val nextSessionId = wsSessions.first().id
            _currentSessionId.value = nextSessionId
            updateSetting("current_session_id", nextSessionId)
        } else {
            createNewSession(workspaceName)
        }
    }

    fun createNewSession(workspaceName: String = _selectedWorkspace.value) {
        viewModelScope.launch {
            insertNewSession(workspaceName)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            chatDao.deleteSessionWithMessages(sessionId)
            if (_currentSessionId.value == sessionId) {
                val remaining = sessions.value.filter { it.workspace == _selectedWorkspace.value && it.id != sessionId }
                if (remaining.isNotEmpty()) {
                    val fallbackId = remaining.first().id
                    _currentSessionId.value = fallbackId
                    updateSetting("current_session_id", fallbackId)
                } else {
                    insertNewSession(_selectedWorkspace.value)
                }
            }
        }
    }

    fun renameSession(sessionId: String, newName: String) {
        viewModelScope.launch {
            val session = chatDao.getSessionById(sessionId)
            if (session != null) {
                chatDao.insertSession(session.copy(title = newName))
            }
        }
    }

    fun toggleSlashPalette(show: Boolean) {
        _showSlashPalette.value = show
    }

    // Send Message Routing (Local Gemini API vs Remote Daemon WebSocket)
    fun sendMessage(text: String) {
        val sId = _currentSessionId.value ?: return
        if (text.trim().isEmpty()) return

        viewModelScope.launch {
            try {
                // Check for slash commands first
                if (text.startsWith("/")) {
                    handleSlashCommand(text)
                    return@launch
                }
                
                // Check if this is the first message in the session for auto-rename
                val existingMessages = withContext(Dispatchers.IO) {
                    chatDao.getMessagesForSessionList(sId)
                }
                val isFirstMessage = existingMessages.isEmpty()

                // Save user message to DB
                val userMsg = ChatMessage(
                    sessionId = sId,
                    role = "user",
                    content = text
                )
                chatDao.insertMessage(userMsg)
                
                // Auto-rename session based on first message
                if (isFirstMessage) {
                    withContext(Dispatchers.IO) {
                        autoRenameSession(sId, text)
                    }
                }

                // Always route directly to Ollama Direct AI completions
                executeLocalAILoop(text)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to sendMessage", e)
            }
        }
    }

    // Sanitizer helper for raw HTML tags in streaming content
    private fun sanitizeAndCleanMarkdown(input: String): String {
        var text = input
        text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
        text = text.replace(Regex("(?i)<b>(.*?)</b>"), "**$1**")
        text = text.replace(Regex("(?i)<i>(.*?)</i>"), "*$1*")
        text = text.replace(Regex("(?i)<p>(.*?)</p>"), "$1\n\n")
        // Stop aggressively stripping all other tags (<[^>]*>) 
        // to preserve <think>...</think> blocks from R1/deepseek models.
        return text.trim()
    }

    // Direct Ollama / OpenCode API Agent simulation with inline tool execution support
    private suspend fun executeLocalAILoop(userText: String, initialText: String = "") {
        val sId = _currentSessionId.value ?: return
        val key = _apiKey.value

        // Check remaining turns before proceeding
        val remainingTurns = agentRuntime.getRemainingTurns(sId)
        if (remainingTurns <= 0) {
            chatDao.insertMessage(
                ChatMessage(
                    sessionId = sId,
                    role = "model",
                    content = "⚠️ Turn budget exceeded. Please start a new session or wait.",
                    status = "Error"
                )
            )
            _statusText.value = "Turn budget exceeded"
            return
        }
        
        // Adjust reasoning level based on query complexity
        val reasoningLevel = agentRuntime.adjustReasoningForQuery(userText)
        Log.d("ChatViewModel", "Reasoning level adjusted to: $reasoningLevel for query")

        // 1. One and only Assistant model message created initially in "Pending" status
        val aiMsgId = chatDao.insertMessage(
            ChatMessage(
                sessionId = sId,
                role = "model",
                content = initialText,
                status = "Pending"
            )
        ).toInt()

        if (key.trim().isEmpty()) {
            val errContent = "API Key / Access Token is missing! Go to settings and enter your Ollama / OpenCode API Key/Token."
            chatDao.updateMessage(
                ChatMessage(
                    id = aiMsgId,
                    sessionId = sId,
                    role = "model",
                    content = errContent,
                    status = "Error"
                )
            )
            return
        }

        _isThinking.value = true
        _statusText.value = if (initialText.isNotEmpty()) initialText else "Analyzing Prompt..."

        // Query active context workspace memories for RAG injection
        val relevantMemories = withContext(Dispatchers.IO) {
            memoryService.getRelevantMemories(userText, projectId = sId)
        }
        val memoryContextPrompt = memoryService.formatForPrompt(relevantMemories)

        // Query user preferences for personalization injection
        val userPreferences = withContext(Dispatchers.IO) {
            memoryService.getUserPreferences()
        }

        // === EXTRACTION PIPELINE: Analyze user message for memories ===
        withContext(Dispatchers.IO) {
            try {
                val extractions = com.example.memory.UserMessageExtractor.extract(userText)
                for (extraction in extractions) {
                    memoryService.saveExtractedMemory(
                        type = extraction.type,
                        title = extraction.title,
                        content = extraction.content,
                        confidence = extraction.confidence
                    )
                    Log.d("ChatViewModel", "Extracted memory: [${extraction.type.value}] ${extraction.title}")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Memory extraction failed", e)
            }
        }

        // Get available tools descriptions
        val toolDescriptions = com.example.tools.ToolRegistry.getToolDescriptions()
        
        // Get active skill instructions
        val activeSkillInstructions = skillRegistry.getActiveInstructions()
        val skillPromptAddons = skillRegistry.getSystemPromptAddons()
        
        // Get mode-specific system prompt from ModeController
        val modeConfig = modeController.currentConfig.value
        val modeSystemPrompt = modeConfig.getSystemPromptAddon()
        val currentMode = modeController.currentMode.value

        // Read previous messages to build a real sliding model history
        val historyList = withContext(Dispatchers.IO) {
            chatDao.getMessagesForSessionList(sId)
        }
        
        // Use custom system prompt if provided, otherwise use default
        val customSystemPrompt = _systemPrompt.value
        val finalSystemPrompt = buildString {
            if (customSystemPrompt.isNotBlank()) {
                append(customSystemPrompt)
                append("\n\n")
            }
            
            append("You are logy, an intelligent AI assistant on Android.\n\n")
            
            // === USER CONTEXT ===
            val userContextBlock = UserContextBuilder.fromViewModel(
                profileName = _profileName.value,
                profileRole = _profileRole.value,
                preferences = userPreferences,
                context = getApplication()
            ).build()
            if (userContextBlock.isNotEmpty()) {
                append(userContextBlock)
                append("\n\n")
            }
            
            // === MODE-SPECIFIC INSTRUCTIONS ===
            append(modeSystemPrompt)
            append("\n\n")
            
            // === CURRENT MODE INDICATOR ===
            append("## CURRENT MODE: ${currentMode.displayName.uppercase()} ${currentMode.icon}\n")
            append("${currentMode.description}\n\n")
            
            // === REASONING LEVEL ===
            append("## REASONING LEVEL: ${reasoningLevel.name}\n")
            append(reasoningLevel.systemPromptAddon)
            append("\n\n")
            
            append("## AVAILABLE TOOLS\n")
            append(toolDescriptions)
            append("\n\n")
            
            append("## CRITICAL: TOOL CALL FORMAT\n")
            append("When you need to use a tool, you MUST output a JSON object with the \"tool\" key.\n")
            append("The \"tool\" key is REQUIRED - never omit it!\n\n")
            append("CORRECT FORMAT:\n")
            append("{\"tool\": \"datetime\", \"operation\": \"now\"}\n")
            append("{\"tool\": \"list_dir\", \"path\": \"/sdcard/\"}\n")
            append("{\"tool\": \"read_file\", \"path\": \"config.json\"}\n")
            append("{\"tool\": \"contacts\", \"operation\": \"search\", \"query\": \"John\"}\n\n")
            append("WRONG FORMAT (missing tool key - DO NOT DO THIS):\n")
            append("{\"operation\": \"now\"} ← WRONG! Missing \"tool\" key\n")
            append("{\"path\": \"~\"} ← WRONG! Missing \"tool\" key\n\n")
            
            append("## TOOL USAGE RULES\n")
            when (currentMode) {
                AgentMode.PLANNING -> {
                    append("- In PLANNING mode, do NOT execute tools - only outline what would be done\n")
                    append("- Create step-by-step plans with estimated effort\n")
                    append("- Wait for user approval before suggesting execution\n")
                }
                AgentMode.EXECUTION -> {
                    append("- Safe tools (datetime, read_file, list_dir, device_info, memory, plan, tracker, skill, contacts, media_search, document_search, find) execute automatically\n")
                    append("- Dangerous tools (write_file) require user approval\n")
                    append("- Act quickly and decisively\n")
                }
                AgentMode.DEEP_THINKING -> {
                    append("- Use <think> tags to show your reasoning process\n")
                    append("- Consider multiple perspectives before concluding\n")
                    append("- Tools can be used after thorough analysis\n")
                }
            }
            append("- Paths default to /sdcard/ (shared storage) or app storage\n")
            append("- Use device_info tool for accurate system information\n")
            append("- Use list_dir tool for listing files\n")
            append("- Use read_file tool for reading files\n")
            append("- Use contacts, media_search, document_search for accessing phone data\n")
            append("- Use find tool for personalised file search - describe what you want in plain English\n\n")
            
            append("## EXAMPLES\n")
            append("User: What time is it?\n")
            append("Assistant: {\"tool\": \"datetime\", \"operation\": \"now\"}\n\n")
            append("User: List files in Downloads\n")
            append("Assistant: {\"tool\": \"list_dir\", \"path\": \"/sdcard/Download\"}\n\n")
            append("User: Show me the contents of readme.md\n")
            append("Assistant: {\"tool\": \"read_file\", \"path\": \"readme.md\"}\n\n")
            append("User: Find John in my contacts\n")
            append("Assistant: {\"tool\": \"contacts\", \"operation\": \"search\", \"query\": \"John\"}\n\n")
            append("User: Show my recent photos\n")
            append("Assistant: {\"tool\": \"media_search\", \"operation\": \"recent_photos\", \"limit\": 10}\n\n")
            append("User: Can you find those budget pdfs I downloaded last week?\n")
            append("Assistant: {\"tool\": \"find\", \"description\": \"budget pdfs from last week\"}\n\n")
            append("User: Show me my vacation photos from june\n")
            append("Assistant: {\"tool\": \"find\", \"description\": \"vacation photos from june\"}\n\n")
            
            append("Keep responses clean and concise. Use markdown for formatting.")
            
            // Include memory context
            if (memoryContextPrompt.isNotEmpty()) {
                append("\n\nRelevant context from memory:\n$memoryContextPrompt")
            }
            
            // Include active skill instructions
            if (activeSkillInstructions.isNotEmpty()) {
                append(activeSkillInstructions)
            }
            if (skillPromptAddons.isNotEmpty()) {
                append(skillPromptAddons)
            }
        }
        
        Log.d("ChatViewModel", "System prompt built (${finalSystemPrompt.length} chars) for mode: $currentMode")
        
        val apiMessages = mutableListOf<OpenCodeMessage>()
        apiMessages.add(
            OpenCodeMessage(
                role = "system",
                content = finalSystemPrompt
            )
        )

        val conversationHistory = historyList.filter { it.id != aiMsgId && it.status != "Pending" }
        conversationHistory.takeLast(10).forEach { msg ->
            val mappedRole = when (msg.role) {
                "user" -> "user"
                "model" -> "assistant"
                "tool" -> "user"
                "system" -> "system"
                else -> "user"
            }
            apiMessages.add(
                OpenCodeMessage(
                    role = mappedRole,
                    content = msg.content
                )
            )
        }

        // Build request URL based on endpoint type
        val base = opencodeBaseUrl.value.trim().removeSuffix("/")
        val requestUrl = when {
            // Ollama Cloud API (https://ollama.com/api)
            base.contains("ollama.com/api") -> {
                if (base.endsWith("/chat")) base else "$base/chat"
            }
            // Local Ollama with /api endpoint
            base.endsWith("/api") -> {
                "$base/chat"
            }
            // OpenAI-compatible endpoint (/v1/chat/completions)
            base.contains("/v1") -> {
                if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
            }
            // Local Ollama (localhost:11434)
            base.contains("localhost") || base.contains("127.0.0.1") -> {
                "$base/api/chat"
            }
            // Default: assume OpenAI-compatible
            else -> {
                "$base/v1/chat/completions"
            }
        }
        
        Log.d("ChatViewModel", "Built request URL: $requestUrl from base: $base")

        try {
            _statusText.value = "Thinking..."
            Log.d("ChatViewModel", "Calling streaming API with model: ${_selectedModel.value}")

            val provider = ProviderRegistry.getActive() ?: OllamaProvider(
                baseUrl = base,
                model = _selectedModel.value,
                apiKey = key,
                thinkMode = _selectedModel.value.contains("gpt-oss") || _selectedModel.value.contains("qwen")
            )

            val providerMessages = conversationHistory.takeLast(10).map { msg ->
                val role = when (msg.role) {
                    "user" -> "user"
                    "model" -> "assistant"
                    "tool" -> "tool"
                    "system" -> "system"
                    else -> "user"
                }
                com.example.providers.ChatMessage(
                    role = role,
                    content = msg.content,
                    toolCallId = if (msg.role == "tool") "call_${msg.id}" else null,
                    name = msg.toolName
                )
            }

            var aiText = ""
            
            withContext(Dispatchers.IO) {
                provider.chatStream(
                    messages = providerMessages,
                    tools = null,
                    systemInstruction = finalSystemPrompt
                ).collect { chunk ->
                    chunk.content?.let { contentPiece ->
                        // Skip thinking blocks for display (they're for internal use)
                        if (!contentPiece.startsWith("<think>")) {
                            if (chunk.isComplete) {
                                // If the chunk is complete, it contains the full accumulated response
                                aiText = contentPiece
                            } else {
                                // Otherwise, it's an incremental chunk, so we append it
                                aiText += contentPiece
                            }
                            
                            chatDao.updateMessage(
                                ChatMessage(
                                    id = aiMsgId,
                                    sessionId = sId,
                                    role = "model",
                                    content = aiText,
                                    status = "Streaming"
                                )
                            )
                        }
                    }
                }
            }

            _isThinking.value = false
            _statusText.value = "Operational"

            // Now parse if there's any tool JSON call in the markdown response!
            var foundToolCall = false
            try {
                // Try to find JSON object in the response using advanced brace-matching
                val parsedResult = extractJsonAndRange(aiText)
                
                if (parsedResult != null) {
                    val (jsonStr, range) = parsedResult
                    Log.d("ChatViewModel", "Found JSON in response: $jsonStr")
                    
                    val toolObj = JSONObject(jsonStr)
                    
                    // Get tool name - either from "tool" key or infer from parameters
                    var toolName = toolObj.optString("tool", "")
                    
                    // If tool key is missing, try to infer tool from parameters
                    if (toolName.isEmpty()) {
                        toolName = inferToolFromParams(toolObj)
                        Log.d("ChatViewModel", "Inferred tool name: $toolName from params")
                    }
                    
                    if (toolName.isNotEmpty()) {
                        // Map incoming tool names to our registered tools
                        val mappedToolName = when (toolName) {
                            "read_project_files" -> "read_file"
                            "list_directory", "ls", "dir" -> "list_dir"
                            "write", "create_file" -> "write_file"
                            "time", "date", "now" -> "datetime"
                            "exec", "run", "execute", "bash", "sh" -> "shell_exec"
                            else -> toolName
                        }
                        
                        // Check if tool exists in registry
                        val tool = com.example.tools.ToolRegistry.get(mappedToolName)
                        if (tool != null) {
                            // Build tool arguments
                            val toolArgsMap = mutableMapOf<String, Any?>()
                            toolObj.keys().forEach { key ->
                                if (key != "tool") {
                                    toolArgsMap[key] = toolObj.opt(key)
                                }
                            }
                            
                            // Determine risk level from the actual tool
                            val risk = when (tool.riskLevel) {
                                com.example.tools.RiskLevel.SAFE -> "low"
                                com.example.tools.RiskLevel.APPROVAL_REQUIRED -> "medium"
                                com.example.tools.RiskLevel.DANGEROUS -> "high"
                            }

                            // Extract any text before the tool call as AI explanation
                            val jsonStart = range.first
                            val preToolText = if (jsonStart > 0) {
                                sanitizeAndCleanMarkdown(aiText.substring(0, jsonStart))
                            } else ""

                            // Update original model message with the explanation (if any)
                            if (preToolText.isNotBlank()) {
                                chatDao.updateMessage(
                                    ChatMessage(
                                        id = aiMsgId,
                                        sessionId = sId,
                                        role = "model",
                                        content = preToolText,
                                        status = "Complete"
                                    )
                                )
                            } else {
                                chatDao.updateMessage(
                                    ChatMessage(
                                        id = aiMsgId,
                                        sessionId = sId,
                                        role = "model",
                                        content = "Preparing to execute tool: $mappedToolName...",
                                        status = "Complete"
                                    )
                                )
                            }

                            // Check if tool is SAFE - auto-execute without approval
                            if (tool.riskLevel == com.example.tools.RiskLevel.SAFE) {
                                _isExecutingTool.value = true
                                _currentToolName.value = mappedToolName
                                _statusText.value = "Auto-executing safe tool: $mappedToolName"
                                
                                // Check for loops before executing
                                val loopEvent = AgentEvent(
                                    type = AgentEventType.TOOL_CALL,
                                    toolName = mappedToolName,
                                    toolArgs = toolArgsMap
                                )
                                val loopResult = agentRuntime.checkLoop(loopEvent, sId)
                                if (loopResult.detected) {
                                    Log.w("ChatViewModel", "Loop detected: ${loopResult.loopType}")
                                    _statusText.value = "Loop detected - skipping repeated tool"
                                    chatDao.insertMessage(
                                        ChatMessage(
                                            sessionId = sId,
                                            role = "model",
                                            content = "⚠️ Loop detected: ${loopResult.loopType}. Stopping repeated execution.",
                                            status = "Warning"
                                        )
                                    )
                                    _isExecutingTool.value = false
                                    _currentToolName.value = null
                                    foundToolCall = true
                                } else {
                                    // Execute immediately
                                    val startTime = System.currentTimeMillis()
                                    val toolResult = withContext(Dispatchers.IO) {
                                        tool.execute(toolArgsMap)
                                    }
                                    val duration = System.currentTimeMillis() - startTime
                                    Log.d("ChatViewModel", "Tool $mappedToolName executed in ${duration}ms, success=${toolResult.success}")
                                
                                // Insert result message
                                val resultContent = if (toolResult.success) {
                                    "✅ ${mappedToolName} completed:\n```\n${toolResult.content}\n```"
                                } else {
                                    "❌ ${mappedToolName} failed: ${toolResult.error}"
                                }
                                
                                chatDao.insertMessage(
                                    ChatMessage(
                                        sessionId = sId,
                                        role = "tool",
                                        content = resultContent,
                                        toolName = mappedToolName,
                                        toolArgs = JSONObject(toolArgsMap).toString(),
                                        toolStatus = if (toolResult.success) "success" else "error",
                                        toolResult = toolResult.content ?: toolResult.error,
                                        riskLevel = risk,
                                        durationMs = duration,
                                        status = "Complete"
                                    )
                                )
                                
                                _isExecutingTool.value = false
                                _currentToolName.value = null
                                
                                // Continue agent loop with result
                                if (true) {
                                    _isThinking.value = true
                                    executeLocalAILoop(if (toolResult.success) "Tool `$mappedToolName` returned:\n${toolResult.content}\n\nContinue with the next step or provide a summary." else "Tool `$mappedToolName` failed with error:\n${toolResult.error}\n\nExplain the failure to the user or present an alternate strategy.")
                                }
                                
                                foundToolCall = true
                                } // end else (not a loop)
                            } else {
                                // Dangerous tool - requires approval
                                _isExecutingTool.value = true
                                _currentToolName.value = mappedToolName
                                _statusText.value = "Tool requires approval: $mappedToolName"

                                // Create tool approval message
                                val toolMsgId = chatDao.insertMessage(
                                    ChatMessage(
                                        sessionId = sId,
                                        role = "tool",
                                        content = "⚠️ Requesting approval to run: $mappedToolName",
                                        toolName = mappedToolName,
                                        toolArgs = JSONObject(toolArgsMap).toString(),
                                        toolStatus = "pending",
                                        riskLevel = risk,
                                        status = "Complete"
                                    )
                                ).toInt()

                                val toolMsg = ChatMessage(
                                    id = toolMsgId,
                                    sessionId = sId,
                                    role = "tool",
                                    content = "⚠️ Requesting approval to run: $mappedToolName",
                                    toolName = mappedToolName,
                                    toolArgs = JSONObject(toolArgsMap).toString(),
                                    toolStatus = "pending",
                                    riskLevel = risk,
                                    status = "Complete"
                                )

                                _activeToolApproval.value = toolMsg
                                foundToolCall = true
                            }
                        } else {
                            Log.w("ChatViewModel", "Unknown tool requested: $toolName (mapped: $mappedToolName)")
                            // Tool not found - update message to show what was attempted
                            chatDao.updateMessage(
                                ChatMessage(
                                    id = aiMsgId,
                                    sessionId = sId,
                                    role = "model",
                                    content = "⚠️ Attempted to use tool '$mappedToolName' but it's not available.\n\nOriginal response: $aiText",
                                    status = "Warning"
                                )
                            )
                            foundToolCall = true  // Prevent duplicate processing
                        }
                    } else {
                        // JSON found but couldn't determine tool - show the raw response
                        Log.d("ChatViewModel", "JSON found but no tool could be inferred: $jsonStr")
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error parsing inline JSON tool call: ${e.message}", e)
                _isExecutingTool.value = false
                _currentToolName.value = null
            }

            if (!foundToolCall) {
                var cleanAiText = sanitizeAndCleanMarkdown(aiText)
                if (cleanAiText.isBlank()) {
                    cleanAiText = "⚠️ Model returned an empty response. Proceeding with execution or waiting for next user input."
                }
                
                // Just finalize - streaming already displayed the text
                chatDao.updateMessage(
                    ChatMessage(
                        id = aiMsgId,
                        sessionId = sId,
                        role = "model",
                        content = cleanAiText,
                        status = "Complete"
                    )
                )

                // Save AI response insights to memory (better heuristics)
                val shouldSaveResponse = cleanAiText.length > 50 && (
                    cleanAiText.contains("learn", ignoreCase = true) ||
                    cleanAiText.contains("decid", ignoreCase = true) ||
                    cleanAiText.contains("configur", ignoreCase = true) ||
                    cleanAiText.contains("recommend", ignoreCase = true) ||
                    cleanAiText.contains("best practice", ignoreCase = true) ||
                    cleanAiText.contains("you should", ignoreCase = true)
                )
                if (shouldSaveResponse) {
                    val title = "Insight: " + userText.take(40).trim()
                    memoryService.saveExtractedMemory(
                        type = com.example.memory.MemoryType.PROJECT,
                        title = title,
                        content = cleanAiText.take(300),
                        confidence = 0.6f
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "API call failed", e)
            _isThinking.value = false
            _statusText.value = "Error"
            
            val errMsg = buildString {
                append("❌ **Error communicating with AI server**\n\n")
                append("**Error:** ${e.localizedMessage ?: "Unknown error"}\n\n")
                append("**Troubleshooting:**\n")
                append("1. Check if your Ollama server is running\n")
                append("2. Verify the endpoint URL: ${_opencodeBaseUrl.value}\n")
                append("3. Confirm the model exists: ${_selectedModel.value}\n")
                append("4. Check your API key/token in Settings\n\n")
                append("If using Ollama Cloud, ensure your subscription is active.")
            }
            
            chatDao.updateMessage(
                ChatMessage(
                    id = aiMsgId,
                    sessionId = sId,
                    role = "model",
                    content = errMsg,
                    status = "Error"
                )
            )
        }
    }


    // Custom Interactive Tool approvals
    fun handleToolApprovalDecision(message: ChatMessage, approved: Boolean, rememberAll: Boolean) {
        _activeToolApproval.value = null
        val sId = _currentSessionId.value ?: return

        viewModelScope.launch {
            // Reset tool execution states
            _isExecutingTool.value = false
            _currentToolName.value = null

            if (approved) {
                _statusText.value = "Executing tool: ${message.toolName}..."
                _isExecutingTool.value = true
                _currentToolName.value = message.toolName
                
                // Update DB state to show "executing"
                val executingMessage = message.copy(
                    toolStatus = "executing",
                    content = "⚙️ Executing ${message.toolName}..."
                )
                chatDao.updateMessage(executingMessage)

                // Execute using real tool registry
                val startTime = System.currentTimeMillis()
                val toolResult = try {
                    val tool = com.example.tools.ToolRegistry.get(message.toolName ?: "")
                    val args = try {
                        val jsonObj = JSONObject(message.toolArgs ?: "{}")
                        jsonObj.keys().asSequence().associateWith { key -> jsonObj.opt(key) }
                    } catch (e: Exception) {
                        emptyMap<String, Any?>()
                    }
                    
                    if (tool != null) {
                        withContext(Dispatchers.IO) {
                            tool.execute(args)
                        }
                    } else {
                        com.example.tools.ToolResult.error("Tool '${message.toolName}' is not registered.")
                    }
                } catch (e: Exception) {
                    com.example.tools.ToolResult.error("Execution failed: ${e.message}")
                }
                val duration = System.currentTimeMillis() - startTime

                // Update message with result
                val resultContent = if (toolResult.success) {
                    "✅ Tool completed successfully:\n```\n${toolResult.content}\n```"
                } else {
                    "❌ Tool failed:\n```\n${toolResult.error ?: "Unknown error"}\n```"
                }

                val resultMsg = message.copy(
                    content = resultContent,
                    toolStatus = if (toolResult.success) "success" else "error",
                    toolResult = toolResult.content ?: toolResult.error,
                    durationMs = duration
                )
                chatDao.updateMessage(resultMsg)

                // Reset states
                _isExecutingTool.value = false
                _currentToolName.value = null
                _statusText.value = "Operational"

                // Continue the agent loop with tool result
                if (true) {
                    _isThinking.value = true
                    val nextLoopText = if (toolResult.success) "Tool `${message.toolName}` succeeded with output:\n${toolResult.content}\nProvide next workflow sequence or summary." else "Tool `${message.toolName}` failed with error:\n${toolResult.error}\nExplain the failure to the user or present an alternate strategy."
                    executeLocalAILoop(nextLoopText)
                }

            } else {
                // Denied. Let the user and AI know.
                val deniedMessage = message.copy(
                    content = "🚫 Tool execution blocked by user.",
                    toolStatus = "denied"
                )
                chatDao.updateMessage(deniedMessage)

                _statusText.value = "Operational"

                // Inform the agent of user refusal
                _isThinking.value = true
                executeLocalAILoop("User manually refused to run tool `${message.toolName}`. Present alternate strategy.")
            }
        }
    }

    /**
     * Extracts a complete balanced JSON object string from text and its character range.
     * Supports markdown json fences and custom bracket-matching for nested structures.
     */
    private fun extractJsonAndRange(text: String): Pair<String, IntRange>? {
        // 1. Try to find json inside markdown code blocks first
        val codeBlockRegex = Regex("""```(?:json)?\s*(\{[\s\S]*?\})\s*```""", RegexOption.DOT_MATCHES_ALL)
        val match = codeBlockRegex.find(text)
        if (match != null) {
            val jsonStr = match.groupValues[1].trim()
            val startIdx = text.indexOf(jsonStr, match.range.first)
            if (startIdx != -1) {
                return Pair(jsonStr, startIdx until (startIdx + jsonStr.length))
            }
        }
        
        // 2. Otherwise find the first '{' and perform brace matching to extract the balanced JSON object
        val startIdx = text.indexOf('{')
        if (startIdx == -1) return null
        
        var openBrackets = 0
        var endIdx = -1
        var inString = false
        var escape = false
        
        for (i in startIdx until text.length) {
            val char = text[i]
            if (escape) {
                escape = false
                continue
            }
            if (char == '\\') {
                escape = true
                continue
            }
            if (char == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (char == '{') {
                    openBrackets++
                } else if (char == '}') {
                    openBrackets--
                    if (openBrackets == 0) {
                        endIdx = i
                        break
                    }
                }
            }
        }
        
        if (endIdx != -1) {
            val jsonStr = text.substring(startIdx, endIdx + 1)
            return Pair(jsonStr, startIdx..endIdx)
        }
        
        // 3. Robust Fallback: Repair abruptly truncated or mismatched JSON
        // If the brace matcher traversed everything and didn't find balanced closing braces,
        // it means we either had unclosed string quotes or missing closing braces. We can repair it!
        try {
            val subText = text.substring(startIdx)
            var pCount = 0
            var sClosed = false
            val sb = StringBuilder()
            var sEsc = false
            var sQuote = false
            
            for (char in subText) {
                sb.append(char)
                if (sEsc) {
                    sEsc = false
                    continue
                }
                if (char == '\\') {
                    sEsc = true
                    continue
                }
                if (char == '"') {
                    sQuote = !sQuote
                    continue
                }
                if (!sQuote) {
                    if (char == '{') pCount++
                    else if (char == '}') {
                        pCount--
                        if (pCount == 0) {
                            sClosed = true
                            break
                        }
                    }
                }
            }
            
            if (!sClosed && pCount > 0) {
                if (sQuote) {
                    sb.append('"') // close string auto-repair
                }
                while (pCount > 0) {
                    sb.append('}') // close brackets auto-repair
                    pCount--
                }
                val repaired = sb.toString()
                // Verify it's structurally valid to prevent parsing crashes afterwards
                val testObj = JSONObject(repaired)
                if (testObj.has("tool") || testObj.has("action") || testObj.has("title")) {
                    return Pair(repaired, startIdx until text.length)
                }
            }
        } catch (_: Exception) {}
        
        return null
    }

    /**
     * Infer tool name from JSON parameters when the "tool" key is missing.
     * This handles cases where models don't follow the exact format but output recognizable tool params.
     */
    private fun inferToolFromParams(jsonObj: JSONObject): String {
        val keys = jsonObj.keys().asSequence().toList()
        
        // Check for datetime tool patterns
        // If "operation" key exists with time-related values, or timezone/format keys exist
        if (keys.contains("operation")) {
            val operation = jsonObj.optString("operation", "").lowercase()
            // Accept various datetime-related operation values
            if (operation.isEmpty() || operation in listOf("now", "date", "time", "timestamp", "iso", "unix", "current", "today", "get")) {
                return "datetime"
            }
        }
        if (keys.any { it in listOf("timezone", "format") } && !keys.contains("command") && !keys.contains("path")) {
            return "datetime"
        }
        
        // Check for list_dir tool patterns
        if (keys.contains("path") || keys.contains("directory") || keys.contains("dir")) {
            val hasListKeywords = keys.any { it in listOf("show_hidden", "long_format", "recursive") }
            if (hasListKeywords || keys.size == 1) {
                // Single "path" key likely means list_dir
                return "list_dir"
            }
        }
        
        // Check for read_file tool patterns
        if (keys.any { it in listOf("file", "filename", "filepath") } || 
            (keys.contains("path") && keys.any { it in listOf("max_lines", "encoding", "start_line", "end_line") })) {
            return "read_file"
        }
        
        // Check for find tool patterns (natural language description)
        if (keys.contains("description") && !keys.contains("operation")) {
            return "find"
        }
        
        // Check for contacts tool patterns
        if (keys.contains("query") && (keys.contains("operation") || keys.any { it.contains("contact") })) {
            val operation = jsonObj.optString("operation", "").lowercase()
            if (operation in listOf("search", "list", "get", "find") || keys.any { it.contains("contact") }) {
                return "contacts"
            }
        }
        
        // Check for media_search tool patterns
        if (keys.any { it in listOf("operation") } && jsonObj.optString("operation", "").lowercase().let { 
            it.contains("photo") || it.contains("video") || it.contains("media") || it.contains("image")
        }) {
            return "media_search"
        }
        
        // Check for document_search tool patterns
        if (keys.any { it in listOf("operation") } && jsonObj.optString("operation", "").lowercase().let {
            it.contains("document") || it.contains("download") || it.contains("pdf") || it.contains("file")
        }) {
            return "document_search"
        }
        
        // Check for write_file tool patterns
        if (keys.any { it in listOf("content", "data", "text") } && keys.contains("path")) {
            return "write_file"
        }
        
        // Check for memory tool patterns
        if (keys.any { it in listOf("action", "query", "title") } && 
            (keys.contains("type") || keys.contains("content") || keys.contains("tags"))) {
            return "memory"
        }
        
        // Check for plan/tracker tool patterns
        if (keys.any { it in listOf("action", "plan_id", "task_id", "step_id") }) {
            if (keys.any { it.contains("plan") }) return "plan"
            if (keys.any { it.contains("task") || it.contains("tracker") }) return "tracker"
        }
        
        // Default: if just "path" key exists, assume list_dir
        if (keys.size == 1 && keys.first() == "path") {
            return "list_dir"
        }
        
        Log.d("ChatViewModel", "Could not infer tool from params: $keys")
        return ""
    }
    
    // Slash Commands Handling
    private fun handleSlashCommand(commandText: String) {
        val sId = _currentSessionId.value ?: return
        viewModelScope.launch {
            // Save command as a regular user input in logs
            chatDao.insertMessage(ChatMessage(sessionId = sId, role = "user", content = commandText))
            
            val parts = commandText.split(" ")
            val cmd = parts[0].lowercase()

            val responseText = when (cmd) {
                "/help" -> """
                    === logy Workspace Help System ===
                    Here are the core terminal control options:
                    - `/help` : Displays this system manual.
                    - `/explain` : Requests deep code or architecture analysis.
                    - `/code` : Instantly generates optimized code snippets.
                    - `/fix` : Diagnoses bugs and presents visual patches.
                    - `/summarize` : Provides highly condensed workspace summaries.
                    - `/deploy` : Triggers simulated cloud compilation pipeline.
                """.trimIndent()
                "/explain" -> "Let's perform a deep structural analysis on the core files. Checking `/src` code patterns..."
                "/code" -> "Generating optimal code block for requested architectural module..."
                "/fix" -> "Scanning local file buffers. No immediate syntax errors found. Looking for structural edge cases..."
                "/summarize" -> "Summarizing project context. 3 active directories in database, 12 sessions registered, daemon active."
                "/deploy" -> """
                    === Deployment Pipeline Initiated ===
                    - Compiling local assets... [OK]
                    - Linting source code... [OK]
                    - Uploading archive to staging server...
                    Server output: 🟢 Deploy successfully resolved! Link: shared-app.internal
                """.trimIndent()
                else -> "Unknown slash command `$cmd`. Type `/help` to see list of functions."
            }

            if (cmd == "/help" || cmd == "/deploy") {
                chatDao.insertMessage(
                    ChatMessage(
                        sessionId = sId,
                        role = "model",
                        content = responseText,
                        status = "Complete"
                    )
                )
            } else {
                // For /explain, /code, /fix, /summarize, execute AI loop with simulated in-place streaming
                executeLocalAILoop(commandText, initialText = responseText)
            }
        }
    }

    // Connection Manager Actions for WebSocket
    fun toggleDaemonConnection() {
        if (_daemonConnection.value == ConnectionState.CONNECTED) {
            daemonService.disconnect()
        } else {
            daemonService.connect(_daemonHost.value, _daemonApiKey.value)
        }
    }

    private fun handleDaemonEvent(event: DaemonEvent) {
        val sId = _currentSessionId.value ?: return
        viewModelScope.launch {
            when (event) {
                is DaemonEvent.TextChunk -> {
                    _isThinking.value = false
                    _isTyping.value = true
                    // Real-time daemon text chunk. To support clean streaming display we append to the last message!
                    val currentList = withContext(Dispatchers.IO) {
                        chatDao.getMessagesForSessionList(sId)
                    }
                    val lastMsg = currentList.lastOrNull()
                    if (lastMsg != null && lastMsg.role == "model") {
                        val updated = lastMsg.copy(content = lastMsg.content + event.chunk)
                        chatDao.updateMessage(updated)
                    } else {
                        val newMsg = ChatMessage(sessionId = sId, role = "model", content = event.chunk)
                        chatDao.insertMessage(newMsg)
                    }
                }
                is DaemonEvent.ToolCall -> {
                    _isThinking.value = false
                    _isTyping.value = false
                    val toolMsg = ChatMessage(
                        sessionId = sId,
                        role = "tool",
                        content = "Requested tool call: ${event.name}",
                        toolName = event.name,
                        toolArgs = event.argsJson,
                        toolStatus = "pending",
                        riskLevel = event.risk
                    )
                    val msgId = chatDao.insertMessage(toolMsg)
                    _activeToolApproval.value = toolMsg.copy(id = msgId.toInt())
                }
                is DaemonEvent.ToolResult -> {
                    val resultMsg = ChatMessage(
                        sessionId = sId,
                        role = "system",
                        content = "Tool ${event.name} returned output:\n${event.result}"
                    )
                    chatDao.insertMessage(resultMsg)
                }
                is DaemonEvent.StatusUpdate -> {
                    _statusText.value = event.status
                }
                is DaemonEvent.Error -> {
                    _isThinking.value = false
                    _isTyping.value = false
                    chatDao.insertMessage(ChatMessage(sessionId = sId, role = "system", content = "Daemon System Error: ${event.message}"))
                }
                DaemonEvent.SessionEnded -> {
                    _isThinking.value = false
                    _isTyping.value = false
                    chatDao.insertMessage(ChatMessage(sessionId = sId, role = "system", content = "WebSocket terminal session closed gracefully."))
                }
            }
        }
    }

    fun stopGeneration() {
        _isThinking.value = false
        _isTyping.value = false
        _statusText.value = "Operational"
    }

    override fun onCleared() {
        super.onCleared()
        daemonService.disconnect()
    }
}
