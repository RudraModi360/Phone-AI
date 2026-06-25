package com.example.clock

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.clock.database.ClockDatabase
import com.example.clock.scheduler.AlarmScheduler
import com.example.clock.scheduler.ReminderScheduler
import com.example.clock.scheduler.TimerScheduler
import com.example.clock.service.StopwatchService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class ClockManager private constructor(private val context: Context) {

    private val db = ClockDatabase.getDatabase(context)
    private val dao = db.clockDao()

    private val alarmScheduler = AlarmScheduler(context)
    private val reminderScheduler = ReminderScheduler(context)
    private val timerScheduler = TimerScheduler(context)

    private val toggleMutex = Mutex()

    fun getAllEventsFlow(): Flow<List<ClockEvent>> = dao.getAllEventsFlow()

    suspend fun getEventById(id: String): ClockEvent? = dao.getEventById(id)

    suspend fun createAlarm(title: String, startTime: Long, repeat: String, ringtone: String): ClockEvent {
        val event = ClockEvent(
            id = UUID.randomUUID().toString(),
            type = "alarm",
            title = title.ifBlank { "Alarm" },
            startTime = startTime,
            duration = 0,
            repeat = repeat.ifBlank { "once" },
            ringtone = ringtone.ifBlank { "default" },
            status = "active"
        )
        dao.insertEvent(event)
        alarmScheduler.schedule(event)
        Log.d("ClockManager", "Created and scheduled alarm: ${event.id}")
        return event
    }

    suspend fun createReminder(title: String, startTime: Long): ClockEvent {
        val event = ClockEvent(
            id = UUID.randomUUID().toString(),
            type = "reminder",
            title = title.ifBlank { "Reminder" },
            startTime = startTime,
            duration = 0,
            repeat = "once",
            ringtone = "default",
            status = "active"
        )
        dao.insertEvent(event)
        reminderScheduler.schedule(event)
        Log.d("ClockManager", "Created and scheduled reminder: ${event.id}")
        return event
    }

    suspend fun createTimer(title: String, durationMs: Long): ClockEvent {
        val event = ClockEvent(
            id = UUID.randomUUID().toString(),
            type = "timer",
            title = title.ifBlank { "Timer" },
            startTime = System.currentTimeMillis(),
            duration = durationMs,
            repeat = "once",
            ringtone = "default",
            status = "active"
        )
        dao.insertEvent(event)
        timerScheduler.schedule(event)
        Log.d("ClockManager", "Created and scheduled timer: ${event.id}")
        return event
    }

    suspend fun deleteEvent(eventId: String) {
        val event = dao.getEventById(eventId) ?: return
        cancelEventScheduler(event)
        dao.deleteEvent(event)
        Log.d("ClockManager", "Deleted event: $eventId")
    }

    suspend fun toggleEventStatus(eventId: String) {
        toggleMutex.withLock {
            val event = dao.getEventById(eventId) ?: return@withLock
            if (event.status == "active") {
                // Deactivate
                cancelEventScheduler(event)
                val updated = event.copy(status = "paused")
                dao.updateEvent(updated)
                Log.d("ClockManager", "Paused event: $eventId")
            } else {
                // Activate (Ensure trigger times aren't in the past)
                var nextStartTime = event.startTime
                if (event.type == "alarm" && nextStartTime < System.currentTimeMillis()) {
                    nextStartTime = AlarmScheduler.calculateNextOccurrence(event.startTime, event.repeat)
                } else if (event.type == "reminder" && nextStartTime < System.currentTimeMillis()) {
                    // If reminder in the past, default to 1 minute from now
                    nextStartTime = System.currentTimeMillis() + 60 * 1000L
                } else if (event.type == "timer") {
                    // Restart timer from current time
                    nextStartTime = System.currentTimeMillis()
                }
                val updated = event.copy(startTime = nextStartTime, status = "active")
                dao.updateEvent(updated)
                scheduleEventScheduler(updated)
                Log.d("ClockManager", "Resumed and rescheduled event: $eventId")
            }
        }
    }

    private fun scheduleEventScheduler(event: ClockEvent) {
        when (event.type) {
            "alarm" -> alarmScheduler.schedule(event)
            "reminder" -> reminderScheduler.schedule(event)
            "timer" -> timerScheduler.schedule(event)
        }
    }

    private fun cancelEventScheduler(event: ClockEvent) {
        when (event.type) {
            "alarm" -> alarmScheduler.cancel(event)
            "reminder" -> reminderScheduler.cancel(event)
            "timer" -> timerScheduler.cancel(event)
        }
    }

    // Stopwatch integration controls
    fun startStopwatch() {
        sendStopwatchIntent(StopwatchService.ACTION_START)
    }

    fun pauseStopwatch() {
        sendStopwatchIntent(StopwatchService.ACTION_PAUSE)
    }

    fun resumeStopwatch() {
        sendStopwatchIntent(StopwatchService.ACTION_RESUME)
    }

    fun stopStopwatch() {
        sendStopwatchIntent(StopwatchService.ACTION_STOP)
    }

    private fun sendStopwatchIntent(action: String) {
        try {
            val intent = Intent(context, StopwatchService::class.java).apply {
                this.action = action
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d("ClockManager", "Sent stopwatch intent action: $action")
        } catch (e: Exception) {
            Log.e("ClockManager", "Failed to send stopwatch intent action: $action", e)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ClockManager? = null

        fun getInstance(context: Context): ClockManager {
            return INSTANCE ?: synchronized(this) {
                val instance = ClockManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
