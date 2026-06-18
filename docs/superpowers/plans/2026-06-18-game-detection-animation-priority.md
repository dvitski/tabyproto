# Game Detection & Animation Priority Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Detect when the user is playing a game on Windows (process-name list + fullscreen-window heuristic) and drive a new `GAME` animation priority on the active Taby device, with an enable/disable settings toggle for both game and (retrofitted) music detection.

**Architecture:** A new `GameMonitor` (desktop module) polls a `GamePoller` every 3s; `GamePoller` shells out to a small Win32 P/Invoke C# helper (compiled at runtime via `csc.exe`, same mechanism as `SmtcPoller`) that reports the foreground window's process name and whether it's fullscreen. A pure `KnownGames.classify()` function turns that into a `GameState`. `AppState`/`App.kt` wires `GameState` into `AnimationController.request()` at a new `AnimationPriority.GAME` (55, between `MUSIC=50` and `VOICE=60`), mirroring the existing `MusicState` → `AnimationPriority.MUSIC` wiring exactly. A new `DetectionSettingsStore` (java `Preferences`, same pattern as `ThemeStore`/`IdleStore`) persists on/off toggles for both monitors, surfaced in `SettingsScreen` via the existing `TogglePill` component.

**Tech Stack:** Kotlin/JVM 21, Gradle multi-module (`api`, `desktop`), Compose Desktop, `kotlinx.coroutines` (`StateFlow`), `kotlinx.serialization.json`, Windows `csc.exe` (.NET Framework, ships with Windows) for runtime-compiled Win32 P/Invoke helper, `kotlin.test`/JUnit5 for tests.

## Global Constraints

- Default branch is `latest`, not `master` — after committing, merge `master` → `latest` and push `latest` (per `CLAUDE.md`). This plan assumes commits land directly on `latest` (current branch), so no merge step is needed unless work happens on a different branch.
- `api` module stays UI-free and device-communication-only; `GameMonitor`/`GamePoller`/`KnownGames`/`DetectionSettingsStore`/UI all belong in `desktop`, only `AnimationPriority.GAME` goes in `api`.
- Follow the existing `MusicMonitor`/`MusicState`/`SmtcPoller` pattern exactly where an equivalent already exists — don't invent a different shape for the analogous game-detection piece.
- `SmtcPoller`, `MusicMonitor`, `ThemeStore`, `IdleStore`, `GeneralStore` have no unit tests in this codebase (OS-dependent subprocess calls / simple `Preferences` wrappers) — `GamePoller`, `GameMonitor`, and `DetectionSettingsStore` follow that same convention. Only the pure `KnownGames.classify()` logic and the `AnimationPriority` ordering get automated tests; everything else is verified manually by running the desktop app.

---

### Task 1: `AnimationPriority.GAME` constant

**Files:**
- Modify: `api/src/main/kotlin/cc/dvitski/tabyproto/AnimationPriority.kt`
- Test: `api/src/test/kotlin/cc/dvitski/tabyproto/AnimationPriorityTest.kt`

**Interfaces:**
- Produces: `AnimationPriority.GAME: Int = 55`, used by Task 4 (`App.kt` wiring).

- [ ] **Step 1: Write the failing test**

```kotlin
package cc.dvitski.tabyproto

import kotlin.test.Test
import kotlin.test.assertTrue

class AnimationPriorityTest {
    @Test
    fun `GAME priority sits between MUSIC and VOICE`() {
        assertTrue(AnimationPriority.GAME > AnimationPriority.MUSIC)
        assertTrue(AnimationPriority.GAME < AnimationPriority.VOICE)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :api:test --tests "cc.dvitski.tabyproto.AnimationPriorityTest"`
Expected: FAIL to compile — `Unresolved reference: GAME`

- [ ] **Step 3: Add the constant**

Edit `api/src/main/kotlin/cc/dvitski/tabyproto/AnimationPriority.kt` to:

```kotlin
package cc.dvitski.tabyproto

object AnimationPriority {
    const val MANUAL = 100
    const val TOUCH  = 70
    const val VOICE  = 60
    const val GAME   = 55
    const val MUSIC  = 50
    const val IDLE   = 10
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :api:test --tests "cc.dvitski.tabyproto.AnimationPriorityTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add api/src/main/kotlin/cc/dvitski/tabyproto/AnimationPriority.kt api/src/test/kotlin/cc/dvitski/tabyproto/AnimationPriorityTest.kt
git commit -m "feat(api): add GAME animation priority between MUSIC and VOICE"
```

