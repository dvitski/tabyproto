# Animation Types Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce `RawAnimation` (wire-level enum) and `Animation` (sealed domain class with `Once`/`Looping` variants) so the API models full playback intent, and the desktop app respects loop/intro behaviour.

**Architecture:** `RawAnimation` replaces the current `Animation` enum as the device-level primitive. `Animation` (sealed) is the new domain type used at all call sites that don't need raw wire control. A top-level `Animations` object holds the complete registry. `TabySession.play(Animation)` is added alongside the existing raw overloads.

**Tech Stack:** Kotlin, Compose Multiplatform (desktop), existing `TabySession`/`TabyUsbClient`/`TabyWifiClient` infrastructure, `compose-media-player` for video.

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `api/.../TabyAnimation.kt` | Rename enum → `RawAnimation`; add `WORKSPACES_LOOP`, `WOW`, `YEAH`; update `AnimationCommand` |
| Create | `api/.../Animation.kt` | Sealed class `Animation` (Once + Looping) |
| Create | `api/.../Animations.kt` | Registry object `Animations` with all known entries |
| Modify | `api/.../TabySession.kt` | Add `play(Animation)` overload |
| Create | `api/src/test/.../AnimationsTest.kt` | Unit tests for registry + wire format |
| Modify | `desktop/.../AnimationResources.kt` | Preload intro videos for Looping entries |
| Modify | `desktop/.../ThumbnailCache.kt` | Update to `Animation` sealed type |
| Modify | `desktop/.../App.kt` | Switch `Animation.entries` → `Animations.all` throughout |
| Modify | `desktop/.../SettingsScreen.kt` | Import update only |
| Modify | `desktop/.../AnimationGrid.kt` | Import update only |
| Modify | `desktop/.../AnimationCell.kt` | Use `animation.displayName` for label |
| Modify | `desktop/.../Sidebar.kt` | Handle Once vs Looping (with intro transition) |

---

### Task 1: Rename `Animation` → `RawAnimation`, add missing entries

**Files:**
- Modify: `api/src/main/kotlin/cc/dvitski/tabyproto/TabyAnimation.kt`

- [ ] **Step 1: Replace the file contents**

```kotlin
package cc.dvitski.tabyproto

enum class RawAnimation(val id: String) {
    ANGRY_01_LOOP("angry_01_loop"),
    ANGRY_02_LOOP("angry_02_loop"),
    BASKETBALL_DUNK("basketball_dunk"),
    BASKETBALL_THROW("basketball_throw"),
    BLUSH("blush"),
    BOXING("boxing"),
    BREAK_START("break_start"),
    BUSY_LOOP("busy_loop"),
    CALENDAR_IN("calendar_in"),
    CALENDAR_LOOP("calendar_loop"),
    CIRCLE("circle"),
    CLAUDE_IN("claude_in"),
    CLAUDE_LOOP("claude_loop"),
    CODEX_IN("codex_in"),
    CODEX_LOOP("codex_loop"),
    CONFIRMATION("confirmation"),
    COPY_PASTE("copy_paste"),
    CREATING_TASK_LOOP("creating_task_loop"),
    DAY_PLANNED("day_planned"),
    DELETE_01("delete_01"),
    DISAPPOINTED("disappointed"),
    DIZZY_LOOP("dizzy_loop"),
    DRINK_WATER("drink_water"),
    F1_CAR("f1_car"),
    FISHING_LONG("fishing_long"),
    FISHING_SHORT("fishing_short"),
    FLOWER_GROW("flower_grow"),
    HELLO_ANNOYED("hello_annoyed"),
    HELLO_DISAPPOINTED("hello_disappointed"),
    IDLE_01_LOOP("idle_01_loop"),
    IDLE_02_LOOP("idle_02_loop"),
    IDLE_VARIATION_LOOP("idle_variation_loop"),
    LISTENING_IN("listening_in"),
    LISTENING_LOOP("listening_loop"),
    LISTENING_MUSIC_LOOP("listening_music_loop"),
    LOCKIN("lockin"),
    LOVE_01("love_01"),
    NO("no"),
    PERFECT_DAY_01("perfect_day_01"),
    PERFECT_DAY_01_SIMPLE("perfect_day_01_simple"),
    PERFECT_DAY_02("perfect_day_02"),
    PERFECT_DAY_03("perfect_day_03"),
    POSTURE_CHECK("posture_check"),
    RELAXING_01_LOOP("relaxing_01_loop"),
    RELAXING_COUCH_IN("relaxing_couch_in"),
    RELAXING_COUCH_LOOP("relaxing_couch_loop"),
    REVIEW("review"),
    SLEEPING_LOOP("sleeping_loop"),
    SQUARE("square"),
    STARTUP("startup"),
    STRETCHING("stretching"),
    TABY_RESPONSE_READY_IN("taby_response_ready_in"),
    TABY_RESPONSE_READY_LOOP("taby_response_ready_loop"),
    TALKING_DEFAULT_LOOP("talking_default_loop"),
    TALKING_MAN_LOOP("talking_man_loop"),
    TASK_COMPLETED("task_completed"),
    TASK_CREATED("task_created"),
    TASK_PAGE("task_page"),
    THUMBS_UP("thumbs_up"),
    TROPHY("trophy"),
    TURN_OFF_REDDIT("turn_off_reddit"),
    TURN_OFF_SCROLL("turn_off_scroll"),
    TURN_OFF_TV("turn_off_tv"),
    WAITING_01("waiting_01"),
    WORKING_IN("working_in"),
    WORKING_LAPTOP_BORED_LOOP("working_laptop_bored_loop"),
    WORKING_LAPTOP_EXCITED_LOOP("working_laptop_excited_loop"),
    WORKING_LAPTOP_IN("working_laptop_in"),
    WORKING_LAPTOP_LOOP("working_laptop_loop"),
    WORKING_LAPTOP_NORMAL_LOOP("working_laptop_normal_loop"),
    WORKING_LOOP("working_loop"),
    WORKSPACES_IN("workspaces_in"),
    WORKSPACES_LOOP("workspaces_loop"),
    WOW("wow"),
    YEAH("yeah"),
}

data class AnimationCommand(val from: RawAnimation, val to: RawAnimation) {
    internal fun toWireString() = "${from.id}>${to.id}"
}

infix fun RawAnimation.then(next: RawAnimation) = AnimationCommand(this, next)
```

