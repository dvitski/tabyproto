# Responsive Window Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the desktop app's layout adapt gracefully across window sizes, with a minimum window size constraint.

**Architecture:** A `WindowSize` enum (Compact/Medium/Expanded) is derived from `BoxWithConstraints` in `App` and distributed via `CompositionLocal`. Every composable reads `LocalWindowSize` directly — no prop threading. Breakpoints are 680dp and 960dp wide.

**Tech Stack:** Kotlin, Compose Multiplatform Desktop, `BoxWithConstraints`, `compositionLocalOf`, AWT `window.minimumSize`

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| **Create** | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/WindowSize.kt` | `WindowSize` enum, `windowSizeFor()`, `LocalWindowSize` |
| **Create** | `desktop/src/test/kotlin/cc/dvitski/tabyproto/desktop/WindowSizeTest.kt` | Unit tests for `windowSizeFor()` |
| **Modify** | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Main.kt` | Minimum window size enforcement |
| **Modify** | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt` | `BoxWithConstraints` wrapper, provide `LocalWindowSize` |
| **Modify** | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Sidebar.kt` | Collapse to icon-only in Compact, hide content blocks |
| **Modify** | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/HomeScreen.kt` | Stack panels vertically in Compact/Medium |
| **Modify** | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/SettingsScreen.kt` | Horizontal tab nav in Compact, narrow left column in Medium |

---

### Task 1: `WindowSize` enum with `windowSizeFor()` (TDD)

**Files:**
- Create: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/WindowSize.kt`
- Create: `desktop/src/test/kotlin/cc/dvitski/tabyproto/desktop/WindowSizeTest.kt`

- [ ] **Step 1: Write the failing test**

Create `desktop/src/test/kotlin/cc/dvitski/tabyproto/desktop/WindowSizeTest.kt`:

```kotlin
package cc.dvitski.tabyproto.desktop

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowSizeTest {
    @Test fun `below 680dp is Compact`()   = assertEquals(WindowSize.Compact,  windowSizeFor(679.dp))
    @Test fun `zero dp is Compact`()       = assertEquals(WindowSize.Compact,  windowSizeFor(0.dp))
    @Test fun `680dp is Medium`()          = assertEquals(WindowSize.Medium,   windowSizeFor(680.dp))
    @Test fun `959dp is Medium`()          = assertEquals(WindowSize.Medium,   windowSizeFor(959.dp))
    @Test fun `960dp is Expanded`()        = assertEquals(WindowSize.Expanded, windowSizeFor(960.dp))
    @Test fun `1100dp is Expanded`()       = assertEquals(WindowSize.Expanded, windowSizeFor(1100.dp))
}
```

- [ ] **Step 2: Run test — expect compile failure (symbol not found)**

```
./gradlew :desktop:test --tests "cc.dvitski.tabyproto.desktop.WindowSizeTest"
```

Expected: compilation error — `WindowSize` and `windowSizeFor` are not defined yet.

- [ ] **Step 3: Create `WindowSize.kt`**

Create `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/WindowSize.kt`:

```kotlin
package cc.dvitski.tabyproto.desktop

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowSize { Compact, Medium, Expanded }

fun windowSizeFor(width: Dp): WindowSize = when {
    width < 680.dp -> WindowSize.Compact
    width < 960.dp -> WindowSize.Medium
    else           -> WindowSize.Expanded
}

val LocalWindowSize = compositionLocalOf { WindowSize.Expanded }
```

- [ ] **Step 4: Run test — expect all 6 to pass**

```
./gradlew :desktop:test --tests "cc.dvitski.tabyproto.desktop.WindowSizeTest"
```

Expected: `BUILD SUCCESSFUL`, 6 tests passed.

- [ ] **Step 5: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/WindowSize.kt
git add desktop/src/test/kotlin/cc/dvitski/tabyproto/desktop/WindowSizeTest.kt
git commit -m "feat: add WindowSize enum and LocalWindowSize CompositionLocal"
```

---

