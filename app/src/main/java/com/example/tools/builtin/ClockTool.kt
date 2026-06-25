package com.example.tools.builtin

import com.example.clock.ClockManager
import com.example.service.Parameters
import com.example.service.PropertySchema
import com.example.tools.AndroidPathResolver
import com.example.tools.BaseTool
import com.example.tools.RiskLevel
import com.example.tools.ToolResult
import java.text.SimpleDateFormat
import java.util.*

/**
 * Clock Tool - Native Time Management System integration.
 * Supports persistent background alarms, reminders, countdown timers, and stopwatch controls.
 */
class ClockTool : BaseTool {
    override val name = "clock"
    override val description = "Creation and management of system alarms, reminders, countdown timers, and background stopwatch. All tasks run and persist in the background. Safe to run."
    override val riskLevel = RiskLevel.SAFE

    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "operation" to PropertySchema(
                type = "STRING",
                description = "Operation to perform: 'create_alarm', 'create_reminder', 'create_timer', 'start_stopwatch', 'pause_stopwatch', 'resume_stopwatch', 'stop_stopwatch', 'get_all_events', 'toggle_event', 'delete_event', 'get_clock_info'"
            ),
            "hour" to PropertySchema(
                type = "INTEGER",
                description = "Hour for alarm (0-23, required if operation is 'create_alarm')"
            ),
            "minutes" to PropertySchema(
                type = "INTEGER",
                description = "Minutes for alarm (0-59, required if operation is 'create_alarm')"
            ),
            "seconds" to PropertySchema(
                type = "INTEGER",
                description = "Length of the timer in seconds (required if operation is 'create_timer')"
            ),
            "minutes_from_now" to PropertySchema(
                type = "INTEGER",
                description = "Minutes from now when reminder should trigger (required if operation is 'create_reminder')"
            ),
            "message" to PropertySchema(
                type = "STRING",
                description = "Label/Title/Message for alarm, timer, or reminder (optional)"
            ),
            "repeat" to PropertySchema(
                type = "STRING",
                description = "Repeat pattern for alarm: 'once', 'daily', 'weekly', or comma-separated days 'Mon,Tue' (optional, defaults to 'once')"
            ),
            "ringtone" to PropertySchema(
                type = "STRING",
                description = "Ringtone sound key: 'default' or custom sound name (optional)"
            ),
            "eventId" to PropertySchema(
                type = "STRING",
                description = "The database UUID of the target event (required for 'toggle_event' and 'delete_event')"
            )
        ),
        required = listOf("operation")
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val operation = args["operation"] as? String ?: "get_clock_info"
        val hour = (args["hour"] as? Number)?.toInt()
        val minutes = (args["minutes"] as? Number)?.toInt()
        val seconds = (args["seconds"] as? Number)?.toInt()
        val minutesFromNow = (args["minutes_from_now"] as? Number)?.toInt()
        val message = args["message"] as? String ?: args["title"] as? String ?: ""
        val repeat = args["repeat"] as? String ?: "once"
        val ringtone = args["ringtone"] as? String ?: "default"
        val eventId = args["eventId"] as? String ?: ""

        val context = AndroidPathResolver.getContext()
            ?: return ToolResult.error("Application context is unavailable")

        val clockManager = ClockManager.getInstance(context)

        return try {
            when (operation) {
                "create_alarm" -> {
                    if (hour == null || minutes == null) {
                        return ToolResult.error("Both 'hour' and 'minutes' parameters are required for 'create_alarm'")
                    }
                    if (hour !in 0..23 || minutes !in 0..59) {
                        return ToolResult.error("Invalid time: hour must be 0-23 and minutes must be 0-59")
                    }

                    // Calculate target alarm calendar time
                    val now = Calendar.getInstance()
                    val target = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minutes)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (target.before(now)) {
                        target.add(Calendar.DAY_OF_YEAR, 1)
                    }

                    val event = clockManager.createAlarm(
                        title = message.ifBlank { "Alarm" },
                        startTime = target.timeInMillis,
                        repeat = repeat,
                        ringtone = ringtone
                    )

                    val formattedMsg = "Successfully scheduled background alarm '${event.title}' for $hour:${String.format("%02d", minutes)} (Repeat: $repeat)"
                    ToolResult.success(
                        formattedMsg,
                        mapOf("success" to true, "eventId" to event.id, "message" to formattedMsg)
                    )
                }
                "create_reminder" -> {
                    val minutesVal = minutesFromNow ?: 5
                    val targetTime = System.currentTimeMillis() + (minutesVal * 60 * 1000L)
                    val reminderTitle = message.ifBlank { "Reminder" }

                    val event = clockManager.createReminder(
                        title = reminderTitle,
                        startTime = targetTime
                    )

                    val formattedMsg = "Successfully scheduled background reminder '${event.title}' in $minutesVal minutes"
                    ToolResult.success(
                        formattedMsg,
                        mapOf("success" to true, "eventId" to event.id, "message" to formattedMsg)
                    )
                }
                "create_timer" -> {
                    val secondsVal = seconds ?: 60
                    if (secondsVal <= 0) {
                        return ToolResult.error("'seconds' must be a positive integer greater than zero")
                    }
                    val timerTitle = message.ifBlank { "Timer" }

                    val event = clockManager.createTimer(
                        title = timerTitle,
                        durationMs = secondsVal * 1000L
                    )

                    val formattedMsg = "Successfully started background timer '${event.title}' for $secondsVal seconds"
                    ToolResult.success(
                        formattedMsg,
                        mapOf("success" to true, "eventId" to event.id, "message" to formattedMsg)
                    )
                }
                "start_stopwatch" -> {
                    clockManager.startStopwatch()
                    ToolResult.success("Stopwatch started in the background", mapOf("success" to true))
                }
                "pause_stopwatch" -> {
                    clockManager.pauseStopwatch()
                    ToolResult.success("Stopwatch paused in the background", mapOf("success" to true))
                }
                "resume_stopwatch" -> {
                    clockManager.resumeStopwatch()
                    ToolResult.success("Stopwatch resumed in the background", mapOf("success" to true))
                }
                "stop_stopwatch" -> {
                    clockManager.stopStopwatch()
                    ToolResult.success("Stopwatch stopped and reset", mapOf("success" to true))
                }
                "toggle_event" -> {
                    if (eventId.isEmpty()) {
                        return ToolResult.error("'eventId' is required to toggle event")
                    }
                    clockManager.toggleEventStatus(eventId)
                    ToolResult.success("Successfully toggled status for event $eventId", mapOf("success" to true))
                }
                "delete_event" -> {
                    if (eventId.isEmpty()) {
                        return ToolResult.error("'eventId' is required to delete event")
                    }
                    clockManager.deleteEvent(eventId)
                    ToolResult.success("Successfully deleted event $eventId", mapOf("success" to true))
                }
                "get_all_events" -> {
                    // Collect events directly from standard DB query
                    val db = com.example.clock.database.ClockDatabase.getDatabase(context)
                    val list = db.clockDao().getAllEvents()
                    ToolResult.success("Retrieved ${list.size} clock events", mapOf("success" to true, "events" to list))
                }
                "get_clock_info" -> {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
                    val calendar = Calendar.getInstance()
                    val timeStr = sdf.format(calendar.time)
                    val tz = TimeZone.getDefault().id
                    val ms = System.currentTimeMillis()
                    ToolResult.success(
                        "Clock Info:\nTime: $timeStr\nTimeZone: $tz\nMillis: $ms",
                        mapOf(
                            "currentTime" to timeStr,
                            "timezone" to tz,
                            "currentTimeMillis" to ms
                        )
                    )
                }
                else -> ToolResult.error("Unknown operation: '$operation'")
            }
        } catch (e: Exception) {
            ToolResult.error("Clock execution failed: ${e.message}")
        }
    }
}