- [ ] **Step 2: Verify the project still compiles**

Run: `./gradlew :api:compileKotlin`

Expected: compilation errors on all sites that still import `cc.dvitski.tabyproto.Animation` — that's expected; they'll be fixed in later tasks. If there are errors only in the `api` module itself (not `desktop`), fix them now before continuing.

---

### Task 2: Create `Animation` sealed class

**Files:**
- Create: `api/src/main/kotlin/cc/dvitski/tabyproto/Animation.kt`

- [ ] **Step 1: Create the file**

```kotlin
package cc.dvitski.tabyproto

sealed class Animation {
    abstract val id: String
    abstract val displayName: String

    /** Plays once and stops. */
    data class Once(
        override val id: String,
        override val displayName: String,
        val raw: RawAnimation,
    ) : Animation()

    /** Plays an optional intro, then loops the body indefinitely until stopped. */
    data class Looping(
        override val id: String,
        override val displayName: String,
        val intro: RawAnimation?,
        val body: RawAnimation,
    ) : Animation()
}
```

- [ ] **Step 2: Compile api module**

Run: `./gradlew :api:compileKotlin`

Expected: no errors within the `api` module itself (desktop errors are still expected).

---

### Task 3: Create `Animations` registry

**Files:**
- Create: `api/src/main/kotlin/cc/dvitski/tabyproto/Animations.kt`
- Create: `api/src/test/kotlin/cc/dvitski/tabyproto/AnimationsTest.kt`

- [ ] **Step 1: Write the failing tests first**

```kotlin
package cc.dvitski.tabyproto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnimationsTest {

    @Test
    fun `byId returns null for unknown id`() {
        assertNull(Animations.byId("does_not_exist"))
    }

    @Test
    fun `busy_loop is Looping without intro`() {
        val anim = Animations.byId("busy_loop")
        assertNotNull(anim)
        assertIs<Animation.Looping>(anim)
        assertNull(anim.intro)
        assertEquals(RawAnimation.BUSY_LOOP, anim.body)
    }

    @Test
    fun `calendar_loop is Looping with calendar_in as intro`() {
        val anim = Animations.byId("calendar_loop")
        assertNotNull(anim)
        assertIs<Animation.Looping>(anim)
        assertEquals(RawAnimation.CALENDAR_IN, anim.intro)
        assertEquals(RawAnimation.CALENDAR_LOOP, anim.body)
    }

    @Test
    fun `task_completed is Once`() {
        val anim = Animations.byId("task_completed")
        assertNotNull(anim)
        assertIs<Animation.Once>(anim)
        assertEquals(RawAnimation.TASK_COMPLETED, anim.raw)
    }

    @Test
    fun `all entries have unique ids`() {
        val ids = Animations.all.map { it.id }
        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun `no entry in all uses an _in id as its canonical id`() {
        assertTrue(Animations.all.none { it.id.endsWith("_in") })
    }

    @Test
    fun `intro transition wire format is correct`() {
        val looping = Animations.byId("calendar_loop") as Animation.Looping
        val cmd = AnimationCommand(looping.intro!!, looping.body)
        assertEquals("calendar_in>calendar_loop", cmd.toWireString())
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

Run: `./gradlew :api:test --tests "cc.dvitski.tabyproto.AnimationsTest"`

Expected: compilation failure — `Animations` not defined yet.

- [ ] **Step 3: Create `Animations.kt`**

```kotlin
package cc.dvitski.tabyproto