---

### Task 2: `GameState` + `KnownGames` classification logic

**Files:**
- Create: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/GameState.kt`
- Create: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/KnownGames.kt`
- Test: `desktop/src/test/kotlin/cc/dvitski/tabyproto/desktop/KnownGamesTest.kt`

**Interfaces:**
- Produces: `sealed class GameState { object Idle; data class Active(val processName: String) }`, `KnownGames.classify(processName: String?, isFullscreen: Boolean): GameState`. Used by Task 3 (`GameMonitor`) and Task 4 (`App.kt`).

- [ ] **Step 1: Write the failing test**

```kotlin
package cc.dvitski.tabyproto.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class KnownGamesTest {

    @Test
    fun `known game process is Active regardless of fullscreen state`() {
        val result = KnownGames.classify("VALORANT-Win64-Shipping", isFullscreen = false)
        assertEquals(GameState.Active("VALORANT-Win64-Shipping"), result)
    }

    @Test
    fun `known game process matches case-insensitively`() {
        val result = KnownGames.classify("valorant-win64-shipping", isFullscreen = false)
        assertEquals(GameState.Active("valorant-win64-shipping"), result)
    }

    @Test
    fun `unknown fullscreen process is Active`() {
        val result = KnownGames.classify("SomeIndieGame", isFullscreen = true)
        assertEquals(GameState.Active("SomeIndieGame"), result)
    }

    @Test
    fun `unknown fullscreen process on the exclude list is Idle`() {
        val result = KnownGames.classify("chrome", isFullscreen = true)
        assertEquals(GameState.Idle, result)
    }

    @Test
    fun `unknown windowed process is Idle`() {
        val result = KnownGames.classify("notepad", isFullscreen = false)
        assertEquals(GameState.Idle, result)
    }

    @Test
    fun `null process name is Idle`() {
        val result = KnownGames.classify(null, isFullscreen = true)
        assertEquals(GameState.Idle, result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :desktop:test --tests "cc.dvitski.tabyproto.desktop.KnownGamesTest"`
Expected: FAIL to compile — `Unresolved reference: GameState`, `Unresolved reference: KnownGames`

- [ ] **Step 3: Create `GameState.kt`**

```kotlin
package cc.dvitski.tabyproto.desktop

sealed class GameState {
    object Idle : GameState()
    data class Active(val processName: String) : GameState()
}
```

- [ ] **Step 4: Create `KnownGames.kt`**

```kotlin
package cc.dvitski.tabyproto.desktop

object KnownGames {

    // High-confidence matches: fires regardless of fullscreen state. Process names as
    // reported by System.Diagnostics.Process.ProcessName (no .exe extension).
    private val KNOWN_GAME_PROCESSES: Set<String> = setOf(
        "VALORANT-Win64-Shipping",
        "csgo",
        "cs2",
        "LeagueClientUx",
        "League of Legends",
        "Dota2",
        "FortniteClient-Win64-Shipping",
        "GTA5",
        "Overwatch",
        "RocketLeague",
        "EscapeFromTarkov",
        "ApexLegends",
        "r5apex",
    ).map { it.lowercase() }.toSet()

    // Common non-game apps that often run fullscreen/borderless; excluded from the
    // fullscreen fallback so e.g. a fullscreen browser doesn't register as a game.
    private val FULLSCREEN_EXCLUDE_PROCESSES: Set<String> = setOf(
        "chrome", "firefox", "msedge", "explorer",
        "idea64", "code", "Taby", "WindowsTerminal", "cmd", "powershell", "pwsh",
        "vlc", "mpv", "Spotify",
    ).map { it.lowercase() }.toSet()

    fun classify(processName: String?, isFullscreen: Boolean): GameState {
        if (processName == null) return GameState.Idle
        val normalized = processName.lowercase()
        return when {
            normalized in KNOWN_GAME_PROCESSES -> GameState.Active(processName)
            isFullscreen && normalized !in FULLSCREEN_EXCLUDE_PROCESSES -> GameState.Active(processName)
            else -> GameState.Idle
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :desktop:test --tests "cc.dvitski.tabyproto.desktop.KnownGamesTest"`
Expected: PASS (6 tests)

