# Launch at Startup — Design

## Goal

Let users enable "launch at startup" for the packaged Windows desktop app, with an
optional "start minimized" sub-setting that controls window visibility specifically
for startup-triggered launches (manual launches always show the window).

## Mechanism

Windows startup registration via the per-user `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`
registry key. This requires no admin rights, matching the existing `perUserInstall = true`
jpackage config. The JVM has no native API for this key (`java.util.prefs` on Windows
writes to a different registry subtree), so registration is done by shelling out to the
built-in `reg.exe` via `ProcessBuilder`:

- Enable: `reg add HKCU\Software\Microsoft\Windows\CurrentVersion\Run /v TabyProto-Desktop /t REG_SZ /d "<command>" /f`
- Disable: `reg delete HKCU\Software\Microsoft\Windows\CurrentVersion\Run /v TabyProto-Desktop /f`

where `<command>` is `"<exePath>" --minimized` if "start minimized" is on, or just
`"<exePath>"` otherwise.

## Locating the installed exe

jpackage's Windows app-image layout places the native launcher at
`<install-dir>/TabyProto-Desktop.exe`, with `java.home` resolving to
`<install-dir>/runtime` at runtime. `bootstrap/Launcher.kt` already uses this trick to
locate `app/`. The same computation gives the exe path:

```kotlin
val installDir = Paths.get(System.getProperty("java.home")).parent
val exe = installDir.resolve("TabyProto-Desktop.exe")
```

If `exe` doesn't exist (e.g. running via `./gradlew :desktop:run`), the feature is
unavailable in that session — the toggle renders disabled with a short caption. This
mirrors how the bootstrap update-check already skips gracefully when `app/` isn't a
real install directory.

## New component: `StartupLaunchManager` (desktop module)

```kotlin
object StartupLaunchManager {
    fun installedExePath(): File?
    fun sync(enabled: Boolean, minimized: Boolean, exePath: File)
}
```

- `installedExePath()` returns null when not running from an installed app (dev mode).
- `sync(...)` builds the `reg.exe` argv (pure, unit-testable) and runs it via
  `ProcessBuilder`, wrapped in `runCatching` with a logged warning on failure. Best
  effort — no error dialog, consistent with how update-check failures are handled
  elsewhere in this codebase.
- The argv-building logic is split into a pure function so it can be tested without
  touching the real registry, following the existing pattern in `KnownGamesTest`.

## Settings storage

Extend `GeneralStore` (`java.util.prefs`-backed, same pattern as `minimizeToTray`)
with two new booleans, both defaulting to `false`:

- `launchAtStartupEnabled`
- `startMinimizedOnStartup`

`AppState` gains:

```kotlin
val launchAtStartup: StateFlow<Boolean>
val startMinimizedOnStartup: StateFlow<Boolean>
fun setLaunchAtStartup(enabled: Boolean)
fun setStartMinimizedOnStartup(enabled: Boolean)
```

Each setter updates its `StateFlow`, persists via `GeneralStore`, then calls
`StartupLaunchManager.sync(launchAtStartup.value, startMinimizedOnStartup.value, exePath)`
so toggling either setting re-syncs the registry value to match current combined state.
If `installedExePath()` is null, the setters still persist the preference (so it takes
effect later if the user installs properly) but skip the registry call.

## Startup arg handling

`Main.kt`'s `main()` becomes `main(args: Array<String>)`. If `args.contains("--minimized")`,
the initial `isWindowVisible` state is seeded to `false` instead of `true`. No change
needed in `bootstrap/Launcher.kt` — it already forwards `args` through to
`MainKt.main`.

## UI changes

`SettingsScreen.kt`, `AppearanceDetail`: add a new toggle row below "Minimize To Tray":

```
LAUNCH AT STARTUP                    [toggle]
  (shown only when enabled)
  START MINIMIZED                    [toggle]
```

When `StartupLaunchManager.installedExePath()` is null, the "Launch at Startup" toggle
renders disabled with caption "Only available in the installed app."

## Testing

Unit test the pure reg.exe argv-building function: given an exe path and
enabled/minimized flags, assert the expected `add` or `delete` argv. No test touches
the real Windows registry or spawns `reg.exe`.

## Out of scope

- macOS/Linux equivalents (login items / systemd) — this app is Windows-only per the
  existing `targetFormats(TargetFormat.Exe)` packaging.
- Detecting/repairing a stale registry entry after an app reinstall to a different
  path — the per-user install path is stable across updates, so this isn't needed.
