# Responsive Window Design

**Date:** 2026-06-15  
**Status:** Approved

## Goal

The desktop app should adapt gracefully to any window size the user drags it to, and must be constrained so it cannot be resized to unusable extremes.

---

## WindowSize System

A `WindowSize` sealed class (or enum) with three tiers drives all layout decisions:

| Tier | Width threshold | Description |
|------|----------------|-------------|
| `Compact` | < 680dp | Icons-only sidebar, stacked panels, top-tab settings nav |
| `Medium` | 680–960dp | Labeled sidebar at 180dp, side-by-side panels, side settings nav at 160dp |
| `Expanded` | > 960dp | Current layout at 200dp — no behavioral change |

`BoxWithConstraints` in `App` derives the tier from `maxWidth` and provides it via a `CompositionLocal` (`LocalWindowSize`). Composables read from `LocalWindowSize`; there is no prop-threading of the tier. When the tier changes the full tree recomposes naturally.

Individual content blocks (device block, music block, voice block in the sidebar) may additionally use local `BoxWithConstraints` for fine-grained internal adaptation, keeping the top-level enum clean.

**Window size limits** enforced in `Main.kt` via `SideEffect` + `window.minimumSize` with density-aware dp→px conversion:
- Minimum: **520×460dp**
- Maximum: none

---

## Sidebar (`Sidebar.kt`)

| Tier | Width | Behavior |
|------|-------|----------|
| `Compact` | 56dp | Icons only. Logo mark (or "T") at top. Home + Settings icons, no labels. Device / music / voice blocks hidden. |
| `Medium` | 180dp | Icons + labels. All blocks visible with slightly tighter padding. |
| `Expanded` | 200dp | No change from current layout. |

`NavItem` gets a `compact: Boolean` param — when `true`, the label is hidden and padding reduced to center the icon.

The three content blocks (`MusicBlockAnimated`, device `Column`, voice `Column`) are gated with `if (windowSize != WindowSize.Compact)`.

---

## HomeScreen (`HomeScreen.kt`)

| Tier | Music + Mobile panels |
|------|-----------------------|
| `Compact` | `Column` — stacked vertically |
| `Medium` | `Column` — stacked vertically |
| `Expanded` | `Row` with `weight(1f)` each — current layout |

`DevicePanel` stays full-width at all sizes. No changes to individual panel internals.

---

## SettingsScreen (`SettingsScreen.kt`)

| Tier | Category nav |
|------|-------------|
| `Compact` | Horizontal scrollable `Row` of text tabs across the top of the screen (above content area). No left column. |
| `Medium` | Left column at 160dp. |
| `Expanded` | Left column at 200dp — no change from current layout. |

The content `Box` with `weight(1f)` fills remaining space at all tiers. No changes to individual detail composables.

---

## Files to Change

| File | Change |
|------|--------|
| `desktop/.../WindowSize.kt` *(new)* | `WindowSize` enum + `LocalWindowSize` CompositionLocal |
| `desktop/.../Main.kt` | Add `SideEffect` to set `window.minimumSize` with density conversion |
| `desktop/.../App.kt` | Wrap content in `BoxWithConstraints`, derive `WindowSize`, provide via `LocalWindowSize` |
| `desktop/.../Sidebar.kt` | Read `LocalWindowSize`; collapse to 56dp icons-only in `Compact`; hide content blocks in `Compact`; tighter padding in `Medium` |
| `desktop/.../HomeScreen.kt` | Read `LocalWindowSize`; use `Column` for panels in `Compact`/`Medium`, `Row` in `Expanded` |
| `desktop/.../SettingsScreen.kt` | Read `LocalWindowSize`; horizontal tab row in `Compact`, 160dp column in `Medium`, 200dp in `Expanded` |
