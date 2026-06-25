package com.example.clock.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StopwatchService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var tickerJob: Job? = null
    
    private var baseTime: Long = 0L
    private var accumulatedTime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        Log.d("StopwatchService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        Log.d("StopwatchService", "Received action: $action")

        when (action) {
            ACTION_START -> startStopwatch()
            ACTION_PAUSE -> pauseStopwatch()
            ACTION_RESUME -> resumeStopwatch()
            ACTION_STOP -> stopStopwatch()
        }

        return START_NOT_STICKY
    }

    private fun startStopwatch() {
        _stateFlow.value = "running"
        baseTime = System.currentTimeMillis()
        accumulatedTime = 0L
        _elapsedTimeFlow.value = 0L
        
        startForeground(NOTIFICATION_ID, createNotification("00:00.0"))
        startTicker()
    }

    private fun pauseStopwatch() {
        if (_stateFlow.value != "running") return
        _stateFlow.value = "paused"
        tickerJob?.cancel()
        accumulatedTime += System.currentTimeMillis() - baseTime
        _elapsedTimeFlow.value = accumulatedTime
        
        updateNotification(formatTime(accumulatedTime))
    }

    private fun resumeStopwatch() {
        if (_stateFlow.value != "paused") return
        _stateFlow.value = "running"
        baseTime = System.currentTimeMillis()
        
        startTicker()
    }

    private fun stopStopwatch() {
        _stateFlow.value = "idle"
        tickerJob?.cancel()
        _elapsedTimeFlow.value = 0L
        accumulatedTime = 0L
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        stopSelf()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            var lastNotificationSec = -1L
            while (isActive) {
                val elapsed = accumulatedTime + (System.currentTimeMillis() - baseTime)
                _elapsedTimeFlow.value = elapsed
                
                val currentSec = elapsed / 1000
                if (currentSec != lastNotificationSec) {
                    updateNotification(formatTime(elapsed))
                    lastNotificationSec = currentSec
                }
                
                delay(50) // Tick frequently for smooth millisecond resolution in the UI
            }
        }
    }

    private fun updateNotification(formattedTime: String) {
        val m = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        m.notify(NOTIFICATION_ID, createNotification(formattedTime))
    }

    private fun createNotification(formattedTime: String): android.app.Notification {
        val channelId = "clock_stopwatch_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Stopwatch State",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time ticking for the background stopwatch."
            }
            val m = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            m.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_TAB", "clock")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            201,
            openIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Stopwatch is active")
            .setContentText("Elapsed time: $formattedTime")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun formatTime(millis: Long): String {
        val minutes = (millis / 1000) / 60
        val seconds = (millis / 1000) % 60
        val tenths = (millis % 1000) / 100
        return String.format("%02d:%02d.%d", minutes, seconds, tenths)
    }

    override fun onDestroy() {
        super.onDestroy()
        tickerJob?.cancel()
        serviceScope.cancel()
        Log.d("StopwatchService", "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 2026
        
        const val ACTION_START = "com.example.clock.service.STOPWATCH_START"
        const val ACTION_PAUSE = "com.example.clock.service.STOPWATCH_PAUSE"
        const val ACTION_RESUME = "com.example.clock.service.STOPWATCH_RESUME"
        const val ACTION_STOP = "com.example.clock.service.STOPWATCH_STOP"

        private val _elapsedTimeFlow = MutableStateFlow(0L)
        val elapsedTimeFlow: StateFlow<Long> = _elapsedTimeFlow.asStateFlow()

        private val _stateFlow = MutableStateFlow("idle") // "idle", "running", "paused"
        val stateFlow: StateFlow<String> = _stateFlow.asStateFlow()
    }
}
