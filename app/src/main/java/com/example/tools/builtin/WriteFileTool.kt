package com.example.tools.builtin

import com.example.service.Parameters
import com.example.service.PropertySchema
import com.example.tools.AndroidPathResolver
import com.example.tools.BaseTool
import com.example.tools.RiskLevel
import com.example.tools.ToolResult
import java.io.File

/**
 * Android-native file writing tool.
 * Uses AndroidPathResolver for path handling - no Termux dependencies.
 * Writes to shared storage (/sdcard/) by default.
 */
class WriteFileTool : BaseTool {
    override val name = "write_file"
    override val description = "Write content to a file in Android storage (Downloads, Documents, etc.)"
    override val riskLevel = RiskLevel.DANGEROUS
    
    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "path" to PropertySchema(
                type = "STRING",
                description = "Path to file (relative paths resolve to /sdcard/, use ~/Download for Downloads)"
            ),
            "content" to PropertySchema(
                type = "STRING",
                description = "Content to write to the file"
            ),
            "append" to PropertySchema(
                type = "BOOLEAN",
                description = "Append to file instead of overwriting (default: false)"
            ),
            "create_dirs" to PropertySchema(
                type = "BOOLEAN",
                description = "Create parent directories if they don't exist (default: true)"
            )
        ),
        required = listOf("path", "content")
    )
    
    companion object {
        private const val MAX_FILE_SIZE = 10 * 1024 * 1024L // 10MB max
    }
    
    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val pathArg = args["path"] as? String
            ?: return ToolResult.error("Missing 'path' argument")
        
        val content = args["content"] as? String
            ?: return ToolResult.error("Missing 'content' argument")
        
        val append = args["append"] as? Boolean ?: false
        val createDirs = args["create_dirs"] as? Boolean ?: true
        
        // Size check
        if (content.toByteArray(Charsets.UTF_8).size > MAX_FILE_SIZE) {
            return ToolResult.error("Content too large: ${content.toByteArray(Charsets.UTF_8).size} bytes (max: $MAX_FILE_SIZE)")
        }
        
        return try {
            // Security check
            if (!AndroidPathResolver.isWriteAllowed(pathArg)) {
                return ToolResult.error("Access denied: cannot write to protected path or file type")
            }
            
            val file = AndroidPathResolver.resolvePath(pathArg)
            val canonicalPath = file.canonicalPath
            
            // Create parent directories if needed
            if (createDirs) {
                file.parentFile?.let { parent ->
                    if (!parent.exists()) {
                        if (!parent.mkdirs()) {
                            return ToolResult.error("Failed to create parent directories")
                        }
                    }
                }
            }
            
            // Check if parent exists
            if (file.parentFile?.exists() != true) {
                return ToolResult.error("Parent directory does not exist: ${file.parentFile?.absolutePath}")
            }
            
            // Write file
            if (append) {
                file.appendText(content, Charsets.UTF_8)
            } else {
                val tempFile = File(file.parent, ".${file.name}.tmp")
                try {
                    tempFile.writeText(content, Charsets.UTF_8)
                    tempFile.renameTo(file)
                } catch (e: Exception) {
                    tempFile.delete()
                    throw e
                }
            }
            
            val location = AndroidPathResolver.describeLocation(pathArg)
            
            ToolResult.success(
                "Successfully ${if (append) "appended to" else "wrote"} file: $canonicalPath",
                mapOf(
                    "path" to canonicalPath,
                    "location" to location,
                    "bytes_written" to content.toByteArray(Charsets.UTF_8).size,
                    "append_mode" to append
                )
            )
        } catch (e: SecurityException) {
            ToolResult.error("Permission denied: ${e.message}")
        } catch (e: Exception) {
            ToolResult.error("Failed to write file: ${e.message}")
        }
    }
}
