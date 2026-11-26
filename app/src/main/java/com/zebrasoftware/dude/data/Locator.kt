package com.zebrasoftware.dude.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** A fix this accurate (in metres) is saved straight away. */
internal const val GOOD_ACCURACY_M = 25f

/** How long to wait for GPS before using the best fix so far. */
private const val FIX_TIMEOUT_MS = 20_000L

/** A cached fix this recent can stand in when no fresh fix arrives. */
private const val CACHED_FIX_MAX_AGE_MS = 5 * 60 * 1000L

internal fun hasLocationPermission(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

internal fun isLocationEnabled(context: Context): Boolean =
    context.getSystemService(LocationManager::class.java).isLocationEnabled

/** The most recent location any app has received, if location permission is granted. */
@SuppressLint("MissingPermission")
internal fun lastKnownLocation(context: Context, maxAgeMs: Long = Long.MAX_VALUE): Location? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(LocationManager::class.java)
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .filter { System.currentTimeMillis() - it.time <= maxAgeMs }
        .maxByOrNull { it.time }
}

/**
 * Listens to GPS and network location at the same time and returns the most accurate
 * fix. Stops early once a fix is within [GOOD_ACCURACY_M], otherwise after a timeout.
 * [onProgress] gets each improved fix so the UI can show the accuracy as it improves.
 *
 * Must be called on the main thread. Returns null if no location could be found.
 */
@SuppressLint("MissingPermission")
internal suspend fun findBestFix(context: Context, onProgress: (Location) -> Unit): Location? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(LocationManager::class.java)
    var best: Location? = null

    val listener = LocationListener { location ->
        val current = best
        if (current == null || location.accuracy < current.accuracy) {
            best = location
            onProgress(location)
        }
    }

    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

    try {
        providers.forEach { provider ->
            runCatching { manager.requestLocationUpdates(provider, 1_000L, 0f, listener, Looper.getMainLooper()) }
        }
        withTimeoutOrNull(FIX_TIMEOUT_MS) {
            while (true) {
                val current = best
                if (current != null && current.accuracy <= GOOD_ACCURACY_M) break
                delay(250)
            }
        }
    } finally {
        manager.removeUpdates(listener)
    }

    return best ?: lastKnownLocation(context, CACHED_FIX_MAX_AGE_MS)
}
