package com.example.planner

import com.example.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

enum class PlanStatus(val value: String) {
    DRAFT("draft"),
    PENDING("pending"),
    APPROVED("approved"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    REJECTED("rejected")
}

enum class StepStatus(val value: String) {
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    SKIPPED("skipped"),
    FAILED("failed")
}

class PlanService(private val planDao: PlanDao) {
    
    @Volatile
    private var planModeActive = false
    @Volatile
    private var currentPlanId: String? = null
    
    val isInPlanMode: Boolean
        get() = planModeActive
    
    fun getAllPlans(): Flow<List<PlanEntity>> = planDao.getAllPlans()
    
    fun getStepsForPlan(planId: String): Flow<List<PlanStepEntity>> = 
        planDao.getStepsForPlan(planId)
    
    suspend fun enterPlanMode(reason: String = ""): String {
        planModeActive = true
        val planId = UUID.randomUUID().toString()
        currentPlanId = planId
        return planId
    }
    
    suspend fun exitPlanMode() {
        planModeActive = false
        currentPlanId = null
    }
    
    suspend fun createPlan(
        title: String,
        description: String = "",
        steps: List<Map<String, Any>>,
        reason: String = ""
    ): PlanEntity {
        val planId = currentPlanId ?: UUID.randomUUID().toString()
        
        val plan = PlanEntity(
            id = planId,
            title = title,
            description = description,
            status = PlanStatus.DRAFT.value,
            reason = reason
        )
        planDao.insertPlan(plan)
        
        // Create steps
        steps.forEachIndexed { index, stepData ->
            val step = PlanStepEntity(
                id = UUID.randomUUID().toString(),
                planId = planId,
                description = stepData["description"] as? String ?: "",
                stepOrder = index + 1,
                estimatedTurns = stepData["estimated_turns"] as? Int ?: 1
            )
            planDao.insertStep(step)
        }
        
        return plan
    }
    
    suspend fun submitPlan(planId: String) {
        val plan = planDao.getPlanById(planId) ?: return
        planDao.updatePlan(plan.copy(status = PlanStatus.PENDING.value))
    }
    
    suspend fun approvePlan(planId: String) {
        val plan = planDao.getPlanById(planId) ?: return
        planDao.updatePlan(plan.copy(
            status = PlanStatus.APPROVED.value,
            approvedAt = System.currentTimeMillis()
        ))
    }
    
    suspend fun rejectPlan(planId: String, reason: String) {
        val plan = planDao.getPlanById(planId) ?: return
        planDao.updatePlan(plan.copy(
            status = PlanStatus.REJECTED.value,
            rejectionReason = reason
        ))
    }
    
    suspend fun startStep(planId: String, stepId: String) {
        val plan = planDao.getPlanById(planId) ?: return
        val steps = planDao.getStepsForPlan(planId)
        val stepsList = steps.firstOrNull() ?: emptyList()
        val targetStep = stepsList.find { it.id == stepId }
        if (targetStep != null) {
            val updatedStep = targetStep.copy(
                status = StepStatus.IN_PROGRESS.value,
                startedAt = System.currentTimeMillis()
            )
            planDao.updateStep(updatedStep)
        }
        val updatedPlan = if (plan.status == PlanStatus.APPROVED.value) {
            plan.copy(status = PlanStatus.IN_PROGRESS.value)
        } else plan
        planDao.updatePlan(updatedPlan)
    }
    
    suspend fun completeStep(planId: String, stepId: String) {
        val plan = planDao.getPlanById(planId) ?: return
        val steps = planDao.getStepsForPlan(planId)
        val stepsList = steps.firstOrNull() ?: emptyList()
        val targetStep = stepsList.find { it.id == stepId }
        if (targetStep != null) {
            val updatedStep = targetStep.copy(
                status = StepStatus.COMPLETED.value,
                completedAt = System.currentTimeMillis()
            )
            planDao.updateStep(updatedStep)
        }
        planDao.updatePlan(plan.copy(status = PlanStatus.COMPLETED.value))
    }
}
