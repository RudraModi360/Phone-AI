package com.example.tools.builtin

import android.annotation.SuppressLint
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import com.example.service.Parameters
import com.example.service.PropertySchema
import com.example.tools.AndroidPathResolver
import com.example.tools.BaseTool
import com.example.tools.PermissionManager
import com.example.tools.PermissionType
import com.example.tools.RiskLevel
import com.example.tools.ToolResult
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume

/**
 * Android Location Tool - Access real GPS location from the device.
 * Uses FusedLocationProviderClient for best accuracy.
 * NO MOCK DATA - Returns actual device location.
 */
class LocationTool : BaseTool {
    override val name = "location"
    override val description = "Get device location: current GPS coordinates, last known location, reverse geocode to address."
    override val riskLevel = RiskLevel.SAFE

    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "operation" to PropertySchema(
                type = "STRING",
                description = "Operation: 'get_current', 'get_last_known', 'get_address'"
            ),
            "latitude" to PropertySchema(
                type = "NUMBER",
                description = "Latitude for get_address operation"
            ),
            "longitude" to PropertySchema(
                type = "NUMBER",
                description = "Longitude for get_address operation"
            ),
            "accuracy" to PropertySchema(
                type = "STRING",
                description = "Accuracy level: 'high', 'balanced', 'low' (default: 'balanced')"
            )
        ),
        required = emptyList()
    )

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        private const val LOCATION_TIMEOUT_MS = 15000L
    }

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val operation = args["operation"] as? String ?: "get_current"
        val latitude = (args["latitude"] as? Number)?.toDouble()
        val longitude = (args["longitude"] as? Number)?.toDouble()
        val accuracy = args["accuracy"] as? String ?: "balanced"

        val context = AndroidPathResolver.getContext()
            ?: return ToolResult.error("Application context not available")

        // Request location permission
        val permissionError = PermissionManager.ensurePermissions(PermissionType.LOCATION)
        if (permissionError != null) {
            return ToolResult.error(permissionError)
        }

        return try {
            when (operation) {
                "get_current" -> getCurrentLocation(context, accuracy)
                "get_last_known" -> getLastKnownLocation(context)
                "get_address" -> {
                    if (latitude == null || longitude == null) {
                        return ToolResult.error("Missing 'latitude' and 'longitude' parameters for get_address")
                    }
                    getAddressFromCoordinates(context, latitude, longitude)
                }
                else -> ToolResult.error("Unknown operation: '$operation'. Use: get_current, get_last_known, get_address")
            }
        } catch (e: SecurityException) {
            ToolResult.error("Permission denied: ${PermissionManager.getPermissionDeniedMessage(context, PermissionType.LOCATION)}")
        } catch (e: Exception) {
            ToolResult.error("Location operation failed: ${e.message}")
        }
    }

    /**
     * Get current location using FusedLocationProviderClient.
     */
    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(context: android.content.Context, accuracy: String): ToolResult {
        val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        
        val priority = when (accuracy.lowercase()) {
            "high" -> Priority.PRIORITY_HIGH_ACCURACY
            "low" -> Priority.PRIORITY_LOW_POWER
            else -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        return try {
            val location = withTimeout(LOCATION_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val locationRequest = LocationRequest.Builder(priority, 1000L)
                        .setWaitForAccurateLocation(priority == Priority.PRIORITY_HIGH_ACCURACY)
                        .setMinUpdateIntervalMillis(500L)
                        .setMaxUpdates(1)
                        .build()

                    val callback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            fusedClient.removeLocationUpdates(this)
                            val loc = result.lastLocation
                            if (loc != null && continuation.isActive) {
                                continuation.resume(loc)
                            }
                        }
                    }

                    fusedClient.requestLocationUpdates(
                        locationRequest,
                        callback,
                        Looper.getMainLooper()
                    )

                    continuation.invokeOnCancellation {
                        fusedClient.removeLocationUpdates(callback)
                    }
                }
            }

            formatLocationResult(context, location, "Current Location")
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            // Fallback to last known location
            getLastKnownLocation(context)
        }
    }

    /**
     * Get last known location (faster, may be stale).
     */
    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocation(context: android.content.Context): ToolResult {
        val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

        return suspendCancellableCoroutine { continuation ->
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        continuation.resume(formatLocationResult(context, location, "Last Known Location"))
                    } else {
                        continuation.resume(ToolResult.error("No location available. Try 'get_current' to request a fresh location."))
                    }
                }
                .addOnFailureListener { e ->
                    continuation.resume(ToolResult.error("Failed to get location: ${e.message}"))
                }
        }
    }

    /**
     * Format location result with optional address.
     */
    private fun formatLocationResult(context: android.content.Context, location: Location, label: String): ToolResult {
        val lat = location.latitude
        val lng = location.longitude
        val accuracy = location.accuracy
        val altitude = if (location.hasAltitude()) location.altitude else null
        val speed = if (location.hasSpeed()) location.speed else null
        val bearing = if (location.hasBearing()) location.bearing else null
        val time = DATE_FORMAT.format(Date(location.time))

        // Try to get address
        val address = try {
            getAddressString(context, lat, lng)
        } catch (e: Exception) {
            null
        }

        val details = mutableMapOf<String, Any>(
            "latitude" to lat,
            "longitude" to lng,
            "accuracy_meters" to accuracy,
            "timestamp" to time,
            "provider" to (location.provider ?: "fused")
        )

        altitude?.let { details["altitude_meters"] = it }
        speed?.let { details["speed_mps"] = it }
        bearing?.let { details["bearing_degrees"] = it }
        address?.let { details["address"] = it }

        val formatted = buildString {
            appendLine("$label")
            appendLine("=" .repeat(label.length))
            appendLine()
            appendLine("📍 Coordinates: $lat, $lng")
            appendLine("🎯 Accuracy: ${accuracy.toInt()} meters")
            altitude?.let { appendLine("⛰️ Altitude: ${it.toInt()} meters") }
            speed?.let { appendLine("🚗 Speed: ${String.format("%.1f", it * 3.6)} km/h") }
            appendLine("🕐 Time: $time")
            appendLine()
            address?.let { 
                appendLine("📮 Address:")
                appendLine("   $it")
            }
            appendLine()
            appendLine("Google Maps: https://maps.google.com/?q=$lat,$lng")
        }

        return ToolResult.success(formatted, details)
    }

    /**
     * Get address from coordinates using Geocoder.
     */
    private fun getAddressFromCoordinates(context: android.content.Context, lat: Double, lng: Double): ToolResult {
        val address = try {
            getAddressString(context, lat, lng)
        } catch (e: Exception) {
            return ToolResult.error("Geocoding failed: ${e.message}")
        }

        if (address == null) {
            return ToolResult.error("No address found for coordinates ($lat, $lng)")
        }

        val details = mapOf(
            "latitude" to lat,
            "longitude" to lng,
            "address" to address,
            "google_maps_url" to "https://maps.google.com/?q=$lat,$lng"
        )

        val formatted = """
Address for Coordinates
=======================
📍 Location: $lat, $lng

📮 Address:
   $address

🗺️ Google Maps: https://maps.google.com/?q=$lat,$lng
        """.trimIndent()

        return ToolResult.success(formatted, details)
    }

    /**
     * Get address string from coordinates.
     */
    @Suppress("DEPRECATION")
    private fun getAddressString(context: android.content.Context, lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) {
            return null
        }

        val geocoder = Geocoder(context, Locale.getDefault())
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Use callback-based API for Android 13+
                var result: String? = null
                val latch = java.util.concurrent.CountDownLatch(1)
                
                geocoder.getFromLocation(lat, lng, 1) { addresses ->
                    result = addresses.firstOrNull()?.let { addr ->
                        (0..addr.maxAddressLineIndex).mapNotNull { addr.getAddressLine(it) }.joinToString(", ")
                    }
                    latch.countDown()
                }
                
                latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
                result
            } else {
                // Use synchronous API for older Android
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                addresses?.firstOrNull()?.let { addr ->
                    (0..addr.maxAddressLineIndex).mapNotNull { addr.getAddressLine(it) }.joinToString(", ")
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
