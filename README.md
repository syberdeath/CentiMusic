# CentiMusic

A minimalist, fully offline Android music player inspired by Nothing's design language: monochrome surfaces, dot-matrix type, geometric
artwork and a single red accent. It plays the music already on your phone. There are no accounts,
no streaming and no network permission.

> CentiMusic is an independent project. It is not affiliated with, endorsed by or a product of Nothing Technology Ltd.,
> and it does not use Nothing's logos.

## Features

| Area | What it does |
| --- | --- |
| **Delete** | "Delete from device" in every song's menu. Android 11+ shows the system confirmation; Android 10 confirms in-app then may ask the system; Android 8–9 asks for the legacy write permission only when you delete. Deleted songs leave the queue, playlists, likes and history. Imported files are deleted through their provider when it allows it, otherwise removed from the library. |
| **Library** | Scans MediaStore for local audio (title, artist, album, duration, track no., year, folder, artwork). Rescans automatically when files change, and on demand. Duplicates are removed by content URI. |
| **Import** | Pick audio files from any folder or document provider with the system file picker (persisted read access). Also opens audio from "Open with" in file managers. |
| **Home** | Dot-matrix hero, Shuffle all, Recently Played, Recently Added and Albums shelves, built from your real library. Empty, loading and permission states. |
| **Library screen** | Liked Songs, Playlists, Albums, Artists, All Songs, Folders, Recently Played, Recently Added — with live counts. Album/artist/folder detail pages with Play, Shuffle and Add-to-playlist. Song sorting (title, artist, album, date added, duration) and album sorting. |
| **Search** | Accent- and case-insensitive search over songs, artists, albums and playlists, recent searches with per-item delete and Clear, and a "Your artists" shortcut row drawn from your own library. |
| **Playlists** | Create, rename, delete, add/remove songs, drag to reorder, play, shuffle, song count and total duration. Stored in Room. |
| **Now Playing** | Large artwork with animated transitions, dot-matrix title, like, seek bar with scrubbing, shuffle, previous, play/pause, next, repeat (off/all/one), current audio output, queue. |
| **Queue** | Mirrors the real ExoPlayer playlist in play order: now playing, up next, tap to jump, remove, drag to reorder (when shuffle is off), clear. |
| **Playback** | Media3 ExoPlayer inside a `MediaSessionService`: background playback, media notification, lock-screen and Bluetooth/headset controls, audio focus, pause on "becoming noisy", skip silence, missing-file skipping, and queue/position restore (including Android's media resumption). |
| **Launch** | Animated splash: the dot equalizer from the app icon bounces, the CentiMusic wordmark types out in dot-matrix, then "Made by Vincent with ♥" fades in. It continues seamlessly from the system launch screen (AndroidX SplashScreen), plays once per launch, and can be skipped with a tap. |
| **Visualizer** | Full-screen black dot-matrix waveform driven by the music's measured loudness, a red dot that tracks the real playback position (tap/drag to seek), track title/artist/artwork and Media3-synced controls. See below. |
| **Settings** | Theme (System/Light/Dark, applied instantly), remember shuffle & repeat, resume last queue, pause on disconnect, skip silence, ignore short audio, rescan, import, clear search history, privacy, about, version. Stored in DataStore. |

## Tech stack

Kotlin · Jetpack Compose (Material 3 for dialogs/sheets only) · Media3 ExoPlayer + MediaSession ·
Room · DataStore · Navigation Compose · [Reorderable](https://github.com/Calvin-LL/Reorderable) for
drag-and-drop · Gradle Kotlin DSL with a version catalog.

- `minSdk` 26 (Android 8.0), `compileSdk`/`targetSdk` 35
- AGP 8.9.1, Kotlin 2.1.20, Gradle 8.14.3 (wrapper included)

## Project structure

```
app/src/main/java/com/centimusic/player/
├── CentiMusicApp.kt, AppContainer.kt, MainActivity.kt   app layer, manual DI
├── data/
│   ├── media/        Song/Album/Artist/Folder models, MediaStore scanner, artwork loader, MusicRepository
│   ├── database/     Room entities, DAOs, AppDatabase
│   ├── preferences/  DataStore-backed settings
│   └── repository/   Playlists, favourites, play & search history
├── playback/         PlaybackService (ExoPlayer + MediaSession), PlayerConnection (MediaController → StateFlow), output monitor
├── navigation/       Routes, Navigator, root scaffold (NavHost, bottom bar, mini-player, permissions, file picker)
└── ui/
    ├── theme/        Colors, typography (Doto dot-matrix + sans), spacing, custom stroke icon set
    ├── components/   Artwork + generated placeholders, grain texture, rows, mini-player, bottom nav, sheets, dialogs
    └── home/ player/ library/ search/ playlists/ queue/ settings/
```

UI state flows one way: repositories expose `Flow`s → ViewModels combine them into immutable UI
state → Compose renders it. Every playback command goes through the `MediaController`, so the
app, notification, lock screen and headset buttons always agree.

## Building

Requirements: JDK 17+ and the Android SDK (platform 35). Android Studio Ladybug or newer works out of the box.

```bash
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew installDebug           # install on a connected device
```

If the SDK isn't auto-detected, create `local.properties` with `sdk.dir=/path/to/Android/sdk`.

### Signing (install updates without uninstalling)

Android only installs an APK over an existing app when both are signed with the same key. CI signs
every build with one permanent key stored in two repository secrets (Settings → Secrets and variables
→ Actions):

| Secret | Value |
| --- | --- |
| `CENTIMUSIC_KEYSTORE` | The PKCS12 keystore, base64-encoded (alias `centimusic`) |
| `CENTIMUSIC_KEY_PASSWORD` | Its password (store and key) |

Each CI run also gets a higher `versionCode` (the run number), so new APKs install as updates and keep
your library, playlists and settings. Keep a private backup of the keystore: losing it means users
must uninstall once to switch to a new key. Builds without the secrets (local or pull requests) use the
machine's own debug key.

### CI

`.github/workflows/android.yml` builds the debug APK and runs the unit tests on every push. The APK
is attached to each run as the **centimusic-debug-apk** artifact.

## Visualizer and the lock screen

### How the waveform is driven

Android's `android.media.audiofx.Visualizer` requires the `RECORD_AUDIO` (microphone) permission,
so CentiMusic doesn't use it. Instead ExoPlayer is built with `MeteringRenderersFactory`, which wraps
the real audio sink in `LevelMeteringAudioSink`, a pass-through `ForwardingAudioSink`:

- Decoded PCM (16/24/32-bit integer or float) is measured in 25 ms slices; each slice's RMS level is
  stored on a dB scale with its presentation timestamp (`AudioLevelMeter`).
- The sink also reports the timestamp currently being output, so the UI shows only levels that are
  already audible. The waveform follows what you hear rather than audio that is still buffered.
- Each column of the waveform is one measured slice; smoothing is limited to a fast-attack/slow-release
  filter. Nothing is randomised. For formats that can't be measured (e.g. passthrough), the screen
  says "Live levels aren't available for this audio format" instead of faking motion.
- Metering only runs while the visualizer is on screen; the frame loop runs only while music is playing
  and the app is visible. Pausing freezes the waveform; a new track clears it. No extra permissions.

### What appears where

| Where | What you get |
| --- | --- |
| **System lock screen and notification** | Android's standard media controls from the Media3 `MediaSessionService`: title, artist, album, artwork (loaded by `ArtworkBitmapLoader`, including embedded art of imported files), play/pause, previous, next and the seek bar. Android draws these itself. Apps cannot put a custom or animated view inside the system lock-screen media controls, so the visualizer is **not** shown there. |
| **Inside CentiMusic** | The animated visualizer (Now Playing → waveform button). |
| **Optional: Settings → "Visualizer on lock screen"** (off by default) | While the visualizer is open, CentiMusic's window is allowed to show over the keyguard (`setShowWhenLocked`). If the phone locks while you're on that screen, waking it shows the visualizer without unlocking, and the controls still work. Leaving it while locked asks you to unlock first, so the rest of the app stays protected. The app never launches itself over the lock screen (Android restricts background activity starts and full-screen intents), so this only applies when the visualizer was already open. |

## Permissions

| Permission | Why |
| --- | --- |
| `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (Android 8–12, `maxSdkVersion 32`) | Read the device's audio through MediaStore. No broad storage access is requested. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playing in the background. |
| `POST_NOTIFICATIONS` (Android 13+) | Show the playback notification. |
| `WAKE_LOCK` | Keep the CPU awake while playing with the screen off. |
| `WRITE_EXTERNAL_STORAGE` (Android 8–9 only, `maxSdkVersion 28`) | Requested only when you delete a song; newer versions use the system delete dialog instead. |

If audio access is denied the app still works with files picked through the system file picker.

## Design

Dark: background `#080808`, text `#FFFFFF` / `#A0A0A0`, dividers `#252525`.
Light: background `#FAFAF8`, text `#111111` / `#686868`, dividers `#E5E5E2`.
Accent `#FF3030` in both. Large headings use **Doto** (dot-matrix, SIL OFL 1.1 — see
`licenses/Doto-OFL.txt`); metadata uses the system sans-serif. Icons are custom vectors drawn on a
24-unit grid with one 1.6 stroke weight.

### App icon

The CentiMusic mark is an original dot-matrix equalizer: five columns of white dots on black, with the
top dot of the tallest column in red. It is used for the launcher icon (with a monochrome layer for
themed icons), the launch screen, the animated splash and the notification.

### Controls

The main play/pause button is a red (`#FF3030`) disc with a white glyph on Now Playing, the visualizer
and the mini-player; other controls are white (black in the light theme), and active shuffle/repeat
turn red. On Now Playing the artwork is circular and sits at the centre of the pulse ring.

### Typography

Dot-matrix (Doto) is used for screen and section headings, track titles, the bottom-navigation labels,
buttons, time and duration labels and small section kickers. Song, artist and album lines in lists stay
in the sans-serif face for readability.

## Visualizer and the lock screen

### How the waveform is driven

Android's `android.media.audiofx.Visualizer` requires the `RECORD_AUDIO` (microphone) permission,
so CentiMusic doesn't use it. Instead ExoPlayer is built with `MeteringRenderersFactory`, which wraps
the real audio sink in `LevelMeteringAudioSink`, a pass-through `ForwardingAudioSink`:

- Decoded PCM (16/24/32-bit integer or float) is measured in 25 ms slices; each slice's RMS level is
  stored on a dB scale with its presentation timestamp (`AudioLevelMeter`).
- The sink also reports the timestamp currently being output, so the UI shows only levels that are
  already audible. The waveform follows what you hear rather than audio that is still buffered.
- Each column of the waveform is one measured slice; smoothing is limited to a fast-attack/slow-release
  filter. Nothing is randomised. For formats that can't be measured (e.g. passthrough), the screen
  says "Live levels aren't available for this audio format" instead of faking motion.
- Metering only runs while the visualizer is on screen; the frame loop runs only while music is playing
  and the app is visible. Pausing freezes the waveform; a new track clears it. No extra permissions.

### What appears where

| Where | What you get |
| --- | --- |
| **System lock screen and notification** | Android's standard media controls from the Media3 `MediaSessionService`: title, artist, album, artwork (loaded by `ArtworkBitmapLoader`, including embedded art of imported files), play/pause, previous, next and the seek bar. Android draws these itself. Apps cannot put a custom or animated view inside the system lock-screen media controls, so the visualizer is **not** shown there. |
| **Inside CentiMusic** | The animated visualizer (Now Playing → waveform button). |
| **Optional: Settings → "Visualizer on lock screen"** (off by default) | While the visualizer is open, CentiMusic's window is allowed to show over the keyguard (`setShowWhenLocked`). If the phone locks while you're on that screen, waking it shows the visualizer without unlocking, and the controls still work. Leaving it while locked asks you to unlock first, so the rest of the app stays protected. The app never launches itself over the lock screen (Android restricts background activity starts and full-screen intents), so this only applies when the visualizer was already open. |

## Permissions

| Permission | Why |
| --- | --- |
| `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (Android 8–12, `maxSdkVersion 32`) | Read the device's audio through MediaStore. No broad storage access is requested. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playing in the background. |
| `POST_NOTIFICATIONS` (Android 13+) | Show the playback notification. |
| `WAKE_LOCK` | Keep the CPU awake while playing with the screen off. |
| `WRITE_EXTERNAL_STORAGE` (Android 8–9 only, `maxSdkVersion 28`) | Requested only when you delete a song; newer versions use the system delete dialog instead. |

If audio access is denied the app still works with files picked through the system file picker.

## Design

Dark: background `#080808`, text `#FFFFFF` / `#A0A0A0`, dividers `#252525`.
Light: background `#FAFAF8`, text `#111111` / `#686868`, dividers `#E5E5E2`.
Accent `#FF3030` in both. Large headings use **Doto** (dot-matrix, SIL OFL 1.1 — see
`licenses/Doto-OFL.txt`); metadata uses the system sans-serif. Icons are custom vectors drawn on a
24-unit grid with one 1.6 stroke weight.

### App icon

The CentiMusic mark is an original dot-matrix equalizer: five columns of white dots rising from a
common baseline on black, with the top dot of the tallest column in red. It ships as an adaptive
vector icon with a monochrome layer for Android 13 themed icons, and the same mark is used for the
launch screen and the notification icon.