- [ ] **Step 6: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/GameState.kt desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/KnownGames.kt desktop/src/test/kotlin/cc/dvitski/tabyproto/desktop/KnownGamesTest.kt
git commit -m "feat(desktop): add GameState and known-game/fullscreen classification logic"
```

---

### Task 3: `GamePoller` + `GameMonitor`

**Files:**
- Create: `desktop/src/main/resources/game_query.cs`
- Create: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/GamePoller.kt`
- Create: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/GameMonitor.kt`

**Interfaces:**
- Consumes: `KnownGames.classify(processName: String?, isFullscreen: Boolean): GameState` (Task 2).
- Produces: `class GameMonitor(scope: CoroutineScope) { val state: StateFlow<GameState>; fun start(); fun setEnabled(enabled: Boolean) }`. Used by Task 4 (`App.kt`).

This task has no automated test — `GamePoller` shells out to a compiled Win32 helper that reads the live foreground window, which can't be deterministically driven from a test runner (same reason `SmtcPoller` has none). It's verified manually in Step 5.

- [ ] **Step 1: Write the C# helper**

Create `desktop/src/main/resources/game_query.cs`:

```csharp
using System;
using System.Diagnostics;
using System.Runtime.InteropServices;

class Program {
    [DllImport("user32.dll")]
    static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);

    [DllImport("user32.dll")]
    static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);

    [DllImport("user32.dll")]
    static extern IntPtr MonitorFromWindow(IntPtr hwnd, uint dwFlags);

    [DllImport("user32.dll")]
    static extern bool GetMonitorInfo(IntPtr hMonitor, ref MONITORINFO lpmi);

    const uint MONITOR_DEFAULTTONEAREST = 2;

    [StructLayout(LayoutKind.Sequential)]
    struct RECT { public int Left, Top, Right, Bottom; }

    [StructLayout(LayoutKind.Sequential)]
    struct MONITORINFO {
        public uint cbSize;
        public RECT rcMonitor;
        public RECT rcWork;
        public uint dwFlags;
    }

    static void Main() {
        try { Console.WriteLine(Run()); }
        catch { Console.WriteLine("{\"processName\":null,\"isFullscreen\":false}"); }
    }

    static string Run() {
        IntPtr hwnd = GetForegroundWindow();
        if (hwnd == IntPtr.Zero) return "{\"processName\":null,\"isFullscreen\":false}";

        uint pid;
        GetWindowThreadProcessId(hwnd, out pid);
        string processName = null;
        try { processName = Process.GetProcessById((int)pid).ProcessName; } catch {}

        bool isFullscreen = false;
        RECT windowRect;
        if (GetWindowRect(hwnd, out windowRect)) {
            IntPtr hMonitor = MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST);
            MONITORINFO mi = new MONITORINFO();
            mi.cbSize = (uint)Marshal.SizeOf(typeof(MONITORINFO));
            if (GetMonitorInfo(hMonitor, ref mi)) {
                isFullscreen = windowRect.Left <= mi.rcMonitor.Left &&
                               windowRect.Top <= mi.rcMonitor.Top &&
                               windowRect.Right >= mi.rcMonitor.Right &&
                               windowRect.Bottom >= mi.rcMonitor.Bottom;
            }
        }

        string nameJson = processName != null ? "\"" + processName.Replace("\\", "\\\\").Replace("\"", "\\\"") + "\"" : "null";
        return "{\"processName\":" + nameJson + ",\"isFullscreen\":" + (isFullscreen ? "true" : "false") + "}";
    }
}
```

- [ ] **Step 2: Write `GamePoller.kt`**

```kotlin
package cc.dvitski.tabyproto.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

class GamePoller {

    private val cacheDir = File(System.getProperty("java.io.tmpdir"), "taby-game").also { it.mkdirs() }
    private val exeFile = File(cacheDir, "game_query_v1.exe")
    private val exePath: String? by lazy { buildExe() }