### Task 2: Minimum window size in `Main.kt`

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Main.kt`

Current `Main.kt` (full file for reference):
```kotlin
fun main() = application {
    val appState = remember { AppState() }
    Window(
        onCloseRequest = { appState.close(); exitApplication() },
        title = "TabyProto",
        state = rememberWindowState(width = 1100.dp, height = 860.dp),
    ) {
        App(appState)
    }
}
```

- [ ] **Step 1: Replace `Main.kt` with size-constrained version**

```kotlin
package cc.dvitski.tabyproto.desktop

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() = application {
    val appState = remember { AppState() }
    Window(
        onCloseRequest = {
            appState.close()
            exitApplication()
        },
        title = "TabyProto",
        state = rememberWindowState(width = 1100.dp, height = 860.dp),
    ) {
        val density = LocalDensity.current
        SideEffect {
            window.minimumSize = with(density) {
                Dimension(520.dp.roundToPx(), 460.dp.roundToPx())
            }
        }
        App(appState)
    }
}
```

- [ ] **Step 2: Run the app and verify the constraint**

```
./gradlew :desktop:run
```

Drag the window as small as possible. It should stop resizing at roughly 520×460dp (physical pixels depend on display scale). Confirm it can no longer be dragged to a tiny or unusable size.

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Main.kt
git commit -m "feat: enforce 520x460dp minimum window size"
```

---

