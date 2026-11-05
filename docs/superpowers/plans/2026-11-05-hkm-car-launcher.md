# HKM Car Launcher Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A dark, HKM-branded Android home-screen app for a 1280×720 Android 15 head unit, with a live online/offline UAE map as its main area.

**Architecture:** New `:launcher` application module in the existing Gradle build, package `com.hkm.carlauncher`, no code shared with `:app`. Small single-purpose units expose `StateFlow`s and plain callbacks to Compose screens; pure logic (dock matching, session choice, progress, map source, follow/bearing, file checks) lives in plain Kotlin classes with JVM unit tests. The map is MapLibre Native in an `AndroidView`, fed either a local PMTiles file or OpenFreeMap online tiles through one bundled dark style.

**Tech Stack:** Kotlin 2.1.20, Jetpack Compose (BOM 2025.02.00), MapLibre Native Android `org.maplibre.gl:android-sdk:13.7.1`, DataStore, Android `DownloadManager`, `MediaSessionManager`, `LocationManager`, JUnit 4, Robolectric + Compose UI test for screen tests, Planetiler (CI only) for the map file.

**Spec:** `docs/superpowers/specs/2026-11-05-hkm-car-launcher-design.md`

## Global Constraints

- Module `launcher/`, `namespace`/`applicationId` = `com.hkm.carlauncher`; `minSdk 26`, `compileSdk 35`, `targetSdk 35`; Java/JVM target 17; landscape only.
- No dependency on `:app`, and no Google Play services.
- Colours (from the approved mockup): background `#07090C`, card `#12161C`, raised `#181D25`, outline `#232A34`, text `#F2F5F8`, muted `#8A95A3`, accent `#4F86F7`, online/offline OK `#7EE2A8`.
- Layout at 1280×720 dp-equivalent: left column 300 dp; dock 104 dp tall; gaps and outer padding 12 dp; card corner radius 20 dp; dock and media touch targets ≥ 58 dp, play/pause 72 dp, dock items ≥ 72 dp.
- The map must show "© OpenStreetMap contributors".
- Online tiles: `https://tiles.openfreemap.org/planet`. Offline file URL: `https://github.com/syberdeath/CentiMusic/releases/download/uae-map/uae.pmtiles` and its checksum at `…/uae-map/uae.pmtiles.sha256`.
- Default map centre with no fix: Dubai, `25.2048, 55.2708`, zoom 15.
- This container has no Android SDK (dl.google.com is blocked). Gradle steps run in GitHub Actions: push the branch and read the "Android build" run for the commit. The workflow must therefore run on this branch (Task 1).
- Commit messages end with the session's attribution lines.

## Review Focus

1. **Media session with missing data** (no metadata, no artwork, duration unknown/`-1`, position past duration) — card shows what exists, progress is 0..1, never NaN or a crash. Test: Task 5 `progressFraction` cases.
2. **Two apps with the same dock label** (e.g. vendor "Music" and another "Music") — the vendor/system app wins deterministically; the launcher itself never matches. Test: Task 2 `prefersSystemAppOnLabelTie`, `neverMatchesSelf`.
3. **Interrupted or corrupted map download, then reboot** — an unverified or partial file is never used; the previous good file stays. Test: Task 7 `unverifiedFileIsNotInstalled`, `failedVerificationKeepsOldFile`.
4. **Car stopped (no bearing from GPS)** — marker keeps its last heading instead of snapping north. Test: Task 8 `keepsLastBearingWhenLocationHasNone`.
5. **Clock across midnight and 12/24 h switch** — date line changes at midnight; format follows the system setting without restart. Test: Task 4 `dateChangesAtMidnight`, `followsTwelveHourSetting`.

---

### Task 1: Module scaffold, theme, logo and CI

**Files:**
- Modify: `settings.gradle.kts` (add `include(":launcher")`)
- Modify: `gradle/libs.versions.toml` (add `maplibre = "13.7.1"`, `robolectric = "4.14.1"`, libraries `maplibre-android`, `robolectric`, `compose-ui-test-junit4`, `compose-ui-test-manifest`)
- Create: `launcher/build.gradle.kts`, `launcher/proguard-rules.pro`
- Create: `launcher/src/main/AndroidManifest.xml`
- Create: `launcher/src/main/java/com/hkm/carlauncher/MainActivity.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/LauncherApp.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/ui/theme/Theme.kt`
- Create: `launcher/src/main/res/drawable-nodpi/hkm_logo.png` (copy of `docs/launcher/hkm-logo.png`), `res/values/strings.xml`, `res/mipmap-*/ic_launcher` (logo on `#07090C`)
- Modify: `.github/workflows/android.yml`
- Test: `launcher/src/test/java/com/hkm/carlauncher/ThemeTest.kt`

