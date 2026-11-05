package com.hkm.carlauncher.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore(name = "launcher_settings")

/** The launcher's few stored choices. */
class LauncherSettings(context: Context) {
    private val store = context.applicationContext.store

    val navigationPackage: Flow<String?> = store.data.map { it[NAV] }
    val launchCount: Flow<Int> = store.data.map { it[LAUNCHES] ?: 0 }
    val mapOfferShown: Flow<Boolean> = store.data.map { it[MAP_OFFER] ?: false }

    suspend fun setNavigationPackage(pkg: String) { store.edit { it[NAV] = pkg } }
    suspend fun incrementLaunchCount() { store.edit { it[LAUNCHES] = (it[LAUNCHES] ?: 0) + 1 } }
    suspend fun setMapOfferShown() { store.edit { it[MAP_OFFER] = true } }

    private companion object {
        val NAV = stringPreferencesKey("navigation_package")
        val LAUNCHES = intPreferencesKey("launch_count")
        val MAP_OFFER = booleanPreferencesKey("map_offer_shown")
    }
}
