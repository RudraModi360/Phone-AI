package com.example.context

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import com.example.data.MemoryEntry
import com.example.memory.PreferenceProcessor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DeviceInfo(
    val currentTime: String? = null,
    val timezone: String? = null,
    val deviceModel: String? = null,
    val androidVersion: String? = null,
    val batteryLevel: Int? = null
)

class UserContextBuilder(
    private val profileName: String,
    private val profileRole: String,
    private val preferences: List<MemoryEntry>,
    private val deviceInfo: DeviceInfo?
) {
    fun build(): String {
        val hasName = profileName.isNotBlank() && profileName != "User"
        val hasRole = profileRole.isNotBlank() && profileRole != "General User"
        val hasPrefs = preferences.isNotEmpty()
        val hasDevice = deviceInfo != null

        if (!hasName && !hasRole && !hasPrefs && !hasDevice) return ""

        return buildString {
            appendLine("## USER CONTEXT")
            appendLine("You are helping the following user. Use this context naturally — address them by name when appropriate, tailor responses to their role, and respect their preferences.")
            appendLine()

            if (hasName || hasRole) {
                appendLine("### About the User")
                if (hasName) appendLine("- **Name:** $profileName")
                if (hasRole) appendLine("- **Role:** $profileRole")
                appendLine()
            }

            if (hasPrefs) {
                val processed = PreferenceProcessor.process(preferences)
                val formatted = PreferenceProcessor.formatForPrompt(processed)
                if (formatted.isNotBlank()) {
                    append(formatted)
                    appendLine()
                }
            }

            if (hasDevice) {
                appendLine("### Current Context")
                deviceInfo?.let { device ->
                    device.currentTime?.let { appendLine("- **Time:** $it") }
                    device.timezone?.let { appendLine("- **Timezone:** $it") }
                    device.deviceModel?.let { appendLine("- **Device:** $it") }
                    device.androidVersion?.let { appendLine("- **OS:** Android $it") }
                    device.batteryLevel?.let { appendLine("- **Battery:** $it%") }
                }
                appendLine()
            }
        }
    }

    companion object {
        fun fromViewModel(
            profileName: String,
            profileRole: String,
            preferences: List<MemoryEntry>,
            context: Context?
        ): UserContextBuilder {
            val deviceInfo = context?.let { ctx ->
                val batteryManager = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val currentTime = sdf.format(Date())
                val timezone = TimeZone.getDefault().id
                val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                val androidVersion = Build.VERSION.RELEASE
                
                DeviceInfo(
                    currentTime = currentTime,
                    timezone = timezone,
                    deviceModel = deviceModel,
                    androidVersion = androidVersion,
                    batteryLevel = batteryLevel
                )
            }
            return UserContextBuilder(profileName, profileRole, preferences, deviceInfo)
        }
    }
}
