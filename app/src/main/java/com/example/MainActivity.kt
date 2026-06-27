package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.example.ui.MainHub
import com.example.ui.components.SplashScreen

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialize Memory System Background Consolidation Task
    com.example.memory.MemoryAppStartup.init(this)
    
    // Register the current activity and initialize the Android path resolver
    com.example.tools.AndroidPathResolver.setCurrentActivity(this)
    com.example.tools.AndroidPathResolver.initWithContext(applicationContext)
    
    // Set a global uncaught exception handler to prevent any silent crashes or ANRs
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      Log.e("CRITICAL_CRASH", "Uncaught exception on thread: ${thread.name}", throwable)
      if (defaultHandler != null) {
        defaultHandler.uncaughtException(thread, throwable)
      } else {
        System.exit(1)
      }
    }

    val initialOpenTab = intent?.getStringExtra("OPEN_TAB")
    enableEdgeToEdge()
    setContent {
      var isSplashFinished by remember { mutableStateOf(false) }

      if (!isSplashFinished) {
        SplashScreen(onFinished = { isSplashFinished = true })
      } else {
        MainHub(initialOpenTab = initialOpenTab)
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    com.example.tools.AndroidPathResolver.setCurrentActivity(null)
  }
}


