package com.example.tools

import com.example.service.Parameters

/**
 * Base interface for all agent tools.
 */
interface BaseTool {
    /** Unique tool name (snake_case) */
    val name: String
    
    /** Human-readable description for LLM */
    val description: String
    
    /** Schema for arguments */
    val parameters: Parameters
    
    /** Risk level for approval flow */
    val riskLevel: RiskLevel
    
    /**
     * Execute the tool with given arguments.
     */
    suspend fun execute(args: Map<String, Any?>): ToolResult
}
