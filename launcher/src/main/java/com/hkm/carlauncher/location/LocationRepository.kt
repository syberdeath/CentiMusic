package com.hkm.carlauncher.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Position from the head unit via [LocationManager] (no Google Play services needed). Listens to
 * every location source the unit has (gps, fused, network, passive) and prefers GPS, because head
 * units differ in how they route their GPS receiver.
 */
class LocationRepository(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<LocationState>(LocationState.NoPermission)
    val state: StateFlow<LocationState> = _state.asStateFlow()

    private val _diagnostics = MutableStateFlow(GpsDiagnostics())
    val diagnostics: StateFlow<GpsDiagnostics> = _diagnostics.asStateFlow()

    private var started = false
    private var current: Fix? = null

    private val listener = LocationListenerCompat { location ->
        val fix = location.toFix()
        if (shouldUse(current, fix, System.currentTimeMillis())) {
            current = fix
            _state.value = LocationState.Live(fix)
        }
    }

    private val gnssCallback = object : GnssStatusCompat.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatusCompat) {
            var used = 0
            for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
            _diagnostics.value = _diagnostics.value.copy(satellitesUsed = used, satellitesInView = status.satelliteCount)
        }
    }

    /** Safe to call repeatedly, e.g. again after the user grants the location permission. */
    @SuppressLint("MissingPermission")
    fun start() {
        if (!hasPermission()) {
            _state.value = LocationState.NoPermission
            return
        }
        if (started) return
        started = true

        val providers = runCatching { manager.allProviders }.getOrDefault(emptyList())
            .filter { it in WANTED }
            .sortedBy { WANTED.indexOf(it) }
        val errors = mutableListOf<String>()

        val last = providers.mapNotNull { p -> runCatching { manager.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        _state.value = LocationState.Waiting(last?.toFix())

        val request = LocationRequestCompat.Builder(1_000L)
            .setMinUpdateDistanceMeters(0f)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .build()
        for (p in providers) {
            runCatching {
                LocationManagerCompat.requestLocationUpdates(manager, p, request, listener, Looper.getMainLooper())
            }.onFailure { errors += "$p: ${it.javaClass.simpleName} ${it.message.orEmpty()}".trim() }
        }
        runCatching { LocationManagerCompat.registerGnssStatusCallback(manager, gnssCallback, handler) }
            .onFailure { errors += "satellites: ${it.javaClass.simpleName}" }

        if (providers.isEmpty()) errors += "the unit reports no location sources"
        if (!LocationManagerCompat.isLocationEnabled(manager)) errors += "location is switched off in Android settings"
        _diagnostics.value = _diagnostics.value.copy(providers = providers, errors = errors)
    }

    fun stop() {
        if (started) {
            LocationManagerCompat.removeUpdates(manager, listener)
            LocationManagerCompat.unregisterGnssStatusCallback(manager, gnssCallback)
        }
        started = false
    }

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun Location.toFix() = Fix(
        lat = latitude,
        lon = longitude,
        bearing = if (hasBearing()) bearing else null,
        speedMps = if (hasSpeed()) speed else null,
        timeMs = time,
        provider = provider ?: "unknown",
    )

    private companion object {
        /** In order of preference. "fused" exists on Android 12+ and on many units with Google services. */
        val WANTED = listOf(LocationManager.GPS_PROVIDER, "fused", LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
    }
}

