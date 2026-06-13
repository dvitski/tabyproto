# Taby Desktop — Multipurpose UI Redesign

**Date:** 2026-06-13
**Status:** Approved

---

## Goal

Redesign the Taby desktop app from a single-purpose animation browser into a multipurpose companion app. The redesigned interface hosts Voice, Device, Music, and Mobile Sync as first-class features, with a persistent sidebar that keeps the most important status always visible regardless of which screen the user is on.

---

## Overall Layout

The window is divided into two regions: a **narrow persistent sidebar** (~72 dp wide) on the left, and a **content area** filling the rest.

```
┌─────────┬────────────────────────────────┐
│ Sidebar │                                │
│         │  Content area                  │
│  Home   │  (changes per nav selection)   │
│  Settings│                               │
│         │                                │
│  ──────  │                               │
│  Device  │                               │
│  Voice   │                               │
└─────────┴────────────────────────────────┘
```

---

## Sidebar

Width: ~72 dp. Always visible on every screen.

### Navigation section (top)

Two nav items, icon + label, stacked vertically:

| Item | Icon | Screen |
|---|---|---|
| Home | 🏠 | Dashboard |
| Settings | ⚙️ | Settings |

Active item gets a left-border accent (`#7C3AED`) and a subtle background tint.

### Footer section (bottom)

Two persistent widget pills, stacked vertically, separated from nav by a `Spacer`:

**Device pill** (top of footer):
- Background: dark green tint (`#0E1A10`), border `#22C55E44`
- Shows: connection dot (green = online, grey = offline), transport label (`USB` / `WiFi`), current animation name (truncated)
- Clicking navigates to Settings → Device

**Voice orb** (bottom of footer):
- Background: dark purple tint (`#12122A`), border `#7C3AED55`
- Shows: glowing purple orb + idle waveform bars + `VOICE ↑` label
- Clicking opens the **Voice Overlay**
- The orb glows brighter and the label changes to `ACTIVE` while listening

---

## Home — Dashboard

Three panels in a vertical stack:

### 1. Device panel (top, larger)
- Border: `#22C55E44`
- Left side: connection status dot + transport label + "Now playing: `<animation_id>`"
- Right side: animation thumbnail/preview placeholder (robot emoji or actual thumbnail)
- This panel is a status display, not interactive beyond showing current state

### 2. Music + Mobile row (bottom)

**Music panel** (left half):
- Border: `#F59E0B33`
- Shows: track name, source/artist, playback progress bar
- Greyed out / "Not configured" when music integration is off

**Mobile panel** (right half):
- Border: `#06B6D433`
- Shows: device name (e.g. "iPhone 15") + sync status ("Auto-synced ✓")
- Always auto-managed — no user action required from this panel

---

## Voice Overlay

Triggered by:
1. Clicking the sidebar voice orb
2. Wake word detection ("Hey Taby") while the app is open and in the foreground or listening in background

Behaviour:
- Renders as a full-window overlay on top of whatever screen is currently shown
- Sidebar remains visible and interactive through the dimmed backdrop
- The orb in the sidebar footer transitions to its **active** state (brighter glow, label → `ACTIVE`)
- Overlay content (centred in the content area):
  - Large glowing orb
  - `LISTENING` label (letter-spaced, uppercase)
  - Animated waveform bars
  - "Last response" card showing Taby's most recent reply/action
- Dismiss: press `Esc` or click outside the overlay panel
- On dismiss, orb returns to idle state

---

## Settings

Two-pane layout: category list (left, ~90 dp) + detail panel (right).

### Categories

| Category | Contents |
|---|---|
| Play animations | Animation browser grid (existing feature, relocated here) |
| Music | Music source configuration |
| Voice | Wake word toggle, sensitivity, microphone selection |
| Device | Manual host entry, transport preference, device info — hosts the existing `DeviceSelector` composable |

### Play animations detail

The existing `AnimationGrid` component renders here unchanged. Filter field at the top of the detail panel. Clicking an animation cell sends it to the active device.

---

## Navigation and Routing

- The app has two top-level routes: `Home` and `Settings`
- Settings has a selected category sub-state (defaults to `PlayAnimations` on first open)
- The Voice Overlay is not a route — it is a composable overlay driven by a boolean `voiceOverlayVisible` flag in `AppState`
- Clicking the Device pill in the sidebar is a shortcut equivalent to navigating to Settings → Device

---

## State Changes (additions to `AppState`)

```
AppState additions:
  selectedScreen: Screen          // Home | Settings(category)
  voiceOverlayVisible: Boolean    // true while overlay is shown
  listeningState: ListeningState  // Idle | WakeWordDetected | Listening | Responding

Screen
  = Home
  | Settings(category: SettingsCategory)

SettingsCategory
  = PlayAnimations
  | Music
  | Voice
  | Device
```

The existing device/session/animation state remains unchanged.

---

## Compose Component Tree (revised)

```
App
├── Row (fills window)
│   ├── Sidebar
│   │   ├── NavItem(Home)
│   │   ├── NavItem(Settings)
│   │   ├── Spacer
│   │   ├── DevicePill         ← persistent, always visible
│   │   └── VoiceOrb           ← persistent, always visible; click → overlay
│   └── ContentArea (weight=1)
│       ├── HomeScreen         (when selectedScreen == Home)
│       │   ├── DevicePanel
│       │   └── Row
│       │       ├── MusicPanel
│       │       └── MobilePanel
│       └── SettingsScreen     (when selectedScreen == Settings)
│           ├── CategoryList
│           └── SettingsDetail
│               ├── PlayAnimationsDetail  → AnimationGrid (existing)
│               ├── MusicDetail
│               ├── VoiceDetail
│               └── DeviceDetail
└── VoiceOverlay               ← rendered on top when voiceOverlayVisible
    ├── Scrim (dimmed backdrop)
    └── OverlayPanel
        ├── VoiceOrb (large, animated)
        ├── ListeningLabel
        ├── Waveform
        └── LastResponseCard
```

---

## Visual Style

Unchanged from existing app:
- Background: `#1E1E2E`
- Sidebar background: `#090914`
- Accent purple: `#7C3AED` / `#A78BFA`
- Green (device online): `#22C55E`
- Amber (music): `#F59E0B`
- Cyan (mobile): `#06B6D4`
- Muted text: `#9399B2`

---

## Out of Scope

- Actual voice recognition / wake word implementation (UI shell only for now)
- Music playback integration (panel shows status placeholder)
- Mobile sync implementation (panel shows auto-sync status placeholder)
- Bluetooth transport
- Firmware update
