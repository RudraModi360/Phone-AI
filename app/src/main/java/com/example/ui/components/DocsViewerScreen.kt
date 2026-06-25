package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LogyBlue
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocsViewerScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    // Available Docs List
    val docsList = remember {
        listOf(
            DocItem("architecture.md", "Architecture Overview", "Core subsystem flow and layout structure", Icons.Default.Home),
            DocItem("reasoning.md", "5-Level Reasoning Specs", "Thinking token budgets & auto-escalation definitions", Icons.Default.Build),
            DocItem("tracker.md", "Task Tracking Engine", "State flows, Priority criteria & local operations", Icons.Default.List),
            DocItem("planner.md", "Plan Stage Rules", "Workflow step validations & approval states", Icons.Default.List),
            DocItem("tools.md", "Subsystem Agent Tools", "Deduplication loops & execution rules", Icons.Default.Warning),
            DocItem("kotlin_api.md", "Kotlin Integration Specs", "Ktor configurations & websocket progress streams", Icons.Default.Send),
            DocItem("quick_ref.md", "Quick reference card", "Status icons, API requests, and shorthand charts", Icons.Default.Info)
        )
    }

    var selectedDoc by remember { mutableStateOf(docsList.first()) }
    var loadedDocContent by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showFullOutline by remember { mutableStateOf(false) }

    // Load file from asset
    LaunchedEffect(selectedDoc) {
        try {
            context.assets.open("docs/${selectedDoc.fileName}").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    loadedDocContent = reader.readText()
                }
            }
        } catch (e: Exception) {
            loadedDocContent = "Error loading document: ${e.localizedMessage}\n\nFallback content string:\nUnable to find asset assets/docs/${selectedDoc.fileName}"
        }
    }

    val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.PremiumBgDark

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Left Column: Navigation Rail / Mini Sidebar for tablet/large-screens representation
        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                .padding(vertical = 14.dp, horizontal = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Architecture",
                        tint = LogyBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mobile Docs Hub",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close Docs Viewer")
                }
            }

            Text(
                text = "DOCUMENTATION SECTS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 8.dp, start = 6.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(docsList) { doc ->
                    val isSelected = doc == selectedDoc
                    Card(
                        onClick = { selectedDoc = doc },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) LogyBlue.copy(alpha = 0.08f) else Color.Transparent
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) LogyBlue else Color.Transparent
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        if (isSelected) LogyBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.background,
                                        shape = RoundedCornerShape(6.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = doc.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) LogyBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = doc.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) LogyBlue else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = doc.subtitle,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(12.dp))

            // Integration Guide Sync Note
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                ),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Developer Specs",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = LogyBlue
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Directly synchronized from active backend architecture specifications inside .logicore schema definitions.",
                        fontSize = 9.sp,
                        lineHeight = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Right Column: Live Doc Reader & Formatted Monospace Code Highlight viewer
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Live Doc Header Bar with Search Query input for rapid filtering
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = selectedDoc.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "/docs/mobile-backend/${selectedDoc.fileName}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter guide content...", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                }
                            }
                        } else null,
                        modifier = Modifier
                            .width(180.dp)
                            .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LogyBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )

                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(loadedDocContent))
                            Toast.makeText(context, "Full Markdown copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LogyBlue),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Raw", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // Document Content Renderer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                if (loadedDocContent.trim().isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LogyBlue)
                    }
                } else {
                    val contentLines = remember(loadedDocContent, searchQuery) {
                        val allLines = loadedDocContent.split("\n")
                        if (searchQuery.trim().isEmpty()) {
                            allLines
                        } else {
                            // Filter lines keeping outline structure context
                            allLines.filter { line ->
                                line.contains(searchQuery, ignoreCase = true) || line.startsWith("#")
                            }
                        }
                    }

                    DocMarkdownRenderer(
                        lines = contentLines,
                        onCopyCode = { code ->
                            clipboardManager.setText(AnnotatedString(code))
                            Toast.makeText(context, "Code snippet copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        isDark = isDark
                    )
                }
            }
        }
    }
}

@Composable
fun DocMarkdownRenderer(
    lines: List<String>,
    onCopyCode: (String) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Parse blocks of code manually to draw them in monospace panels
    val segments = remember(lines) {
        val list = mutableListOf<DocSegment>()
        var inCodeBlock = false
        val currentCodeBlock = StringBuilder()
        var currentLanguage = ""

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    list.add(DocSegment.Code(currentCodeBlock.toString(), currentLanguage))
                    currentCodeBlock.clear()
                    inCodeBlock = false
                } else {
                    currentLanguage = trimmed.removePrefix("```").trim()
                    inCodeBlock = true
                }
            } else {
                if (inCodeBlock) {
                    currentCodeBlock.append(line).append("\n")
                } else {
                    // Normal segment line type classification
                    when {
                        line.startsWith("# ") -> list.add(DocSegment.Header(line.removePrefix("# ").trim(), 1))
                        line.startsWith("## ") -> list.add(DocSegment.Header(line.removePrefix("## ").trim(), 2))
                        line.startsWith("### ") -> list.add(DocSegment.Header(line.removePrefix("### ").trim(), 3))
                        line.startsWith("|") && line.contains("-") -> { /* skip markdown table layout division row */ }
                        line.startsWith("|") -> list.add(DocSegment.TableLine(line))
                        line.startsWith("- ") || line.startsWith("* ") -> list.add(DocSegment.Bullet(line.substring(2).trim()))
                        else -> {
                            if (line.isNotEmpty()) {
                                list.add(DocSegment.Paragraph(line))
                            }
                        }
                    }
                }
            }
        }
        // Fallback close remaining
        if (inCodeBlock) {
            list.add(DocSegment.Code(currentCodeBlock.toString(), currentLanguage))
        }
        list
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is DocSegment.Header -> {
                    val fontSize = when (segment.level) {
                        1 -> 24.sp
                        2 -> 18.sp
                        else -> 14.sp
                    }
                    val topPadding = if (segment.level == 1) 12.dp else 6.dp
                    Text(
                        text = segment.text,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = if (segment.level == 1) LogyBlue else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = topPadding, bottom = 4.dp)
                    )
                }

                is DocSegment.Paragraph -> {
                    Text(
                        text = segment.text,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }

                is DocSegment.Bullet -> {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = LogyBlue,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = segment.text,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                        )
                    }
                }

                is DocSegment.Code -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = segment.language.uppercase().ifEmpty { "CODE" },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
                                )
                                Row(
                                    modifier = Modifier
                                        .clickable { onCopyCode(segment.code) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit, // Mimicking copy action
                                        contentDescription = "Copy",
                                        tint = LogyBlue,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "COPY",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LogyBlue
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = segment.code.trimEnd(),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    color = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
                                )
                            }
                        }
                    }
                }

                is DocSegment.TableLine -> {
                    // Minimalistic parsed columns list
                    val cols = segment.line.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isDark) Color(0xFF1E293B).copy(alpha = 0.3f) else Color(0xFFF1F5F9).copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        cols.forEachIndexed { idx, colText ->
                            Text(
                                text = colText,
                                fontSize = 12.sp,
                                fontWeight = if (idx == 0) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(30.dp))
    }
}

sealed class DocSegment {
    data class Header(val text: String, val level: Int) : DocSegment()
    data class Paragraph(val text: String) : DocSegment()
    data class Bullet(val text: String) : DocSegment()
    data class Code(val code: String, val language: String) : DocSegment()
    data class TableLine(val line: String) : DocSegment()
}

data class DocItem(
    val fileName: String,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
