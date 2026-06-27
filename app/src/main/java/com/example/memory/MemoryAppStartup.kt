package com.example.memory

import android.content.Context

object MemoryAppStartup {
    fun init(context: Context) {
        ConsolidationWorker.schedule(context)
    }
}
