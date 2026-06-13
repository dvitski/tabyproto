# Multipurpose UI Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the Compose Desktop app from a single animation-browser screen into a sidebar-navigated multipurpose shell with a Home dashboard, Settings, and a persistent voice overlay.

**Architecture:** A 72 dp persistent `Sidebar` sits left of a content area that swaps between `HomeScreen` and `SettingsScreen` based on routing state in `AppState`. A `VoiceOverlay` renders on top of any screen when activated. The existing `AnimationGrid` and `DeviceSelector` composables move into `SettingsScreen` unchanged.

**Tech Stack:** Kotlin, Jetpack Compose Desktop (Material 1, not Material 3), `kotlinx-coroutines`, existing `tabyproto` API module.

---

## File Map

| Action | File | Responsibility |
|---|---|---|
| Modify | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt` | Add `Screen`, `SettingsCategory`, `ListeningState` sealed/enum types; add routing fields to `AppState`; replace `App` composable with new shell |
| Create | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Sidebar.kt` | `Sidebar`, private `NavItem`, `DevicePill`, `VoiceOrb` composables |
| Create | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/VoiceOverlay.kt` | Full-screen dismissible overlay shown when voice is active |
| Create | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/HomeScreen.kt` | Dashboard with `DevicePanel`, `MusicPanel`, `MobilePanel` |
| Create | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/SettingsScreen.kt` | Two-pane settings with category list + detail panels |
| Unchanged | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/AnimationGrid.kt` | Reused in `SettingsScreen` > Play animations |
| Unchanged | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/DeviceSelector.kt` | Reused in `SettingsScreen` > Device |
| Unchanged | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/AnimationCell.kt` | Unchanged |
| Unchanged | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/ThumbnailCache.kt` | Unchanged |
| Unchanged | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/AnimationResources.kt` | Unchanged |
| Unchanged | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/ManualHostsStore.kt` | Unchanged |
| Unchanged | `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Main.kt` | Unchanged |

**Compile command (run after every task):**
- Git Bash / PowerShell: `./gradlew :desktop:compileKotlin`

---

## Task 1: Add routing state types and AppState fields

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt`

- [ ] **Step 1: Add the three new types directly above the `// ── AppState` comment in `App.kt`**

```kotlin
// ── Routing types ──────────────────────────────────────────────────────────────

sealed class Screen {
    object Home : Screen()
    data class Settings(val category: SettingsCategory = SettingsCategory.PlayAnimations) : Screen()
}

enum class SettingsCategory { PlayAnimations, Music, Voice, Device }

enum class ListeningState { Idle, WakeWordDetected, Listening, Responding }
```

- [ ] **Step 2: Add routing state fields and functions to the `AppState` class, after the existing `thumbnailCache`/`totalAnimations` lines and before `init {`**

```kotlin
private val _selectedScreen = MutableStateFlow<Screen>(Screen.Home)
val selectedScreen: StateFlow<Screen> = _selectedScreen.asStateFlow()

private val _voiceOverlayVisible = MutableStateFlow(false)
val voiceOverlayVisible: StateFlow<Boolean> = _voiceOverlayVisible.asStateFlow()

private val _listeningState = MutableStateFlow(ListeningState.Idle)
val listeningState: StateFlow<ListeningState> = _listeningState.asStateFlow()

fun navigate(screen: Screen) { _selectedScreen.value = screen }
fun showVoiceOverlay() { _voiceOverlayVisible.value = true }
fun hideVoiceOverlay() { _voiceOverlayVisible.value = false }
```

- [ ] **Step 3: Compile to verify no errors**

```
./gradlew :desktop:compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt
git commit -m "feat: add routing state types to AppState"
```

---

## Task 2: Build Sidebar.kt

