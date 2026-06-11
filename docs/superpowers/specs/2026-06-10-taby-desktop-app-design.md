# Taby Desktop App — Design Spec

**Date:** 2026-06-10
**Status:** Approved

---

## Goal

A standalone Compose Desktop app that lets the user browse all Taby animations in a scrollable grid, preview them (thumbnail at rest, video plays on hover), and send one to the connected device with a single click.

---

## Gradle Module

A new `desktop` module is added to the existing `tabyproto` multi-module project.

```
tabyproto/
├── api/          # existing Kotlin library
├── app/          # existing CLI demo
└── desktop/      # new Compose Desktop app
    ├── build.gradle.kts
    └── src/main/kotlin/cc/dvitski/tabyproto/desktop/
        ├── Main.kt
        ├── App.kt
        ├── AnimationGrid.kt
        ├── AnimationCell.kt
        └── ThumbnailCache.kt
```

`settings.gradle.kts` gains `include("desktop")`.

Resources (`anim/*.mp4`, `icons/*`) currently in `app/src/main/resources/` move to `desktop/src/main/resources/` since the desktop module is the sole consumer. The `app` CLI demo does not use them.

---

## Dependencies

```kotlin
// desktop/build.gradle.kts
plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":api"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Video playback (Windows Media Foundation, zero system deps)
    implementation("io.github.kdroidfilter:composemediaplayer:0.10.1")

    // Thumbnail extraction (first frame from MP4)
    implementation("org.bytedeco:javacv:1.5.11")
    implementation("org.bytedeco:ffmpeg:6.1.1-1.5.11:windows-x86_64")
}
```

Only the Windows x64 FFmpeg native classifier is included to keep distribution size reasonable.

---

## Architecture

### State

```
AppState
├── connectionState: ConnectionState   // Connecting | Connected(session) | Failed(msg)
├── query: String                      // live filter text
└── lastSent: Animation?               // shown in status bar

ConnectionState
  = Connecting
  | Connected(session: TabySession, transport: TabyTransport)
  | Failed(message: String)
```

### Component tree

```
App
├── TopBar
│   ├── FilterTextField  (updates query)
│   └── StatusBar        (connection dot + transport label + last-sent)
└── AnimationGrid
    └── AnimationCell × N  (one per Animation matching query)
        ├── ThumbnailImage  (static, shown at rest)
        ├── VideoPlayer     (ComposeMediaPlayer, shown on hover)
        └── Label           (animation id, truncated)
```

### ThumbnailCache

`ThumbnailCache` extracts the first frame of each MP4 at first access using `FFmpegFrameGrabber`. Extraction runs on `Dispatchers.IO` and results are stored as `ImageBitmap` in a `MutableMap`. The grid shows a loading spinner for cells whose thumbnail hasn't been fetched yet.

### AnimationCell behaviour

| State | Visual |
|---|---|
| Idle | Thumbnail + label, subtle border |
| Hovered | ComposeMediaPlayer (looped, muted) + highlighted border; thumbnail hidden |
| Sending | Spinner overlay for the duration of the coroutine |
| Just sent | Brief green flash, updates `lastSent` in AppState |

Hover is detected via `Modifier.onPointerEvent`. The `ComposeMediaPlayer` instance is created on hover-enter and disposed on hover-exit, so at most one video plays at a time.

### Connection lifecycle

`App` launches a coroutine on startup that calls `Taby.connect()`. On success it transitions to `Connected`. On failure it shows an error with a **Reconnect** button that re-runs the coroutine. The `TabySession` is closed via `DisposableEffect` when the composable leaves composition.

### Click → send

`AnimationCell` receives an `onSend: suspend (Animation) -> Unit` lambda. When clicked it launches a coroutine, calls `session.play(animation)`, and updates `AppState.lastSent` on success. Errors are shown as a brief toast / snackbar.

---

## Error handling

- **Connection failure on startup** — `Failed` state shows message + Reconnect button.
- **Send failure** — snackbar with error message; device remains connected.
- **Thumbnail extraction failure** — cell shows animation name text instead (graceful fallback).
- **No device found** — `Taby.connect()` throws; caught and shown in `Failed` state.

---

## Window

- Initial size: 900 × 700 px, resizable.
- Title: `Taby Controller`.
- App icon: `icons/icon.ico` (already in resources).
- Grid column count: derived from window width (`(windowWidth / 160.dp).coerceAtLeast(3)`).

---

## Out of scope

- Brightness control
- Animation transitions (`Animation.then(...)`)
- WiFi provisioning / device settings
- Bluetooth transport
- Packaging / installer (out of scope for initial version)
