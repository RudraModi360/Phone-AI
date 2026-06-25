package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TerminalBg
import com.example.ui.theme.TerminalGreen
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var bootLines by remember { mutableStateOf(listOf<String>()) }
    var bootProgress by remember { mutableStateOf(0f) }
    
    val lines = listOf(
        "~ $ logy-app init",
        "> Profile loading: default",
        "> Environment integrity ... SECURE",
        "> Connecting local Daemon socket ... OK",
        "> Service bindings ... ACTIVE",
        "~ $ Ready to execute system tools"
    )

    LaunchedEffect(Unit) {
        for (i in lines.indices) {
            bootLines = bootLines + lines[i]
            bootProgress = (i + 1).toFloat() / lines.size
            delay(400)
        }
        delay(600)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            // Elegant pulsing logy Avatar as the splash icon logo
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                LogyAvatar(
                    modifier = Modifier.size(110.dp),
                    isThinking = true
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Title
            Text(
                text = "logy terminal",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Text(
                text = "Your Mobile System Terminal Assistant",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp, bottom = 32.dp)
            )

            // Terminal Boot screen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color.Black.copy(alpha = 0.7f), shape = MaterialTheme.shapes.medium)
                    .padding(16.dp)
            ) {
                Column {
                    bootLines.forEach { line ->
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = if (line.startsWith("~")) TerminalGreen else Color.White,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    if (bootLines.size < lines.size) {
                        // Flashing Terminal Cursor
                        val blink = rememberInfiniteTransition(label = "cursor")
                        val alpha by blink.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(500, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "blink"
                        )
                        Text(
                            text = "▋",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TerminalGreen,
                            modifier = Modifier.alpha(alpha)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // System loading slider
            LinearProgressIndicator(
                progress = { bootProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = Color(0xFF6C9CE0),
                trackColor = Color.White.copy(alpha = 0.1f)
            )
        }
    }
}
