# Live Device Discovery & Multi-Taby Support — Design Spec

**Date:** 2026-06-11
**Status:** Approved

---

## Goal

The desktop app connects and disconnects from Taby devices live, without an app restart. Devices appearing (USB plug-in, WiFi device coming online) are discovered automatically; devices disappearing are marked offline within seconds. Multiple Tabys are supported simultaneously — USB and WiFi — with a target-selector dropdown choosing which device receives animation sends.

Per the owner's standing direction, all capability lives in the `api` module; the desktop app stays a thin UI over it.

---

## New API surface (`api` module)

### `TabyDevice`

Immutable snapshot of a known device, published via the monitor's state flow:

```kotlin
data class TabyDevice(
    val id: String,             // device_id from INFO/health; fallback "usb:<port>" / "wifi:<host>"
    val label: String,          // display name: mdns_host, else port name / host
    val transport: TabyTransport,
    val online: Boolean,
    val info: DeviceInfo?,      // last known info (refreshed by WiFi polls / USB probe)
    val source: DeviceSource,
)

sealed class DeviceSource {
    data class Usb(val portName: String) : DeviceSource()
    data class Wifi(val host: String, val manual: Boolean) : DeviceSource()
}
```

A Taby reachable over both USB and WiFi appears as two entries (same `device_id`, different transport). No dedup.

### `TabyDeviceMonitor`

```kotlin
class TabyDeviceMonitor(
    initialManualHosts: List<String> = emptyList(),
    usbPollInterval: Duration = 2.seconds,
    wifiPollInterval: Duration = 5.seconds,
) : Closeable {
    val devices: StateFlow<List<TabyDevice>>
    val manualHosts: StateFlow<List<String>>   // callers persist this however they like

    fun start()                                // idempotent; launches discovery loops
    fun addManualHost(host: String)
    fun removeManualHost(host: String)

    suspend fun session(device: TabyDevice): TabySession  // monitor-owned, see below
    override fun close()                       // stops loops, closes all sessions
}
```

**Session ownership:** the monitor caches one `TabySession` per device and returns it from `session()`, creating it lazily. When a device goes offline (or `close()` is called) the monitor closes and evicts its session. Callers never close sessions obtained from the monitor. Calling `session()` on an offline device throws `IllegalStateException`.

`Taby.connect()` / `connectUsb()` / `connectWifi()` remain unchanged for one-shot library use.

---

## Discovery loops (inside the monitor)

### USB — every 2 s

1. Read `SerialPort.getCommPorts()` (cheap; does not open ports).
2. A port not seen before is probed once with `INFO` (existing `TabyUsbClient` probe logic). Responds → registered as a Taby device with its `device_id`; doesn't respond → remembered as non-Taby and not re-probed while it remains present.
3. A registered Taby port present in the list ⇒ `online = true`. Presence is the liveness signal — no repeated probing, so an open session (which holds the port) is never disturbed.
4. A registered port missing from the list ⇒ `online = false`; its cached session is closed and evicted. If it reappears, it is re-probed (device may have been swapped).

### WiFi — every 5 s

For each known host (manual + mDNS-discovered): `TabyWifiClient(host).readHealth()`. Success ⇒ `online = true`, `info` refreshed; failure ⇒ `online = false`. Hosts are never auto-removed; offline hosts keep being retried. One `TabyWifiClient` instance per host, reused across polls.

### mDNS

JmDNS browses `_http._tcp.local.`; any resolved service whose hostname or service name contains `taby` (case-insensitive) becomes a candidate host, confirmed by a successful `readHealth()` before being added to the device list. The health check is the authoritative filter, so the exact advertised service type matters little. JmDNS initialization failure is logged and ignored — manual hosts and USB still work. mDNS-removed events are ignored; the health poll is the source of truth for offline.

### Error policy

All per-device probe/health exceptions are caught and recorded as offline status. One misbehaving device or port never breaks a loop.

---

## Desktop app changes

### AppState

- Drops the `ConnectionState` machine and `Taby.connect()`.
- Owns a `TabyDeviceMonitor` (started in `init`, closed in `close()`), constructed with manual hosts loaded from `java.util.prefs.Preferences`; collects `monitor.manualHosts` and writes changes back to Preferences.
- Tracks `activeDeviceId: StateFlow<String?>`. Auto-select: when nothing is selected and a device comes online, select it (USB preferred over WiFi). After that, selection changes only by user action. If the active device goes offline, the selection is kept and shown as offline; sends are rejected with a clear message.
- `sendAnimation(animation)` resolves the active device, calls `monitor.session(device).play(animation)`. Failures surface via the existing snackbar path.

### UI

- **Grid is always visible** — the splash gate on thumbnails remains, but there is no longer a "Connecting" screen blocking the grid. With no online device, the grid renders normally and clicking a cell shows a "No Taby connected" snackbar.
- **Device dropdown** replaces the current status indicator in the top bar:
  - One row per known device: status dot (green online / grey offline), label, transport tag (USB/WiFi).
  - Active device shown in the collapsed state with its status dot; "No devices" placeholder when the list is empty.
  - "Add device…" item opens a small dialog with a hostname/IP text field → `monitor.addManualHost()`.
  - Manual entries get a remove affordance (small ✕) → `monitor.removeManualHost()`.
- "Last sent" line stays as-is beneath/next to the selector.

---

## Dependencies

`api/build.gradle.kts` gains:

```kotlin
implementation("org.jmdns:jmdns:3.6.1")
```

(Version to be confirmed against Maven Central at implementation time.) No new desktop dependencies.

---

## Testing

- **Unit tests (api):** `TabyDeviceMonitor` registry logic with injected fakes for the port lister, USB prober, and health checker — covering: new port → probe → registered; port vanishes → offline + session evicted; port reappears → re-probe; WiFi host flapping online/offline; manual host add/remove; non-Taby port not re-probed; session caching and eviction. The monitor's constructor takes these as internal-default function parameters to enable injection.
- **Manual hardware test:** real unplug/replug of USB Taby, WiFi Taby power-cycle, dropdown switching between two transports of the same physical device.

---

## Out of scope

- Bluetooth transport
- Dedup/merge of the same physical device across transports
- WiFi provisioning UI
- Broadcast ("send to all") mode
