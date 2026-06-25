package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ChatMessage
import com.example.service.ConnectionState
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainHub(initialOpenTab: String? = null) {
    val viewModel: ChatViewModel = viewModel()

    // Preferences & Config States
    val selectedModel by viewModel.selectedModel.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val profileName by viewModel.profileName.collectAsState()
    val profileRole by viewModel.profileRole.collectAsState()
    val reasoningLevel by viewModel.reasoningLevel.collectAsState()
    
    // Agent Mode States
    val currentAgentMode by viewModel.currentAgentMode.collectAsState()
    val currentReasoningLevel by viewModel.currentReasoningLevel.collectAsState()
    
    // Onboarding States
    val isOnboardingComplete by viewModel.isOnboardingComplete.collectAsState()
    val isLoadingSettings by viewModel.isLoadingSettings.collectAsState()

    // Operational States
    val sessions by viewModel.sessions.collectAsState()
    val filteredSessions by viewModel.filteredSessions.collectAsState()
    val selectedWorkspace by viewModel.selectedWorkspace.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val daemonConnection by viewModel.daemonConnection.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val isExecutingTool by viewModel.isExecutingTool.collectAsState()
    val agentState by viewModel.agentState.collectAsState()
    val currentToolName by viewModel.currentToolName.collectAsState()
    val activeToolApproval by viewModel.activeToolApproval.collectAsState()
    val showSlashPalette by viewModel.showSlashPalette.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val currentPermissionRequest by com.example.tools.PermissionManager.currentRequest.collectAsState()

    // Navigation Panels
    var showSidebar by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showTelemetryDashboard by remember { mutableStateOf(false) }
    var showDocumentationViewer by remember { mutableStateOf(false) }
    var showRemindersScreen by remember { mutableStateOf(initialOpenTab == "reminders" || initialOpenTab == "clock") }
    
    // Mode selector expanded state
    var showReasoningLevelSelector by remember { mutableStateOf(false) }
    var showAgentModeSelectorDropdown by remember { mutableStateOf(false) }

    // Chat Inputs
    var inputText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val chatListState = rememberLazyListState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val speechRecognizerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val data = result.data
                if (data != null) {
                    val results = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                    if (!results.isNullOrEmpty()) {
                        val spokenText = results[0]
                        inputText = if (inputText.isBlank()) {
                            spokenText
                        } else {
                            if (inputText.endsWith(" ")) {
                                inputText + spokenText
                            } else {
                                inputText + " " + spokenText
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainHub", "Speech recognition activity result error: ${e.message}", e)
        }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        com.example.tools.PermissionManager.onPermissionResult(allGranted)
    }

    val triggerSpeechToText = {
        try {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak now...")
            }
            val packageManager = context.packageManager
            val activities = packageManager.queryIntentActivities(intent, 0)
            val isActivityAvailable = activities.isNotEmpty()

            if (!isActivityAvailable) {
                android.widget.Toast.makeText(context, "Voice input activity is not supported or active on this device.", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                try {
                    speechRecognizerLauncher.launch(intent)
                } catch (e2: Exception) {
                    android.widget.Toast.makeText(context, "Voice launch error: ${e2.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Voice recognition service is occupied or unavailable.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Gemini Model Selector States
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var actionMenuExpanded by remember { mutableStateOf(false) }

    // Theme Wrapper
    MyApplicationTheme(themeMode = themeMode) {
        val clipboardManager = LocalClipboardManager.current
        val isDark = MaterialTheme.colorScheme.background == PremiumBgDark

        // Dialog modals for pending tool approvals
        activeToolApproval?.let { toolMsg ->
            ToolApprovalModal(
                toolMessage = toolMsg,
                onResult = { remember, approved ->
                    viewModel.handleToolApprovalDecision(toolMsg, approved, remember)
                }
            )
        }

        // Dialog for dynamic system permissions
        currentPermissionRequest?.let { req ->
            androidx.compose.ui.window.Dialog(onDismissRequest = { com.example.tools.PermissionManager.onPermissionResult(false) }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .widthIn(max = 340.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Permission Required",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Access Required",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = req.rationale,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { com.example.tools.PermissionManager.onPermissionResult(false) }
                            ) {
                                Text("Deny", color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            // For special permissions like MANAGE_EXTERNAL_STORAGE, show Settings button
                            // Otherwise show Settings only as secondary option
                            if (req.needsSettingsIntent && req.settingsIntentAction != null) {
                                // Special permission - Grant button opens Settings directly
                                Button(
                                    onClick = {
                                        try {
                                            val intent = android.content.Intent(req.settingsIntentAction).apply {
                                                data = android.net.Uri.parse("package:${context.packageName}")
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                            android.widget.Toast.makeText(
                                                context,
                                                "Enable 'All files access' and return to the app",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Could not open settings: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        // Don't auto-dismiss - user needs to grant and return
                                        com.example.tools.PermissionManager.onPermissionResult(false)
                                    }
                                ) {
                                    Text("Open Settings")
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        try {
                                            val intent = android.content.Intent(
                                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                android.net.Uri.fromParts("package", context.packageName, null)
                                            ).apply {
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Could not open settings", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        com.example.tools.PermissionManager.onPermissionResult(false)
                                    }
                                ) {
                                    Text("Settings")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(req.permissions.toTypedArray())
                                    }
                                ) {
                                    Text("Grant")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Lightweight searchable model selector Dialog
        if (modelMenuExpanded) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { modelMenuExpanded = false }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Select Model",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { modelMenuExpanded = false }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                            }
                        }
                        
                        var searchQuery by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search models...", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = LogyBlue,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                        
                        val supportedModels = listOf(
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
                        
                        val filteredModels = supportedModels.filter {
                            it.contains(searchQuery, ignoreCase = true)
                        }
                        
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (filteredModels.isEmpty()) {
                                item {
                                    Text(
                                        "No models found",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                                    )
                                }
                            } else {
                                items(filteredModels) { modelKey ->
                                    val isSelected = selectedModel == modelKey
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) LogyBlue.copy(alpha = 0.12f) else Color.Transparent)
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Left side: Model select trigger
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    viewModel.updateSetting("selected_model", modelKey)
                                                    modelMenuExpanded = false
                                                }
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = if (isSelected) LogyBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = modelKey,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) LogyBlue else MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        // Right side: Reasoning level pills (only for the selected model!)
                                        if (isSelected) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(start = 8.dp)
                                            ) {
                                                val levels = listOf(1, 2, 3, 4, 5)
                                                levels.forEach { level ->
                                                    val isLevelSelected = currentReasoningLevel.value == level
                                                    val levelColor = when (level) {
                                                        1 -> Color(0xFF94A3B8)
                                                        2 -> Color(0xFF64748B)
                                                        3 -> Color(0xFF3B82F6)
                                                        4 -> Color(0xFF8B5CF6)
                                                        5 -> Color(0xFFEC4899)
                                                        else -> Color(0xFF3B82F6)
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .size(22.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (isLevelSelected) levelColor else Color.Transparent
                                                            )
                                                            .border(
                                                                width = 1.dp,
                                                                color = if (isLevelSelected) Color.Transparent else levelColor.copy(alpha = 0.4f),
                                                                shape = CircleShape
                                                            )
                                                            .clickable {
                                                                viewModel.setReasoningLevel(level)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "$level",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isLevelSelected) Color.White else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isTablet = configuration.screenWidthDp >= 600

        // Show loading screen while settings are being loaded
        if (isLoadingSettings) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = LogyBlue)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Loading Logy...",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp
                    )
                }
            }
            return@MyApplicationTheme
        }
        
        // Show onboarding screen for new users
        if (!isOnboardingComplete) {
            OnboardingScreen(
                onComplete = { name, role, customSystemPrompt ->
                    viewModel.completeOnboarding(name, role, customSystemPrompt)
                }
            )
            return@MyApplicationTheme
        }

        // Parent responsive layout box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (isTablet) {
                    SessionHistorySidebar(
                        allSessions = sessions,
                        filteredSessions = filteredSessions,
                        selectedWorkspace = selectedWorkspace,
                        currentSessionId = currentSessionId,
                        profileName = profileName,
                        profileRole = profileRole,
                        onSessionSelected = {
                            viewModel.selectSession(it)
                        },
                        onWorkspaceSelected = {
                            viewModel.selectWorkspace(it)
                        },
                        onNewSession = {
                            viewModel.createNewSession()
                        },
                        onDeleteSession = { viewModel.deleteSession(it) },
                        onCloseSidebar = { showSidebar = false },
                        onUpdateProfile = { name, role ->
                            viewModel.updateSetting("profile_name", name)
                            viewModel.updateSetting("profile_role", role)
                        },
                        onSettingsClicked = {
                            showSettings = true
                        },
                        modifier = Modifier
                            .width(260.dp)
                            .fillMaxHeight()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .statusBarsPadding()
                ) {
                // 1. Header Row Area (Google Gemini Minimalist Style)
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { modelMenuExpanded = true }
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        ) {
                            Text(
                                text = selectedModel,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier.widthIn(max = 200.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand Models",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(20.dp)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { showSidebar = true }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Sidebar Menu",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    actions = {
                        // New Chat Edit Icon
                        IconButton(onClick = { viewModel.createNewSession() }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "New Chat",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Workspace Settings Icon
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                )

                // Silent beautiful loading/generating progress indicator across colors
                if (isThinking) {
                    val progressValue by animateFloatAsState(
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "progress"
                    )
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.5.dp),
                        color = Color(0xFF4285F4), // Google Blue
                        trackColor = Color(0xFFFBBC05).copy(alpha = 0.2f) // Google Yellow track
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.5.dp))
                }

                // 2. Main Chat Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (messages.isEmpty()) {
                        // Gemini Style welcoming dashboard
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Hello, $profileName",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 28.sp,
                                color = Color(0xFF4285F4), // Google Blue active title
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Text(
                                text = "I'm logy. How can I help you today?",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(bottom = 32.dp)
                            )

                             // Quick initiation guides in beautiful 2x2 Grid styled with Google's 4 color palette
                             Column(
                                 modifier = Modifier.fillMaxWidth(),
                                 verticalArrangement = Arrangement.spacedBy(12.dp)
                             ) {
                                 data class SuggestAction(val title: String, val subtitle: String, val color: Color, val icon: ImageVector)
                                 val suggestList = listOf(
                                     SuggestAction("Explain Code", "Deconstruct and trace complex system logic", Color(0xFF4285F4), Icons.Default.Code),
                                     SuggestAction("Build UI Component", "Generate clean Material 3 design views", Color(0xFF34A853), Icons.Default.Build),
                                     SuggestAction("Debug Backend", "Analyze and resolve server stack trace errors", Color(0xFFEA4335), Icons.Default.BugReport),
                                     SuggestAction("Analyze Architecture", "Deconstruct system layers and pipelines", Color(0xFFFBBC05), Icons.Default.List)
                                 )
                                 // Render as 2 Rows of 2 Cards each
                                 for (row in 0..1) {
                                     Row(
                                         modifier = Modifier.fillMaxWidth(),
                                         horizontalArrangement = Arrangement.spacedBy(12.dp)
                                     ) {
                                         for (col in 0..1) {
                                             val index = row * 2 + col
                                             val actionItem = suggestList[index]
                                             Card(
                                                 modifier = Modifier
                                                     .weight(1f)
                                                     .scaleOnClick { inputText = "${actionItem.title}: ${actionItem.subtitle}" },
                                                 colors = CardDefaults.cardColors(
                                                     containerColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFAFAFA)
                                                 ),
                                                 border = androidx.compose.foundation.BorderStroke(
                                                     1.dp,
                                                     if (isDark) actionItem.color.copy(alpha = 0.3f) else Color(0xFFE0E0E0)
                                                 ),
                                                 shape = RoundedCornerShape(16.dp)
                                             ) {
                                                 Column(modifier = Modifier.padding(16.dp)) {
                                                     Row(verticalAlignment = Alignment.CenterVertically) {
                                                         Icon(
                                                             imageVector = actionItem.icon,
                                                             contentDescription = actionItem.title,
                                                             tint = actionItem.color,
                                                             modifier = Modifier.size(18.dp)
                                                         )
                                                         Spacer(modifier = Modifier.width(8.dp))
                                                         Text(
                                                             text = actionItem.title,
                                                             fontWeight = FontWeight.Bold,
                                                             fontSize = 13.sp,
                                                             color = actionItem.color
                                                         )
                                                     }
                                                     Spacer(modifier = Modifier.height(6.dp))
                                                     Text(
                                                         text = actionItem.subtitle,
                                                         fontSize = 11.sp,
                                                         color = if (isDark) Color(0xFFB3B3B3) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                         maxLines = 2
                                                     )
                                                 }
                                             }
                                         }
                                     }
                                 }
                             }
                        }
                    } else {
                        // Messages container lists
                        val nonBlankMessages = remember(messages) {
                            messages.filter {
                                it.content.trim().isNotEmpty() ||
                                it.role == "tool" ||
                                it.status == "Pending" ||
                                it.status == "Streaming"
                            }
                        }

                        val chatTurns = remember(nonBlankMessages) {
                            groupMessagesIntoTurns(nonBlankMessages)
                        }

                        var traceExpansionStates by rememberSaveable { mutableStateOf(mapOf<Int, Boolean>()) }

                        LazyColumn(
                            state = chatListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = chatTurns,
                                key = { it.id }
                            ) { turn ->
                                ChatTurnView(
                                    turn = turn,
                                    fontSize = fontSize,
                                    profileName = profileName,
                                    onCopyText = { clipboardManager.setText(AnnotatedString(it)) },
                                    onApproveTool = { msg -> viewModel.handleToolApprovalDecision(msg, true, false) },
                                    onRetry = {
                                        val turnIndex = chatTurns.indexOf(turn)
                                        var lastUserPrompt = ""
                                        if (turnIndex != -1) {
                                            for (i in turnIndex downTo 0) {
                                                if (chatTurns[i].userMessage != null) {
                                                    lastUserPrompt = chatTurns[i].userMessage!!.content
                                                    break
                                                }
                                            }
                                        }
                                        if (lastUserPrompt.trim().isEmpty()) {
                                            lastUserPrompt = chatTurns.lastOrNull { it.userMessage != null }?.userMessage?.content ?: ""
                                        }
                                        if (lastUserPrompt.trim().isNotEmpty()) {
                                            viewModel.sendMessage(lastUserPrompt)
                                        }
                                    },
                                    onEditMessage = {
                                        inputText = it
                                    },
                                    traceExpansionStates = traceExpansionStates,
                                    onToggleTraceExpansion = { turnId, expanded ->
                                        traceExpansionStates = traceExpansionStates + (turnId to expanded)
                                    }
                                )
                            }

                            // Only show global/external list-bottom loaders when there's no active/streaming/pending turn in-bubble
                            val hasActiveTurn = chatTurns.any { it.isStreaming || it.isPending }

                            if (agentState != AgentState.IDLE && !hasActiveTurn) {
                                item {
                                    UnifiedAgentStateBubble(agentState, currentToolName, fontSize, isDark)
                                }
                            }
                        }

                        // Auto scroll bottom when thinking, typing, tool executing or message updates
                        LaunchedEffect(chatTurns.size, isThinking, isTyping, isExecutingTool) {
                            val totalItemCount = chatTurns.size + (if (isThinking || isTyping || isExecutingTool) 1 else 0)
                            if (totalItemCount > 0) {
                                try {
                                    chatListState.animateScrollToItem(totalItemCount - 1)
                                } catch (e: Exception) {
                                    // Safe fallback
                                }
                            }
                        }
                    }

                    // 3. Telemetry Dashboard drawer (toggled inside Chat space)
                    if (showTelemetryDashboard) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        ) {
                            AgentDashboard(
                                telemetry = telemetry,
                                statusText = statusText,
                                onRestartDaemon = { viewModel.toggleDaemonConnection() },
                                onClearCache = {
                                    scope.launch {
                                        viewModel.sendMessage("Clear my cache and restart workspace metadata configs.")
                                        showTelemetryDashboard = false
                                    }
                                }
                            )
                        }
                    }
                }

                // 4. Quick Shell Console Input Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp)
                ) {
                    // Slash interactive choices menu
                    if (showSlashPalette || inputText.startsWith("/")) {
                        SlashCommandPalette(
                            onCommandSelected = { cmd ->
                                inputText = cmd + " "
                                viewModel.toggleSlashPalette(false)
                            },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // Input control capsule row (Google Gemini Style with embedded Agent Mode selector)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, shape = RoundedCornerShape(28.dp))
                            .background(if (isDark) Color(0xFF111827) else Color(0xFFF0F4F8), shape = RoundedCornerShape(28.dp))
                            .border(androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0x33FFFFFF) else Color(0xFFE2E8F0)), shape = RoundedCornerShape(28.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Actions menu activator / Google Multi-colored Expand Icon - with integrated dropdown for modes
                        Box {
                            IconButton(
                                onClick = { showAgentModeSelectorDropdown = !showAgentModeSelectorDropdown },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = if (showAgentModeSelectorDropdown) Icons.Default.Close else Icons.Default.Add,
                                    contentDescription = "Expand working modes menu",
                                    tint = Color(0xFF4285F4), // Google Blue
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showAgentModeSelectorDropdown,
                                onDismissRequest = { showAgentModeSelectorDropdown = false },
                                modifier = Modifier.background(if (isDark) Color(0xFF1E293B) else Color.White)
                            ) {
                                Text(
                                    text = "SELECT WORK MODE",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                                com.example.runtime.mode.AgentMode.values().forEach { mode ->
                                    val isSelected = currentAgentMode == mode
                                    val modeColor = Color(mode.color)
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                              ) {
                                                Icon(
                                                    imageVector = when (mode) {
                                                        com.example.runtime.mode.AgentMode.PLANNING -> Icons.Default.List
                                                        com.example.runtime.mode.AgentMode.EXECUTION -> Icons.Default.PlayArrow
                                                        com.example.runtime.mode.AgentMode.DEEP_THINKING -> Icons.Default.Search
                                                    },
                                                    contentDescription = mode.displayName,
                                                    tint = modeColor,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = mode.displayName,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) modeColor else (if (isDark) Color.White else Color(0xFF1E293B)),
                                                    fontSize = 13.sp
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.setAgentMode(mode)
                                            showAgentModeSelectorDropdown = false
                                        }
                                    )
                                }
                                
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Build,
                                                contentDescription = "Quick Commands",
                                                tint = Color(0xFF4285F4),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Quick Commands (/)",
                                                color = if (isDark) Color.White else Color(0xFF1E293B),
                                                fontSize = 13.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        showAgentModeSelectorDropdown = false
                                        viewModel.toggleSlashPalette(true)
                                    }
                                )
                            }
                        }

                        // Raw Input Box (borderless modern style)
                        BasicTextField(
                            value = inputText,
                            onValueChange = {
                                inputText = it
                                if (it == "/") {
                                    viewModel.toggleSlashPalette(true)
                                } else if (!it.startsWith("/")) {
                                    viewModel.toggleSlashPalette(false)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            maxLines = 4,
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 14.sp,
                                color = if (isDark) Color.White else Color(0xFF1E293B)
                            ),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Send,
                                keyboardType = KeyboardType.Text
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (inputText.isNotBlank()) {
                                        viewModel.sendMessage(inputText)
                                        inputText = ""
                                    }
                                }
                            ),
                            decorationBox = { innerTextField ->
                                if (inputText.isEmpty()) {
                                    Text(
                                        text = "Ask Gemini...",
                                        fontSize = 14.sp,
                                        color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                                    )
                                }
                                innerTextField()
                            }
                        )

                        // Voice input button (Microphone button)
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(38.dp)
                                .background(
                                    color = if (isDark) Color(0xFF1E293B) else Color(0xFFE8F0FE),
                                    shape = RoundedCornerShape(19.dp)
                                )
                                .scaleOnClick {
                                    triggerSpeechToText()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Input",
                                tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF4285F4),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Send / Pause Dynamic circle
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    color = when {
                                        isThinking -> Color(0xFFEA4335) // Google Red when generating/thinking
                                        inputText.isNotBlank() -> Color(0xFF4285F4) // Google Blue when clear send target
                                        else -> if (isDark) Color(0xFF1E293B) else Color(0xFFE8F0FE) // Theme aware background in empty state
                                    },
                                    shape = RoundedCornerShape(19.dp)
                                )
                                .scaleOnClick {
                                    if (isThinking) {
                                        viewModel.stopGeneration()
                                    } else if (inputText.isNotBlank()) {
                                        viewModel.sendMessage(inputText)
                                        inputText = ""
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isThinking) {
                                // Red Pause indicator (two vertical thick bars) in standard vector shape representation
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(width = 3.dp, height = 13.dp).background(Color.White, shape = RoundedCornerShape(1.dp)))
                                    Box(modifier = Modifier.size(width = 3.dp, height = 13.dp).background(Color.White, shape = RoundedCornerShape(1.dp)))
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send message",
                                    tint = if (inputText.isNotBlank()) Color.White else (if (isDark) Color(0xFF64748B) else Color(0xFF4285F4)),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

            // 5. Sidebar Navigation slide Drawer
            // Smoothly fading dark semi-transparent scrim overlay
            AnimatedVisibility(
                visible = showSidebar,
                enter = fadeIn(animationSpec = tween(300, easing = LinearEasing)),
                exit = fadeOut(animationSpec = tween(250, easing = LinearEasing))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { showSidebar = false }
                )
            }

            // Sliding drawer block with standard decelerating easing curve
            AnimatedVisibility(
                visible = showSidebar,
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(250, easing = FastOutLinearInEasing)
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight()
                ) {
                    SessionHistorySidebar(
                        allSessions = sessions,
                        filteredSessions = filteredSessions,
                        selectedWorkspace = selectedWorkspace,
                        currentSessionId = currentSessionId,
                        profileName = profileName,
                        profileRole = profileRole,
                        onSessionSelected = {
                            viewModel.selectSession(it)
                            showSidebar = false
                        },
                        onWorkspaceSelected = {
                            viewModel.selectWorkspace(it)
                            showSidebar = false
                        },
                        onNewSession = {
                            viewModel.createNewSession()
                            showSidebar = false
                        },
                        onDeleteSession = { viewModel.deleteSession(it) },
                        onCloseSidebar = { showSidebar = false },
                        onUpdateProfile = { name, role ->
                            viewModel.updateSetting("profile_name", name)
                            viewModel.updateSetting("profile_role", role)
                        },
                        onSettingsClicked = {
                            showSettings = true
                        },
                        onDocsClicked = {
                            showDocumentationViewer = true
                        },
                        modifier = Modifier.clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {}
                    )
                }
            }

            // 6. Config Settings Panel slide overlay with Shared Z-Axis transition
            AnimatedVisibility(
                visible = showSettings,
                enter = fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.95f, animationSpec = tween(300, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(250, easing = FastOutLinearInEasing)) +
                       scaleOut(targetScale = 0.95f, animationSpec = tween(250, easing = FastOutLinearInEasing))
            ) {
                key("settings_panel") {
                    val isLocalModeLocal by viewModel.isLocalMode.collectAsState()
                    val apiKeyLocal by viewModel.apiKey.collectAsState()
                    val daemonHostLocal by viewModel.daemonHost.collectAsState()
                    val daemonApiKeyLocal by viewModel.daemonApiKey.collectAsState()
                    val opencodeBaseUrlLocal by viewModel.opencodeBaseUrl.collectAsState()
                    val systemPromptLocal by viewModel.systemPrompt.collectAsState()
                    val googleApiKeyLocal by viewModel.googleApiKey.collectAsState()
                    val googleCxLocal by viewModel.googleCx.collectAsState()
                    val groqApiKeyLocal by viewModel.groqApiKey.collectAsState()

                    SettingsScreen(
                        themeMode = themeMode,
                        fontSize = fontSize,
                        isLocalMode = isLocalModeLocal,
                        apiKey = apiKeyLocal,
                        daemonHost = daemonHostLocal,
                        daemonApiKey = daemonApiKeyLocal,
                        selectedModel = selectedModel,
                        opencodeBaseUrl = opencodeBaseUrlLocal,
                        reasoningLevel = reasoningLevel,
                        systemPrompt = systemPromptLocal,
                        googleApiKey = googleApiKeyLocal,
                        googleCx = googleCxLocal,
                        groqApiKey = groqApiKeyLocal,
                        onSettingChanged = { key, value ->
                            viewModel.updateSetting(key, value)
                        },
                        onCloseSettings = { showSettings = false },
                        onConfigureReminders = { showRemindersScreen = true }
                    )
                }
            }

            // 7. Core Architectures & Mobile Docs Hub Viewer overlay slide
            AnimatedVisibility(
                visible = showDocumentationViewer,
                enter = fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.95f, animationSpec = tween(300, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(250, easing = FastOutLinearInEasing)) +
                       scaleOut(targetScale = 0.95f, animationSpec = tween(250, easing = FastOutLinearInEasing))
            ) {
                DocsViewerScreen(
                    onClose = { showDocumentationViewer = false }
                )
            }

            // 8. Adaptive Random Reminders Panel overlay slide
            AnimatedVisibility(
                visible = showRemindersScreen,
                enter = fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.95f, animationSpec = tween(300, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(250, easing = FastOutLinearInEasing)) +
                       scaleOut(targetScale = 0.95f, animationSpec = tween(250, easing = FastOutLinearInEasing))
            ) {
                RemindersScreen(
                    viewModel = viewModel,
                    onClose = { showRemindersScreen = false },
                    initialOpenTab = initialOpenTab
                )
            }
        }
    }
}

