package com.example.ui.trace

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject

private var _contentCopyIcon: ImageVector? = null
val ContentCopyIcon: ImageVector
    get() {
        if (_contentCopyIcon != null) return _contentCopyIcon!!
        _contentCopyIcon = ImageVector.Builder(
            name = "ContentCopyIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(16f, 1f)
            lineTo(4f, 1f)
            quadTo(3f, 1f, 3f, 2f)
            lineTo(3f, 17f)
            horizontalLineTo(5f)
            lineTo(5f, 3f)
            horizontalLineTo(16f)
            close()
            moveTo(19f, 5f)
            lineTo(8f, 5f)
            quadTo(7f, 5f, 7f, 6f)
            lineTo(7f, 21f)
            quadTo(7f, 22f, 8f, 22f)
            lineTo(19f, 22f)
            quadTo(20f, 22f, 20f, 21f)
            lineTo(20f, 6f)
            quadTo(20f, 5f, 19f, 5f)
            close()
        }.build()
        return _contentCopyIcon!!
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentTraceScreen(
    events: List<AgentTraceEvent>,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var expandedEventIndex by remember { mutableStateOf<Int?>(null) }
    var filterType by remember { mutableStateOf<AgentTraceEvent.EventType?>(null) }

    val filteredEvents = if (filterType != null) {
        events.filter { it.type == filterType }
    } else {
        events
    }

    LaunchedEffect(filteredEvents.size) {
        if (filteredEvents.isNotEmpty()) {
            listState.animateScrollToItem(filteredEvents.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PremiumBgDark)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        "Agent Trace",
                        color = PremiumTextPrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        "${filteredEvents.size} events",
                        color = PremiumTextSecondaryDark,
                        fontSize = 11.sp
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Close", tint = PremiumTextPrimaryDark)
                }
            },
            actions = {
                IconButton(onClick = {
                    if (events.isNotEmpty()) {
                        try {
                            val jsonArray = JSONArray()
                            for (event in events) {
                                val jsonObject = JSONObject()
                                jsonObject.put("timestamp", event.timestamp)
                                jsonObject.put("timeString", event.timeString)
                                jsonObject.put("type", event.type.name)
                                jsonObject.put("typeLabel", event.type.label)
                                jsonObject.put("title", event.title)
                                jsonObject.put("content", event.content)
                                
                                val metadataJson = JSONObject()
                                for ((key, value) in event.metadata) {
                                    metadataJson.put(key, value)
                                }
                                jsonObject.put("metadata", metadataJson)
                                
                                jsonArray.put(jsonObject)
                            }
                            val prettyJson = jsonArray.toString(2)
                            clipboardManager.setText(AnnotatedString(prettyJson))
                            Toast.makeText(context, "Copied all logs as JSON to clipboard!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to copy logs: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "No logs available to copy", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(ContentCopyIcon, "Copy all logs as JSON", tint = PremiumTextPrimaryDark)
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Delete, "Clear", tint = ErrorSemantic)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = PremiumSurfaceDark
            )
        )

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = filterType == null,
                onClick = { filterType = null },
                label = { Text("All", fontSize = 10.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color(0xFF1E293B),
                    selectedContainerColor = LogyBlue
                )
            )
            AgentTraceEvent.EventType.values().forEach { type ->
                FilterChip(
                    selected = filterType == type,
                    onClick = { filterType = if (filterType == type) null else type },
                    label = { Text("${type.icon} ${type.label}", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF1E293B),
                        selectedContainerColor = LogyBlue.copy(alpha = 0.7f)
                    )
                )
            }
        }

        HorizontalDivider(color = PremiumBorderDark)

        // Events list
        if (filteredEvents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("\uD83D\uDCDD", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No trace events yet",
                        color = PremiumTextSecondaryDark,
                        fontSize = 14.sp
                    )
                    Text(
                        "Send a message to see the agent trace",
                        color = PremiumTextSecondaryDark,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = filteredEvents,
                    key = { "${it.timestamp}-${it.type.name}" }
                ) { event ->
                    val eventIndex = filteredEvents.indexOf(event)
                    val isExpanded = expandedEventIndex == eventIndex

                    TraceEventCard(
                        event = event,
                        isExpanded = isExpanded,
                        onClick = {
                            expandedEventIndex = if (isExpanded) null else eventIndex
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TraceEventCard(
    event: AgentTraceEvent,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typeColor = when (event.type) {
        AgentTraceEvent.EventType.SYSTEM_PROMPT -> LogyBlue
        AgentTraceEvent.EventType.USER_MESSAGE -> LogyGreen
        AgentTraceEvent.EventType.API_REQUEST -> LogyPurple
        AgentTraceEvent.EventType.API_RESPONSE -> LogyYellow
        AgentTraceEvent.EventType.TOOL_CALL -> TerminalBlue
        AgentTraceEvent.EventType.TOOL_RESULT -> SuccessSemantic
        AgentTraceEvent.EventType.MEMORY_RETRIEVAL -> TerminalPurple
        AgentTraceEvent.EventType.MEMORY_EXTRACTION -> LogyYellow
        AgentTraceEvent.EventType.MEMORY_CONTEXT -> LogyGreen
        AgentTraceEvent.EventType.ASSISTANT_REPLY -> LogyBlue
        AgentTraceEvent.EventType.ERROR -> ErrorSemantic
        AgentTraceEvent.EventType.PHASE -> PremiumTextSecondaryDark
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF111827)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color indicator
                Box(
                    modifier = Modifier
                        .size(4.dp, 32.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(typeColor)
                )
                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            event.type.icon,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            event.type.label,
                            color = typeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        event.title,
                        color = PremiumTextPrimaryDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    event.timeStringShort,
                    color = PremiumTextSecondaryDark,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = PremiumTextSecondaryDark,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Metadata badges
            if (event.metadata.isNotEmpty() && !isExpanded) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    event.metadata.entries.take(3).forEach { (key, value) ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = typeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "$key: $value",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                color = typeColor,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = PremiumBorderDark)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Metadata
                    if (event.metadata.isNotEmpty()) {
                        event.metadata.forEach { (key, value) ->
                            Row(modifier = Modifier.padding(bottom = 2.dp)) {
                                Text(
                                    "$key: ",
                                    color = PremiumTextSecondaryDark,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    value,
                                    color = typeColor,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Content
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF0B0F17)
                    ) {
                        val scrollState = rememberScrollState()
                        Text(
                            text = event.content.ifEmpty { "(empty)" },
                            modifier = Modifier
                                .padding(8.dp)
                                .horizontalScroll(scrollState)
                                .widthIn(max = 600.dp),
                            color = PremiumTextPrimaryDark.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}
