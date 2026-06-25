package com.example.clock.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.clock.ClockEvent
import com.example.clock.database.ClockDatabase
import com.example.clock.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AlarmPlayer {
    private var ringtone: Ringtone? = null

    fun startPlaying(context: Context) {
        try {
            stopPlaying()
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                }
                play()
            }
            Log.d("AlarmPlayer", "Alarm ringtone started playing")
        } catch (e: Exception) {
            Log.e("AlarmPlayer", "Error playing alarm ringtone", e)
        }
    }

    fun stopPlaying() {
        try {
            ringtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
            ringtone = null
            Log.d("AlarmPlayer", "Alarm ringtone stopped")
        } catch (e: Exception) {
            Log.e("AlarmPlayer", "Error stopping alarm ringtone", e)
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val eventId = intent.getStringExtra("EVENT_ID") ?: ""
        val title = intent.getStringExtra("EVENT_TITLE") ?: "Alarm"

        Log.d("AlarmReceiver", "Received intent action: $action, eventId: $eventId")

        if (action == "ACTION_DISMISS_ALARM") {
            AlarmPlayer.stopPlaying()
            cancelNotification(context, eventId.hashCode())
            updateEventState(context, eventId, "dismissed")
            return
        }

        if (eventId.isEmpty()) return

        // Trigger the alarm actions: Sound and Notify
        AlarmPlayer.startPlaying(context)
        showAlarmNotification(context, eventId, title)

        // Reschedule recurring alarms or update status
        updateEventState(context, eventId, "active")
    }

    private fun showAlarmNotification(context: Context, eventId: String, title: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "clock_alarms_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alarms & Timers Alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Fullscreen urgency updates and continuous background alerts."
                enableLights(true)
                enableVibration(true)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = "ACTION_DISMISS_ALARM"
            putExtra("EVENT_ID", eventId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.hashCode() + 1,
            dismissIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_TAB", "clock")
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            fullScreenIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("Your scheduled alarm is ringing!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)

        notificationManager.notify(eventId.hashCode(), builder.build())
    }

    private fun cancelNotification(context: Context, id: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(id)
    }

    private fun updateEventState(context: Context, eventId: String, nextState: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = ClockDatabase.getDatabase(context)
            val dao = db.clockDao()
            val event = dao.getEventById(eventId) ?: return@launch

            if (nextState == "dismissed") {
                if (event.repeat == "once") {
                    dao.updateEvent(event.copy(status = "completed"))
                } else if (event.repeat.isNotEmpty()) {
                    // Reschedule for next repeating slot and keep active
                    val nextTime = AlarmScheduler.calculateNextOccurrence(System.currentTimeMillis(), event.repeat)
                    val updatedEvent = event.copy(startTime = nextTime, status = "active")
                    dao.updateEvent(updatedEvent)
                    AlarmScheduler(context).schedule(updatedEvent)
                }
            } else if (nextState == "active") {
                if (event.repeat == "once") {
                    // Non-recurring was just triggered once, mark completed
                    dao.updateEvent(event.copy(status = "completed"))
                } else if (event.repeat.isNotEmpty()) {
                    // Recurring, so calculate and schedule next trigger
                    val nextTime = AlarmScheduler.calculateNextOccurrence(System.currentTimeMillis(), event.repeat)
                    val updatedEvent = event.copy(startTime = nextTime, status = "active")
                    dao.updateEvent(updatedEvent)
                    AlarmScheduler(context).schedule(updatedEvent)
                }
            }
        }
    }
}
