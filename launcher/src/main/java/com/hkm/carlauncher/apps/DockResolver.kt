package com.hkm.carlauncher.apps

/** The six fixed dock shortcuts, in display order. */
enum class DockEntry(val title: String) {
    BLUETOOTH("Bluetooth"),
    CARMATE("Carmate"),
    MUSIC("Music"),
    VIDEO("Video"),
    GOOGLE_PLAY("Google Play"),
    SETTINGS("Settings"),
}

/** What a dock button opens. */
sealed interface DockTarget {
    data class App(val app: InstalledApp) : DockTarget
    data object SystemSettings : DockTarget
    data object AllApps : DockTarget
}

/**
 * Maps each dock entry to an installed app. The vendor apps' package names aren't known in
 * advance, so entries are matched by label; [pinned] overrides that with exact package names
 * once they are known.
 */
class DockResolver(
    private val selfPackage: String,
    private val pinned: Map<DockEntry, String> = emptyMap(),
) {
    fun resolve(entry: DockEntry, apps: List<InstalledApp>): DockTarget {
        val candidates = apps.filter { it.packageName != selfPackage }
        pinned[entry]?.let { pkg ->
            candidates.firstOrNull { it.packageName == pkg }?.let { return DockTarget.App(it) }
        }
        val match = when (entry) {
            DockEntry.BLUETOOTH ->
                best(candidates.filter { it.normalizedLabel() == "bluetooth" })
                    ?: best(candidates.filter { BT_WORD.containsMatchIn(it.normalizedLabel()) })
            DockEntry.GOOGLE_PLAY -> best(candidates.filter { it.packageName == PLAY_STORE })
            DockEntry.SETTINGS -> {
                val labelled = candidates.filter { it.normalizedLabel() == "settings" }
                best(labelled.filter { it.packageName != ANDROID_SETTINGS }) ?: best(labelled)
            }
            DockEntry.CARMATE, DockEntry.MUSIC, DockEntry.VIDEO ->
                best(candidates.filter { it.normalizedLabel() == entry.title.lowercase() })
        }
        return when {
            match != null -> DockTarget.App(match)
            entry == DockEntry.SETTINGS -> DockTarget.SystemSettings
            else -> DockTarget.AllApps
        }
    }

    /** Deterministic tie-break: system (vendor) apps first, then package name. */
    private fun best(apps: List<InstalledApp>): InstalledApp? =
        apps.minWithOrNull(compareBy<InstalledApp>({ !it.isSystem }, { it.packageName }))

    private fun InstalledApp.normalizedLabel() = label.trim().lowercase()

    private companion object {
        const val PLAY_STORE = "com.android.vending"
        const val ANDROID_SETTINGS = "com.android.settings"
        val BT_WORD = Regex("\\bbt\\b")
    }
}
