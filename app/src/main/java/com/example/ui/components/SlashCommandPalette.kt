package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TerminalPurple

data class SlashCommand(
    val command: String,
    val description: String,
    val category: String = "COMMANDS"
)

@Composable
fun SlashCommandPalette(
    onCommandSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val commands = listOf(
        SlashCommand("/help", "Show help and all terminal options", "COMMANDS"),
        SlashCommand("/explain", "Deep analysis of projects and code segments", "COMMANDS"),
        SlashCommand("/code", "Instantly generate modular source structures", "COMMANDS"),
        SlashCommand("/fix", "Scan active structures to diagnose codebugs", "COMMANDS"),
        SlashCommand("/summarize", "Present high-density overview of context details", "COMMANDS"),
        SlashCommand("/deploy", "Compile code files and push directly to staging", "WORKFLOWS")
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(8.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "⚡ QUICK COMMANDS",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val grouped = commands.groupBy { it.category }
                grouped.forEach { (category, list) ->
                    item {
                        Text(
                            text = category,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 4.dp)
                        )
                    }
                    items(list) { cmd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCommandSelected(cmd.command) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = cmd.command,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (cmd.category == "WORKFLOWS") TerminalPurple else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = cmd.description,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    }
                }
            }
        }
    }
}
