package com.example.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.data.AppDatabase
import com.example.data.AppSetting

/**
 * Permission types for different tool operations.
 */
enum class PermissionType {
    CONTACTS_READ,
    CONTACTS_WRITE,
    CALENDAR_READ,
    CALENDAR_WRITE,
    CALL_LOG_READ,
    SMS_READ,
    LOCATION,
    MEDIA_READ,
    MEDIA_WRITE,
    STORAGE_READ,
    STORAGE_WRITE,
    STORAGE_FULL,  // MANAGE_EXTERNAL_STORAGE for Android 11+
    RECORD_AUDIO,
    CAMERA,
    NOTIFICATIONS
}

/**
 * Result of a permission check with details.
 */
data class PermissionCheckResult(
    val granted: Boolean,
    val permissions: List<String>,
    val needsSettingsIntent: Boolean = false,
    val settingsIntentAction: String? = null,
    val errorMessage: String? = null
)

object PermissionManager {
    data class Request(
        val permissions: List<String>,
        val rationale: String,
        val deferred: CompletableDeferred<Boolean>,
        val needsSettingsIntent: Boolean = false,
        val settingsIntentAction: String? = null
    )

    private val _currentRequest = MutableStateFlow<Request?>(null)
    val currentRequest: StateFlow<Request?> = _currentRequest