// Inner Bubble renderer with formatting helper
@Composable
fun MessageBubble(
    msg: ChatMessage,
    fontSize: Int,
    profileName: String,
    onCopyText: (String) -> Unit,
    onApproveTool: () -> Unit,
    onRetry: () -> Unit,
    onEditMessage: ((String) -> Unit)? = null
) {
    val isUser = msg.role == "user"
    val isSystem = msg.role == "system"
    val isTool = msg.role == "tool"

    // Track whether the user has manually toggled the state
    var isExpandedUserToggled by rememberSaveable(msg.id) { mutableStateOf<Boolean?>(null) }
    
    val isToolRunning = isTool && (msg.toolStatus == "pending" || msg.toolStatus == "executing")
    
    val isExpanded = remember(isExpandedUserToggled, isSystem, isTool, isToolRunning) {
        if (isExpandedUserToggled != null) {
            isExpandedUserToggled!!
        } else {
            if (isTool) {
                isToolRunning
            } else if (isSystem) {
                false // Collapsed by default
            } else {
                true // Expanded by default for standard model/user messages
            }
        }
    }

    val isDark = MaterialTheme.colorScheme.background == PremiumBgDark
    val activeCheckColor = if (isDark) Color(0xFF81B0FF) else Color(0xFF1A73E8)
    val activeCloseColor = if (isDark) Color(0xFFFF8A80) else Color(0xFFD93025)
    val basicIconColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF1F2937)

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val maxBubbleWidth = (configuration.screenWidthDp * 0.82).dp

    val animVisible = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animVisible.value = true
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (animVisible.value) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "alpha"
    )
    val translateY by animateFloatAsState(
        targetValue = if (animVisible.value) 0f else 10f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "translateY"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = alpha, translationY = translateY)
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            // Left Side Avatar for Gemini CLI Assistant / System / Tool
            if (isSystem || isTool) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = if (isSystem) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSystem) Icons.Default.Warning else Icons.Default.Build,
                        contentDescription = "Avatar",
                        tint = if (isSystem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                // Return our cute custom LogyAvatar vector drawing!
                LogyAvatar(
                    modifier = Modifier.size(32.dp),
                    isThinking = false
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = if (isUser) {
                Modifier.padding(start = 40.dp)
            } else {
                Modifier.weight(1f, fill = false)
            },
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Label
            Text(
                text = when {
                    isUser -> profileName
                    isSystem -> "⚡ SYSTEM DIAGNOSTICS"
                    isTool -> "🛠️ Agentic Tool call"
                    else -> "❖ logy"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isUser) MaterialTheme.colorScheme.primary else LogyBlue,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
            )

            if (isSystem) {
                // Diagnostics look (Collapsible/collapsed by default)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isExpandedUserToggled = !isExpanded
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = TerminalBg
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    tint = WarningSemantic,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "System Execution Log",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (fontSize - 1).sp,
                                    color = WarningSemantic,
                                    maxLines = 1
                                )
                            }
                            if (!isExpanded) {
                                Text(
                                    text = if (msg.content.length > 30) msg.content.substring(0, 30).replace("\n", " ") + "..." else msg.content.replace("\n", " "),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = (fontSize - 3).sp,
                                    color = WarningSemantic.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = msg.content,
                                fontFamily = FontFamily.Monospace,
                                fontSize = (fontSize - 2).sp,
                                color = WarningSemantic,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            } else if (isTool) {
                // Interactive in-chat tool request block (Collapsible/collapsed by default)
                val isPending = msg.toolStatus == "pending"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isExpandedUserToggled = !isExpanded
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "🔧 Tool: ${msg.toolName ?: "Command Pipeline"}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = fontSize.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                                if (msg.durationMs != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${msg.durationMs}ms",
                                        fontSize = (fontSize - 3).sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            
                            val toolStatusDisplay = when (msg.toolStatus) {
                                "pending" -> "RUNNING"
                                "executing" -> "RUNNING"
                                "approved" -> "APPROVED"
                                "success" -> "SUCCESS"
                                "denied" -> "DENIED"
                                "error" -> "ERROR"
                                else -> "FINISHED"
                            }
                            
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (msg.toolStatus) {
                                    "approved" -> SuccessSemantic.copy(alpha = 0.2f)
                                    "success" -> SuccessSemantic.copy(alpha = 0.2f)
                                    "denied" -> ErrorSemantic.copy(alpha = 0.2f)
                                    "error" -> ErrorSemantic.copy(alpha = 0.15f)
                                    "pending", "executing" -> WarningSemantic.copy(alpha = 0.2f)
                                    else -> SuccessSemantic.copy(alpha = 0.2f)
                                }
                            ) {
                                Text(
                                    text = toolStatusDisplay,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (msg.toolStatus) {
                                        "approved" -> SuccessSemantic
                                        "success" -> SuccessSemantic
                                        "denied" -> ErrorSemantic
                                        "error" -> ErrorSemantic
                                        "pending", "executing" -> WarningSemantic
                                        else -> SuccessSemantic
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Args: ${msg.toolArgs ?: "{}"}",
                                fontSize = (fontSize - 2).sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )

                            if (isPending) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = onApproveTool,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White
                                    ),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Open Approval Consent Board", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else if (msg.toolStatus == "success") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = msg.content,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = (fontSize - 1).sp,
                                    color = SuccessSemantic
                                )
                            } else if (msg.toolStatus == "error") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = msg.content,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = (fontSize - 1).sp,
                                    color = ErrorSemantic
                                )
                            } else if (msg.toolStatus == "denied") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Command blocked by security policies.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = (fontSize - 1).sp,
                                    color = ErrorSemantic
                                )
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = msg.content,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = (fontSize - 1).sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            } else {
                // General Conversational Chat Bubble (Google Gemini style)
                if (isUser) {
                    val userBubbleBg = if (isDark) Color(0xFF1E293B) else Color(0xFFE8F0FE)
                    val userBubbleText = if (isDark) Color(0xFFF3F4F6) else Color(0xFF1E293B)
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                            color = userBubbleBg,
                            modifier = Modifier.widthIn(max = maxBubbleWidth)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                MarkdownTextRenderer(
                                    text = msg.content,
                                    fontSize = fontSize,
                                    textColor = userBubbleText,
                                    onCopyClicked = onCopyText,
                                    fillMaxWidth = false
                                )
                            }
                        }
                        
                        // Copy & Edit actions under the user bubble
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp, end = 4.dp)
                        ) {
                            // Copy Action
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onCopyText(msg.content) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = ContentCopyCustom,
                                    contentDescription = "Copy message",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Copy",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            // Edit Action
                            if (onEditMessage != null) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onEditMessage(msg.content) }
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit message",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Edit",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            if (msg.content.isEmpty() && (msg.status == "Pending" || msg.status == "Streaming")) {
                                StreamingLoadingDots(modifier = Modifier.padding(vertical = 8.dp))
                            } else {
                                Column {
                                    MarkdownWithCollapsibleThinking(
                                        text = msg.content,
                                        fontSize = fontSize,
                                        textColor = MaterialTheme.colorScheme.onSurface,
                                        onCopyClicked = onCopyText,
                                        msgId = msg.id,
                                        isStreaming = msg.status == "Streaming"
                                    )
                                    if (msg.status == "Streaming") {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        StreamingLoadingDots(modifier = Modifier.padding(start = 4.dp))
                                    }
                                }
                            }
                        }

                        if (msg.content.trim().isNotEmpty() && msg.status == "Complete") {
                            // Only 2 theme-aware options: Copy response and Retry model with same prompt
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp, bottom = 4.dp)
                            ) {
                                // 1. Copy the response
                                IconButton(
                                    onClick = { onCopyText(msg.content) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = ContentCopyCustom,
                                        contentDescription = "Copy response",
                                        tint = basicIconColor.copy(alpha = 0.55f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // 2. Retry prompt
                                IconButton(
                                    onClick = onRetry,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry prompt",
                                        tint = basicIconColor.copy(alpha = 0.55f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Compact markdown text engine for custom codeblocks rendering in chat bubble
fun parseMarkdownToAnnotatedString(
    text: String,
    baseColor: Color,
    codeBgColor: Color = Color(0x33808080)
): AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val nextBoldStar = text.indexOf("**", index)
            val nextBoldUnderscore = text.indexOf("__", index)
            val nextBold = if (nextBoldStar != -1 && nextBoldUnderscore != -1) {
                minOf(nextBoldStar, nextBoldUnderscore)
            } else if (nextBoldStar != -1) {
                nextBoldStar
            } else {
                nextBoldUnderscore
            }

            val nextItalicStar = text.indexOf("*", index)
            val nextItalicUnderscore = text.indexOf("_", index)
            val nextItalic = if (nextItalicStar != -1 && nextItalicUnderscore != -1) {
                minOf(nextItalicStar, nextItalicUnderscore)
            } else if (nextItalicStar != -1) {
                nextItalicStar
            } else {
                nextItalicUnderscore
            }

            val nextCode = text.indexOf("`", index)

            val matches = listOfNotNull(
                if (nextBold != -1) "bold" to nextBold else null,
                if (nextItalic != -1 && (nextBold == -1 || nextItalic != nextBold)) "italic" to nextItalic else null,
                if (nextCode != -1) "code" to nextCode else null
            ).sortedBy { it.second }

            if (matches.isEmpty()) {
                append(text.substring(index))
                break
            }

            val (type, formatIdx) = matches.first()
            if (formatIdx > index) {
                append(text.substring(index, formatIdx))
            }

            index = formatIdx

            when (type) {
                "bold" -> {
                    val delimiter = if (text.startsWith("**", index)) "**" else "__"
                    val endIdx = text.indexOf(delimiter, index + 2)
                    if (endIdx != -1 && endIdx > index + 2) {
                        withStyle(style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(index + 2, endIdx))
                        }
                        index = endIdx + 2
                    } else {
                        append(delimiter)
                        index += 2
                    }
                }
                "italic" -> {
                    val delimiter = if (text.startsWith("*", index)) "*" else "_"
                    val endIdx = text.indexOf(delimiter, index + 1)
                    if (endIdx != -1 && endIdx > index + 1) {
                        withStyle(style = androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                            append(text.substring(index + 1, endIdx))
                        }
                        index = endIdx + 1
                    } else {
                        append(delimiter)
                        index += 1
                    }
                }
                "code" -> {
                    val endIdx = text.indexOf("`", index + 1)
                    if (endIdx != -1 && endIdx > index + 1) {
                        withStyle(
                            style = androidx.compose.ui.text.SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBgColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        ) {
                            append(text.substring(index + 1, endIdx))
                        }
                        index = endIdx + 1
                    } else {
                        append("`")
                        index += 1
                    }
                }
            }
        }
    }
}

// Block types for high-fidelity markdown layout
sealed class MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class BulletItem(val text: String) : MarkdownBlock()
    data class NumberedItem(val num: String, val text: String) : MarkdownBlock()
    data class BlockQuote(val text: String) : MarkdownBlock()
    object Divider : MarkdownBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
}

// Modern linear markdown pre-parser
fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val lines = text.trim().split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0
    while (i < lines.size) {
        val rawLine = lines[i]
        val trimmed = rawLine.trim()
        
        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Table block match
        if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
            val tableLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                tableLines.add(lines[i].trim())
                i++
            }
            if (tableLines.isNotEmpty()) {
                val parsedRows = tableLines.map { tl ->
                    tl.split("|").map { it.trim() }.filterIndexed { index, _ -> index > 0 && index < tl.split("|").lastIndex }
                }
                if (parsedRows.isNotEmpty()) {
                    val headers = parsedRows[0]
                    val hasDivider = parsedRows.size > 1 && parsedRows[1].all { it.contains("---") || it.contains(":") || it.all { c -> c == '-' } }
                    val rows = if (hasDivider) {
                        parsedRows.drop(2)
                    } else {
                        parsedRows.drop(1)
                    }
                    blocks.add(MarkdownBlock.Table(headers, rows))
                }
            }
            continue
        }

        when {
            trimmed.startsWith("# ") -> {
                blocks.add(MarkdownBlock.Header(1, trimmed.substring(2)))
            }
            trimmed.startsWith("## ") -> {
                blocks.add(MarkdownBlock.Header(2, trimmed.substring(3)))
            }
            trimmed.startsWith("### ") -> {
                blocks.add(MarkdownBlock.Header(3, trimmed.substring(4)))
            }
            trimmed.startsWith("> ") -> {
                var quoteText = trimmed.substring(2)
                i++
                while (i < lines.size && lines[i].trim().startsWith("> ")) {
                    quoteText += "\n" + lines[i].trim().substring(2)
                    i++
                }
                blocks.add(MarkdownBlock.BlockQuote(quoteText))
                continue
            }
            trimmed == "---" || trimmed == "***" -> {
                blocks.add(MarkdownBlock.Divider)
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                blocks.add(MarkdownBlock.BulletItem(trimmed.substring(2)))
            }
            trimmed.matches(Regex("^\\d+\\.\\s+.*$")) -> {
                val dotIndex = trimmed.indexOf(".")
                val num = trimmed.substring(0, dotIndex + 1)
                val content = trimmed.substring(dotIndex + 1).trim()
                blocks.add(MarkdownBlock.NumberedItem(num, content))
            }
            else -> {
                blocks.add(MarkdownBlock.Paragraph(trimmed))
            }
        }
        i++
    }
    return blocks
}

