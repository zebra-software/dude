package com.zebrasoftware.dude.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.PathParser
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zebrasoftware.dude.data.DarkMapStyle
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

/*
 * Map tiles from OpenFreeMap (https://openfreemap.org): free OpenStreetMap vector
 * tiles with no API key or usage limits. Attribution is shown by the app itself.
 * Light mode uses the Liberty style; dark mode uses a recoloured copy of it
 * (see DarkMapStyle), falling back to OpenFreeMap's own minimal dark style.
 */
private const val STYLE_DARK_FALLBACK = "https://tiles.openfreemap.org/styles/dark"

private const val CAR_IMAGE = "dude-car-pin"
private const val CAR_SOURCE = "dude-car"
private const val CAR_LAYER = "dude-car"

/** Size of the car pin, and how far down from its top the pin's tip is (Place icon tip at y = 22/24). */
internal val PIN_SIZE = 56.dp
internal val PIN_TIP = PIN_SIZE * (22f / 24f)

/** Material "place" and "directions_car" icons (Apache License 2.0), used to draw the map marker. */
private const val PLACE_PATH =
    "M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13c0,-3.87 -3.13,-7 -7,-7z"
private const val CAR_PATH =
    "M18.92,6.01C18.72,5.42 18.16,5 17.5,5h-11c-0.66,0 -1.21,0.42 -1.42,1.01L3,12v8c0,0.55 0.45,1 1,1h1" +
        "c0.55,0 1,-0.45 1,-1v-1h12v1c0,0.55 0.45,1 1,1h1c0.55,0 1,-0.45 1,-1v-8l-2.08,-5.99z" +
        "M6.5,16c-0.83,0 -1.5,-0.67 -1.5,-1.5S5.67,13 6.5,13s1.5,0.67 1.5,1.5S7.33,16 6.5,16z" +
        "M17.5,16c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z" +
        "M5,11l1.5,-4.5h11L19,11L5,11z"

/**
 * A MapLibre map tied to the screen's lifecycle.
 *
 * The parked car is drawn by the map itself at [carAt] (or hidden when null), so it
 * moves perfectly with the map while panning and zooming.
 */
@Composable
internal fun ParkingMap(
    darkTheme: Boolean,
    carAt: LatLng?,
    onMapReady: (MapLibreMap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnMapReady by rememberUpdatedState(onMapReady)
    val currentCarAt by rememberUpdatedState(carAt)

    val pinColor = MaterialTheme.colorScheme.primary.toArgb()
    val discColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val outlineColor = MaterialTheme.colorScheme.surface.toArgb()

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { readyMap ->
            readyMap.uiSettings.apply {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isCompassEnabled = false
                // Attribution is drawn by the app so it matches the theme.
                isAttributionEnabled = false
                isLogoEnabled = false
            }
            map = readyMap
            currentOnMapReady(readyMap)
        }
    }

    // (Re)load the style when the theme changes, and add the car marker layer to it.
    LaunchedEffect(map, darkTheme) {
        val readyMap = map ?: return@LaunchedEffect
        style = null
        val builder = if (darkTheme) {
            DarkMapStyle.load(context)?.let { Style.Builder().fromJson(it) }
                ?: Style.Builder().fromUri(STYLE_DARK_FALLBACK)
        } else {
            Style.Builder().fromUri(DarkMapStyle.LIGHT_STYLE_URL)
        }
        readyMap.setStyle(builder) { loaded ->
            loaded.addImage(CAR_IMAGE, carMarkerBitmap(context, pinColor, discColor, outlineColor))
            loaded.addSource(GeoJsonSource(CAR_SOURCE, carFeatures(currentCarAt)))
            loaded.addLayer(
                SymbolLayer(CAR_LAYER, CAR_SOURCE).withProperties(
                    PropertyFactory.iconImage(CAR_IMAGE),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                )
            )
            style = loaded
        }
    }

    LaunchedEffect(style, carAt) {
        style?.getSourceAs<GeoJsonSource>(CAR_SOURCE)?.setGeoJson(carFeatures(carAt))
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun carFeatures(at: LatLng?): FeatureCollection =
    if (at == null) {
        FeatureCollection.fromFeatures(emptyList<Feature>())
    } else {
        FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Point.fromLngLat(at.longitude, at.latitude))))
    }

/**
 * Draws the same pin as [CarPin] into a bitmap for the map. The bitmap ends at the
 * pin's tip so it can be anchored at its bottom centre.
 */
private fun carMarkerBitmap(context: Context, pinColor: Int, discColor: Int, outlineColor: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val unit = PIN_SIZE.value * density / 24f // pixels per icon unit
    val margin = 2f * density
    val width = (24f * unit + 2 * margin).roundToInt()
    val height = (22f * unit + margin).roundToInt()

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val place = PathParser.createPathFromPathData(PLACE_PATH)
    val car = PathParser.createPathFromPathData(CAR_PATH)

    canvas.translate(margin, margin)
    canvas.scale(unit, unit)

    // Outline: a slightly larger pin behind, scaled around the tip.
    canvas.save()
    canvas.scale(1.12f, 1.12f, 12f, 22f)
    paint.color = outlineColor
    canvas.drawPath(place, paint)
    canvas.restore()

    paint.color = pinColor
    canvas.drawPath(place, paint)

    // Disc with the car, matching CarPin (22dp disc, 14dp car, centred 21dp from the top).
    val discCenterY = 21f / PIN_SIZE.value * 24f
    paint.color = discColor
    canvas.drawCircle(12f, discCenterY, 11f / PIN_SIZE.value * 24f, paint)

    val carScale = 14f / 24f / PIN_SIZE.value * 24f
    canvas.save()
    canvas.translate(12f - 12f * carScale, discCenterY - 12f * carScale)
    canvas.scale(carScale, carScale)
    paint.color = pinColor
    canvas.drawPath(car, paint)
    canvas.restore()

    return bitmap
}

/**
 * Rounds the corners of whatever is under it (the map) by painting the corners in
 * [color]. The map draws on its own surface, so normal clipping can't round it.
 */
@Composable
internal fun RoundedCornerMask(color: Color, radius: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val corner = radius.toPx()
        val mask = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner, corner)))
        }
        drawPath(mask, color)
    }
}

/**
 * The parked-car pin: a location pin in the primary colour with a car in its head.
 * Shown in the middle of the map while choosing a spot.
 */
@Composable
internal fun CarPin(lifted: Boolean, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(if (lifted) 1.1f else 1f, label = "pinScale")
    val tipOrigin = TransformOrigin(0.5f, 22f / 24f)

    Box(
        modifier = modifier
            .size(PIN_SIZE)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = tipOrigin
            }
    ) {
        // A slightly larger pin in the surface colour behind the real one works as an
        // outline, so the pin stays visible on any part of the map.
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.12f
                    scaleY = 1.12f
                    transformOrigin = tipOrigin
                }
        )
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .size(22.dp)
                .background(MaterialTheme.colorScheme.onPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
