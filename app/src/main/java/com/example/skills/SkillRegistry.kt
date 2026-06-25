package com.example.skills

import android.content.Context
import android.util.Log

/**
 * Registry for managing skills in the agent runtime.
 * Skills provide domain-specific knowledge and instructions.
 */
class SkillRegistry(context: Context) {
    private val loader = SkillLoader(context)
    private val skills = mutableMapOf<String, Skill>()
    private val activeSkills = mutableSetOf<String>()
    
    /**
     * Load all skills from assets.
     */
    fun loadAll(): Int {
        val discovered = loader.discoverSkills()
        discovered.forEach { skill ->
            skills[skill.metadata.name] = skill
        }
        Log.d("SkillRegistry", "Loaded ${discovered.size} skills")
        return discovered.size
    }
    
    /**
     * Register a skill manually.
     */
    fun register(skill: Skill) {
        skills[skill.metadata.name] = skill
    }
    
    /**
     * Get a skill by name.
     */
    fun get(name: String): Skill? = skills[name]
    
    /**
     * Get all registered skills.
     */
    fun all(): List<Skill> = skills.values.toList()
    
    /**
     * Activate a skill for use in prompts.
     */
    fun activate(name: String): Boolean {
        if (skills.containsKey(name)) {
            activeSkills.add(name)
            Log.d("SkillRegistry", "Activated skill: $name")
            return true
        }
        return false
    }
    
    /**
     * Deactivate a skill.
     */
    fun deactivate(name: String) {
        activeSkills.remove(name)
        Log.d("SkillRegistry", "Deactivated skill: $name")
    }
    
    /**
     * Check if a skill is active.
     */
    fun isActive(name: String): Boolean = name in activeSkills
    
    /**
     * Get all active skills.
     */
    fun activeSkills(): List<Skill> {
        return activeSkills.mapNotNull { skills[it] }
    }
    
    /**
     * Get combined instructions from all active skills.
     */
    fun getActiveInstructions(): String {
        val active = activeSkills()
        if (active.isEmpty()) return ""
        
        return buildString {
            appendLine("\n## Active Skills")
            active.forEach { skill ->
                appendLine("\n### ${skill.metadata.name}")
                appendLine(skill.instructions)
            }
        }
    }
    
    /**
     * Get combined system prompt addons from active skills.
     */
    fun getSystemPromptAddons(): String {
        val addons = activeSkills()
            .mapNotNull { it.systemPromptAddon }
            .filter { it.isNotBlank() }
        
        if (addons.isEmpty()) return ""
        
        return buildString {
            appendLine("\n## Skill-Specific Instructions")
            addons.forEach { addon ->
                appendLine(addon)
            }
        }
    }
    
    /**
     * Find skills by tag.
     */
    fun findByTag(tag: String): List<Skill> {
        return skills.values.filter { tag in it.metadata.tags }
    }
    
    /**
     * Search skills by keyword in name or description.
     */
    fun search(query: String): List<Skill> {
        val lowerQuery = query.lowercase()
        return skills.values.filter { skill ->
            skill.metadata.name.lowercase().contains(lowerQuery) ||
            skill.metadata.description.lowercase().contains(lowerQuery) ||
            skill.metadata.tags.any { it.lowercase().contains(lowerQuery) }
        }
    }
    
    /**
     * Get skill summary for UI display.
     */
    fun summary(): Map<String, Any> {
        return mapOf(
            "total" to skills.size,
            "active" to activeSkills.size,
            "skills" to skills.map { (name, skill) ->
                mapOf(
                    "name" to name,
                    "description" to skill.metadata.description,
                    "active" to (name in activeSkills)
                )
            }
        )
    }
    
    /**
     * Clear all skills.
     */
    fun clear() {
        skills.clear()
        activeSkills.clear()
    }
}
