package com.centimusic.files.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val showHidden: Boolean = false,
    val sort: SortOrder = SortOrder(),
    val viewMode: ViewMode = ViewMode.LIST,
    val confirmDelete: Boolean = true,
    val favourites: Set<String> = emptySet(),
    val recentSearches: List<String> = emptyList(),
)

/** Small, synchronous settings store backed by SharedPreferences and exposed as a StateFlow. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("centifiles", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun read() = Settings(
        theme = enumOr(sp.getString("theme", null), ThemeMode.SYSTEM),
        showHidden = sp.getBoolean("showHidden", false),
        sort = SortOrder(enumOr(sp.getString("sortField", null), SortField.NAME), sp.getBoolean("sortAsc", true)),
        viewMode = enumOr(sp.getString("viewMode", null), ViewMode.LIST),
        confirmDelete = sp.getBoolean("confirmDelete", true),
        favourites = sp.getStringSet("favourites", emptySet())?.toSet() ?: emptySet(),
        recentSearches = sp.getString("recentSearches", "")!!.split('\n').filter { it.isNotBlank() },
    )

    fun update(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        sp.edit()
            .putString("theme", s.theme.name)
            .putBoolean("showHidden", s.showHidden)
            .putString("sortField", s.sort.field.name)
            .putBoolean("sortAsc", s.sort.ascending)
            .putString("viewMode", s.viewMode.name)
            .putBoolean("confirmDelete", s.confirmDelete)
            .putStringSet("favourites", s.favourites)
            .putString("recentSearches", s.recentSearches.joinToString("\n"))
            .apply()
        _settings.value = s
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