object Animations {

    // ── Play-once animations ───────────────────────────────────────────────────

    val BASKETBALL_DUNK    = Animation.Once("basketball_dunk",    "Basketball Dunk",    RawAnimation.BASKETBALL_DUNK)
    val BASKETBALL_THROW   = Animation.Once("basketball_throw",   "Basketball Throw",   RawAnimation.BASKETBALL_THROW)
    val BLUSH              = Animation.Once("blush",              "Blush",              RawAnimation.BLUSH)
    val BOXING             = Animation.Once("boxing",             "Boxing",             RawAnimation.BOXING)
    val BREAK_START        = Animation.Once("break_start",        "Break Start",        RawAnimation.BREAK_START)
    val CIRCLE             = Animation.Once("circle",             "Circle",             RawAnimation.CIRCLE)
    val CONFIRMATION       = Animation.Once("confirmation",       "Confirmation",       RawAnimation.CONFIRMATION)
    val COPY_PASTE         = Animation.Once("copy_paste",         "Copy Paste",         RawAnimation.COPY_PASTE)
    val DAY_PLANNED        = Animation.Once("day_planned",        "Day Planned",        RawAnimation.DAY_PLANNED)
    val DELETE_01          = Animation.Once("delete_01",          "Delete 01",          RawAnimation.DELETE_01)
    val DISAPPOINTED       = Animation.Once("disappointed",       "Disappointed",       RawAnimation.DISAPPOINTED)
    val DRINK_WATER        = Animation.Once("drink_water",        "Drink Water",        RawAnimation.DRINK_WATER)
    val F1_CAR             = Animation.Once("f1_car",             "F1 Car",             RawAnimation.F1_CAR)
    val FISHING_LONG       = Animation.Once("fishing_long",       "Fishing Long",       RawAnimation.FISHING_LONG)
    val FISHING_SHORT      = Animation.Once("fishing_short",      "Fishing Short",      RawAnimation.FISHING_SHORT)
    val FLOWER_GROW        = Animation.Once("flower_grow",        "Flower Grow",        RawAnimation.FLOWER_GROW)
    val HELLO_ANNOYED      = Animation.Once("hello_annoyed",      "Hello Annoyed",      RawAnimation.HELLO_ANNOYED)
    val HELLO_DISAPPOINTED = Animation.Once("hello_disappointed", "Hello Disappointed", RawAnimation.HELLO_DISAPPOINTED)
    val LOCKIN             = Animation.Once("lockin",             "Lockin",             RawAnimation.LOCKIN)
    val LOVE_01            = Animation.Once("love_01",            "Love 01",            RawAnimation.LOVE_01)
    val NO                 = Animation.Once("no",                 "No",                 RawAnimation.NO)
    val PERFECT_DAY_01     = Animation.Once("perfect_day_01",     "Perfect Day 01",     RawAnimation.PERFECT_DAY_01)
    val PERFECT_DAY_01_SIMPLE = Animation.Once("perfect_day_01_simple", "Perfect Day 01 Simple", RawAnimation.PERFECT_DAY_01_SIMPLE)
    val PERFECT_DAY_02     = Animation.Once("perfect_day_02",     "Perfect Day 02",     RawAnimation.PERFECT_DAY_02)
    val PERFECT_DAY_03     = Animation.Once("perfect_day_03",     "Perfect Day 03",     RawAnimation.PERFECT_DAY_03)
    val POSTURE_CHECK      = Animation.Once("posture_check",      "Posture Check",      RawAnimation.POSTURE_CHECK)
    val REVIEW             = Animation.Once("review",             "Review",             RawAnimation.REVIEW)
    val SQUARE             = Animation.Once("square",             "Square",             RawAnimation.SQUARE)
    val STARTUP            = Animation.Once("startup",            "Startup",            RawAnimation.STARTUP)
    val STRETCHING         = Animation.Once("stretching",         "Stretching",         RawAnimation.STRETCHING)
    val TASK_COMPLETED     = Animation.Once("task_completed",     "Task Completed",     RawAnimation.TASK_COMPLETED)
    val TASK_CREATED       = Animation.Once("task_created",       "Task Created",       RawAnimation.TASK_CREATED)
    val TASK_PAGE          = Animation.Once("task_page",          "Task Page",          RawAnimation.TASK_PAGE)
    val THUMBS_UP          = Animation.Once("thumbs_up",          "Thumbs Up",          RawAnimation.THUMBS_UP)
    val TROPHY             = Animation.Once("trophy",             "Trophy",             RawAnimation.TROPHY)
    val TURN_OFF_REDDIT    = Animation.Once("turn_off_reddit",    "Turn Off Reddit",    RawAnimation.TURN_OFF_REDDIT)
    val TURN_OFF_SCROLL    = Animation.Once("turn_off_scroll",    "Turn Off Scroll",    RawAnimation.TURN_OFF_SCROLL)
    val TURN_OFF_TV        = Animation.Once("turn_off_tv",        "Turn Off Tv",        RawAnimation.TURN_OFF_TV)
    val WAITING_01         = Animation.Once("waiting_01",         "Waiting 01",         RawAnimation.WAITING_01)
    val WOW                = Animation.Once("wow",                "Wow",                RawAnimation.WOW)
    val YEAH               = Animation.Once("yeah",               "Yeah",               RawAnimation.YEAH)

