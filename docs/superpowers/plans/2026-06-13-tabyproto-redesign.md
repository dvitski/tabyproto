# TabyProto UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current dark-purple AI-cliché aesthetic with a Chunky Blocks toybox design system — zero rounded corners, 2dp borders, 3dp accent panel top-bars — driven by a two-axis theme engine (Light/Dark × 7 named color palettes).

**Architecture:** A new `AppTheme` data class (built by `buildTheme(isDark, palette)`) is provided via `CompositionLocalProvider(LocalAppTheme provides theme)` at the root. All composables read `LocalAppTheme.current` instead of using hardcoded colors. `ThemeStore` persists the user's selection to `Preferences`. A new `Appearance` settings category exposes a Light/Dark toggle and 7 palette swatches.

**Tech Stack:** Kotlin, Jetpack Compose for Desktop (org.jetbrains.compose), `java.util.prefs.Preferences` for persistence, no new dependencies.

**Spec:** `docs/superpowers/specs/2026-06-13-tabyproto-redesign-design.md`

---

## File Map

| File | Action |
|---|---|
| `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/AppTheme.kt` | **Create** — `ColorPalette` enum, `AppTheme` data class, `buildTheme()`, `LocalAppTheme` |
| `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/ThemeStore.kt` | **Create** — persist/load `isDark` + `ColorPalette` via `Preferences` |
| `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt` | **Modify** — add theme state + `setTheme()` to `AppState`; add `Appearance` to `SettingsCategory`; wrap `App` composable with `CompositionLocalProvider`; retheme loading screen |
| `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Main.kt` | **Modify** — update window title to "TabyProto" |
| `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Sidebar.kt` | **Modify** — full redesign: 160dp wide, inverted-dark, accent-fill active nav, device + voice blocks |
| `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/HomeScreen.kt` | **Modify** — full redesign: themed panels with 3dp accent top-bars, 2dp borders |
| `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/SettingsScreen.kt` | **Modify** — full redesign: themed category list, themed text field, new Appearance panel |
| `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/AnimationCell.kt` | **Modify** — remove rounded corners, themed colors, remove Material progress indicators |
| `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/DeviceSelector.kt` | **Modify** — replace hardcoded colors with theme, remove rounded corners, themed dialog |
| `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/VoiceOverlay.kt` | **Modify** — sharp rectangle, accent border, themed text |

---

## Task 1: Create AppTheme.kt

**Files:**
- Create: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/AppTheme.kt`

- [ ] **Step 1: Create the file**

```kotlin
package cc.dvitski.tabyproto.desktop

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ColorPalette(
    val displayName: String,
    val lightAccent: Color,
    val darkAccent: Color,
) {
    EMBER("Ember", Color(0xFFC4271A), Color(0xFFFF4D3D)),
    TANGERINE("Tangerine", Color(0xFFC45A0A), Color(0xFFFF8C3A)),
    SUNFLOWER("Sunflower", Color(0xFF9A7A00), Color(0xFFF5C518)),
    FERN("Fern", Color(0xFF1A6B3A), Color(0xFF3DCC7A)),
    TEAL("Teal", Color(0xFF0A6B6B), Color(0xFF00C7C7)),
    OCEAN("Ocean", Color(0xFF1A4A9A), Color(0xFF5B9EFF)),
    BERRY("Berry", Color(0xFF9A1A6B), Color(0xFFFF5BB8)),
}

data class AppTheme(
    val isDark: Boolean,
    val palette: ColorPalette,
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val sidebarBg: Color,
    val sidebarText: Color,
    val accent: Color,
    val onlineGreen: Color,
)

