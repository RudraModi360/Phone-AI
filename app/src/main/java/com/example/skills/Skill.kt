package com.example.skills

data class SkillMetadata(
    val name: String,
    val description: String,
    val version: String = "1.0.0",
    val author: String = "",
    val tags: List<String> = emptyList(),
    val requires: List<String> = emptyList()
)

data class Skill(
    val metadata: SkillMetadata,
    val instructions: String,
    val systemPromptAddon: String? = null
)
