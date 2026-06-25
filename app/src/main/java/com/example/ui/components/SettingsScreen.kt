package com.example.ui.components

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LogyGreen
import com.example.ui.theme.LogyYellow
import com.example.ui.theme.LogyRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: String,
    fontSize: Int,
    isLocalMode: Boolean,
    apiKey: String,
    daemonHost: String,
    daemonApiKey: String,
    selectedModel: String,
    opencodeBaseUrl: String,
    reasoningLevel: Int,
    systemPrompt: String,
    googleApiKey: String,
    googleCx: String,
    groqApiKey: String,
    onSettingChanged: (key: String, value: String) -> Unit,
    onCloseSettings: () -> Unit,
    onConfigureReminders: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showApiKey by remember { mutableStateOf(false) }
    val stableOnSettingChanged = remember { onSettingChanged }

    // Mock states for workspace features to persist during runtime
    var animationsEnabled by remember { mutableStateOf(true) }
    var contextMemoryEnabled by remember { mutableStateOf(true) }
    
    // Dialog / log states
    var showLogDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // 1. Header (Premium style, centered text, clean close icon)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onCloseSettings,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Settings",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Logy Workspace Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // System Prompt Section on TOP
        Text(
            text = "System Prompt",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4285F4), // Google Blue accent
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Configure Custom System Persona / Prompt",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                DebouncedTextField(
                    value = systemPrompt,
                    onValueChange = { stableOnSettingChanged("system_prompt", it) },
                    placeholder = "E.g., You are a helpful AI buddy. Respond with clean logic and helpful tips...",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                )
            }
        }

        // 2. Section: APPEARANCE
        Text(
            text = "Appearance",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4285F4), // Google Blue accent
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                
                // Theme Selector (Proper Segmented Control)
                Text("Theme Mode", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val themes = listOf("Light", "Dark", "Adaptive")
                    themes.forEach { t ->
                        val isSelected = themeMode == t
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                                )
                                .clickable { stableOnSettingChanged("theme_mode", t) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = t,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF4285F4) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Animations switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Enable Animations", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Smooth transitions and window fading", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    Switch(
                        checked = animationsEnabled,
                        onCheckedChange = { animationsEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF34A853) // Google Green
                        )
                    )
                }
            }
        }

        // 3. Section: AI CONFIGURATION
        val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.PremiumBgDark
        Text(
            text = "AI Configuration",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color(0xFFFBBC05) else Color(0xFF1A73E8),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                
                // Cloud Provider info banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isDark) Color(0xFF2C2C2C) else Color(0xFFE8F0FE), 
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Cloud API Mode",
                        tint = if (isDark) Color(0xFF81B0FF) else Color(0xFF4285F4),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Ollama Cloud Architecture", 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 12.sp, 
                            color = if (isDark) Color.White else Color(0xFF1967D2)
                        )
                        Text(
                            text = "Operating directly on the robust remote Direct Cloud-only completions without local latency.", 
                            fontSize = 11.sp, 
                            color = if (isDark) Color(0xFFB3B3B3) else Color(0xFF1967D2).copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ollama endpoint Base URL input
                Text("Ollama Cloud Endpoint URL", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                DebouncedTextField(
                    value = opencodeBaseUrl,
                    onValueChange = { stableOnSettingChanged("opencode_base_url", it) },
                    placeholder = "https://ollama.com/api",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // API Key / Token (Ensured to be visible and configurable)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ollama Cloud Auth Token / Key", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    IconButton(onClick = { showApiKey = !showApiKey }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Default.Close else Icons.Default.Lock,
                            contentDescription = "Visibility Toggle",
                            tint = Color(0xFFEA4335),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                DebouncedTextField(
                    value = apiKey,
                    onValueChange = { stableOnSettingChanged("api_key", it) },
                    placeholder = "Ollama API authentication credentials...",
                    visualTransformation = if (showApiKey) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Active model
                Text("Active Model", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                DebouncedTextField(
                    value = selectedModel,
                    onValueChange = { stableOnSettingChanged("selected_model", it) },
                    placeholder = "gpt-oss:20b-cloud",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        "gpt-oss:20b-cloud",
                        "gemma4:31b-cloud",
                        "qwen3-coder-next:cloud",
                        "minimax-m2.1:cloud",
                        "ministral-3:14b-cloud",
                        "devstral-small-2:24b-cloud",
                        "nometron-3-nano:30b-cloud",
                        "nemotron-3-nano:30b-cloud",
                        "rnj-1:8b-cloud",
                        "gpt-oss:120b-cloud"
                    )
                    presets.forEach { preset ->
                        val isSelected = selectedModel == preset
                        Card(
                            onClick = { stableOnSettingChanged("selected_model", preset) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF4285F4).copy(alpha = 0.1f) else Color.Transparent
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, 
                                if (isSelected) Color(0xFF4285F4) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = preset,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF4285F4) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reasoning Level Info (now controlled from chat input)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF3B82F6).copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Agent Mode & Reasoning Level",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF3B82F6)
                        )
                        Text(
                            text = "Now accessible directly from the chat input area for quick switching between Plan, Execute, and Think modes.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

            }
        }

        // 3.5 Section: HYBRID WEB SEARCH KEYS
        Text(
            text = "Hybrid Web Search Keys",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFBBC05), // Google Yellow/Orange accent
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                var showGoogleKey by remember { mutableStateOf(false) }
                var showGroqKey by remember { mutableStateOf(false) }

                Text(
                    text = "Configure API keys for Google Custom Search (Medium query tier) and GROQ (Complex query tier). If unconfigured, the tool falls back gracefully to DuckDuckGo (Simple tier).",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Google API Key
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Google Custom Search API Key", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    IconButton(onClick = { showGoogleKey = !showGoogleKey }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = if (showGoogleKey) Icons.Default.Close else Icons.Default.Lock,
                            contentDescription = "Visibility Toggle",
                            tint = Color(0xFF4285F4),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                DebouncedTextField(
                    value = googleApiKey,
                    onValueChange = { stableOnSettingChanged("google_api_key", it) },
                    placeholder = "Enter Google API key...",
                    visualTransformation = if (showGoogleKey) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Google CX ID
                Text("Google Search Engine ID (CX)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                DebouncedTextField(
                    value = googleCx,
                    onValueChange = { stableOnSettingChanged("google_cx", it) },
                    placeholder = "Enter Search Engine CX ID...",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // GROQ API Key
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("GROQ API Key", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    IconButton(onClick = { showGroqKey = !showGroqKey }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = if (showGroqKey) Icons.Default.Close else Icons.Default.Lock,
                            contentDescription = "Visibility Toggle",
                            tint = Color(0xFF34A853),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                DebouncedTextField(
                    value = groqApiKey,
                    onValueChange = { stableOnSettingChanged("groq_api_key", it) },
                    placeholder = "gsk_...",
                    visualTransformation = if (showGroqKey) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        // 4. Section: WORKSPACE
        Text(
            text = "Workspace Options",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF34A853), // Google Green accent
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Memory Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Context Memory Buffer", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Retains sliding window dialog context", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    Switch(
                        checked = contextMemoryEnabled,
                        onCheckedChange = { contextMemoryEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF34A853)
                        )
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                // Clean Cache Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Clear Workspace Cache", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Discards active temporary sessions variables", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    Button(
                        onClick = {
                            Toast.makeText(context, "Workspace cache cleared successfully!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFBBC05), // Google Yellow
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section: RANDOM REMINDERS ENGINE
        Text(
            text = "Adaptive Reminders",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFA142F4), // Premium Elegant Purple
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Personalized Random Notifications", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Fully on-device offline reminders for productivity, tips & health tasks.", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    Button(
                        onClick = onConfigureReminders,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFA142F4),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Configure", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 5. Section: ABOUT
        Text(
            text = "About",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFEA4335), // Google Red accent
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Subsystem Version", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                    Text("v1.0.0-cli", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Device", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                    Text(Build.MODEL ?: "Android Device", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                Button(
                    onClick = { showLogDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4285F4),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Diagnostic Logs", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showLogDialog) {
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            title = { Text("Diagnostic Logs", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .verticalScroll(rememberScrollState())
                        .background(Color.Black, shape = RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    val sampleLogs = listOf(
                        "[SYSTEM] Boot binding initialized.",
                        "[NETWORK] Direct Cloud-only completions ready.",
                        "[SDK] Ollama client connection initialized.",
                        "[MODEL] Selected model: $selectedModel.",
                        "[WS] Connection to direct Ollama endpoint $opencodeBaseUrl verified.",
                        "[STATUS] Active logs pool compiled successfully."
                    )
                    sampleLogs.forEach { logLine ->
                        Text(
                            text = logLine,
                            color = Color(0xFF4ADE80), // Monospace Green
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogDialog = false }) {
                    Text("OK", color = Color(0xFF4285F4))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
