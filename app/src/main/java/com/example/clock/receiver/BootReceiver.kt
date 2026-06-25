package com.example.clock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.clock.database.ClockDatabase
import com.example.clock.scheduler.AlarmScheduler
import com.example.clock.scheduler.ReminderScheduler
import com.example.clock.scheduler.TimerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d("BootReceiver", "Device boot completed, restoring active clock alarms and reminders...")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = ClockDatabase.getDatabase(context)
                val dao = db.clockDao()
                val activeEvents = dao.getAllEvents().filter { it.status == "active" }

                val alarmScheduler = AlarmScheduler(context)
                val reminderScheduler = ReminderScheduler(context)
                val timerScheduler = TimerScheduler(context)

                for (event in activeEvents) {
                    when (event.type) {
                        "alarm" -> {
                            alarmScheduler.schedule(event)
                            Log.d("BootReceiver", "Restored alarm: ${event.id} - ${event.title}")
                        }
                        "reminder" -> {
                            if (event.startTime > System.currentTimeMillis()) {
                                reminderScheduler.schedule(event)
                                Log.d("BootReceiver", "Restored reminder: ${event.id} - ${event.title}")
                            } else {
                                dao.updateEvent(event.copy(status = "completed"))
                            }
                        }
                        "timer" -> {
                            val triggerTime = event.startTime + event.duration
                            if (triggerTime > System.currentTimeMillis()) {
                                timerScheduler.schedule(event)
                                Log.d("BootReceiver", "Restored timer: ${event.id} - ${event.title}")
                            } else {
                                dao.updateEvent(event.copy(status = "completed"))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to restore clock events on boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
