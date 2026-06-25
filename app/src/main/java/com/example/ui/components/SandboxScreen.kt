package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatMessage
import com.example.ui.theme.LogyBlue
import com.example.ui.theme.LogyGreen
import com.example.ui.theme.LogyYellow
import com.example.ui.theme.PremiumBgDark
import com.example.tools.ToolRegistry
import com.example.tools.BaseTool

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxScreen(
    currentSessionId: String?,
    messages: List<ChatMessage>,
    isThinking: Boolean,
    isExecutingTool: Boolean,
    currentToolName: String?,
    onSendMessage: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var customQuery by remember { mutableStateOf("") }
    val chatListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Filter messages only for current session if available
    val sandboxMessages = remember(messages, currentSessionId) {
        messages.filter { msg ->
            if (msg.role == "model") {
                val textWithoutJson = msg.content.replace(Regex("""\{"tool"\s*:\s*"[^"]+"[^}]*\}""", RegexOption.DOT_MATCHES_ALL), "").trim()
                val hasThought = msg.content.contains("<think>")
                textWithoutJson.isNotEmpty() || hasThought
            } else {
                msg.content.trim().isNotEmpty() || msg.role == "tool"
            }
        }
    }

    // Scroll to latest message on change
    LaunchedEffect(sandboxMessages.size) {
        if (sandboxMessages.isNotEmpty()) {
            chatListState.animateScrollToItem(sandboxMessages.lastIndex)
        }
    }

    // Diagnostic Prompts
    val diagnosticPrompts = remember {
        listOf(
            DiagnosticPrompt(
                title = "Check DateTime & Files",
                description = "Invokes date, time, and list_dir safe tools",
                promptText = "What is today's date and what files are in my current workspace directory?"
            ),
            DiagnosticPrompt(
                title = "Write & Verify Info",
                description = "Triggers write_file (requires approval) and read_file",
                promptText = "Create a text file called user_config.txt with contents 'User workspace: active, region: us-east' and then read the file back to verify it was successfully stored."
            ),
            DiagnosticPrompt(
                title = "Check Task Statuses",
                description = "Triggers local tracker diagnostics tool",
                promptText = "Check the local project tracking database task status and create a new critical task"
            )
        )
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Left Column: Tool Details & Prompt Selector (approx 35% on large screens, scrollable)
        Column(
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Close Sandbox")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Backend Sandbox",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tool Registry Summary
            Text(
                text = "Registered Tool Capabilities",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LogyBlue,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val allTools = ToolRegistry.all()
                    Text(
                        text = "Total Active Tools: ${allTools.size}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Small chips of active tools
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allTools.forEach { t ->
                            val isDangerous = t.riskLevel == com.example.tools.RiskLevel.DANGEROUS
                            val chipBg = if (isDangerous) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                            val chipText = if (isDangerous) LogyYellow else LogyGreen
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(chipBg)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = t.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = chipText
                                )
                            }
                        }
                    }
                }
            }

            // Quick Diagnostic Prompts
            Text(
                text = "Quick Tool-Use Prompts",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LogyBlue,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(diagnosticPrompts) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                customQuery = item.promptText
                                Toast.makeText(context, "Prompt loaded", Toast.LENGTH_SHORT).show()
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = item.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.description,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }

        // Right Column: Simulated Chat Console
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            // Status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isThinking || isExecutingTool) LogyYellow else LogyGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isExecutingTool) "Executing tool: ${currentToolName ?: "unknown"}..." else if (isThinking) "Agent thinking/solving..." else "Operational Status - Consolidated Feed",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chat Messages list
            Box(modifier = Modifier.weight(1f)) {
                if (sandboxMessages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No active test logs inside console",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = chatListState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(sandboxMessages) { msg ->
                            val isUser = msg.role == "user"
                            val isTool = msg.role == "tool"
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                            ) {
                                Card(
                                    modifier = Modifier.widthIn(max = 500.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            isUser -> MaterialTheme.colorScheme.primaryContainer
                                            isTool -> Color(0xFFFFFBEA) // Pale yellow for tool logs
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = when {
                                                isUser -> "User Prompt"
                                                isTool -> "⚙️ Tool Diagnostic log"
                                                else -> "Unified Agent Response"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = when {
                                                isUser -> MaterialTheme.colorScheme.primary
                                                isTool -> LogyYellow
                                                else -> LogyBlue
                                            },
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )

                                        val cleanContent = if (msg.role == "model") {
                                            msg.content.replace(Regex("""\{"tool"\s*:\s*"[^"]+"[^}]*\}""", RegexOption.DOT_MATCHES_ALL), "").trim()
                                        } else msg.content

                                        Text(
                                            text = cleanContent,
                                            fontSize = 13.sp,
                                            fontFamily = if (isTool) FontFamily.Monospace else FontFamily.Default,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Input console
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customQuery,
                    onValueChange = { customQuery = it },
                    placeholder = { Text("Enter prompt requiring tool use...", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (customQuery.trim().isNotEmpty()) {
                            onSendMessage(customQuery)
                            customQuery = ""
                        }
                    })
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (customQuery.trim().isNotEmpty()) {
                            onSendMessage(customQuery)
                            customQuery = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

data class DiagnosticPrompt(
    val title: String,
    val description: String,
    val promptText: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}
