# Firmware bug report: `taby_lvgl` task holds the LVGL lock until the watchdog fires

**Date:** 2026-06-16
**Reporter:** desktop/host side (tabyproto)
**Component:** device firmware (ESP32-S3, ESP-IDF + LVGL) — **not** this repo
**Severity:** high — device stops responding mid-session and must be power-cycled / replugged

## Summary

During a normal session the device's `taby_lvgl` rendering task holds the LVGL
lock for more than the Task Watchdog Timer (TWDT) window (~5 s) and never
yields. Symptoms escalate from "recoverable" to "hard hang":

1. **Recoverable stall** — TWDT fires (logged only, no reset). Commands needing
   the LVGL lock are rejected with `runtime_unavailable` while the lock is held,
   then service resumes.
2. **Hard hang** — the device stops emitting *all* serial output (no command
   responses, not even further watchdog dumps) and never recovers. The USB
   port stays enumerated, so the host gets no disconnect event; it just times
   out on every command.

This is a firmware-side defect. The host has no way to release a mutex held
inside the device's LVGL task. (Host-side mitigations were applied separately —
see "Host-side changes already made" below — but they do not fix the root cause.)

## Evidence

### Watchdog trip — `taby_lvgl` starves the idle task on core 1

```
E (...) task_wdt: Task watchdog got triggered. The following tasks/users did not reset the watchdog in time:
E (...) task_wdt:  - IDLE1 (CPU 1)
E (...) task_wdt: Tasks currently running:
E (...) task_wdt: CPU 0: IDLE0
E (...) task_wdt: CPU 1: taby_lvgl
E (...) task_wdt: Print CPU 1 backtrace
Backtrace: 0x40381D32:0x3FCA9580 0x403807B9:0x3FCA95A0 0x420464AD:0x3FCE53B0 0x4204706F:0x3FCE5490 0x4204C58F:0x3FCE5510 0x4200C0D5:0x3FCE5540
```

`taby_lvgl` is pinned to CPU 1 and never yields, so `IDLE1` can't feed the TWDT.

### The LVGL lock is the contended resource

```
→ CMD listening_music_loop
← W taby_runtime: failed to acquire LVGL lock for transport resolution
← TABY:ERR runtime_unavailable
...
← E task_wdt: ... CPU 1: taby_lvgl
```

Commands that need the LVGL lock fail with `runtime_unavailable` while
`taby_lvgl` holds it. `INFO`/`TOUCH_SIGNAL`/`CHOICE_SIGNAL` keep answering
(they don't take the lock), confirming the command-parsing task is alive and
only the LVGL path is wedged. Device `INFO` during the incident also reported
`last_reject_reason: "runtime_unavailable"` and a non-zero
`usb_rejected_command_count`.

### Not a memory problem

`INFO` during the incident reported `free_heap_bytes ≈ 6,117,896` and
`minimum_free_heap_bytes ≈ 5,724,600` (~6 MB free, stable). Rules out a leak or
fragmentation as the trigger.

### Not a reset

`boot_count: 1`, `reset_reason: "power_on"`, uptime climbing past ~3400 s. The
TWDT on this build is log-only (no panic/reset), so the watchdog dump itself is
not the disconnect — the disconnect is the subsequent total-silence hard hang.

## Likely root cause (for the firmware team)

The `taby_lvgl` task performs a long, synchronous operation **while holding the
LVGL lock** and without feeding the watchdog — most plausibly an
animation/asset decode or render step. Suspected directions:

- A blocking decode/render in the LVGL task that can exceed the TWDT window.
- Missing `esp_task_wdt_reset()` / `vTaskDelay()` yields inside that long path.
- A path that can deadlock/spin while holding the LVGL lock (the hard-hang case,
  where even the watchdog logger stops).

Suggested fixes: move heavy decode off the LVGL task; yield/feed the TWDT during
long operations; bound/timeout the lock-holding section; and audit for the
deadlock that produces total serial silence.

## How to reproduce

Run a normal session (host app connected over USB) and let animation
transitions cycle (idle/relaxing/music). The stall appeared after minutes of
uptime and recurred. Capturing the device serial log alongside the host
`→ CMD` / `← …` stream (host DEBUG logging) shows the `failed to acquire LVGL
lock` warning immediately before the watchdog trip.

## Host-side changes already made (mitigations, not fixes)

- **Stopped a redundant `BRIGHTNESS` command flood** (`rampBrightness` was
  emitting dozens of identical commands ~30 ms apart) that hammered the
  USB-Serial-JTAG + LVGL lock and coincided with a hard hang. Commit
  `fix(desktop): stop redundant BRIGHTNESS command flood`.
- **Wedged-device detection**: the host now marks a device offline after a few
  consecutive command timeouts so it can re-probe/reconnect instead of polling a
  dead device forever. Commit
  `feat(api): detect wedged devices; harden monitor reconnection + tests`.

These reduce host-side stress and improve recovery, but the firmware must fix
the LVGL-lock / watchdog root cause.
