package com.example.tools

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Android-native path resolver utility.
 * Resolves paths to appropriate Android storage locations without any Termux dependencies.
 * 
 * Default locations:
 * - ~ or . resolves to /sdcard/ (shared storage, accessible without root)
 * - Relative paths resolve against /sdcard/
 * - Absolute paths are used as-is (with security checks)
 */
object AndroidPathResolver {
    
    // Primary storage paths (lazily evaluated with robust safety to prevent startup NullPointerExceptions)
    private val SDCARD by lazy {
        try {
            Environment.getExternalStorageDirectory()?.absolutePath ?: "/sdcard"
        } catch (e: Exception) {
            "/sdcard"
        }
    }
    private val DOWNLOADS by lazy {
        try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath ?: "$SDCARD/Download"
        } catch (e: Exception) {
            "$SDCARD/Download"
        }
    }
    private val DOCUMENTS by lazy {
        try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.absolutePath ?: "$SDCARD/Documents"
        } catch (e: Exception) {
            "$SDCARD/Documents"
        }
    }
    private val DCIM by lazy {
        try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)?.absolutePath ?: "$SDCARD/DCIM"
        } catch (e: Exception) {
            "$SDCARD/DCIM"
        }
    }
    private val PICTURES by lazy {
        try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)?.absolutePath ?: "$SDCARD/Pictures"
        } catch (e: Exception) {
            "$SDCARD/Pictures"
        }
    }
    private val MUSIC by lazy {
        try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.absolutePath ?: "$SDCARD/Music"
        } catch (e: Exception) {
            "$SDCARD/Music"
        }
    }
    private val MOVIES by lazy {
        try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)?.absolutePath ?: "$SDCARD/Movies"
        } catch (e: Exception) {
            "$SDCARD/Movies"
        }
    }
    
    // Allowed paths for read/write operations (security whitelist)
    private val ALLOWED_PREFIXES by lazy {
        val extraDirs = listOfNotNull(
            appExternalFilesDir?.absolutePath,
            appFilesDir?.absolutePath
        )
        listOf(
            SDCARD,
            "/storage/emulated/0",
            "/storage/emulated/",
            "/sdcard"
        ) + extraDirs
    }
    
    // Blocked system paths (security blacklist)
    private val BLOCKED_PREFIXES = listOf(
        "/data/data/",      // Other app data
        "/data/app/",       // App packages
        "/system/",         // System files
        "/proc/",           // Process info
        "/sys/",            // Kernel interface
        "/dev/",            // Device files
        "/vendor/",         // Vendor partition
        "/oem/"             // OEM partition
    )
    
    /**
     * Get the default home directory (shared storage root).
     */
    fun getDefaultHome(): File {
        return File(SDCARD)
    }
    
    /**
     * Get the Downloads directory.
     */
    fun getDownloadsDir(): File {
        return File(DOWNLOADS)
    }
    
    /**
     * Get the Documents directory.
     */
    fun getDocumentsDir(): File {
        return File(DOCUMENTS)
    }
    
    /**
     * Get the DCIM (camera) directory.
     */
    fun getDCIMDir(): File {
        return File(DCIM)
    }
    
    /**
     * Get the Pictures directory.
     */
    fun getPicturesDir(): File {
        return File(PICTURES)
    }
    
    /**
     * Resolve a path string to an absolute File.
     * 
     * Handles:
     * - ~ → /sdcard/
     * - . → /sdcard/
     * - ~/Downloads → /sdcard/Downloads
     * - /sdcard/file.txt → /sdcard/file.txt (absolute)
     * - relative/path → /sdcard/relative/path
     * 
     * @param path The path to resolve (can be relative, ~, or absolute)
     * @return Resolved File object
     */
    fun resolvePath(path: String?): File {
        if (path.isNullOrBlank() || path == "." || path == "~") {
            return getDefaultHome()
        }
        
        // Expand ~ to sdcard
        val expandedPath = when {
            path == "~" -> SDCARD
            path.startsWith("~/") -> SDCARD + path.substring(1)
            path.startsWith("/") -> path  // Absolute path
            else -> {
                // If the candidate file exists inside our sandbox external files dir, prioritize it!
                val externalDir = appExternalFilesDir
                if (externalDir != null) {
                    val candidate = File(externalDir, path)
                    if (candidate.exists()) {
                        return candidate
                    }
                }
                "$SDCARD/$path"  // Default fallback relative path
            }
        }
        
        return File(expandedPath)
    }
    
    /**
     * Check if a path is allowed for read operations.
     * 
     * @param path The path to check (can be relative or absolute)
     * @return true if the path is in an allowed location
     */
    fun isReadAllowed(path: String): Boolean {
        val normalizedPath = try {
            resolvePath(path).canonicalPath
        } catch (e: Exception) {
            return false
        }
        
        // Check blocked paths first
        for (blocked in BLOCKED_PREFIXES) {
            if (normalizedPath.startsWith(blocked)) {
                return false
            }
        }
        
        // Check if in allowed prefix or is a relative path
        if (!path.startsWith("/")) return true  // Relative paths are resolved to /sdcard
        
        return ALLOWED_PREFIXES.any { normalizedPath.startsWith(it) }
    }
    
    /**
     * Check if a path is allowed for write operations.
     * More restrictive than read - blocks executable extensions.
     * 
     * @param path The path to check
     * @return true if the path is safe to write to
     */
    fun isWriteAllowed(path: String): Boolean {
        // First check read permission (same base rules)
        if (!isReadAllowed(path)) return false
        
        // Additional check: block dangerous file extensions
        val dangerousExtensions = setOf(
            ".apk", ".dex", ".so", ".sh", ".exe", ".bin", ".jar"
        )
        
        val ext = File(path).extension.lowercase()
        return ".$ext" !in dangerousExtensions
    }
    
    /**
     * Get a human-readable description of the resolved path.
     * Useful for showing users where files will be read from/written to.
     */
    fun describeLocation(path: String): String {
        val resolved = resolvePath(path)
        val canonical = resolved.canonicalPath
        
        return when {
            canonical.startsWith(DOWNLOADS) -> "Downloads folder"
            canonical.startsWith(DOCUMENTS) -> "Documents folder"
            canonical.startsWith(DCIM) -> "Camera folder (DCIM)"
            canonical.startsWith(PICTURES) -> "Pictures folder"
            canonical.startsWith(MUSIC) -> "Music folder"
            canonical.startsWith(MOVIES) -> "Movies folder"
            canonical == SDCARD || canonical == "/sdcard" -> "Shared storage root"
            canonical.startsWith(SDCARD) -> "Shared storage"
            else -> "Storage"
        }
    }
    
    /**
     * Get common Android storage locations as a map.
     * Useful for the AI to know available directories.
     */
    fun getCommonLocations(): Map<String, String> {
        return mapOf(
            "home" to SDCARD,
            "downloads" to DOWNLOADS,
            "documents" to DOCUMENTS,
            "dcim" to DCIM,
            "pictures" to PICTURES,
            "music" to MUSIC,
            "movies" to MOVIES
        )
    }
    
    /**
     * Initialize with app context to get app-specific directories.
     * Call this from Application or MainActivity.
     */
    private var appFilesDir: File? = null
    private var appCacheDir: File? = null
    private var appExternalFilesDir: File? = null
    private var appContext: Context? = null
    private var activeActivityRef: java.lang.ref.WeakReference<android.app.Activity>? = null
    
    fun setCurrentActivity(activity: android.app.Activity?) {
        activeActivityRef = activity?.let { java.lang.ref.WeakReference(it) }
    }
    
    fun getActivityContext(): android.app.Activity? {
        return activeActivityRef?.get()
    }
    
    fun initWithContext(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        appFilesDir = applicationContext.filesDir
        appCacheDir = applicationContext.cacheDir
        appExternalFilesDir = applicationContext.getExternalFilesDir(null)
        
        // Seed some sample files in the app's external files directory so files search/read tools are highly interactive
        try {
            val rootDir = appExternalFilesDir ?: appFilesDir
            if (rootDir != null) {
                val downloadDir = File(rootDir, "Download")
                if (!downloadDir.exists()) downloadDir.mkdirs()
                
                val welcomeFile = File(downloadDir, "Welcome_to_Logy.txt")
                if (!welcomeFile.exists()) {
                    welcomeFile.writeText(
                        """
                        ==================================================
                        ❖ Welcome to Logy Companion OS ❖
                        ==================================================
                        Logy is a powerful terminal-style mobile assistant that 
                        interfaces with offline-first memory, task planners,
                        and system operations.
                        
                        Common Commands you can try:
                        - /help: View the main help documentation and commands list.
                        - /telemetry: Open the live telemetry dashboard.
                        - "find my files": search through your local document collection.
                        
                        Created on: 2026-06-13
                        Status: Fully Operational
                        ==================================================
                        """.trimIndent()
                    )
                }
                
                val tasksFile = File(downloadDir, "Daily_Tasks.md")
                if (!tasksFile.exists()) {
                    tasksFile.writeText(
                        """
                        # Logy Daily Tasks - June 13, 2026
                        
                        - [x] Configure system path resolver and fallback engines
                        - [x] Eliminate dual loading indicator glitch
                        - [ ] Test document searches with real localized storage paths
                        - [ ] Verify background daemon logging status
                        """.trimIndent()
                    )
                }
                
                val docDir = File(rootDir, "Documents")
                if (!docDir.exists()) docDir.mkdirs()
                
                val configJson = File(docDir, "Agent_Config.json")
                if (!configJson.exists()) {
                    configJson.writeText(
                        """
                        {
                          "agent_id": "logy-companion-3.5",
                          "version": "1.0.4",
                          "theme": "Cosmic Slate",
                          "local_processing": true,
                          "features": {
                            "document_search": "active",
                            "shell_exec": "restricted",
                            "clipboard": "active"
                          }
                        }
                        """.trimIndent()
                    )
                }
            }
        } catch (_: Exception) {}
    }
    
    fun getContext(): Context? = appContext
    
    /**
     * Get app's private files directory (internal storage).
     */
    fun getAppFilesDir(): File? = appFilesDir
    
    /**
     * Get app's external files directory (app-specific external storage).
     * No permissions needed on Android 10+.
     */
    fun getAppExternalDir(): File? = appExternalFilesDir
}
