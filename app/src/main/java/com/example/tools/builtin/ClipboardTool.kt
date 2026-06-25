package com.example.tools.builtin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.example.service.Parameters
import com.example.service.PropertySchema
import com.example.tools.AndroidPathResolver
import com.example.tools.BaseTool
import com.example.tools.RiskLevel
import com.example.tools.ToolResult

/**
 * Android Clipboard Tool - Read and write to system clipboard.
 * Uses ClipboardManager APIs directly.
 * NO permissions required for clipboard access.
 */
class ClipboardTool : BaseTool {
    override val name = "clipboard"
    override val description = "Read from or write to the device clipboard. No permissions required."
    override val riskLevel = RiskLevel.SAFE

    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "operation" to PropertySchema(
                type = "STRING",
                description = "Operation: 'read', 'write', 'clear', 'has_content'"
            ),
            "text" to PropertySchema(
                type = "STRING",
                description = "Text to copy to clipboard (for 'write' operation)"
            ),
            "label" to PropertySchema(
                type = "STRING",
                description = "Label for clipboard entry (for 'write' operation, default: 'Copied Text')"
            )
        ),
        required = emptyList()
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val operation = args["operation"] as? String ?: "read"
        val text = args["text"] as? String
        val label = args["label"] as? String ?: "Copied Text"

        val context = AndroidPathResolver.getContext()
            ?: return ToolResult.error("Application context not available")

        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return ToolResult.error("Clipboard service not available")

        return try {
            when (operation) {
                "read" -> readClipboard(clipboardManager)
                "write" -> {
                    if (text.isNullOrEmpty()) {
                        return ToolResult.error("Missing 'text' parameter for write operation")
                    }
                    writeClipboard(clipboardManager, text, label)
                }
                "clear" -> clearClipboard(clipboardManager)
                "has_content" -> hasContent(clipboardManager)
                else -> ToolResult.error("Unknown operation: '$operation'. Use: read, write, clear, has_content")
            }
        } catch (e: Exception) {
            ToolResult.error("Clipboard operation failed: ${e.message}")
        }
    }

    /**
     * Read current clipboard content.
     */
    private fun readClipboard(clipboardManager: ClipboardManager): ToolResult {
        if (!clipboardManager.hasPrimaryClip()) {
            return ToolResult.success(
                "Clipboard is empty.",
                mapOf("has_content" to false, "content" to "")
            )
        }

        val clip = clipboardManager.primaryClip
        if (clip == null || clip.itemCount == 0) {
            return ToolResult.success(
                "Clipboard is empty.",
                mapOf("has_content" to false, "content" to "")
            )
        }

        val item = clip.getItemAt(0)
        val description = clip.description
        
        // Try to get text content
        val text = item.text?.toString()
        val uri = item.uri?.toString()
        val htmlText = item.htmlText

        val result = mutableMapOf<String, Any>(
            "has_content" to true,
            "item_count" to clip.itemCount
        )

        val formatted = buildString {
            appendLine("Clipboard Content")
            appendLine("=================")
            
            if (description != null) {
                val label = description.label?.toString() ?: "Unknown"
                appendLine("Label: $label")
                
                // List MIME types
                val mimeTypes = (0 until description.mimeTypeCount).map { description.getMimeType(it) }
                if (mimeTypes.isNotEmpty()) {
                    appendLine("Types: ${mimeTypes.joinToString(", ")}")
                }
                result["label"] = label
                result["mime_types"] = mimeTypes
            }
            
            appendLine()
            
            when {
                text != null -> {
                    appendLine("Text Content:")
                    appendLine(text)
                    result["content"] = text
                    result["type"] = "text"
                }
                uri != null -> {
                    appendLine("URI Content:")
                    appendLine(uri)
                    result["content"] = uri
                    result["type"] = "uri"
                }
                htmlText != null -> {
                    appendLine("HTML Content:")
                    appendLine(htmlText)
                    result["content"] = htmlText
                    result["type"] = "html"
                }
                else -> {
                    appendLine("(Unknown content type)")
                    result["content"] = ""
                    result["type"] = "unknown"
                }
            }
        }

        return ToolResult.success(formatted, result)
    }

    /**
     * Write text to clipboard.
     */
    private fun writeClipboard(clipboardManager: ClipboardManager, text: String, label: String): ToolResult {
        val clip = ClipData.newPlainText(label, text)
        clipboardManager.setPrimaryClip(clip)

        val preview = if (text.length > 100) text.take(100) + "..." else text

        return ToolResult.success(
            "Text copied to clipboard!\nLabel: $label\nContent: $preview",
            mapOf(
                "success" to true,
                "label" to label,
                "content" to text,
                "length" to text.length
            )
        )
    }

    /**
     * Clear clipboard content.
     */
    private fun clearClipboard(clipboardManager: ClipboardManager): ToolResult {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9+ has clearPrimaryClip
            clipboardManager.clearPrimaryClip()
            ToolResult.success(
                "Clipboard cleared.",
                mapOf("success" to true)
            )
        } else {
            // On older versions, set empty clip
            val emptyClip = ClipData.newPlainText("", "")
            clipboardManager.setPrimaryClip(emptyClip)
            ToolResult.success(
                "Clipboard cleared.",
                mapOf("success" to true)
            )
        }
    }

    /**
     * Check if clipboard has content.
     */
    private fun hasContent(clipboardManager: ClipboardManager): ToolResult {
        val hasClip = clipboardManager.hasPrimaryClip()
        
        val details = mutableMapOf<String, Any>(
            "has_content" to hasClip
        )

        if (hasClip && clipboardManager.primaryClip != null) {
            val clip = clipboardManager.primaryClip!!
            details["item_count"] = clip.itemCount
            
            clip.description?.let { desc ->
                details["label"] = desc.label?.toString() ?: ""
                details["mime_types"] = (0 until desc.mimeTypeCount).map { desc.getMimeType(it) }
            }
        }

        val message = if (hasClip) {
            "Clipboard has content (${details["item_count"] ?: 0} item(s))"
        } else {
            "Clipboard is empty"
        }

        return ToolResult.success(message, details)
    }
}
