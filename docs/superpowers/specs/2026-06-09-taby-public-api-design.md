# Taby Kotlin Library — Public API Design

**Date:** 2026-06-09  
**Status:** Approved

---

## Goal

Refactor the existing protocol implementation into a clean, typed Kotlin library. Callers depend only on `Taby`, `TabySession`, `Animation`, and a handful of domain types — never on transport internals.

---

## File Layout

```
src/main/kotlin/dev/taby/
├── Taby.kt            # public entry point — Taby object, factory methods
├── TabySession.kt     # TabySession interface + UsbTabySession / WifiTabySession (internal impls)
├── TabyAnimation.kt   # Animation enum, AnimationCommand data class, infix `then`
├── TabyProtocol.kt    # wire constants, data classes, parsers (internal)
├── TabyUsbClient.kt   # USB serial transport (internal)
├── TabyWifiClient.kt  # WiFi REST transport (internal)
└── Main.kt            # demo main() only — uses the public API
```

`TabyUsbClient`, `TabyWifiClient`, and `TabyProtocol` internals are `internal`. The public surface is:

> `Taby`, `TabySession`, `Animation`, `AnimationCommand`, `DeviceInfo`, `DeviceState`, `WifiMode`, `CommandResult`, `TabyTransport`

---

## Animation

```kotlin
// TabyAnimation.kt

enum class Animation(val id: String) {
    ANGRY_01_LOOP("angry_01_loop"),
    ANGRY_02_LOOP("angry_02_loop"),
    BASKETBALL_DUNK("basketball_dunk"),
    BASKETBALL_THROW("basketball_throw"),
    BLUSH("blush"),
    BOXING("boxing"),
    BREAK_START("break_start"),
    BUSY_LOOP("busy_loop"),
    CALENDAR_IN("calendar_in"),
    CALENDAR_LOOP("calendar_loop"),
    CIRCLE("circle"),
    CLAUDE_IN("claude_in"),
    CLAUDE_LOOP("claude_loop"),
    CODEX_IN("codex_in"),
    CODEX_LOOP("codex_loop"),
    CONFIRMATION("confirmation"),
    COPY_PASTE("copy_paste"),
    CREATING_TASK_LOOP("creating_task_loop"),
    DAY_PLANNED("day_planned"),
    DELETE_01("delete_01"),
    DISAPPOINTED("disappointed"),
    DIZZY_LOOP("dizzy_loop"),
    DRINK_WATER("drink_water"),
    F1_CAR("f1_car"),
    FISHING_LONG("fishing_long"),
    FISHING_SHORT("fishing_short"),
    FLOWER_GROW("flower_grow"),
    HELLO_ANNOYED("hello_annoyed"),
    HELLO_DISAPPOINTED("hello_disappointed"),
    IDLE_01_LOOP("idle_01_loop"),
    IDLE_02_LOOP("idle_02_loop"),
    IDLE_VARIATION_LOOP("idle_variation_loop"),
    LISTENING_IN("listening_in"),
    LISTENING_LOOP("listening_loop"),
    LISTENING_MUSIC_LOOP("listening_music_loop"),
    LOCKIN("lockin"),
    LOVE_01("love_01"),
    NO("no"),
    PERFECT_DAY_01("perfect_day_01"),
    PERFECT_DAY_01_SIMPLE("perfect_day_01_simple"),
    PERFECT_DAY_02("perfect_day_02"),
    PERFECT_DAY_03("perfect_day_03"),
    POSTURE_CHECK("posture_check"),
    RELAXING_01_LOOP("relaxing_01_loop"),
    RELAXING_COUCH_IN("relaxing_couch_in"),
    RELAXING_COUCH_LOOP("relaxing_couch_loop"),
    REVIEW("review"),
    SLEEPING_LOOP("sleeping_loop"),
    SQUARE("square"),
    STARTUP("startup"),
    STRETCHING("stretching"),
    TABY_RESPONSE_READY_IN("taby_response_ready_in"),
    TABY_RESPONSE_READY_LOOP("taby_response_ready_loop"),
    TALKING_DEFAULT_LOOP("talking_default_loop"),
    TALKING_MAN_LOOP("talking_man_loop"),
    TASK_COMPLETED("task_completed"),
    TASK_CREATED("task_created"),
    TASK_PAGE("task_page"),
    THUMBS_UP("thumbs_up"),
    TROPHY("trophy"),
    TURN_OFF_REDDIT("turn_off_reddit"),
    TURN_OFF_SCROLL("turn_off_scroll"),
    TURN_OFF_TV("turn_off_tv"),
    WAITING_01("waiting_01"),
    WORKING_IN("working_in"),
    WORKING_LAPTOP_BORED_LOOP("working_laptop_bored_loop"),
    WORKING_LAPTOP_EXCITED_LOOP("working_laptop_excited_loop"),
    WORKING_LAPTOP_IN("working_laptop_in"),
    WORKING_LAPTOP_LOOP("working_laptop_loop"),
    WORKING_LAPTOP_NORMAL_LOOP("working_laptop_normal_loop"),
    WORKING_LOOP("working_loop"),
    WORKSPACES_IN("workspaces_in"),
}

data class AnimationCommand(val from: Animation, val to: Animation) {
    internal fun toWireString() = "${from.id}>${to.id}"
}

infix fun Animation.then(next: Animation) = AnimationCommand(this, next)
```

