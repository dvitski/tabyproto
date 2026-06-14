# Idle Scheduler — Design Spec

**Date:** 2026-06-14  
**Status:** Approved

---

## Overview

When no higher-priority animation is playing, Taby should feel alive: it occasionally switches between idle animations, transitions to a more relaxed pool after extended inactivity, and gradually dims its brightness toward a configurable floor. All timing and dimming parameters are user-configurable. Dimming is an ephemeral override — it does not affect the saved brightness setting and restores immediately on any activity.

---

## 1. Idle Settings (`desktop` module)

### `IdleSettings`

```kotlin
data class IdleSettings(
    val variationIntervalSec: Int = 90,
    val relaxedThresholdSec: Int = 300,
    val dimEnabled: Boolean = true,
    val dimDelayThresholdSec: Int = 300,
    val dimFloorPercent: Int = 20,
)
```

| Field | Default | Meaning |
|---|---|---|
| `variationIntervalSec` | 90s | How often to rotate to a new random idle animation |
| `relaxedThresholdSec` | 300s (5 min) | Elapsed idle time before switching to the relaxed animation pool |
| `dimEnabled` | true | Whether brightness dimming is active at all |
| `dimDelayThresholdSec` | 300s (5 min) | Elapsed idle time before the brightness ramp begins |
| `dimFloorPercent` | 20% | Minimum brightness to dim to |

### Animation pools (hardcoded)

- **Idle pool** (elapsed < `relaxedThresholdSec`): `IDLE_01_LOOP`, `IDLE_02_LOOP`, `IDLE_VARIATION_LOOP`
- **Relaxed pool** (elapsed ≥ `relaxedThresholdSec`): `SLEEPING_LOOP`, `RELAXING_01_LOOP`, `RELAXING_COUCH_LOOP`

Each tick picks uniformly at random from the appropriate pool, avoiding repeating the same animation twice in a row.

### `IdleStore`

Persists `IdleSettings` as JSON to disk. Same pattern as `ThemeStore`.

---

## 2. `IdleScheduler` (`desktop` module)

```kotlin
class IdleScheduler(
    private val scope: CoroutineScope,
    private val settings: StateFlow<IdleSettings>,
    private val onRequestAnimation: suspend (animation: Animation, priority: Int) -> Unit,
    private val onStopAnimation: suspend (priority: Int) -> Unit,
    private val onOverrideBrightness: suspend (percent: Int) -> Unit,
    private val onRestoreBrightness: suspend () -> Unit,
    private val getSavedBrightness: () -> Int?,
)
```

### `notifyActivity()`

Called by `AppState` when any non-idle event occurs (manual animation send, music transitions to `Playing`). Effects:

1. Cancels the current idle coroutine loop.
2. Calls `onRestoreBrightness()` — sends the saved brightness back to the device without touching UI state.
3. Calls `onStopAnimation(IDLE)` to release the idle priority slot.
4. Restarts the idle loop from `t = 0`.

### Internal loop

Runs as a single coroutine. On each iteration:

1. Waits `variationIntervalSec`.
2. Increments elapsed time.
3. Selects pool based on `elapsed >= relaxedThresholdSec`.
4. Picks a random animation from the pool (no immediate repeat).
5. Calls `onRequestAnimation(anim, AnimationPriority.IDLE)`.
6. If `dimEnabled` and `elapsed >= dimDelayThresholdSec`: computes a linearly interpolated brightness between `savedBrightness` and `dimFloorPercent` based on how far past the dim threshold we are (capped at floor). Calls `onOverrideBrightness(computed)`.

The brightness ramp ticks on the same `variationIntervalSec` cadence — no separate timer.

### Brightness override vs. saved setting

`onOverrideBrightness` calls `session.setBrightness()` directly on the device. It does **not** update `AppState._brightness` (the UI slider value). The slider always reflects the user's saved preference. On restore, `onRestoreBrightness()` sends `getSavedBrightness()` back to the device, also without touching UI state.

### Settings reactivity

`IdleScheduler` collects `settings` and restarts the loop on any change, resetting elapsed time.

---

## 3. Wiring in `AppState`

### New state

```kotlin
val idleSettings: StateFlow<IdleSettings>  // from IdleStore, mutable via updateIdleSettings()
fun updateIdleSettings(settings: IdleSettings)
```

### `IdleScheduler` creation

Created once in `AppState.init`. Lambdas:

- `onRequestAnimation` → `controller(device).request(animation, priority, durationMs = null, preempt = false)`
- `onStopAnimation` → `controller(device).stop(priority)`
- `onOverrideBrightness` → `monitor.session(device).setBrightness(percent)` (bypasses `_brightness`)
- `onRestoreBrightness` → `monitor.session(device).setBrightness(_brightness.value ?: return)`
- `getSavedBrightness` → `{ _brightness.value }`

### Activity signals

`notifyActivity()` is called:
- In `sendAnimation()` before the controller request.
- In the `musicMonitor.state` collector when state transitions to `MusicState.Playing` with `isPlaying == true`.

### Removal of existing idle request

The `controller.request(IDLE, IDLE_01_LOOP)` calls in the `musicMonitor.state` collector (on `Idle` and `!isPlaying`) are removed. `IdleScheduler` owns idle animation entirely.

---

## 4. Settings UI

A new **"Idle"** category added to `SettingsCategory`. The settings panel contains:

| Control | Range | Bound to |
|---|---|---|
| Variation interval slider | 30s – 300s | `variationIntervalSec` |
| Relaxed threshold slider | 60s – 900s (1–15 min) | `relaxedThresholdSec` |
| Dim enable toggle | on/off | `dimEnabled` |
| Dim delay slider (visible when dim on) | 60s – 900s | `dimDelayThresholdSec` |
| Dim floor slider (visible when dim on) | 10% – 80% | `dimFloorPercent` |

All changes call `appState.updateIdleSettings()`, which persists to `IdleStore` and updates the `StateFlow` immediately.

---

## Out of Scope

- Per-animation-pool configuration via UI (pools are hardcoded for now)
- "Sleep" mode (screen off) — a potential future tier beyond relaxed
- System-level idle detection (mouse/keyboard inactivity) — currently uses app-defined activity only
