# Idle Status Display in Settings Tab

**Date:** 2026-06-15

## Overview

Show live idle state values in the Idle settings panel so the user can see what the scheduler is doing in real time.

## Data Model

Add `IdlePhase` enum and `IdleStatus` data class (in `IdleScheduler.kt` or a new `IdleStatus.kt`):

```kotlin
enum class IdlePhase { Active, Idle, Relaxed }

data class IdleStatus(
    val phase: IdlePhase,
    val lastActivityAt: Long,   // epoch ms of last activity reset
    val currentAnimation: Animation?,
    val dimBrightness: Int?,    // null = not currently dimming
    val nextAnimAt: Long,       // epoch ms of next animation tick; 0 when Active
)
```

`IdleScheduler` gains `val status: StateFlow<IdleStatus>` backed by a `MutableStateFlow`. It is updated in two places:

- **`notifyActivity()`** — sets `phase = Active`, clears `currentAnimation = null`, `dimBrightness = null`, `nextAnimAt = 0`, records `lastActivityAt = now`.
- **Each loop tick** — sets `phase` (Idle or Relaxed based on `elapsedSec` vs `relaxedThresholdSec`), `currentAnimation`, `dimBrightness` (null if not dimming), and `nextAnimAt = now + variationIntervalSec * 1000`.

`AppState` adds `val idleStatus: StateFlow<IdleStatus>` sourced from `idleScheduler.status` and exposes it to the UI.

## UI

At the top of `IdleDetail` (above the sliders), a status card styled with `surface2`/`border` matching the Device tab. Layout:

```
STATUS
● Active                         (accent-colored when Idle/Relaxed, muted when Active)

ANIMATION           NEXT IN
idle_01_loop        45s          (both "—" when Active)

DIM BRIGHTNESS
38%                              ("—" when not dimming)
```

- Elapsed time appended to phase label when idle/relaxed: e.g. `Idle · 2m 30s`, `Relaxed · 8m 12s`.
- A `LaunchedEffect(Unit)` ticks every second inside the composable to recompute elapsed (`now - lastActivityAt`) and countdown (`nextAnimAt - now`) from timestamps. No extra `StateFlow` updates needed for the clock.
- `IdleDetail` gains two new params: `idleStatus: IdleStatus?` (null until first tick, shows `—` placeholders) and `clock: () -> Long = { System.currentTimeMillis() }` for testability.

## Threading Down

`SettingsScreen` and `App.kt` thread `idleStatus` down from `AppState` to `IdleDetail`, following the existing pattern for `idleSettings`.

## Out of Scope

- No persistence of idle status.
- No alerts or notifications from the status display.
- Animation name shown as raw ID (no display-name mapping).
