package com.zebrasoftware.dude.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Identifies Dude to the map, search and address services, as their terms ask. */
internal const val USER_AGENT = "Dude/1.0 (Android; com.zebrasoftware.dude)"

/**
 * Place search and address lookup using Photon (https://photon.komoot.io), an
 * open geocoder built on OpenStreetMap data. Its public server allows fair use,
 * so searches are debounced in the UI and only sent after a few letters.
 */
private const val PHOTON_URL = "https://photon.komoot.io"

/** Languages Photon can return results in. Others fall back to local names. */
private val PHOTON_LANGUAGES = setOf("en", "de", "fr")

/** A search result. */
internal data class Place(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * Searches for places matching [query], preferring results near [nearLat], [nearLon].
 * Returns an empty list if the search fails.
 */
internal suspend fun searchPlaces(query: String, nearLat: Double?, nearLon: Double?): List<Place> =
    withContext(Dispatchers.IO) {
        runCatching {
            val params = buildList {
                add("q=" + URLEncoder.encode(query, "UTF-8"))
                add("limit=6")
                language()?.let { add("lang=$it") }
                if (nearLat != null && nearLon != null) {
                    add("lat=$nearLat")
                    add("lon=$nearLon")
                }
            }
            val json = getJson("$PHOTON_URL/api/?" + params.joinToString("&"))
            parseFeatures(json.optJSONArray("features"))
        }.getOrDefault(emptyList())
    }

/**
 * A short street address for a point: the phone's own geocoder first, then Photon.
 * Falls back to plain coordinates.
 */
internal suspend fun lookupAddress(context: Context, latitude: Double, longitude: Double): String =
    withContext(Dispatchers.IO) {
        val local = runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()?.let(::compactAddress)
        }.getOrNull()?.takeIf { it.isNotBlank() }

        local ?: runCatching {
            val lang = language()?.let { "&lang=$it" }.orEmpty()
            val json = getJson("$PHOTON_URL/reverse?lat=$latitude&lon=$longitude$lang")
            parseFeatures(json.optJSONArray("features")).firstOrNull()
                ?.let { listOf(it.title, it.subtitle).filter(String::isNotBlank).joinToString(", ") }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: String.format(Locale.getDefault(), "%.5f, %.5f", latitude, longitude)
    }

private fun language(): String? = Locale.getDefault().language.takeIf { it in PHOTON_LANGUAGES }

private fun getJson(url: String): JSONObject {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("User-Agent", USER_AGENT)
        return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    } finally {
        connection.disconnect()
    }
}

private fun parseFeatures(features: JSONArray?): List<Place> {
    if (features == null) return emptyList()
    return (0 until features.length()).mapNotNull { index ->
        val feature = features.optJSONObject(index) ?: return@mapNotNull null
        val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates")
            ?: return@mapNotNull null
        val props = feature.optJSONObject("properties") ?: JSONObject()
        fun field(key: String) = props.optString(key).takeIf { it.isNotBlank() }

        val street = listOfNotNull(field("street"), field("housenumber")).joinToString(" ")
            .takeIf { it.isNotBlank() }
        val title = field("name") ?: street ?: field("city") ?: return@mapNotNull null
        val subtitle = listOfNotNull(
            street.takeIf { it != title },
            listOfNotNull(field("postcode"), field("city")).joinToString(" ").takeIf { it.isNotBlank() && it != title },
            field("country")
        ).joinToString(", ")

        Place(
            title = title,
            subtitle = subtitle,
            latitude = coordinates.getDouble(1),
            longitude = coordinates.getDouble(0)
        )
    }.distinctBy { it.title to it.subtitle }
}

private fun compactAddress(address: Address): String {
    val road = address.thoroughfare?.takeIf { it.isNotBlank() }
        ?: address.featureName?.takeIf { it.isNotBlank() && it != address.subThoroughfare }
    val street = listOfNotNull(road, address.subThoroughfare?.takeIf { it.isNotBlank() })
        .joinToString(" ").takeIf { it.isNotBlank() }
    return listOfNotNull(
        street,
        address.postalCode?.takeIf { it.isNotBlank() },
        address.locality?.takeIf { it.isNotBlank() } ?: address.countryName?.takeIf { it.isNotBlank() }
    ).joinToString(", ")
}
