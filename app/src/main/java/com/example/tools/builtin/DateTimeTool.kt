package com.example.tools.builtin

import com.example.service.Parameters
import com.example.service.PropertySchema
import com.example.tools.BaseTool
import com.example.tools.RiskLevel
import com.example.tools.ToolResult
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class DateTimeTool : BaseTool {
    override val name = "datetime"
    override val description = "Get current date/time or perform date calculations"
    override val riskLevel = RiskLevel.SAFE
    
    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "operation" to PropertySchema(
                type = "STRING",
                description = "Operation to perform: 'now', 'add_days', 'format', 'parse'"
            ),
            "timezone" to PropertySchema(
                type = "STRING",
                description = "Timezone (default UTC)"
            ),
            "format" to PropertySchema(
                type = "STRING",
                description = "Date format pattern"
            )
        ),
        required = emptyList()
    )
    
    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val operation = args["operation"] as? String ?: "now"
        val timezone = args["timezone"] as? String ?: "UTC"
        val format = args["format"] as? String ?: "yyyy-MM-dd HH:mm:ss z"
        
        return try {
            val zone = ZoneId.of(timezone)
            val now = ZonedDateTime.now(zone)
            val formatter = DateTimeFormatter.ofPattern(format)
            
            val result = when (operation) {
                "now" -> now.format(formatter)
                else -> now.format(formatter)
            }
            
            ToolResult.success(result, mapOf(
                "timezone" to timezone,
                "epoch" to now.toEpochSecond()
            ))
        } catch (e: Exception) {
            ToolResult.error("DateTime operation failed: ${e.message}")
        }
    }
}
