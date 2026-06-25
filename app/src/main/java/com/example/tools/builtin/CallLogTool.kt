package com.example.tools.builtin

import android.content.ContentResolver
import android.provider.CallLog
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
 * Android Call Log Tool - Access real call history from the device.
 * Uses CallLog ContentProvider APIs for all operations.
 * NO MOCK DATA - All results come from the device's actual call log.
 */
class CallLogTool : BaseTool {
    override val name = "call_log"
    override val description = "Access call history: recent calls, search by number/name, get call details."
    override val riskLevel = RiskLevel.SAFE

    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "operation" to PropertySchema(
                type = "STRING",
                description = "Operation: 'recent_calls', 'search_calls', 'get_call_details', 'call_stats'"
            ),
            "query" to PropertySchema(
                type = "STRING",
                description = "Search query (phone number or cached name) for search_calls"
            ),
            "call_id" to PropertySchema(
                type = "STRING",
                description = "Call ID for get_call_details"
            ),
            "call_type" to PropertySchema(
                type = "STRING",
                description = "Filter by type: 'incoming', 'outgoing', 'missed', 'all' (default: 'all')"
            ),
            "limit" to PropertySchema(
                type = "INTEGER",
                description = "Maximum number of calls to return (default: 20)"
            )
        ),
        required = emptyList()
    )

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        private val DISPLAY_FORMAT = SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val operation = args["operation"] as? String ?: "recent_calls"
        val query = args["query"] as? String
        val callId = args["call_id"] as? String
        val callType = args["call_type"] as? String ?: "all"
        val limit = (args["limit"] as? Number)?.toInt() ?: 20

        val context = AndroidPathResolver.getContext()
            ?: return ToolResult.error("Application context not available")

        // Request call log permission
        val permissionError = PermissionManager.ensurePermissions(PermissionType.CALL_LOG_READ)
        if (permissionError != null) {
            return ToolResult.error(permissionError)
        }

        val resolver = context.contentResolver

        return try {
            when (operation) {
                "recent_calls" -> getRecentCalls(resolver, callType, limit)
                "search_calls" -> {
                    if (query.isNullOrBlank()) {
                        return ToolResult.error("Missing 'query' parameter for search_calls")
                    }
                    searchCalls(resolver, query, callType, limit)
                }
                "get_call_details" -> {
                    if (callId.isNullOrBlank()) {
                        return ToolResult.error("Missing 'call_id' parameter")
                    }
                    getCallDetails(resolver, callId)
                }
                "call_stats" -> getCallStats(resolver)
                else -> ToolResult.error("Unknown operation: '$operation'. Use: recent_calls, search_calls, get_call_details, call_stats")
            }
        } catch (e: SecurityException) {
            ToolResult.error("Permission denied: ${PermissionManager.getPermissionDeniedMessage(context, PermissionType.CALL_LOG_READ)}")
        } catch (e: Exception) {
            ToolResult.error("Call log operation failed: ${e.message}")
        }
    }

    /**
     * Get recent calls from call log.
     */
    private fun getRecentCalls(resolver: ContentResolver, callType: String, limit: Int): ToolResult {
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        val selection = getTypeSelection(callType)
        val selectionArgs = getTypeSelectionArgs(callType)

        val cursor = resolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CallLog.Calls.DATE} DESC"
        )

        return parseCallsFromCursor(cursor, limit, "Recent Calls")
    }

    /**
     * Search calls by number or cached name.
     */
    private fun searchCalls(resolver: ContentResolver, query: String, callType: String, limit: Int): ToolResult {
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        val typeSelection = getTypeSelection(callType)
        val baseSelection = "(${CallLog.Calls.NUMBER} LIKE ? OR ${CallLog.Calls.CACHED_NAME} LIKE ?)"
        val selection = if (typeSelection != null) {
            "$baseSelection AND $typeSelection"
        } else {
            baseSelection
        }

        val baseArgs = arrayOf("%$query%", "%$query%")
        val selectionArgs = if (typeSelection != null) {
            baseArgs + getTypeSelectionArgs(callType)!!
        } else {
            baseArgs
        }

        val cursor = resolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CallLog.Calls.DATE} DESC"
        )

        return parseCallsFromCursor(cursor, limit, "Calls matching '$query'")
    }

    /**
     * Get details for a specific call.
     */
    private fun getCallDetails(resolver: ContentResolver, callId: String): ToolResult {
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_NUMBER_TYPE,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.GEOCODED_LOCATION,
            CallLog.Calls.IS_READ
        )

        val cursor = resolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            "${CallLog.Calls._ID} = ?",
            arrayOf(callId),
            null
        )

        cursor?.use { c ->
            if (c.moveToFirst()) {
                val number = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)) ?: "Unknown"
                val name = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)) ?: ""
                val type = c.getInt(c.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                val dateMs = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DATE))
                val duration = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                val locationIdx = c.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)
                val location = if (locationIdx >= 0) c.getString(locationIdx) ?: "" else ""
                val isRead = c.getInt(c.getColumnIndexOrThrow(CallLog.Calls.IS_READ)) == 1

                val typeStr = getCallTypeString(type)
                val durationStr = formatDuration(duration)
                val dateStr = DISPLAY_FORMAT.format(Date(dateMs))

                val details = mapOf(
                    "id" to callId,
                    "number" to number,
                    "name" to name,
                    "type" to typeStr,
                    "date" to dateStr,
                    "date_ms" to dateMs,
                    "duration" to duration,
                    "duration_formatted" to durationStr,
                    "location" to location,
                    "is_read" to isRead
                )

                val formatted = """
Call Details (ID: $callId)
==========================
Number: $number
Name: ${name.ifEmpty { "Unknown" }}
Type: $typeStr
Date: $dateStr
Duration: $durationStr
Location: ${location.ifEmpty { "N/A" }}
Read: ${if (isRead) "Yes" else "No"}
                """.trimIndent()

                return ToolResult.success(formatted, details)
            }
        }

        return ToolResult.error("Call with ID '$callId' not found.")
    }

    /**
     * Get call statistics.
     */
    private fun getCallStats(resolver: ContentResolver): ToolResult {
        val projection = arrayOf(
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION
        )

        var incoming = 0
        var outgoing = 0
        var missed = 0
        var rejected = 0
        var totalDuration: Long = 0
        var totalCalls = 0

        resolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val durationIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)

            while (cursor.moveToNext()) {
                val type = cursor.getInt(typeIdx)
                val duration = cursor.getLong(durationIdx)

                when (type) {
                    CallLog.Calls.INCOMING_TYPE -> incoming++
                    CallLog.Calls.OUTGOING_TYPE -> outgoing++
                    CallLog.Calls.MISSED_TYPE -> missed++
                    CallLog.Calls.REJECTED_TYPE -> rejected++
                }

                totalDuration += duration
                totalCalls++
            }
        }

        val avgDuration = if (totalCalls > 0) totalDuration / totalCalls else 0

        val stats = mapOf(
            "total_calls" to totalCalls,
            "incoming" to incoming,
            "outgoing" to outgoing,
            "missed" to missed,
            "rejected" to rejected,
            "total_duration_seconds" to totalDuration,
            "total_duration_formatted" to formatDuration(totalDuration),
            "average_duration_seconds" to avgDuration,
            "average_duration_formatted" to formatDuration(avgDuration)
        )

        val formatted = """
Call Statistics
===============
Total Calls: $totalCalls
  📞 Incoming: $incoming
  📱 Outgoing: $outgoing
  ❌ Missed: $missed
  🚫 Rejected: $rejected

Total Talk Time: ${formatDuration(totalDuration)}
Average Call Duration: ${formatDuration(avgDuration)}
        """.trimIndent()

        return ToolResult.success(formatted, stats)
    }

    /**
     * Parse calls from cursor into ToolResult.
     */
    private fun parseCallsFromCursor(cursor: android.database.Cursor?, limit: Int, label: String): ToolResult {
        val calls = mutableListOf<Map<String, Any>>()

        cursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(CallLog.Calls._ID)
            val numberIdx = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val nameIdx = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val typeIdx = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateIdx = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durationIdx = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)

            var count = 0
            while (c.moveToNext() && count < limit) {
                val id = c.getLong(idIdx)
                val number = c.getString(numberIdx) ?: "Unknown"
                val name = c.getString(nameIdx) ?: ""
                val type = c.getInt(typeIdx)
                val dateMs = c.getLong(dateIdx)
                val duration = c.getLong(durationIdx)

                val typeStr = getCallTypeString(type)
                val typeIcon = getCallTypeIcon(type)
                val durationStr = formatDuration(duration)
                val dateStr = DISPLAY_FORMAT.format(Date(dateMs))

                calls.add(mapOf(
                    "id" to id.toString(),
                    "number" to number,
                    "name" to name,
                    "type" to typeStr,
                    "type_icon" to typeIcon,
                    "date" to dateStr,
                    "date_ms" to dateMs,
                    "duration" to duration,
                    "duration_formatted" to durationStr
                ))
                count++
            }
        }

        if (calls.isEmpty()) {
            return ToolResult.success(
                "No calls found.",
                mapOf("count" to 0, "calls" to emptyList<Map<String, Any>>())
            )
        }

        val formatted = calls.joinToString("\n\n") { call ->
            val displayName = if ((call["name"] as String).isNotEmpty()) {
                "${call["name"]} (${call["number"]})"
            } else {
                call["number"] as String
            }
            "${call["type_icon"]} $displayName\n  ${call["date"]} • ${call["duration_formatted"]}"
        }

        return ToolResult.success(
            "$label (${calls.size}):\n\n$formatted",
            mapOf("count" to calls.size, "calls" to calls)
        )
    }

    private fun getTypeSelection(callType: String): String? {
        return when (callType.lowercase()) {
            "incoming" -> "${CallLog.Calls.TYPE} = ?"
            "outgoing" -> "${CallLog.Calls.TYPE} = ?"
            "missed" -> "${CallLog.Calls.TYPE} = ?"
            else -> null
        }
    }

    private fun getTypeSelectionArgs(callType: String): Array<String>? {
        return when (callType.lowercase()) {
            "incoming" -> arrayOf(CallLog.Calls.INCOMING_TYPE.toString())
            "outgoing" -> arrayOf(CallLog.Calls.OUTGOING_TYPE.toString())
            "missed" -> arrayOf(CallLog.Calls.MISSED_TYPE.toString())
            else -> null
        }
    }

    private fun getCallTypeString(type: Int): String {
        return when (type) {
            CallLog.Calls.INCOMING_TYPE -> "Incoming"
            CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
            CallLog.Calls.MISSED_TYPE -> "Missed"
            CallLog.Calls.REJECTED_TYPE -> "Rejected"
            CallLog.Calls.BLOCKED_TYPE -> "Blocked"
            CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
            else -> "Unknown"
        }
    }

    private fun getCallTypeIcon(type: Int): String {
        return when (type) {
            CallLog.Calls.INCOMING_TYPE -> "📞"
            CallLog.Calls.OUTGOING_TYPE -> "📱"
            CallLog.Calls.MISSED_TYPE -> "❌"
            CallLog.Calls.REJECTED_TYPE -> "🚫"
            CallLog.Calls.BLOCKED_TYPE -> "🔒"
            CallLog.Calls.VOICEMAIL_TYPE -> "📧"
            else -> "📞"
        }
    }

    private fun formatDuration(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> {
                val mins = seconds / 60
                val secs = seconds % 60
                "${mins}m ${secs}s"
            }
            else -> {
                val hours = seconds / 3600
                val mins = (seconds % 3600) / 60
                val secs = seconds % 60
                "${hours}h ${mins}m ${secs}s"
            }
        }
    }
}
