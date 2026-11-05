package com.hkm.carlauncher.location

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class LocationRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(LocationManager::class.java)

    private fun grant() = shadowOf(context as android.app.Application)
        .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun location(bearing: Float?, provider: String = LocationManager.GPS_PROVIDER) = Location(provider).apply {
        latitude = 25.2; longitude = 55.27; time = 1_000
        if (bearing != null) this.bearing = bearing
    }

    @Test
    fun noPermissionState() {
        val repo = LocationRepository(context)
        repo.start()
        assertEquals(LocationState.NoPermission, repo.state.value)
    }

    @Test
    fun waitingUsesLastKnown() {
        grant()
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(manager).setLastKnownLocation(LocationManager.GPS_PROVIDER, location(10f))
        val repo = LocationRepository(context)
        repo.start()
        val state = repo.state.value as LocationState.Waiting
        assertEquals(25.2, state.lastKnown!!.lat, 1e-9)
    }

    @Test
    fun liveAfterUpdate() {
        grant()
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val repo = LocationRepository(context)
        repo.start()
        shadowOf(manager).simulateLocation(location(90f))
        shadowOf(Looper.getMainLooper()).idle()
        val fix = (repo.state.value as LocationState.Live).fix
        assertEquals(90f, fix.bearing!!, 0f)
    }

    @Test
    fun noBearingIsNull() {
        grant()
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val repo = LocationRepository(context)
        repo.start()
        shadowOf(manager).simulateLocation(location(null))
        shadowOf(Looper.getMainLooper()).idle()
        assertNull((repo.state.value as LocationState.Live).fix.bearing)
    }

    @Test
    fun networkFixUsedWhenGpsHasNone() {
        grant()
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        val repo = LocationRepository(context)
        repo.start()
        shadowOf(manager).simulateLocation(location(null, LocationManager.NETWORK_PROVIDER))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("network", (repo.state.value as LocationState.Live).fix.provider)
    }

    @Test
    fun diagnosticsListEnabledProviders() {
        grant()
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        val repo = LocationRepository(context)
        repo.start()
        val providers = repo.diagnostics.value.providers.orEmpty()
        assertEquals(true, "gps" in providers && "network" in providers)
    }
}
