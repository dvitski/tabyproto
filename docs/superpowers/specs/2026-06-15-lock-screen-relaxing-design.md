# Lock-Screen → Idle Relaxing Mode

**Date:** 2026-06-15
**Status:** Approved (design)

## Goal

When the Windows session is locked while Tabyproto is open, the active Taby
device should *immediately* enter idle **relaxing** mode (the `RELAXED_POOL`
animations). When the session is unlocked, behavior returns to the normal
`Active` idle cycle, exactly as if the user had touched the device.

## Behavior decisions

- **On unlock:** treat as activity. Reset the idle scheduler to `Active`
  (restore brightness, stop idle animation), and let idle/relaxed re-engage on
  the normal inactivity schedule. Same effect as a touch signal or manual send.
- **On lock:** switch the animation pool to relaxed and send a relaxed
  animation immediately. Do **not** dim brightness early — dimming continues to
  follow real inactivity time on its normal schedule.

### Why dimming must not be fast-forwarded

`IdleScheduler` selects the relaxed pool once `elapsedSec >=
relaxedThresholdSec`, and ramps brightness once `elapsedSec >=
dimDelayThresholdSec` (both default 300s). If lock were implemented by simply
advancing `elapsedSec` past the relaxed threshold, the dim ramp would also be
fast-forwarded — contradicting the "relaxing animations only" decision.
Therefore lock forces the *pool selection* to relaxed via a separate flag while
leaving `elapsedSec` (the dim clock) untouched.

## Detection approach

Use a **persistent C# event helper**, reusing the `csc.exe` compile-and-run
toolchain already proven by `SmtcPoller`. This is genuinely event-driven, so it
fires immediately on lock (the requirement), and stays consistent with the
existing native-integration pattern.

Alternatives considered and rejected:
- **Polling helper** (`WTSQuerySessionInformation`): simpler, but adds ~1s
  latency and an extra poll loop.
- **Pure-JVM/JNA**: avoids the compile step but adds a JNA dependency and a
  native message window — heavier and inconsistent with the codebase.

## Components

### New: `LockMonitor` (desktop module)

Mirrors `MusicMonitor` / `SmtcPoller`.

- Lazily compiles a bundled `/session_monitor.cs` resource into
  `session_monitor.exe` in the temp cache dir, using the same `findCsc()`
  toolchain. Adds `/r:System.Windows.Forms.dll` so the helper can host the
  message loop that `Microsoft.Win32.SystemEvents.SessionSwitch` requires.
- `start()` launches the process on `Dispatchers.IO`, reads stdout line by
  line, and emits a `SharedFlow<LockEvent>` (`Locked` / `Unlocked`).
- Auto-restarts the helper process if it exits unexpectedly.
- `close()` terminates the process.
- **Degrades gracefully:** if compilation fails (no `csc.exe`/SDK), it emits
  nothing and the app behaves exactly as today.

`session_monitor.cs` (bundled resource): an STA console app that subscribes to
`SystemEvents.SessionSwitch`, prints `LOCK` on
`SessionSwitchReason.SessionLock` and `UNLOCK` on
`SessionSwitchReason.SessionUnlock`, flushes stdout per line, and runs a
message loop (`Application.Run()`) so the events are delivered.

```kotlin
sealed class LockEvent {
    object Locked : LockEvent()
    object Unlocked : LockEvent()
}
```

### Changed: `IdleScheduler`

Add `fun forceRelaxed()` and support an "immediate" loop start.

- Lift `elapsedSec` and `lastAnim` from `runLoop` locals to instance fields.
- Add `@Volatile private var forced = false`.
- Factor the per-tick "pick + send animation, update status, apply dim" logic
  into a private `suspend fun step()` that reads `forced` and `elapsedSec`:
  - pool = `if (forced || elapsedSec >= relaxedThresholdSec) RELAXED_POOL else IDLE_POOL`
  - phase = `if (forced || elapsedSec >= relaxedThresholdSec) Relaxed else Idle`
  - dim logic unchanged (still keyed off `elapsedSec` and `dimDelayThresholdSec`)
- `forceRelaxed()`:
  - no-op if already `forced`.
  - sets `forced = true`, cancels `loopJob`, restarts the loop in "immediate"
    mode (calls `step()` once before the first `delay`) so a relaxed animation
    is sent right away.
  - **does not** reset or advance `elapsedSec` — the dim clock is preserved.
- `notifyActivity()` (called on unlock and on touch/manual): clears `forced`,
  resets `elapsedSec`/`lastAnim`, restores brightness, stops idle animation —
  unchanged in spirit from today, plus the `forced = false` reset.

Loop shape after refactor:

```kotlin
private suspend fun runLoop(immediate: Boolean) {
    if (immediate) step()
    while (true) {
        val s = settings.value
        _status.value = _status.value.copy(nextAnimAt = clock() + s.variationIntervalSec * 1000L)
        delay(s.variationIntervalSec * 1000L)
        elapsedSec += s.variationIntervalSec
        step()
    }
}
```

### Changed: `AppState`

- Construct a `LockMonitor` (alongside `musicMonitor`).
- In `init`, after `idleScheduler` is created, `lockMonitor.start()` and collect
  its flow on `scope`:
  - `LockEvent.Locked` → `idleScheduler.forceRelaxed()`
  - `LockEvent.Unlocked` → `idleScheduler.notifyActivity()`
- Add `lockMonitor.close()` to `close()`.

## Interaction with music priority

Relaxed animations run at `AnimationPriority.IDLE`. If real audio is still
playing while locked, the existing `MUSIC` priority continues to win — lock
changes idle behavior only and does not fight the music layer. This is
consistent with the current priority model and requires no special handling.

## Testing

- **`IdleSchedulerTest`** additions (using the existing fake-clock / callback
  harness):
  - `forceRelaxed()` sends a relaxed-pool animation immediately.
  - while forced, the pool stays relaxed regardless of `elapsedSec`.
  - `forceRelaxed()` does **not** change the dim behavior keyed off
    `elapsedSec` (dim is unchanged vs. an un-forced scheduler at the same
    elapsed time).
  - `notifyActivity()` exits forced mode and returns to `Active`.
- **`LockMonitor`** wraps a native process and cannot be meaningfully
  unit-tested; it is kept thin and verified manually (lock the screen, confirm
  the device switches to a relaxed animation; unlock, confirm it returns to the
  active cycle).