**Files:**
- Create: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Sidebar.kt`

- [ ] **Step 1: Create `Sidebar.kt` with the full file content below**

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.TabyDevice
import cc.dvitski.tabyproto.TabyTransport

private val SidebarBg = Color(0xFF090914)
private val AccentPurple = Color(0xFF7C3AED)
private val LightPurple = Color(0xFFA78BFA)
private val OnlineGreen = Color(0xFF22C55E)
private val OfflineGrey = Color(0xFF6C7086)

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
    Column(
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(SidebarBg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        Text("TABY", color = LightPurple, fontSize = 10.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))

        NavItem(
            icon = "🏠",
            label = "Home",
            selected = selectedScreen == Screen.Home,
            onClick = { onNavigate(Screen.Home) },
        )
        NavItem(
            icon = "⚙",
            label = "Settings",
            selected = selectedScreen is Screen.Settings,
            onClick = { onNavigate(Screen.Settings()) },
        )

        Spacer(Modifier.weight(1f))

        DevicePill(
            devices = devices,
            activeDeviceId = activeDeviceId,
            lastSent = lastSent,
            onClick = { onNavigate(Screen.Settings(SettingsCategory.Device)) },
        )
        Spacer(Modifier.height(4.dp))
        VoiceOrb(listeningState = listeningState, onClick = onVoiceClick)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun NavItem(
    icon: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .drawBehind {
                if (selected) {
                    drawRect(color = AccentPurple.copy(alpha = 0.13f))
                    drawRect(
                        color = AccentPurple,
                        topLeft = Offset.Zero,
                        size = Size(2.dp.toPx(), size.height),
                    )
                }
            }
            .padding(vertical = 5.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(icon, fontSize = 14.sp)
            Text(
                text = label,
                fontSize = 7.sp,
                color = if (selected) LightPurple else Color(0xFF888888),
            )
        }
    }
}

@Composable
private fun DevicePill(
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    lastSent: Animation?,
    onClick: () -> Unit,
) {
    val active = devices.firstOrNull { it.id == activeDeviceId }
    val online = active?.online == true

    Column(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0E1A10))
            .border(1.dp, OnlineGreen.copy(alpha = 0.27f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (online) OnlineGreen else OfflineGrey),
            )
            Text(
                text = when (active?.transport) {
                    TabyTransport.USB -> "USB"
                    TabyTransport.WIFI -> "WiFi"
                    TabyTransport.BLUETOOTH -> "BT"
                    null -> "—"
                },
                fontSize = 7.sp,
                color = if (online) Color(0xFF4ADE80) else OfflineGrey,
            )
        }
        Text(
            text = lastSent?.id ?: if (active != null) "connected" else "No device",
            fontSize = 6.sp,
            color = if (online) Color(0xFF3A6040) else OfflineGrey,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VoiceOrb(
    listeningState: ListeningState,
    onClick: () -> Unit,
) {
    val active = listeningState != ListeningState.Idle

    Column(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF12122A))
            .border(
                width = 1.dp,
                color = AccentPurple.copy(alpha = if (active) 0.8f else 0.33f),
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (active) Color(0xFF6D28D9) else Color(0xFF4C1D95)),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(3.dp, 6.dp, 4.dp, 5.dp, 3.dp).forEach { h ->
                Box(
                    Modifier
                        .width(1.dp)
                        .height(h)
                        .background(LightPurple.copy(alpha = if (active) 1f else 0.5f)),
                )
            }
        }
        Text(
            text = if (active) "ACTIVE" else "VOICE ↑",
            fontSize = 6.sp,
            color = if (active) LightPurple else AccentPurple,
            letterSpacing = 0.5.sp,
        )
    }
}
```

- [ ] **Step 2: Fix the typo on the `Arrangement.Arrangement.spacedBy` line in `DevicePill` — it should be just `Arrangement.spacedBy(4.dp)`**

Find in the file:
```kotlin
            horizontalArrangement = Arrangement.Arrangement.spacedBy(4.dp),
```
Replace with:
```kotlin
            horizontalArrangement = Arrangement.spacedBy(4.dp),
```

- [ ] **Step 3: Compile**

```
./gradlew :desktop:compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/Sidebar.kt
git commit -m "feat: add persistent Sidebar with DevicePill and VoiceOrb"
```

---

## Task 3: Build VoiceOverlay.kt

**Files:**
- Create: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/VoiceOverlay.kt`

- [ ] **Step 1: Create `VoiceOverlay.kt`**

```kotlin
package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VoiceOverlay(
    listeningState: ListeningState,
    onDismiss: () -> Unit,
) {
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
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF12122A))
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF5B21B6)),
            )

            Text(
                text = when (listeningState) {
                    ListeningState.Idle -> "READY"
                    ListeningState.WakeWordDetected -> "WAKE WORD"
                    ListeningState.Listening -> "LISTENING"
                    ListeningState.Responding -> "RESPONDING"
                },
                color = Color(0xFFA78BFA),
                fontSize = 11.sp,
                letterSpacing = 3.sp,
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
                            .background(Color(0xFFA78BFA)),
                    )
                }
            }

            Text(
                text = "Click outside to dismiss",
                color = Color(0xFF3D3060),
                fontSize = 9.sp,
            )
        }
    }
}
```

- [ ] **Step 2: Compile**

```
./gradlew :desktop:compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/VoiceOverlay.kt
git commit -m "feat: add VoiceOverlay composable"
```

---

## Task 4: Build HomeScreen.kt

**Files:**
- Create: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/HomeScreen.kt`

- [ ] **Step 1: Create `HomeScreen.kt`**

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D1A))
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
private fun DevicePanel(
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    lastSent: Animation?,
    modifier: Modifier = Modifier,
) {
    val active = devices.firstOrNull { it.id == activeDeviceId }
    val online = active?.online == true

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0E1A10))
            .border(1.dp, Color(0xFF22C55E).copy(alpha = 0.27f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("DEVICE", color = Color(0xFF22C55E), fontSize = 8.sp, letterSpacing = 1.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (online) Color(0xFF22C55E) else Color(0xFF6C7086)),
                )
                Text(
                    text = if (online) {
                        "Connected · ${when (active?.transport) {
                            TabyTransport.USB -> "USB"
                            TabyTransport.WIFI -> "WiFi"
                            TabyTransport.BLUETOOTH -> "BT"
                            null -> "—"
                        }}"
                    } else "Disconnected",
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                )
            }
            Text(
                text = "Now playing: ${lastSent?.id ?: "—"}",
                color = Color(0xFF555555),
                fontSize = 9.sp,
            )
        }
        Text("🤖", fontSize = 36.sp)
    }
}

