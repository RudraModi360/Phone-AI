package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LogyBlue
import com.example.ui.theme.LogyPurple
import com.example.ui.theme.PremiumBgDark

/**
 * Onboarding screen shown to new users on first app launch.
 * Collects name, role, and optional system prompt customization.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: (name: String, role: String, systemPrompt: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableStateOf(0) }
    var userName by remember { mutableStateOf("") }
    var userRole by remember { mutableStateOf("") }
    var customSystemPrompt by remember { mutableStateOf("") }
    var selectedRolePreset by remember { mutableStateOf<String?>(null) }
    
    val isDark = MaterialTheme.colorScheme.background == PremiumBgDark
    val scrollState = rememberScrollState()
    
    // Role presets with descriptions
    val rolePresets = listOf(
        RolePreset("Developer", "Software development, coding assistance", "💻"),
        RolePreset("Student", "Learning, research, homework help", "📚"),
        RolePreset("Designer", "UI/UX, graphics, creative work", "🎨"),
        RolePreset("Writer", "Content creation, editing, writing", "✍️"),
        RolePreset("Researcher", "Data analysis, academic research", "🔬"),
        RolePreset("Business", "Productivity, management, planning", "📊"),
        RolePreset("Other", "General purpose assistant", "🌟")
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(Color(0xFF0D1117), Color(0xFF161B22), Color(0xFF0D1117))
                    } else {
                        listOf(Color(0xFFF8F9FA), Color(0xFFFFFFFF), Color(0xFFF0F4F8))
                    }
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            // Logo and Welcome Header
            LogyAvatar(
                modifier = Modifier
                    .size(100.dp)
                    .shadow(12.dp, CircleShape)
                    .background(
                        color = if (isDark) Color(0xFF1E293B) else Color.White,
                        shape = CircleShape
                    )
                    .padding(12.dp),
                isThinking = false
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Welcome to Logy",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                text = "Your AI-powered assistant",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Progress Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val isActive = index <= currentStep
                    Box(
                        modifier = Modifier
                            .size(if (index == currentStep) 12.dp else 8.dp)
                            .background(
                                color = if (isActive) LogyBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                    )
                    if (index < 2) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(2.dp)
                                .background(
                                    color = if (index < currentStep) LogyBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Step Content
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                },
                label = "step_animation"
            ) { step ->
                when (step) {
                    0 -> NameStep(
                        name = userName,
                        onNameChange = { userName = it },
                        isDark = isDark
                    )
                    1 -> RoleStep(
                        selectedRole = userRole,
                        selectedPreset = selectedRolePreset,
                        rolePresets = rolePresets,
                        onRoleSelected = { role, preset ->
                            userRole = role
                            selectedRolePreset = preset
                        },
                        isDark = isDark
                    )
                    2 -> SystemPromptStep(
                        systemPrompt = customSystemPrompt,
                        onSystemPromptChange = { customSystemPrompt = it },
                        userRole = userRole,
                        isDark = isDark
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))
            
            // Navigation Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Back button (not on first step)
                if (currentStep > 0) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = ButtonDefaults.outlinedButtonBorder
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back", fontWeight = FontWeight.Medium)
                    }
                }
                
                // Next / Complete button
                Button(
                    onClick = {
                        if (currentStep < 2) {
                            currentStep++
                        } else {
                            // Complete onboarding
                            onComplete(
                                userName.ifBlank { "User" },
                                userRole.ifBlank { "General User" },
                                customSystemPrompt
                            )
                        }
                    },
                    enabled = when (currentStep) {
                        0 -> userName.isNotBlank()
                        1 -> userRole.isNotBlank()
                        else -> true
                    },
                    modifier = Modifier
                        .weight(if (currentStep > 0) 1f else 2f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LogyBlue,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (currentStep < 2) "Continue" else "Get Started",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (currentStep < 2) Icons.Default.ArrowForward else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            // Skip option on system prompt step
            if (currentStep == 2 && customSystemPrompt.isBlank()) {
                TextButton(
                    onClick = {
                        onComplete(
                            userName.ifBlank { "User" },
                            userRole.ifBlank { "General User" },
                            ""
                        )
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "Skip customization",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun NameStep(
    name: String,
    onNameChange: (String) -> Unit,
    isDark: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "What's your name?",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Text(
            text = "This helps personalize your experience",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
        
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = { 
                Text(
                    "Enter your name",
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                ) 
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = LogyBlue
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LogyBlue,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun RoleStep(
    selectedRole: String,
    selectedPreset: String?,
    rolePresets: List<RolePreset>,
    onRoleSelected: (String, String?) -> Unit,
    isDark: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "What do you do?",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Text(
            text = "Select your role for tailored assistance",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
        
        // Role preset grid
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            rolePresets.chunked(2).forEach { rowPresets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowPresets.forEach { preset ->
                        val isSelected = selectedPreset == preset.name
                        
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { 
                                    onRoleSelected(preset.name, preset.name) 
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) 
                                    LogyBlue.copy(alpha = 0.12f) 
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = if (isSelected) {
                                androidx.compose.foundation.BorderStroke(2.dp, LogyBlue)
                            } else null,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = preset.icon,
                                    fontSize = 24.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = preset.name,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) LogyBlue else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = preset.description,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                    
                    // Fill empty space if odd number
                    if (rowPresets.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Custom role input
        OutlinedTextField(
            value = if (selectedPreset == null || selectedPreset == "Other") selectedRole else "",
            onValueChange = { onRoleSelected(it, null) },
            placeholder = { Text("Or enter custom role...", fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LogyBlue,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun SystemPromptStep(
    systemPrompt: String,
    onSystemPromptChange: (String) -> Unit,
    userRole: String,
    isDark: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Customize your assistant",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Text(
            text = "Optional: Add specific instructions for the AI",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
        
        // Suggestion chips based on role
        val suggestions = when (userRole.lowercase()) {
            "developer" -> listOf(
                "Always explain code with comments",
                "Prefer concise solutions",
                "Use best practices"
            )
            "student" -> listOf(
                "Explain concepts simply",
                "Provide examples",
                "Help me understand step by step"
            )
            "writer" -> listOf(
                "Help with grammar and style",
                "Suggest creative ideas",
                "Keep tone professional"
            )
            else -> listOf(
                "Be concise and direct",
                "Explain your reasoning",
                "Ask clarifying questions"
            )
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.take(2).forEach { suggestion ->
                SuggestionChip(
                    onClick = {
                        val newPrompt = if (systemPrompt.isBlank()) {
                            suggestion
                        } else {
                            "$systemPrompt. $suggestion"
                        }
                        onSystemPromptChange(newPrompt)
                    },
                    label = { 
                        Text(
                            text = suggestion,
                            fontSize = 11.sp,
                            maxLines = 1
                        ) 
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }
        
        OutlinedTextField(
            value = systemPrompt,
            onValueChange = onSystemPromptChange,
            placeholder = { 
                Text(
                    "E.g., Always respond in a friendly tone. Focus on practical solutions...",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                ) 
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LogyBlue,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = LogyBlue.copy(alpha = 0.08f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = LogyBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "These instructions will guide how Logy responds to you. You can change this anytime in Settings.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

private data class RolePreset(
    val name: String,
    val description: String,
    val icon: String
)