### Task 3: Provide `LocalWindowSize` in `App.kt`

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt`

The key change is replacing the outermost `Box(Modifier.fillMaxSize().background(...))` with `BoxWithConstraints`, deriving `windowSize` from `maxWidth`, and providing it via `CompositionLocalProvider`.

- [ ] **Step 1: Add import and replace outer `Box` with `BoxWithConstraints`**

Add this import at the top of `App.kt`:
```kotlin
import androidx.compose.foundation.layout.BoxWithConstraints
```

In the `App` composable, find the outermost `Box` inside `CompositionLocalProvider(LocalAppTheme provides theme)`:

```kotlin
// BEFORE (around line 346):
Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
    if (!thumbnailsReady) {
        // ...loading screen...
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            // ...
        }
        // ...
    }
    SnackbarHost(...)
}
```

Replace with:

```kotlin
// AFTER:
BoxWithConstraints(modifier = Modifier.fillMaxSize().background(theme.background)) {
    val windowSize = windowSizeFor(maxWidth)
    CompositionLocalProvider(LocalWindowSize provides windowSize) {
        if (!thumbnailsReady) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "LOADING $loadedCount / $total",
                    color = theme.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(
                    selectedScreen = selectedScreen,
                    devices = devices,
                    activeDeviceId = activeDeviceId,
                    lastSent = lastSent,
                    listeningState = listeningState,
                    musicState = musicState,
                    brightness = brightness,
                    onBrightnessChange = appState::setBrightness,
                    onNavigate = appState::navigate,
                    onVoiceClick = appState::showVoiceOverlay,
                    onMusicControl = appState::sendMusicControl,
                )
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (val screen = selectedScreen) {
                        Screen.Home -> HomeScreen(
                            devices = devices,
                            activeDeviceId = activeDeviceId,
                            lastSent = lastSent,
                            brightness = brightness,
                            onBrightnessChange = appState::setBrightness,
                            musicState = musicState,
                            onMusicControl = appState::sendMusicControl,
                        )
                        is Screen.Settings -> {
                            val filtered = Animations.all.filter { animation ->
                                (query.isBlank() || animation.id.contains(query, ignoreCase = true)) &&
                                when (typeFilter) {
                                    AnimationTypeFilter.All       -> true
                                    AnimationTypeFilter.Once      -> animation is Animation.Once
                                    AnimationTypeFilter.Loop      -> animation is Animation.Looping && animation.intro == null
                                    AnimationTypeFilter.IntroLoop -> animation is Animation.Looping && animation.intro != null
                                }
                            }
                            SettingsScreen(
                                selectedCategory = screen.category,
                                onCategorySelect = { appState.navigate(Screen.Settings(it)) },
                                animations = filtered,
                                query = query,
                                onQueryChange = appState::setQuery,
                                typeFilter = typeFilter,
                                onTypeFilterChange = appState::setTypeFilter,
                                thumbnailCache = appState.thumbnailCache,
                                sendingAnimation = sendingAnimation,
                                onSend = onSend,
                                devices = devices,
                                activeDeviceId = activeDeviceId,
                                onSelectDevice = appState::selectDevice,
                                onAddHost = appState::addManualHost,
                                onRemoveHost = appState::removeManualHost,
                                isDark = isDark,
                                currentPalette = palette,
                                onSetTheme = appState::setTheme,
                                brightness = brightness,
                                onBrightnessChange = appState::setBrightness,
                                musicState = musicState,
                                onMusicControl = appState::sendMusicControl,
                                idleSettings = idleSettings,
                                onIdleSettingsChange = appState::updateIdleSettings,
                                idleStatus = idleStatus,
                            )
                        }
                    }
                }
            }
            if (voiceOverlayVisible) {
                VoiceOverlay(listeningState = listeningState, onDismiss = appState::hideVoiceOverlay)
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
```

- [ ] **Step 2: Build to confirm no compile errors**

```
./gradlew :desktop:assemble
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt
git commit -m "feat: derive and provide LocalWindowSize from BoxWithConstraints in App"
```

---

### Task 4: Responsive `Sidebar.kt`

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Sidebar.kt`

Behaviour:
- `Compact` (56dp): logo mark "T", icons only (no labels), no device/music/voice blocks
- `Medium` (180dp): icons + labels, all blocks, tighter padding unchanged
- `Expanded` (200dp): no change from current

- [ ] **Step 1: Update `NavItem` to support compact mode**

Replace the `NavItem` composable at the bottom of `Sidebar.kt`:

```kotlin
@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, compact: Boolean = false, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    val bgColor = if (selected) theme.accent.copy(alpha = 0.18f) else theme.sidebarBg
    val contentColor = if (selected) theme.accent else theme.sidebarText.copy(alpha = 0.65f)

    Row(
        modifier = Modifier
            .padding(horizontal = if (compact) 4.dp else 10.dp)
            .fillMaxWidth()
            .clip(AppItemShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 0.dp else 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(24.dp))
        if (!compact) {
            Text(label, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = contentColor)
        }
    }
}
```

- [ ] **Step 2: Update the main `Sidebar` `Column` to read `LocalWindowSize`**

Replace the entire `Column` that opens at line 129 through the end of the `Sidebar` composable with:

```kotlin
val windowSize = LocalWindowSize.current
val sidebarWidth = when (windowSize) {
    WindowSize.Compact  -> 56.dp
    WindowSize.Medium   -> 180.dp
    WindowSize.Expanded -> 200.dp
}
val compact = windowSize == WindowSize.Compact

Column(
    modifier = modifier
        .width(sidebarWidth)
        .fillMaxHeight()
        .background(theme.sidebarBg),
    horizontalAlignment = Alignment.Start,
) {
    if (compact) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("T", color = theme.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        Text(
            text = "TABYPROTO",
            color = theme.accent,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 18.dp, top = 24.dp, bottom = 16.dp),
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(theme.sidebarText.copy(alpha = 0.12f)))
    Spacer(Modifier.height(6.dp))

    NavItem(Icons.Rounded.Home, "Home", selectedScreen == Screen.Home, compact = compact) { onNavigate(Screen.Home) }
    NavItem(Icons.Rounded.Settings, "Settings", selectedScreen is Screen.Settings, compact = compact) { onNavigate(Screen.Settings()) }

    Spacer(Modifier.weight(1f))

    if (!compact) {
        val active = devices.firstOrNull { it.id == activeDeviceId }
        val online = active?.online == true
        val blockBorder = theme.sidebarText.copy(alpha = 0.20f)

        // Device block
        Column(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .fillMaxWidth()
                .clip(AppItemShape)
                .border(1.dp, blockBorder, AppItemShape)
                .clickable { onNavigate(Screen.Settings(SettingsCategory.Device)) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.DeviceHub, contentDescription = null, tint = theme.sidebarText.copy(alpha = 0.4f), modifier = Modifier.size(15.dp))
                Text("DEVICE", color = theme.sidebarText.copy(alpha = 0.4f), fontSize = 11.sp, letterSpacing = 1.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (online) theme.onlineGreen else theme.sidebarText.copy(alpha = 0.3f)))
                Text(
                    text = when (active?.transport) {
                        TabyTransport.USB -> "USB"; TabyTransport.WIFI -> "WiFi"; TabyTransport.BLUETOOTH -> "BT"; null -> "—"
                    },
                    fontSize = 13.sp,
                    color = if (online) theme.onlineGreen else theme.sidebarText.copy(alpha = 0.4f),
                )
            }
            if (videoVisible) {
                VideoPlayerSurface(
                    playerState = playerState,
                    modifier = Modifier.fillMaxWidth().height(72.dp).clip(AppItemShape),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    text = lastSent?.displayName ?: if (active != null) "connected" else "no device",
                    fontSize = 12.sp,
                    color = theme.sidebarText.copy(alpha = 0.3f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (online) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Rounded.Brightness6,
                        contentDescription = null,
                        tint = theme.sidebarText.copy(alpha = 0.4f),
                        modifier = Modifier.size(13.dp),
                    )
                    Slider(
                        value = (brightness ?: 100).toFloat() / 100f,
                        onValueChange = { onBrightnessChange((it * 100).roundToInt().coerceIn(0, 100)) },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = theme.accent,
                            activeTrackColor = theme.accent,
                            inactiveTrackColor = theme.sidebarText.copy(alpha = 0.2f),
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        MusicBlockAnimated(musicState = musicState, onControl = onMusicControl)
        if (musicState is MusicState.Active) Spacer(Modifier.height(6.dp))

        // Voice block
        val voiceActive = listeningState != ListeningState.Idle
        Column(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .fillMaxWidth()
                .clip(AppItemShape)
                .border(1.dp, if (voiceActive) theme.accent else blockBorder, AppItemShape)
                .clickable(onClick = onVoiceClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Mic, contentDescription = null,
                    tint = if (voiceActive) theme.accent else theme.sidebarText.copy(alpha = 0.4f),
                    modifier = Modifier.size(15.dp))
                Text("VOICE", color = theme.sidebarText.copy(alpha = 0.4f), fontSize = 11.sp, letterSpacing = 1.sp)
            }
            Text(
                text = when (listeningState) {
                    ListeningState.Idle -> "Ready"
                    ListeningState.WakeWordDetected -> "Wake word"
                    ListeningState.Listening -> "Listening…"
                    ListeningState.Responding -> "Responding"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (voiceActive) theme.accent else theme.sidebarText.copy(alpha = 0.5f),
            )
        }
    }

    Spacer(Modifier.height(12.dp))
}
```

Note: the `val active`, `val online`, and `val voiceActive` declarations that were previously outside the `if (!compact)` block are now moved inside it. Remove those declarations from their original positions (they were around lines 152–154 and 229 in the original file).

- [ ] **Step 3: Run the app and verify sidebar at three sizes**

```
./gradlew :desktop:run
```

- Drag window narrow (<680dp wide): sidebar collapses to 56dp, shows "T" logo, icons only, no device/music/voice blocks.
- Drag to medium (700–950dp): sidebar at 180dp, icons + labels, blocks visible.
- Drag to wide (>960dp): sidebar at 200dp, layout unchanged from before.

- [ ] **Step 4: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Sidebar.kt
git commit -m "feat: responsive sidebar — icon-only at Compact, full at Medium/Expanded"
```

---

### Task 5: Responsive `HomeScreen.kt`

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/HomeScreen.kt`

- [ ] **Step 1: Add import and replace the panels `Row` with a size-aware branch**

Add this import at the top of `HomeScreen.kt`:
```kotlin
import cc.dvitski.tabyproto.desktop.WindowSize
```

In the `HomeScreen` composable, find the `Column` body. The `DevicePanel` call stays unchanged. Replace only the `Row` that lays out `MusicPanel` and `MobilePanel`:

```kotlin
// BEFORE:
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    MusicPanel(musicState, onMusicControl, Modifier.weight(1f))
    MobilePanel(Modifier.weight(1f))
}

// AFTER:
val windowSize = LocalWindowSize.current
if (windowSize == WindowSize.Expanded) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MusicPanel(musicState, onMusicControl, Modifier.weight(1f))
        MobilePanel(Modifier.weight(1f))
    }
} else {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MusicPanel(musicState, onMusicControl, Modifier.fillMaxWidth())
        MobilePanel(Modifier.fillMaxWidth())
    }
}
```

- [ ] **Step 2: Run the app and verify**

```
./gradlew :desktop:run
```

- Wide window (>960dp): Music and Mobile panels sit side-by-side.
- Narrower window (<960dp): Music and Mobile panels stack vertically, each full width.

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/HomeScreen.kt
git commit -m "feat: stack HomeScreen panels vertically in Compact and Medium"
```

---

### Task 6: Responsive `SettingsScreen.kt`

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/SettingsScreen.kt`

Behaviour:
- `Compact`: No left column. A horizontally scrollable `Row` of category tabs appears across the top of the content area.
- `Medium`: Left column at 160dp.
- `Expanded`: Left column at 200dp (unchanged).

`CategoryItem` currently hardcodes `fillMaxWidth()`. It needs a `modifier` parameter so it can be used both in the vertical list (full-width) and the horizontal tab row (wrap-content).

- [ ] **Step 1: Add `modifier` param to `CategoryItem`**

Replace the `CategoryItem` composable:

```kotlin
@Composable
private fun CategoryItem(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Box(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(AppItemShape)
            .background(if (selected) theme.accent.copy(alpha = 0.15f) else theme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) theme.accent else theme.textSecondary,
        )
    }
}
```

- [ ] **Step 2: Add horizontal scroll imports**

Add at the top of `SettingsScreen.kt`:
```kotlin
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
```

- [ ] **Step 3: Replace the outer `Row` in `SettingsScreen` with a size-aware layout**

Replace the body of `SettingsScreen` (everything inside the composable after the `val theme = ...` line) with:

```kotlin
val theme = LocalAppTheme.current
val windowSize = LocalWindowSize.current

if (windowSize == WindowSize.Compact) {
    Column(modifier = modifier.fillMaxSize().background(theme.background)) {
        // Horizontal scrollable tab row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingsCategory.entries.forEach { category ->
                CategoryItem(category.label, category == selectedCategory) { onCategorySelect(category) }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp)) {
            when (selectedCategory) {
                SettingsCategory.Music      -> MusicDetail(musicState, onMusicControl)
                SettingsCategory.Voice      -> PlaceholderDetail("Voice", "Wake word and microphone settings coming soon.")
                SettingsCategory.Device     -> DeviceDetail(devices, activeDeviceId, onSelectDevice, onAddHost, onRemoveHost, brightness, onBrightnessChange, animations, query, onQueryChange, typeFilter, onTypeFilterChange, thumbnailCache, sendingAnimation, onSend)
                SettingsCategory.Appearance -> AppearanceDetail(isDark, currentPalette, onSetTheme)
                SettingsCategory.Idle       -> IdleDetail(idleSettings, onIdleSettingsChange, idleStatus)
            }
        }
    }
} else {
    val navWidth = if (windowSize == WindowSize.Medium) 160.dp else 200.dp
    Row(modifier = modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier.width(navWidth).fillMaxHeight().background(theme.surface).padding(vertical = 14.dp),
        ) {
            Text(
                text = "SETTINGS",
                color = theme.textSecondary,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            SettingsCategory.entries.forEach { category ->
                CategoryItem(category.label, category == selectedCategory, modifier = Modifier.fillMaxWidth()) { onCategorySelect(category) }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(20.dp)) {
            when (selectedCategory) {
                SettingsCategory.Music      -> MusicDetail(musicState, onMusicControl)
                SettingsCategory.Voice      -> PlaceholderDetail("Voice", "Wake word and microphone settings coming soon.")
                SettingsCategory.Device     -> DeviceDetail(devices, activeDeviceId, onSelectDevice, onAddHost, onRemoveHost, brightness, onBrightnessChange, animations, query, onQueryChange, typeFilter, onTypeFilterChange, thumbnailCache, sendingAnimation, onSend)
                SettingsCategory.Appearance -> AppearanceDetail(isDark, currentPalette, onSetTheme)
                SettingsCategory.Idle       -> IdleDetail(idleSettings, onIdleSettingsChange, idleStatus)
            }
        }
    }
}
```

- [ ] **Step 4: Run the app and verify all three breakpoints for Settings**

```
./gradlew :desktop:run
```

Navigate to Settings and resize the window:
- **Compact** (<680dp wide): No left sidebar. A horizontal row of tab labels ("Music", "Voice", "Device", "Appearance", "Idle") appears across the top. Tapping a tab switches the content below.
- **Medium** (680–960dp): Left sidebar at 160dp with vertical category list. Content fills the rest.
- **Expanded** (>960dp): Left sidebar at 200dp, unchanged from before.

- [ ] **Step 5: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/SettingsScreen.kt
git commit -m "feat: responsive SettingsScreen — horizontal tabs in Compact, narrower column in Medium"
```

---

### Task 7: Run all tests and merge

- [ ] **Step 1: Run full test suite**

```
./gradlew test
```

Expected: all tests pass, including the 6 `WindowSizeTest` cases.

- [ ] **Step 2: Final visual check — run the app and exercise all breakpoints**

```
./gradlew :desktop:run
```

Checklist:
- [ ] Window can't be dragged below ~520×460dp
- [ ] At <680dp: sidebar is 56dp icon-only; Home stacks panels; Settings shows top tabs
- [ ] At 680–960dp: sidebar is 180dp with labels and blocks; Home stacks panels; Settings has 160dp left column
- [ ] At >960dp: all layouts match the original design exactly
- [ ] Voice overlay still appears correctly at all sizes
- [ ] Loading screen ("LOADING N / M") still centers correctly at all sizes

- [ ] **Step 3: Merge latest → master per branch convention (if applicable)**

```bash
git checkout master
git merge --ff-only latest
git push origin latest
```
