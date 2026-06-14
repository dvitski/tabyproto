# Music Detection & Animation Priority System — Design Spec

**Date:** 2026-06-14  
**Status:** Approved

---

## Overview

Two complementary features:

1. **Animation Priority Controller** — a single gatekeeper through which all animation requests flow, with named priority levels, preemption logic, and duration-aware scheduling.
2. **Music Detection & UI** — detects system audio playback via WASAPI, surfaces rich media metadata via SMTC, displays a full music panel in the desktop sidebar, and instructs Taby to play `LISTENING_MUSIC_LOOP` while music is active.

---

## 1. Animation Priority Controller (`api` module)

### Priority constants

```kotlin
object AnimationPriority {
    const val MANUAL = 100  // user-initiated via UI — always wins
    const val VOICE  = 60   // AI/voice interactions
    const val MUSIC  = 50   // music detection
    const val IDLE   = 10   // idle/ambient animations
}
```

### `AnimationController`

Wraps a `TabySession`. All callers — manual UI, music monitor, voice, future sources — go through this class instead of calling `session.play()` directly.

**Internal state tracked per active request:**
- `priority: Int`
- `animation: Animation`
- `startedAt: Long` (epoch ms)
- `durationMs: Long?` (null = unknown / loops indefinitely)

**`request(priority, animation, durationMs?, preempt = true): RequestResult`**

Decision logic:
1. Nothing playing → play immediately, record state.
2. New priority > active priority:
   - Compute `remaining = durationMs - (now - startedAt)`.
   - If `remaining > 500ms` and `preempt = true` → interrupt, play now.
   - If `remaining ≤ 500ms` → suspend until current finishes, then play.
   - If `preempt = false` → enqueue, play when current source calls `stop()`.
3. New priority ≤ active priority → enqueue; plays when active source releases.

At most one pending request is held per priority level. A second `request()` from the same priority replaces the existing pending one.

**`stop(priority)`** — releases control only if the given priority is currently active. The next queued request (highest priority wins) begins playing immediately.

**`cancel(priority)`** — removes a pending (not yet active) request for that priority without affecting what's playing.

### Duration per request

`AnimationController` receives `durationMs` as a parameter — it has no knowledge of video files. Callers in the desktop layer provide durations looked up from `AnimationResources.durations`. For `Animation.Looping` with an intro, only the intro duration is passed (the body loops indefinitely; `remaining` is treated as `Long.MAX_VALUE` once in the loop body).

### Session lifecycle

When the active device changes, `AppState` recreates `AnimationController` with the new session, cancelling any in-flight request.

---

## 2. Duration Extraction (`desktop` module)

`AnimationResources` already preloads all animation video files at startup. During preload, duration is extracted from each file's media metadata (available from the media player before the first frame renders).

Exposed as:
```kotlin
object AnimationResources {
    val durations: Map<RawAnimation, Long>  // milliseconds; populated during preloadAll()
}
```

Available by the time `AppState` begins handling requests.

---

## 3. Music Detection (`desktop` module)

### `MusicState`

```kotlin
sealed class MusicState {
    object Idle : MusicState()
    data class Playing(
        val track: String?,
        val artist: String?,
        val albumArtUri: String?,
        val position: Duration,
        val duration: Duration?,
        val source: MusicSource,
    ) : MusicState()
}

enum class MusicSource { Generic, Spotify, Tidal }
```

### `MusicMonitor`

Runs two polling loops on a background coroutine and exposes `StateFlow<MusicState>`:

**WASAPI layer** (polls every ~1s) — calls `IAudioSessionManager2` via JNA. Checks if any non-system audio session is in an active (non-expired, non-silent) state. This is the gate: if nothing is playing, SMTC is not queried.

**SMTC layer** (polls every ~2s when WASAPI is active) — queries `GlobalSystemMediaTransportControlsSessionManager` via a JNA WinRT bridge. Provides track name, artist, album art (bitmap URI), playback position, total duration, and play/pause/skip control handles.

**Known-app detection** — reads `SourceAppUserModelId` from the active SMTC session. Well-known IDs for Spotify and Tidal map to `MusicSource.Spotify` / `MusicSource.Tidal`; all others map to `MusicSource.Generic`.

`MusicMonitor` also exposes a `controls()` handle (play/pause, skip prev/next) that delegates to the SMTC session's control interface.

`MusicMonitor` is created once in `AppState` and runs for the app lifetime.

---

## 4. Music UI (`desktop` module — Sidebar)

A `MusicBlock` composable inserted into `Sidebar`, animated visible/invisible as `MusicState` transitions between `Playing` and `Idle`.

**Contents (when Playing):**
- **Album art** — ~48dp rounded thumbnail; fallback to a music note icon if unavailable
- **Source badge** — Spotify/Tidal logo or generic icon, overlaid on the art
- **Track + artist** — two lines, ellipsis-truncated
- **Progress bar** — thin bar below track info; ticks in real-time via a `LaunchedEffect` that increments position every second against `MusicState.Playing.duration`
- **Controls** — prev / play-pause / next icon buttons; tap calls `MusicMonitor.controls()`

**Source-specific theming:**
- `Spotify` → green accent
- `Tidal` → blue accent
- `Generic` → current app palette accent

Slide-in/out transition when music starts or stops.

---

## 5. Wiring in `AppState`

### `AnimationController` integration

`AppState` creates one `AnimationController` and replaces `session.play()` throughout:

- **Manual sends** (`sendAnimation()`):
  ```kotlin
  controller.request(
      priority = AnimationPriority.MANUAL,
      animation = animation,
      durationMs = AnimationResources.durations[animation.primaryRaw],
      preempt = true,
  )
  ```
- **Music reactions** (coroutine collecting `MusicMonitor.state`):
  - `Playing` → `controller.request(AnimationPriority.MUSIC, Animations.LISTENING_MUSIC_LOOP, durationMs = null, preempt = true)`
  - `Idle` → `controller.stop(AnimationPriority.MUSIC)`

### New `AppState` state

```kotlin
val musicState: StateFlow<MusicState>  // from MusicMonitor, observed by Sidebar
```

### Session swap

`AppState` already collects `activeDeviceId`. On change, the old `AnimationController` is cancelled and a new one is created wrapping the new session.

---

## Out of Scope

- Voice priority wiring (structure is in place; implementation deferred)
- Idle animation scheduling
- Linux/macOS audio detection
- Per-app animation customisation beyond `LISTENING_MUSIC_LOOP`
