# Lock-Screen → Idle Relaxing Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When the Windows session is locked while Tabyproto is open, the active Taby device immediately enters idle *relaxing* mode; unlocking returns it to the normal active idle cycle.

**Architecture:** A persistent C# helper (`session_monitor.cs`, compiled with `csc.exe` like `SmtcPoller`) subscribes to `SystemEvents.SessionSwitch` and prints `LOCK`/`UNLOCK`. A new `LockMonitor` reads those lines and emits a `SharedFlow<LockEvent>`. `IdleScheduler` gains a `forceRelaxed()` method that switches the animation pool to relaxed immediately while leaving the brightness-dim clock (`elapsedSec`) untouched. `AppState` maps `Locked → forceRelaxed()` and `Unlocked → notifyActivity()`.

**Tech Stack:** Kotlin/JVM, Compose Desktop, kotlinx.coroutines (incl. `kotlinx-coroutines-test`), Windows `csc.exe` + `System.Windows.Forms` / `Microsoft.Win32.SystemEvents`.

---

## File Structure

- **Modify** `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/IdleScheduler.kt` — lift loop state to fields, add `forced` flag + `forceRelaxed()`, extract `step()`.
- **Create** `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/LockMonitor.kt` — compile/run the helper, expose `events: SharedFlow<LockEvent>`.
- **Create** `desktop/src/main/resources/session_monitor.cs` — STA console app emitting `LOCK`/`UNLOCK`.
- **Modify** `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt` (`AppState`) — own a `LockMonitor`, wire events, close it.
- **Modify** `desktop/src/test/kotlin/cc/dvitski/tabyproto/desktop/IdleSchedulerTest.kt` — tests for `forceRelaxed()`.

---