    // ── Looping animations (no intro) ──────────────────────────────────────────

    val ANGRY_01_LOOP            = Animation.Looping("angry_01_loop",            "Angry 01 Loop",            intro = null, body = RawAnimation.ANGRY_01_LOOP)
    val ANGRY_02_LOOP            = Animation.Looping("angry_02_loop",            "Angry 02 Loop",            intro = null, body = RawAnimation.ANGRY_02_LOOP)
    val BUSY_LOOP                = Animation.Looping("busy_loop",                "Busy Loop",                intro = null, body = RawAnimation.BUSY_LOOP)
    val CREATING_TASK_LOOP       = Animation.Looping("creating_task_loop",       "Creating Task Loop",       intro = null, body = RawAnimation.CREATING_TASK_LOOP)
    val DIZZY_LOOP               = Animation.Looping("dizzy_loop",               "Dizzy Loop",               intro = null, body = RawAnimation.DIZZY_LOOP)
    val IDLE_01_LOOP             = Animation.Looping("idle_01_loop",             "Idle 01 Loop",             intro = null, body = RawAnimation.IDLE_01_LOOP)
    val IDLE_02_LOOP             = Animation.Looping("idle_02_loop",             "Idle 02 Loop",             intro = null, body = RawAnimation.IDLE_02_LOOP)
    val IDLE_VARIATION_LOOP      = Animation.Looping("idle_variation_loop",      "Idle Variation Loop",      intro = null, body = RawAnimation.IDLE_VARIATION_LOOP)
    val LISTENING_LOOP           = Animation.Looping("listening_loop",           "Listening Loop",           intro = null, body = RawAnimation.LISTENING_LOOP)
    val LISTENING_MUSIC_LOOP     = Animation.Looping("listening_music_loop",     "Listening Music Loop",     intro = null, body = RawAnimation.LISTENING_MUSIC_LOOP)
    val RELAXING_01_LOOP         = Animation.Looping("relaxing_01_loop",         "Relaxing 01 Loop",         intro = null, body = RawAnimation.RELAXING_01_LOOP)
    val SLEEPING_LOOP            = Animation.Looping("sleeping_loop",            "Sleeping Loop",            intro = null, body = RawAnimation.SLEEPING_LOOP)
    val TALKING_DEFAULT_LOOP     = Animation.Looping("talking_default_loop",     "Talking Default Loop",     intro = null, body = RawAnimation.TALKING_DEFAULT_LOOP)
    val TALKING_MAN_LOOP         = Animation.Looping("talking_man_loop",         "Talking Man Loop",         intro = null, body = RawAnimation.TALKING_MAN_LOOP)
    val WORKING_LAPTOP_BORED_LOOP   = Animation.Looping("working_laptop_bored_loop",   "Working Laptop Bored Loop",   intro = null, body = RawAnimation.WORKING_LAPTOP_BORED_LOOP)
    val WORKING_LAPTOP_EXCITED_LOOP = Animation.Looping("working_laptop_excited_loop", "Working Laptop Excited Loop", intro = null, body = RawAnimation.WORKING_LAPTOP_EXCITED_LOOP)
    val WORKING_LAPTOP_NORMAL_LOOP  = Animation.Looping("working_laptop_normal_loop",  "Working Laptop Normal Loop",  intro = null, body = RawAnimation.WORKING_LAPTOP_NORMAL_LOOP)

