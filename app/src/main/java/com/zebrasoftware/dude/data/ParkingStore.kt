package com.zebrasoftware.dude.data

import android.content.Context
import androidx.core.content.edit

/** A saved parking spot. */
internal data class ParkingSpot(
    val latitude: Double,
    val longitude: Double,
    val savedAt: Long,
    val floor: String
)

/** Keeps the parking spot on this device only. */
internal class ParkingStore(context: Context) {
    private val prefs = context.getSharedPreferences("parked_car", Context.MODE_PRIVATE)

    fun load(): ParkingSpot? {
        if (!prefs.contains(KEY_SAVED)) return null
        return ParkingSpot(
            latitude = Double.fromBits(prefs.getLong(KEY_LAT, 0)),
            longitude = Double.fromBits(prefs.getLong(KEY_LNG, 0)),
            savedAt = prefs.getLong(KEY_SAVED, 0),
            floor = prefs.getString(KEY_FLOOR, null).orEmpty()
        )
    }

    fun save(spot: ParkingSpot) = prefs.edit {
        putLong(KEY_LAT, spot.latitude.toBits())
        putLong(KEY_LNG, spot.longitude.toBits())
        putLong(KEY_SAVED, spot.savedAt)
        putString(KEY_FLOOR, spot.floor)
    }

    fun clear() = prefs.edit { clear() }

    private companion object {
        const val KEY_LAT = "lat"
        const val KEY_LNG = "lng"
        const val KEY_SAVED = "saved"
        const val KEY_FLOOR = "floor"
    }
}
