package com.example.tools.builtin

import android.os.Build
import com.example.service.Parameters
import com.example.service.PropertySchema
import com.example.tools.BaseTool
import com.example.tools.RiskLevel
import com.example.tools.ToolResult
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class ShellExecTool : BaseTool {
    override val name = "shell_exec"
    override val description = "Execute a shell command and return output (Android/Termux compatible)"
    override val riskLevel = RiskLevel.DANGEROUS
    
    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "command" to PropertySchema(
                type = "STRING",
                description = "Shell command to execute"
            ),
            "timeout_seconds" to PropertySchema(
                type = "INTEGER",
                description = "Timeout in seconds (default 30)"
            ),
            "working_dir" to PropertySchema(
                type = "STRING",
                description = "Working directory for command execution"
            )
        ),
        required = listOf("command")
    )
    
    companion object {
        // Android shell detection - find the best available shell
        private fun findShell(): String {
            val shellPaths = listOf(
                "/data/data/com.termux/files/usr/bin/sh",  // Termux shell (preferred)
                "/data/data/com.termux/files/usr/bin/bash", // Termux bash
                "/system/bin/sh",                          // Android system shell
                "/system/bin/bash",                        // Some ROMs have bash
                "/bin/sh"                                  // Fallback (Linux/desktop)
            )
            
            for (path in shellPaths) {
                if (File(path).exists()) {
                    return path
                }
            }
            
            // Final fallback - use /system/bin/sh which should exist on all Android
            return "/system/bin/sh"
        }
        
        // Safe commands that don't need approval (can be auto-executed)
        val SAFE_COMMANDS = setOf(
            "pwd", "whoami", "date", "echo", "cat", "head", "tail",
            "ls", "dir", "find", "grep", "wc", "sort", "uniq",
            "uname", "hostname", "id", "env", "printenv"
        )
        
        // Dangerous command patterns that should be blocked
        val BLOCKED_PATTERNS = listOf(
            Regex("rm\\s+-rf\\s+/"),           // rm -rf /
            Regex("rm\\s+-rf\\s+\\*"),         // rm -rf *
            Regex("dd\\s+.*of=/dev/"),         // dd to devices
            Regex("mkfs\\."),                   // Format commands
            Regex(":.*>\\s*/"),                // Redirect to root
            Regex("chmod\\s+777\\s+/"),        // chmod 777 /
            Regex("su\\s+-c"),                 // su -c
            Regex("mount\\s+.*-o.*remount")   // Remount commands
        )
        
        fun isCommandSafe(command: String): Boolean {
            val firstWord = command.trim().split("\\s+".toRegex()).firstOrNull() ?: return false
            return firstWord in SAFE_COMMANDS
        }
        
        fun isCommandBlocked(command: String): Boolean {
            return BLOCKED_PATTERNS.any { it.containsMatchIn(command) }
        }
    }
    
    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val command = args["command"] as? String
            ?: return ToolResult.error("Missing 'command' argument")
        
        val timeoutSeconds = (args["timeout_seconds"] as? Number)?.toLong() ?: 30L
        val workingDir = args["working_dir"] as? String
        
        // Security check - block dangerous commands
        if (isCommandBlocked(command)) {
            return ToolResult.error("Command is blocked for safety reasons.")
        }
        // Block compound commands that chain dangerous operations
        val lowerCommand = command.lowercase()
        if (lowerCommand.contains("&&") || lowerCommand.contains("||") || 
            lowerCommand.contains(";") || lowerCommand.contains("|")) {
            // Allow safe pipes like grep, but block chaining
            if (lowerCommand.contains("rm ") || lowerCommand.contains("dd ") || 
                lowerCommand.contains("mkfs") || lowerCommand.contains("> /dev")) {
                return ToolResult.error("Compound command with dangerous operation is blocked.")
            }
        }
        
        return try {
            val shell = findShell()
            
            val processBuilder = ProcessBuilder(shell, "-c", command)
                .redirectErrorStream(true)
            
            // Set working directory if provided
            if (workingDir != null) {
                val dir = File(workingDir)
                if (dir.exists() && dir.isDirectory) {
                    processBuilder.directory(dir)
                }
            }
            
            // Set up environment for Termux compatibility
            val env = processBuilder.environment()
            
            // Check if running in Termux
            val termuxPrefix = "/data/data/com.termux/files/usr"
            if (File(termuxPrefix).exists()) {
                env["PREFIX"] = termuxPrefix
                env["HOME"] = "/data/data/com.termux/files/home"
                env["TMPDIR"] = "/data/data/com.termux/files/usr/tmp"
                env["PATH"] = "$termuxPrefix/bin:$termuxPrefix/bin/applets:${env["PATH"] ?: ""}"
                env["LD_LIBRARY_PATH"] = "$termuxPrefix/lib"
            }
            
            val process = processBuilder.start()
            
            // Add this after the process is started
            try {
                val job = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
                job?.invokeOnCompletion { cause ->
                    if (cause is kotlinx.coroutines.CancellationException) {
                        process.destroyForcibly()
                    }
                }
            } catch (e: Exception) {
                // invokeOnCancellation/invokeOnCompletion not available outside specific contexts, ignore
            }
            
            // Drain streams concurrently to prevent deadlock
            var stdoutResult = ""
            var stderrResult = ""
            val stdoutThread = Thread {
                try {
                    stdoutResult = process.inputStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            val stderrThread = Thread {
                try {
                    stderrResult = process.errorStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    // Ignore
                }
            }
            stdoutThread.start()
            stderrThread.start()
            
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return ToolResult.error("Command timed out after ${timeoutSeconds}s")
            }
            
            stdoutThread.join(1000)
            stderrThread.join(1000)
            
            // Re-read after threads complete
            val output = stdoutResult.ifEmpty { stderrResult }.ifEmpty {
                process.inputStream.bufferedReader().use { it.readText() }.ifEmpty {
                    process.errorStream.bufferedReader().use { it.readText() }
                }
            }
            
            val exitCode = process.exitValue()
            
            if (exitCode == 0) {
                ToolResult.success(
                    output.ifEmpty { "(command completed with no output)" }, 
                    mapOf(
                        "exitCode" to exitCode,
                        "shell" to shell,
                        "command" to command
                    )
                )
            } else {
                ToolResult.error("Command failed with exit code $exitCode:\n$output")
            }
        } catch (e: SecurityException) {
            ToolResult.error("Permission denied: ${e.message}")
        } catch (e: Exception) {
            ToolResult.error("Execution failed: ${e.message}")
        }
    }
}
