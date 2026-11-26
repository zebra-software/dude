package com.zebrasoftware.dude.data

import android.content.Context
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * A detailed dark map, made by recolouring OpenFreeMap's Liberty style.
 *
 * OpenFreeMap's own dark style is based on "Dark Matter", a minimal background map
 * where buildings are nearly the same colour as the ground. Liberty shows far more
 * detail, so Dude downloads it and turns its colours dark instead:
 *  - ground, water, parks and other areas are inverted to dark, muted tones,
 *  - buildings get a lighter grey so they stand out,
 *  - roads keep their order (main roads brightest) but are dimmed,
 *  - text becomes light with a dark halo.
 *
 * The result is cached, so the map also loads when the style can't be downloaded.
 */
internal object DarkMapStyle {
    const val LIGHT_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    /** Bump when the recolouring changes, so old cached versions are not used. */
    private const val CACHE_FILE = "dark-liberty-v1.json"

    private enum class Role { Fill, Building, Road, Text, Halo }

    /** The dark style as JSON, or null if it has never been downloaded. */
    suspend fun load(context: Context): String? = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, CACHE_FILE)
        runCatching {
            darken(download(LIGHT_STYLE_URL)).also { cache.writeText(it) }
        }.getOrNull() ?: cache.takeIf { it.exists() }?.readText()
    }

    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun darken(styleJson: String): String {
        val style = JSONObject(styleJson)
        val layers = style.getJSONArray("layers")

        for (index in 0 until layers.length()) {
            val layer = layers.getJSONObject(index)
            val type = layer.optString("type")
            val sourceLayer = layer.optString("source-layer")
            val paint = layer.optJSONObject("paint") ?: continue

            // Shaded relief at low zoom levels: just dim it.
            if (type == "raster") {
                paint.put("raster-brightness-max", 0.3)
                continue
            }

            val keys = paint.keys().asSequence().toList()
            for (key in keys) {
                if (!key.endsWith("color")) continue
                val role = when {
                    key == "text-halo-color" || key == "icon-halo-color" -> Role.Halo
                    key.startsWith("text-") || key.startsWith("icon-") -> Role.Text
                    sourceLayer == "building" -> Role.Building
                    type == "line" && sourceLayer == "transportation" -> Role.Road
                    else -> Role.Fill
                }
                paint.put(key, recolor(paint.get(key), role))
            }
        }
        return style.toString()
    }

    /** Recolours a paint value, including colours inside expressions and zoom stops. */
    private fun recolor(value: Any, role: Role): Any = when (value) {
        is String -> parseColor(value)?.let { formatColor(darkColor(it, role)) } ?: value
        is JSONArray -> JSONArray().apply {
            for (i in 0 until value.length()) put(recolor(value.get(i), role))
        }
        is JSONObject -> JSONObject().apply {
            value.keys().forEach { put(it, recolor(value.get(it), role)) }
        }
        else -> value
    }

    /** Colour as red, green, blue (0–255) and alpha (0–1). */
    private class Rgba(val r: Int, val g: Int, val b: Int, val a: Float)

    private fun darkColor(color: Rgba, role: Role): Rgba {
        val hsl = FloatArray(3)
        ColorUtils.RGBToHSL(color.r, color.g, color.b, hsl)
        val hue = hsl[0]
        var saturation = hsl[1]
        val lightness = hsl[2]
        val isBlue = hue in 190f..250f

        val newLightness = when (role) {
            Role.Text -> 0.92f - lightness * 0.6f
            Role.Halo -> 0.07f + (1f - lightness) * 0.2f
            Role.Road -> {
                saturation *= 0.4f
                0.17f + lightness * 0.25f
            }
            Role.Building -> {
                saturation *= 0.3f
                0.13f + (1f - lightness) * 0.75f
            }
            Role.Fill -> {
                saturation *= if (isBlue) 0.45f else 0.2f
                0.085f + (1f - lightness) * 0.45f
            }
        }

        val argb = ColorUtils.HSLToColor(
            floatArrayOf(hue, saturation.coerceIn(0f, 1f), newLightness.coerceIn(0f, 1f))
        )
        return Rgba((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, color.a)
    }

    private fun formatColor(color: Rgba): String =
        String.format(Locale.ROOT, "rgba(%d,%d,%d,%.3f)", color.r, color.g, color.b, color.a)

    private val HEX = Regex("#([0-9a-fA-F]{3,8})")
    private val FUNCTION = Regex("(rgba?|hsla?)\\(([^)]*)\\)")
    private val SEPARATORS = Regex("[,\\s/]+")

    private fun parseColor(text: String): Rgba? {
        val value = text.trim()
        when (value.lowercase(Locale.ROOT)) {
            "white" -> return Rgba(255, 255, 255, 1f)
            "black" -> return Rgba(0, 0, 0, 1f)
        }

        HEX.matchEntire(value)?.let { match ->
            var hex = match.groupValues[1]
            if (hex.length == 3 || hex.length == 4) hex = hex.map { "$it$it" }.joinToString("")
            if (hex.length != 6 && hex.length != 8) return null
            val alpha = if (hex.length == 8) hex.substring(6, 8).toInt(16) / 255f else 1f
            return Rgba(hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16), hex.substring(4, 6).toInt(16), alpha)
        }

        val match = FUNCTION.matchEntire(value) ?: return null
        val parts = match.groupValues[2].trim().split(SEPARATORS).filter { it.isNotEmpty() }
        if (parts.size < 3) return null

        fun number(part: String, scale: Float): Float? =
            if (part.endsWith("%")) part.dropLast(1).toFloatOrNull()?.div(100f)
            else part.toFloatOrNull()?.div(scale)

        val alpha = if (parts.size > 3) number(parts[3], 1f) ?: return null else 1f

        return if (match.groupValues[1].startsWith("rgb")) {
            val r = number(parts[0], 255f) ?: return null
            val g = number(parts[1], 255f) ?: return null
            val b = number(parts[2], 255f) ?: return null
            Rgba((r * 255).toInt().coerceIn(0, 255), (g * 255).toInt().coerceIn(0, 255), (b * 255).toInt().coerceIn(0, 255), alpha)
        } else {
            val hue = parts[0].toFloatOrNull() ?: return null
            val saturation = number(parts[1], 100f) ?: return null
            val lightness = number(parts[2], 100f) ?: return null
            val argb = ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness))
            Rgba((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, alpha)
        }
    }
}
