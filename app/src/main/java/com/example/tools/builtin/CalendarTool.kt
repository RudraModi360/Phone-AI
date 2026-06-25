package com.example.tools.builtin

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
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
 * Android Calendar Tool - Full CRUD operations on calendar events.
 * Uses CalendarContract ContentProvider APIs for all operations.
 * NO MOCK DATA - All results come from the device's actual calendar.
 */
class CalendarTool : BaseTool {
    override val name = "calendar"
    override val description = "Full CRUD operations on Android calendar: list calendars, list/search events, create/update/delete events."
    override val riskLevel = RiskLevel.SAFE

    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "operation" to PropertySchema(
                type = "STRING",
                description = "Operation: 'list_calendars', 'list_events', 'search_events', 'get_event', 'create_event', 'update_event', 'delete_event'"
            ),
            "calendar_id" to PropertySchema(
                type = "STRING",
                description = "Calendar ID for operations (use list_calendars to find IDs)"
            ),
            "event_id" to PropertySchema(
                type = "STRING",
                description = "Event ID for get/update/delete operations"
            ),
            "title" to PropertySchema(
                type = "STRING",
                description = "Event title for create/update/search"
            ),
            "description" to PropertySchema(
                type = "STRING",
                description = "Event description"
            ),
            "location" to PropertySchema(
                type = "STRING",
                description = "Event location"
            ),
            "start_time" to PropertySchema(
                type = "STRING",
                description = "Start time in ISO format (yyyy-MM-dd HH:mm) or 'today', 'tomorrow'"
            ),
            "end_time" to PropertySchema(
                type = "STRING",
                description = "End time in ISO format (yyyy-MM-dd HH:mm)"
            ),
            "all_day" to PropertySchema(
                type = "BOOLEAN",
                description = "Whether event is all-day (default: false)"
            ),
            "days_ahead" to PropertySchema(
                type = "INTEGER",
                description = "Number of days ahead to search for events (default: 7)"
            ),
            "limit" to PropertySchema(
                type = "INTEGER",
                description = "Maximum number of results (default: 20)"
            )
        ),
        required = emptyList()
    )

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        private val DATE_ONLY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        private val DISPLAY_FORMAT = SimpleDateFormat("EEE, MMM d yyyy 'at' h:mm a", Locale.getDefault())
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val operation = args["operation"] as? String ?: "list_events"
        val calendarId = args["calendar_id"] as? String
        val eventId = args["event_id"] as? String
        val title = args["title"] as? String
        val description = args["description"] as? String
        val location = args["location"] as? String
        val startTime = args["start_time"] as? String
        val endTime = args["end_time"] as? String
        val allDay = args["all_day"] as? Boolean ?: false
        val daysAhead = (args["days_ahead"] as? Number)?.toInt() ?: 7
        val limit = (args["limit"] as? Number)?.toInt() ?: 20

        val context = AndroidPathResolver.getContext()
            ?: return ToolResult.error("Application context not available")

        // Determine permission type based on operation
        val permissionType = when (operation) {
            "create_event", "update_event", "delete_event" -> PermissionType.CALENDAR_WRITE
            else -> PermissionType.CALENDAR_READ
        }

        val permissionError = PermissionManager.ensurePermissions(permissionType)
        if (permissionError != null) {
            return ToolResult.error(permissionError)
        }

        val resolver = context.contentResolver

        return try {
            when (operation) {
                "list_calendars" -> listCalendars(resolver)
                "list_events" -> listEvents(resolver, calendarId, daysAhead, limit)
                "search_events" -> {
                    if (title.isNullOrBlank()) {
                        return ToolResult.error("Missing 'title' parameter for search_events")
                    }
                    searchEvents(resolver, title, daysAhead, limit)
                }
                "get_event" -> {
                    if (eventId.isNullOrBlank()) {
                        return ToolResult.error("Missing 'event_id' parameter")
                    }
                    getEventDetails(resolver, eventId)
                }
                "create_event" -> {
                    if (title.isNullOrBlank()) {
                        return ToolResult.error("Missing 'title' parameter for create_event")
                    }
                    if (startTime.isNullOrBlank()) {
                        return ToolResult.error("Missing 'start_time' parameter for create_event")
                    }
                    createEvent(resolver, calendarId, title, description, location, startTime, endTime, allDay)
                }
                "update_event" -> {
                    if (eventId.isNullOrBlank()) {
                        return ToolResult.error("Missing 'event_id' parameter")
                    }
                    updateEvent(resolver, eventId, title, description, location, startTime, endTime, allDay)
                }
                "delete_event" -> {
                    if (eventId.isNullOrBlank()) {
                        return ToolResult.error("Missing 'event_id' parameter")
                    }
                    deleteEvent(resolver, eventId)
                }
                else -> ToolResult.error("Unknown operation: '$operation'")
            }
        } catch (e: SecurityException) {
            ToolResult.error("Permission denied: ${PermissionManager.getPermissionDeniedMessage(context, permissionType)}")
        } catch (e: Exception) {
            ToolResult.error("Calendar operation failed: ${e.message}")
        }
    }

    /**
     * List all calendars on the device.
     */
    private fun listCalendars(resolver: ContentResolver): ToolResult {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE
        )

        val cursor = resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
        )

        val calendars = mutableListOf<Map<String, Any>>()

        cursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val ownerIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars.OWNER_ACCOUNT)
            val visibleIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars.VISIBLE)

            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val name = c.getString(nameIdx) ?: "Unnamed"
                val account = c.getString(accountIdx) ?: ""
                val owner = c.getString(ownerIdx) ?: ""
                val visible = c.getInt(visibleIdx) == 1

                calendars.add(mapOf(
                    "id" to id.toString(),
                    "name" to name,
                    "account" to account,
                    "owner" to owner,
                    "visible" to visible
                ))
            }
        }

        if (calendars.isEmpty()) {
            return ToolResult.success(
                "No calendars found on this device.",
                mapOf("count" to 0, "calendars" to emptyList<Map<String, Any>>())
            )
        }

        val formatted = calendars.joinToString("\n") { cal ->
            "- ${cal["name"]} (ID: ${cal["id"]})\n  Account: ${cal["account"]}\n  Visible: ${cal["visible"]}"
        }

        return ToolResult.success(
            "Calendars (${calendars.size}):\n\n$formatted",
            mapOf("count" to calendars.size, "calendars" to calendars)
        )
    }

    /**
     * List upcoming events.
     */
    private fun listEvents(resolver: ContentResolver, calendarId: String?, daysAhead: Int, limit: Int): ToolResult {
        val now = System.currentTimeMillis()
        val endRange = now + (daysAhead.toLong() * 24 * 60 * 60 * 1000)

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.CALENDAR_ID
        )

        val selection = if (calendarId != null) {
            "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ? AND ${CalendarContract.Events.CALENDAR_ID} = ?"
        } else {
            "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        }

        val selectionArgs = if (calendarId != null) {
            arrayOf(now.toString(), endRange.toString(), calendarId)
        } else {
            arrayOf(now.toString(), endRange.toString())
        }

        val cursor = resolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC"
        )

        return parseEventsFromCursor(cursor, limit, "Upcoming Events (next $daysAhead days)")
    }

    /**
     * Search events by title.
     */
    private fun searchEvents(resolver: ContentResolver, query: String, daysAhead: Int, limit: Int): ToolResult {
        val now = System.currentTimeMillis()
        val endRange = now + (daysAhead.toLong() * 24 * 60 * 60 * 1000)

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.CALENDAR_ID
        )

        val selection = "${CalendarContract.Events.TITLE} LIKE ? AND ${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf("%$query%", now.toString(), endRange.toString())

        val cursor = resolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC"
        )

        return parseEventsFromCursor(cursor, limit, "Events matching '$query'")
    }

    /**
     * Parse events from cursor into ToolResult.
     */
    private fun parseEventsFromCursor(cursor: android.database.Cursor?, limit: Int, label: String): ToolResult {
        val events = mutableListOf<Map<String, Any>>()

        cursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val titleIdx = c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val descIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
            val startIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val endIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            val locationIdx = c.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
            val allDayIdx = c.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)

            var count = 0
            while (c.moveToNext() && count < limit) {
                val id = c.getLong(idIdx)
                val title = c.getString(titleIdx) ?: "Untitled"
                val desc = c.getString(descIdx) ?: ""
                val startMs = c.getLong(startIdx)
                val endMs = c.getLong(endIdx)
                val location = c.getString(locationIdx) ?: ""
                val allDay = c.getInt(allDayIdx) == 1

                val startStr = DISPLAY_FORMAT.format(Date(startMs))
                val endStr = if (endMs > 0) DISPLAY_FORMAT.format(Date(endMs)) else "N/A"

                events.add(mapOf(
                    "id" to id.toString(),
                    "title" to title,
                    "description" to desc,
                    "start" to startStr,
                    "end" to endStr,
                    "start_ms" to startMs,
                    "end_ms" to endMs,
                    "location" to location,
                    "all_day" to allDay
                ))
                count++
            }
        }

        if (events.isEmpty()) {
            return ToolResult.success(
                "No events found.",
                mapOf("count" to 0, "events" to emptyList<Map<String, Any>>())
            )
        }

        val formatted = events.joinToString("\n\n") { e ->
            val locationStr = if ((e["location"] as String).isNotEmpty()) "\n  📍 ${e["location"]}" else ""
            val descStr = if ((e["description"] as String).isNotEmpty()) "\n  ${e["description"]}" else ""
            "- ${e["title"]} (ID: ${e["id"]})\n  🕐 ${e["start"]}$locationStr$descStr"
        }

        return ToolResult.success(
            "$label (${events.size}):\n\n$formatted",
            mapOf("count" to events.size, "events" to events)
        )
    }

    /**
     * Get details for a specific event.
     */
    private fun getEventDetails(resolver: ContentResolver, eventId: String): ToolResult {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.ORGANIZER,
            CalendarContract.Events.EVENT_TIMEZONE
        )

        val cursor = resolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "${CalendarContract.Events._ID} = ?",
            arrayOf(eventId),
            null
        )

        cursor?.use { c ->
            if (c.moveToFirst()) {
                val title = c.getString(c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)) ?: "Untitled"
                val desc = c.getString(c.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)) ?: ""
                val startMs = c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                val endMs = c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events.DTEND))
                val location = c.getString(c.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)) ?: ""
                val allDay = c.getInt(c.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)) == 1
                val calId = c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID))
                val organizer = c.getString(c.getColumnIndexOrThrow(CalendarContract.Events.ORGANIZER)) ?: ""
                val timezone = c.getString(c.getColumnIndexOrThrow(CalendarContract.Events.EVENT_TIMEZONE)) ?: ""

                val details = mapOf(
                    "id" to eventId,
                    "title" to title,
                    "description" to desc,
                    "start" to DISPLAY_FORMAT.format(Date(startMs)),
                    "end" to if (endMs > 0) DISPLAY_FORMAT.format(Date(endMs)) else "N/A",
                    "location" to location,
                    "all_day" to allDay,
                    "calendar_id" to calId.toString(),
                    "organizer" to organizer,
                    "timezone" to timezone
                )

                val formatted = """
Event Details (ID: $eventId)
============================
Title: $title
Start: ${DISPLAY_FORMAT.format(Date(startMs))}
End: ${if (endMs > 0) DISPLAY_FORMAT.format(Date(endMs)) else "N/A"}
Location: ${location.ifEmpty { "Not set" }}
All Day: $allDay
Description: ${desc.ifEmpty { "None" }}
Organizer: ${organizer.ifEmpty { "N/A" }}
Timezone: ${timezone.ifEmpty { "Default" }}
                """.trimIndent()

                return ToolResult.success(formatted, details)
            }
        }

        return ToolResult.error("Event with ID '$eventId' not found.")
    }

    /**
     * Create a new calendar event.
     */
    private fun createEvent(
        resolver: ContentResolver,
        calendarId: String?,
        title: String,
        description: String?,
        location: String?,
        startTime: String,
        endTime: String?,
        allDay: Boolean
    ): ToolResult {
        // Get default calendar if not specified
        val actualCalendarId = calendarId?.toLongOrNull() ?: getDefaultCalendarId(resolver)
            ?: return ToolResult.error("No calendar found. Please specify calendar_id or add a calendar to the device.")

        // Parse start time
        val startMs = parseDateTime(startTime)
            ?: return ToolResult.error("Invalid start_time format. Use 'yyyy-MM-dd HH:mm' or 'today'/'tomorrow'")

        // Parse or calculate end time (default: 1 hour after start)
        val endMs = if (endTime != null) {
            parseDateTime(endTime) ?: return ToolResult.error("Invalid end_time format")
        } else {
            startMs + (60 * 60 * 1000) // 1 hour
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, actualCalendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMs)
            put(CalendarContract.Events.DTEND, endMs)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            description?.let { put(CalendarContract.Events.DESCRIPTION, it) }
            location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
        }

        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return ToolResult.error("Failed to create event")

        val eventId = uri.lastPathSegment

        return ToolResult.success(
            "Event created successfully!\nTitle: $title\nStart: ${DISPLAY_FORMAT.format(Date(startMs))}\nEvent ID: $eventId",
            mapOf(
                "success" to true,
                "event_id" to (eventId ?: "unknown"),
                "title" to title,
                "start" to DISPLAY_FORMAT.format(Date(startMs)),
                "end" to DISPLAY_FORMAT.format(Date(endMs)),
                "calendar_id" to actualCalendarId.toString()
            )
        )
    }

    /**
     * Update an existing event.
     */
    private fun updateEvent(
        resolver: ContentResolver,
        eventId: String,
        title: String?,
        description: String?,
        location: String?,
        startTime: String?,
        endTime: String?,
        allDay: Boolean?
    ): ToolResult {
        val values = ContentValues()
        
        title?.let { values.put(CalendarContract.Events.TITLE, it) }
        description?.let { values.put(CalendarContract.Events.DESCRIPTION, it) }
        location?.let { values.put(CalendarContract.Events.EVENT_LOCATION, it) }
        startTime?.let { 
            val ms = parseDateTime(it) ?: return ToolResult.error("Invalid start_time format")
            values.put(CalendarContract.Events.DTSTART, ms)
        }
        endTime?.let {
            val ms = parseDateTime(it) ?: return ToolResult.error("Invalid end_time format")
            values.put(CalendarContract.Events.DTEND, ms)
        }
        allDay?.let { values.put(CalendarContract.Events.ALL_DAY, if (it) 1 else 0) }

        if (values.size() == 0) {
            return ToolResult.error("No fields to update. Provide at least one of: title, description, location, start_time, end_time, all_day")
        }

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId.toLong())
        val rowsUpdated = resolver.update(uri, values, null, null)

        return if (rowsUpdated > 0) {
            ToolResult.success(
                "Event updated successfully! (ID: $eventId)",
                mapOf("success" to true, "event_id" to eventId, "fields_updated" to values.size())
            )
        } else {
            ToolResult.error("Failed to update event. Event may not exist.")
        }
    }

    /**
     * Delete an event.
     */
    private fun deleteEvent(resolver: ContentResolver, eventId: String): ToolResult {
        // Get event title first for confirmation message
        var eventTitle = "Unknown"
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events.TITLE),
            "${CalendarContract.Events._ID} = ?",
            arrayOf(eventId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                eventTitle = cursor.getString(0) ?: "Unknown"
            }
        }

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId.toLong())
        val rowsDeleted = resolver.delete(uri, null, null)

        return if (rowsDeleted > 0) {
            ToolResult.success(
                "Event deleted successfully!\nDeleted: $eventTitle (ID: $eventId)",
                mapOf("success" to true, "event_id" to eventId, "deleted_title" to eventTitle)
            )
        } else {
            ToolResult.error("Failed to delete event. Event may not exist.")
        }
    }

    /**
     * Get the default (primary) calendar ID.
     */
    private fun getDefaultCalendarId(resolver: ContentResolver): Long? {
        val cursor = resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC"
        )

        cursor?.use { c ->
            if (c.moveToFirst()) {
                return c.getLong(0)
            }
        }
        return null
    }

    /**
     * Parse date/time string to milliseconds.
     */
    private fun parseDateTime(input: String): Long? {
        val calendar = Calendar.getInstance()
        
        return when (input.lowercase().trim()) {
            "today" -> {
                calendar.set(Calendar.HOUR_OF_DAY, 9)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.timeInMillis
            }
            "tomorrow" -> {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 9)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.timeInMillis
            }
            else -> {
                try {
                    DATE_FORMAT.parse(input)?.time
                } catch (e: Exception) {
                    try {
                        val date = DATE_ONLY_FORMAT.parse(input)
                        calendar.time = date!!
                        calendar.set(Calendar.HOUR_OF_DAY, 9)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.timeInMillis
                    } catch (e2: Exception) {
                        null
                    }
                }
            }
        }
    }
}