fun buildTheme(isDark: Boolean, palette: ColorPalette): AppTheme {
    val accent = if (isDark) palette.darkAccent else palette.lightAccent
    return if (isDark) {
        AppTheme(
            isDark = true,
            palette = palette,
            background = Color(0xFF1A1714),
            surface = Color(0xFF242017),
            surface2 = Color(0xFF2E2B20),
            border = Color(0xFFEDE6DC),
            textPrimary = Color(0xFFEDE6DC),
            textSecondary = Color(0xFF6E6A60),
            sidebarBg = Color(0xFF111008),
            sidebarText = Color(0xFFEDE6DC),
            accent = accent,
            onlineGreen = Color(0xFF3DCC7A),
        )
    } else {
        AppTheme(
            isDark = false,
            palette = palette,
            background = Color(0xFFFAF7F2),
            surface = Color(0xFFF0EBE3),
            surface2 = Color(0xFFE5DDD4),
            border = Color(0xFF1A1614),
            textPrimary = Color(0xFF1A1614),
            textSecondary = Color(0xFF7A6E68),
            sidebarBg = Color(0xFF1A1614),
            sidebarText = Color(0xFFFAF7F2),
            accent = accent,
            onlineGreen = Color(0xFF1A6B3A),
        )
    }
}

val LocalAppTheme = compositionLocalOf { buildTheme(isDark = true, palette = ColorPalette.OCEAN) }
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :desktop:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/AppTheme.kt
git commit -m "feat: add AppTheme data class, ColorPalette enum, buildTheme factory"
```

---

## Task 2: Create ThemeStore.kt

**Files:**
- Create: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/ThemeStore.kt`

- [ ] **Step 1: Create the file**

```kotlin
package cc.dvitski.tabyproto.desktop

import java.util.prefs.Preferences

class ThemeStore {
    private val prefs = Preferences.userNodeForPackage(ThemeStore::class.java)

    fun load(): Pair<Boolean, ColorPalette> {
        val isDark = prefs.getBoolean(KEY_DARK, true)
        val paletteName = prefs.get(KEY_PALETTE, ColorPalette.OCEAN.name)
        val palette = ColorPalette.entries.firstOrNull { it.name == paletteName }
            ?: ColorPalette.OCEAN
        return Pair(isDark, palette)
    }

    fun save(isDark: Boolean, palette: ColorPalette) {
        prefs.putBoolean(KEY_DARK, isDark)
        prefs.put(KEY_PALETTE, palette.name)
    }

    private companion object {
        const val KEY_DARK = "isDark"
        const val KEY_PALETTE = "palette"
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :desktop:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/ThemeStore.kt
git commit -m "feat: add ThemeStore to persist light/dark mode and palette selection"
```

---

## Task 3: Update App.kt and Main.kt

Add theme state to `AppState`, wire `LocalAppTheme` provider in `App`, retheme the loading screen, add `Appearance` to the enum, update window title.

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt`
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Main.kt`

- [ ] **Step 1: Replace App.kt entirely**