@Composable
fun MarkdownTableRenderer(
    headers: List<String>,
    rows: List<List<String>>,
    fontSize: Int,
    textColor: Color
) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Header row
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    headers.forEach { header ->
                        Box(
                            modifier = Modifier
                                .widthIn(min = 100.dp, max = 220.dp)
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = header,
                                fontWeight = FontWeight.Bold,
                                fontSize = fontSize.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2
                            )
                        }
                    }
                }
                
                // Rows
                rows.forEach { row ->
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        row.forEach { cell ->
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 100.dp, max = 220.dp)
                                    .padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = parseMarkdownToAnnotatedString(cell, textColor),
                                    fontSize = (fontSize - 1).sp,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ParsedMarkdownColumn(
    text: String,
    fontSize: Int,
    textColor: Color,
    fillMaxWidth: Boolean = true
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    Column(
        modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> {
                    val sizeBonus = when (block.level) {
                        1 -> 4
                        2 -> 2
                        else -> 1
                    }
                    val headerColor = when (block.level) {
                        1 -> MaterialTheme.colorScheme.primary
                        2 -> LogyBlue
                        else -> MaterialTheme.colorScheme.secondary
                    }
                    Text(
                        text = parseMarkdownToAnnotatedString(block.text, textColor),
                        fontSize = (fontSize + sizeBonus).sp,
                        fontWeight = FontWeight.Bold,
                        color = headerColor,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                is MarkdownBlock.Divider -> {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = (if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier).padding(vertical = 12.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = (if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
                            .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontSize = fontSize.sp,
                            fontWeight = FontWeight.Bold,
                            color = LogyBlue,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = parseMarkdownToAnnotatedString(block.text, textColor),
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize + 5).sp,
                            color = textColor,
                            modifier = if (fillMaxWidth) Modifier.weight(1f) else Modifier
                        )
                    }
                }
                is MarkdownBlock.NumberedItem -> {
                    Row(
                        modifier = (if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
                            .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = block.num,
                            fontSize = fontSize.sp,
                            fontWeight = FontWeight.Bold,
                            color = LogyBlue,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = parseMarkdownToAnnotatedString(block.text, textColor),
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize + 5).sp,
                            color = textColor,
                            modifier = if (fillMaxWidth) Modifier.weight(1f) else Modifier
                        )
                    }
                }
                is MarkdownBlock.BlockQuote -> {
                    Row(
                        modifier = (if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
                            .padding(vertical = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .drawBehind {
                                drawRect(
                                    color = LogyBlue,
                                    size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)
                                )
                            }
                            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = parseMarkdownToAnnotatedString(block.text, textColor),
                            fontSize = fontSize.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            lineHeight = (fontSize + 5).sp,
                            color = textColor.copy(alpha = 0.85f)
                        )
                    }
                }
                is MarkdownBlock.Table -> {
                    MarkdownTableRenderer(
                        headers = block.headers,
                        rows = block.rows,
                        fontSize = fontSize,
                        textColor = textColor
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = parseMarkdownToAnnotatedString(block.text, textColor),
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize + 5).sp,
                        color = textColor,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private val HighlightKeywords = setOf(
    "package", "import", "class", "interface", "object", "fun", "function", "let", "const", "var", "val", "def", "fn", "pub",
    "impl", "struct", "return", "if", "else", "for", "while", "do", "break", "continue", "when", "match", "switch", "case",
    "default", "try", "catch", "finally", "throw", "new", "in", "as", "is", "typeof", "instanceof", "async", "await", "yield", "from", "type"
)

private val HighlightBuiltins = setOf(
    "true", "false", "null", "nil", "None", "this", "super", "self", "void", "int", "float", "double", "char", "boolean", "long", "short", "byte", "string", "any", "number"
)

private val CodeHighlightRegex = Regex(
    "/\\*(?s).*?\\*/" +                  // Multi-line comment (safe linear non-greedy)
    "|//[^\\n]*" +                       // Single-line comment 1
    "|#[^\\n]*" +                        // Single-line comment 2
    "|\\\"\\\"\\\"(?s).*?\\\"\\\"\\\"" + // Triple-quote string (safe linear non-greedy)
    "|\\\"[^\\\"\\\\\\n]*(?:\\\\.[^\\\"\\\\\\n]*)*\\\"" +  // Double-quoted string (safe single line)
    "|'[^'\\\\\\n]*(?:\\\\.[^'\\\\\\n]*)*'" +              // Single-quoted string (safe single line)
    "|\\b(?:package|import|class|interface|object|fun|function|let|const|var|val|def|fn|pub|impl|struct|return|if|else|for|while|do|break|continue|when|match|switch|case|default|try|catch|finally|throw|new|in|as|is|typeof|instanceof|async|await|yield|from|type)\\b" + // Keywords
    "|\\b(?:true|false|null|nil|None|this|super|self|void|int|float|double|char|boolean|long|short|byte|string|any|number)\\b" + // Builtins
    "|\\b\\d+(?:\\.\\d+)?\\b|0x[0-9a-fA-F]+\\b" + // Numbers
    "|@[a-zA-Z0-9_]+\\b|\\b[A-Z][a-zA-Z0-9_]*\\b"   // Annotations and Types
)

fun highlightCodeBlocks(code: String, language: String, isDark: Boolean): AnnotatedString {
    val builder = AnnotatedString.Builder(code)
    if (code.length > 5000) {
        return builder.toAnnotatedString() // Guarantee fast render and prevent ANR on large responses
    }
    try {
        val matches = CodeHighlightRegex.findAll(code)
        for (match in matches) {
            val range = match.range
            val text = match.value
            
            val style = when {
                text.startsWith("//") || text.startsWith("/*") || text.startsWith("#") -> {
                    SpanStyle(
                        color = if (isDark) Color(0xFF6272A4) else Color(0xFF6A737D),
                        fontStyle = FontStyle.Italic
                    )
                }
                text.startsWith("\"") || text.startsWith("'") -> {
                    SpanStyle(color = if (isDark) Color(0xFFF1FA8C) else Color(0xFF032F62))
                }
                HighlightKeywords.contains(text) -> {
                    SpanStyle(
                        color = if (isDark) Color(0xFFFF79C6) else Color(0xFFD73A49),
                        fontWeight = FontWeight.Bold
                    )
                }
                HighlightBuiltins.contains(text) -> {
                    SpanStyle(color = if (isDark) Color(0xFFBD93F9) else Color(0xFF005CC5))
                }
                text.firstOrNull()?.isDigit() == true || text.startsWith("0x") -> {
                    SpanStyle(color = if (isDark) Color(0xFFBD93F9) else Color(0xFF005CC5))
                }
                text.startsWith("@") || (text.firstOrNull()?.isUpperCase() == true) -> {
                    SpanStyle(
                        color = if (isDark) Color(0xFF8BE9FD) else Color(0xFF6F42C1),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                else -> {
                    SpanStyle(color = if (isDark) Color(0xFF50FA7B) else Color(0xFF22863A))
                }
            }
            builder.addStyle(style, range.start, range.endInclusive + 1)
        }
    } catch (e: Exception) {
        // Fallback gracefully on parsing errors
    }
    return builder.toAnnotatedString()
}

data class ParsedMsgContent(
    val thought: String?,
    val response: String
)

fun parseThoughtAndResponse(content: String): ParsedMsgContent {
    val thinkStart = content.indexOf("<think>")
    if (thinkStart == -1) {
        return ParsedMsgContent(null, content)
    }
    val thinkEnd = content.indexOf("</think>", thinkStart + 7)
    if (thinkEnd == -1) {
        // Streaming thought, or no closing think tag
        val thought = content.substring(thinkStart + 7)
        return ParsedMsgContent(thought, "")
    } else {
        val thought = content.substring(thinkStart + 7, thinkEnd).trim()
        val response = content.substring(thinkEnd + 8).trim()
        return ParsedMsgContent(thought, response)
    }
}

@Composable
fun MarkdownWithCollapsibleThinking(
    text: String,
    fontSize: Int,
    textColor: Color,
    onCopyClicked: (String) -> Unit,
    msgId: Int,
    isStreaming: Boolean
) {
    val parsed = remember(text) { parseThoughtAndResponse(text) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (parsed.thought != null) {
            var userToggledThoughts by rememberSaveable(msgId) { mutableStateOf<Boolean?>(null) }
            val isThinkingRunning = isStreaming && text.contains("<think>") && !text.contains("</think>")
            
            val thoughtsExpanded = remember(userToggledThoughts, isThinkingRunning) {
                if (userToggledThoughts != null) {
                    userToggledThoughts!!
                } else {
                    isThinkingRunning // Show temporarily expanded while actively streaming/thinking
                }
            }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { userToggledThoughts = !thoughtsExpanded },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (thoughtsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (thoughtsExpanded) "Collapse" else "Expand",
                                tint = LogyBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isThinkingRunning) "Thinking..." else "Reasoning Process",
                                fontSize = (fontSize - 1).sp,
                                fontWeight = FontWeight.Bold,
                                color = LogyBlue
                            )
                            if (isThinkingRunning) {
                                Spacer(modifier = Modifier.width(6.dp))
                                StreamingLoadingDots()
                            }
                        }
                        if (!thoughtsExpanded && parsed.thought.isNotBlank()) {
                            Text(
                                text = "Click to view thoughts",
                                fontSize = (fontSize - 3).sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                    
                    if (thoughtsExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = parsed.thought,
                            fontSize = (fontSize - 1).sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            lineHeight = (fontSize + 4).sp
                        )
                    }
                }
            }
        }
        
        val displayText = if (parsed.thought != null) parsed.response else text
        if (displayText.isNotEmpty()) {
            MarkdownTextRenderer(
                text = displayText,
                fontSize = fontSize,
                textColor = textColor,
                onCopyClicked = onCopyClicked
            )
        }
    }
}

// Compact markdown text engine for custom codeblocks rendering in chat bubble
@Composable
fun MarkdownTextRenderer(
    text: String,
    fontSize: Int,
    textColor: Color,
    onCopyClicked: (String) -> Unit,
    fillMaxWidth: Boolean = true
) {
    if (text.contains("```")) {
        val parts = text.split("```")
        Column(
            modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            parts.forEachIndexed { idx, part ->
                if (idx % 2 == 1) {
                    val firstNewLine = part.indexOf("\n")
                    val language = if (firstNewLine != -1) part.substring(0, firstNewLine).trim() else ""
                    val codeContent = if (firstNewLine != -1) part.substring(firstNewLine + 1).trim() else part.trim()
                    
                    val isDark = MaterialTheme.colorScheme.background == PremiumBgDark
                    val codeCardBg = if (isDark) Color(0xFF1E1E2E) else Color(0xFFF8FAFC)
                    val codeCardBorder = if (isDark) Color(0xFF313244) else Color(0xFFE2E8F0)
                    val codeHeaderBg = if (isDark) Color(0xFF11111B) else Color(0xFFEDF2F7)
                    val codeHeaderTitleColor = if (isDark) Color(0xFFC6A0F6) else Color(0xFF4A5568)
                    val copyBtnColor = if (isDark) Color.White else Color(0xFF4A5568)
                    val codeDefaultColor = if (isDark) Color(0xFFCDD6F4) else Color(0xFF24292E)
                    
                    val highlightedCode = remember(codeContent, language, isDark) {
                        highlightCodeBlocks(codeContent, language, isDark)
                    }
                    
                    Card(
                        modifier = (if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
                            .padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = codeCardBg
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, codeCardBorder)
                    ) {
                        Column {
                            // Header Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(codeHeaderBg)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = null,
                                        tint = LogyBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = language.ifEmpty { "CODE" }.uppercase(),
                                        color = codeHeaderTitleColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onCopyClicked(codeContent) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = ContentCopyCustom,
                                        contentDescription = "Copy code",
                                        tint = copyBtnColor,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "COPY",
                                        color = copyBtnColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            // Scrollable Code Content Block
                            Box(
                                modifier = (if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = highlightedCode,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = (fontSize - 1).sp,
                                    lineHeight = (fontSize + 4).sp,
                                    color = codeDefaultColor
                                )
                            }
                        }
                    }
                } else {
                    if (part.isNotBlank()) {
                        ParsedMarkdownColumn(
                            text = part,
                            fontSize = fontSize,
                            textColor = textColor,
                            fillMaxWidth = fillMaxWidth
                        )
                    }
                }
            }
        }
    } else {
        ParsedMarkdownColumn(
            text = text,
            fontSize = fontSize,
            textColor = textColor,
            fillMaxWidth = fillMaxWidth
        )
    }
}

// Elegant dancing dot loading animation representing streaming state
@Composable
fun StreamingLoadingDots(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    
    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0f at 0 with FastOutSlowInEasing
                -6f at 150 with FastOutSlowInEasing
                0f at 300 with FastOutSlowInEasing
                0f at 1000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1"
    )
    
    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0f at 150 with FastOutSlowInEasing
                -6f at 300 with FastOutSlowInEasing
                0f at 450 with FastOutSlowInEasing
                0f at 1000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2"
    )

    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0f at 300 with FastOutSlowInEasing
                -6f at 450 with FastOutSlowInEasing
                0f at 600 with FastOutSlowInEasing
                0f at 1000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot3"
    )

    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Dot 1: Sky Blue
        Box(
            modifier = Modifier
                .size(6.dp)
                .offset(y = dot1Offset.dp)
                .background(LogyBlue, shape = RoundedCornerShape(3.dp))
        )
        // Dot 2: Light Green
        Box(
            modifier = Modifier
                .size(6.dp)
                .offset(y = dot2Offset.dp)
                .background(LogyGreen, shape = RoundedCornerShape(3.dp))
        )
        // Dot 3: Yellow
        Box(
            modifier = Modifier
                .size(6.dp)
                .offset(y = dot3Offset.dp)
                .background(LogyYellow, shape = RoundedCornerShape(3.dp))
        )
    }
}

