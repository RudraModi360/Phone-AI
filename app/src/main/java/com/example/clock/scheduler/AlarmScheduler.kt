package com.example.clock.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.clock.ClockEvent
import com.example.clock.receiver.AlarmReceiver
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(event: ClockEvent) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("EVENT_ID", event.id)
            putExtra("EVENT_TITLE", event.title)
            putExtra("EVENT_RINGTONE", event.ringtone)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.id.hashCode(),
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        var triggerTime = event.startTime

        // If startTime is in the past, adjust if repeating, or keep for immediate trigger
        if (triggerTime < System.currentTimeMillis()) {
            if (event.repeat != "once" && event.repeat.isNotEmpty()) {
                triggerTime = calculateNextOccurrence(event.startTime, event.repeat)
            } else {
                // If it's single alarm and in the past, don't schedule or schedule for immediately
                Log.d("AlarmScheduler", "Alarm ${event.id} is in the past and non-repeating. Skipping.")
                return
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d("AlarmScheduler", "Scheduled alarm ${event.id} at $triggerTime (${java.util.Date(triggerTime)})")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "SecurityException scheduling exact alarm, falling back to set()", e)
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    fun cancel(event: ClockEvent) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.id.hashCode(),
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_NO_CREATE
            }
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("AlarmScheduler", "Cancelled alarm ${event.id}")
        }
    }

    companion object {
        fun calculateNextOccurrence(originalTime: Long, repeatPattern: String): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                timeInMillis = originalTime
            }

            if (repeatPattern == "daily") {
                while (target.before(now)) {
                    target.add(Calendar.DAY_OF_YEAR, 1)
                }
                return target.timeInMillis
            }

            if (repeatPattern == "weekly") {
                while (target.before(now)) {
                    target.add(Calendar.WEEK_OF_YEAR, 1)
                }
                return target.timeInMillis
            }

            // Custom days pattern e.g. "Mon,Tue,Wed..."
            val days = repeatPattern.split(",").map { it.trim().lowercase() }
            if (days.isNotEmpty()) {
                val dayMapping = mapOf(
                    "sun" to Calendar.SUNDAY,
                    "mon" to Calendar.MONDAY,
                    "tue" to Calendar.TUESDAY,
                    "wed" to Calendar.WEDNESDAY,
                    "thu" to Calendar.THURSDAY,
                    "fri" to Calendar.FRIDAY,
                    "sat" to Calendar.SATURDAY
                )

                val targetDays = days.mapNotNull { dayMapping[it] }
                if (targetDays.isNotEmpty()) {
                    // Try to find the nearest target day in the future (or today if target is in the future)
                    val candidate = Calendar.getInstance().apply {
                        timeInMillis = originalTime
                    }
                    
                    // Increment day by day until we match one of targetDays and it's in the future
                    var matches = false
                    var attempts = 0
                    while (attempts < 14) {
                        val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
                        if (targetDays.contains(dayOfWeek) && candidate.after(now)) {
                            matches = true
                            break
                        }
                        candidate.add(Calendar.DAY_OF_YEAR, 1)
                        attempts++
                    }
                    if (matches) {
                        return candidate.timeInMillis
                    }
                }
            }

            // Fallback: 1 day later
            return originalTime + 24 * 60 * 60 * 1000L
        }
    }
}
