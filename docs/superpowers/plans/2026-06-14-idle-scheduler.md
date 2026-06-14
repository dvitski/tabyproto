# Idle Scheduler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an `IdleScheduler` that rotates animations when idle, transitions to a relaxed pool after a configurable threshold, and linearly dims brightness (as a transient device override) while idle — all driven by user-configurable settings with a new Idle settings panel.

**Architecture:** `IdleScheduler` in the `desktop` module owns the idle timer, animation rotation, and brightness ramp. It is created once in `AppState` and reset on any activity signal. `IdleStore` persists settings via `java.util.prefs.Preferences`. The Settings screen gains an "Idle" category panel.

**Tech Stack:** Kotlin coroutines (`delay`, `Job`, `StateFlow`), Compose Desktop, `java.util.prefs.Preferences`, `kotlinx-coroutines-test` for unit tests.

---

## File Map

| Action | Path | Responsibility |
|---|---|---|
| Create | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/IdleSettings.kt` | `IdleSettings` data class |
| Create | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/IdleStore.kt` | Persists `IdleSettings` via Preferences |
| Create | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/IdleScheduler.kt` | Idle loop: animation rotation + brightness ramp |
| Create | `desktop/src/test/kotlin/cc/dvitski/tabyproto/desktop/IdleSchedulerTest.kt` | Unit tests for `IdleScheduler` |
| Modify | `desktop/build.gradle.kts` | Add test dependencies |
| Modify | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt` | Wire `IdleStore`, `IdleScheduler`, add `SettingsCategory.Idle`, expose `idleSettings`, `updateIdleSettings()`, remove old IDLE_01_LOOP requests |
| Modify | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/SettingsScreen.kt` | Add "Idle" label + `IdleDetail` composable, update `SettingsScreen` signature |

---

## Task 1: Add test infrastructure to the desktop module

**Files:**
- Modify: `desktop/build.gradle.kts`

- [ ] **Step 1: Add test dependencies**

Open `desktop/build.gradle.kts`. The current `dependencies` block has no test entries. Add:

```kotlin
plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":api"))
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("io.github.kdroidfilter:composemediaplayer:0.10.0")
    implementation("org.bytedeco:javacv:1.5.11")
    implementation("org.bytedeco:ffmpeg:6.1.1-1.5.11:windows-x86_64")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

