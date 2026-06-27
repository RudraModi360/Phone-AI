package com.example.memory

import com.example.data.MemoryEntry

object ExtractionPromptBuilder {

    fun build(
        userMessage: String,
        aiResponse: String,
        existingManifest: List<MemoryEntry>
    ): String {
        val manifestStr = if (existingManifest.isEmpty()) {
            "No existing memories."
        } else {
            buildString {
                for (mem in existingManifest) {
                    appendLine("- [${mem.id}] ${mem.name} (${mem.memoryType}): ${mem.description}")
                }
            }
        }

        return """You are a memory extraction subagent. Analyze this conversation and extract important memories.

CONVERSATION:
User: $userMessage
Assistant: $aiResponse

EXISTING MEMORIES (do not duplicate):
$manifestStr

MEMORY TYPES:
- user: User's role, goals, responsibilities, knowledge
- feedback: Corrections and confirmations from the user
- project: Ongoing work not derivable from code
- reference: Pointers to external systems

INSTRUCTIONS:
1. Read the conversation carefully
2. Identify information worth remembering across sessions
3. Check existing memories to avoid duplicates
4. For each new memory, output a JSON object with: name, description, type, content
5. If updating an existing memory, include the "id" field
6. If nothing new to remember, respond with: []

OUTPUT FORMAT — JSON array:
[{"name":"descriptive_filename","description":"one-line summary","type":"user|feedback|project|reference","content":"markdown body"}]

To update existing:
[{"id":42,"name":"user_role","description":"updated summary","type":"user","content":"updated content"}]

RULES:
- Maximum 5 memories per extraction
- Names should be descriptive filenames (e.g., "user_role", "feedback_no_markdown")
- Content can be multi-line markdown
- Skip trivial conversational content
- Only extract genuinely important information

RESPOND WITH ONLY THE JSON ARRAY."""
    }
}
