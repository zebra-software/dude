package com.zebrasoftware.dude.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zebrasoftware.dude.R
import com.zebrasoftware.dude.data.DudeSettings
import com.zebrasoftware.dude.data.GOOD_ACCURACY_M
import com.zebrasoftware.dude.data.ParkingSpot
import com.zebrasoftware.dude.data.ParkingStore
import com.zebrasoftware.dude.data.Place
import com.zebrasoftware.dude.data.findBestFix
import com.zebrasoftware.dude.data.hasLocationPermission
import com.zebrasoftware.dude.data.isLocationEnabled
import com.zebrasoftware.dude.data.lastKnownLocation
import com.zebrasoftware.dude.data.lookupAddress
import com.zebrasoftware.dude.data.searchPlaces
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.roundToInt

/** What the bottom panel is showing. */
private enum class Mode {
    /** Choose how to set the parking spot: GPS or search/pick on the map. */
    Choose,

    /** Waiting for a GPS fix. */
    Locating,

    /** Moving the map under a fixed pin to mark the exact spot. */
    Picking,

    /** A spot is saved. */
    Parked
}

private const val PARKED_ZOOM = 18.0

/** Space between the map and the screen edges, and where its rounded corner ends. */
private val MAP_MARGIN = 16.dp
private val MAP_CORNER = 44.dp

/** Where TopAppBar already starts its title (4 dp bar padding + 12 dp title inset). */
private val TITLE_INSET = 16.dp
private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
private const val SEARCH_DELAY_MS = 500L
private const val SEARCH_MIN_CHARS = 3