    // ── Looping animations (with intro) ────────────────────────────────────────

    val CALENDAR_LOOP           = Animation.Looping("calendar_loop",           "Calendar Loop",           intro = RawAnimation.CALENDAR_IN,           body = RawAnimation.CALENDAR_LOOP)
    val CLAUDE_LOOP             = Animation.Looping("claude_loop",             "Claude Loop",             intro = RawAnimation.CLAUDE_IN,             body = RawAnimation.CLAUDE_LOOP)
    val CODEX_LOOP              = Animation.Looping("codex_loop",              "Codex Loop",              intro = RawAnimation.CODEX_IN,              body = RawAnimation.CODEX_LOOP)
    val LISTENING_IN_LOOP       = Animation.Looping("listening_loop",          "Listening Loop",          intro = RawAnimation.LISTENING_IN,          body = RawAnimation.LISTENING_LOOP)
    val RELAXING_COUCH_LOOP     = Animation.Looping("relaxing_couch_loop",     "Relaxing Couch Loop",     intro = RawAnimation.RELAXING_COUCH_IN,     body = RawAnimation.RELAXING_COUCH_LOOP)
    val TABY_RESPONSE_READY_LOOP = Animation.Looping("taby_response_ready_loop", "Taby Response Ready Loop", intro = RawAnimation.TABY_RESPONSE_READY_IN, body = RawAnimation.TABY_RESPONSE_READY_LOOP)
    val WORKING_LOOP            = Animation.Looping("working_loop",            "Working Loop",            intro = RawAnimation.WORKING_IN,            body = RawAnimation.WORKING_LOOP)
    val WORKING_LAPTOP_LOOP     = Animation.Looping("working_laptop_loop",     "Working Laptop Loop",     intro = RawAnimation.WORKING_LAPTOP_IN,     body = RawAnimation.WORKING_LAPTOP_LOOP)
    val WORKSPACES_LOOP         = Animation.Looping("workspaces_loop",         "Folders Loop",            intro = RawAnimation.WORKSPACES_IN,         body = RawAnimation.WORKSPACES_LOOP)

    // ── Registry ───────────────────────────────────────────────────────────────

    val all: List<Animation> = listOf(
        // once
        BASKETBALL_DUNK, BASKETBALL_THROW, BLUSH, BOXING, BREAK_START, CIRCLE,
        CONFIRMATION, COPY_PASTE, DAY_PLANNED, DELETE_01, DISAPPOINTED, DRINK_WATER,
        F1_CAR, FISHING_LONG, FISHING_SHORT, FLOWER_GROW, HELLO_ANNOYED, HELLO_DISAPPOINTED,
        LOCKIN, LOVE_01, NO, PERFECT_DAY_01, PERFECT_DAY_01_SIMPLE, PERFECT_DAY_02,
        PERFECT_DAY_03, POSTURE_CHECK, REVIEW, SQUARE, STARTUP, STRETCHING,
        TASK_COMPLETED, TASK_CREATED, TASK_PAGE, THUMBS_UP, TROPHY,
        TURN_OFF_REDDIT, TURN_OFF_SCROLL, TURN_OFF_TV, WAITING_01, WOW, YEAH,
        // looping (no intro)
        ANGRY_01_LOOP, ANGRY_02_LOOP, BUSY_LOOP, CREATING_TASK_LOOP, DIZZY_LOOP,
        IDLE_01_LOOP, IDLE_02_LOOP, IDLE_VARIATION_LOOP, LISTENING_LOOP, LISTENING_MUSIC_LOOP,
        RELAXING_01_LOOP, SLEEPING_LOOP, TALKING_DEFAULT_LOOP, TALKING_MAN_LOOP,
        WORKING_LAPTOP_BORED_LOOP, WORKING_LAPTOP_EXCITED_LOOP, WORKING_LAPTOP_NORMAL_LOOP,
        // looping (with intro)
        CALENDAR_LOOP, CLAUDE_LOOP, CODEX_LOOP, LISTENING_IN_LOOP, RELAXING_COUCH_LOOP,
        TABY_RESPONSE_READY_LOOP, WORKING_LOOP, WORKING_LAPTOP_LOOP, WORKSPACES_LOOP,
    )

    private val byIdMap: Map<String, Animation> = all.associateBy { it.id }