private var _contentCopyCustom: ImageVector? = null
val ContentCopyCustom: ImageVector
    get() {
        if (_contentCopyCustom != null) {
            return _contentCopyCustom!!
        }
        _contentCopyCustom = ImageVector.Builder(
            name = "ContentCopyCustom",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color(0xFF000000)),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(16f, 1f)
            lineTo(4f, 1f)
            quadTo(2.9f, 1f, 2f, 1.9f)
            lineTo(2f, 17f)
            horizontalLineTo(4f)
            lineTo(4f, 3f)
            horizontalLineTo(16f)
            close()
            
            moveTo(19f, 5f)
            lineTo(8f, 5f)
            quadTo(6.9f, 5f, 6f, 5.9f)
            lineTo(6f, 21f)
            quadTo(6f, 22.1f, 8f, 23f)
            lineTo(19f, 23f)
            quadTo(20.1f, 23f, 21f, 22.1f)
            lineTo(21f, 7f)
            curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f)
            close()
            
            moveTo(19f, 21f)
            lineTo(8f, 21f)
            lineTo(8f, 7f)
            lineTo(19f, 7f)
            lineTo(19f, 21f)
            close()
        }.build()
        return _contentCopyCustom!!
    }

// Collapsible Agent Turn structures and visual layers
sealed class ExecutionStep {
    abstract val id: Int
    abstract val name: String
    abstract val args: String
    abstract val rawMessage: ChatMessage

