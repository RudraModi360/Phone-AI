package com.example.skills

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class SkillLoader(private val context: Context) {
    
    /**
     * Load a skill from assets.
     */
    fun loadFromAssets(skillName: String): Skill? {
        val path = "skills/$skillName/SKILL.md"
        
        return try {
            val inputStream = context.assets.open(path)
            val content = BufferedReader(InputStreamReader(inputStream)).use { 
                it.readText() 
            }
            parseSkillMd(content)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Discover all skills in assets.
     */
    fun discoverSkills(): List<Skill> {
        return try {
            val skillDirs = context.assets.list("skills") ?: return emptyList()
            skillDirs.mapNotNull { loadFromAssets(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun parseSkillMd(content: String): Skill? {
        return try {
            // Parse YAML frontmatter
            val frontmatterMatch = Regex("^---\\s*\\n(.*?)\\n---\\s*\\n(.*)", RegexOption.DOT_MATCHES_ALL)
                .find(content) ?: return null
            
            val frontmatter = frontmatterMatch.groupValues[1]
            val instructions = frontmatterMatch.groupValues[2].trim()
            
            // Simple YAML parsing
            val metadata = mutableMapOf<String, Any>()
            frontmatter.lines().forEach { line ->
                val colonIndex = line.indexOf(':')
                if (colonIndex > 0) {
                    val key = line.substring(0, colonIndex).trim()
                    val value = line.substring(colonIndex + 1).trim().trim('"', '\'')
                    metadata[key] = value
                }
            }
            
            Skill(
                metadata = SkillMetadata(
                    name = metadata["name"] as? String ?: "Unknown",
                    description = metadata["description"] as? String ?: "",
                    version = metadata["version"] as? String ?: "1.0.0"
                ),
                instructions = instructions
            )
        } catch (e: Exception) {
            android.util.Log.e("SkillLoader", "Failed to parse skill file", e)
            null
        }
    }
}
