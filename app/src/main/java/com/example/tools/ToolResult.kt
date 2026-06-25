package com.example.tools

data class ToolResult(
    val success: Boolean,
    val content: String? = null,
    val error: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    companion object {
        fun success(content: String, metadata: Map<String, Any> = emptyMap()) = 
            ToolResult(success = true, content = content, metadata = metadata)
        
        fun error(error: String) = 
            ToolResult(success = false, error = error)
    }
}