    data class ToolStart(
        override val id: Int,
        override val name: String,
        override val args: String,
        override val rawMessage: ChatMessage
    ) : ExecutionStep()

    data class ToolRunning(
        override val id: Int,
        override val name: String,
        override val args: String,
        override val rawMessage: ChatMessage
    ) : ExecutionStep()

    data class ToolSuccess(
        override val id: Int,
        override val name: String,
        override val args: String,
        val output: String,
        val durationMs: Long?,
        override val rawMessage: ChatMessage
    ) : ExecutionStep()

    data class ToolFailed(
        override val id: Int,
        override val name: String,
        override val args: String,
        val error: String,
        val durationMs: Long?,
        override val rawMessage: ChatMessage
    ) : ExecutionStep()
}

fun cleanInternalMarkers(text: String): String {
    val lines = text.split("\n")
    val cleanedLines = lines.filter { line ->
        val trimmed = line.trim()
        val lower = trimmed.lowercase()
        val isInternal = trimmed.startsWith("Preparing to execute tool", ignoreCase = true) ||
                         trimmed.startsWith("Preparing...", ignoreCase = true) ||
                         trimmed.startsWith("Executing tool", ignoreCase = true) ||
                         trimmed.startsWith("Executing...", ignoreCase = true) ||
                         trimmed.startsWith("Calling function", ignoreCase = true) ||
                         trimmed.startsWith("Tool name text", ignoreCase = true) ||
                         trimmed.startsWith("Tool arguments", ignoreCase = true) ||
                         trimmed.startsWith("Internal status logs", ignoreCase = true) ||
                         trimmed.startsWith("Tool:", ignoreCase = true) ||
                         trimmed.startsWith("⚙️ Executing", ignoreCase = true) ||
                         trimmed.startsWith("✅ Tool completed", ignoreCase = true) ||
                         trimmed.startsWith("✅ write_file completed", ignoreCase = true) ||
                         trimmed.startsWith("✅ list_dir completed", ignoreCase = true) ||
                         trimmed.startsWith("✅ read_file completed", ignoreCase = true) ||
                         trimmed.startsWith("✅ datetime completed", ignoreCase = true) ||
                         trimmed.startsWith("🚫 Tool execution blocked", ignoreCase = true) ||
                         lower.startsWith("tool:") ||
                         lower.startsWith("preparing to execute") ||
                         lower.startsWith("executing tool") ||
                         lower.startsWith("calling function") ||
                         lower.startsWith("internal status") ||
                         (trimmed.startsWith("{") && trimmed.contains("\"tool\"") && trimmed.endsWith("}"))
        !isInternal
    }
    return cleanedLines.joinToString("\n").trim()
}

