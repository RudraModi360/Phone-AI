package com.example.memory

import com.example.data.MemoryEntry

class MemorySurfaceTracker {

    companion object {
        const val MAX_FILES_PER_TURN = 5
        const val MAX_BYTES_PER_FILE = 4096
        const val SESSION_BUDGET_BYTES = 60_000
    }

    private val surfacedIds = mutableSetOf<Long>()
    private var sessionBytesUsed = 0

    fun isSurfaced(id: Long): Boolean = id in surfacedIds

    fun hasBudget(): Boolean = sessionBytesUsed < SESSION_BUDGET_BYTES

    fun surface(memories: List<MemoryEntry>): List<MemoryEntry> {
        val result = mutableListOf<MemoryEntry>()
        var turnBytes = 0

        for (mem in memories) {
            if (surfacedIds.contains(mem.id)) continue
            if (result.size >= MAX_FILES_PER_TURN) break
            if (!hasBudget()) break

            val contentBytes = mem.content.toByteArray().size
            val cappedBytes = minOf(contentBytes, MAX_BYTES_PER_FILE)

            if (turnBytes + cappedBytes > MAX_BYTES_PER_FILE * MAX_FILES_PER_TURN) break

            surfacedIds.add(mem.id)
            sessionBytesUsed += cappedBytes
            turnBytes += cappedBytes
            result.add(mem)
        }

        return result
    }

    fun reset() {
        surfacedIds.clear()
        sessionBytesUsed = 0
    }
}