    fun byId(id: String): Animation? = byIdMap[id]
}
```

> **Note:** `LISTENING_LOOP` appears twice above — once as a no-intro looping (standalone) and once with `LISTENING_IN` as intro (`LISTENING_IN_LOOP`). These are two distinct `Animation` objects with the same `id` (`listening_loop`) — but `all` must not contain both. The correct entry for `all` is `LISTENING_IN_LOOP` (with intro), matching the manifest's `nextAnimationId`. Remove the standalone `LISTENING_LOOP` constant or rename it.
>
> **Fix before saving:** In the registry, `LISTENING_LOOP` (no intro) conflicts with `LISTENING_IN_LOOP` (with intro) — both have `id = "listening_loop"`. Remove the standalone `LISTENING_LOOP` constant entirely; `LISTENING_IN_LOOP` (renamed simply to `LISTENING_LOOP`) is the correct entry. Apply the same check for any other animation that has both a standalone loop and an `_in` entry — there are none others.
>
> **Corrected constants section (replace the two LISTENING entries with):**
> ```kotlin
> val LISTENING_LOOP = Animation.Looping("listening_loop", "Listening Loop", intro = RawAnimation.LISTENING_IN, body = RawAnimation.LISTENING_LOOP)
> ```
> And in `all`, use `LISTENING_LOOP` (not `LISTENING_IN_LOOP`).

- [ ] **Step 4: Run tests**

Run: `./gradlew :api:test --tests "cc.dvitski.tabyproto.AnimationsTest"`

Expected: all 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/kotlin/cc/dvitski/tabyproto/TabyAnimation.kt \
        api/src/main/kotlin/cc/dvitski/tabyproto/Animation.kt \
        api/src/main/kotlin/cc/dvitski/tabyproto/Animations.kt \
        api/src/test/kotlin/cc/dvitski/tabyproto/AnimationsTest.kt
git commit -m "feat(api): add RawAnimation enum, Animation sealed class, and Animations registry"
```

---

### Task 4: Add `play(Animation)` overload to `TabySession`

**Files:**
- Modify: `api/src/main/kotlin/cc/dvitski/tabyproto/TabySession.kt`

- [ ] **Step 1: Update the interface and both implementations**

Replace the full file:

```kotlin
package cc.dvitski.tabyproto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

interface TabySession : Closeable {
    val transport: TabyTransport
    val device: DeviceInfo

    suspend fun play(animation: Animation): CommandResult
    suspend fun play(raw: RawAnimation): CommandResult
    suspend fun play(command: AnimationCommand): CommandResult
    suspend fun setBrightness(percent: Int): CommandResult
    suspend fun sendRaw(command: String): CommandResult

    override fun close()
}

internal class UsbTabySession(
    private val usbSession: TabyUsbClient.TabyUsbSession,
    override val device: DeviceInfo,
) : TabySession {
    override val transport = TabyTransport.USB

    override suspend fun play(animation: Animation): CommandResult = when (animation) {
        is Animation.Once    -> play(animation.raw)
        is Animation.Looping -> when (val intro = animation.intro) {
            null -> play(animation.body)
            else -> play(AnimationCommand(intro, animation.body))
        }
    }

    override suspend fun play(raw: RawAnimation) = sendRaw(raw.id)
    override suspend fun play(command: AnimationCommand) = sendRaw(command.toWireString())

    override suspend fun setBrightness(percent: Int): CommandResult {
        require(percent in 0..100) { "Brightness must be 0–100, got $percent" }
        return sendRaw("BRIGHTNESS $percent")
    }

    override suspend fun sendRaw(command: String): CommandResult =
        withContext(Dispatchers.IO) { usbSession.sendCommand(command) }

    override fun close() = usbSession.close()
}

internal class WifiTabySession(
    private val wifiClient: TabyWifiClient,
    override val device: DeviceInfo,
) : TabySession {
    override val transport = TabyTransport.WIFI

    override suspend fun play(animation: Animation): CommandResult = when (animation) {
        is Animation.Once    -> play(animation.raw)
        is Animation.Looping -> when (val intro = animation.intro) {
            null -> play(animation.body)
            else -> play(AnimationCommand(intro, animation.body))
        }
    }

    override suspend fun play(raw: RawAnimation) = sendRaw(raw.id)
    override suspend fun play(command: AnimationCommand) = sendRaw(command.toWireString())

    override suspend fun setBrightness(percent: Int): CommandResult {
        require(percent in 0..100) { "Brightness must be 0–100, got $percent" }
        return sendRaw("BRIGHTNESS $percent")
    }

    override suspend fun sendRaw(command: String) = wifiClient.sendCommand(command)

    override fun close() {}
}
```

- [ ] **Step 2: Compile the api module**

Run: `./gradlew :api:compileKotlin`

Expected: clean (desktop errors still expected).

- [ ] **Step 3: Commit**

