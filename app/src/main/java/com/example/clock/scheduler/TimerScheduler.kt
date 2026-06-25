package com.example.clock.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.clock.ClockEvent
import com.example.clock.receiver.NotificationReceiver

class TimerScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(event: ClockEvent) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("EVENT_ID", event.id)
            putExtra("EVENT_TYPE", "timer")
            putExtra("EVENT_TITLE", event.title)
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

        // For a timer, the target time is start time + duration
        val triggerTime = event.startTime + event.duration

        if (triggerTime < System.currentTimeMillis()) {
            Log.d("TimerScheduler", "Timer target is in the past, triggering notification now.")
            context.sendBroadcast(intent)
            return
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
            Log.d("TimerScheduler", "Scheduled timer completion for event ${event.id} at $triggerTime")
        } catch (e: SecurityException) {
            Log.e("TimerScheduler", "Security exception. Falling back to set()", e)
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    fun cancel(event: ClockEvent) {
        val intent = Intent(context, NotificationReceiver::class.java)
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
            Log.d("TimerScheduler", "Cancelled timer ${event.id}")
        }
    }
}
