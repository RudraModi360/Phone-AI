package com.example.tools.builtin

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import com.example.service.Parameters
import com.example.service.PropertySchema
import com.example.tools.AndroidPathResolver
import com.example.tools.BaseTool
import com.example.tools.PermissionManager
import com.example.tools.PermissionType
import com.example.tools.RiskLevel
import com.example.tools.ToolResult
import java.text.SimpleDateFormat
import java.util.*

/**
 * Android Sms Tool - Access real SMS inbox and conversations.
 * Uses system Telephony providers.
 * NO MOCK DATA - Returns actual device messages.
 */
class SmsTool : BaseTool {
    override val name = "sms"
    override val description = "Read SMS messages, list conversation threads, or search for content in messages. Requires SMS permission."
    override val riskLevel = RiskLevel.SAFE

    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "operation" to PropertySchema(
                type = "STRING",
                description = "Operation to perform: 'recent_messages', 'search_messages', 'get_threads'"
            ),
            "query" to PropertySchema(
                type = "STRING",
                description = "Search query: keyword in message body or sender name/number (optional)"
            ),
            "limit" to PropertySchema(
                type = "INTEGER",
                description = "Maximum number of messages to return (default: 15, max: 50)"
            )
        ),
        required = emptyList()
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val operation = args["operation"] as? String ?: "recent_messages"
        val query = args["query"] as? String
        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 50) ?: 15

        val context = AndroidPathResolver.getContext()
            ?: return ToolResult.error("Application context not available")

        // Check and request SMS permission
        val permissionError = PermissionManager.ensurePermissions(PermissionType.SMS_READ)
        if (permissionError != null) {
            return ToolResult.error(permissionError)
        }

        val resolver = context.contentResolver

        return try {
            when (operation) {
                "recent_messages" -> getRecentMessages(resolver, limit)
                "search_messages" -> {
                    if (query.isNullOrBlank()) {
                        return ToolResult.error("Query parameter is required for search_messages")
                    }
                    searchMessages(resolver, query, limit)
                }
                "get_threads" -> getThreads(resolver, limit)
                else -> ToolResult.error("Unknown operation: '$operation'. Supported: recent_messages, search_messages, get_threads")
            }
        } catch (e: SecurityException) {
            ToolResult.error("Permission denied: ${PermissionManager.getPermissionDeniedMessage(context, PermissionType.SMS_READ)}")
        } catch (e: Exception) {
            ToolResult.error("SMS operation failed: ${e.message}")
        }
    }

    private fun getRecentMessages(resolver: ContentResolver, limit: Int): ToolResult {
        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("_id", "address", "body", "date", "read")
        val sortOrder = "date DESC"

        val cursor = resolver.query(uri, projection, null, null, sortOrder)
        return formatMessagesCursor(cursor, limit, "Recent SMS Messages")
    }

    private fun searchMessages(resolver: ContentResolver, query: String, limit: Int): ToolResult {
        val uri = Uri.parse("content://sms")
        val projection = arrayOf("_id", "address", "body", "date", "read")
        val selection = "body LIKE ? OR address LIKE ?"
        val selectionArgs = arrayOf("%$query%", "%$query%")
        val sortOrder = "date DESC"

        val cursor = resolver.query(uri, projection, selection, selectionArgs, sortOrder)
        return formatMessagesCursor(cursor, limit, "Search results for '$query'")
    }

    private fun getThreads(resolver: ContentResolver, limit: Int): ToolResult {
        val uri = Uri.parse("content://sms/conversations")
        val projection = arrayOf("thread_id", "msg_count", "snippet")
        val sortOrder = "date DESC"

        val cursor: Cursor? = try {
            resolver.query(uri, projection, null, null, sortOrder)
        } catch (e: Exception) {
            resolver.query(Uri.parse("content://sms/inbox"), arrayOf("thread_id", "address", "body", "date"), null, null, "date DESC")
        }

        val threadsList = mutableListOf<Map<String, Any>>()
        cursor?.use { c ->
            val threadIdIdx = c.getColumnIndex("thread_id")
            val msgCountIdx = c.getColumnIndex("msg_count")
            val snippetIdx = c.getColumnIndex("snippet")

            var count = 0
            while (c.moveToNext() && count < limit) {
                val threadId = if (threadIdIdx >= 0) c.getLong(threadIdIdx) else -1L
                val msgCount = if (msgCountIdx >= 0) c.getInt(msgCountIdx) else 0
                val snippet = if (snippetIdx >= 0) c.getString(snippetIdx) ?: "" else ""

                threadsList.add(mapOf(
                    "thread_id" to threadId.toString(),
                    "msg_count" to msgCount,
                    "snippet" to snippet
                ))
                count++
            }
        }

        if (threadsList.isEmpty()) {
            return ToolResult.success("No SMS threads found.", mapOf("count" to 0, "threads" to emptyList<Any>()))
        }

        val formattedResult = threadsList.joinToString("\n\n") { thread ->
            "🧵 Thread ID: ${thread["thread_id"]} (${thread["msg_count"]} messages)\n  Snippet: ${thread["snippet"]}"
        }

        return ToolResult.success(
            "SMS Conversation Threads:\n\n$formattedResult",
            mapOf("count" to threadsList.size, "threads" to threadsList)
        )
    }

    private fun formatMessagesCursor(cursor: Cursor?, limit: Int, label: String): ToolResult {
        val messages = mutableListOf<Map<String, Any>>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        cursor?.use { c ->
            val idIdx = c.getColumnIndex("_id")
            val addressIdx = c.getColumnIndex("address")
            val bodyIdx = c.getColumnIndex("body")
            val dateIdx = c.getColumnIndex("date")
            val readIdx = c.getColumnIndex("read")

            var count = 0
            while (c.moveToNext() && count < limit) {
                val id = if (idIdx >= 0) c.getLong(idIdx) else -1L
                val address = if (addressIdx >= 0) c.getString(addressIdx) ?: "Unknown" else "Unknown"
                val body = if (bodyIdx >= 0) c.getString(bodyIdx) ?: "" else ""
                val dateMs = if (dateIdx >= 0) c.getLong(dateIdx) else 0L
                val read = if (readIdx >= 0) c.getInt(readIdx) == 1 else false

                val dateStr = if (dateMs > 0) dateFormat.format(Date(dateMs)) else "Unknown"

                messages.add(mapOf(
                    "id" to id.toString(),
                    "sender" to address,
                    "body" to body,
                    "date" to dateStr,
                    "read" to read
                ))
                count++
            }
        }

        if (messages.isEmpty()) {
            return ToolResult.success("No messages found.", mapOf("count" to 0, "messages" to emptyList<Any>()))
        }

        val formattedResult = messages.joinToString("\n\n") { msg ->
            "👤 Sender: ${msg["sender"]}\n📅 Date: ${msg["date"]} [${if (msg["read"] == true) "Read" else "Unread"}]\n💬 Message: ${msg["body"]}"
        }

        return ToolResult.success(
            "$label (${messages.size}):\n\n$formattedResult",
            mapOf("count" to messages.size, "messages" to messages)
        )
    }
}