## Task 1: Refactor `IdleScheduler` and add `forceRelaxed()`

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/IdleScheduler.kt`
- Test: `desktop/src/test/kotlin/cc/dvitski/tabyproto/desktop/IdleSchedulerTest.kt`

- [ ] **Step 1: Write the failing tests**

Append these four tests inside the `IdleSchedulerTest` class in `desktop/src/test/kotlin/cc/dvitski/tabyproto/desktop/IdleSchedulerTest.kt` (before the closing `}`):

```kotlin
    @Test
    fun `forceRelaxed sends a relaxed animation immediately`() = runTest {
        val requested = mutableListOf<Animation>()
        val settings = MutableStateFlow(IdleSettings(
            variationIntervalSec = 60,
            relaxedThresholdSec  = 600,
            dimEnabled           = false,
        ))
        val scheduler = IdleScheduler(
            scope                = backgroundScope,
            settings             = settings,
            onRequestAnimation   = { anim, _ -> requested += anim },
            onStopAnimation      = {},
            onOverrideBrightness = {},
            onRestoreBrightness  = {},
            getSavedBrightness   = { 100 },
        )

        scheduler.forceRelaxed()
        advanceTimeBy(1L) // dispatch the immediate send, no interval wait

        assertEquals(1, requested.size)
        assertTrue(requested[0] in IdleScheduler.RELAXED_POOL)
    }

    @Test
    fun `forceRelaxed keeps pool relaxed even before relaxedThresholdSec`() = runTest {
        val requested = mutableListOf<Animation>()
        val settings = MutableStateFlow(IdleSettings(
            variationIntervalSec = 60,
            relaxedThresholdSec  = 600, // would be IDLE pool if not forced
            dimEnabled           = false,
        ))
        val scheduler = IdleScheduler(
            scope                = backgroundScope,
            settings             = settings,
            onRequestAnimation   = { anim, _ -> requested += anim },
            onStopAnimation      = {},
            onOverrideBrightness = {},
            onRestoreBrightness  = {},
            getSavedBrightness   = { 100 },
        )

        scheduler.forceRelaxed()
        advanceTimeBy(180_001L) // immediate + 3 interval ticks, all well under 600s

        assertTrue(requested.isNotEmpty())
        assertTrue(requested.all { it in IdleScheduler.RELAXED_POOL },
            "expected all relaxed, got ${requested.map { it.id }}")
    }

    @Test
    fun `forceRelaxed does not dim early - dim clock is preserved`() = runTest {
        val brightnesses = mutableListOf<Int>()
        val settings = MutableStateFlow(IdleSettings(
            variationIntervalSec = 60,
            relaxedThresholdSec  = 600,
            dimEnabled           = true,
            dimDelayThresholdSec = 120, // dim only after 120s of real inactivity
            dimFloorPercent      = 20,
        ))
        val scheduler = IdleScheduler(
            scope                = backgroundScope,
            settings             = settings,
            onRequestAnimation   = { _, _ -> },
            onStopAnimation      = {},
            onOverrideBrightness = { brightnesses += it },
            onRestoreBrightness  = {},
            getSavedBrightness   = { 100 },
        )

        scheduler.forceRelaxed() // elapsed is still 0 → no dim yet
        advanceTimeBy(60_001L)   // immediate send + one 60s tick: elapsed=60 < 120

        assertTrue(brightnesses.isEmpty(), "dim must not be fast-forwarded by lock")
    }

    @Test
    fun `notifyActivity exits forced mode and returns to idle pool`() = runTest {
        val requested = mutableListOf<Animation>()
        val settings = MutableStateFlow(IdleSettings(
            variationIntervalSec = 60,
            relaxedThresholdSec  = 600,
            dimEnabled           = false,
        ))
        val scheduler = IdleScheduler(
            scope                = backgroundScope,
            settings             = settings,
            onRequestAnimation   = { anim, _ -> requested += anim },
            onStopAnimation      = {},
            onOverrideBrightness = {},
            onRestoreBrightness  = {},
            getSavedBrightness   = { 100 },
        )

        scheduler.forceRelaxed()
        advanceTimeBy(1L)
        scheduler.notifyActivity() // unlock
        requested.clear()
        advanceTimeBy(60_001L) // one tick after unlock, elapsed=60 < 600 → IDLE pool

        assertTrue(requested.isNotEmpty())
        assertTrue(requested.all { it in IdleScheduler.IDLE_POOL },
            "expected idle pool after unlock, got ${requested.map { it.id }}")
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :desktop:test --tests "cc.dvitski.tabyproto.desktop.IdleSchedulerTest"`
Expected: FAIL — compilation error, `forceRelaxed()` is unresolved.

- [ ] **Step 3: Refactor `IdleScheduler` and add `forceRelaxed()`**

Replace the entire contents of `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/IdleScheduler.kt` with:

```kotlin
package cc.dvitski.tabyproto.desktop

import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.AnimationPriority
import cc.dvitski.tabyproto.Animations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IdleScheduler(
    private val scope: CoroutineScope,
    private val settings: StateFlow<IdleSettings>,
    private val onRequestAnimation: suspend (Animation, Int) -> Unit,
    private val onStopAnimation: suspend (Int) -> Unit,
    private val onOverrideBrightness: suspend (Int) -> Unit,
    private val onRestoreBrightness: suspend () -> Unit,
    private val getSavedBrightness: () -> Int?,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    internal companion object {
        val IDLE_POOL = listOf(
            Animations.IDLE_01_LOOP,
            Animations.IDLE_02_LOOP,
            Animations.IDLE_VARIATION_LOOP,
        )
        val RELAXED_POOL = listOf(
            Animations.SLEEPING_LOOP,
            Animations.RELAXING_01_LOOP,
            Animations.RELAXING_COUCH_LOOP,
        )
    }

    private var loopJob: Job? = null
    private var elapsedSec = 0L
    private var lastAnim: Animation? = null
    // When true, the pool is forced to RELAXED regardless of elapsedSec (e.g. screen locked).
    // elapsedSec keeps advancing so the dim ramp still follows real inactivity time.
    @Volatile private var forced = false

    private val _status = MutableStateFlow(IdleStatus(IdlePhase.Active, clock(), null, null, 0L))
    val status: StateFlow<IdleStatus> = _status.asStateFlow()

    init { startLoop(immediate = false) }

    fun notifyActivity() {
        forced = false
        loopJob?.cancel()
        elapsedSec = 0L
        lastAnim = null
        _status.value = IdleStatus(IdlePhase.Active, clock(), null, null, 0L)
        scope.launch {
            onRestoreBrightness()
            onStopAnimation(AnimationPriority.IDLE)
        }
        startLoop(immediate = false)
    }

    /** Immediately enter relaxing mode (relaxed animation pool) without resetting the dim clock. */
    fun forceRelaxed() {
        if (forced) return
        forced = true
        loopJob?.cancel()
        startLoop(immediate = true)
    }

    private fun startLoop(immediate: Boolean) {
        loopJob = scope.launch { runLoop(immediate) }
    }

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

    private suspend fun step() {
        val s = settings.value
        val relaxed = forced || elapsedSec >= s.relaxedThresholdSec
        val pool = if (relaxed) RELAXED_POOL else IDLE_POOL
        val candidates = pool.filter { it != lastAnim }
        val next = candidates.randomOrNull() ?: pool.random()
        lastAnim = next
        onRequestAnimation(next, AnimationPriority.IDLE)
        val phase = if (relaxed) IdlePhase.Relaxed else IdlePhase.Idle
        val dimBrightness: Int?
        if (s.dimEnabled && elapsedSec >= s.dimDelayThresholdSec) {
            val saved = getSavedBrightness() ?: 100
            val floor = s.dimFloorPercent.coerceAtMost(saved)
            val progress = ((elapsedSec - s.dimDelayThresholdSec).toFloat() /
                s.dimDelayThresholdSec.toFloat()).coerceIn(0f, 1f)
            val dimmed = (saved - (saved - floor) * progress)
                .toInt().coerceAtLeast(floor)
            onOverrideBrightness(dimmed)
            dimBrightness = dimmed
        } else {
            dimBrightness = null
        }
        _status.value = IdleStatus(
            phase = phase,
            lastActivityAt = _status.value.lastActivityAt,
            currentAnimation = next,
            dimBrightness = dimBrightness,
            nextAnimAt = clock() + s.variationIntervalSec * 1000L,
        )
    }
}
```

- [ ] **Step 4: Run the full `IdleSchedulerTest` suite to verify all pass**

Run: `./gradlew :desktop:test --tests "cc.dvitski.tabyproto.desktop.IdleSchedulerTest"`
Expected: PASS — the 4 new tests plus all 8 pre-existing tests (the `step()` extraction must not change existing behavior).

- [ ] **Step 5: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/IdleScheduler.kt \
        desktop/src/test/kotlin/cc/dvitski/tabyproto/desktop/IdleSchedulerTest.kt
git commit -m "feat(desktop): IdleScheduler.forceRelaxed for immediate relaxing mode"
```

---

## Task 2: Add the `session_monitor.cs` helper and `LockMonitor`

**Files:**
- Create: `desktop/src/main/resources/session_monitor.cs`
- Create: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/LockMonitor.kt`

No unit test: `LockMonitor` wraps a native Windows process and is verified manually in Task 3. Keep it thin.

- [ ] **Step 1: Create the C# helper**

Create `desktop/src/main/resources/session_monitor.cs`:

```csharp
using System;
using System.Windows.Forms;
using Microsoft.Win32;

// Persistent helper: prints LOCK / UNLOCK to stdout on Windows session lock/unlock.
// SystemEvents.SessionSwitch requires a UI-thread message loop, provided by Application.Run().
class SessionMonitor
{
    [STAThread]
    static void Main()
    {
        SystemEvents.SessionSwitch += OnSessionSwitch;
        Application.Run();
    }

    static void OnSessionSwitch(object sender, SessionSwitchEventArgs e)
    {
        if (e.Reason == SessionSwitchReason.SessionLock)
        {
            Console.Out.WriteLine("LOCK");
            Console.Out.Flush();
        }
        else if (e.Reason == SessionSwitchReason.SessionUnlock)
        {
            Console.Out.WriteLine("UNLOCK");
            Console.Out.Flush();
        }
    }
}
```

- [ ] **Step 2: Create `LockMonitor`**

Create `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/LockMonitor.kt`:

```kotlin
package cc.dvitski.tabyproto.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class LockEvent {
    object Locked : LockEvent()
    object Unlocked : LockEvent()
}

/**
 * Watches Windows session lock/unlock by compiling and running [session_monitor.cs]
 * with csc.exe (same toolchain as [SmtcPoller]) and reading LOCK / UNLOCK lines.
 * Degrades to a no-op if csc.exe is unavailable.
 */
class LockMonitor(private val scope: CoroutineScope) {

    private val cacheDir = File(System.getProperty("java.io.tmpdir"), "taby-lock").also { it.mkdirs() }
    private val exeFile = File(cacheDir, "session_monitor.exe")

    private val _events = MutableSharedFlow<LockEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<LockEvent> = _events.asSharedFlow()

    @Volatile private var process: Process? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        scope.launch(Dispatchers.IO) { runLoop() }
    }

    fun close() {
        running = false
        process?.destroy()
        process = null
    }

    private suspend fun runLoop() {
        val exe = buildExe() ?: return
        while (running) {
            try {
                val proc = ProcessBuilder(exe).start()
                process = proc
                proc.inputStream.bufferedReader().use { reader ->
                    while (running) {
                        val line = reader.readLine() ?: break
                        when (line.trim()) {
                            "LOCK"   -> _events.emit(LockEvent.Locked)
                            "UNLOCK" -> _events.emit(LockEvent.Unlocked)
                        }
                    }
                }
                proc.waitFor()
            } catch (_: Exception) {
                // fall through and restart below
            }
            if (running) delay(2_000L) // brief backoff before relaunching the helper
        }
    }

    private fun buildExe(): String? {
        if (exeFile.exists()) return exeFile.absolutePath
        val csc = findCsc() ?: return null
        val src = LockMonitor::class.java.getResourceAsStream("/session_monitor.cs") ?: return null
        val srcFile = File(cacheDir, "session_monitor.cs").also { f -> src.use { s -> f.writeBytes(s.readBytes()) } }
        ProcessBuilder(
            csc, "/nologo", "/target:exe", "/platform:x64",
            "/out:${exeFile.absolutePath}",
            "/r:System.Windows.Forms.dll",
            srcFile.absolutePath,
        ).redirectErrorStream(true).start().also { it.inputStream.bufferedReader().readText() }.waitFor()
        return exeFile.takeIf { it.exists() }?.absolutePath
    }

    // csc.exe ships with .NET Framework 4.x on Windows (see SmtcPoller for the same probe).
    private fun findCsc(): String? =
        """C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe""".let { if (File(it).exists()) it else null }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :desktop:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add desktop/src/main/resources/session_monitor.cs \
        desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/LockMonitor.kt
git commit -m "feat(desktop): LockMonitor detects Windows session lock/unlock"
```

---

## Task 3: Wire `LockMonitor` into `AppState`

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt`

- [ ] **Step 1: Add the `LockMonitor` field**

In `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt`, add the field next to `musicMonitor` (currently at line 66):

```kotlin
    private val musicMonitor = MusicMonitor(scope)
    private val lockMonitor = LockMonitor(scope)
```

- [ ] **Step 2: Start and collect lock events in `init`**

In the `init` block, immediately after the existing touch-signal collector (the `scope.launch { monitor.events.collect { ... } }` block that ends around line 200), add:

```kotlin
        lockMonitor.start()
        scope.launch {
            lockMonitor.events.collect { event ->
                when (event) {
                    LockEvent.Locked   -> idleScheduler.forceRelaxed()
                    LockEvent.Unlocked -> idleScheduler.notifyActivity()
                }
            }
        }
```

- [ ] **Step 3: Close the monitor in `close()`**

Replace the existing `close()` (currently line 316):

```kotlin
    fun close() { scope.cancel(); monitor.close() }
```

with:

```kotlin
    fun close() { lockMonitor.close(); scope.cancel(); monitor.close() }
```

- [ ] **Step 4: Verify the module builds and all tests still pass**

Run: `./gradlew :desktop:build`
Expected: BUILD SUCCESSFUL; `IdleSchedulerTest` and all other desktop tests pass.

- [ ] **Step 5: Manual verification**

1. `./gradlew :desktop:run` with a Taby device connected and selected.
2. Lock the screen (Win+L). Expected: the device switches to a relaxed-pool animation (sleeping / relaxing) within ~1s; brightness does **not** jump-dim.
3. Unlock. Expected: brightness restores and the device returns to the normal active idle cycle (idle-pool animations resume on the usual schedule).

- [ ] **Step 6: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt
git commit -m "feat(desktop): enter relaxing mode on screen lock, resume on unlock"
```

---

## Self-Review Notes

- **Spec coverage:** lock → immediate relaxed (Task 1 `forceRelaxed` + Task 2/3 detection & wiring); unlock → activity (Task 3 maps to `notifyActivity`); dim follows real inactivity, not lock (Task 1 preserves `elapsedSec`, verified by the "does not dim early" test); graceful degradation (Task 2 `buildExe`/`findCsc` return null → no-op); music priority unaffected (relaxed runs at `IDLE`, no code change needed).
- **Type consistency:** `forceRelaxed()` / `notifyActivity()` names match across `IdleScheduler` and `AppState`; `LockEvent.Locked` / `LockEvent.Unlocked` match between `LockMonitor` and the `AppState` collector; helper emits exactly `LOCK` / `UNLOCK` matching the `when` in `LockMonitor.runLoop`.
- **No placeholders:** every code and command step is complete.
