package com.example.tools

import com.example.memory.MemoryService
import com.example.planner.PlanService
import com.example.skills.SkillRegistry
import com.example.tracker.TrackerService
import com.example.tools.builtin.*

object ToolRegistry {
    private val tools = java.util.concurrent.ConcurrentHashMap<String, BaseTool>()
    @Volatile
    private var initialized = false
    @Volatile
    private var servicesInitialized = false
    private var memoryService: com.example.memory.MemoryService? = null
    private var planService: com.example.planner.PlanService? = null
    private var trackerService: com.example.tracker.TrackerService? = null
    private var skillRegistry: com.example.skills.SkillRegistry? = null
    
    fun register(tool: BaseTool) {
        tools[tool.name] = tool
    }
    
    fun unregister(name: String) {
        tools.remove(name)
    }
    
    fun get(name: String): BaseTool? = tools[name]
    
    fun all(): List<BaseTool> = tools.values.toList()
    
    fun allByRisk(risk: RiskLevel): List<BaseTool> = 
        tools.values.filter { it.riskLevel == risk }
    
    fun safeTools(): List<BaseTool> = 
        allByRisk(RiskLevel.SAFE)
    
    fun dangerousTools(): List<BaseTool> = 
        allByRisk(RiskLevel.DANGEROUS)
    
    @Synchronized
    fun clear() {
        tools.clear()
        initialized = false
        servicesInitialized = false
        memoryService = null
        planService = null
        trackerService = null
        skillRegistry = null
    }
    
    /**
     * Get tool descriptions for system prompt injection.
     */
    fun getToolDescriptions(): String {
        return tools.values.joinToString("\n\n") { tool ->
            """
            Tool: ${tool.name}
            Description: ${tool.description}
            Risk Level: ${tool.riskLevel}
            Parameters: ${tool.parameters.properties.entries.joinToString(", ") { (k, v) -> 
                "$k (${v.type}): ${v.description}" 
            }}
            Required: ${tool.parameters.required.joinToString(", ")}
            """.trimIndent()
        }
    }
    
    /**
     * Check if a tool exists and is safe to auto-execute.
     */
    fun isSafeTool(name: String): Boolean {
        val tool = tools[name] ?: return false
        return tool.riskLevel == RiskLevel.SAFE
    }
    
    /**
     * Initialize with default built-in tools (no service dependencies).
     * Call this first for basic functionality.
     */
    @Synchronized
    fun initializeDefaults() {
        if (initialized) return
        
        // Safe tools (can be auto-executed without approval)
        register(DateTimeTool())
        register(FileReadTool())
        register(ListDirTool())
        register(ContactsTool())
        register(MediaSearchTool())
        register(DocumentSearchTool())
        register(SmartFindTool())
        register(WebSearchTool())
        register(ExaSearchTool())
        
        // New native Android tools
        register(CalendarTool())      // Calendar events CRUD
        register(CallLogTool())       // Call history
        register(SmsTool())           // SMS messages (read-only)
        register(LocationTool())      // GPS location
        register(ClipboardTool())     // Clipboard read/write
        register(DeviceInfoTool())    // Device info, battery, network, storage
        register(ClockTool())         // Alarms & Timers clock management
        
        // Dangerous tools (require user approval)
        register(ShellExecTool())
        register(WriteFileTool())
        
        initialized = true
    }
    
    /**
     * Initialize service-dependent tools.
     * Call after services are available.
     */
    @Synchronized
    fun initializeWithServices(
        memoryService: com.example.memory.MemoryService,
        planService: com.example.planner.PlanService,
        trackerService: com.example.tracker.TrackerService,
        skillRegistry: com.example.skills.SkillRegistry
    ) {
        // Ensure defaults are initialized first
        if (!initialized) {
            initializeDefaults()
        }
        if (servicesInitialized) return
        this.memoryService = memoryService
        this.planService = planService
        this.trackerService = trackerService
        this.skillRegistry = skillRegistry
        
        // Register service-dependent tools
        register(MemoryTool(memoryService))
        register(PlanTool(planService))
        register(TrackerTool(trackerService))
        register(SkillTool(skillRegistry))
        
        servicesInitialized = true
    }
    
    /**
     * Get a summary of available tools for the UI.
     */
    fun getToolsSummary(): Map<String, Any> {
        return mapOf(
            "total" to tools.size,
            "safe" to safeTools().size,
            "dangerous" to dangerousTools().size,
            "names" to tools.keys.toList()
        )
    }
}
