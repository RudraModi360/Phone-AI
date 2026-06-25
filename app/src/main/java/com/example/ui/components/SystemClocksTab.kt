package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clock.ClockEvent
import com.example.clock.ClockManager
import com.example.clock.database.ClockDatabase
import com.example.clock.service.StopwatchService
import com.example.ui.theme.LogyBlue
import com.example.ui.theme.LogyGreen
import com.example.ui.theme.LogyRed
import com.example.ui.theme.LogyYellow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SystemClocksTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val clockManager = remember { ClockManager.getInstance(context) }
    val clockDatabase = remember { ClockDatabase.getDatabase(context) }
    val clockDao = remember { clockDatabase.clockDao() }
    
    // Reactive flow list of events
    val clockEvents by clockDao.getAllEventsFlow().collectAsState(initial = emptyList())
    
    // Stopwatch details
    val stopwatchTime by StopwatchService.elapsedTimeFlow.collectAsState(initial = 0L)
    val stopwatchStatus by StopwatchService.stateFlow.collectAsState(initial = "idle")

    // Creator State variables
    var activeCreatorType by remember { mutableStateOf("alarm") } // "alarm", "reminder", "timer"
    
    // Alarm fields
    var alarmHourStr by remember { mutableStateOf("") }
    var alarmMinuteStr by remember { mutableStateOf("") }
    var alarmMessage by remember { mutableStateOf("") }
    var alarmRepeat by remember { mutableStateOf("once") } // "once", "daily", "weekly"
    
    // Timer fields
    var timerSecondsStr by remember { mutableStateOf("") }
    var timerTitle by remember { mutableStateOf("") }

    // Reminder fields
    var reminderMinutesStr by remember { mutableStateOf("") }
    var reminderMessage by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // --- SECTION 1: STOPWATCH CONTROLS ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = LogyBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Active Background Stopwatch", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    val statusColor = when (stopwatchStatus) {
                        "running" -> LogyGreen
                        "paused" -> LogyYellow
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = stopwatchStatus.uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = statusColor,
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stopwatch Display Time (Format: MM:SS.S)
                val minutes = (stopwatchTime / 1000) / 60
                val seconds = (stopwatchTime / 1000) % 60
                val tenths = (stopwatchTime % 1000) / 100
                Text(
                    text = String.format("%02d:%02d.%d", minutes, seconds, tenths),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 36.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (stopwatchStatus == "idle") {
                        Button(
                            onClick = { clockManager.startStopwatch() },
                            colors = ButtonDefaults.buttonColors(containerColor = LogyBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start")
                        }
                    } else {
                        if (stopwatchStatus == "running") {
                            Button(
                                onClick = { clockManager.pauseStopwatch() },
                                colors = ButtonDefaults.buttonColors(containerColor = LogyYellow),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pause", color = Color.Black)
                            }
                        } else {
                            Button(
                                onClick = { clockManager.resumeStopwatch() },
                                colors = ButtonDefaults.buttonColors(containerColor = LogyGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Resume")
                            }
                        }

                        Button(
                            onClick = { clockManager.stopStopwatch() },
                            colors = ButtonDefaults.buttonColors(containerColor = LogyRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- SECTION 2: ADD TIME EVENT CREATOR ---
        Text("Create Persistent Actions", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val creatorTypes = listOf("alarm" to "Alarm", "timer" to "Timer", "reminder" to "Reminder")
            creatorTypes.forEach { (type, label) ->
                val selected = activeCreatorType == type
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) LogyBlue else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { activeCreatorType = type }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Creators Forms
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (activeCreatorType) {
                    "alarm" -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = alarmHourStr,
                                onValueChange = { alarmHourStr = it.take(2) },
                                label = { Text("Hour (0-23)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                            )
                            OutlinedTextField(
                                value = alarmMinuteStr,
                                onValueChange = { alarmMinuteStr = it.take(2) },
                                label = { Text("Min (0-59)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = alarmMessage,
                            onValueChange = { alarmMessage = it },
                            label = { Text("Message / Title") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Repeat:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            val repeats = listOf("once", "daily", "weekly")
                            repeats.forEach { r ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { alarmRepeat = r }
                                ) {
                                    RadioButton(selected = alarmRepeat == r, onClick = { alarmRepeat = r })
                                    Text(r.replaceFirstChar { it.uppercase() }, fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val h = alarmHourStr.toIntOrNull()
                                val m = alarmMinuteStr.toIntOrNull()
                                if (h == null || h !in 0..23 || m == null || m !in 0..59) {
                                    Toast.makeText(context, "Please enter a valid Hour (0-23) and Minute (0-59)", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                scope.launch(Dispatchers.IO) {
                                    val now = Calendar.getInstance()
                                    val target = Calendar.getInstance().apply {
                                        set(Calendar.HOUR_OF_DAY, h)
                                        set(Calendar.MINUTE, m)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                    if (target.before(now)) {
                                        target.add(Calendar.DAY_OF_YEAR, 1)
                                    }
                                    clockManager.createAlarm(
                                        title = alarmMessage.ifBlank { "Alarm" },
                                        startTime = target.timeInMillis,
                                        repeat = alarmRepeat,
                                        ringtone = "default"
                                    )
                                    // Clear input
                                    alarmHourStr = ""
                                    alarmMinuteStr = ""
                                    alarmMessage = ""
                                }
                                Toast.makeText(context, "Alarm created in background successfully!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LogyBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Queue Background Alarm", fontWeight = FontWeight.Bold)
                        }
                    }
                    "timer" -> {
                        OutlinedTextField(
                            value = timerSecondsStr,
                            onValueChange = { timerSecondsStr = it },
                            label = { Text("Duration in Seconds") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = timerTitle,
                            onValueChange = { timerTitle = it },
                            label = { Text("Timer Title") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val seconds = timerSecondsStr.toLongOrNull()
                                if (seconds == null || seconds <= 0) {
                                    Toast.makeText(context, "Please enter valid countdown seconds", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                scope.launch(Dispatchers.IO) {
                                    clockManager.createTimer(
                                        title = timerTitle.ifBlank { "Countdown" },
                                        durationMs = seconds * 1000L
                                    )
                                    timerSecondsStr = ""
                                    timerTitle = ""
                                }
                                Toast.makeText(context, "Timer set in background!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LogyBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Background Timer", fontWeight = FontWeight.Bold)
                        }
                    }
                    "reminder" -> {
                        OutlinedTextField(
                            value = reminderMinutesStr,
                            onValueChange = { reminderMinutesStr = it },
                            label = { Text("Fires in (Minutes from now)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = reminderMessage,
                            onValueChange = { reminderMessage = it },
                            label = { Text("Reminder Task Name") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val minutes = reminderMinutesStr.toLongOrNull()
                                if (minutes == null || minutes <= 0) {
                                    Toast.makeText(context, "Please enter minutes offset", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val targetTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
                                scope.launch(Dispatchers.IO) {
                                    clockManager.createReminder(
                                        title = reminderMessage.ifBlank { "Reminder" },
                                        startTime = targetTime
                                    )
                                    reminderMinutesStr = ""
                                    reminderMessage = ""
                                }
                                Toast.makeText(context, "Task reminder queued!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LogyBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Schedule Background Reminder", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- SECTION 3: REAL-TIME EVENTS LIST ---
        Text("Your Tracked Background Tasks (${clockEvents.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))

        if (clockEvents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No active background clock events scheduled.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(clockEvents, key = { it.id }) { event ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = when (event.type) {
                                "alarm" -> Icons.Default.AccessAlarm
                                "timer" -> Icons.Default.HourglassEmpty
                                else -> Icons.Default.Notifications
                            }
                            val iconColor = when (event.type) {
                                "alarm" -> LogyYellow
                                "timer" -> LogyBlue
                                else -> LogyGreen
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(iconColor.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(event.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                val desc = when (event.type) {
                                    "alarm" -> {
                                        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                                        "Rings at ${sdf.format(Date(event.startTime))} (${event.repeat})"
                                    }
                                    "timer" -> {
                                        "Duration: ${event.duration / 1000}s"
                                    }
                                    else -> {
                                        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                                        "Fires at ${sdf.format(Date(event.startTime))}"
                                    }
                                }
                                Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            
                            val active = event.status == "active"
                            Text(
                                text = event.status.uppercase(),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                color = if (active) LogyGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(end = 8.dp)
                            )

                            IconButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        clockManager.toggleEventStatus(event.id)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (active) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Toggle status",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        clockManager.deleteEvent(event.id)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete event",
                                    tint = LogyRed.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