    private fun buildExe(): String? {
        if (exeFile.exists()) return exeFile.absolutePath
        val csc = findCsc() ?: return null
        val src = GamePoller::class.java.getResourceAsStream("/game_query.cs") ?: return null
        val srcFile = File(cacheDir, "game_query.cs").also { f -> src.use { s -> f.writeBytes(s.readBytes()) } }

        ProcessBuilder(
            csc, "/nologo", "/target:exe", "/platform:x64",
            "/out:${exeFile.absolutePath}",
            srcFile.absolutePath,
        ).redirectErrorStream(true).start().also { it.inputStream.bufferedReader().readText() }.waitFor()

        return exeFile.takeIf { it.exists() }?.absolutePath
    }

    // Requires .NET Framework 4.x (csc.exe ships with Windows via .NET Framework).
    private fun findCsc(): String? =
        """C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe""".let { if (File(it).exists()) it else null }

    suspend fun poll(): GameQueryResult = withContext(Dispatchers.IO) {
        val exe = exePath ?: return@withContext GameQueryResult(null, false)
        try {
            val proc = ProcessBuilder(exe).redirectErrorStream(true).start()
            val output = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            parseResult(output)
        } catch (_: Exception) {
            GameQueryResult(null, false)
        }
    }

    private fun parseResult(raw: String): GameQueryResult = try {
        val json = raw.lines().lastOrNull { it.trim().startsWith("{") }?.trim() ?: return GameQueryResult(null, false)
        val obj = Json.parseToJsonElement(json) as? JsonObject ?: return GameQueryResult(null, false)
        GameQueryResult(
            processName  = (obj["processName"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
            isFullscreen = (obj["isFullscreen"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false,
        )
    } catch (_: Exception) {
        GameQueryResult(null, false)
    }

    data class GameQueryResult(val processName: String?, val isFullscreen: Boolean)
}
```

- [ ] **Step 3: Write `GameMonitor.kt`**

```kotlin
package cc.dvitski.tabyproto.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GameMonitor(private val scope: CoroutineScope) {

    private val poller = GamePoller()
    private val _enabled = MutableStateFlow(true)
    private val _state = MutableStateFlow<GameState>(GameState.Idle)
    val state: StateFlow<GameState> = _state.asStateFlow()

    fun start() {
        scope.launch {
            while (true) {
                if (_enabled.value) {
                    val result = poller.poll()
                    _state.value = KnownGames.classify(result.processName, result.isFullscreen)
                }
                delay(3_000L)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        if (!enabled) _state.value = GameState.Idle
    }
}
```

- [ ] **Step 4: Build**

Run: `./gradlew :desktop:assemble`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Manually verify the poller end-to-end**

This step is a one-off manual check, not part of the automated suite. From a Windows machine with the repo checked out:

1. Add a temporary `fun main() { runBlocking { println(GamePoller().poll()) } }` to a scratch file (e.g. `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/ScratchMain.kt`), or run it via `jshell`/a temporary test — whichever is fastest.
2. Run it once with a normal window (e.g. this terminal) focused — expect `GameQueryResult(processName=..., isFullscreen=false)` with a real process name (e.g. `WindowsTerminal` or `idea64`).
3. Run it again with a fullscreen app focused (e.g. press `F11` in a browser, or launch any fullscreen game/video) — expect `isFullscreen=true`.
4. Delete the scratch file before committing.

- [ ] **Step 6: Commit**

```bash
git add desktop/src/main/resources/game_query.cs desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/GamePoller.kt desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/GameMonitor.kt
git commit -m "feat(desktop): add GamePoller and GameMonitor for foreground-process/fullscreen detection"
```

---

### Task 4: `MusicMonitor.setEnabled` + `DetectionSettingsStore`

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/MusicMonitor.kt`
- Create: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/DetectionSettingsStore.kt`

**Interfaces:**
- Produces: `MusicMonitor.setEnabled(enabled: Boolean): Unit`; `data class DetectionSettings(musicEnabled: Boolean = true, gameEnabled: Boolean = true)`; `class DetectionSettingsStore { fun load(): DetectionSettings; fun save(settings: DetectionSettings) }`. Used by Task 5 (`App.kt`).

No automated test for either change — `MusicMonitor` has no existing tests (OS-dependent polling), and `DetectionSettingsStore` is a `Preferences` wrapper following the untested `ThemeStore`/`IdleStore`/`GeneralStore` convention.

- [ ] **Step 1: Add `setEnabled` to `MusicMonitor`**

Edit `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/MusicMonitor.kt`:

```kotlin
package cc.dvitski.tabyproto.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MusicMonitor(private val scope: CoroutineScope) {

    private val poller = SmtcPoller()
    private val _enabled = MutableStateFlow(true)
    private val _state = MutableStateFlow<MusicState>(MusicState.Idle)
    val state: StateFlow<MusicState> = _state.asStateFlow()

    fun start() {
        scope.launch {
            while (true) {
                if (_enabled.value) {
                    _state.value = poller.poll().toMusicState()
                }
                delay(2_000L)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        if (!enabled) _state.value = MusicState.Idle
    }

    fun sendControl(control: MediaControl, appId: String?) {
        if (control == MediaControl.PlayPause) {
            val current = _state.value
            if (current is MusicState.Active) {
                val sessions = current.sessions.toMutableList()
                val idx = if (appId != null) sessions.indexOfFirst { it.appId == appId }
                          else sessions.indexOfFirst { it.isPlaying }.takeIf { it >= 0 } ?: 0
                if (idx in sessions.indices) {
                    sessions[idx] = sessions[idx].copy(isPlaying = !sessions[idx].isPlaying)
                    _state.value = current.copy(sessions = sessions)
                }
            }
        }
        if (appId == null) return
        val command = when (control) {
            MediaControl.PlayPause -> "toggle"
            MediaControl.Next      -> "next"
            MediaControl.Prev      -> "prev"
        }
        scope.launch { poller.sendCommand(command, appId) }
    }

    private fun List<SmtcPoller.SmtcSession>.toMusicState(): MusicState {
        if (isEmpty()) return MusicState.Idle
        return MusicState.Active(map { s ->
            NowPlaying(
                track        = s.title,
                artist       = s.artist,
                albumArtUri  = s.albumArtPath?.let { "file://$it" },
                position     = s.positionMs.milliseconds,
                duration     = s.durationMs?.takeIf { it > 0 }?.milliseconds,
                source       = when {
                    s.appId?.contains("Spotify", ignoreCase = true) == true -> MusicSource.Spotify
                    s.appId?.contains("Tidal",   ignoreCase = true) == true -> MusicSource.Tidal
                    else -> MusicSource.Generic
                },
                isPlaying    = s.isPlaying,
                appId        = s.appId,
            )
        })
    }
}
```

- [ ] **Step 2: Create `DetectionSettingsStore.kt`**

```kotlin
package cc.dvitski.tabyproto.desktop

import java.util.prefs.Preferences

data class DetectionSettings(val musicEnabled: Boolean = true, val gameEnabled: Boolean = true)

class DetectionSettingsStore {
    private val prefs = Preferences.userNodeForPackage(DetectionSettingsStore::class.java)

    fun load(): DetectionSettings = DetectionSettings(
        musicEnabled = prefs.getBoolean(KEY_MUSIC, true),
        gameEnabled  = prefs.getBoolean(KEY_GAME, true),
    )

    fun save(settings: DetectionSettings) {
        prefs.putBoolean(KEY_MUSIC, settings.musicEnabled)
        prefs.putBoolean(KEY_GAME, settings.gameEnabled)
    }

    private companion object {
        const val KEY_MUSIC = "musicDetectionEnabled"
        const val KEY_GAME = "gameDetectionEnabled"
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :desktop:assemble`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/MusicMonitor.kt desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/DetectionSettingsStore.kt
git commit -m "feat(desktop): add MusicMonitor.setEnabled and DetectionSettingsStore"
```

---

### Task 5: Wire `GameMonitor` + detection toggles into `AppState`/`App.kt`

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt`

**Interfaces:**
- Consumes: `AnimationPriority.GAME` (Task 1), `GameState`/`KnownGames` (Task 2), `GameMonitor` (Task 3), `MusicMonitor.setEnabled`/`DetectionSettingsStore`/`DetectionSettings` (Task 4), `Animations.F1_CAR` (existing).
- Produces (new `AppState` members, consumed by Task 6's `SettingsScreen`/`App` composable changes): `val gameState: StateFlow<GameState>`, `val musicDetectionEnabled: StateFlow<Boolean>`, `val gameDetectionEnabled: StateFlow<Boolean>`, `fun setMusicDetectionEnabled(enabled: Boolean)`, `fun setGameDetectionEnabled(enabled: Boolean)`.

No automated test — this is `AppState` init-block wiring identical in shape to the existing untested `musicMonitor.state` wiring it sits next to. Verified manually in Step 6.

- [ ] **Step 1: Add new `AppState` fields**

In `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt`, in the `AppState` class body, add the `gameMonitor` and `detectionSettingsStore` alongside `musicMonitor` (after line 72 `private val musicMonitor = MusicMonitor(scope)`):

```kotlin
    private val musicMonitor = MusicMonitor(scope)
    private val gameMonitor = GameMonitor(scope)
    private val detectionSettingsStore = DetectionSettingsStore()
```

Add the exposed state flows after line 78 (`val musicState: StateFlow<MusicState> = musicMonitor.state`):

```kotlin
    val musicState: StateFlow<MusicState> = musicMonitor.state
    val gameState: StateFlow<GameState> = gameMonitor.state

    private val _initialDetectionSettings = detectionSettingsStore.load()
    private val _musicDetectionEnabled = MutableStateFlow(_initialDetectionSettings.musicEnabled)
    val musicDetectionEnabled: StateFlow<Boolean> = _musicDetectionEnabled.asStateFlow()
    private val _gameDetectionEnabled = MutableStateFlow(_initialDetectionSettings.gameEnabled)
    val gameDetectionEnabled: StateFlow<Boolean> = _gameDetectionEnabled.asStateFlow()
```

- [ ] **Step 2: Add toggle setters**

After the existing `fun setMinimizeToTray(enabled: Boolean) { ... }` method (around line 171-174), add:

```kotlin
    fun setMusicDetectionEnabled(enabled: Boolean) {
        _musicDetectionEnabled.value = enabled
        musicMonitor.setEnabled(enabled)
        detectionSettingsStore.save(DetectionSettings(musicEnabled = enabled, gameEnabled = _gameDetectionEnabled.value))
    }

    fun setGameDetectionEnabled(enabled: Boolean) {
        _gameDetectionEnabled.value = enabled
        gameMonitor.setEnabled(enabled)
        detectionSettingsStore.save(DetectionSettings(musicEnabled = _musicDetectionEnabled.value, gameEnabled = enabled))
    }
```

- [ ] **Step 3: Start and enable the monitor in `init {}`**

In the `init {}` block, change line 177-178 from:

```kotlin
        monitor.start()
        musicMonitor.start()
```

to:

```kotlin
        monitor.start()
        musicMonitor.start()
        musicMonitor.setEnabled(_musicDetectionEnabled.value)
        gameMonitor.start()
        gameMonitor.setEnabled(_gameDetectionEnabled.value)
```

- [ ] **Step 4: Add the `gameMonitor.state` collector**

Immediately after the existing music periodic re-send block (after line 306, the closing of the `scope.launch { while (true) { delay(2_000) ...` block for music, and before the closing `}` of `init {}` on line 307), add:

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

- [ ] **Step 5: Build**

Run: `./gradlew :desktop:assemble`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Manually verify**

1. Run `./gradlew :desktop:run` with a Taby device connected.
2. Launch any fullscreen app not on the exclude list (e.g. a fullscreen video game, or press `F11` in a non-browser app). Within ~3s, confirm the device plays the `F1_CAR` animation.
3. Exit/minimize the fullscreen app. Within ~3s, confirm the device falls back to an idle animation.
4. Leave this terminal running, this confirms the `WindowsTerminal`/IDE-in-foreground case stays idle the whole time (it's on `FULLSCREEN_EXCLUDE_PROCESSES` and not fullscreen anyway).

- [ ] **Step 7: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt
git commit -m "feat(desktop): wire GameMonitor into AppState at AnimationPriority.GAME"
```

---

### Task 6: Settings UI — Game category + Music/Game toggles

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt`
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/SettingsScreen.kt`

**Interfaces:**
- Consumes: `AppState.gameState`, `AppState.musicDetectionEnabled`, `AppState.gameDetectionEnabled`, `AppState.setMusicDetectionEnabled`, `AppState.setGameDetectionEnabled` (Task 5).

No automated test — Compose UI, verified manually by running the app (Step 6 below), consistent with how the rest of `SettingsScreen` is verified in this codebase (no Compose UI tests exist).

- [ ] **Step 1: Add `Game` to `SettingsCategory`**

In `App.kt` line 60, change:

```kotlin
enum class SettingsCategory { Music, Voice, Device, Appearance, Idle }
```

to:

```kotlin
enum class SettingsCategory { Music, Game, Voice, Device, Appearance, Idle }
```

- [ ] **Step 2: Thread new state through the `App` composable**

In `App.kt`'s `App(appState: AppState)` composable, after line 389 (`val musicState by appState.musicState.collectAsState()`), add:

```kotlin
        val musicState by appState.musicState.collectAsState()
        val gameState by appState.gameState.collectAsState()
        val musicDetectionEnabled by appState.musicDetectionEnabled.collectAsState()
        val gameDetectionEnabled by appState.gameDetectionEnabled.collectAsState()
```

In the `is Screen.Settings ->` branch's `SettingsScreen(...)` call (around line 461-493), add these arguments alongside the existing `musicState = musicState, onMusicControl = appState::sendMusicControl,` (line 484-485):

```kotlin
                        musicState = musicState,
                        onMusicControl = appState::sendMusicControl,
                        musicDetectionEnabled = musicDetectionEnabled,
                        onSetMusicDetectionEnabled = appState::setMusicDetectionEnabled,
                        gameState = gameState,
                        gameDetectionEnabled = gameDetectionEnabled,
                        onSetGameDetectionEnabled = appState::setGameDetectionEnabled,
```

- [ ] **Step 3: Add the new parameters and label to `SettingsScreen.kt`**

In `SettingsScreen.kt`, update the `SettingsCategory.label` extension (lines 63-70):

```kotlin
private val SettingsCategory.label: String
    get() = when (this) {
        SettingsCategory.Music -> "Music"
        SettingsCategory.Game -> "Game"
        SettingsCategory.Voice -> "Voice"
        SettingsCategory.Device -> "Device"
        SettingsCategory.Appearance -> "Appearance"
        SettingsCategory.Idle -> "Idle"
    }
```

Add new parameters to the `SettingsScreen` composable signature (after `onMusicControl: (MediaControl, String?) -> Unit,` on line 97):

```kotlin
    musicState: MusicState,
    onMusicControl: (MediaControl, String?) -> Unit,
    musicDetectionEnabled: Boolean,
    onSetMusicDetectionEnabled: (Boolean) -> Unit,
    gameState: GameState,
    gameDetectionEnabled: Boolean,
    onSetGameDetectionEnabled: (Boolean) -> Unit,
```

Pass them through both `CategoryContent(...)` call sites (the `Compact` branch around lines 121-150 and the else branch around lines 169-198) by adding, alongside the existing `musicState = musicState, onMusicControl = onMusicControl,`:

```kotlin
                musicState = musicState,
                onMusicControl = onMusicControl,
                musicDetectionEnabled = musicDetectionEnabled,
                onSetMusicDetectionEnabled = onSetMusicDetectionEnabled,
                gameState = gameState,
                gameDetectionEnabled = gameDetectionEnabled,
                onSetGameDetectionEnabled = onSetGameDetectionEnabled,
```

- [ ] **Step 4: Update `CategoryContent` and add `GameDetail`**

Update the `CategoryContent` composable signature (lines 204-232) by adding, after `onMusicControl: (MediaControl, String?) -> Unit,`:

```kotlin
    musicDetectionEnabled: Boolean,
    onSetMusicDetectionEnabled: (Boolean) -> Unit,
    gameState: GameState,
    gameDetectionEnabled: Boolean,
    onSetGameDetectionEnabled: (Boolean) -> Unit,
```

Update its `when (selectedCategory)` body (lines 235-241):

```kotlin
        when (selectedCategory) {
            SettingsCategory.Music      -> MusicDetail(musicState, musicDetectionEnabled, onSetMusicDetectionEnabled, onMusicControl)
            SettingsCategory.Game       -> GameDetail(gameState, gameDetectionEnabled, onSetGameDetectionEnabled)
            SettingsCategory.Voice      -> PlaceholderDetail("Voice", "Wake word and microphone settings coming soon.")
            SettingsCategory.Device     -> DeviceDetail(devices, activeDeviceId, onSelectDevice, onAddHost, onRemoveHost, brightness, onBrightnessChange, animations, query, onQueryChange, typeFilter, onTypeFilterChange, thumbnailCache, sendingAnimation, onSend, onReboot)
            SettingsCategory.Appearance -> AppearanceDetail(isDark, currentPalette, onSetTheme, minimizeToTray, onSetMinimizeToTray)
            SettingsCategory.Idle       -> IdleDetail(idleSettings, onIdleSettingsChange, idleStatus)
        }
```

- [ ] **Step 5: Update `MusicDetail` and add `GameDetail`**

Replace the existing `MusicDetail` composable (lines 296-313) with:

```kotlin
@Composable
private fun MusicDetail(state: MusicState, enabled: Boolean, onSetEnabled: (Boolean) -> Unit, onControl: (MediaControl, String?) -> Unit) {
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Music", color = theme.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("DETECT MUSIC", color = theme.textSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
            TogglePill(checked = enabled, onCheckedChange = onSetEnabled)
        }
        when (state) {
            MusicState.Idle -> Text("No music playing", color = theme.textSecondary, fontSize = 15.sp)
            is MusicState.Active -> {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    state.sessions.forEach { session ->
                        val appId = session.appId
                        MusicSessionDetail(session = session, onControl = { c -> onControl(c, appId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GameDetail(state: GameState, enabled: Boolean, onSetEnabled: (Boolean) -> Unit) {
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Game", color = theme.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("DETECT GAMES", color = theme.textSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
            TogglePill(checked = enabled, onCheckedChange = onSetEnabled)
        }
        val statusText = when (state) {
            GameState.Idle -> "No game detected"
            is GameState.Active -> "Currently detected: ${state.processName}"
        }
        Text(statusText, color = theme.textSecondary, fontSize = 15.sp)
    }
}
```

- [ ] **Step 6: Build and manually verify**

Run: `./gradlew :desktop:assemble`
Expected: BUILD SUCCESSFUL

Run: `./gradlew :desktop:run`

1. Open Settings → confirm a new "Game" category appears in the sidebar, between "Music" and "Voice".
2. Click into "Game" → confirm the "DETECT GAMES" toggle and "No game detected" text render.
3. Click into "Music" → confirm a new "DETECT MUSIC" toggle now appears above the existing now-playing content.
4. Toggle "DETECT GAMES" off → launch a fullscreen app → confirm the device does *not* react (no `F1_CAR` animation).
5. Toggle "DETECT GAMES" back on → confirm detection resumes.
6. Toggle "DETECT MUSIC" off while music is playing → confirm `MUSIC` priority animation stops and the music sidebar block (if visible) reflects `Idle`.
7. Quit and relaunch the app → confirm both toggle states persisted.

- [ ] **Step 7: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/SettingsScreen.kt
git commit -m "feat(desktop): add Game settings category and music/game detection toggles"
```

---

### Task 7: Full test suite + final review

**Files:** none (verification only)

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass including the new `AnimationPriorityTest` and `KnownGamesTest`.

- [ ] **Step 2: Run a full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Re-run the manual verification from Tasks 5 and 6 end-to-end**

With a Taby device connected: confirm a fullscreen game (or any fullscreen app not on the exclude list) drives `F1_CAR` at `GAME` priority, preempting any active `MUSIC`-priority animation but not a `VOICE`/`TOUCH`/`MANUAL` one; confirm both Settings toggles work and persist across restarts.

- [ ] **Step 4: Push branch state (if working on a feature branch other than `latest`)**

If commits landed directly on `latest`, no merge is needed. If they landed on a separate branch, follow `CLAUDE.md`: `git checkout latest && git merge --ff-only <branch> && git push origin latest`.

---

## Out of Scope (carried over from the design spec)

- A dedicated "gaming" animation asset (using `F1_CAR` as a stand-in until one exists)
- Expanding `KNOWN_GAME_PROCESSES` beyond an initial small seed list
- Pulling Discord's `detectable.json` or any external/network game database
- Per-game animation customization
- Linux/macOS game detection
