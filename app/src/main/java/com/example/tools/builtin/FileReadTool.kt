package com.example.tools.builtin

import com.example.service.Parameters
import com.example.service.PropertySchema
import com.example.tools.AndroidPathResolver
import com.example.tools.BaseTool
import com.example.tools.RiskLevel
import com.example.tools.ToolResult
import java.io.File

/**
 * Android-native file reading tool.
 * Uses AndroidPathResolver for path handling - no Termux dependencies.
 */
class FileReadTool : BaseTool {
    override val name = "read_file"
    override val description = "Read contents of a file from Android storage (Downloads, Documents, etc.)"
    override val riskLevel = RiskLevel.SAFE
    
    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "path" to PropertySchema(
                type = "STRING",
                description = "Path to file (relative paths resolve to /sdcard/, use ~ for home)"
            ),
            "max_lines" to PropertySchema(
                type = "INTEGER",
                description = "Maximum lines to read (default: all)"
            ),
            "encoding" to PropertySchema(
                type = "STRING",
                description = "File encoding (default: UTF-8)"
            )
        ),
        required = listOf("path")
    )
    
    companion object {
        // Maximum file size to read (5MB)
        private const val MAX_FILE_SIZE = 5 * 1024 * 1024L
    }
    
    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val pathArg = args["path"] as? String
            ?: return ToolResult.error("Missing 'path' argument")
        
        val maxLines = (args["max_lines"] as? Number)?.toInt()
        val encoding = args["encoding"] as? String ?: "UTF-8"
        
        return try {
            // Security check first
            if (!AndroidPathResolver.isReadAllowed(pathArg)) {
                return ToolResult.error("Access denied: cannot read from protected system path")
            }
            
            val file = AndroidPathResolver.resolvePath(pathArg)
            val canonicalPath = file.canonicalPath
            
            if (!file.exists()) {
                return ToolResult.error("File not found: $canonicalPath")
            }
            
            if (!file.isFile) {
                return ToolResult.error("Not a file: $canonicalPath (is directory: ${file.isDirectory})")
            }
            
            if (!file.canRead()) {
                return ToolResult.error("Permission denied: cannot read file $canonicalPath")
            }
            
            // Size check
            if (file.length() > MAX_FILE_SIZE) {
                return ToolResult.error("File too large: ${file.length()} bytes (max: $MAX_FILE_SIZE)")
            }
            
            val content = if (maxLines != null) {
                file.useLines(charset(encoding)) { lines ->
                    lines.take(maxLines).joinToString("\n")
                }
            } else {
                file.readText(charset(encoding))
            }
            
            val location = AndroidPathResolver.describeLocation(pathArg)
            
            ToolResult.success(
                content,
                mapOf(
                    "path" to canonicalPath,
                    "location" to location,
                    "size_bytes" to file.length(),
                    "lines" to content.lines().size
                )
            )
        } catch (e: SecurityException) {
            ToolResult.error("Permission denied: ${e.message}")
        } catch (e: Exception) {
            ToolResult.error("Failed to read file: ${e.message}")
        }
    }
}
