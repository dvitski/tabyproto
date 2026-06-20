# Launch at Startup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Launch at Startup" toggle (with a "Start Minimized" sub-toggle that only applies to startup-triggered launches) to the packaged Windows desktop app.

**Architecture:** A new `StartupLaunchManager` object builds and runs `reg.exe` commands against the per-user `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` key — no admin rights needed. `GeneralStore` persists the two booleans. `AppState` wires StateFlows/setters that call into `StartupLaunchManager` whenever either setting changes. `Main.kt` reads a `--minimized` CLI flag (forwarded by the existing `bootstrap/Launcher.kt`) to decide initial window visibility. `SettingsScreen.kt`'s `AppearanceDetail` gets two new toggle rows.

**Tech Stack:** Kotlin/JVM, Compose Desktop, `java.util.prefs.Preferences`, `ProcessBuilder` + Windows `reg.exe`, `kotlin.test` / JUnit Platform (existing `desktop` test setup).

## Global Constraints

- Windows-only feature (the app packages exclusively as a Windows `.exe` via `targetFormats(TargetFormat.Exe)` in `desktop/build.gradle.kts`) — no cross-platform branching needed.
- No admin rights may be required (matches `perUserInstall = true`).
- Registry/process side effects must be `runCatching`-wrapped and only logged on failure — never surfaced as a UI error (matches the existing bootstrap update-check pattern in `bootstrap/Launcher.kt`).
- Pure logic (command-building, arg-parsing) must be separated from side-effecting code so it can be unit tested without touching the real registry or spawning processes, following the existing `KnownGamesTest` pattern of testing only pure functions.
- Installed exe name is `TabyProto-Desktop.exe` (must match `packageName = "TabyProto-Desktop"` in `desktop/build.gradle.kts:29`).

---

### Task 1: `StartupLaunchManager` — registry command building + exe path detection

