# Game Detection & Animation Priority — Design Spec

**Date:** 2026-06-18
**Status:** Approved

---

## Overview

Adds a `GAME` animation priority, detected via foreground-process/fullscreen heuristics (the same approach Discord/Steam/Playnite use, since there's no reliable third-party Windows API for "is a game running"). Also retrofits an enable/disable settings toggle for both `GAME` and the existing `MUSIC` detection, since process scanning is more worth gating than passive SMTC polling.

---

## 1. Priority constant (`api` module)

```kotlin
object AnimationPriority {
    const val MANUAL = 100
    const val TOUCH  = 70
    const val VOICE  = 60
    const val GAME   = 55   // new
    const val MUSIC  = 50
    const val IDLE   = 10
}
```

`GAME` sits above `MUSIC` (gaming preempts a music-reactive animation) and below `VOICE`/`TOUCH`/`MANUAL`.

---

## 2. Detection (`desktop` module)

### `GamePoller`

Modeled on `SmtcPoller`: compiles a small C# helper at runtime via `csc.exe` and runs it as a subprocess, parsing JSON stdout. Unlike `SmtcPoller`, this helper is pure Win32 P/Invoke — no WinRT/UWP references, no `Windows.winmd` dependency — so the build path is simpler:

- `GetForegroundWindow` → `GetWindowThreadProcessId` → `Process.GetProcessById(pid).ProcessName`
- Fullscreen check: `GetWindowRect` on the foreground window vs. the bounds of its containing monitor (`MonitorFromWindow` + `GetMonitorInfo`), combined with a check that the window lacks caption/border styles (`GetWindowLong(GWL_STYLE)`excludes `WS_CAPTION`/`WS_THICKFRAME`) or simply spans the full monitor bounds.
- Output: `{ "processName": string, "isFullscreen": bool }`

### `KnownGames.kt`

Two small curated Kotlin lists (no external dependency, no network fetch):

- `KNOWN_GAME_PROCESSES` — process names of popular titles (e.g. League of Legends, Valorant, CS2, Dota 2, Fortnite, Minecraft, GTA5, Overwatch 2...). High-confidence match regardless of fullscreen state.
- `FULLSCREEN_EXCLUDE_PROCESSES` — common non-game apps that often go fullscreen (browsers, IDEs, terminals, Explorer, video players, the Taby desktop app itself). Used to filter the fullscreen fallback.

### `GameState`

```kotlin
sealed class GameState {
    object Idle : GameState()
    data class Active(val processName: String) : GameState()
}
```

### `GameMonitor`

Mirrors `MusicMonitor`:

```kotlin
class GameMonitor(private val scope: CoroutineScope) {
    private val poller = GamePoller()
    val state: StateFlow<GameState>
    fun start()
    fun setEnabled(enabled: Boolean)
}
```

- Polls every 3s (slightly less aggressive than music's 2s, since foreground app switches are less frequent than track changes).
- Classification: `Active` if `processName` is in `KNOWN_GAME_PROCESSES`, **or** (`isFullscreen` and `processName` not in `FULLSCREEN_EXCLUDE_PROCESSES`).
- `setEnabled(false)` short-circuits the poll loop (skip the subprocess call, just delay) and forces `state` to `Idle`.

---

## 3. Animation wiring (`desktop` module — `App.kt`)

No dedicated "gaming" animation asset exists yet. Per direction, reuse `Animations.F1_CAR` (a `Once` animation) as the stand-in.

Mirrors the existing `musicMonitor.state` collector (`App.kt` ~267-289) and its periodic re-send loop (~292-304):

```kotlin
scope.launch {
    gameMonitor.state.collect { state ->
        val id = _activeDeviceId.value ?: return@collect
        val device = devices.value.firstOrNull { it.id == id && it.online } ?: return@collect
        when (state) {
            GameState.Idle -> {
                controller(device).cancel(AnimationPriority.GAME)
                controller(device).stop(AnimationPriority.GAME)
                controller(device).request(AnimationPriority.IDLE, IdleScheduler.IDLE_POOL.random(), null, preempt = false)
            }
            is GameState.Active -> {
                idleScheduler.notifyActivity()
                controller(device).request(
                    priority   = AnimationPriority.GAME,
                    animation  = Animations.F1_CAR,
                    durationMs = null,
                    preempt    = true,
                )
            }
        }
    }
}
scope.launch {
    while (true) {
        delay(3_000)
        val active = gameMonitor.state.value as? GameState.Active ?: continue
        val id = _activeDeviceId.value ?: continue
        val device = devices.value.firstOrNull { it.id == id && it.online } ?: continue
        controller(device).request(
            priority   = AnimationPriority.GAME,
            animation  = Animations.F1_CAR,
            durationMs = null,
            preempt    = true,
        )
    }
}
```

Because `F1_CAR` is a `Once` animation, the periodic re-send is what keeps it replaying for the duration of the gaming session — without it, it would play once and go dark, the same trick already relied on for `LISTENING_MUSIC_LOOP`.

---

## 4. Settings toggle (`desktop` module)

New for **both** `GAME` and `MUSIC` detection.

### `DetectionSettingsStore`

```kotlin
class DetectionSettingsStore {
    private val prefs = Preferences.userNodeForPackage(DetectionSettingsStore::class.java)
    fun load(): DetectionSettings  // musicEnabled, gameEnabled — both default true
    fun save(settings: DetectionSettings)
}

data class DetectionSettings(val musicEnabled: Boolean = true, val gameEnabled: Boolean = true)
```

Same `Preferences`-backed pattern as `ThemeStore`.

### Wiring

- `App.kt` loads `DetectionSettingsStore` at init, alongside `themeStore`/`hostsStore`.
- Calls `musicMonitor.setEnabled(settings.musicEnabled)` and `gameMonitor.setEnabled(settings.gameEnabled)` on load and whenever the setting changes.
- `MusicMonitor` gains the same `setEnabled(Boolean)` method described for `GameMonitor` above (short-circuit poll loop, force `Idle`).

### UI (`SettingsScreen.kt`)

- Existing `Music` settings category gets a new `TogglePill` row ("Detect music playback") above/alongside the now-playing detail, following the same pattern as `dimEnabled`/`minimizeToTray`.
- New `Game` settings category (new `SettingsCategory` entry, sidebar icon) showing a `TogglePill` ("Detect games") and the current `GameState` (e.g. "Currently detected: VALORANT-Win64-Shipping.exe" / "No game detected") for visibility/debugging, mirroring `MusicDetail`.

---

## Out of Scope

- A dedicated "gaming" animation asset (using `F1_CAR` as a stand-in until one exists)
- Expanding `KNOWN_GAME_PROCESSES` beyond an initial small seed list
- Pulling Discord's `detectable.json` or any external/network game database
- Per-game animation customization
- Linux/macOS game detection