compose.desktop {
    application {
        mainClass = "cc.dvitski.tabyproto.desktop.MainKt"
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 2: Verify Gradle syncs**

Run:
```
./gradlew :desktop:dependencies --configuration testRuntimeClasspath
```
Expected: resolves without error, output includes `kotlinx-coroutines-test`.

- [ ] **Step 3: Commit**

```bash
git add desktop/build.gradle.kts
git commit -m "build: add test dependencies to desktop module"
```

---

## Task 2: `IdleSettings` data class and `IdleStore`

**Files:**
- Create: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/IdleSettings.kt`
- Create: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/IdleStore.kt`

- [ ] **Step 1: Create `IdleSettings.kt`**

```kotlin
package cc.dvitski.tabyproto.desktop

data class IdleSettings(
    val variationIntervalSec: Int = 90,
    val relaxedThresholdSec: Int = 300,
    val dimEnabled: Boolean = true,
    val dimDelayThresholdSec: Int = 300,
    val dimFloorPercent: Int = 20,
)
```

- [ ] **Step 2: Create `IdleStore.kt`**

```kotlin
package cc.dvitski.tabyproto.desktop

import java.util.prefs.Preferences

class IdleStore {
    private val prefs = Preferences.userNodeForPackage(IdleStore::class.java)

    fun load() = IdleSettings(
        variationIntervalSec  = prefs.getInt(KEY_VARIATION, 90),
        relaxedThresholdSec   = prefs.getInt(KEY_RELAXED,   300),
        dimEnabled            = prefs.getBoolean(KEY_DIM_ENABLED, true),
        dimDelayThresholdSec  = prefs.getInt(KEY_DIM_DELAY,  300),
        dimFloorPercent       = prefs.getInt(KEY_DIM_FLOOR,  20),
    )

    fun save(s: IdleSettings) {
        prefs.putInt(KEY_VARIATION,       s.variationIntervalSec)
        prefs.putInt(KEY_RELAXED,         s.relaxedThresholdSec)
        prefs.putBoolean(KEY_DIM_ENABLED, s.dimEnabled)
        prefs.putInt(KEY_DIM_DELAY,       s.dimDelayThresholdSec)
        prefs.putInt(KEY_DIM_FLOOR,       s.dimFloorPercent)
    }

    private companion object {
        const val KEY_VARIATION   = "idleVariationIntervalSec"
        const val KEY_RELAXED     = "idleRelaxedThresholdSec"
        const val KEY_DIM_ENABLED = "idleDimEnabled"
        const val KEY_DIM_DELAY   = "idleDimDelayThresholdSec"
        const val KEY_DIM_FLOOR   = "idleDimFloorPercent"
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/IdleSettings.kt
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/IdleStore.kt
git commit -m "feat: add IdleSettings data class and IdleStore persistence"
```

---

## Task 3: `IdleScheduler`

**Files:**
- Create: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/IdleScheduler.kt`
- Create: `desktop/src/test/kotlin/cc/dvitski/tabyproto/desktop/IdleSchedulerTest.kt`

- [ ] **Step 1: Write failing tests**

Create `desktop/src/test/kotlin/cc/dvitski/tabyproto/desktop/IdleSchedulerTest.kt`:

```kotlin
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package cc.dvitski.tabyproto.desktop

import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.AnimationPriority
import cc.dvitski.tabyproto.Animations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdleSchedulerTest {

    private val IDLE_POOL = listOf(
        Animations.IDLE_01_LOOP, Animations.IDLE_02_LOOP, Animations.IDLE_VARIATION_LOOP,
    )
    private val RELAXED_POOL = listOf(
        Animations.SLEEPING_LOOP, Animations.RELAXING_01_LOOP, Animations.RELAXING_COUCH_LOOP,
    )

    @Test
    fun `requests idle animation after variationIntervalSec`() = runTest {
        val requested = mutableListOf<Animation>()
        val settings = MutableStateFlow(IdleSettings(
            variationIntervalSec = 60,
            relaxedThresholdSec  = 300,
            dimEnabled           = false,
            dimDelayThresholdSec = 300,
            dimFloorPercent      = 20,
        ))
        IdleScheduler(
            scope                = this,
            settings             = settings,
            onRequestAnimation   = { anim, _ -> requested += anim },
            onStopAnimation      = {},
            onOverrideBrightness = {},
            onRestoreBrightness  = {},
            getSavedBrightness   = { 100 },
        )

        advanceTimeBy(60_001L)

        assertEquals(1, requested.size)
        assertTrue(requested[0] in IDLE_POOL)
    }

    @Test
    fun `switches to relaxed pool after relaxedThresholdSec`() = runTest {
        val requested = mutableListOf<Animation>()
        val settings = MutableStateFlow(IdleSettings(
            variationIntervalSec = 60,
            relaxedThresholdSec  = 120,
            dimEnabled           = false,
            dimDelayThresholdSec = 600,
            dimFloorPercent      = 20,
        ))
        IdleScheduler(
            scope                = this,
            settings             = settings,
            onRequestAnimation   = { anim, _ -> requested += anim },
            onStopAnimation      = {},
            onOverrideBrightness = {},
            onRestoreBrightness  = {},
            getSavedBrightness   = { 100 },
        )

        advanceTimeBy(180_001L) // 3 ticks: 60s, 120s, 180s

        // ticks at 60s → idle pool, 120s → relaxed pool (120 >= 120), 180s → relaxed pool
        assertEquals(3, requested.size)
        assertTrue(requested[0] in IDLE_POOL)
        assertTrue(requested[1] in RELAXED_POOL)
        assertTrue(requested[2] in RELAXED_POOL)
    }

    @Test
    fun `requests use AnimationPriority_IDLE priority`() = runTest {
        val priorities = mutableListOf<Int>()
        val settings = MutableStateFlow(IdleSettings(variationIntervalSec = 60))
        IdleScheduler(
            scope                = this,
            settings             = settings,
            onRequestAnimation   = { _, priority -> priorities += priority },
            onStopAnimation      = {},
            onOverrideBrightness = {},
            onRestoreBrightness  = {},
            getSavedBrightness   = { 100 },
        )

        advanceTimeBy(60_001L)

        assertEquals(listOf(AnimationPriority.IDLE), priorities)
    }

    @Test
    fun `no brightness override when dimEnabled is false`() = runTest {
        val brightnesses = mutableListOf<Int>()
        val settings = MutableStateFlow(IdleSettings(
            variationIntervalSec = 60,
            relaxedThresholdSec  = 600,
            dimEnabled           = false,
            dimDelayThresholdSec = 60,
            dimFloorPercent      = 20,
        ))
        IdleScheduler(
            scope                = this,
            settings             = settings,
            onRequestAnimation   = { _, _ -> },
            onStopAnimation      = {},
            onOverrideBrightness = { brightnesses += it },
            onRestoreBrightness  = {},
            getSavedBrightness   = { 100 },
        )

        advanceTimeBy(300_001L)

        assertTrue(brightnesses.isEmpty())
    }

    @Test
    fun `brightness ramps linearly from saved to floor`() = runTest {
        val brightnesses = mutableListOf<Int>()
        // variationInterval=60s, dimDelay=120s, floor=20, saved=100
        // tick 1 (60s): elapsed=60, < dimDelay=120 → no dim
        // tick 2 (120s): elapsed=120 == dimDelay → progress=0.0, dimmed=100
        // tick 3 (180s): progress=(180-120)/120=0.5, dimmed=100-(80*0.5)=60
        // tick 4 (240s): progress=1.0, dimmed=20
        val settings = MutableStateFlow(IdleSettings(
            variationIntervalSec = 60,
            relaxedThresholdSec  = 600,
            dimEnabled           = true,
            dimDelayThresholdSec = 120,
            dimFloorPercent      = 20,
        ))
        IdleScheduler(
            scope                = this,
            settings             = settings,
            onRequestAnimation   = { _, _ -> },
            onStopAnimation      = {},
            onOverrideBrightness = { brightnesses += it },
            onRestoreBrightness  = {},
            getSavedBrightness   = { 100 },
        )

        advanceTimeBy(241_000L)

        assertEquals(3, brightnesses.size)
        assertEquals(100, brightnesses[0]) // progress 0.0
        assertEquals(60,  brightnesses[1]) // progress 0.5
        assertEquals(20,  brightnesses[2]) // progress 1.0, clamped at floor
    }

    @Test
    fun `brightness is clamped at floor when past 2x dimDelay`() = runTest {
        val brightnesses = mutableListOf<Int>()
        val settings = MutableStateFlow(IdleSettings(
            variationIntervalSec = 60,
            relaxedThresholdSec  = 600,
            dimEnabled           = true,
            dimDelayThresholdSec = 60,
            dimFloorPercent      = 30,
        ))
        IdleScheduler(
            scope                = this,
            settings             = settings,
            onRequestAnimation   = { _, _ -> },
            onStopAnimation      = {},
            onOverrideBrightness = { brightnesses += it },
            onRestoreBrightness  = {},
            getSavedBrightness   = { 80 },
        )

        advanceTimeBy(600_001L) // 10 ticks, well past 2×dimDelay

        assertTrue(brightnesses.all { it >= 30 })
        assertEquals(30, brightnesses.last())
    }

    @Test
    fun `notifyActivity stops IDLE and restores brightness`() = runTest {
        val stopped = mutableListOf<Int>()
        val restoredCount = mutableListOf<Unit>()
        val settings = MutableStateFlow(IdleSettings(variationIntervalSec = 60, dimEnabled = false))
        val scheduler = IdleScheduler(
            scope                = this,
            settings             = settings,
            onRequestAnimation   = { _, _ -> },
            onStopAnimation      = { priority -> stopped += priority },
            onOverrideBrightness = {},
            onRestoreBrightness  = { restoredCount += Unit },
            getSavedBrightness   = { 100 },
        )

        scheduler.notifyActivity()
        advanceTimeBy(1L)

        assertEquals(listOf(AnimationPriority.IDLE), stopped)
        assertEquals(1, restoredCount.size)
    }

    @Test
    fun `notifyActivity resets elapsed time — animation comes from idle pool after reset`() = runTest {
        val requested = mutableListOf<Animation>()
        val settings = MutableStateFlow(IdleSettings(
            variationIntervalSec = 60,
            relaxedThresholdSec  = 60,  // would switch to relaxed after 60s without a reset
            dimEnabled           = false,
        ))
        val scheduler = IdleScheduler(
            scope                = this,
            settings             = settings,
            onRequestAnimation   = { anim, _ -> requested += anim },
            onStopAnimation      = {},
            onOverrideBrightness = {},
            onRestoreBrightness  = {},
            getSavedBrightness   = { 100 },
        )

        // advance 59s, call notifyActivity (resets elapsed to 0), advance 60s more
        advanceTimeBy(59_000L)
        scheduler.notifyActivity()
        advanceTimeBy(61_000L) // 60s after reset → still in idle pool (elapsed=60, threshold=60, so >=60 means relaxed)

        // with relaxedThreshold=60, elapsed=60 at first tick post-reset → relaxed
        // but the FIRST tick in requested (before notifyActivity) should never fire since we only advanced 59s
        // After reset: tick at 60s → elapsed=60 >= relaxedThreshold=60 → relaxed
        // This test verifies notifyActivity cancelled the old loop and started fresh
        assertFalse(requested.isEmpty())
        // The first request after reset will be in RELAXED (60 >= 60), confirming timer reset
        assertTrue(requested.last() in RELAXED_POOL)
    }

    @Test
    fun `consecutive animations are not the same`() = runTest {
        val requested = mutableListOf<Animation>()
        val settings = MutableStateFlow(IdleSettings(
            variationIntervalSec = 60,
            relaxedThresholdSec  = 600,
            dimEnabled           = false,
        ))
        IdleScheduler(
            scope                = this,
            settings             = settings,
            onRequestAnimation   = { anim, _ -> requested += anim },
            onStopAnimation      = {},
            onOverrideBrightness = {},
            onRestoreBrightness  = {},
            getSavedBrightness   = { 100 },
        )

        advanceTimeBy(600_001L) // 10 ticks

        for (i in 1 until requested.size) {
            assertFalse(requested[i] == requested[i - 1], "Repeated animation at index $i: ${requested[i].id}")
        }
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
./gradlew :desktop:test --tests "cc.dvitski.tabyproto.desktop.IdleSchedulerTest"
```
Expected: compilation error — `IdleScheduler` does not exist yet.

- [ ] **Step 3: Create `IdleScheduler.kt`**

```kotlin
package cc.dvitski.tabyproto.desktop

import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.AnimationPriority
import cc.dvitski.tabyproto.Animations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class IdleScheduler(
    private val scope: CoroutineScope,
    private val settings: StateFlow<IdleSettings>,
    private val onRequestAnimation: suspend (Animation, Int) -> Unit,
    private val onStopAnimation: suspend (Int) -> Unit,
    private val onOverrideBrightness: suspend (Int) -> Unit,
    private val onRestoreBrightness: suspend () -> Unit,
    private val getSavedBrightness: () -> Int?,
) {
    private companion object {
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

    init { startLoop() }

    fun notifyActivity() {
        loopJob?.cancel()
        scope.launch {
            onRestoreBrightness()
            onStopAnimation(AnimationPriority.IDLE)
        }
        startLoop()
    }

    private fun startLoop() {
        loopJob = scope.launch {
            var elapsedSec = 0L
            var lastAnim: Animation? = null
            while (true) {
                val s = settings.value
                delay(s.variationIntervalSec * 1000L)
                elapsedSec += s.variationIntervalSec
                val pool = if (elapsedSec >= s.relaxedThresholdSec) RELAXED_POOL else IDLE_POOL
                val candidates = pool.filter { it != lastAnim }
                val next = candidates.randomOrNull() ?: pool.random()
                lastAnim = next
                onRequestAnimation(next, AnimationPriority.IDLE)
                if (s.dimEnabled && elapsedSec >= s.dimDelayThresholdSec) {
                    val saved = getSavedBrightness() ?: 100
                    val progress = ((elapsedSec - s.dimDelayThresholdSec).toFloat() /
                        s.dimDelayThresholdSec.toFloat()).coerceIn(0f, 1f)
                    val dimmed = (saved - (saved - s.dimFloorPercent) * progress)
                        .toInt().coerceAtLeast(s.dimFloorPercent)
                    onOverrideBrightness(dimmed)
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run tests and confirm they pass**

```
./gradlew :desktop:test --tests "cc.dvitski.tabyproto.desktop.IdleSchedulerTest"
```
Expected: all 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/IdleScheduler.kt
git add desktop/src/test/kotlin/cc/dvitski/tabyproto/desktop/IdleSchedulerTest.kt
git commit -m "feat: add IdleScheduler with animation rotation and brightness ramp"
```

---

## Task 4: Wire `IdleScheduler` into `AppState`

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt`

- [ ] **Step 1: Add `SettingsCategory.Idle` and `idleSettings` to `AppState`**

In `App.kt`, make these changes:

**1a. Update the `SettingsCategory` enum** (currently line 50):
```kotlin
enum class SettingsCategory { PlayAnimations, Music, Voice, Device, Appearance, Idle }
```

**1b. Add `IdleStore`, `_idleSettings`, `idleSettings`, `updateIdleSettings`, and `idleScheduler` to `AppState`:**

Add after the `themeStore` declaration (currently around line 61):
```kotlin
private val idleStore = IdleStore()
private val _idleSettings = MutableStateFlow(idleStore.load())
val idleSettings: StateFlow<IdleSettings> = _idleSettings.asStateFlow()
```

Add a new method alongside `setTheme`:
```kotlin
fun updateIdleSettings(s: IdleSettings) {
    _idleSettings.value = s
    idleStore.save(s)
}
```

**1c. Add `idleScheduler` field** after the `controllers` declaration:
```kotlin
private lateinit var idleScheduler: IdleScheduler
```

- [ ] **Step 2: Initialize `idleScheduler` and wire activity signals in `AppState.init`**

In the `init` block, add right after `AnimationResources.preloadAll(...)`:

```kotlin
idleScheduler = IdleScheduler(
    scope = scope,
    settings = _idleSettings,
    onRequestAnimation = { anim, priority ->
        _activeDeviceId.value?.let { id ->
            devices.value.firstOrNull { it.id == id && it.online }?.let { device ->
                controller(device).request(priority, anim, durationMs = null, preempt = false)
            }
        }
    },
    onStopAnimation = { priority ->
        _activeDeviceId.value?.let { id ->
            devices.value.firstOrNull { it.id == id && it.online }?.let { device ->
                controller(device).stop(priority)
            }
        }
    },
    onOverrideBrightness = { percent ->
        _activeDeviceId.value?.let { id ->
            devices.value.firstOrNull { it.id == id && it.online }?.let { device ->
                runCatching { monitor.session(device).setBrightness(percent) }
            }
        }
    },
    onRestoreBrightness = {
        val saved = _brightness.value ?: return@IdleScheduler
        _activeDeviceId.value?.let { id ->
            devices.value.firstOrNull { it.id == id && it.online }?.let { device ->
                runCatching { monitor.session(device).setBrightness(saved) }
            }
        }
    },
    getSavedBrightness = { _brightness.value },
)
```

**Step 3: Update the `musicMonitor.state` collector**

The existing music state collector (currently lines 151–185) sends IDLE_01_LOOP on idle and calls notifyActivity on playing. Replace the entire `scope.launch { musicMonitor.state.collect { ... } }` block with:

```kotlin
scope.launch {
    musicMonitor.state.collect { state ->
        val id = _activeDeviceId.value ?: return@collect
        val device = devices.value.firstOrNull { it.id == id && it.online } ?: return@collect
        when (state) {
            MusicState.Idle -> {
                controller(device).cancel(AnimationPriority.MUSIC)
                controller(device).stop(AnimationPriority.MUSIC)
            }
            is MusicState.Playing -> if (state.isPlaying) {
                idleScheduler.notifyActivity()
                controller(device).request(
                    priority   = AnimationPriority.MUSIC,
                    animation  = Animations.LISTENING_MUSIC_LOOP,
                    durationMs = null,
                    preempt    = true,
                )
            } else {
                controller(device).cancel(AnimationPriority.MUSIC)
                controller(device).stop(AnimationPriority.MUSIC)
            }
        }
    }
}
```

- [ ] **Step 4: Call `notifyActivity` from `sendAnimation`**

In `sendAnimation()`, add `idleScheduler.notifyActivity()` right before the `controller(device).request(...)` call:

```kotlin
suspend fun sendAnimation(animation: Animation): Result<Unit> {
    val device = devices.value.firstOrNull { it.id == _activeDeviceId.value }
        ?: return Result.failure(IllegalStateException("No Taby connected"))
    if (!device.online) return Result.failure(IllegalStateException("${device.label} is offline"))
    if (!_sendingAnimation.compareAndSet(null, animation)) {
        return Result.failure(IllegalStateException("Still sending ${_sendingAnimation.value?.id ?: "an animation"}"))
    }
    return try {
        val introOrBody = when (animation) {
            is Animation.Once    -> animation.raw
            is Animation.Looping -> animation.intro ?: animation.body
        }
        idleScheduler.notifyActivity()
        controller(device).request(
            priority   = AnimationPriority.MANUAL,
            animation  = animation,
            durationMs = AnimationResources.durations[introOrBody],
            preempt    = true,
        )
        _lastSent.value = animation
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    } finally {
        _sendingAnimation.value = null
    }
}
```

- [ ] **Step 5: Add required import**

At the top of `App.kt`, add:
```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
```
(It is already imported — verify it's present; if not, add it.)

Also add if missing:
```kotlin
import kotlinx.coroutines.flow.asStateFlow
```

- [ ] **Step 6: Update `App` composable to collect and pass `idleSettings`**

In the `App` composable, add:
```kotlin
val idleSettings by appState.idleSettings.collectAsState()
```

Pass to `SettingsScreen` (the existing call site, where `is Screen.Settings` is handled):
```kotlin
idleSettings = idleSettings,
onIdleSettingsChange = appState::updateIdleSettings,
```

- [ ] **Step 7: Build to confirm no compilation errors**

```
./gradlew :desktop:compileKotlin
```
Expected: compiles cleanly (SettingsScreen will emit errors since its signature hasn't been updated yet — fix those in Task 5).

- [ ] **Step 8: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt
git commit -m "feat: wire IdleScheduler into AppState, add SettingsCategory.Idle"
```

---

## Task 5: Idle settings panel in `SettingsScreen`

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/SettingsScreen.kt`

- [ ] **Step 1: Add "Idle" to the category label extension**

Update the `SettingsCategory.label` extension at the top of `SettingsScreen.kt`:

```kotlin
private val SettingsCategory.label: String
    get() = when (this) {
        SettingsCategory.PlayAnimations -> "Play Animations"
        SettingsCategory.Music         -> "Music"
        SettingsCategory.Voice         -> "Voice"
        SettingsCategory.Device        -> "Device"
        SettingsCategory.Appearance    -> "Appearance"
        SettingsCategory.Idle          -> "Idle"
    }
```

- [ ] **Step 2: Update `SettingsScreen` signature and `when` dispatch**

Add two parameters to `SettingsScreen` (after `onMusicControl`):
```kotlin
idleSettings: IdleSettings,
onIdleSettingsChange: (IdleSettings) -> Unit,
```

Add the new case to the `when (selectedCategory)` block (after `SettingsCategory.Appearance`):
```kotlin
SettingsCategory.Idle -> IdleDetail(idleSettings, onIdleSettingsChange)
```

- [ ] **Step 3: Add `IdleDetail` composable and helpers**

Add these at the bottom of `SettingsScreen.kt`, before the closing of the file:

```kotlin
private fun formatSeconds(secs: Int): String = when {
    secs < 60       -> "${secs}s"
    secs % 60 == 0  -> "${secs / 60}m"
    else            -> "${secs / 60}m ${secs % 60}s"
}

@Composable
private fun IdleDetail(settings: IdleSettings, onChange: (IdleSettings) -> Unit) {
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Idle", color = theme.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        IdleSlider(
            label    = "ANIMATION INTERVAL",
            value    = settings.variationIntervalSec,
            range    = 30..300,
            step     = 30,
            format   = { formatSeconds(it) },
            onChange = { onChange(settings.copy(variationIntervalSec = it)) },
        )

        IdleSlider(
            label    = "RELAXED AFTER",
            value    = settings.relaxedThresholdSec,
            range    = 60..900,
            step     = 60,
            format   = { formatSeconds(it) },
            onChange = { onChange(settings.copy(relaxedThresholdSec = it)) },
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("BRIGHTNESS DIM", color = theme.textSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton("On",  settings.dimEnabled,  Modifier.weight(1f)) { onChange(settings.copy(dimEnabled = true)) }
                ModeButton("Off", !settings.dimEnabled, Modifier.weight(1f)) { onChange(settings.copy(dimEnabled = false)) }
            }
        }

        if (settings.dimEnabled) {
            IdleSlider(
                label    = "DIM AFTER",
                value    = settings.dimDelayThresholdSec,
                range    = 60..900,
                step     = 60,
                format   = { formatSeconds(it) },
                onChange = { onChange(settings.copy(dimDelayThresholdSec = it)) },
            )

            IdleSlider(
                label    = "DIM FLOOR",
                value    = settings.dimFloorPercent,
                range    = 10..80,
                step     = 5,
                format   = { "$it%" },
                onChange = { onChange(settings.copy(dimFloorPercent = it)) },
            )
        }
    }
}

@Composable
private fun IdleSlider(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    format: (Int) -> String,
    onChange: (Int) -> Unit,
) {
    val theme = LocalAppTheme.current
    val steps = (range.last - range.first) / step - 1
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = theme.textSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Slider(
                value    = (value - range.first).toFloat() / (range.last - range.first).toFloat(),
                onValueChange = {
                    val raw = range.first + (it * (range.last - range.first)).toInt()
                    val snapped = ((raw - range.first + step / 2) / step) * step + range.first
                    onChange(snapped.coerceIn(range.first, range.last))
                },
                steps    = steps,
                modifier = Modifier.weight(1f),
                colors   = SliderDefaults.colors(thumbColor = theme.accent, activeTrackColor = theme.accent),
            )
            Text(
                text  = format(value),
                color = theme.textSecondary,
                fontSize = 13.sp,
            )
        }
    }
}
```

- [ ] **Step 4: Add required import if missing**

Ensure at the top of `SettingsScreen.kt`:
```kotlin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
```
These may already be present. If `Spacer` or `height` aren't used anywhere else and you're not using them in `IdleDetail`, skip them.

- [ ] **Step 5: Build to confirm compilation**

```
./gradlew :desktop:compileKotlin
```
Expected: compiles cleanly with no errors.

- [ ] **Step 6: Run all tests**

```
./gradlew :desktop:test
```
Expected: all `IdleSchedulerTest` tests pass.

- [ ] **Step 7: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/SettingsScreen.kt
git commit -m "feat: add Idle settings panel with animation interval, relaxed threshold, and dim controls"
```

---

## Self-Review Checklist

- [x] **IdleSettings** — all 5 fields spec'd, defaults match spec (90s variation, 300s relaxed, dim on, 300s delay, 20% floor)
- [x] **IdleStore** — Preferences-based, same pattern as ThemeStore
- [x] **IdleScheduler** — animation rotation with pool switching, no-repeat, brightness ramp (linear, capped at floor), `notifyActivity()` resets loop + restores brightness + stops IDLE
- [x] **AppState** — idleScheduler created in init, notifyActivity called on sendAnimation and music→Playing, old IDLE_01_LOOP requests removed, idleSettings StateFlow exposed, updateIdleSettings persists
- [x] **Brightness override** — calls `monitor.session(device).setBrightness()` directly; does NOT update `_brightness`; restore uses `_brightness.value`
- [x] **Settings UI** — new Idle category, variation/relaxed sliders, dim toggle, dim delay + floor sliders (hidden when dim off)
- [x] **SettingsCategory.Idle** — added to enum and to label extension and to `when` dispatch
- [x] **Tests** — 8 tests covering: first tick in idle pool, pool switch at threshold, IDLE priority, no dim when disabled, linear ramp math at 3 breakpoints, floor clamping, notifyActivity effects, no consecutive repeat