data class AgentTurn(
    val id: Int,
    val userMessage: ChatMessage?,
    val executionSteps: List<ExecutionStep>,
    val reasoning: List<String>,
    val finalResponse: String,
    val isStreaming: Boolean = false,
    val isPending: Boolean = false
)

fun groupMessagesIntoTurns(messages: List<ChatMessage>): List<AgentTurn> {
    val turns = mutableListOf<AgentTurn>()
    
    var currentTurnUser: ChatMessage? = null
    val currentTurnMessages = mutableListOf<ChatMessage>()
    var firstMsgIdInTurn = -1
    
    fun commitCurrentTurn() {
        if (currentTurnUser != null || currentTurnMessages.isNotEmpty()) {
            val keyId = currentTurnUser?.id ?: firstMsgIdInTurn
            
            val executionSteps = mutableListOf<ExecutionStep>()
            val reasoning = mutableListOf<String>()
            var finalResponse = ""
            var isStreaming = false
            var isPending = false
            
            // Separate model messages
            val modelMsgs = currentTurnMessages.filter { it.role == "model" }
            
            // Process non-model messages first
            currentTurnMessages.forEach { msg ->
                if (msg.role == "tool") {
                    val name = msg.toolName ?: "Command Pipeline"
                    val args = msg.toolArgs ?: ""
                    val output = msg.toolResult ?: msg.content
                    val status = msg.toolStatus ?: "success"
                    
                    val existingIndex = executionSteps.indexOfFirst { it.name == name && (it is ExecutionStep.ToolStart || it is ExecutionStep.ToolRunning) }
                    
                    val step = when (status) {
                        "pending", "approved" -> ExecutionStep.ToolStart(msg.id, name, args, msg)
                        "executing" -> ExecutionStep.ToolRunning(msg.id, name, args, msg)
                        "success" -> ExecutionStep.ToolSuccess(msg.id, name, args, output, msg.durationMs, msg)
                        "error", "denied" -> ExecutionStep.ToolFailed(msg.id, name, args, output, msg.durationMs, msg)
                        else -> {
                            if (output.startsWith("❌") || output.contains("failed", ignoreCase = true)) {
                                ExecutionStep.ToolFailed(msg.id, name, args, output, msg.durationMs, msg)
                            } else {
                                ExecutionStep.ToolSuccess(msg.id, name, args, output, msg.durationMs, msg)
                            }
                        }
                    }
                    
                    if (existingIndex != -1) {
                        executionSteps[existingIndex] = step
                    } else {
                        executionSteps.add(step)
                    }
                } else if (msg.role == "system") {
                    if (msg.content.isNotBlank() && !msg.content.contains("WebSocket terminal session closed")) {
                        reasoning.add(msg.content)
                    }
                }
            }
            
            // Now process model messages inside this turn
            modelMsgs.forEachIndexed { index, msg ->
                val parsed = parseThoughtAndResponse(msg.content)
                
                // If there's a thought block, add it to reasoning
                if (!parsed.thought.isNullOrBlank()) {
                    reasoning.add(parsed.thought)
                }
                
                val cleanResp = cleanInternalMarkers(parsed.response.trim())
                
                // Check if this is the final assistant response
                val isLastModelMsg = index == modelMsgs.lastIndex
                if (isLastModelMsg) {
                    if (msg.status == "Streaming") {
                        isStreaming = true
                    }
                    if (msg.status == "Pending") {
                        isPending = true
                    }
                    
                    finalResponse = cleanResp
                } else {
                    // Intermediate model message - treat its clean text as reasoning/explanation log
                    if (cleanResp.isNotEmpty()) {
                        reasoning.add(cleanResp)
                    }
                }
            }
            
            turns.add(
                AgentTurn(
                    id = keyId,
                    userMessage = currentTurnUser,
                    executionSteps = executionSteps,
                    reasoning = reasoning,
                    finalResponse = finalResponse,
                    isStreaming = isStreaming,
                    isPending = isPending
                )
            )
            
            currentTurnMessages.clear()
            currentTurnUser = null
        }
    }
    
    for (msg in messages) {
        if (msg.role == "user") {
            commitCurrentTurn()
            currentTurnUser = msg
            firstMsgIdInTurn = msg.id
        } else {
            if (firstMsgIdInTurn == -1) {
                firstMsgIdInTurn = msg.id
            }
            currentTurnMessages.add(msg)
        }
    }
    commitCurrentTurn()
    
    return turns
}