```bash
git add api/src/main/kotlin/cc/dvitski/tabyproto/TabySession.kt
git commit -m "feat(api): add play(Animation) overload to TabySession"
```

---

### Task 5: Update `AnimationResources` — preload intro videos

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/AnimationResources.kt`

- [ ] **Step 1: Update `preloadAll` to also preload intro videos**

```kotlin
package cc.dvitski.tabyproto.desktop

import cc.dvitski.tabyproto.Animation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object AnimationResources {

    private val tempDir = File(System.getProperty("java.io.tmpdir"), "taby-desktop").also { it.mkdirs() }

    fun preloadAll(animations: List<Animation>, scope: kotlinx.coroutines.CoroutineScope) {
        animations.forEach { animation ->
            scope.launch(Dispatchers.IO) {
                videoPath(animation.id)
                if (animation is Animation.Looping && animation.intro != null) {
                    videoPath(animation.intro.id)
                }
            }
        }
    }

    suspend fun videoPath(animationId: String): String? = withContext(Dispatchers.IO) {
        val dest = File(tempDir, "$animationId.mp4")
        if (!dest.exists()) {
            val stream = AnimationResources::class.java.getResourceAsStream("/anim/$animationId.mp4")
                ?: return@withContext null
            try {
                stream.use { it.copyTo(dest.outputStream()) }
            } catch (_: Exception) {
                dest.delete()
                return@withContext null
            }
        }
        dest.absolutePath
    }
}
```

---

### Task 6: Update `ThumbnailCache` to use `Animation` (sealed)

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/ThumbnailCache.kt`

- [ ] **Step 1: Replace the import and update type references**

```kotlin
package cc.dvitski.tabyproto.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import cc.dvitski.tabyproto.Animation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.util.concurrent.ConcurrentHashMap

class ThumbnailCache {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val cache = ConcurrentHashMap<Animation, ImageBitmap?>()

    private val _loadedCount = MutableStateFlow(0)
    val loadedCount: StateFlow<Int> = _loadedCount.asStateFlow()

    fun preloadAll(animations: List<Animation>) {
        animations.forEach { animation ->
            scope.launch {
                cache[animation] = extractFrame(animation.id)
                _loadedCount.update { it + 1 }
            }
        }
    }

    fun thumbnailFor(animation: Animation): ImageBitmap? = cache[animation]

    private fun extractFrame(animationId: String): ImageBitmap? {
        val resourcePath = "/anim/$animationId.mp4"
        val inputStream = ThumbnailCache::class.java.getResourceAsStream(resourcePath)
            ?: return null
        return try {
            FFmpegFrameGrabber(inputStream).use { grabber ->
                grabber.start()
                val frame = grabber.grabImage() ?: return null
                val bufferedImage = Java2DFrameConverter().convert(frame) ?: return null
                bufferedImage.toComposeImageBitmap()
            }
        } catch (_: Exception) {
            null
        } finally {
            inputStream.close()
        }
    }
}
```

---

### Task 7: Update `App.kt`

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt`

The `Animation` import now resolves to the sealed class. Update all references to `Animation.entries` and fix the `sendAnimation` signature.

- [ ] **Step 1: Update imports and all `Animation.entries` references**

Change the import block at the top:
```kotlin
import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.Animations
import cc.dvitski.tabyproto.TabyDevice
import cc.dvitski.tabyproto.TabyDeviceMonitor
import cc.dvitski.tabyproto.TabyTransport
```

Line 70 — `totalAnimations`:
```kotlin
val totalAnimations: Int = Animations.all.size
```

Lines 99–100 — `init` block preload calls:
```kotlin
thumbnailCache.preloadAll(Animations.all)
AnimationResources.preloadAll(Animations.all, scope)
```

Line 206 — filtered animations in Settings screen:
```kotlin
val filtered = Animations.all.filter { animation ->
    query.isBlank() || animation.id.contains(query, ignoreCase = true)
}
```

The `sendAnimation` signature on line 120 is already `suspend fun sendAnimation(animation: Animation)` — no change needed since `Animation` now refers to the sealed class. The `_lastSent` and `_sendingAnimation` flow types are `Animation?` — also correct.

- [ ] **Step 2: Update `SettingsScreen.kt` import**

In `SettingsScreen.kt`, the import `cc.dvitski.tabyproto.Animation` now resolves to the sealed class. No other changes needed there — the function signature `animations: List<Animation>` and `sendingAnimation: Animation?` are unchanged.

- [ ] **Step 3: Update `AnimationGrid.kt` import**

In `AnimationGrid.kt`, the import `cc.dvitski.tabyproto.Animation` now resolves to the sealed class. Function signature `animations: List<Animation>` is unchanged.

- [ ] **Step 4: Compile the desktop module**

Run: `./gradlew :desktop:compileKotlin`

Expected: errors only in `AnimationCell.kt` and `Sidebar.kt` — fix those in the next tasks.

---

### Task 8: Update `AnimationCell.kt` — use `displayName`

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/AnimationCell.kt`