```kotlin
package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.TabyDevice
import cc.dvitski.tabyproto.TabyDeviceMonitor
import cc.dvitski.tabyproto.TabyTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── Routing types ──────────────────────────────────────────────────────────────

sealed class Screen {
    object Home : Screen()
    data class Settings(val category: SettingsCategory = SettingsCategory.PlayAnimations) : Screen()
}

enum class SettingsCategory { PlayAnimations, Music, Voice, Device, Appearance }

enum class ListeningState { Idle, WakeWordDetected, Listening, Responding }

// ── AppState ───────────────────────────────────────────────────────────────────

class AppState {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val hostsStore = ManualHostsStore()
    private val monitor = TabyDeviceMonitor(initialManualHosts = hostsStore.load())
    private val themeStore = ThemeStore()

    val devices: StateFlow<List<TabyDevice>> = monitor.devices

    private val _activeDeviceId = MutableStateFlow<String?>(null)
    val activeDeviceId: StateFlow<String?> = _activeDeviceId.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _lastSent = MutableStateFlow<Animation?>(null)
    val lastSent: StateFlow<Animation?> = _lastSent.asStateFlow()

    private val _sendingAnimation = MutableStateFlow<Animation?>(null)
    val sendingAnimation: StateFlow<Animation?> = _sendingAnimation.asStateFlow()

    val thumbnailCache: ThumbnailCache = ThumbnailCache()
    val totalAnimations: Int = Animation.entries.size

    private val _selectedScreen = MutableStateFlow<Screen>(Screen.Home)
    val selectedScreen: StateFlow<Screen> = _selectedScreen.asStateFlow()

    private val _voiceOverlayVisible = MutableStateFlow(false)
    val voiceOverlayVisible: StateFlow<Boolean> = _voiceOverlayVisible.asStateFlow()

    private val _listeningState = MutableStateFlow(ListeningState.Idle)
    val listeningState: StateFlow<ListeningState> = _listeningState.asStateFlow()

    private val _initialTheme = themeStore.load()
    private val _isDark = MutableStateFlow(_initialTheme.first)
    private val _palette = MutableStateFlow(_initialTheme.second)
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()
    val palette: StateFlow<ColorPalette> = _palette.asStateFlow()

    fun navigate(screen: Screen) { _selectedScreen.value = screen }
    fun showVoiceOverlay() { _voiceOverlayVisible.value = true }
    fun hideVoiceOverlay() { _voiceOverlayVisible.value = false }

    fun setTheme(isDark: Boolean, palette: ColorPalette) {
        _isDark.value = isDark
        _palette.value = palette
        themeStore.save(isDark, palette)
    }

    init {
        monitor.start()
        thumbnailCache.preloadAll(Animation.entries)
        AnimationResources.preloadAll(Animation.entries, scope)
        scope.launch(Dispatchers.IO) { monitor.manualHosts.collect { hostsStore.save(it) } }
        scope.launch {
            monitor.devices.collect { list ->
                val activeId = _activeDeviceId.value
                if (activeId != null && list.none { it.id == activeId }) {
                    _activeDeviceId.value = null
                }
                if (_activeDeviceId.value == null) {
                    val candidate = list.filter { it.online }
                        .minByOrNull { if (it.transport == TabyTransport.USB) 0 else 1 }
                    if (candidate != null) _activeDeviceId.value = candidate.id
                }
            }
        }
    }

    fun selectDevice(id: String) { _activeDeviceId.value = id }
    fun addManualHost(host: String) = monitor.addManualHost(host)
    fun removeManualHost(host: String) = monitor.removeManualHost(host)
    fun setQuery(q: String) { _query.value = q }

    suspend fun sendAnimation(animation: Animation): Result<Unit> {
        val device = devices.value.firstOrNull { it.id == _activeDeviceId.value }
            ?: return Result.failure(IllegalStateException("No Taby connected"))
        if (!device.online) {
            return Result.failure(IllegalStateException("${device.label} is offline"))
        }
        if (!_sendingAnimation.compareAndSet(null, animation)) {
            return Result.failure(IllegalStateException("Still sending ${_sendingAnimation.value?.id ?: "an animation"}"))
        }
        return try {
            val result = monitor.session(device).play(animation)
            if (result.ok) {
                _lastSent.value = animation
                Result.success(Unit)
            } else {
                Result.failure(RuntimeException(result.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _sendingAnimation.value = null
        }
    }

    fun close() {
        scope.cancel()
        monitor.close()
    }
}

// ── App composable ─────────────────────────────────────────────────────────────

@Composable
fun App(appState: AppState) {
    val isDark by appState.isDark.collectAsState()
    val palette by appState.palette.collectAsState()
    val theme = buildTheme(isDark, palette)

    CompositionLocalProvider(LocalAppTheme provides theme) {
        val devices by appState.devices.collectAsState()
        val activeDeviceId by appState.activeDeviceId.collectAsState()
        val query by appState.query.collectAsState()
        val lastSent by appState.lastSent.collectAsState()
        val sendingAnimation by appState.sendingAnimation.collectAsState()
        val loadedCount by appState.thumbnailCache.loadedCount.collectAsState()
        val selectedScreen by appState.selectedScreen.collectAsState()
        val voiceOverlayVisible by appState.voiceOverlayVisible.collectAsState()
        val listeningState by appState.listeningState.collectAsState()
        val total = appState.totalAnimations
        val thumbnailsReady = loadedCount >= total

        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        val onSend: (Animation) -> Unit = remember(scope, snackbarHostState) {
            { animation ->
                scope.launch {
                    val result = appState.sendAnimation(animation)
                    result.onFailure { e ->
                        snackbarHostState.showSnackbar(message = e.message ?: "Send failed")
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.background),
        ) {
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
                        onNavigate = appState::navigate,
                        onVoiceClick = appState::showVoiceOverlay,
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (val screen = selectedScreen) {
                            Screen.Home -> HomeScreen(
                                devices = devices,
                                activeDeviceId = activeDeviceId,
                                lastSent = lastSent,
                            )
                            is Screen.Settings -> {
                                val filtered = Animation.entries.filter { animation ->
                                    query.isBlank() || animation.id.contains(query, ignoreCase = true)
                                }
                                SettingsScreen(
                                    selectedCategory = screen.category,
                                    onCategorySelect = { appState.navigate(Screen.Settings(it)) },
                                    animations = filtered,
                                    query = query,
                                    onQueryChange = appState::setQuery,
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
                                )
                            }
                        }
                    }
                }

                if (voiceOverlayVisible) {
                    VoiceOverlay(
                        listeningState = listeningState,
                        onDismiss = appState::hideVoiceOverlay,
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
```

