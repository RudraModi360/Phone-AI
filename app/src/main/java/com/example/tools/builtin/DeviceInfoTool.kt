package com.example.tools.builtin

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.service.Parameters
import com.example.service.PropertySchema
import com.example.tools.AndroidPathResolver
import com.example.tools.BaseTool
import com.example.tools.RiskLevel
import com.example.tools.ToolResult
import java.io.File

/**
 * Android Device Info Tool - Get real device information.
 * Uses system services and Build class for actual device data.
 * NO MOCK DATA - All information comes from the actual device.
 * No sensitive permissions required.
 */
class DeviceInfoTool : BaseTool {
    override val name = "device_info"
    override val description = "Get device information: battery status, network state, storage, display, device specs. No permissions required."
    override val riskLevel = RiskLevel.SAFE

    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "operation" to PropertySchema(
                type = "STRING",
                description = "Operation: 'all', 'battery', 'network', 'storage', 'display', 'device'"
            )
        ),
        required = emptyList()
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val operation = args["operation"] as? String ?: "all"

        val context = AndroidPathResolver.getContext()
            ?: return ToolResult.error("Application context not available")

        return try {
            when (operation) {
                "all" -> getAllDeviceInfo(context)
                "battery" -> getBatteryInfo(context)
                "network" -> getNetworkInfo(context)
                "storage" -> getStorageInfo(context)
                "display" -> getDisplayInfo(context)
                "device" -> getDeviceSpecs()
                else -> ToolResult.error("Unknown operation: '$operation'. Use: all, battery, network, storage, display, device")
            }
        } catch (e: Exception) {
            ToolResult.error("Device info operation failed: ${e.message}")
        }
    }

    /**
     * Get all device information.
     */
    private fun getAllDeviceInfo(context: Context): ToolResult {
        val battery = getBatteryData(context)
        val network = getNetworkData(context)
        val storage = getStorageData()
        val display = getDisplayData(context)
        val device = getDeviceData()

        val allData = mapOf(
            "battery" to battery,
            "network" to network,
            "storage" to storage,
            "display" to display,
            "device" to device
        )

        val formatted = buildString {
            appendLine("📱 Device Information")
            appendLine("=" .repeat(25))
            appendLine()
            
            // Device
            appendLine("🔧 Device")
            appendLine("   ${device["manufacturer"]} ${device["model"]}")
            appendLine("   Android ${device["android_version"]} (API ${device["sdk_version"]})")
            appendLine()
            
            // Battery
            appendLine("🔋 Battery")
            val batteryIcon = when {
                battery["is_charging"] == true -> "⚡"
                (battery["level"] as? Int ?: 0) > 50 -> "🔋"
                (battery["level"] as? Int ?: 0) > 20 -> "🪫"
                else -> "⚠️"
            }
            appendLine("   $batteryIcon ${battery["level"]}% (${battery["status"]})")
            if (battery["is_charging"] == true) {
                appendLine("   Charging via ${battery["plugged"]}")
            }
            appendLine()
            
            // Network
            appendLine("🌐 Network")
            appendLine("   ${network["type"]} (${if (network["is_connected"] == true) "Connected" else "Disconnected"})")
            if (network["is_connected"] == true) {
                appendLine("   ${network["capabilities"]}")
            }
            appendLine()
            
            // Storage
            appendLine("💾 Storage")
            val usedPercent = ((storage["internal_used_gb"] as Double) / (storage["internal_total_gb"] as Double) * 100).toInt()
            appendLine("   Internal: ${String.format("%.1f", storage["internal_used_gb"])} / ${String.format("%.1f", storage["internal_total_gb"])} GB ($usedPercent% used)")
            appendLine("   Available: ${String.format("%.1f", storage["internal_available_gb"])} GB")
            appendLine()
            
            // Display
            appendLine("📺 Display")
            appendLine("   ${display["resolution"]} @ ${display["density_dpi"]} dpi")
            appendLine("   ${String.format("%.1f", display["screen_inches"])}\" (${display["density_bucket"]})")
        }

        return ToolResult.success(formatted, allData)
    }

    /**
     * Get battery information.
     */
    private fun getBatteryInfo(context: Context): ToolResult {
        val data = getBatteryData(context)
        
        val formatted = buildString {
            appendLine("🔋 Battery Status")
            appendLine("=" .repeat(20))
            appendLine()
            appendLine("Level: ${data["level"]}%")
            appendLine("Status: ${data["status"]}")
            appendLine("Health: ${data["health"]}")
            appendLine("Plugged: ${data["plugged"]}")
            appendLine("Charging: ${if (data["is_charging"] == true) "Yes" else "No"}")
            appendLine("Temperature: ${data["temperature"]}°C")
            appendLine("Voltage: ${data["voltage"]}mV")
            appendLine("Technology: ${data["technology"]}")
        }

        return ToolResult.success(formatted, data)
    }

    /**
     * Get network information.
     */
    private fun getNetworkInfo(context: Context): ToolResult {
        val data = getNetworkData(context)
        
        val formatted = buildString {
            appendLine("🌐 Network Status")
            appendLine("=" .repeat(20))
            appendLine()
            appendLine("Connected: ${if (data["is_connected"] == true) "Yes" else "No"}")
            appendLine("Type: ${data["type"]}")
            if (data["is_connected"] == true) {
                appendLine("Capabilities: ${data["capabilities"]}")
                appendLine("Metered: ${if (data["is_metered"] == true) "Yes" else "No"}")
                appendLine("VPN: ${if (data["is_vpn"] == true) "Yes" else "No"}")
            }
        }

        return ToolResult.success(formatted, data)
    }

    /**
     * Get storage information.
     */
    private fun getStorageInfo(context: Context): ToolResult {
        val data = getStorageData()
        
        val internalUsedPercent = ((data["internal_used_gb"] as Double) / (data["internal_total_gb"] as Double) * 100).toInt()
        
        val formatted = buildString {
            appendLine("💾 Storage Status")
            appendLine("=" .repeat(20))
            appendLine()
            appendLine("Internal Storage:")
            appendLine("   Total: ${String.format("%.2f", data["internal_total_gb"])} GB")
            appendLine("   Used: ${String.format("%.2f", data["internal_used_gb"])} GB ($internalUsedPercent%)")
            appendLine("   Available: ${String.format("%.2f", data["internal_available_gb"])} GB")
            
            if (data["external_total_gb"] != null && (data["external_total_gb"] as Double) > 0) {
                val externalUsedPercent = ((data["external_used_gb"] as Double) / (data["external_total_gb"] as Double) * 100).toInt()
                appendLine()
                appendLine("External Storage:")
                appendLine("   Total: ${String.format("%.2f", data["external_total_gb"])} GB")
                appendLine("   Used: ${String.format("%.2f", data["external_used_gb"])} GB ($externalUsedPercent%)")
                appendLine("   Available: ${String.format("%.2f", data["external_available_gb"])} GB")
            }
        }

        return ToolResult.success(formatted, data)
    }

    /**
     * Get display information.
     */
    private fun getDisplayInfo(context: Context): ToolResult {
        val data = getDisplayData(context)
        
        val formatted = buildString {
            appendLine("📺 Display Info")
            appendLine("=" .repeat(18))
            appendLine()
            appendLine("Resolution: ${data["resolution"]}")
            appendLine("Width: ${data["width_px"]} px")
            appendLine("Height: ${data["height_px"]} px")
            appendLine("Density: ${data["density_dpi"]} dpi")
            appendLine("Density Bucket: ${data["density_bucket"]}")
            appendLine("Screen Size: ${String.format("%.1f", data["screen_inches"])}\"")
            appendLine("Scale Factor: ${data["density"]}")
        }

        return ToolResult.success(formatted, data)
    }

    /**
     * Get device specifications.
     */
    private fun getDeviceSpecs(): ToolResult {
        val data = getDeviceData()
        
        val formatted = buildString {
            appendLine("🔧 Device Specifications")
            appendLine("=" .repeat(25))
            appendLine()
            appendLine("Manufacturer: ${data["manufacturer"]}")
            appendLine("Model: ${data["model"]}")
            appendLine("Device: ${data["device"]}")
            appendLine("Brand: ${data["brand"]}")
            appendLine("Product: ${data["product"]}")
            appendLine()
            appendLine("Android Version: ${data["android_version"]}")
            appendLine("SDK/API Level: ${data["sdk_version"]}")
            appendLine("Security Patch: ${data["security_patch"]}")
            appendLine("Build ID: ${data["build_id"]}")
            appendLine()
            appendLine("Hardware: ${data["hardware"]}")
            appendLine("Board: ${data["board"]}")
            appendLine("Supported ABIs: ${data["supported_abis"]}")
        }

        return ToolResult.success(formatted, data)
    }

    // Data collection helpers

    private fun getBatteryData(context: Context): Map<String, Any> {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percentage = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val statusStr = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }
        
        val health = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val healthStr = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
        
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val pluggedStr = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Unplugged"
        }
        
        val temperature = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val technology = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"

        return mapOf(
            "level" to percentage,
            "status" to statusStr,
            "health" to healthStr,
            "plugged" to pluggedStr,
            "is_charging" to (status == BatteryManager.BATTERY_STATUS_CHARGING),
            "temperature" to temperature,
            "voltage" to voltage,
            "technology" to technology
        )
    }

    private fun getNetworkData(context: Context): Map<String, Any> {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

        val isConnected = capabilities != null
        
        val type = when {
            capabilities == null -> "None"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Other"
        }

        val capList = mutableListOf<String>()
        capabilities?.let {
            if (it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) capList.add("Internet")
            if (it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) capList.add("Validated")
            if (it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) capList.add("Unlimited")
        }

        return mapOf(
            "is_connected" to isConnected,
            "type" to type,
            "capabilities" to capList.joinToString(", "),
            "is_metered" to (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false),
            "is_vpn" to (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true)
        )
    }

    private fun getStorageData(): Map<String, Any> {
        val internalPath = Environment.getDataDirectory()
        val internalStat = StatFs(internalPath.path)
        
        val internalTotal = internalStat.blockCountLong * internalStat.blockSizeLong
        val internalAvailable = internalStat.availableBlocksLong * internalStat.blockSizeLong
        val internalUsed = internalTotal - internalAvailable

        val result = mutableMapOf<String, Any>(
            "internal_total_bytes" to internalTotal,
            "internal_available_bytes" to internalAvailable,
            "internal_used_bytes" to internalUsed,
            "internal_total_gb" to (internalTotal / (1024.0 * 1024.0 * 1024.0)),
            "internal_available_gb" to (internalAvailable / (1024.0 * 1024.0 * 1024.0)),
            "internal_used_gb" to (internalUsed / (1024.0 * 1024.0 * 1024.0))
        )

        // Check external storage
        val externalPath = Environment.getExternalStorageDirectory()
        if (externalPath.exists() && externalPath.path != internalPath.path) {
            try {
                val externalStat = StatFs(externalPath.path)
                val externalTotal = externalStat.blockCountLong * externalStat.blockSizeLong
                val externalAvailable = externalStat.availableBlocksLong * externalStat.blockSizeLong
                val externalUsed = externalTotal - externalAvailable

                result["external_total_bytes"] = externalTotal
                result["external_available_bytes"] = externalAvailable
                result["external_used_bytes"] = externalUsed
                result["external_total_gb"] = externalTotal / (1024.0 * 1024.0 * 1024.0)
                result["external_available_gb"] = externalAvailable / (1024.0 * 1024.0 * 1024.0)
                result["external_used_gb"] = externalUsed / (1024.0 * 1024.0 * 1024.0)
            } catch (e: Exception) {
                // External storage not accessible
            }
        }

        return result
    }

    @Suppress("DEPRECATION")
    private fun getDisplayData(context: Context): Map<String, Any> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = context.display ?: windowManager.defaultDisplay
            display.getRealMetrics(metrics)
        } else {
            windowManager.defaultDisplay.getRealMetrics(metrics)
        }

        val widthPx = metrics.widthPixels
        val heightPx = metrics.heightPixels
        val densityDpi = metrics.densityDpi
        val density = metrics.density
        
        val widthInches = widthPx / densityDpi.toDouble()
        val heightInches = heightPx / densityDpi.toDouble()
        val screenInches = kotlin.math.sqrt(widthInches * widthInches + heightInches * heightInches)

        val densityBucket = when {
            densityDpi <= 120 -> "ldpi"
            densityDpi <= 160 -> "mdpi"
            densityDpi <= 240 -> "hdpi"
            densityDpi <= 320 -> "xhdpi"
            densityDpi <= 480 -> "xxhdpi"
            else -> "xxxhdpi"
        }

        return mapOf(
            "width_px" to widthPx,
            "height_px" to heightPx,
            "resolution" to "${widthPx}x${heightPx}",
            "density_dpi" to densityDpi,
            "density" to density,
            "density_bucket" to densityBucket,
            "screen_inches" to screenInches
        )
    }

    private fun getDeviceData(): Map<String, Any> {
        val supportedAbis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.joinToString(", ")
        } else {
            @Suppress("DEPRECATION")
            Build.CPU_ABI
        }

        return mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "device" to Build.DEVICE,
            "brand" to Build.BRAND,
            "product" to Build.PRODUCT,
            "android_version" to Build.VERSION.RELEASE,
            "sdk_version" to Build.VERSION.SDK_INT,
            "security_patch" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A"),
            "build_id" to Build.ID,
            "hardware" to Build.HARDWARE,
            "board" to Build.BOARD,
            "supported_abis" to supportedAbis
        )
    }
}
