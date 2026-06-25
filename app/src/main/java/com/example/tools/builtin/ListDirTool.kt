package com.example.tools.builtin

import com.example.service.Parameters
import com.example.service.PropertySchema
import com.example.tools.AndroidPathResolver
import com.example.tools.BaseTool
import com.example.tools.RiskLevel
import com.example.tools.ToolResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Android-native directory listing tool.
 * Works without shell access, directly using Java File API.
 * Uses AndroidPathResolver for path handling - no Termux dependencies.
 */
class ListDirTool : BaseTool {
    override val name = "list_dir"
    override val description = "List contents of a directory in Android storage (Downloads, Documents, DCIM, etc.)"
    override val riskLevel = RiskLevel.SAFE
    
    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "path" to PropertySchema(
                type = "STRING",
                description = "Directory path (default: /sdcard/, use ~ for home, ~/Download for Downloads)"
            ),
            "show_hidden" to PropertySchema(
                type = "BOOLEAN",
                description = "Show hidden files starting with . (default: false)"
            ),
            "long_format" to PropertySchema(
                type = "BOOLEAN",
                description = "Show detailed info like size and date (default: false)"
            )
        ),
        required = emptyList()
    )
    
    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val pathArg = args["path"] as? String
        val showHidden = args["show_hidden"] as? Boolean ?: false
        val longFormat = args["long_format"] as? Boolean ?: false
        
        return try {
            val dir = AndroidPathResolver.resolvePath(pathArg)
            
            if (!dir.exists()) {
                return ToolResult.error("Directory not found: ${dir.absolutePath}")
            }
            
            if (!dir.isDirectory) {
                return ToolResult.error("Not a directory: ${dir.absolutePath}")
            }
            
            if (!dir.canRead()) {
                return ToolResult.error("Permission denied: cannot read ${dir.absolutePath}")
            }
            
            val files = dir.listFiles() ?: emptyArray()
            val filtered = if (showHidden) {
                files.toList()
            } else {
                files.filter { !it.name.startsWith(".") }
            }
            
            val sorted = filtered.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            
            val output = if (longFormat) {
                val dateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.US)
                sorted.map { file ->
                    val type = if (file.isDirectory) "d" else "-"
                    val readable = if (file.canRead()) "r" else "-"
                    val writable = if (file.canWrite()) "w" else "-"
                    val executable = if (file.canExecute()) "x" else "-"
                    val perms = "$type$readable$writable$executable"
                    val size = if (file.isFile) formatSize(file.length()) else "   <DIR>"
                    val modified = dateFormat.format(Date(file.lastModified()))
                    val name = if (file.isDirectory) "${file.name}/" else file.name
                    "$perms $size $modified  $name"
                }.joinToString("\n")
            } else {
                sorted.map { file ->
                    if (file.isDirectory) "${file.name}/" else file.name
                }.joinToString("\n")
            }
            
            val location = AndroidPathResolver.describeLocation(pathArg ?: "~")
            
            ToolResult.success(
                output.ifEmpty { "(empty directory)" },
                mapOf(
                    "path" to dir.absolutePath,
                    "location" to location,
                    "count" to sorted.size,
                    "dirs" to sorted.count { it.isDirectory },
                    "files" to sorted.count { it.isFile }
                )
            )
        } catch (e: SecurityException) {
            ToolResult.error("Permission denied: ${e.message}")
        } catch (e: Exception) {
            ToolResult.error("Failed to list directory: ${e.message}")
        }
    }
    
    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format("%6.1fM", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%6.1fK", bytes / 1024.0)
            else -> String.format("%7d", bytes)
        }
    }
}
