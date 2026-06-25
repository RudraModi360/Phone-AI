package com.example.clock.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.clock.database.ClockDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra("EVENT_ID") ?: ""
        val type = intent.getStringExtra("EVENT_TYPE") ?: "reminder"
        val title = intent.getStringExtra("EVENT_TITLE") ?: "Scheduled Task"

        Log.d("NotificationReceiver", "Received broadcast. Type: $type, EventId: $eventId, Title: $title")

        if (eventId.isEmpty()) return

        // Mark the event as completed in DB and fetch the actual start time
        CoroutineScope(Dispatchers.IO).launch {
            val db = ClockDatabase.getDatabase(context)
            val dao = db.clockDao()
            val event = dao.getEventById(eventId)
            
            val finalTitle = event?.title ?: title
            val finalTime = event?.startTime ?: System.currentTimeMillis()

            // Post the notification alert with resolved info
            postNotification(context, eventId, type, finalTitle, finalTime)

            if (event != null) {
                dao.updateEvent(event.copy(status = "completed"))
                Log.d("NotificationReceiver", "Event $eventId state updated to completed")
            }
        }
    }

    private fun postNotification(context: Context, eventId: String, type: String, title: String, startTime: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "clock_time_notifications_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Time Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Triggers immediate alerts for reminders and completed timers"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_TAB", "clock")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            tapIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val formattedTime = timeFormat.format(java.util.Date(startTime))

        val text = if (type == "timer") {
            "Timer completed at $formattedTime"
        } else {
            "Time created: $formattedTime"
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(if (type == "timer") android.R.drawable.ic_media_play else android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setShowWhen(true)
            .setWhen(startTime)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(if (type == "timer") NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_REMINDER)

        notificationManager.notify(eventId.hashCode() + 10, builder.build())
        Log.d("NotificationReceiver", "Notification posted for $eventId")
    }
}