- [ ] **Step 2: Update Main.kt window title**

Replace the `title` parameter value in `Main.kt`:

```kotlin
title = "TabyProto",
```

(The rest of `Main.kt` is unchanged.)

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :desktop:compileKotlin`
Expected: BUILD SUCCESSFUL (SettingsScreen signature mismatch is expected — will be fixed in Task 6)

- [ ] **Step 4: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Main.kt
git commit -m "feat: wire theme state into AppState and App composable, add Appearance category"
```

---

## Task 4: Redesign Sidebar.kt

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Sidebar.kt`

- [ ] **Step 1: Replace Sidebar.kt entirely**

```kotlin
package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.TabyDevice
import cc.dvitski.tabyproto.TabyTransport

@Composable
fun Sidebar(
    selectedScreen: Screen,
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    lastSent: Animation?,
    listeningState: ListeningState,
    onNavigate: (Screen) -> Unit,
    onVoiceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current

    Column(
        modifier = modifier
            .width(160.dp)
            .fillMaxHeight()
            .background(theme.sidebarBg),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "TABYPROTO",
            color = theme.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 12.dp),
        )

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(theme.sidebarText.copy(alpha = 0.12f))
        )

        Spacer(Modifier.height(4.dp))

        NavItem(
            glyph = "⬡",
            label = "Home",
            selected = selectedScreen == Screen.Home,
            onClick = { onNavigate(Screen.Home) },
        )
        NavItem(
            glyph = "◈",
            label = "Settings",
            selected = selectedScreen is Screen.Settings,
            onClick = { onNavigate(Screen.Settings()) },
        )

        Spacer(Modifier.weight(1f))

        val active = devices.firstOrNull { it.id == activeDeviceId }
        val online = active?.online == true
        val blockBorder = theme.sidebarText.copy(alpha = 0.25f)

        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .border(2.dp, blockBorder)
                .clickable { onNavigate(Screen.Settings(SettingsCategory.Device)) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "DEVICE",
                color = theme.sidebarText.copy(alpha = 0.4f),
                fontSize = 7.sp,
                letterSpacing = 1.sp,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (online) theme.onlineGreen else theme.sidebarText.copy(alpha = 0.3f))
                )
                Text(
                    text = when (active?.transport) {
                        TabyTransport.USB -> "USB"
                        TabyTransport.WIFI -> "WiFi"
                        TabyTransport.BLUETOOTH -> "BT"
                        null -> "—"
                    },
                    fontSize = 9.sp,
                    color = if (online) theme.onlineGreen else theme.sidebarText.copy(alpha = 0.4f),
                )
            }
            Text(
                text = lastSent?.id ?: if (active != null) "connected" else "no device",
                fontSize = 8.sp,
                color = theme.sidebarText.copy(alpha = 0.3f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(4.dp))

        val voiceActive = listeningState != ListeningState.Idle
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .border(2.dp, if (voiceActive) theme.accent else blockBorder)
                .clickable(onClick = onVoiceClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "VOICE",
                color = theme.sidebarText.copy(alpha = 0.4f),
                fontSize = 7.sp,
                letterSpacing = 1.sp,
            )
            Text(
                text = when (listeningState) {
                    ListeningState.Idle -> "READY"
                    ListeningState.WakeWordDetected -> "WAKE WORD"
                    ListeningState.Listening -> "LISTENING"
                    ListeningState.Responding -> "RESPONDING"
                },
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (voiceActive) theme.accent else theme.sidebarText.copy(alpha = 0.5f),
                letterSpacing = 0.5.sp,
            )
        }

        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun NavItem(
    glyph: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val bgColor = if (selected) theme.accent else theme.sidebarBg
    val textColor = if (selected) theme.sidebarBg else theme.sidebarText.copy(alpha = 0.7f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(glyph, fontSize = 12.sp, color = textColor)
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :desktop:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Sidebar.kt
git commit -m "feat: redesign Sidebar — 160dp wide, inverted dark, accent-fill active nav, themed blocks"
```

---

## Task 5: Redesign HomeScreen.kt

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/HomeScreen.kt`

- [ ] **Step 1: Replace HomeScreen.kt entirely**

```kotlin
package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.TabyDevice
import cc.dvitski.tabyproto.TabyTransport

@Composable
fun HomeScreen(
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    lastSent: Animation?,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DevicePanel(
            devices = devices,
            activeDeviceId = activeDeviceId,
            lastSent = lastSent,
            modifier = Modifier.fillMaxWidth().weight(1.4f),
        )
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MusicPanel(modifier = Modifier.weight(1f).fillMaxHeight())
            MobilePanel(modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun PanelHeader(label: String) {
    val theme = LocalAppTheme.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(theme.accent)
    )
    Text(
        text = label,
        color = theme.accent,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun DevicePanel(
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    lastSent: Animation?,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val active = devices.firstOrNull { it.id == activeDeviceId }
    val online = active?.online == true

    Column(
        modifier = modifier
            .background(theme.surface)
            .border(2.dp, theme.border),
    ) {
        PanelHeader("DEVICE")
        Column(
            modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (online) theme.onlineGreen else theme.textSecondary)
                )
                Text(
                    text = if (active != null && online) {
                        "Connected · ${when (active.transport) {
                            TabyTransport.USB -> "USB"
                            TabyTransport.WIFI -> "WiFi"
                            TabyTransport.BLUETOOTH -> "BT"
                        }}"
                    } else "Disconnected",
                    color = theme.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "NOW PLAYING",
                    color = theme.textSecondary,
                    fontSize = 8.sp,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = lastSent?.id ?: "—",
                    color = theme.textPrimary,
                    fontSize = 8.sp,
                )
            }
        }
    }
}

@Composable
private fun MusicPanel(modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Column(
        modifier = modifier
            .background(theme.surface)
            .border(2.dp, theme.border),
    ) {
        PanelHeader("MUSIC")
        Text(
            text = "Not configured",
            color = theme.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
    }
}

@Composable
private fun MobilePanel(modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Column(
        modifier = modifier
            .background(theme.surface)
            .border(2.dp, theme.border),
    ) {
        PanelHeader("MOBILE")
        Text(
            text = "No device paired",
            color = theme.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :desktop:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/HomeScreen.kt
git commit -m "feat: redesign HomeScreen — themed panels with accent top-bars and 2dp borders"
```

---

## Task 6: Redesign SettingsScreen.kt

Adds three new parameters (`isDark`, `currentPalette`, `onSetTheme`) and a new Appearance panel. Replaces hardcoded colors and Material `OutlinedTextField` with themed equivalents.

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/SettingsScreen.kt`

- [ ] **Step 1: Replace SettingsScreen.kt entirely**

```kotlin
package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.TabyDevice

private val SettingsCategory.label: String
    get() = when (this) {
        SettingsCategory.PlayAnimations -> "Play Animations"
        SettingsCategory.Music -> "Music"
        SettingsCategory.Voice -> "Voice"
        SettingsCategory.Device -> "Device"
        SettingsCategory.Appearance -> "Appearance"
    }

@Composable
fun SettingsScreen(
    selectedCategory: SettingsCategory,
    onCategorySelect: (SettingsCategory) -> Unit,
    animations: List<Animation>,
    query: String,
    onQueryChange: (String) -> Unit,
    thumbnailCache: ThumbnailCache,
    sendingAnimation: Animation?,
    onSend: (Animation) -> Unit,
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    onSelectDevice: (String) -> Unit,
    onAddHost: (String) -> Unit,
    onRemoveHost: (String) -> Unit,
    isDark: Boolean,
    currentPalette: ColorPalette,
    onSetTheme: (Boolean, ColorPalette) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    Row(modifier = modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier
                .width(160.dp)
                .fillMaxHeight()
                .background(theme.surface),
        ) {
            Text(
                text = "SETTINGS",
                color = theme.textSecondary,
                fontSize = 7.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            )
            SettingsCategory.entries.forEach { category ->
                CategoryItem(
                    label = category.label,
                    selected = category == selectedCategory,
                    onClick = { onCategorySelect(category) },
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
            when (selectedCategory) {
                SettingsCategory.PlayAnimations -> PlayAnimationsDetail(
                    animations = animations,
                    query = query,
                    onQueryChange = onQueryChange,
                    thumbnailCache = thumbnailCache,
                    sendingAnimation = sendingAnimation,
                    onSend = onSend,
                )
                SettingsCategory.Music -> PlaceholderDetail(
                    title = "Music",
                    body = "Music integration coming soon.",
                )
                SettingsCategory.Voice -> PlaceholderDetail(
                    title = "Voice",
                    body = "Wake word and microphone settings coming soon.",
                )
                SettingsCategory.Device -> DeviceDetail(
                    devices = devices,
                    activeDeviceId = activeDeviceId,
                    onSelect = onSelectDevice,
                    onAddHost = onAddHost,
                    onRemoveHost = onRemoveHost,
                )
                SettingsCategory.Appearance -> AppearanceDetail(
                    isDark = isDark,
                    currentPalette = currentPalette,
                    onSetTheme = onSetTheme,
                )
            }
        }
    }
}

@Composable
private fun CategoryItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    val bgColor = if (selected) theme.accent else theme.surface
    val textColor = if (selected) theme.background else theme.textSecondary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
        )
    }
}