    private suspend fun getCachedPermission(permissionName: String): Boolean? {
        val context = AndroidPathResolver.getContext() ?: return null
        return try {
            val db = AppDatabase.getDatabase(context.applicationContext)
            db.settingDao().getSettingValue("perm_cache_$permissionName")?.toBoolean()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun cachePermission(permissionName: String, granted: Boolean) {
        val context = AndroidPathResolver.getContext() ?: return
        try {
            val db = AppDatabase.getDatabase(context.applicationContext)
            db.settingDao().insertSetting(AppSetting("perm_cache_$permissionName", granted.toString()))
        } catch (e: Exception) {}
    }

    /**
     * Check if a single permission is granted.
     */
    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if a list of permissions are all granted.
     */
    fun arePermissionsGranted(context: Context, permissions: List<String>): Boolean {
        return permissions.all { isPermissionGranted(context, it) }
    }

    /**
     * Get required permissions for a specific permission type based on API level.
     */
    fun getRequiredPermissions(permissionType: PermissionType): List<String> {
        val apiLevel = Build.VERSION.SDK_INT
        
        return when (permissionType) {
            PermissionType.CONTACTS_READ -> listOf(Manifest.permission.READ_CONTACTS)
            PermissionType.CONTACTS_WRITE -> listOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS
            )
            
            PermissionType.CALENDAR_READ -> listOf(Manifest.permission.READ_CALENDAR)
            PermissionType.CALENDAR_WRITE -> listOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
            
            PermissionType.CALL_LOG_READ -> listOf(Manifest.permission.READ_CALL_LOG)
            PermissionType.SMS_READ -> listOf(Manifest.permission.READ_SMS)
            
            PermissionType.LOCATION -> listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            
            PermissionType.MEDIA_READ -> when {
                apiLevel >= 33 -> listOf(  // Android 13+ (Tiramisu)
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
                else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            
            PermissionType.MEDIA_WRITE -> when {
                apiLevel >= 33 -> listOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
                apiLevel >= 30 -> listOf()  // Android 11+ uses MediaStore.createWriteRequest()
                else -> listOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
            
            PermissionType.STORAGE_READ -> when {
                apiLevel >= 33 -> listOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
                else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            
            PermissionType.STORAGE_WRITE -> when {
                apiLevel >= 30 -> listOf()  // Requires MANAGE_EXTERNAL_STORAGE via Settings
                else -> listOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
            
            PermissionType.STORAGE_FULL -> listOf()  // Always requires Settings intent for MANAGE_EXTERNAL_STORAGE
            
            PermissionType.RECORD_AUDIO -> listOf(Manifest.permission.RECORD_AUDIO)
            PermissionType.CAMERA -> listOf(Manifest.permission.CAMERA)
            
            PermissionType.NOTIFICATIONS -> when {
                apiLevel >= 33 -> listOf(Manifest.permission.POST_NOTIFICATIONS)
                else -> listOf()  // No permission needed below Android 13
            }
        }
    }

    /**
     * Check if MANAGE_EXTERNAL_STORAGE permission is granted (Android 11+).
     */
    fun hasManageExternalStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true  // Not needed on older versions
        }
    }

    /**
     * Check if a permission type requires Settings intent instead of runtime permission.
     */
    fun requiresSettingsIntent(permissionType: PermissionType): Boolean {
        return when (permissionType) {
            PermissionType.STORAGE_FULL -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            PermissionType.STORAGE_WRITE -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            else -> false
        }
    }

    /**
     * Get the Settings intent for special permissions.
     */
    fun getSettingsIntent(context: Context, permissionType: PermissionType): Intent? {
        return when {
            permissionType == PermissionType.STORAGE_FULL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
            permissionType == PermissionType.STORAGE_WRITE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
            else -> null
        }
    }

    /**
     * Check permissions for a type and return detailed result.
     */
    /**
     * Check permissions for a type and return detailed result.
     * Integrates persistent caching and OS permission revalidation.
     */
    suspend fun checkPermissions(context: Context, permissionType: PermissionType): PermissionCheckResult {
        // Special handling for MANAGE_EXTERNAL_STORAGE
        if (permissionType == PermissionType.STORAGE_FULL || 
            (permissionType == PermissionType.STORAGE_WRITE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)) {
            val granted = hasManageExternalStoragePermission()
            val prefKey = "manage_external_storage"
            val cached = getCachedPermission(prefKey)
            if (granted != cached) {
                cachePermission(prefKey, granted)
            }
            return PermissionCheckResult(
                granted = granted,
                permissions = emptyList(),
                needsSettingsIntent = !granted,
                settingsIntentAction = if (!granted) Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION else null,
                errorMessage = if (!granted) "Full storage access required. Grant 'All files access' in Settings > Apps > ${getAppName(context)} > Permissions > Files and media > Allow management of all files" else null
            )
        }

        val permissions = getRequiredPermissions(permissionType)
        if (permissions.isEmpty()) {
            return PermissionCheckResult(granted = true, permissions = emptyList())
        }

        // Perform live OS check (ground-truth)
        val osGranted = arePermissionsGranted(context, permissions)

        // Read and update cache as security revalidation
        for (perm in permissions) {
            val isGranted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            cachePermission(perm, isGranted)
        }

        return PermissionCheckResult(
            granted = osGranted,
            permissions = permissions,
            errorMessage = if (!osGranted) getPermissionDeniedMessage(context, permissionType) else null
        )
    }

    /**
     * Get human-readable error message for permission denial.
     */
    fun getPermissionDeniedMessage(context: Context, permissionType: PermissionType): String {
        val appName = getAppName(context)
        val settingsPath = "Settings > Apps > $appName > Permissions"
        
        return when (permissionType) {
            PermissionType.CONTACTS_READ -> "Contacts permission denied. Grant access in $settingsPath > Contacts"
            PermissionType.CONTACTS_WRITE -> "Contacts write permission denied. Grant access in $settingsPath > Contacts"
            PermissionType.CALENDAR_READ -> "Calendar permission denied. Grant access in $settingsPath > Calendar"
            PermissionType.CALENDAR_WRITE -> "Calendar write permission denied. Grant access in $settingsPath > Calendar"
            PermissionType.CALL_LOG_READ -> "Call log permission denied. Grant access in $settingsPath > Call logs"
            PermissionType.SMS_READ -> "SMS permission denied. Grant access in $settingsPath > SMS"
            PermissionType.LOCATION -> "Location permission denied. Grant access in $settingsPath > Location"
            PermissionType.MEDIA_READ -> "Media access denied. Grant access in $settingsPath > Photos and videos"
            PermissionType.MEDIA_WRITE -> "Media write access denied. Grant access in $settingsPath > Photos and videos"
            PermissionType.STORAGE_READ -> "Storage read permission denied. Grant access in $settingsPath > Files and media"
            PermissionType.STORAGE_WRITE -> "Storage write permission denied. Grant 'All files access' in $settingsPath > Files and media"
            PermissionType.STORAGE_FULL -> "Full storage access denied. Grant 'All files access' in $settingsPath > Files and media > Allow management of all files"
            PermissionType.RECORD_AUDIO -> "Microphone permission denied. Grant access in $settingsPath > Microphone"
            PermissionType.CAMERA -> "Camera permission denied. Grant access in $settingsPath > Camera"
            PermissionType.NOTIFICATIONS -> "Notification permission denied. Grant access in $settingsPath > Notifications"
        }
    }

    /**
     * Get the app name for error messages.
     */
    private fun getAppName(context: Context): String {
        return try {
            val appInfo = context.applicationInfo
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            context.packageName
        }
    }

    /**
     * Get rationale message for permission request dialog.
     */
    fun getRationale(permissionType: PermissionType): String {
        return when (permissionType) {
            PermissionType.CONTACTS_READ -> "Access to your contacts is needed to search and display contact information."
            PermissionType.CONTACTS_WRITE -> "Access to modify contacts is needed to create, update, or delete contact information."
            PermissionType.CALENDAR_READ -> "Access to your calendar is needed to view events and schedules."
            PermissionType.CALENDAR_WRITE -> "Access to modify calendar is needed to create, update, or delete events."
            PermissionType.CALL_LOG_READ -> "Access to call history is needed to view recent calls and call details."
            PermissionType.SMS_READ -> "Access to messages is needed to read SMS conversations."
            PermissionType.LOCATION -> "Location access is needed to determine your current position."
            PermissionType.MEDIA_READ -> "Access to photos and videos is needed to browse your media library."
            PermissionType.MEDIA_WRITE -> "Access to modify media is needed to manage photos and videos."
            PermissionType.STORAGE_READ -> "Storage access is needed to read files and documents."
            PermissionType.STORAGE_WRITE -> "Storage write access is needed to create and modify files."
            PermissionType.STORAGE_FULL -> "Full file access is needed to read and write files anywhere on storage."
            PermissionType.RECORD_AUDIO -> "Microphone access is needed for voice input."
            PermissionType.CAMERA -> "Camera access is needed to take photos or videos."
            PermissionType.NOTIFICATIONS -> "Notification permission is needed to show alerts and updates."
        }
    }

    /**
     * Request permissions suspending until the user responds in the UI.
     */
    suspend fun requestPermissions(permissions: List<String>, rationale: String): Boolean {
        val context = AndroidPathResolver.getContext() ?: return false
        
        // If already granted in OS, update cache & return true immediately (no dialog)
        if (arePermissionsGranted(context, permissions)) {
            permissions.forEach { cachePermission(it, true) }
            return true
        }

        val deferred = CompletableDeferred<Boolean>()
        _currentRequest.value = Request(permissions, rationale, deferred)
        
        val result = try {
            deferred.await()
        } finally {
            _currentRequest.value = null
        }

        // Cache persistent status of outcome
        permissions.forEach { cachePermission(it, result) }
        return result
    }

    /**
     * Request permissions for a specific permission type.
     */
    suspend fun requestPermissionType(permissionType: PermissionType): Boolean {
        val context = AndroidPathResolver.getContext() ?: return false
        
        // Handle special permissions that need Settings intent
        if (requiresSettingsIntent(permissionType)) {
            if (permissionType == PermissionType.STORAGE_FULL || permissionType == PermissionType.STORAGE_WRITE) {
                val hasPerm = hasManageExternalStoragePermission()
                if (hasPerm) {
                    cachePermission("manage_external_storage", true)
                    return true
                }
                // Request via Settings intent
                val deferred = CompletableDeferred<Boolean>()
                _currentRequest.value = Request(
                    permissions = emptyList(),
                    rationale = getRationale(permissionType),
                    deferred = deferred,
                    needsSettingsIntent = true,
                    settingsIntentAction = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                )
                val result = try {
                    deferred.await()
                } finally {
                    _currentRequest.value = null
                }
                cachePermission("manage_external_storage", result)
                return result
            }
        }

        val permissions = getRequiredPermissions(permissionType)
        if (permissions.isEmpty()) {
            return true
        }

        return requestPermissions(permissions, getRationale(permissionType))
    }

    /**
     * Ensure permissions are granted for a permission type, returning error message if denied.
     * Returns null if permissions are granted, or error message string if denied.
     */
    suspend fun ensurePermissions(permissionType: PermissionType): String? {
        val context = AndroidPathResolver.getContext() 
            ?: return "Application context not available"
        
        val checkResult = checkPermissions(context, permissionType)
        if (checkResult.granted) {
            return null
        }

        // Try to request permissions
        val granted = requestPermissionType(permissionType)
        if (granted) {
            return null
        }

        return checkResult.errorMessage ?: getPermissionDeniedMessage(context, permissionType)
    }

    fun onPermissionResult(granted: Boolean) {
        _currentRequest.value?.deferred?.complete(granted)
        _currentRequest.value = null
    }
}