/**
 * The whole app: one screen with the map as the base, a top bar with the title and
 * settings, and a panel at the bottom that changes with what the user is doing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DudeApp(settings: DudeSettings, darkTheme: Boolean) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val store = remember { ParkingStore(context.applicationContext) }
    var spot by remember { mutableStateOf(store.load()) }
    var floor by rememberSaveable { mutableStateOf(spot?.floor.orEmpty()) }
    var mode by remember { mutableStateOf(if (spot != null) Mode.Parked else Mode.Choose) }
    var accuracy by remember { mutableStateOf<Float?>(null) }
    var pickHint by remember { mutableStateOf<String?>(null) }
    var address by remember { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var locateJob by remember { mutableStateOf<Job?>(null) }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var panelMinHeight by remember { mutableIntStateOf(0) }

    var query by rememberSaveable { mutableStateOf("") }
    var pickedQuery by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    var focusSearch by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    var floorFocused by remember { mutableStateOf(false) }
    var spaceBelowMapPx by remember { mutableIntStateOf(0) }

    // ---- Map helpers ------------------------------------------------------------

    fun moveCamera(latitude: Double, longitude: Double, zoom: Double = PARKED_ZOOM, animate: Boolean = true) {
        val update = CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoom)
        map?.let { if (animate) it.animateCamera(update) else it.moveCamera(update) }
    }

    fun showMessage(@StringRes message: Int) {
        val text = context.getString(message)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(text)
        }
    }

    // ---- Spot changes -----------------------------------------------------------

    fun commitFloor() {
        val normalized = floor.trim()
        floor = normalized
        spot?.takeIf { it.floor != normalized }?.copy(floor = normalized)?.let { updated ->
            store.save(updated)
            spot = updated
        }
    }

    fun saveSpot(latitude: Double, longitude: Double) {
        commitFloor()
        val saved = ParkingSpot(latitude, longitude, System.currentTimeMillis(), floor)
        store.save(saved)
        spot = saved
        mode = Mode.Parked
    }

    fun clearSpot() {
        store.clear()
        spot = null
        floor = ""
        mode = Mode.Choose
    }

    // ---- Choosing a location ----------------------------------------------------

    fun startPicking(
        hint: String? = null,
        withSearch: Boolean = false,
        recenter: Boolean = false
    ) {
        locateJob?.cancel()
        locateJob = null
        pickHint = hint
        query = ""
        results = emptyList()
        searched = false
        mode = Mode.Picking
        focusSearch = withSearch
        if (recenter) {
            // Start on the car if there is one, otherwise where the phone was last seen.
            val current = spot
            if (current != null) moveCamera(current.latitude, current.longitude)
            else lastKnownLocation(context)?.let { moveCamera(it.latitude, it.longitude, 16.0) }
        }
    }

    fun startLocating() {
        locateJob?.cancel()
        accuracy = null
        mode = Mode.Locating
        locateJob = scope.launch {
            val fix = findBestFix(context) { location ->
                accuracy = location.accuracy
                moveCamera(location.latitude, location.longitude, 17.0)
            }
            locateJob = null
            when {
                fix == null -> startPicking(context.getString(R.string.hint_no_fix))
                fix.accuracy <= GOOD_ACCURACY_M -> {
                    saveSpot(fix.latitude, fix.longitude)
                    moveCamera(fix.latitude, fix.longitude)
                    showMessage(R.string.msg_saved_gps)
                }
                else -> {
                    moveCamera(fix.latitude, fix.longitude)
                    startPicking(context.getString(R.string.hint_approximate, fix.accuracy.roundToInt()))
                }
            }
        }
    }

    fun cancelChoosing() {
        locateJob?.cancel()
        locateJob = null
        focusManager.clearFocus()
        mode = if (spot != null) Mode.Parked else Mode.Choose
        spot?.let { moveCamera(it.latitude, it.longitude) }
    }

    val locationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (isLocationEnabled(context)) startLocating()
        else startPicking(context.getString(R.string.hint_location_off))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        when {
            result.values.none { it } -> startPicking(context.getString(R.string.hint_no_permission))
            !isLocationEnabled(context) ->
                locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            else -> startLocating()
        }
    }

    fun useGps() {
        commitFloor()
        when {
            !hasLocationPermission(context) -> permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            !isLocationEnabled(context) ->
                locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            else -> startLocating()
        }
    }

    fun confirmPickedSpot() {
        val target = map?.cameraPosition?.target ?: return
        focusManager.clearFocus()
        saveSpot(target.latitude, target.longitude)
    }

    fun selectPlace(place: Place) {
        pickedQuery = place.title
        query = place.title
        results = emptyList()
        searched = false
        focusManager.clearFocus()
        moveCamera(place.latitude, place.longitude, PARKED_ZOOM)
    }

    // ---- Effects ----------------------------------------------------------------

    // Put the camera on the car (or the phone's last location) when the map is ready.
    LaunchedEffect(map) {
        if (map == null) return@LaunchedEffect
        val current = spot
        if (current != null) {
            moveCamera(current.latitude, current.longitude, animate = false)
        } else {
            lastKnownLocation(context)?.let { moveCamera(it.latitude, it.longitude, 15.0, animate = false) }
        }
    }

    LaunchedEffect(spot?.latitude, spot?.longitude) {
        address = null
        val current = spot ?: return@LaunchedEffect
        address = lookupAddress(context, current.latitude, current.longitude)
    }

    // Search as you type, after a short pause, so the free search service isn't flooded.
    LaunchedEffect(query, mode) {
        val text = query.trim()
        if (mode != Mode.Picking || text.length < SEARCH_MIN_CHARS || text == pickedQuery) {
            results = emptyList()
            searching = false
            searched = false
            return@LaunchedEffect
        }
        delay(SEARCH_DELAY_MS)
        searching = true
        val near = map?.cameraPosition?.target
        results = searchPlaces(text, near?.latitude, near?.longitude)
        searching = false
        searched = true
    }

    LaunchedEffect(mode, focusSearch) {
        if (mode == Mode.Picking && focusSearch) {
            delay(300)
            runCatching { searchFocus.requestFocus() }
            focusSearch = false
        }
    }

    BackHandler(enabled = mode == Mode.Picking || mode == Mode.Locating) {
        cancelChoosing()
    }

    // Searching: a query is typed or results are showing.
    val searchActive = mode == Mode.Picking &&
        ((searchFocused && query.isNotBlank()) || searching || searched || results.isNotEmpty())

    // How much of the map the keyboard covers, so search results can stay above it.
    val keyboardOverMap = with(density) {
        (WindowInsets.ime.getBottom(density) - spaceBelowMapPx).coerceAtLeast(0).toDp()
    }

    // Back closes the search first, then leaves picking (handler added last wins).
    BackHandler(enabled = searchActive) {
        query = ""
        focusManager.clearFocus()
    }

    // ---- UI ---------------------------------------------------------------------

    if (showSettings) {
        SettingsSheet(settings = settings, onDismiss = { showSettings = false })
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.clear_title)) },
            text = { Text(stringResource(R.string.clear_body)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; clearSpot() }) {
                    Text(stringResource(R.string.action_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    // Starts where the map's rounded corner turns straight
                    // (16 dp map margin + 28 dp corner radius = 44 dp from the edge).
                    Text(
                        text = stringResource(if (spot != null) R.string.title_found else R.string.title_empty),
                        modifier = Modifier.padding(start = MAP_CORNER - TITLE_INSET),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .navigationBarsPadding()
                // The keyboard only pushes the layout up while typing the parking floor.
                // While searching it simply covers the bottom, so the map never resizes.
                .then(if (floorFocused) Modifier.imePadding() else Modifier)
        ) {
            // The map, inset from the screen edges with rounded corners.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = MAP_MARGIN, end = MAP_MARGIN, top = 8.dp)
                    .onGloballyPositioned { coordinates ->
                        val root = coordinates.findRootCoordinates()
                        spaceBelowMapPx = (root.size.height - coordinates.boundsInRoot().bottom).roundToInt()
                    }
            ) {
                ParkingMap(
                    darkTheme = darkTheme,
                    // Only shown once parked; while choosing, the pin in the middle is used instead.
                    carAt = spot?.takeIf { mode == Mode.Parked }?.let { LatLng(it.latitude, it.longitude) },
                    onMapReady = { map = it },
                    modifier = Modifier.fillMaxSize()
                )
                RoundedCornerMask(
                    color = MaterialTheme.colorScheme.surface,
                    radius = MAP_CORNER - MAP_MARGIN,
                    modifier = Modifier.matchParentSize()
                )

                MapAttribution(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        // Low in the corner, lined up with the title, clear of the rounded edge.
                        .padding(start = MAP_CORNER - MAP_MARGIN, bottom = 6.dp)
                )

                // While picking, the pin stays in the middle and the map moves under it.
                if (mode == Mode.Picking) {
                    CarPin(
                        lifted = true,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = -(PIN_TIP - PIN_SIZE / 2))
                    )
                    SearchOverlay(
                        query = query,
                        onQueryChange = {
                            query = it
                            pickedQuery = null
                        },
                        results = results,
                        searching = searching,
                        searched = searched,
                        focusRequester = searchFocus,
                        onFocusChange = { searchFocused = it },
                        onSearch = { focusManager.clearFocus() },
                        onPlaceSelected = ::selectPlace,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            // Keep the result list above the keyboard where it covers the map.
                            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + keyboardOverMap)
                    )
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
            }

            // The panel never shrinks, so the map keeps the same size when switching
            // between choosing a spot and looking at it. Buttons stay at the bottom.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = with(density) { panelMinHeight.toDp() })
                    .onSizeChanged { panelMinHeight = maxOf(panelMinHeight, it.height) }
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                val currentSpot = spot
                val parkedAt = remember(currentSpot?.savedAt) {
                    currentSpot?.let { formatParkedAt(context, it.savedAt) }
                }
                val addressText = address ?: stringResource(R.string.finding_address)

                // Every state uses the same layout: a title and one line of text, the
                // parking floor, and a row of equal buttons, so nothing jumps around.
                val (title, body) = when (mode) {
                    Mode.Parked -> addressText to parkedAt
                    Mode.Picking -> if (currentSpot != null) {
                        addressText to (pickHint ?: parkedAt)
                    } else {
                        stringResource(R.string.choose_title) to (pickHint ?: stringResource(R.string.hint_pick))
                    }
                    Mode.Locating -> stringResource(R.string.locating_title) to (
                        accuracy?.let { stringResource(R.string.locating_accuracy, it.roundToInt()) }
                            ?: stringResource(R.string.locating_waiting)
                        )
                    Mode.Choose -> stringResource(R.string.choose_title) to stringResource(R.string.choose_body)
                }

                PanelLayout(
                    title = title,
                    body = body,
                    floor = floor,
                    onFloorChange = { floor = it },
                    onFloorDone = ::commitFloor,
                    onFloorFocusChange = { floorFocused = it },
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                    showTrailing = mode == Mode.Parked || mode == Mode.Locating,
                    trailing = {
                        if (mode == Mode.Parked) {
                            IconButton(onClick = { confirmClear = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_clear))
                            }
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                        }
                    }
                ) {
                    when (mode) {
                        Mode.Parked -> {
                            PanelButton(Icons.Filled.Navigation, stringResource(R.string.action_navigate)) {
                                currentSpot?.let { navigate(context, it) }
                            }
                            PanelButton(Icons.Filled.EditLocationAlt, stringResource(R.string.action_change)) {
                                startPicking(recenter = true)
                            }
                        }
                        Mode.Picking -> {
                            PanelButton(Icons.Filled.MyLocation, stringResource(R.string.action_use_gps), onClick = ::useGps)
                            PanelButton(Icons.Filled.Check, stringResource(R.string.action_park_here), onClick = ::confirmPickedSpot)
                        }
                        Mode.Locating -> {
                            PanelButton(Icons.Filled.Close, stringResource(R.string.action_cancel), onClick = ::cancelChoosing)
                            PanelButton(Icons.Filled.EditLocationAlt, stringResource(R.string.action_pick_instead)) {
                                startPicking()
                            }
                        }
                        Mode.Choose -> {
                            PanelButton(Icons.Filled.AddLocationAlt, stringResource(R.string.action_set_spot)) {
                                commitFloor()
                                startPicking(recenter = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Map overlays ---------------------------------------------------------------

@Composable
private fun SearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<Place>,
    searching: Boolean,
    searched: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onPlaceSelected: (Place) -> Unit,
    modifier: Modifier = Modifier
) {
    // Fills the map's height so a long result list scrolls inside the map instead of
    // being cut off. Empty space here doesn't block touches on the map.
    Column(
        modifier = modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 4.dp
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChange(it.isFocused) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    when {
                        searching -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        query.isNotEmpty() -> IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // The search key just closes the keyboard, so all results can be seen and picked.
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }

        if (results.isNotEmpty() || (searched && !searching)) {
            Card(
                modifier = Modifier.weight(1f, fill = false),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (results.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_no_results),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    results.forEach { place ->
                        ListItem(
                            headlineContent = { Text(place.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = if (place.subtitle.isNotBlank()) {
                                { Text(place.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            } else {
                                null
                            },
                            leadingContent = { Icon(Icons.Filled.Place, contentDescription = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onPlaceSelected(place) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Credits for the map, as OpenStreetMap and OpenMapTiles require. Tapping it opens
 * OpenStreetMap's copyright and licence page. Full credits are in Settings > About.
 */