**Files:**
- Create: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/StartupLaunchManager.kt`
- Test: `desktop/src/test/kotlin/cc/dvitski/tabyproto/desktop/StartupLaunchManagerTest.kt`

**Interfaces:**
- Produces: `StartupLaunchManager.installedExePath(): File?`, `StartupLaunchManager.sync(enabled: Boolean, minimized: Boolean, exePath: File): Unit`, `StartupLaunchManager.buildRegCommand(exePath: String, enabled: Boolean, minimized: Boolean): List<String>` (internal, used by the test).

- [ ] **Step 1: Write the failing test for `buildRegCommand`**

```kotlin
package cc.dvitski.tabyproto.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class StartupLaunchManagerTest {

    private val runKeyPath = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private val exePath = "C:\\Users\\test\\AppData\\Local\\TabyProto-Desktop\\TabyProto-Desktop.exe"

    @Test
    fun `enabled without minimized builds add command with bare exe path`() {
        val result = StartupLaunchManager.buildRegCommand(exePath, enabled = true, minimized = false)
        assertEquals(
            listOf("reg", "add", runKeyPath, "/v", "TabyProto-Desktop", "/t", "REG_SZ", "/d", "\"$exePath\"", "/f"),
            result,
        )
    }

    @Test
    fun `enabled with minimized builds add command with minimized flag appended`() {
        val result = StartupLaunchManager.buildRegCommand(exePath, enabled = true, minimized = true)
        assertEquals(
            listOf("reg", "add", runKeyPath, "/v", "TabyProto-Desktop", "/t", "REG_SZ", "/d", "\"$exePath\" --minimized", "/f"),
            result,
        )
    }

    @Test
    fun `disabled builds delete command regardless of minimized flag`() {
        val result = StartupLaunchManager.buildRegCommand(exePath, enabled = false, minimized = true)
        assertEquals(
            listOf("reg", "delete", runKeyPath, "/v", "TabyProto-Desktop", "/f"),
            result,
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :desktop:test --tests "cc.dvitski.tabyproto.desktop.StartupLaunchManagerTest"`
Expected: FAIL — `StartupLaunchManager` is unresolved (doesn't exist yet).

- [ ] **Step 3: Write `StartupLaunchManager` with `buildRegCommand` and `installedExePath`**

```kotlin
package cc.dvitski.tabyproto.desktop

import java.io.File
import java.nio.file.Paths
import java.util.logging.Logger

object StartupLaunchManager {
    private val log = Logger.getLogger("taby-startup-launch")

    private const val RUN_KEY_PATH = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE_NAME = "TabyProto-Desktop"
    private const val EXE_NAME = "TabyProto-Desktop.exe"

    /** Resolves the installed app's native launcher exe from java.home (<install>/runtime),
     *  matching the jpackage Windows app-image layout. Returns null when not running from
     *  an installed app (e.g. ./gradlew :desktop:run), where the feature is unavailable. */
    fun installedExePath(): File? {
        val installDir = Paths.get(System.getProperty("java.home")).parent ?: return null
        val exe = installDir.resolve(EXE_NAME).toFile()
        return if (exe.isFile) exe else null
    }

    internal fun buildRegCommand(exePath: String, enabled: Boolean, minimized: Boolean): List<String> =
        if (enabled) {
            val command = if (minimized) "\"$exePath\" --minimized" else "\"$exePath\""
            listOf("reg", "add", RUN_KEY_PATH, "/v", VALUE_NAME, "/t", "REG_SZ", "/d", command, "/f")
        } else {
            listOf("reg", "delete", RUN_KEY_PATH, "/v", VALUE_NAME, "/f")
        }

    /** Best-effort: failures are logged, never surfaced to the UI. */
    fun sync(enabled: Boolean, minimized: Boolean, exePath: File) {
        val command = buildRegCommand(exePath.absolutePath, enabled, minimized)
        runCatching {
            ProcessBuilder(command).redirectErrorStream(true).start().waitFor()
        }.onFailure { log.warning("Startup registration sync failed: ${it.message}") }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :desktop:test --tests "cc.dvitski.tabyproto.desktop.StartupLaunchManagerTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/StartupLaunchManager.kt desktop/src/test/kotlin/cc/dvitski/tabyproto/desktop/StartupLaunchManagerTest.kt
git commit -m "feat(desktop): add StartupLaunchManager for Windows Run-key registration"
```

---

### Task 2: `GeneralStore` — persist the two new settings

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/GeneralStore.kt`

**Interfaces:**
- Produces: `GeneralStore.loadLaunchAtStartup(): Boolean`, `GeneralStore.saveLaunchAtStartup(enabled: Boolean)`, `GeneralStore.loadStartMinimizedOnStartup(): Boolean`, `GeneralStore.saveStartMinimizedOnStartup(enabled: Boolean)`.

No test for this task — `GeneralStore` has no existing test coverage (it's a thin `Preferences` wrapper, same as `loadMinimizeToTray`/`saveMinimizeToTray` which are also untested).

- [ ] **Step 1: Add the new keys and methods**

Replace the full contents of `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/GeneralStore.kt` with:

```kotlin
package cc.dvitski.tabyproto.desktop

import java.util.prefs.Preferences

class GeneralStore {
    private val prefs = Preferences.userNodeForPackage(GeneralStore::class.java)

    fun loadMinimizeToTray(): Boolean = prefs.getBoolean(KEY_MINIMIZE_TO_TRAY, true)

    fun saveMinimizeToTray(enabled: Boolean) {
        prefs.putBoolean(KEY_MINIMIZE_TO_TRAY, enabled)
    }

    fun loadLaunchAtStartup(): Boolean = prefs.getBoolean(KEY_LAUNCH_AT_STARTUP, false)

    fun saveLaunchAtStartup(enabled: Boolean) {
        prefs.putBoolean(KEY_LAUNCH_AT_STARTUP, enabled)
    }

    fun loadStartMinimizedOnStartup(): Boolean = prefs.getBoolean(KEY_START_MINIMIZED, false)

    fun saveStartMinimizedOnStartup(enabled: Boolean) {
        prefs.putBoolean(KEY_START_MINIMIZED, enabled)
    }

    private companion object {
        const val KEY_MINIMIZE_TO_TRAY = "minimizeToTray"
        const val KEY_LAUNCH_AT_STARTUP = "launchAtStartupEnabled"
        const val KEY_START_MINIMIZED = "startMinimizedOnStartup"
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :desktop:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/GeneralStore.kt
git commit -m "feat(desktop): persist launch-at-startup and start-minimized settings"
```

---

### Task 3: `Main.kt` — `--minimized` arg handling

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Main.kt`
- Test: `desktop/src/test/kotlin/cc/dvitski/tabyproto/desktop/MainArgsTest.kt`

**Interfaces:**
- Consumes: none (this task is self-contained — `bootstrap/Launcher.kt` already forwards `args` to `MainKt.main` unchanged).
- Produces: `shouldStartMinimized(args: Array<String>): Boolean` (internal, used by `main()` and by the test).

- [ ] **Step 1: Write the failing test for the pure arg-check**

```kotlin
package cc.dvitski.tabyproto.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainArgsTest {

    @Test
    fun `no args means not minimized`() {
        assertFalse(shouldStartMinimized(emptyArray()))
    }

    @Test
    fun `minimized flag present means minimized`() {
        assertTrue(shouldStartMinimized(arrayOf("--minimized")))
    }

    @Test
    fun `unrelated args do not trigger minimized`() {
        assertFalse(shouldStartMinimized(arrayOf("--some-other-flag")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :desktop:test --tests "cc.dvitski.tabyproto.desktop.MainArgsTest"`
Expected: FAIL — `shouldStartMinimized` is unresolved.

- [ ] **Step 3: Add `shouldStartMinimized` and wire it into `main()`**

In `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Main.kt`, change line 24 from:

```kotlin
fun main() = application {
```

to:

```kotlin
internal fun shouldStartMinimized(args: Array<String>): Boolean = args.contains("--minimized")

fun main(args: Array<String>) = application {
```

Then change line 27 from:

```kotlin
    var isWindowVisible by remember { mutableStateOf(true) }
```

to:

```kotlin
    var isWindowVisible by remember { mutableStateOf(!shouldStartMinimized(args)) }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :desktop:test --tests "cc.dvitski.tabyproto.desktop.MainArgsTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Main.kt desktop/src/test/kotlin/cc/dvitski/tabyproto/desktop/MainArgsTest.kt
git commit -m "feat(desktop): honor --minimized startup flag for initial window visibility"
```

---

### Task 4: `AppState` — StateFlows, setters, and `StartupLaunchManager` wiring

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt:135-137` (insert after the existing `minimizeToTray` block), `App.kt:194-197` (insert after `setMinimizeToTray`)

**Interfaces:**
- Consumes: `GeneralStore.loadLaunchAtStartup/saveLaunchAtStartup/loadStartMinimizedOnStartup/saveStartMinimizedOnStartup` (Task 2), `StartupLaunchManager.installedExePath/sync` (Task 1).
- Produces: `AppState.launchAtStartup: StateFlow<Boolean>`, `AppState.startMinimizedOnStartup: StateFlow<Boolean>`, `AppState.startupExePath: File?`, `AppState.setLaunchAtStartup(enabled: Boolean)`, `AppState.setStartMinimizedOnStartup(enabled: Boolean)`.

No isolated unit test — `AppState` is a large composite class wired to `TabyDeviceMonitor` and friends; none of the existing settings setters (`setMinimizeToTray`, `setMusicDetectionEnabled`) have dedicated tests either. Correctness here is covered by Tasks 1-3's tests plus manual verification in Task 6.

- [ ] **Step 1: Add the new StateFlows next to `minimizeToTray`**

In `App.kt`, find:

```kotlin
    private val generalStore = GeneralStore()
    private val _minimizeToTray = MutableStateFlow(generalStore.loadMinimizeToTray())
    val minimizeToTray: StateFlow<Boolean> = _minimizeToTray.asStateFlow()
```

Replace with:

```kotlin
    private val generalStore = GeneralStore()
    private val _minimizeToTray = MutableStateFlow(generalStore.loadMinimizeToTray())
    val minimizeToTray: StateFlow<Boolean> = _minimizeToTray.asStateFlow()

    val startupExePath: java.io.File? = StartupLaunchManager.installedExePath()
    private val _launchAtStartup = MutableStateFlow(generalStore.loadLaunchAtStartup())
    val launchAtStartup: StateFlow<Boolean> = _launchAtStartup.asStateFlow()
    private val _startMinimizedOnStartup = MutableStateFlow(generalStore.loadStartMinimizedOnStartup())
    val startMinimizedOnStartup: StateFlow<Boolean> = _startMinimizedOnStartup.asStateFlow()
```

- [ ] **Step 2: Add the setters next to `setMinimizeToTray`**

In `App.kt`, find:

```kotlin
    fun setMinimizeToTray(enabled: Boolean) {
        _minimizeToTray.value = enabled
        generalStore.saveMinimizeToTray(enabled)
    }
```

Replace with:

```kotlin
    fun setMinimizeToTray(enabled: Boolean) {
        _minimizeToTray.value = enabled
        generalStore.saveMinimizeToTray(enabled)
    }

    fun setLaunchAtStartup(enabled: Boolean) {
        _launchAtStartup.value = enabled
        generalStore.saveLaunchAtStartup(enabled)
        startupExePath?.let { StartupLaunchManager.sync(enabled, _startMinimizedOnStartup.value, it) }
    }

    fun setStartMinimizedOnStartup(enabled: Boolean) {
        _startMinimizedOnStartup.value = enabled
        generalStore.saveStartMinimizedOnStartup(enabled)
        startupExePath?.let { StartupLaunchManager.sync(_launchAtStartup.value, enabled, it) }
    }
```

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :desktop:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt
git commit -m "feat(desktop): wire launch-at-startup state through AppState"
```

---

### Task 5: `SettingsScreen.kt` — Appearance UI toggles

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/SettingsScreen.kt`

**Interfaces:**
- Consumes: `AppState.launchAtStartup`/`startMinimizedOnStartup`/`startupExePath` and setters (Task 4); existing `TogglePill` composable (`SettingsScreen.kt:594`).
- Produces: `SettingsScreen(...)` and `CategoryContent(...)` gain four new parameters: `launchAtStartup: Boolean`, `onSetLaunchAtStartup: (Boolean) -> Unit`, `startMinimizedOnStartup: Boolean`, `onSetStartMinimizedOnStartup: (Boolean) -> Unit`, `startupAvailable: Boolean`; `AppearanceDetail(...)` gains the same four plus passes through.

- [ ] **Step 1: Add the four new parameters to the public `SettingsScreen` signature**

In `SettingsScreen.kt`, find:

```kotlin
    minimizeToTray: Boolean,
    onSetMinimizeToTray: (Boolean) -> Unit,
    brightness: Int?,
```

(this string appears once, in the `SettingsScreen` function signature around line 93-95) and replace with:

```kotlin
    minimizeToTray: Boolean,
    onSetMinimizeToTray: (Boolean) -> Unit,
    launchAtStartup: Boolean,
    onSetLaunchAtStartup: (Boolean) -> Unit,
    startMinimizedOnStartup: Boolean,
    onSetStartMinimizedOnStartup: (Boolean) -> Unit,
    startupAvailable: Boolean,
    brightness: Int?,
```

- [ ] **Step 2: Pass the new params through both `CategoryContent` call sites inside `SettingsScreen`**

There are two identical-shaped calls to `CategoryContent` inside `SettingsScreen` (one for `WindowSize.Compact`, one for the else branch). In both, find:

```kotlin
                minimizeToTray = minimizeToTray,
                onSetMinimizeToTray = onSetMinimizeToTray,
```

and replace each occurrence with:

```kotlin
                minimizeToTray = minimizeToTray,
                onSetMinimizeToTray = onSetMinimizeToTray,
                launchAtStartup = launchAtStartup,
                onSetLaunchAtStartup = onSetLaunchAtStartup,
                startMinimizedOnStartup = startMinimizedOnStartup,
                onSetStartMinimizedOnStartup = onSetStartMinimizedOnStartup,
                startupAvailable = startupAvailable,
```

(Use `replace_all` — both call sites have this exact text.)

- [ ] **Step 3: Add the new parameters to `CategoryContent` and forward to `AppearanceDetail`**

Find:

```kotlin
    minimizeToTray: Boolean,
    onSetMinimizeToTray: (Boolean) -> Unit,
    idleSettings: IdleSettings,
```

Replace with:

```kotlin
    minimizeToTray: Boolean,
    onSetMinimizeToTray: (Boolean) -> Unit,
    launchAtStartup: Boolean,
    onSetLaunchAtStartup: (Boolean) -> Unit,
    startMinimizedOnStartup: Boolean,
    onSetStartMinimizedOnStartup: (Boolean) -> Unit,
    startupAvailable: Boolean,
    idleSettings: IdleSettings,
```

Then find:

```kotlin
            SettingsCategory.Appearance -> AppearanceDetail(isDark, currentPalette, onSetTheme, minimizeToTray, onSetMinimizeToTray)
```

Replace with:

```kotlin
            SettingsCategory.Appearance -> AppearanceDetail(
                isDark, currentPalette, onSetTheme, minimizeToTray, onSetMinimizeToTray,
                launchAtStartup, onSetLaunchAtStartup, startMinimizedOnStartup, onSetStartMinimizedOnStartup, startupAvailable,
            )
```

- [ ] **Step 4: Extend `AppearanceDetail` with the new toggles**

Find the full `AppearanceDetail` function:

```kotlin
@Composable
private fun AppearanceDetail(
    isDark: Boolean,
    currentPalette: ColorPalette,
    onSetTheme: (Boolean, ColorPalette) -> Unit,
    minimizeToTray: Boolean,
    onSetMinimizeToTray: (Boolean) -> Unit,
) {
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Appearance", color = theme.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("MODE", color = theme.textSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton("Light", !isDark, Modifier.weight(1f)) { onSetTheme(false, currentPalette) }
                ModeButton("Dark", isDark, Modifier.weight(1f)) { onSetTheme(true, currentPalette) }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PALETTE", color = theme.textSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorPalette.entries.forEach { palette ->
                    PaletteSwatch(palette, isDark, palette == currentPalette) { onSetTheme(isDark, palette) }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MINIMIZE TO TRAY", color = theme.textSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
            TogglePill(checked = minimizeToTray, onCheckedChange = onSetMinimizeToTray)
        }
    }
}
```

Replace with:

```kotlin
@Composable
private fun AppearanceDetail(
    isDark: Boolean,
    currentPalette: ColorPalette,
    onSetTheme: (Boolean, ColorPalette) -> Unit,
    minimizeToTray: Boolean,
    onSetMinimizeToTray: (Boolean) -> Unit,
    launchAtStartup: Boolean,
    onSetLaunchAtStartup: (Boolean) -> Unit,
    startMinimizedOnStartup: Boolean,
    onSetStartMinimizedOnStartup: (Boolean) -> Unit,
    startupAvailable: Boolean,
) {
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Appearance", color = theme.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("MODE", color = theme.textSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton("Light", !isDark, Modifier.weight(1f)) { onSetTheme(false, currentPalette) }
                ModeButton("Dark", isDark, Modifier.weight(1f)) { onSetTheme(true, currentPalette) }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PALETTE", color = theme.textSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorPalette.entries.forEach { palette ->
                    PaletteSwatch(palette, isDark, palette == currentPalette) { onSetTheme(isDark, palette) }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MINIMIZE TO TRAY", color = theme.textSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
            TogglePill(checked = minimizeToTray, onCheckedChange = onSetMinimizeToTray)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("LAUNCH AT STARTUP", color = theme.textSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
                TogglePill(checked = launchAtStartup, onCheckedChange = onSetLaunchAtStartup)
            }
            if (!startupAvailable) {
                Text("Only available in the installed app.", color = theme.textSecondary, fontSize = 12.sp)
            }
            if (startupAvailable && launchAtStartup) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("START MINIMIZED", color = theme.textSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
                    TogglePill(checked = startMinimizedOnStartup, onCheckedChange = onSetStartMinimizedOnStartup)
                }
            }
        }
    }
}
```

(Note: `TogglePill` has no built-in disabled state — when `!startupAvailable`, the toggle remains clickable but the caption explains why it has no effect. This matches YAGNI: `AppState.setLaunchAtStartup` already no-ops the registry side effect when `startupExePath` is null, so clicking it in dev mode just flips local UI state harmlessly.)

- [ ] **Step 5: Update the call site in `App.kt`**

In `App.kt`, find:

```kotlin
        val minimizeToTray by appState.minimizeToTray.collectAsState()
```

Replace with:

```kotlin
        val minimizeToTray by appState.minimizeToTray.collectAsState()
        val launchAtStartup by appState.launchAtStartup.collectAsState()
        val startMinimizedOnStartup by appState.startMinimizedOnStartup.collectAsState()
```

Then find, inside the `SettingsScreen(...)` call:

```kotlin
                                        minimizeToTray = minimizeToTray,
                                        onSetMinimizeToTray = appState::setMinimizeToTray,
```

Replace with:

```kotlin
                                        minimizeToTray = minimizeToTray,
                                        onSetMinimizeToTray = appState::setMinimizeToTray,
                                        launchAtStartup = launchAtStartup,
                                        onSetLaunchAtStartup = appState::setLaunchAtStartup,
                                        startMinimizedOnStartup = startMinimizedOnStartup,
                                        onSetStartMinimizedOnStartup = appState::setStartMinimizedOnStartup,
                                        startupAvailable = appState.startupExePath != null,
```

- [ ] **Step 6: Build to verify it compiles**

Run: `./gradlew :desktop:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Run the full desktop test suite**

Run: `./gradlew :desktop:test`
Expected: BUILD SUCCESSFUL, all tests pass (including the new `StartupLaunchManagerTest` and `MainArgsTest`)

- [ ] **Step 8: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/SettingsScreen.kt desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt
git commit -m "feat(desktop): add Launch at Startup and Start Minimized toggles to Appearance settings"
```

---

### Task 6: Manual verification

**Files:** none (verification only)

- [ ] **Step 1: Run the app in dev mode and confirm the disabled-state caption**

Run: `./gradlew :desktop:run`
Expected: App launches, navigate to Settings → Appearance. "LAUNCH AT STARTUP" toggle is visible with caption "Only available in the installed app." beneath it (since `java.home` won't resolve to a jpackage install layout under `./gradlew run`).

- [ ] **Step 2: Build the installer and verify registry behavior**

Run: `./gradlew :desktop:packageExe` (or the existing packaging task used for releases — check `desktop/build.gradle.kts` task names if this differs)
Expected: Installer builds successfully.

- [ ] **Step 3: Install, enable "Launch at Startup", and verify the registry key**

After installing: open the app → Settings → Appearance → toggle "LAUNCH AT STARTUP" on. Then run:

```
reg query "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v TabyProto-Desktop
```

Expected: Output shows a `REG_SZ` value whose data is the quoted path to `TabyProto-Desktop.exe` under `%LocalAppData%\TabyProto-Desktop\`.

- [ ] **Step 4: Verify "Start Minimized" updates the registry value**

Toggle "START MINIMIZED" on, then re-run the same `reg query` command.
Expected: The value data now ends with ` --minimized`.

- [ ] **Step 5: Verify disabling removes the registry key**

Toggle "LAUNCH AT STARTUP" off, then re-run the `reg query` command.
Expected: `reg query` reports `ERROR: The system was unable to find the specified registry key or value.`

- [ ] **Step 6: Log out/in (or reboot) to confirm real startup behavior**

Re-enable "Launch at Startup" with "Start Minimized" on, then log out and back in (or restart Windows).
Expected: The app starts automatically with no visible window, and a tray icon is present.