**Interfaces:**
- Produces: `object LauncherColors { val Background; val Card; val Raised; val Outline; val Text; val Muted; val Accent; val Ok }` (Compose `Color`s), `@Composable fun LauncherTheme(content: @Composable () -> Unit)`; `class LauncherApp : Application` holding `lateinit var graph: AppGraph` (Task 10 fills `AppGraph`; for now an empty class in `LauncherApp.kt`).

- [ ] **Step 1: Write the failing test** — `ThemeTest.accentIsLogoBlue`: `assertEquals(Color(0xFF4F86F7), LauncherColors.Accent)` and `backgroundIsNearBlack`: `assertEquals(Color(0xFF07090C), LauncherColors.Background)`.
- [ ] **Step 2: Create the module.** `launcher/build.gradle.kts` mirrors `app/build.gradle.kts` (same plugins minus `ksp`, same CI signing block with alias `centimusic`, `versionCode = GITHUB_RUN_NUMBER ?: 1`), with `testOptions.unitTests.isIncludeAndroidResources = true`. Manifest: `MainActivity` with `android:screenOrientation="landscape"`, `launchMode="singleTask"`, `stateNotNeeded="true"`, `excludeFromRecents="true"`, intent filter `MAIN` + `HOME` + `DEFAULT` + `LAUNCHER`; permissions `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `QUERY_ALL_PACKAGES`. `MainActivity` uses `enableEdgeToEdge()` off (system bars stay vendor-drawn) and shows `LauncherTheme { Box(Modifier.fillMaxSize().background(LauncherColors.Background)) }`.
- [ ] **Step 3: CI.** In `android.yml`: add `"ccr-**"` to `push.branches`; build with `assembleDebug testDebugUnitTest` (already covers all modules); copy `launcher/build/outputs/apk/debug/launcher-debug.apk` to `HKM-Launcher.apk` and upload it beside `CentiMusic.apk`; add `launcher/build/reports/` and `launcher/build/test-results/` to the reports upload.
- [ ] **Step 4: Push and verify.** `git push`; the "Android build" run for the commit is green, `ThemeTest` appears in `launcher/build/test-results`, and `HKM-Launcher.apk` is an artifact (when signing secrets exist).
- [ ] **Step 5: Commit** — `feat(launcher): add HKM launcher module, theme and CI build`.

### Task 2: Dock resolution

**Files:**
- Create: `launcher/src/main/java/com/hkm/carlauncher/apps/InstalledApp.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/apps/DockResolver.kt`
- Test: `launcher/src/test/java/com/hkm/carlauncher/apps/DockResolverTest.kt`

**Interfaces:**
- Produces: `data class InstalledApp(val packageName: String, val activityName: String, val label: String, val isSystem: Boolean)`; `enum class DockEntry(val title: String) { BLUETOOTH("Bluetooth"), CARMATE("Carmate"), MUSIC("Music"), VIDEO("Video"), GOOGLE_PLAY("Google Play"), SETTINGS("Settings") }`; `sealed interface DockTarget { data class App(val app: InstalledApp) : DockTarget; data object SystemSettings : DockTarget; data object AllApps : DockTarget }`; `class DockResolver(private val selfPackage: String, private val pinned: Map<DockEntry, String> = emptyMap()) { fun resolve(entry: DockEntry, apps: List<InstalledApp>): DockTarget }`.

- [ ] **Step 1: Write the failing tests** (labels compared trimmed, case-insensitive):
  - `matchesExactLabel`: app label `" bluetooth "` → `BLUETOOTH` resolves to `App` of it.
  - `bluetoothFallsBackToBt`: only label `"BT Music"` present → `BLUETOOTH` resolves to it.
  - `googlePlayByPackage`: package `com.android.vending` label `"Play Store"` → `GOOGLE_PLAY` resolves to it.
  - `settingsPrefersVendorApp`: vendor (non-`com.android.settings`) app labelled `"Settings"` and `com.android.settings` labelled `"Settings"` → vendor app; with only `com.android.settings` → `App(com.android.settings)`; with none → `SystemSettings`.
  - `prefersSystemAppOnLabelTie`: two `"Music"` apps, `isSystem` true and false → the system one, regardless of list order.
  - `neverMatchesSelf`: app with `packageName == selfPackage` labelled `"Music"` is never returned.
  - `pinnedPackageWins`: `pinned = mapOf(MUSIC to "com.vendor.music")` → that package even if its label is `"Player"`.
  - `missingFallsBackToAllApps`: no `"Video"` → `AllApps`.
- [ ] **Step 2: Run to verify they fail** — CI run red on `DockResolverTest` (unresolved references).
- [ ] **Step 3: Implement `DockResolver.resolve`.** Order: pinned package → entry rule from the spec table (§6) → `AllApps` (or `SystemSettings` for `SETTINGS`). Ties: system app first, then alphabetical package name.
- [ ] **Step 4: Push; `DockResolverTest` passes.**
- [ ] **Step 5: Commit** — `feat(launcher): resolve fixed dock entries to installed apps`.

### Task 3: Installed apps and All apps screen

**Files:**
- Create: `launcher/src/main/java/com/hkm/carlauncher/apps/AppsRepository.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/apps/AppLauncher.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/apps/AllAppsScreen.kt`
- Test: `launcher/src/test/java/com/hkm/carlauncher/apps/AllAppsScreenTest.kt` (Robolectric)

**Interfaces:**
- Consumes: `InstalledApp`, `DockTarget` (Task 2).
- Produces: `class AppsRepository(context: Context, scope: CoroutineScope) { val apps: StateFlow<List<InstalledApp>>; fun iconFor(app: InstalledApp): Drawable }` — queries `ACTION_MAIN`/`CATEGORY_LAUNCHER`, excludes own package, sorts by label (case-insensitive), refreshes on `ACTION_PACKAGE_ADDED/REMOVED/CHANGED`. `interface AppOpener { fun launchPackage(packageName: String): Boolean }`; `class AppLauncher(context: Context) : AppOpener { fun launch(app: InstalledApp): Boolean; fun launch(target: DockTarget, onAllApps: () -> Unit) }` — returns false (and launches nothing) when the activity is gone. `@Composable fun AllAppsScreen(apps: List<InstalledApp>, icon: (InstalledApp) -> ImageBitmap, onOpen: (InstalledApp) -> Unit, onClose: () -> Unit)`.

- [ ] **Step 1: Write the failing test** — `AllAppsScreenTest.showsLabelsAndOpens`: given apps `"Carmate"`, `"Music"`, node with text `"Music"` exists; clicking it calls `onOpen` with that app; pressing the close control calls `onClose`.
- [ ] **Step 2: Run — fails (no `AllAppsScreen`).**
- [ ] **Step 3: Implement.** Grid: `LazyVerticalGrid(GridCells.Adaptive(140.dp))`, icon 64 dp, label below, dark background, top bar with title "All apps" and a 58 dp close button; system Back calls `onClose` via `BackHandler`.
- [ ] **Step 4: Push; test passes.**
- [ ] **Step 5: Commit** — `feat(launcher): installed apps list and All apps screen`.

### Task 4: Clock card

**Files:**
- Create: `launcher/src/main/java/com/hkm/carlauncher/clock/ClockState.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/clock/ClockCard.kt`
- Test: `launcher/src/test/java/com/hkm/carlauncher/clock/ClockStateTest.kt`

**Interfaces:**
- Produces: `data class ClockText(val time: String, val date: String)`; `fun formatClock(now: ZonedDateTime, is24h: Boolean, locale: Locale): ClockText`; `class ClockState(context: Context, scope: CoroutineScope, clock: () -> ZonedDateTime = { ZonedDateTime.now() }) { val text: StateFlow<ClockText> }` — re-emits on `ACTION_TIME_TICK`, `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED`, reading `DateFormat.is24HourFormat(context)` each time. `@Composable fun ClockCard(text: ClockText, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Write the failing tests** (`Locale.ENGLISH`):
  - `formats24h`: 2026-11-05 14:13 → `ClockText("14:13", "Thursday, 5 November 2026")`.
  - `followsTwelveHourSetting`: same instant, `is24h = false` → time `"2:13"` (no AM/PM inside the large digits).
  - `dateChangesAtMidnight`: 23:59 on 5 Nov → date `"Thursday, 5 November 2026"`; 00:00 on 6 Nov → `"Friday, 6 November 2026"`.
- [ ] **Step 2: Run — fails.**
- [ ] **Step 3: Implement** `formatClock` with `DateTimeFormatter` patterns `"HH:mm"` / `"h:mm"` and `"EEEE, d MMMM yyyy"`. `ClockCard`: time 78 sp light weight, date 17 sp muted, as in the mockup.
- [ ] **Step 4: Push; tests pass.**
- [ ] **Step 5: Commit** — `feat(launcher): clock card`.

### Task 5: Now playing

**Files:**
- Create: `launcher/src/main/java/com/hkm/carlauncher/media/NowPlaying.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/media/SessionPicker.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/media/NowPlayingRepository.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/media/LauncherNotificationListener.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/media/NowPlayingCard.kt`
- Modify: `launcher/src/main/AndroidManifest.xml` (listener service with `BIND_NOTIFICATION_LISTENER_SERVICE`)
- Test: `launcher/src/test/java/com/hkm/carlauncher/media/SessionPickerTest.kt`, `NowPlayingCardTest.kt` (Robolectric)

**Interfaces:**
- Produces:
  - `data class SessionInfo(val packageName: String, val isPlaying: Boolean, val lastActiveMillis: Long)`; `fun pickSession(sessions: List<SessionInfo>): SessionInfo?` — first playing session in the given (system) order; else the highest `lastActiveMillis`; empty → null.
  - `fun progressFraction(positionMs: Long, updateTimeMs: Long, speed: Float, isPlaying: Boolean, durationMs: Long, nowMs: Long): Float` — extrapolates `position + (now - updateTime) * speed` only while playing; returns `0f` when duration ≤ 0; clamped to `0f..1f`.
  - `sealed interface NowPlaying { data object NoAccess; data object Nothing; data class Playing(val sourceLabel: String, val packageName: String, val title: String, val artist: String, val artwork: Bitmap?, val isPlaying: Boolean, val progress: Float) }`
  - `class NowPlayingRepository(context: Context, scope: CoroutineScope) { val state: StateFlow<NowPlaying>; fun playPause(); fun next(); fun previous(); fun openSource(launcher: AppLauncher) }` — `NoAccess` when the listener isn't enabled (re-checked on `ON_RESUME`); listens to `addOnActiveSessionsChangedListener` and each controller's callback; ticks progress every second while playing. Title falls back to the source app label when metadata has no title.
  - `@Composable fun NowPlayingCard(state: NowPlaying, onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onOpenSource: () -> Unit, onGrantAccess: () -> Unit, onOpenMusic: () -> Unit, modifier: Modifier = Modifier)`.
- Consumes: `AppLauncher` (Task 3).

- [ ] **Step 1: Write the failing tests**
  - `SessionPickerTest`: `prefersPlaying` (playing B after paused A newer → B); `fallsBackToMostRecent` (none playing → max `lastActiveMillis`); `emptyIsNull`.
  - `progressFraction`: `(30_000, 0, 1f, false, 120_000, 99_999) == 0.25f`; playing extrapolates: `(30_000, 1_000, 1f, true, 120_000, 31_000) == 0.5f`; `durationUnknown` `(5_000, 0, 1f, true, -1, 6_000) == 0f`; `pastEndClamps` `(200_000, 0, 1f, false, 120_000, 0) == 1f`; no NaN for `speed = 0f`.
  - `NowPlayingCardTest`: `Playing(sourceLabel = "Bluetooth", title = "Desert Highway", artist = "", artwork = null, …)` shows `"NOW PLAYING · BLUETOOTH"` and `"Desert Highway"` without crashing on null artwork; `NoAccess` shows `"Allow access to show what's playing"` and its button calls `onGrantAccess`; `Nothing` shows `"Nothing playing"` and a button calling `onOpenMusic`.
- [ ] **Step 2: Run — fails.**
- [ ] **Step 3: Implement** the pure functions, then the repository and card (layout per mockup: label 12 sp accent letter-spaced, 96 dp artwork with note-icon placeholder, title 21 sp, artist 15 sp muted, 4 dp progress bar, buttons 58/72/58 dp). `onGrantAccess` opens `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.
- [ ] **Step 4: Push; tests pass.**
- [ ] **Step 5: Commit** — `feat(launcher): now playing card for any media session`.

### Task 6: Location

**Files:**
- Create: `launcher/src/main/java/com/hkm/carlauncher/location/LocationRepository.kt`
- Test: `launcher/src/test/java/com/hkm/carlauncher/location/LocationRepositoryTest.kt` (Robolectric `ShadowLocationManager`)

**Interfaces:**
- Produces: `data class Fix(val lat: Double, val lon: Double, val bearing: Float?, val speedMps: Float?, val timeMs: Long)`; `sealed interface LocationState { data object NoPermission; data class Waiting(val lastKnown: Fix?); data class Live(val fix: Fix) }`; `class LocationRepository(context: Context, scope: CoroutineScope) { val state: StateFlow<LocationState>; fun start(); fun stop() }` — GPS provider, 1 s / 0 m updates, `bearing` null unless `hasBearing()`; after permission is granted (`start()` called again) it begins updates.

- [ ] **Step 1: Write the failing tests** — `noPermissionState` (permission denied → `NoPermission`); `waitingUsesLastKnown` (granted, last known set, no update → `Waiting(lastKnown = that fix)`); `liveAfterUpdate` (simulated GPS location with bearing 90 → `Live` with `bearing = 90f`); `noBearingIsNull` (location without bearing → `bearing == null`).
- [ ] **Step 2: Run — fails.**
- [ ] **Step 3: Implement** with `LocationManagerCompat.requestLocationUpdates`.
- [ ] **Step 4: Push; tests pass.**
- [ ] **Step 5: Commit** — `feat(launcher): GPS location state`.

### Task 7: Offline map file store

**Files:**
- Create: `launcher/src/main/java/com/hkm/carlauncher/map/MapSource.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/map/OfflineMapStore.kt`
- Test: `launcher/src/test/java/com/hkm/carlauncher/map/MapSourceTest.kt`, `OfflineMapStoreTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface MapSource { data class Offline(val file: File) : MapSource; data object Online : MapSource; data object Unavailable : MapSource }`; `fun selectMapSource(installed: File?, online: Boolean): MapSource` — installed file wins; else `Online` if online; else `Unavailable`.
  - `sealed interface OfflineMapStatus { data object NotInstalled; data class Downloading(val percent: Int); data class Installed(val sizeBytes: Long, val dateMillis: Long); data class Failed(val message: String) }`
  - `class OfflineMapStore(private val dir: File, private val sha256Fetcher: suspend () -> String?, private val downloader: MapDownloader) { val status: StateFlow<OfflineMapStatus>; val installedFile: StateFlow<File?>; suspend fun download(); suspend fun import(input: InputStream, expectedSha256: String?); fun delete() }` — work file `uae.pmtiles.part`, final `uae.pmtiles`, marker `uae.pmtiles.ok` holding the verified SHA-256; `installedFile` is non-null only when both exist and match. On failure the `.part` file is deleted and any existing good file stays.
  - `interface MapDownloader { suspend fun fetch(url: String, target: File, onProgress: (Int) -> Unit) }` with production `DownloadManagerMapDownloader(context)` (destination in `getExternalFilesDir("maps")`, polls progress every 500 ms).
  - `fun sha256Hex(file: File): String`.
  - `const val UAE_MAP_URL = "https://github.com/syberdeath/CentiMusic/releases/download/uae-map/uae.pmtiles"` and `UAE_MAP_SHA_URL = "$UAE_MAP_URL.sha256"`.
  - For USB import with no checksum available (offline), `expectedSha256 = null` accepts the file only if it starts with the PMTiles magic bytes `"PMTiles"` and version byte `3`.

- [ ] **Step 1: Write the failing tests** (temp dirs, fake downloader writing given bytes):
  - `MapSourceTest`: file + offline → `Offline`; no file + online → `Online`; no file + offline → `Unavailable`.
  - `verifiedDownloadInstalls`: fake writes bytes whose SHA matches → `installedFile` = `uae.pmtiles`, status `Installed`.
  - `unverifiedFileIsNotInstalled`: `uae.pmtiles` exists but no `.ok` marker (simulated crash) → new store instance reports `installedFile == null`, `NotInstalled`.
  - `failedVerificationKeepsOldFile`: good file installed; second download with wrong SHA → status `Failed`, `installedFile` still the old file, no `.part` left.
  - `importWithoutChecksumChecksMagic`: bytes starting `"PMTiles"` + `0x03` accepted; `"hello"` rejected with `Failed`.
- [ ] **Step 2: Run — fails.**
- [ ] **Step 3: Implement.** Replace atomically: verify `.part`, then `renameTo` final, then write marker.
- [ ] **Step 4: Push; tests pass.**
- [ ] **Step 5: Commit** — `feat(launcher): verified offline map download and import`.

### Task 8: Map panel

**Files:**
- Create: `launcher/src/main/assets/map/style-dark.json`
- Create: `launcher/src/main/assets/map/fonts/Noto Sans Regular/{0-255,256-511,1536-1791,8192-8447}.pbf` and same for `Noto Sans Bold` (downloaded from `https://tiles.openfreemap.org/fonts/<stack>/<range>.pbf`; if that host is unreachable from the build environment, fetch them in CI with a script `launcher/scripts/fetch-fonts.sh` run before Gradle and keep them out of git)
- Create: `launcher/src/main/java/com/hkm/carlauncher/map/MapStyle.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/map/FollowState.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/map/Bearing.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/map/StreetName.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/map/MapPanel.kt`
- Modify: `launcher/build.gradle.kts` (add `libs.maplibre.android`)
- Test: `launcher/src/test/java/com/hkm/carlauncher/map/{MapStyleTest,FollowStateTest,BearingTest,StreetNameTest}.kt`

**Interfaces:**
- Consumes: `MapSource` (Task 7), `LocationState`/`Fix` (Task 6).
- Produces:
  - `fun styleJsonFor(template: String, source: MapSource): String` — replaces the placeholder `"{{TILES}}"` with `"pmtiles://file://<absolute path>"` (Offline) or `"https://tiles.openfreemap.org/planet"` (Online); glyphs always `"asset://map/fonts/{fontstack}/{range}.pbf"`. Style: OpenMapTiles schema layers only (water, landcover/landuse, buildings, roads by class, road labels, place labels), no sprite, text field `["coalesce", ["get","name:en"], ["get","name_en"], ["get","name"]]`, attribution `"© OpenStreetMap contributors"`, colours from Global Constraints (water `#0A1A2B`, land `#0D1117`, buildings `#121820`, minor roads `#1F2733`, major roads `#3D4A5C`, labels `#8A95A3`).
  - `class FollowState(private val resumeAfterMs: Long = 15_000) { fun onUserGesture(nowMs: Long); fun onRecenter(); fun isFollowing(nowMs: Long): Boolean }`.
  - `fun markerBearing(previous: Float, fix: Fix): Float` — `fix.bearing ?: previous`.
  - `data class RoadFeature(val name: String?, val roadClass: String?)`; `fun pickStreetName(features: List<RoadFeature>): String?` — first non-blank name, preferring classes in order `motorway, trunk, primary, secondary, tertiary, minor, service`.
  - `@Composable fun MapPanel(source: MapSource, location: LocationState, showHint: Boolean, onTap: () -> Unit, onAllowLocation: () -> Unit, onGetMap: () -> Unit, modifier: Modifier = Modifier)` — overlays per spec §3.1: street-name chip (top left; "Dubai" fallback text omitted when null), indicator chip "Offline map"/"Online map" in `#7EE2A8` (top right), 56 dp zoom +/−/re-centre buttons (right), hint "Tap map to open navigation" while `showHint`, banners: `NoPermission` → "Allow location" button; `Waiting` → "Waiting for GPS…"; `Unavailable` → "Map not available — connect to the internet once, or load the UAE map from USB" with a button calling `onGetMap`. Camera: zoom 15, follows `Live` fixes with marker bearing when `isFollowing`; single tap → `onTap`.

- [ ] **Step 1: Write the failing tests**
  - `MapStyleTest`: offline output contains `"pmtiles://file:///data/x/uae.pmtiles"` for `File("/data/x/uae.pmtiles")`; online contains `"https://tiles.openfreemap.org/planet"`; output has no `"{{TILES}}"`; parses as JSON (`org.json.JSONObject`); has `"© OpenStreetMap contributors"`.
  - `FollowStateTest`: following at start; gesture at t=0 → not following at t=14_999, following at t=15_000; `onRecenter` → following immediately.
  - `BearingTest.keepsLastBearingWhenLocationHasNone`: `markerBearing(62f, fix(bearing = null)) == 62f`; with bearing 90 → `90f`.
  - `StreetNameTest`: `[minor "Al Sufouh St", motorway "Sheikh Zayed Rd"]` → `"Sheikh Zayed Rd"`; blank/null names skipped; empty → null.
- [ ] **Step 2: Run — fails.**
- [ ] **Step 3: Implement** the pure functions and assets, then `MapPanel` (`AndroidView` with `MapView`, lifecycle forwarded through `LocalLifecycleOwner`; `MapLibre.getInstance(context)` once in `LauncherApp`; style reloaded when `source` changes; car marker via MapLibre `LocationComponent` in GPS render mode, accent colour, fed by `forceLocationUpdate` from `LocationRepository` fixes with `markerBearing` applied). Street name: `queryRenderedFeatures` at the marker's screen point with road layers, mapped to `RoadFeature`, through `pickStreetName`.
- [ ] **Step 4: Push; tests pass and the APK builds.** If MapLibre 13.7.1 rejects `pmtiles://file://` at runtime on the unit, record it and switch the offline format to MBTiles served by a localhost server (spec §4 fallback) in a follow-up task.
- [ ] **Step 5: Commit** — `feat(launcher): dark MapLibre map panel with follow, street name and states`.

### Task 9: Settings and navigation launching

**Files:**
- Create: `launcher/src/main/java/com/hkm/carlauncher/settings/LauncherSettings.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/settings/NavigationLauncher.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/settings/SettingsScreen.kt`
- Modify: `launcher/build.gradle.kts` (add `libs.datastore.preferences`)
- Test: `launcher/src/test/java/com/hkm/carlauncher/settings/NavigationLauncherTest.kt`, `SettingsScreenTest.kt` (Robolectric)

**Interfaces:**
- Consumes: `InstalledApp`, `AppOpener` (Tasks 2–3), `OfflineMapStore`/`OfflineMapStatus` (Task 7).
- Produces:
  - `class LauncherSettings(context: Context) { val navigationPackage: Flow<String?>; suspend fun setNavigationPackage(pkg: String); val launchCount: Flow<Int>; suspend fun incrementLaunchCount(); val mapOfferShown: Flow<Boolean>; suspend fun setMapOfferShown() }` (DataStore file `launcher_settings`).
  - `fun defaultNavigationPackage(apps: List<InstalledApp>): String?` — `com.google.android.apps.maps` if installed, else the app labelled `"Carmate"`, else null.
  - `class NavigationLauncher(settings: LauncherSettings, opener: AppOpener) { suspend fun open(apps: List<InstalledApp>, onNeedsChoice: () -> Unit) }` — saved package → default → `onNeedsChoice()`; a saved package that no longer launches also calls `onNeedsChoice()`.
  - `@Composable fun SettingsScreen(apps: List<InstalledApp>, navigationPackage: String?, mapStatus: OfflineMapStatus, hasLocation: Boolean, hasMediaAccess: Boolean, versionName: String, onPickNavigation: (String) -> Unit, onDownloadMap: () -> Unit, onImportMap: () -> Unit, onDeleteMap: () -> Unit, onGrantLocation: () -> Unit, onGrantMediaAccess: () -> Unit, onSetHome: () -> Unit, onClose: () -> Unit)` — sections exactly as spec §3.3; Set as home opens `Settings.ACTION_HOME_SETTINGS`; Import uses `ActivityResultContracts.OpenDocument` (`*/*`) wired in `MainActivity`.

- [ ] **Step 1: Write the failing tests**
  - `defaultNavigationPackage`: Google Maps present → its package; only Carmate → Carmate's package; neither → null.
  - `NavigationLauncherTest.savedMissingAsksForChoice`: saved `"gone.app"` with fake `AppOpener` returning false → `onNeedsChoice` called once.
  - `SettingsScreenTest`: `Downloading(42)` shows `"Downloading 42%"`; `Installed(80_000_000, …)` shows `"80 MB"` and a Delete button calling `onDeleteMap`; tapping app `"Carmate"` in the navigation list calls `onPickNavigation` with its package.
- [ ] **Step 2: Run — fails.**
- [ ] **Step 3: Implement.** Tests fake `AppOpener` (Task 3).
- [ ] **Step 4: Push; tests pass.**
- [ ] **Step 5: Commit** — `feat(launcher): settings screen and navigation app launching`.

### Task 10: Home screen assembly

**Files:**
- Create: `launcher/src/main/java/com/hkm/carlauncher/AppGraph.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/home/HomeScreen.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/home/Dock.kt`
- Create: `launcher/src/main/java/com/hkm/carlauncher/home/LogoCard.kt`
- Modify: `launcher/src/main/java/com/hkm/carlauncher/MainActivity.kt`, `LauncherApp.kt`
- Test: `launcher/src/test/java/com/hkm/carlauncher/home/HomeScreenTest.kt` (Robolectric, `@Config(qualifiers = "w1280dp-h632dp-land")`)

**Interfaces:**
- Consumes: everything above.
- Produces: `class AppGraph(context: Context, scope: CoroutineScope)` constructing one instance each of `AppsRepository`, `AppLauncher`, `DockResolver(selfPackage)`, `ClockState`, `NowPlayingRepository`, `LocationRepository`, `OfflineMapStore`, `LauncherSettings`, `NavigationLauncher`, plus `val online: StateFlow<Boolean>` from `ConnectivityManager` and `val mapSource: StateFlow<MapSource>` combining `installedFile` and `online` via `selectMapSource`. `@Composable fun HomeScreen(clock: ClockText, nowPlaying: NowPlaying, mapSource: MapSource, location: LocationState, showMapHint: Boolean, dock: List<Pair<DockEntry, DockTarget>>, callbacks: HomeCallbacks)` with `data class HomeCallbacks(val onLogoLongPress: () -> Unit, val onDock: (DockTarget) -> Unit, val onAllApps: () -> Unit, val onMapTap: () -> Unit, val onAllowLocation: () -> Unit, val onGetMap: () -> Unit, val onPlayPause: () -> Unit, val onNext: () -> Unit, val onPrevious: () -> Unit, val onOpenSource: () -> Unit, val onGrantMediaAccess: () -> Unit, val onOpenMusic: () -> Unit)`.

- [ ] **Step 1: Write the failing tests** (`HomeScreenTest`, fake states, `MapPanel` replaced by a placeholder through a `mapContent` slot parameter so Robolectric needn't render MapLibre):
  - `rendersAllRegions`: texts `"HKM"`, `"CAR LAUNCHER"`, the clock time, `"Bluetooth"`, `"Carmate"`, `"Music"`, `"Video"`, `"Google Play"`, `"Settings"`, `"All apps"` all displayed.
  - `dockClickPassesTarget`: clicking `"Video"` resolved to `AllApps` calls `onDock(AllApps)`.
  - `logoLongPressOpensSettings`: long-press on the logo card calls `onLogoLongPress`; a single tap does not.
- [ ] **Step 2: Run — fails.**
- [ ] **Step 3: Implement.** `HomeScreen` gains `mapContent: @Composable (Modifier) -> Unit` as its last parameter (production passes `MapPanel`). `MainActivity`: in-activity navigation state `Screen { HOME, ALL_APPS, SETTINGS }`; `onNewIntent` with `CATEGORY_HOME` resets to `HOME`; requests location permission on first start; offers the map download once when `online && installedFile == null && !mapOfferShown` (dialog: "Download the UAE offline map (about N MB)?" with Download / Not now; size read from the release asset `Content-Length`, omitted if unknown); increments launch count each cold start; map hint shown while `launchCount <= 3`; dock built from `DockEntry.entries.map { it to resolver.resolve(it, apps) }`; dock icons are the launcher's own accent-coloured vector drawables named `ic_dock_bluetooth`, `ic_dock_carmate`, `ic_dock_music`, `ic_dock_video`, `ic_dock_play`, `ic_dock_settings`, `ic_dock_apps` (shapes as in the mockup).
- [ ] **Step 4: Push; tests pass; download `HKM-Launcher.apk` artifact.**
- [ ] **Step 5: Commit** — `feat(launcher): assemble home screen`.

### Task 11: UAE map build workflow

**Files:**
- Create: `.github/workflows/uae-map.yml`
- Create: `launcher/README.md`

**Interfaces:**
- Produces: release `uae-map` with assets `uae.pmtiles` and `uae.pmtiles.sha256` (single line: 64 lowercase hex chars), at the URLs in Global Constraints.

- [ ] **Step 1: Write the workflow.** `workflow_dispatch` only; `permissions: contents: write`; Java 21; download Planetiler `0.8.4` jar from its GitHub release; run `java -Xmx6g -jar planetiler.jar --download --area=gcc-states --bounds=51.4,22.5,56.5,26.3 --maxzoom=14 --output=uae.pmtiles`; `sha256sum uae.pmtiles | cut -d' ' -f1 > uae.pmtiles.sha256`; `gh release view uae-map || gh release create uae-map --title "UAE offline map" --notes "Built from OpenStreetMap (© OpenStreetMap contributors, ODbL) with Planetiler."`; `gh release upload uae-map uae.pmtiles uae.pmtiles.sha256 --clobber`; print the file size.
- [ ] **Step 2: Write `launcher/README.md`:** what it is, install (copy `HKM-Launcher.apk` from the Actions run to USB, install, press Home → choose HKM Launcher → Always), first-run grants (location, notification access), map download / USB import (file from the `uae-map` release), how to pin vendor package names (`DockResolver` `pinned` map), the manual on-unit checklist from spec §8, attribution.
- [ ] **Step 3: Run the workflow** (`workflow_dispatch` via the GitHub Actions tool) and verify both release assets exist and the `.sha256` content matches the file. Record the size in the README and in the spec's §4 "Expected size".
- [ ] **Step 4: Commit** — `feat(launcher): UAE offline map build workflow and README`.

### Task 12: Whole-branch check

- [ ] **Step 1:** CI green on the final commit; `HKM-Launcher.apk` artifact present.
- [ ] **Step 2:** Walk the spec section by section and tick each requirement against code; list anything only verifiable on the unit (vendor media sessions, dock label matches, PMTiles loading, GPS) in the README's checklist.
- [ ] **Step 3:** Commit any fixes — `chore(launcher): final review fixes`.