@Composable
private fun MapAttribution(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        onClick = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OSM_COPYRIGHT_URL)))
            }
        }
    ) {
        Text(
            text = stringResource(R.string.map_attribution),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ---- Panel ----------------------------------------------------------------------

/**
 * The bottom panel, shared by every state: a title with one line of text below it
 * (and an optional icon or spinner on the right), the parking floor, and a row of
 * equally sized buttons.
 */
@Composable
private fun PanelLayout(
    title: String,
    body: String?,
    floor: String,
    onFloorChange: (String) -> Unit,
    onFloorDone: () -> Unit,
    onFloorFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showTrailing: Boolean = false,
    trailing: @Composable () -> Unit = {},
    buttons: @Composable RowScope.() -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (body != null) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (showTrailing) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    trailing()
                }
            }
        }
        FloorField(floor, onFloorChange, onFloorDone, onFocusChange = onFloorFocusChange)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = buttons)
    }
}

/** A full-height filled button that shares the row equally with the others. */
@Composable
private fun RowScope.PanelButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---- Helpers --------------------------------------------------------------------

/** Weekday and time the car was parked, with the date added once it is more than a few days ago. */
private fun formatParkedAt(context: Context, savedAt: Long): String {
    var flags = DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY
    if (System.currentTimeMillis() - savedAt > 6 * DateUtils.DAY_IN_MILLIS) {
        flags = flags or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
    }
    return DateUtils.formatDateTime(context, savedAt, flags)
}

private fun navigate(context: Context, spot: ParkingSpot) {
    val point = "${spot.latitude},${spot.longitude}"
    val label = Uri.encode(context.getString(R.string.parked_car))
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$point?q=$point($label)")))
    } catch (_: ActivityNotFoundException) {
        val osm = "https://www.openstreetmap.org/?mlat=${spot.latitude}&mlon=${spot.longitude}" +
            "#map=19/${spot.latitude}/${spot.longitude}"
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(osm)))
    }
}
