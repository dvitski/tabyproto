# CLAUDE.md

> **Default branch:** `latest` (not `master`). After committing on `master`, merge into `latest` and push: `git checkout latest && git merge --ff-only master && git push origin latest`.


This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Run Commands

```bash
# Build all modules
./gradlew build

# Run the desktop app
./gradlew :desktop:run

# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :api:test
./gradlew :desktop:test

# Run a single test class
./gradlew :api:test --tests "cc.dvitski.tabyproto.AnimationControllerTest"

# Build without running tests
./gradlew assemble
```

## Architecture

This is a Kotlin/JVM multi-module Gradle project targeting JVM 21.

### Modules

**`api`** — Core device library (no UI). Contains everything needed to communicate with a Taby device:
- `Taby` — top-level entry point; `connect()` auto-detects USB then falls back to WiFi
- `TabySession` — interface for sending commands (`play`, `setBrightness`, `sendRaw`); implemented by `UsbTabySession` and `WifiTabySession`
- `TabyDeviceMonitor` — background poller that tracks all online devices (USB + WiFi/mDNS), exposes `StateFlow<List<TabyDevice>>` and `SharedFlow<TabyEvent>`
- `AnimationController` — priority queue for animations; handles preemption, expiry, and pending advancement
- `AnimationPriority` — constants: `MANUAL=100`, `VOICE=60`, `MUSIC=50`, `IDLE=10`
- `Animation` — sealed class (`Once`, `Looping`); `Looping` has an optional intro `RawAnimation` then a body
- `Animations` — singleton registry of all known animations
- `TabyProtocol` — wire format helpers; `normalizeUsbRequest` wraps bare animation IDs in `CMD <id>`

**`desktop`** — Compose Desktop UI. Depends on `api`. Entry point: `cc.dvitski.tabyproto.desktop.MainKt`.
- `AppState` — central view-model; owns `TabyDeviceMonitor`, `AnimationController` per device, `IdleScheduler`, `MusicMonitor`
- `App` — root composable; renders `Sidebar` + either `HomeScreen` or `SettingsScreen`
- `IdleScheduler` — drives idle/relaxed animation cycles based on inactivity; bridges to `AnimationController` at `IDLE` priority
- `MusicMonitor` + `SmtcPoller` — polls Windows SMTC via a compiled C# script (`smtc_query.cs`) to detect playing media; drives `MUSIC` priority animations
- `ThumbnailCache` — pre-renders GIF thumbnails for the animation grid using FFmpeg/JavaCV

**`app`** — Minimal CLI entry point; not the desktop GUI.

### Key Design Patterns

**API-first**: All capability lives in `api`. The `desktop` module is a thin consumer. Do not move logic from `api` into `desktop`.

**Wire format**: USB sends `CMD <anim_id>\n` for animations (e.g., `CMD idle_01_loop`). Intro+loop transitions use `>` separator: `CMD <intro_id>><body_id>`. WiFi sends the same raw string without `CMD` wrapping (see `normalizeUsbRequest` in `TabyProtocol.kt`).

**Animation priority flow**: `AppState` creates one `AnimationController` per device. All animation sources (manual send, music detection, idle scheduler) call `controller.request(priority, animation, durationMs, preempt)`. Higher priority preempts lower unless the active animation has <500ms remaining (in which case it's queued as pending).

**Device ID format**: `"usb:<portName>"` or `"wifi:<host>"`.

**SMTC integration**: `SmtcPoller` compiles and runs `smtc_query.cs` and `smtc_control.cs` at runtime using `csc.exe` (Windows built-in C# compiler). The output is JSON parsed into `MusicState`.