- [ ] **Step 1: Update import and label text**

Change the import:
```kotlin
import cc.dvitski.tabyproto.Animation
```
(already correct — sealed class)

On line 116, change the label from `animation.id` to `animation.displayName`:
```kotlin
Text(
    text = animation.displayName,
    fontSize = 11.sp,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    textAlign = TextAlign.Center,
    color = theme.textPrimary,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
)
```

The `AnimationResources.videoPath(animation.id)` call on line 60 stays as-is — `animation.id` is the body/canonical ID, which maps to the correct video file.

---

### Task 9: Update `Sidebar.kt` — Once vs Looping playback with intro transition

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Sidebar.kt`

- [ ] **Step 1: Update the `LaunchedEffect(lastSent)` block and add intro-phase state**

The `lastSent` parameter type is already `Animation?` — now the sealed class. Replace the two `LaunchedEffect` blocks and add a `playingIntro` state variable:

```kotlin
val playerState = rememberVideoPlayerState()
var videoVisible by remember { mutableStateOf(false) }
var videoStarted by remember { mutableStateOf(false) }
var playingIntro by remember { mutableStateOf(false) }

DisposableEffect(playerState) { onDispose { playerState.dispose() } }

LaunchedEffect(lastSent) {
    videoStarted = false
    videoVisible = false
    playingIntro = false
    playerState.pause()
    if (lastSent != null) {
        when (lastSent) {
            is Animation.Once -> {
                val path = AnimationResources.videoPath(lastSent.raw.id) ?: return@LaunchedEffect
                videoVisible = true
                playerState.loop = false
                playerState.openUri(path)
            }
            is Animation.Looping -> {
                val intro = lastSent.intro
                if (intro != null) {
                    val introPath = AnimationResources.videoPath(intro.id) ?: return@LaunchedEffect
                    videoVisible = true
                    playingIntro = true
                    playerState.loop = false
                    playerState.openUri(introPath)
                } else {
                    val bodyPath = AnimationResources.videoPath(lastSent.body.id) ?: return@LaunchedEffect
                    videoVisible = true
                    playerState.loop = true
                    playerState.openUri(bodyPath)
                }
            }
        }
    }
}

LaunchedEffect(playerState.isPlaying, playerState.isLoading) {
    when {
        playerState.isPlaying && !playerState.isLoading -> videoStarted = true
        videoStarted && !playerState.isPlaying && !playerState.isLoading -> {
            val current = lastSent
            if (playingIntro && current is Animation.Looping) {
                val bodyPath = AnimationResources.videoPath(current.body.id)
                if (bodyPath != null) {
                    playingIntro = false
                    videoStarted = false
                    playerState.loop = true
                    playerState.openUri(bodyPath)
                    return@LaunchedEffect
                }
            }
            videoVisible = false
            videoStarted = false
            playingIntro = false
        }
    }
}
```

- [ ] **Step 2: Compile the desktop module**

Run: `./gradlew :desktop:compileKotlin`

Expected: clean.

- [ ] **Step 3: Run all tests**

Run: `./gradlew test`

Expected: all tests pass.

- [ ] **Step 4: Final commit**

```bash
git add api/src/main/kotlin/cc/dvitski/tabyproto/desktop/ \
        desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/
git commit -m "feat(desktop): update all call sites to Animation sealed class, add intro→loop sidebar transition"
```

---

## Self-Review Checklist

- [x] **Spec coverage:** `RawAnimation` enum ✓ · `Animation` sealed class ✓ · `Animations` registry ✓ · `TabySession.play(Animation)` ✓ · `AnimationCommand` uses `RawAnimation` ✓ · desktop Sidebar loop/intro ✓ · missing entries added ✓ · `displayName` added ✓
- [x] **Placeholder scan:** No TBD/TODO entries. All code steps are complete.
- [x] **Type consistency:** `RawAnimation` used in `AnimationCommand`/`TabyAnimation.kt`. `Animation` sealed used in `TabySession`, `App`, `ThumbnailCache`, `AnimationCell`, `Sidebar`. `Animations.all` replaces `Animation.entries` throughout.
- [x] **Naming conflict resolved:** `LISTENING_LOOP` duplicate addressed in Task 3 note.
- [x] **`app:Main.kt`:** Not modified — it only constructs `AppState`, which compiles through the updated `App.kt`.
