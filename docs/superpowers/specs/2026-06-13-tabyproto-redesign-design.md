# TabyProto Desktop — UI Redesign Spec
**Date:** 2026-06-13
**Status:** Approved by user

---

## Overview

Full visual redesign of the TabyProto desktop app (Kotlin Compose for Desktop). The goal is to replace the current generic "AI app" aesthetic — dark indigo/navy backgrounds, purple gradients, rounded glass cards — with a **toybox/playful hardware-controller** aesthetic. Bold, chunky, flat, and zero rounded corners.

The redesign introduces a two-axis theme system (Light/Dark × Color Palette) configurable from Settings.

---

## Aesthetic Direction: Chunky Blocks

- **Zero rounded corners** everywhere. `RoundedCornerShape(0.dp)` or no shape at all.
- **2dp solid borders** in the theme's border color on all cards/panels/inputs.
- **3dp accent top-bar** on every panel card (a solid colored strip across the top).
- **No emoji.** All icons replaced with text symbols: `▶ ◉ ⏻ ⬡ ✦` etc.
- **Typography**: system bold sans-serif. Section labels in 8sp ALL-CAPS with letter spacing. Nav labels 11sp bold.
- **Sidebar always inverted**: dark sidebar regardless of light/dark mode (the darkest variant of the current theme).

---

## Theme System

Two independent axes combine to produce the final theme:

### Axis 1: Mode (Light / Dark)

**Light mode base**
| Token | Value |
|---|---|
| Background | `#FAF7F2` warm cream |
| Surface | `#F0EBE3` |
| Surface2 | `#E5DDD4` |
| Border | `#1A1614` near-black |
| Text primary | `#1A1614` |
| Text secondary | `#7A6E68` |
| Sidebar bg | `#1A1614` (inverted) |
| Sidebar text | `#FAF7F2` |

**Dark mode base**
| Token | Value |
|---|---|
| Background | `#1A1714` warm charcoal |
| Surface | `#242017` |
| Surface2 | `#2E2B20` |
| Border | `#EDE6DC` warm off-white |
| Text primary | `#EDE6DC` |
| Text secondary | `#6E6A60` |
| Sidebar bg | `#111008` near-black |
| Sidebar text | `#EDE6DC` |

### Axis 2: Color Palette (7 options)

Each palette provides two accent values: one tuned for light mode (darker/more saturated), one for dark mode (brighter/more vivid).

| Name | Light accent | Dark accent |
|---|---|---|
| **Ember** (red) | `#C4271A` | `#FF4D3D` |
| **Tangerine** (orange) | `#C45A0A` | `#FF8C3A` |
| **Sunflower** (yellow) | `#9A7A00` | `#F5C518` |
| **Fern** (green) | `#1A6B3A` | `#3DCC7A` |
| **Teal** (teal-cyan) | `#0A6B6B` | `#00C7C7` |
| **Ocean** (blue) | `#1A4A9A` | `#5B9EFF` |
| **Berry** (pink-magenta) | `#9A1A6B` | `#FF5BB8` |

The resolved accent color is `palette.lightAccent` when in Light mode, `palette.darkAccent` when in Dark mode.

### Theme Data Class

```kotlin
data class AppTheme(
    val isDark: Boolean,
    val palette: ColorPalette,
    // resolved tokens:
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val sidebarBg: Color,
    val sidebarText: Color,
    val accent: Color,
    val onlineGreen: Color = if (isDark) Color(0xFF3DCC7A) else Color(0xFF1A6B3A),
)
```

A `CompositionLocal<AppTheme>` (`LocalAppTheme`) provides the theme everywhere. No Material theming dependency for colors — all colors come from `LocalAppTheme.current`.

---

## Theme Persistence

User's selected mode and palette are stored in a `ThemeStore` (similar to `ManualHostsStore`) using a small JSON file in the app data directory. Loaded at startup, updated on selection.

---

## Sidebar

- **Width**: 160dp
- **Background**: `sidebarBg` (always dark)
- **Top**: "TABYPROTO" wordmark, 16sp bold, accent color, padded 16dp from top
- **Divider**: 1dp border-color horizontal line
- **Nav items** (Home, Settings):
  - Full-width rectangle, no shape
  - Layout: `Row` with 14dp horizontal padding, 12dp vertical padding
  - Glyph (text symbol, 12sp) + label (11sp bold) with 10dp gap
  - **Active state**: entire row background fills with accent color; text color becomes sidebar-bg (inverted for legibility)
  - **Inactive state**: transparent bg; text color = sidebarText at 70% alpha
