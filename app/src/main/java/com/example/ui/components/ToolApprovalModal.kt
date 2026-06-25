package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.ui.theme.*
import org.json.JSONObject

@Composable
fun ToolApprovalModal(
    toolMessage: ChatMessage,
    onResult: (remember: Boolean, approved: Boolean) -> Unit
) {
    var rememberMyChoice by remember { mutableStateOf(false) }

    val toolArgsJson = try {
        JSONObject(toolMessage.toolArgs ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }

    val cmd = toolArgsJson.optString("command", toolArgsJson.optString("path", ""))

    val riskColor = when (toolMessage.riskLevel?.lowercase()) {
        "high" -> ErrorSemantic
        "medium" -> WarningSemantic
        else -> SuccessSemantic
    }

    Dialog(onDismissRequest = { onResult(false, false) }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Shield Icon",
                        tint = riskColor,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = "Tool Approval Request",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Subtitle
                Text(
                    text = "logy wants to execute the following administrative command:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Command Code block view
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TerminalBg, shape = RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = cmd,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TerminalGreen,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Metadata Metrics Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Risk level
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = riskColor.copy(alpha = 0.15f),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text(
                                text = "Risk level: ${toolMessage.riskLevel?.uppercase() ?: "MEDIUM"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = riskColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Scope Access
                    Text(
                        text = "Access: System Shell",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Mock System Directory Preview for shell_exec style
                Text(
                    text = "📂 Preview (affected workspace targets):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("📁 .git/", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        Text("📁 src/components/", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        Text("📄 package.json", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = rememberMyChoice,
                        onCheckedChange = { rememberMyChoice = it }
                    )
                    Text(
                        text = "Remember my choice and authorize automatically",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action controls layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onResult(rememberMyChoice, false) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ErrorSemantic
                        )
                    ) {
                        Text("Deny", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { onResult(rememberMyChoice, true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Allow Once", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onResult(true, true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TerminalBg,
                        contentColor = TerminalGreen
                    )
                ) {
                    Text("Always Allow", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