@Composable
private fun MusicPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1A140A))
            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("MUSIC", color = Color(0xFFF59E0B), fontSize = 8.sp, letterSpacing = 1.sp)
        Text("Not configured", color = Color(0xFF555555), fontSize = 9.sp)
    }
}

@Composable
private fun MobilePanel(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF080E1A))
            .border(1.dp, Color(0xFF06B6D4).copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("MOBILE", color = Color(0xFF06B6D4), fontSize = 8.sp, letterSpacing = 1.sp)
            Text("No device paired", color = Color(0xFF555555), fontSize = 9.sp)
        }
        Text("📱", fontSize = 20.sp)
    }
}
```

- [ ] **Step 2: Compile**

```
./gradlew :desktop:compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/HomeScreen.kt
git commit -m "feat: add HomeScreen dashboard with Device, Music, Mobile panels"
```

---

## Task 5: Build SettingsScreen.kt

**Files:**
- Create: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/SettingsScreen.kt`

- [ ] **Step 1: Create `SettingsScreen.kt`**

```kotlin
package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dvitski.tabyproto.Animation
import cc.dvitski.tabyproto.TabyDevice

private val SettingsBg = Color(0xFF0D0D1A)
private val CategoryBg = Color(0xFF090914)
private val AccentPurple = Color(0xFF7C3AED)
private val LightPurple = Color(0xFFA78BFA)

private val SettingsCategory.label: String
    get() = when (this) {
        SettingsCategory.PlayAnimations -> "Play animations"
        SettingsCategory.Music -> "Music"
        SettingsCategory.Voice -> "Voice"
        SettingsCategory.Device -> "Device"
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
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxSize().background(SettingsBg)) {
        Column(
            modifier = Modifier
                .width(120.dp)
                .fillMaxHeight()
                .background(CategoryBg)
                .padding(top = 16.dp),
        ) {
            Text(
                text = "SETTINGS",
                color = Color(0xFF444444),
                fontSize = 7.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp),
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
            }
        }
    }
}

@Composable
private fun CategoryItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .drawBehind {
                if (selected) {
                    drawRect(color = AccentPurple.copy(alpha = 0.1f))
                    drawRect(
                        color = AccentPurple,
                        topLeft = Offset.Zero,
                        size = Size(2.dp.toPx(), size.height),
                    )
                }
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = if (selected) LightPurple else Color(0xFF888888),
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
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Play animations", color = Color(0xFFE2E8F0), fontSize = 13.sp)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Filter animations") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color(0xFFE2E8F0), fontSize = 13.sp)
        Text(body, color = Color(0xFF555555), fontSize = 10.sp)
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Device", color = Color(0xFFE2E8F0), fontSize = 13.sp)
        DeviceSelector(
            devices = devices,
            activeDeviceId = activeDeviceId,
            onSelect = onSelect,
            onAddHost = onAddHost,
            onRemoveHost = onRemoveHost,
        )
    }
}
```

- [ ] **Step 2: Compile**

```
./gradlew :desktop:compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/SettingsScreen.kt
git commit -m "feat: add two-pane SettingsScreen with Play animations, Music, Voice, Device"
```

---

## Task 6: Rewire App.kt — replace the App composable

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt`

- [ ] **Step 1: Replace the entire `App` composable (everything from `@Composable` `fun App(...)` to the closing `}`) with the new version below. Leave `AppState` and the routing types at the top of the file untouched.**

```kotlin
@Composable
fun App(appState: AppState) {
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

    val onSend: (Animation) -> Unit = { animation ->
        scope.launch {
            val result = appState.sendAnimation(animation)
            result.onFailure { e ->
                snackbarHostState.showSnackbar(message = e.message ?: "Send failed")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground),
    ) {
        if (!thumbnailsReady) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Loading animations… $loadedCount / $total",
                    color = Color.White,
                    fontSize = 14.sp,
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
```

- [ ] **Step 2: Update the imports in `App.kt` — remove unused imports and add missing ones**

Remove these imports (no longer used in `App`):
```kotlin
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
```

Add these imports if not already present:
```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
```

- [ ] **Step 3: Compile**

```
./gradlew :desktop:compileKotlin
```

Expected: `BUILD SUCCESSFUL`. If there are import errors, add the missing `androidx.compose.*` import that matches the unresolved symbol.

- [ ] **Step 4: Run the app and visually verify**

```
./gradlew :desktop:run
```

Check:
- Loading splash shows while thumbnails extract
- Sidebar visible with Home/Settings nav items + DevicePill + VoiceOrb at bottom
- Home screen shows Device, Music, Mobile panels
- Clicking Settings nav item switches to two-pane settings view
- "Play animations" category shows the animation grid with filter field
- Clicking an animation sends it to the device
- Clicking VoiceOrb opens the overlay; clicking outside dismisses it
- Clicking DevicePill navigates to Settings → Device

- [ ] **Step 5: Commit**

```bash
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt
git commit -m "feat: wire multipurpose UI shell — sidebar, home dashboard, settings, voice overlay"
```
