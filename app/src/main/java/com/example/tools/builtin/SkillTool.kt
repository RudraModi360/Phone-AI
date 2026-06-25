package com.example.tools.builtin

import com.example.service.Parameters
import com.example.service.PropertySchema
import com.example.skills.SkillRegistry
import com.example.tools.BaseTool
import com.example.tools.RiskLevel
import com.example.tools.ToolResult

/**
 * Tool for managing skills during agent execution.
 * Allows the agent to discover, activate, and query skills.
 */
class SkillTool(private val skillRegistry: SkillRegistry) : BaseTool {
    override val name = "skill"
    override val description = "List, activate, and manage domain-specific skills"
    override val riskLevel = RiskLevel.SAFE
    
    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "action" to PropertySchema(
                type = "STRING",
                description = "Action: list, activate, deactivate, info, search"
            ),
            "skill_name" to PropertySchema(
                type = "STRING",
                description = "Name of the skill (for activate/deactivate/info)"
            ),
            "query" to PropertySchema(
                type = "STRING",
                description = "Search query (for search action)"
            ),
            "show_active" to PropertySchema(
                type = "BOOLEAN",
                description = "Only show active skills (for list action)"
            )
        ),
        required = listOf("action")
    )
    
    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val action = args["action"] as? String
            ?: return ToolResult.error("Missing 'action' argument")
        
        return try {
            when (action.lowercase()) {
                "list" -> listSkills(args)
                "activate" -> activateSkill(args)
                "deactivate" -> deactivateSkill(args)
                "info" -> skillInfo(args)
                "search" -> searchSkills(args)
                else -> ToolResult.error("Unknown action: $action. Valid: list, activate, deactivate, info, search")
            }
        } catch (e: Exception) {
            ToolResult.error("Skill operation failed: ${e.message}")
        }
    }
    
    private fun listSkills(args: Map<String, Any?>): ToolResult {
        val showActiveOnly = args["show_active"] as? Boolean ?: false
        
        val skills = if (showActiveOnly) {
            skillRegistry.activeSkills()
        } else {
            skillRegistry.all()
        }
        
        if (skills.isEmpty()) {
            return ToolResult.success(
                if (showActiveOnly) "No active skills." else "No skills loaded.",
                mapOf("count" to 0)
            )
        }
        
        val output = buildString {
            appendLine("📚 ${if (showActiveOnly) "Active " else ""}Skills (${skills.size}):")
            appendLine()
            
            skills.forEach { skill ->
                val activeIcon = if (skillRegistry.isActive(skill.metadata.name)) "✅" else "○"
                appendLine("$activeIcon ${skill.metadata.name} (v${skill.metadata.version})")
                appendLine("   ${skill.metadata.description}")
                if (skill.metadata.tags.isNotEmpty()) {
                    appendLine("   Tags: ${skill.metadata.tags.joinToString(", ")}")
                }
            }
        }
        
        return ToolResult.success(output, mapOf("count" to skills.size))
    }
    
    private fun activateSkill(args: Map<String, Any?>): ToolResult {
        val skillName = args["skill_name"] as? String
            ?: return ToolResult.error("Missing 'skill_name' for activate action")
        
        val success = skillRegistry.activate(skillName)
        
        return if (success) {
            val skill = skillRegistry.get(skillName)
            ToolResult.success(
                "✅ Skill activated: $skillName\n${skill?.metadata?.description ?: ""}",
                mapOf("skill" to skillName)
            )
        } else {
            ToolResult.error("Skill not found: $skillName")
        }
    }
    
    private fun deactivateSkill(args: Map<String, Any?>): ToolResult {
        val skillName = args["skill_name"] as? String
            ?: return ToolResult.error("Missing 'skill_name' for deactivate action")
        
        skillRegistry.deactivate(skillName)
        return ToolResult.success("🔴 Skill deactivated: $skillName", mapOf("skill" to skillName))
    }
    
    private fun skillInfo(args: Map<String, Any?>): ToolResult {
        val skillName = args["skill_name"] as? String
            ?: return ToolResult.error("Missing 'skill_name' for info action")
        
        val skill = skillRegistry.get(skillName)
            ?: return ToolResult.error("Skill not found: $skillName")
        
        val output = buildString {
            appendLine("📖 Skill: ${skill.metadata.name}")
            appendLine("─".repeat(40))
            appendLine("Version: ${skill.metadata.version}")
            appendLine("Author: ${skill.metadata.author.ifEmpty { "Unknown" }}")
            appendLine("Description: ${skill.metadata.description}")
            appendLine("Status: ${if (skillRegistry.isActive(skillName)) "Active ✅" else "Inactive"}")
            
            if (skill.metadata.tags.isNotEmpty()) {
                appendLine("Tags: ${skill.metadata.tags.joinToString(", ")}")
            }
            
            if (skill.metadata.requires.isNotEmpty()) {
                appendLine("Requires: ${skill.metadata.requires.joinToString(", ")}")
            }
            
            appendLine()
            appendLine("Instructions Preview:")
            appendLine(skill.instructions.take(500))
            if (skill.instructions.length > 500) {
                appendLine("... (truncated)")
            }
        }
        
        return ToolResult.success(output, mapOf("skill" to skillName))
    }
    
    private fun searchSkills(args: Map<String, Any?>): ToolResult {
        val query = args["query"] as? String
            ?: return ToolResult.error("Missing 'query' for search action")
        
        val results = skillRegistry.search(query)
        
        if (results.isEmpty()) {
            return ToolResult.success("No skills found matching: $query", mapOf("count" to 0))
        }
        
        val output = buildString {
            appendLine("🔍 Search results for: \"$query\" (${results.size})")
            appendLine()
            
            results.forEach { skill ->
                val activeIcon = if (skillRegistry.isActive(skill.metadata.name)) "✅" else "○"
                appendLine("$activeIcon ${skill.metadata.name}")
                appendLine("   ${skill.metadata.description}")
            }
        }
        
        return ToolResult.success(output, mapOf("count" to results.size))
    }
}
