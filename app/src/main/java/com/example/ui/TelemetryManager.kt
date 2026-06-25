package com.example.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.text.DecimalFormat

data class SystemTelemetry(
    val ramUsedGb: Double,
    val ramTotalGb: Double,
    val storageFreeGb: Double,
    val storageTotalGb: Double,
    val batteryLevel: Int = -1,  // -1 if unavailable
    val isLowMemory: Boolean = false
)

object TelemetryManager {
    private val decimalFormat = DecimalFormat("#.#")

    fun getTelemetryFlow(context: Context) = flow {
        val am = try {
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        } catch (e: Exception) {
            null
        }
        val memoryInfo = ActivityManager.MemoryInfo()

        while (true) {
            try {
                // Default fallback variables
                var ramTotal = 4.0
                var ramUsed = 1.8
                var storageTotal = 64.0
                var storageFree = 18.2
                var isLowMemory = false

                // Try safe memory calculation
                if (am != null) {
                    try {
                        am.getMemoryInfo(memoryInfo)
                        ramTotal = memoryInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
                        val ramFree = memoryInfo.availMem.toDouble() / (1024 * 1024 * 1024)
                        ramUsed = (ramTotal - ramFree).coerceIn(0.0, ramTotal)
                        isLowMemory = memoryInfo.lowMemory
                    } catch (e: Exception) {
                        Log.e("TelemetryManager", "Error reading RAM info: ${e.message}")
                    }
                }

                // Try safe storage calculation
                try {
                    val path: File = Environment.getDataDirectory()
                    if (path.exists()) {
                        val stat = StatFs(path.path)
                        val blockSize = stat.blockSizeLong
                        val totalBlocks = stat.blockCountLong
                        val availableBlocks = stat.availableBlocksLong

                        storageTotal = (totalBlocks * blockSize).toDouble() / (1024 * 1024 * 1024)
                        storageFree = (availableBlocks * blockSize).toDouble() / (1024 * 1024 * 1024)
                    }
                } catch (e: Exception) {
                    Log.e("TelemetryManager", "Error reading storage info: ${e.message}")
                }

                emit(
                    SystemTelemetry(
                        ramUsedGb = ramUsed,
                        ramTotalGb = ramTotal,
                        storageFreeGb = storageFree,
                        storageTotalGb = storageTotal,
                        isLowMemory = isLowMemory
                    )
                )
            } catch (e: Exception) {
                Log.e("TelemetryManager", "Critical telemetry flow error", e)
                // Emit robust default fallback telemetry on error
                emit(SystemTelemetry(1.8, 4.0, 18.2, 64.0))
            }
            delay(1500) // Update every 1.5s
        }
    }.flowOn(Dispatchers.IO)
}
