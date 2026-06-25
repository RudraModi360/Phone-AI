package com.example.clock

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clock_events")
data class ClockEvent(
    @PrimaryKey val id: String,
    val type: String, // "alarm", "reminder", "timer"
    val title: String,
    val startTime: Long, // timestamp when it starts or was set
    val duration: Long, // duration for timer/reminder (in milliseconds)
    val repeat: String, // "once", "daily", "weekly", "Mon,Tue,Wed..."
    val ringtone: String, // ringtone identifier or uri, e.g. "default"
    val status: String // "active", "completed", "paused", etc.
)
