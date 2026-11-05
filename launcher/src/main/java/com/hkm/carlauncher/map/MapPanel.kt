package com.hkm.carlauncher.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.location.Location
import android.location.LocationManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hkm.carlauncher.location.Fix
import com.hkm.carlauncher.location.LocationState
import com.hkm.carlauncher.ui.theme.CardShape
import com.hkm.carlauncher.ui.theme.LauncherColors
import com.hkm.carlauncher.ui.theme.LauncherIcons
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private val DUBAI = LatLng(25.2048, 55.2708)
private const val FOLLOW_ZOOM = 15.0
private val ROAD_LAYERS = arrayOf("road-major", "road-minor", "road-name")

@Composable
fun MapPanel(
    source: MapSource,
    location: LocationState,
    showHint: Boolean,
    waitingText: String,
    onTap: () -> Unit,
    onAllowLocation: () -> Unit,
    onGetMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnTap by rememberUpdatedState(onTap)
    val follow = remember { FollowState() }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var streetName by remember { mutableStateOf<String?>(null) }
    var bearing by remember { mutableStateOf(0f) }
    var tick by remember { mutableStateOf(0L) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { m ->
                m.uiSettings.apply {
                    isCompassEnabled = false
                    isLogoEnabled = false
                    isAttributionEnabled = true
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                }
                m.cameraPosition = CameraPosition.Builder().target(DUBAI).zoom(FOLLOW_ZOOM).build()
                m.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        follow.onUserGesture(System.currentTimeMillis())
                    }
                }
                m.addOnMapClickListener { currentOnTap(); true }
                map = m
            }
        }
    }

    // Forward the screen's lifecycle to the MapView.
    DisposableEffect(lifecycle) {
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
            val state = lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    // (Re)load the style whenever the tile source changes.
    LaunchedEffect(map, source) {
        val m = map ?: return@LaunchedEffect
        if (source == MapSource.Unavailable) return@LaunchedEffect
        val template = context.assets.open("map/style-dark.json").bufferedReader().use { it.readText() }
        style = null
        m.setStyle(Style.Builder().fromJson(styleJsonFor(template, source))) { loaded ->
            enableLocationComponent(context, m, loaded)
            style = loaded
        }
    }

    // Re-check following every second so the camera returns 15 s after the last gesture.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            tick++
        }
    }

    val fix: Fix? = when (location) {
        is LocationState.Live -> location.fix
        is LocationState.Waiting -> location.lastKnown
        LocationState.NoPermission -> null
    }

    LaunchedEffect(map, style, fix, tick) {
        val m = map ?: return@LaunchedEffect
        val f = fix ?: return@LaunchedEffect
        bearing = markerBearing(bearing, f)
        if (style != null && m.locationComponent.isLocationComponentActivated) {
            m.locationComponent.forceLocationUpdate(f.toLocation(bearing))
        }
        val target = LatLng(f.lat, f.lon)
        if (follow.isFollowing(System.currentTimeMillis())) {
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(target, m.cameraPosition.zoom.coerceAtLeast(12.0)), 800)
        }
        if (style != null) {
            val p = m.projection.toScreenLocation(target)
            val features = m.queryRenderedFeatures(RectF(p.x - 40, p.y - 40, p.x + 40, p.y + 40), *ROAD_LAYERS)
            streetName = pickStreetName(features.map { feat ->
                val name = listOf("name:en", "name_en", "name").firstNotNullOfOrNull { k ->
                    if (feat.hasProperty(k)) feat.getStringProperty(k) else null
                }
                RoadFeature(name, if (feat.hasProperty("class")) feat.getStringProperty("class") else null)
            }) ?: streetName
        }
    }

    Box(modifier.clip(CardShape).border(1.dp, LauncherColors.Outline, CardShape).background(Color(0xFF0D1117))) {
        // Always composed: re-adding a MapView that is still attached to an old holder would crash.
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        streetName?.let { Chip(it, Modifier.align(Alignment.TopStart).padding(16.dp), bold = true) }
        when (source) {
            is MapSource.Offline -> Chip("● Offline map", Modifier.align(Alignment.TopEnd).padding(16.dp), color = LauncherColors.Ok)
            MapSource.Online -> Chip("● Online map", Modifier.align(Alignment.TopEnd).padding(16.dp), color = LauncherColors.Ok)
            MapSource.Unavailable -> Unit
        }

        if (source != MapSource.Unavailable) {
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MapButton("Zoom in", { map?.animateCamera(CameraUpdateFactory.zoomIn()) }) { LauncherIcons.Plus(it, LauncherColors.Text) }
                MapButton("Zoom out", { map?.animateCamera(CameraUpdateFactory.zoomOut()) }) { LauncherIcons.Minus(it, LauncherColors.Text) }
                MapButton("Re-centre", {
                    follow.onRecenter()
                    fix?.let { f -> map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(f.lat, f.lon), FOLLOW_ZOOM)) }
                }) { LauncherIcons.Recenter(it, LauncherColors.Accent) }
            }
        }

        val banner: Pair<String, (() -> Unit)?>? = when {
            source == MapSource.Unavailable -> null
            location == LocationState.NoPermission -> "Allow location to show where you are" to onAllowLocation
            location is LocationState.Waiting -> waitingText to null
            showHint -> "Tap map to open navigation" to null
            else -> null
        }
        banner?.let { (text, action) ->
            Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(text, Modifier, color = LauncherColors.Muted)
                if (action != null) ActionButton("Allow location", action, Modifier.padding(start = 10.dp))
            }
        }

        if (source == MapSource.Unavailable) {
            Column(
                Modifier.fillMaxSize().background(Color(0xFF0D1117)).padding(40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Map not available — connect to the internet once, or load the UAE map from USB",
                    color = LauncherColors.Text,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                )
                ActionButton("Get the map", onGetMap, Modifier.padding(top = 20.dp))
            }
        }
    }
}

/**
 * The car marker: MapLibre's location puck in the accent colour. It is fed with our own GPS fixes
 * (no MapLibre location engine), so it works as soon as permission arrives, without a style reload.
 */
@SuppressLint("MissingPermission")
private fun enableLocationComponent(context: Context, map: MapLibreMap, style: Style) {
    val options = LocationComponentOptions.builder(context)
        .foregroundTintColor(0xFF4F86F7.toInt())
        .bearingTintColor(0xFF4F86F7.toInt())
        .accuracyAlpha(0f)
        .build()
    map.locationComponent.apply {
        activateLocationComponent(
            LocationComponentActivationOptions.builder(context, style)
                .locationComponentOptions(options)
                .useDefaultLocationEngine(false)
                .build(),
        )
        isLocationComponentEnabled = true
        cameraMode = CameraMode.NONE
        renderMode = RenderMode.GPS
    }
}

private fun Fix.toLocation(bearing: Float) = Location(LocationManager.GPS_PROVIDER).also {
    it.latitude = lat
    it.longitude = lon
    it.bearing = bearing
    it.time = timeMs
    speedMps?.let { s -> it.speed = s }
}

@Composable
private fun Chip(text: String, modifier: Modifier, bold: Boolean = false, color: Color = LauncherColors.Text) {
    Text(
        text,
        color = color,
        fontSize = 15.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xEB12161C))
            .border(1.dp, LauncherColors.Outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun MapButton(description: String, onClick: () -> Unit, icon: @Composable (Modifier) -> Unit) {
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xEB12161C))
            .border(1.dp, LauncherColors.Outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { icon(Modifier.size(26.dp)) }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(LauncherColors.Accent).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) { Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}
