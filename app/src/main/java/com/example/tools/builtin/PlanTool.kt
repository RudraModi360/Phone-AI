package com.example.tools.builtin

import com.example.data.PlanDao
import com.example.planner.PlanService
import com.example.planner.PlanStatus
import com.example.service.Parameters
import com.example.service.PropertySchema
import com.example.tools.BaseTool
import com.example.tools.RiskLevel
import com.example.tools.ToolResult
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Tool for creating and managing execution plans.
 * Allows the agent to break down complex tasks into steps.
 */
class PlanTool(private val planService: PlanService) : BaseTool {
    override val name = "plan"
    override val description = "Create, view, or manage execution plans for complex multi-step tasks"
    override val riskLevel = RiskLevel.SAFE
    
    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "action" to PropertySchema(
                type = "STRING",
                description = "Action: create, list, view, submit, approve, reject"
            ),
            "title" to PropertySchema(
                type = "STRING",
                description = "Plan title (for create action)"
            ),
            "description" to PropertySchema(
                type = "STRING",
                description = "Plan description (for create action)"
            ),
            "steps" to PropertySchema(
                type = "STRING",
                description = "JSON array of steps: [{\"description\": \"...\", \"estimated_turns\": 1}]"
            ),
            "plan_id" to PropertySchema(
                type = "STRING",
                description = "Plan ID (for view/submit/approve/reject actions)"
            ),
            "reason" to PropertySchema(
                type = "STRING",
                description = "Reason for plan or rejection reason"
            )
        ),
        required = listOf("action")
    )
    
    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val action = args["action"] as? String
            ?: return ToolResult.error("Missing 'action' argument")
        
        return try {
            when (action.lowercase()) {
                "create" -> createPlan(args)
                "list" -> listPlans()
                "view" -> viewPlan(args)
                "submit" -> submitPlan(args)
                "approve" -> approvePlan(args)
                "reject" -> rejectPlan(args)
                "enter_mode" -> enterPlanMode(args)
                "exit_mode" -> exitPlanMode()
                else -> ToolResult.error("Unknown action: $action. Valid: create, list, view, submit, approve, reject")
            }
        } catch (e: Exception) {
            ToolResult.error("Plan operation failed: ${e.message}")
        }
    }
    
    private suspend fun createPlan(args: Map<String, Any?>): ToolResult {
        val title = args["title"] as? String
            ?: return ToolResult.error("Missing 'title' for create action")
        
        val description = args["description"] as? String ?: ""
        val reason = args["reason"] as? String ?: ""
        
        val stepsJson = args["steps"] as? String
        val steps = if (stepsJson != null) {
            try {
                val arr = JSONArray(stepsJson)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    mapOf(
                        "description" to obj.optString("description", ""),
                        "estimated_turns" to obj.optInt("estimated_turns", 1)
                    )
                }
            } catch (e: Exception) {
                return ToolResult.error("Invalid steps JSON: ${e.message}")
            }
        } else {
            emptyList()
        }
        
        val plan = planService.createPlan(title, description, steps, reason)
        
        val output = buildString {
            appendLine("✅ Plan created successfully!")
            appendLine("Plan ID: ${plan.id}")
            appendLine("Title: ${plan.title}")
            appendLine("Status: ${plan.status}")
            if (steps.isNotEmpty()) {
                appendLine("\nSteps (${steps.size}):")
                steps.forEachIndexed { i, step ->
                    appendLine("  ${i + 1}. ${step["description"]}")
                }
            }
        }
        
        return ToolResult.success(output, mapOf("plan_id" to plan.id))
    }
    
    private suspend fun listPlans(): ToolResult {
        val plans = planService.getAllPlans().first()
        
        if (plans.isEmpty()) {
            return ToolResult.success("No plans found.", mapOf("count" to 0))
        }
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val output = buildString {
            appendLine("📋 Plans (${plans.size}):")
            appendLine()
            plans.forEach { plan ->
                val date = dateFormat.format(Date(plan.createdAt))
                val statusIcon = when (plan.status) {
                    "draft" -> "📝"
                    "pending" -> "⏳"
                    "approved" -> "✅"
                    "in_progress" -> "🔄"
                    "completed" -> "✓"
                    "rejected" -> "❌"
                    else -> "?"
                }
                appendLine("$statusIcon [${plan.id}] ${plan.title}")
                appendLine("   Status: ${plan.status} | Created: $date")
            }
        }
        
        return ToolResult.success(output, mapOf("count" to plans.size))
    }
    
    private suspend fun viewPlan(args: Map<String, Any?>): ToolResult {
        val planId = args["plan_id"] as? String
            ?: return ToolResult.error("Missing 'plan_id' for view action")
        
        val plans = planService.getAllPlans().first()
        val plan = plans.find { it.id == planId }
            ?: return ToolResult.error("Plan not found: $planId")
        
        val steps = planService.getStepsForPlan(planId).first()
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val output = buildString {
            appendLine("📋 Plan: ${plan.title}")
            appendLine("─".repeat(40))
            appendLine("ID: ${plan.id}")
            appendLine("Status: ${plan.status}")
            appendLine("Created: ${dateFormat.format(Date(plan.createdAt))}")
            if (plan.description.isNotEmpty()) {
                appendLine("Description: ${plan.description}")
            }
            if (plan.reason.isNotEmpty()) {
                appendLine("Reason: ${plan.reason}")
            }
            
            if (steps.isNotEmpty()) {
                appendLine()
                appendLine("Steps (${steps.size}):")
                steps.forEach { step ->
                    val icon = when (step.status) {
                        "pending" -> "○"
                        "in_progress" -> "◐"
                        "completed" -> "●"
                        "skipped" -> "⊘"
                        "failed" -> "✗"
                        else -> "?"
                    }
                    appendLine("  $icon ${step.stepOrder}. ${step.description}")
                    appendLine("    Status: ${step.status} | Est: ${step.estimatedTurns} turns")
                }
            }
        }
        
        return ToolResult.success(output, mapOf("plan_id" to planId, "step_count" to steps.size))
    }
    
    private suspend fun submitPlan(args: Map<String, Any?>): ToolResult {
        val planId = args["plan_id"] as? String
            ?: return ToolResult.error("Missing 'plan_id' for submit action")
        
        planService.submitPlan(planId)
        return ToolResult.success("✅ Plan $planId submitted for approval.", mapOf("plan_id" to planId))
    }
    
    private suspend fun approvePlan(args: Map<String, Any?>): ToolResult {
        val planId = args["plan_id"] as? String
            ?: return ToolResult.error("Missing 'plan_id' for approve action")
        
        planService.approvePlan(planId)
        return ToolResult.success("✅ Plan $planId approved and ready for execution.", mapOf("plan_id" to planId))
    }
    
    private suspend fun rejectPlan(args: Map<String, Any?>): ToolResult {
        val planId = args["plan_id"] as? String
            ?: return ToolResult.error("Missing 'plan_id' for reject action")
        
        val reason = args["reason"] as? String ?: "No reason provided"
        
        planService.rejectPlan(planId, reason)
        return ToolResult.success("❌ Plan $planId rejected. Reason: $reason", mapOf("plan_id" to planId))
    }
    
    private suspend fun enterPlanMode(args: Map<String, Any?>): ToolResult {
        val reason = args["reason"] as? String ?: ""
        val planId = planService.enterPlanMode(reason)
        return ToolResult.success("📝 Entered plan mode. Plan ID: $planId", mapOf("plan_id" to planId))
    }
    
    private suspend fun exitPlanMode(): ToolResult {
        planService.exitPlanMode()
        return ToolResult.success("Exited plan mode.")
    }
}
