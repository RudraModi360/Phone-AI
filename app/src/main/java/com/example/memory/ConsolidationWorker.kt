package com.example.memory

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.data.AppDatabase
import java.util.concurrent.TimeUnit

class ConsolidationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "Consolidation"
        const val WORK_NAME = "memory_consolidation"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ConsolidationWorker>(
                24, TimeUnit.HOURS, 1, TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting consolidation")
        val db = AppDatabase.getDatabase(applicationContext)
        val memoryDao = db.memoryDao()
        val memoryService = MemoryService(memoryDao)

        val allMemories = memoryService.getMemoryManifest()
        var pruned = 0
        var merged = 0

        val grouped = allMemories.groupBy { "${it.name.lowercase()}_${it.memoryType}" }
        for ((_, group) in grouped) {
            if (group.size < 2) continue
            val best = group.maxByOrNull { it.usageCount } ?: continue
            val others = group.filter { it.id != best.id }

            val mergedContent = buildString {
                append(best.content)
                for (other in others) {
                    if (!best.content.contains(other.content, ignoreCase = true)) {
                        append("\n\n---\n\n${other.content}")
                    }
                }
            }

            memoryService.updateMemory(best.copy(
                content = mergedContent.take(4000),
                usageCount = best.usageCount + others.sumOf { it.usageCount },
                updatedAt = System.currentTimeMillis()
            ))
            for (other in others) {
                memoryService.deleteMemory(other.id)
            }
            merged++
        }

        val cutoff = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
        for (mem in allMemories) {
            if (mem.updatedAt < cutoff && mem.usageCount < 2) {
                memoryService.deleteMemory(mem.id)
                pruned++
            }
        }

        Log.d(TAG, "Done: merged=$merged, pruned=$pruned")
        return Result.success()
    }
}
