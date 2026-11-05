package com.hkm.carlauncher.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface LocationState {
    data object NoPermission : LocationState
    data class Waiting(val lastKnown: Fix?) : LocationState
    data class Live(val fix: Fix) : LocationState
}

/** GPS position from the head unit's receiver via [LocationManager] (no Google Play services needed). */
class LocationRepository(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val _state = MutableStateFlow<LocationState>(LocationState.NoPermission)
    val state: StateFlow<LocationState> = _state.asStateFlow()
    private var started = false

    private val listener = LocationListenerCompat { location -> _state.value = LocationState.Live(location.toFix()) }

    /** Safe to call repeatedly, e.g. again after the user grants the location permission. */
    @SuppressLint("MissingPermission")
    fun start() {
        if (!hasPermission()) {
            _state.value = LocationState.NoPermission
            return
        }
        if (started) return
        started = true
        val last = runCatching {
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull()
        _state.value = LocationState.Waiting(last?.toFix())
        val request = LocationRequestCompat.Builder(1_000L).setMinUpdateDistanceMeters(0f)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY).build()
        runCatching {
            LocationManagerCompat.requestLocationUpdates(manager, LocationManager.GPS_PROVIDER, request, listener, Looper.getMainLooper())
        }
    }

    fun stop() {
        if (started) LocationManagerCompat.removeUpdates(manager, listener)
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
    )
}