- **Bottom section** (below `Spacer(weight 1f)`):
  - Device block: full-width bordered rectangle (2dp, `sidebarText.copy(alpha=0.25f)`), 8dp margin sides
    - Transport badge (`USB` / `WiFi` / `BT`) + online indicator dot (5dp circle)
    - Last-sent animation name, truncated
  - Voice block: full-width bordered rectangle below device block (2dp, `sidebarText.copy(alpha=0.25f)`), 8dp margin sides
    - State label: "READY" / "LISTENING" / "RESPONDING" in 8sp ALL-CAPS
    - Clickable → opens VoiceOverlay

---

## Home Screen

Three panels in a 2-row grid, all sharp rectangles with 2dp borders:

**Device Panel** (top, full width, `weight(1.4f)`)
- 3dp accent top-bar
- "DEVICE" section label (8sp ALL-CAPS, accent color)
- Status row: online dot (7dp circle) + "Connected · USB" or "Disconnected"
- "NOW PLAYING" sub-label + animation id

**Music Panel** (bottom-left, `weight(1f)`)
- 3dp accent top-bar (accent color)
- "MUSIC" section label
- "Not configured" body text

**Mobile Panel** (bottom-right, `weight(1f)`)
- 3dp accent top-bar
- "MOBILE" section label
- "No device paired" body text

All panels: `background(surface)`, `border(2dp, border)`, no clip needed (sharp by default).

---

## Settings Screen

Two-column layout:

**Left category pane** (160dp)
- Background: `surface`
- "SETTINGS" header label (7sp ALL-CAPS, textSecondary, letter-spaced)
- Categories: Play Animations, Music, Voice, Device, **Appearance** (new)
- Each item: full-width, 10dp horizontal + 8dp vertical padding, 9sp bold
- Active: background fills with accent; text = background color (inverted)

**Right content pane** (fills remaining width)
- Background: `background`
- 16dp padding all around

### Appearance Panel (new)

Two controls:

1. **Mode toggle**: Two side-by-side full-width-equal buttons, labeled "LIGHT" and "DARK", 2dp border. Selected = accent fill + inverted text.

2. **Palette picker**: 7 square swatches (40dp × 40dp), arranged in a `Row` with `Arrangement.spacedBy(8.dp)`. Each swatch shows the accent color as a solid fill. Selected swatch has a 2dp `border` in `border` color + inset white/dark ring to distinguish. Tapping updates the palette.

### Play Animations Panel

- Search field: `BasicTextField` with manual 2dp border (no Material `OutlinedTextField` — avoids rounded label animation). Styled with theme colors.
- Animation grid: square cells, 2dp border, thumbnail + name below, chunky "▶ PLAY" button (filled accent when sending, bordered otherwise)

---

## Voice Overlay

Full-screen scrim (`Color.Black.copy(0.75f)`), click outside to dismiss.

Center panel: sharp rectangle (no clip/shape), 2dp border in accent color, `background(surface)`, 40dp padding.

Contents:
- State label: "READY" / "WAKE WORD" / "LISTENING" / "RESPONDING" — 14sp bold, accent color, ALL-CAPS, large letter spacing
- Waveform: static bars (5 bars, varying heights), accent color
- "CLICK OUTSIDE TO DISMISS" — 8sp, textSecondary

---

## Loading Screen

Current: `CircularProgressIndicator` (Material, purple).
Replacement: A simple text counter — "LOADING NN / NN" — centered, 12sp bold, accent color. No spinner.

---

## Implementation Scope

Files to create or modify:

| File | Change |
|---|---|
| `AppTheme.kt` | New — theme data class, `ColorPalette` enum, `LocalAppTheme`, factory function |
| `ThemeStore.kt` | New — persist mode + palette to disk |
| `App.kt` | Wire `ThemeStore`, provide `LocalAppTheme`, pass theme down |
| `Sidebar.kt` | Full redesign per spec |
| `HomeScreen.kt` | Full redesign per spec |
| `SettingsScreen.kt` | Full redesign + new Appearance category |
| `VoiceOverlay.kt` | Redesign per spec |
| `AnimationCell.kt` | Remove rounding, new button style |
| `AnimationGrid.kt` | Minor: remove rounded container if any |
| `DeviceSelector.kt` | Style update to match theme |

`AppState` is unchanged. `ManualHostsStore` pattern is reused for `ThemeStore`.

---

## Out of Scope

- Functional changes to animation playback, device discovery, or voice
- Custom font bundling (system bold sans-serif is sufficient)
- Animations/transitions (may add in a follow-up)
