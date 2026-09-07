package com.example.nanoagent.tools

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.example.nanoagent.agent.Tool
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.Locale

class LocationTool(private val context: Context) : Tool {
    override val name = "getCurrentLocation"
    override val description = "Gets the current latitude, longitude, and approximate city name."
    override val parameters = emptyMap<String, String>()

    @SuppressLint("MissingPermission")
    override suspend fun execute(args: Map<String, String>): String {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            return "ERROR: Location permissions not granted. Request permissions from the user."
        }

        return try {
            val locationClient = LocationServices.getFusedLocationProviderClient(context)
            val location: Location? = locationClient.lastLocation.await()
            if (location != null) {
                val lat = location.latitude
                val lng = location.longitude
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                val cityName = if (!addresses.isNullOrEmpty()) {
                    addresses[0].locality ?: addresses[0].adminArea ?: "Unknown City"
                } else {
                    "Unknown City"
                }
                "Latitude: $lat, Longitude: $lng, City: $cityName"
            } else {
                "ERROR: No recent location is available. Turn on location services and try again."
            }
        } catch (e: Exception) {
            "ERROR: Could not fetch location details: ${e.message}"
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(task.exception ?: RuntimeException("Task failed"))
        }
    }
    continuation.invokeOnCancellation { /* Google Play services task cannot be cancelled reliably. */ }
}