@Composable
private fun PlayAnimationsDetail(
    animations: List<Animation>,
    query: String,
    onQueryChange: (String) -> Unit,
    thumbnailCache: ThumbnailCache,
    sendingAnimation: Animation?,
    onSend: (Animation) -> Unit,
) {
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Play Animations",
            color = theme.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, theme.border)
                .background(theme.surface2)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = theme.textPrimary, fontSize = 11.sp),
                cursorBrush = SolidColor(theme.accent),
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.isEmpty()) {
                Text(
                    text = "Filter animations…",
                    color = theme.textSecondary,
                    fontSize = 11.sp,
                )
            }
        }
        AnimationGrid(
            animations = animations,
            thumbnailCache = thumbnailCache,
            sendingAnimation = sendingAnimation,
            onSend = onSend,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

@Composable
private fun PlaceholderDetail(title: String, body: String) {
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(body, color = theme.textSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun DeviceDetail(
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    onSelect: (String) -> Unit,
    onAddHost: (String) -> Unit,
    onRemoveHost: (String) -> Unit,
) {
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Device", color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        DeviceSelector(
            devices = devices,
            activeDeviceId = activeDeviceId,
            onSelect = onSelect,
            onAddHost = onAddHost,
            onRemoveHost = onRemoveHost,
        )
    }
}

@Composable
private fun AppearanceDetail(
    isDark: Boolean,
    currentPalette: ColorPalette,
    onSetTheme: (Boolean, ColorPalette) -> Unit,
) {
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Appearance", color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "MODE",
                color = theme.textSecondary,
                fontSize = 8.sp,
                letterSpacing = 1.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                ModeButton(
                    label = "LIGHT",
                    selected = !isDark,
                    onClick = { onSetTheme(false, currentPalette) },
                    modifier = Modifier.weight(1f),
                )
                ModeButton(
                    label = "DARK",
                    selected = isDark,
                    onClick = { onSetTheme(true, currentPalette) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "PALETTE",
                color = theme.textSecondary,
                fontSize = 8.sp,
                letterSpacing = 1.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPalette.entries.forEach { palette ->
                    PaletteSwatch(
                        palette = palette,
                        isDark = isDark,
                        selected = palette == currentPalette,
                        onClick = { onSetTheme(isDark, palette) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val bgColor = if (selected) theme.accent else theme.surface
    val textColor = if (selected) theme.background else theme.textSecondary

    Box(
        modifier = modifier
            .border(2.dp, theme.border)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun PaletteSwatch(
    palette: ColorPalette,
    isDark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val swatchColor = if (isDark) palette.darkAccent else palette.lightAccent

    Box(
        modifier = Modifier
            .size(40.dp)
            .border(if (selected) 3.dp else 2.dp, if (selected) theme.border else swatchColor)
            .background(swatchColor)
            .clickable(onClick = onClick),
    )
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :desktop:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/SettingsScreen.kt
git commit -m "feat: redesign SettingsScreen — themed categories, BasicTextField, Appearance panel"
```

---

## Task 7: Redesign AnimationCell.kt

Remove rounded corners and Material progress indicators. Use theme colors. Replace the sending overlay with a plain text indicator.

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/AnimationCell.kt`

- [ ] **Step 1: Replace AnimationCell.kt entirely**

```kotlin
package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dvitski.tabyproto.Animation
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState

private val SendingScrim = Color(0xAA000000)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AnimationCell(
    animation: Animation,
    thumbnail: ImageBitmap?,
    isSending: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    var hovered by remember { mutableStateOf(false) }
    val playerState = rememberVideoPlayerState()

    DisposableEffect(playerState) {
        onDispose { playerState.dispose() }
    }

    LaunchedEffect(isSending) {
        if (isSending) hovered = false
    }

    LaunchedEffect(hovered) {
        if (hovered) {
            val path = AnimationResources.videoPath(animation.id) ?: return@LaunchedEffect
            playerState.loop = true
            playerState.openUri(path)
        } else {
            playerState.pause()
        }
    }

    val borderColor = if (hovered) theme.accent else theme.border
    val borderWidth = if (hovered) 2.dp else 1.dp

    Column(
        modifier = modifier
            .background(theme.surface)
            .border(BorderStroke(borderWidth, borderColor))
            .clickable(enabled = !isSending, onClick = onSend)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            val videoReady = hovered && playerState.isPlaying && !playerState.isLoading
            if (videoReady) {
                VideoPlayerSurface(
                    playerState = playerState,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = animation.id,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = "…",
                    color = theme.textSecondary,
                    fontSize = 16.sp,
                )
            }

            if (isSending) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SendingScrim),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "SENDING",
                        color = theme.accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }

        Text(
            text = animation.id,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = theme.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :desktop:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/AnimationCell.kt
git commit -m "feat: redesign AnimationCell — sharp corners, themed colors, text sending indicator"
```

---

## Task 8: Style DeviceSelector.kt

Replace hardcoded colors with theme tokens, remove rounded corners, theme the dialog.

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/DeviceSelector.kt`

- [ ] **Step 1: Replace DeviceSelector.kt entirely**

```kotlin
package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cc.dvitski.tabyproto.DeviceSource
import cc.dvitski.tabyproto.TabyDevice
import cc.dvitski.tabyproto.TabyTransport

@Composable
fun DeviceSelector(
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    onSelect: (String) -> Unit,
    onAddHost: (String) -> Unit,
    onRemoveHost: (String) -> Unit,
) {
    val theme = LocalAppTheme.current
    var expanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    val active = devices.firstOrNull { it.id == activeDeviceId }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .border(2.dp, theme.border)
                .background(theme.surface)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            StatusDot(
                color = when {
                    active == null -> theme.textSecondary
                    active.online -> theme.onlineGreen
                    else -> theme.textSecondary
                },
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = active?.label ?: "No device",
                color = theme.textPrimary,
                fontSize = 13.sp,
            )
            if (active != null) {
                Spacer(Modifier.width(6.dp))
                TransportTag(active.transport)
            }
            Spacer(Modifier.width(4.dp))
            Text("▾", color = theme.textPrimary, fontSize = 11.sp)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (devices.isEmpty()) {
                DropdownMenuItem(onClick = {}, enabled = false) {
                    Text("No devices found", fontSize = 13.sp)
                }
            }
            devices.forEach { device ->
                DropdownMenuItem(onClick = {
                    onSelect(device.id)
                    expanded = false
                }) {
                    StatusDot(color = if (device.online) theme.onlineGreen else theme.textSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = device.label,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f).widthIn(min = 120.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    TransportTag(device.transport)
                    val src = device.source
                    if (src is DeviceSource.Wifi && src.manual) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "✕",
                            fontSize = 12.sp,
                            color = theme.textSecondary,
                            modifier = Modifier.clickable { onRemoveHost(src.host) },
                        )
                    }
                }
            }
            DropdownMenuItem(onClick = {
                expanded = false
                showAddDialog = true
            }) {
                Text("Add device…", fontSize = 13.sp)
            }
        }
    }

    if (showAddDialog) {
        AddDeviceDialog(
            onAdd = { host ->
                onAddHost(host)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun StatusDot(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun TransportTag(transport: TabyTransport) {
    val theme = LocalAppTheme.current
    Text(
        text = when (transport) {
            TabyTransport.USB -> "USB"
            TabyTransport.WIFI -> "WiFi"
            TabyTransport.BLUETOOTH -> "BT"
        },
        color = theme.textSecondary,
        fontSize = 10.sp,
    )
}

@Composable
private fun AddDeviceDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    val theme = LocalAppTheme.current
    var host by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(theme.surface)
                .border(2.dp, theme.border)
                .padding(20.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Add Taby by hostname or IP",
                color = theme.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, theme.border)
                    .background(theme.surface2)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                BasicTextField(
                    value = host,
                    onValueChange = { host = it },
                    singleLine = true,
                    textStyle = TextStyle(color = theme.textPrimary, fontSize = 11.sp),
                    cursorBrush = SolidColor(theme.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (host.isEmpty()) {
                    Text(
                        text = "e.g. taby.local or 192.168.1.50",
                        color = theme.textSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .border(2.dp, theme.border)
                        .background(theme.surface)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("Cancel", color = theme.textSecondary, fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .border(2.dp, if (host.isNotBlank()) theme.border else theme.textSecondary)
                        .background(if (host.isNotBlank()) theme.accent else theme.surface)
                        .clickable(enabled = host.isNotBlank()) { onAdd(host) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Add",
                        color = if (host.isNotBlank()) theme.background else theme.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :desktop:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/DeviceSelector.kt
git commit -m "feat: style DeviceSelector with theme tokens, remove rounded corners, themed dialog"
```

---

## Task 9: Redesign VoiceOverlay.kt

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/VoiceOverlay.kt`

- [ ] **Step 1: Replace VoiceOverlay.kt entirely**

```kotlin
package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VoiceOverlay(
    listeningState: ListeningState,
    onDismiss: () -> Unit,
) {
    val theme = LocalAppTheme.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .background(theme.surface)
                .border(2.dp, theme.accent)
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = when (listeningState) {
                    ListeningState.Idle -> "READY"
                    ListeningState.WakeWordDetected -> "WAKE WORD"
                    ListeningState.Listening -> "LISTENING"
                    ListeningState.Responding -> "RESPONDING"
                },
                color = theme.accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(6.dp, 14.dp, 20.dp, 11.dp, 22.dp, 8.dp, 18.dp, 6.dp).forEach { h ->
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(h)
                            .background(theme.accent),
                    )
                }
            }

            Text(
                text = "CLICK OUTSIDE TO DISMISS",
                color = theme.textSecondary,
                fontSize = 8.sp,
                letterSpacing = 1.sp,
            )
        }
    }
}
```

- [ ] **Step 2: Full build and run verification**

Run: `./gradlew :desktop:run`

Verify visually:
- Sidebar is 160dp wide, "TABYPROTO" wordmark in accent color
- Nav items fill with accent on selection
- Home screen has panels with 3dp top-bars and 2dp borders
- Settings → Appearance shows Light/Dark toggle and 7 palette swatches
- Switching palette updates accent everywhere instantly
- Switching mode updates all backgrounds/borders instantly
- Voice overlay: sharp rectangle, accent border, large state label
- Animation cells: no rounded corners, "SENDING" text overlay when sending
- Loading screen: "LOADING N / N" text instead of spinner
- Window title is "TabyProto"

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/VoiceOverlay.kt
git commit -m "feat: redesign VoiceOverlay — sharp rectangle, accent border, themed text"
```