---

## Domain Enumerations

```kotlin
// In TabyProtocol.kt (public types)

enum class DeviceState {
    IDLE, UNKNOWN;
    companion object {
        fun from(raw: String?) =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class WifiMode {
    NONE, STATION, AP, UNKNOWN;
    companion object {
        fun from(raw: String?) =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}
```

`DeviceInfo` is updated so that:
- `state: String?` → `state: DeviceState`
- `wifiMode: String?` → `wifiMode: WifiMode`
- `preferredTransport: String?` → `preferredTransport: TabyTransport?`

Raw string fields remain in `DeviceInfoRaw` (internal). All other `DeviceInfo` fields stay as primitives.

---

## `TabySession` Interface

```kotlin
// TabySession.kt

interface TabySession : Closeable {
    val transport: TabyTransport
    val device: DeviceInfo

    suspend fun play(animation: Animation): CommandResult
    suspend fun play(command: AnimationCommand): CommandResult
    suspend fun setBrightness(percent: Int): CommandResult  // throws IllegalArgumentException if not 0–100
    suspend fun sendRaw(command: String): CommandResult     // escape hatch for untyped commands

    override fun close()
}
```

Two `internal` implementations:
- `UsbTabySession` — wraps `TabyUsbClient.TabyUsbSession`
- `WifiTabySession` — wraps `TabyWifiClient`

Both delegate `play` and `setBrightness` to `sendRaw` after formatting the wire string.

---

## `Taby` Entry Point

```kotlin
// Taby.kt

object Taby {
    /** Auto-detect: USB first, WiFi fallback. */
    suspend fun connect(debug: Boolean = false): TabySession

    suspend fun connectUsb(debug: Boolean = false): TabySession

    suspend fun connectWifi(host: String = WIFI_DEFAULT_HOST): TabySession
}
```

`connect()` attempts USB, catches any exception, falls back to WiFi. `connectUsb()` throws if no USB device responds. `connectWifi()` throws if the host is unreachable.

---

## `Main.kt` After Refactor

The demo becomes a clean showcase of the public API:

```kotlin
fun main() = runBlocking {
    val session = Taby.connectUsb(debug = true)

    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking { session.play(Animation.TURN_OFF_TV) }
        session.close()
    })

    session.play(Animation.STARTUP)
    println("Connected. Press Ctrl+C to disconnect.")
    while (true) delay(1_000)
}
```

---

## What Stays Internal

| Symbol | Visibility | Reason |
|---|---|---|
| `TabyUsbClient` | `internal` | Low-level port management |
| `TabyWifiClient` | `internal` | Low-level HTTP |
| `TabyUsbPort` | `internal` | Port discovery detail |
| `normalizeUsbRequest` | `internal` | Wire formatting |
| `parseInfoResponse` | `internal` | Wire parsing |
| `USB_DIRECT_PREFIXES` | `internal` | Wire constant |
| `USB_BAUD_RATE` | `internal` | Wire constant |
| `DeviceInfoRaw` / `ChoiceSignalRaw` etc. | `internal` | Raw wire types |
| `UsbTabySession` / `WifiTabySession` | `internal` | Impl detail |
| `WIFI_DEFAULT_HOST` | `public` | Useful default for `connectWifi` |
| `TabyTransport` | `public` | Session inspection |
| `CommandResult` | `public` | Return type of all commands |