@Composable
fun ChatTurnView(
    turn: AgentTurn,
    fontSize: Int,
    profileName: String,
    onCopyText: (String) -> Unit,
    onApproveTool: (ChatMessage) -> Unit,
    onRetry: () -> Unit,
    onEditMessage: ((String) -> Unit)? = null,
    traceExpansionStates: Map<Int, Boolean>,
    onToggleTraceExpansion: (Int, Boolean) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == PremiumBgDark
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. User Message (always visible styled as normal speech bubble)
        if (turn.userMessage != null) {
            MessageBubble(
                msg = turn.userMessage,
                fontSize = fontSize,
                profileName = profileName,
                onCopyText = onCopyText,
                onApproveTool = {},
                onRetry = {},
                onEditMessage = onEditMessage
            )
        }

        // 2. Single Unified Assistant Message Container for everything else
        val hasAssistantContent = turn.executionSteps.isNotEmpty() || turn.reasoning.isNotEmpty() || turn.finalResponse.isNotEmpty() || turn.isStreaming || turn.isPending
        if (hasAssistantContent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                // Left side LogyAvatar
                val isThinking = turn.isStreaming || turn.isPending || turn.executionSteps.any { it is ExecutionStep.ToolStart || it is ExecutionStep.ToolRunning }
                LogyAvatar(
                    modifier = Modifier.size(32.dp),
                    isThinking = isThinking
                )
                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Label
                    Text(
                        text = "❖ logy",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = LogyBlue,
                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                    )

                    // Contents column
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 12.dp)
                    ) {
                        // A. Collapsible Execution Trace (if any trace)
                        val hasTrace = turn.executionSteps.isNotEmpty() || turn.reasoning.isNotEmpty()
                        if (hasTrace) {
                            AgentExecutionTrace(
                                turn = turn,
                                fontSize = fontSize,
                                onCopyText = onCopyText,
                                onApproveTool = onApproveTool,
                                expanded = traceExpansionStates[turn.id] ?: turn.executionSteps.any { it is ExecutionStep.ToolStart || it is ExecutionStep.ToolRunning },
                                onToggleExpand = { expanded -> onToggleTraceExpansion(turn.id, expanded) }
                            )
                        }

                        // B. Final Assistant Answers (rendered directly, without secondary avatars)
                        val shouldShowFinalResponseSection = turn.finalResponse.isNotEmpty() ||
                                ((turn.isStreaming || turn.isPending) && turn.executionSteps.isEmpty())

                        if (shouldShowFinalResponseSection) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                if (turn.finalResponse.isEmpty() && (turn.isPending || turn.isStreaming)) {
                                    StreamingLoadingDots(modifier = Modifier.padding(vertical = 8.dp))
                                } else {
                                    Column {
                                        MarkdownWithCollapsibleThinking(
                                            text = turn.finalResponse,
                                            fontSize = fontSize,
                                            textColor = MaterialTheme.colorScheme.onSurface,
                                            onCopyClicked = onCopyText,
                                            msgId = turn.id,
                                            isStreaming = turn.isStreaming
                                        )
                                        val isThinkingRunning = turn.isStreaming &&
                                                turn.finalResponse.contains("<think>") &&
                                                !turn.finalResponse.contains("</think>")

                                        if (turn.isStreaming && !isThinkingRunning) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            StreamingLoadingDots(modifier = Modifier.padding(start = 4.dp))
                                        }
                                    }
                                }
                            }

                            if (turn.finalResponse.trim().isNotEmpty() && !turn.isStreaming && !turn.isPending) {
                                val basicIconColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF1F2937)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, bottom = 4.dp)
                                ) {
                                    IconButton(
                                        onClick = { onCopyText(turn.finalResponse) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = ContentCopyCustom,
                                            contentDescription = "Copy response",
                                            tint = basicIconColor.copy(alpha = 0.55f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = onRetry,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Retry prompt",
                                            tint = basicIconColor.copy(alpha = 0.55f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class TimelineStep(
    val title: String,
    val subtitle: String? = null,
    val accentColor: Color,
    val statusText: String? = null,
    val statusColor: Color? = null,
    val isPulsing: Boolean = false,
    val content: @Composable () -> Unit
)

@Composable
fun ToolExecutionCard(
    msg: ChatMessage,
    fontSize: Int,
    isExpanded: Boolean,
    onToggleExpand: (Boolean) -> Unit,
    currentSuccessColor: Color,
    currentWarningColor: Color,
    currentErrorColor: Color,
    terminalBgColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    onApproveTool: (ChatMessage) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == PremiumBgDark

    val cardBg = if (isDark) Color(0xFF1E1E2E) else Color(0xFFF8FAFC)
    val cardBorder = if (isDark) Color(0x33FFFFFF) else Color(0xFFE2E8F0)

    val statusText = when (msg.toolStatus) {
        "pending" -> "Pending Approval"
        "executing" -> "Executing..."
        "approved" -> "Approved & Starting"
        "success" -> "Completed"
        "denied" -> "Blocked by policies"
        "error" -> "Failed"
        else -> "Completed"
    }

    val statusColor = when (msg.toolStatus) {
        "success", "approved" -> currentSuccessColor
        "error", "denied" -> currentErrorColor
        "pending", "executing" -> currentWarningColor
        else -> currentSuccessColor
    }

    val statusIcon = when (msg.toolStatus) {
        "success", "approved" -> Icons.Default.CheckCircle
        "error", "denied" -> Icons.Default.Warning
        "pending", "executing" -> Icons.Default.Refresh
        else -> Icons.Default.CheckCircle
    }

    val toolIcon = when {
        msg.toolName?.contains("gmail", ignoreCase = true) == true -> "📧 "
        msg.toolName?.contains("calendar", ignoreCase = true) == true -> "📅 "
        msg.toolName?.contains("contacts", ignoreCase = true) == true -> "👥 "
        msg.toolName?.contains("sheet", ignoreCase = true) == true -> "📊 "
        msg.toolName?.contains("search", ignoreCase = true) == true -> "🔍 "
        msg.toolName?.contains("db", ignoreCase = true) == true || msg.toolName?.contains("sql", ignoreCase = true) == true -> "🗄️ "
        msg.toolName?.contains("code", ignoreCase = true) == true || msg.toolName?.contains("shell", ignoreCase = true) == true -> "💻 "
        else -> "🔧 "
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand(!isExpanded) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "$toolIcon${msg.toolName ?: "Command Pipeline"}",
                        fontSize = (fontSize - 1).sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimaryColor,
                        fontFamily = FontFamily.Monospace
                    )
                    if (msg.durationMs != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${msg.durationMs}ms",
                            fontSize = (fontSize - 3).sp,
                            color = textSecondaryColor.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (msg.toolStatus == "pending" || msg.toolStatus == "executing") {
                    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
                    val angle by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "rotate"
                    )
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Executing",
                        tint = statusColor,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer(rotationZ = angle)
                    )
                } else {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = statusText,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = statusText,
                fontSize = (fontSize - 3).sp,
                fontWeight = FontWeight.Medium,
                color = statusColor,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            )

            if (isExpanded) {
                HorizontalDivider(color = cardBorder.copy(alpha = 0.4f), thickness = 1.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column {
                        Text(
                            text = "Arguments",
                            fontSize = (fontSize - 3).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondaryColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = terminalBgColor),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg.toolArgs ?: "{}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = (fontSize - 3).sp,
                                color = textPrimaryColor.copy(alpha = 0.9f),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    if (msg.toolStatus == "pending") {
                        Button(
                            onClick = { onApproveTool(msg) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Approve & Enable Execution",
                                fontWeight = FontWeight.Bold,
                                fontSize = (fontSize - 1).sp
                            )
                        }
                    } else if (msg.content.trim().isNotEmpty()) {
                        Column {
                            val outputHeader = if (msg.toolStatus == "error") "Execution Error Log" else "Output"
                            val outputColor = if (msg.toolStatus == "error") currentErrorColor else currentSuccessColor
                            
                            Text(
                                text = outputHeader,
                                fontSize = (fontSize - 3).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textSecondaryColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = terminalBgColor),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = msg.content,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = (fontSize - 3).sp,
                                    color = outputColor,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    } else if (msg.toolStatus == "denied") {
                        Column {
                            Text(
                                text = "Output",
                                fontSize = (fontSize - 3).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textSecondaryColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = terminalBgColor),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Secure Sandbox Action: Blocked by policies.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = (fontSize - 3).sp,
                                    color = currentErrorColor,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgentExecutionTrace(
    turn: AgentTurn,
    fontSize: Int,
    onCopyText: (String) -> Unit,
    onApproveTool: (ChatMessage) -> Unit,
    expanded: Boolean,
    onToggleExpand: (Boolean) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == PremiumBgDark
    val currentSuccessColor = if (isDark) SuccessSemantic else Color(0xFF15803D)
    val currentWarningColor = if (isDark) WarningSemantic else Color(0xFF854D0E)
    val currentErrorColor = if (isDark) ErrorSemantic else Color(0xFFB91C1C)
    val currentLogColor = if (isDark) LogyBlue else Color(0xFF1D4ED8)
    val terminalBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

    var toolExpansionStates by rememberSaveable { mutableStateOf(mapOf<Int, Boolean>()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )

        // 1. Render Reasoning/Thinking steps
        turn.reasoning.forEachIndexed { idx, thoughtText ->
            val isThinking = turn.isStreaming && idx == turn.reasoning.lastIndex
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF161B22) else Color(0xFFF0F2F5)
                ),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    currentLogColor.copy(alpha = 0.15f)
                )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(currentLogColor, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isThinking) "Thinking" else "Reasoning Step",
                                fontSize = (fontSize - 1).sp,
                                fontWeight = FontWeight.Bold,
                                color = currentLogColor
                            )
                        }
                        if (isThinking) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = currentLogColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = currentLogColor,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = thoughtText,
                        fontSize = (fontSize - 1).sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                        lineHeight = (fontSize + 3).sp
                    )
                }
            }
        }

        // 2. Render Tool Calls
        turn.executionSteps.forEach { step ->
            val statusColor = when (step) {
                is ExecutionStep.ToolStart -> currentWarningColor
                is ExecutionStep.ToolRunning -> currentWarningColor
                is ExecutionStep.ToolSuccess -> currentSuccessColor
                is ExecutionStep.ToolFailed -> currentErrorColor
            }
            val statusLabel = when (step) {
                is ExecutionStep.ToolStart -> "WAITING"
                is ExecutionStep.ToolRunning -> "RUNNING"
                is ExecutionStep.ToolSuccess -> "SUCCESS"
                is ExecutionStep.ToolFailed -> "FAILED"
            }
            val isPending = step is ExecutionStep.ToolStart || step is ExecutionStep.ToolRunning
            val isExpanded = toolExpansionStates[step.id] ?: isPending

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF8FAFC)
                ),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    statusColor.copy(alpha = 0.25f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                toolExpansionStates = toolExpansionStates + (step.id to !isExpanded)
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "Tool Call",
                                tint = statusColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tool Call: ${step.name}",
                                fontSize = (fontSize - 1).sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimaryColor
                            )
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = statusColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = statusLabel,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle Details",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (isExpanded) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                            thickness = 1.dp
                        )
                        Box(modifier = Modifier.padding(8.dp)) {
                            ToolExecutionCard(
                                msg = step.rawMessage,
                                fontSize = fontSize,
                                isExpanded = true,
                                onToggleExpand = {},
                                currentSuccessColor = currentSuccessColor,
                                currentWarningColor = currentWarningColor,
                                currentErrorColor = currentErrorColor,
                                terminalBgColor = terminalBgColor,
                                textPrimaryColor = textPrimaryColor,
                                textSecondaryColor = textSecondaryColor,
                                onApproveTool = onApproveTool
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Modifier.scaleOnClick(
    scaleAmount: Float = 0.98f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleAmount else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "scale"
    )
    return this
        .graphicsLayer(scaleX = scale, scaleY = scale)
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current,
            onClick = onClick
        )
}


@Composable
fun UnifiedAgentStateBubble(
    agentState: AgentState,
    currentToolName: String?,
    fontSize: Int,
    isDark: Boolean
) {
    val bubbleColor = when (agentState) {
        AgentState.EXECUTING_TOOL -> if (isDark) Color(0xFF1E1E1E) else Color(0xFFFEF3C7) // Amber/Yellow
        AgentState.WAITING_APPROVAL -> if (isDark) Color(0xFF1E1E1E) else Color(0xFFFEE2E2) // Red/Pink
        AgentState.THINKING -> if (isDark) Color(0xFF1E1E1E) else Color(0xFFEFF6FF) // Blue
        AgentState.TYPING -> if (isDark) Color(0xFF1E1E1E) else Color(0xFFECFDF5) // Green
        else -> if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F4F6)
    }

    val textColor = when (agentState) {
        AgentState.EXECUTING_TOOL -> Color(0xFFD97706)
        AgentState.WAITING_APPROVAL -> Color(0xFFDC2626)
        AgentState.THINKING -> Color(0xFF2563EB)
        AgentState.TYPING -> Color(0xFF059669)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val labelText = when (agentState) {
        AgentState.EXECUTING_TOOL -> "Executing: ${currentToolName ?: "tool"}..."
        AgentState.WAITING_APPROVAL -> "Awaiting verification..."
        AgentState.THINKING -> "Thinking..."
        AgentState.TYPING -> "logy is typing..."
        else -> agentState.label
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        LogyAvatar(modifier = Modifier.size(28.dp), isThinking = true)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = "❖ logy",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = LogyBlue,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            )
            Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                color = bubbleColor,
                shadowElevation = 1.dp,
                border = if (agentState == AgentState.EXECUTING_TOOL) {
                    androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFBBC05).copy(alpha = 0.5f))
                } else null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (agentState == AgentState.EXECUTING_TOOL) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Tool",
                            tint = Color(0xFFFBBC05),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = labelText,
                        color = textColor,
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StreamingLoadingDots()
                }
            }
        }
    }
}

