# Live Device Discovery & Multi-Taby Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The desktop app connects/disconnects from Taby devices live — USB and WiFi devices are discovered automatically, marked offline within seconds of disappearing, and a target-selector dropdown picks which device receives sends.

**Architecture:** A new `TabyDeviceMonitor` in the `api` module owns all discovery (USB port polling, WiFi health polling, mDNS browse), the device registry (`StateFlow<List<TabyDevice>>`), and session lifecycle (one cached `TabySession` per device, auto-closed on offline). The desktop app drops its `ConnectionState` machine and becomes a thin consumer: it collects the flow, renders a dropdown, and sends via `monitor.session(device).play(animation)`.

**Tech Stack:** Kotlin/JVM 21, kotlinx-coroutines, jSerialComm (existing), OkHttp (existing), JmDNS (new), Compose Desktop, JUnit5 + kotlinx-coroutines-test (new test infra).

**Spec:** `docs/superpowers/specs/2026-06-11-live-device-discovery-design.md`

**One deliberate deviation from the spec:** the spec said `TabyDevice.id` is `device_id` with a port/host fallback. That breaks when the same physical Taby is reachable over USB *and* WiFi (two entries, same id → dropdown selection and Compose `key`s collide). Instead, `id` is always the unique source key (`"usb:<port>"` / `"wifi:<host>"`); the firmware `device_id` remains available via `info?.deviceId`.

**Reference — existing API internals the monitor builds on (all in `api/src/main/kotlin/cc/dvitski/tabyproto/`):**
- `TabyUsbClient.listCandidatePorts(): List<SerialPort>` — lists serial ports, excludes Bluetooth COM ports, does NOT open them (`TabyUsbClient.kt:79`)
- `TabyUsbClient.readInfo(portName): DeviceInfo` — opens port, probes with `INFO`, closes port; throws on non-Taby (`TabyUsbClient.kt:90`)
- `TabyUsbClient.openSession(portName): TabyUsbSession` — opens port persistently (`TabyUsbClient.kt:144`)
- `TabyWifiClient(host).readHealth(): DeviceInfo` — GET /v1/health, throws on unreachable (`TabyWifiClient.kt:44`)
- `UsbTabySession(usbSession, info)` / `WifiTabySession(client, info)` — internal `TabySession` impls (`TabySession.kt`)
- `DeviceInfo` has nullable `deviceId` and `mdnsHost` fields (`TabyProtocol.kt:94`)
- Opening a USB port resets the ESP32 (auto-reset circuit) and waits ~0.3–3 s for boot output to settle. This means: probing a new port is slow-ish (by design), and the first `session()` on a USB device takes a few seconds. Never probe a port that has an open session — presence in the port list is the liveness signal.

---

### Task 1: Test infrastructure + JmDNS dependency

**Files:**
- Modify: `api/build.gradle.kts`

- [ ] **Step 1: Add dependencies and JUnit platform config**

Replace the entire content of `api/build.gradle.kts` with:

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    api("com.fazecast:jSerialComm:2.10.4")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.27")
    implementation("org.jmdns:jmdns:3.6.1")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
```

If `org.jmdns:jmdns:3.6.1` fails to resolve, use `3.5.12` (known-good on Maven Central).

- [ ] **Step 2: Verify the build resolves**

Run: `.\gradlew.bat :api:build`
Expected: `BUILD SUCCESSFUL` (no tests exist yet; compilation and dependency resolution succeed)

- [ ] **Step 3: Commit**

```powershell
git add api/build.gradle.kts
git commit -m "build: add JmDNS dependency and JUnit5 test infrastructure to api module"
```

---

### Task 2: Device model types

**Files:**
- Create: `api/src/main/kotlin/cc/dvitski/tabyproto/TabyDevice.kt`

These are pure data declarations — no behavior to test; the monitor tests in Task 3 exercise them.

- [ ] **Step 1: Create `TabyDevice.kt`**

```kotlin
package cc.dvitski.tabyproto

/**
 * Immutable snapshot of a Taby known to a [TabyDeviceMonitor].
 *
 * [id] is a stable unique key derived from the source (`"usb:<port>"` or
 * `"wifi:<host>"`). The same physical device reachable over both transports
 * appears as two entries with different ids; the firmware-assigned device id
 * is available via `info?.deviceId`.
 */
data class TabyDevice(
    val id: String,
    val label: String,
    val transport: TabyTransport,
    val online: Boolean,
    val info: DeviceInfo?,
    val source: DeviceSource,
)

/** Where a [TabyDevice] was discovered. */
sealed class DeviceSource {
    data class Usb(val portName: String, val friendlyName: String) : DeviceSource()
    data class Wifi(val host: String, val manual: Boolean) : DeviceSource()
}

/** A serial port candidate as seen by the monitor's port lister. */
internal data class UsbPortRef(val portName: String, val friendlyName: String)
```

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :api:compileKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```powershell
git add api/src/main/kotlin/cc/dvitski/tabyproto/TabyDevice.kt
git commit -m "feat: add TabyDevice and DeviceSource model types"
```

---

### Task 3: Monitor core + USB discovery & liveness (TDD)

**Files:**
- Create: `api/src/main/kotlin/cc/dvitski/tabyproto/TabyDeviceMonitor.kt`
- Create: `api/src/test/kotlin/cc/dvitski/tabyproto/TabyDeviceMonitorTest.kt`

The monitor's poll bodies are `internal suspend fun pollUsbOnce()` / `pollWifiOnce()` so tests drive them directly without timers (Kotlin test source sets see `internal` members of `main`). The constructor takes injectable seams for everything that touches hardware or network; the real wiring arrives in Task 6.

- [ ] **Step 1: Write the failing tests**

Create `api/src/test/kotlin/cc/dvitski/tabyproto/TabyDeviceMonitorTest.kt`:

```kotlin
package cc.dvitski.tabyproto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private fun fakeInfo(id: String, mdnsHost: String? = null) = DeviceInfo(
    firmwareVersion = "1.0", assetsVersion = null, deviceId = id,
    state = DeviceState.UNKNOWN, wifiMode = WifiMode.UNKNOWN, ip = null,
    claimed = null, claimedBy = null, usbMode = null, bootCount = null,
    brightnessPercent = null, freeHeapBytes = null, preferredTransport = null,
    transportOnboardingComplete = null, mdnsHost = mdnsHost, stationSsid = null,
    wifiError = null, bluetoothReady = null, bluetoothConnected = null,
    bluetoothName = null, powerVoltageMv = null, batteryPercent = null,
    externalPower = null,
)

private class FakeSession(
    override val transport: TabyTransport,
    override val device: DeviceInfo,
) : TabySession {
    var closed = false
    override suspend fun play(animation: Animation) = sendRaw(animation.id)
    override suspend fun play(command: AnimationCommand) = sendRaw(command.toWireString())
    override suspend fun setBrightness(percent: Int) = sendRaw("BRIGHTNESS $percent")
    override suspend fun sendRaw(command: String) =
        CommandResult(true, transport, command, "TABY:OK", "fake")
    override fun close() { closed = true }
}

private class Harness(manualHosts: List<String> = emptyList()) {
    var ports: List<UsbPortRef> = emptyList()
    val probeResults = mutableMapOf<String, DeviceInfo>()
    val healthResults = mutableMapOf<String, DeviceInfo>()
    val probeCalls = mutableListOf<String>()
    val sessions = mutableListOf<FakeSession>()

    val monitor = TabyDeviceMonitor(
        initialManualHosts = manualHosts,
        usbPollInterval = 2.seconds,
        wifiPollInterval = 5.seconds,
        portLister = { ports },
        usbProber = { port ->
            probeCalls += port
            probeResults[port] ?: error("no taby on $port")
        },
        healthChecker = { host -> healthResults[host] ?: error("unreachable $host") },
        usbSessionFactory = { _, info ->
            FakeSession(TabyTransport.USB, info).also { sessions += it }
        },
        wifiSessionFactory = { _, info ->
            FakeSession(TabyTransport.WIFI, info).also { sessions += it }
        },
        mdnsBrowserFactory = { null },
    )

    fun devices() = monitor.devices.value
    fun device(id: String) = devices().firstOrNull { it.id == id }
}

class TabyDeviceMonitorTest {

    // ── USB discovery & liveness ───────────────────────────────────────────────

    @Test
    fun `new taby port is probed and registered online`() = runTest {
        val h = Harness()
        h.ports = listOf(UsbPortRef("COM5", "USB Serial (COM5)"))
        h.probeResults["COM5"] = fakeInfo("taby-1")

        h.monitor.pollUsbOnce()

        val device = h.device("usb:COM5")!!
        assertTrue(device.online)
        assertEquals(TabyTransport.USB, device.transport)
        assertEquals("taby-1", device.info?.deviceId)
        assertEquals(DeviceSource.Usb("COM5", "USB Serial (COM5)"), device.source)
    }

    @Test
    fun `device label prefers mdns host over port name`() = runTest {
        val h = Harness()
        h.ports = listOf(UsbPortRef("COM5", "USB Serial (COM5)"))
        h.probeResults["COM5"] = fakeInfo("taby-1", mdnsHost = "taby.local")

        h.monitor.pollUsbOnce()

        assertEquals("taby.local", h.device("usb:COM5")!!.label)
    }

    @Test
    fun `non-taby port is probed once and then skipped while present`() = runTest {
        val h = Harness()
        h.ports = listOf(UsbPortRef("COM3", "Some Modem (COM3)"))
        // no probeResults entry → prober throws

        h.monitor.pollUsbOnce()
        h.monitor.pollUsbOnce()

        assertNull(h.device("usb:COM3"))
        assertEquals(listOf("COM3"), h.probeCalls)
    }

    @Test
    fun `online device is not re-probed while its port stays present`() = runTest {
        val h = Harness()
        h.ports = listOf(UsbPortRef("COM5", "USB Serial (COM5)"))
        h.probeResults["COM5"] = fakeInfo("taby-1")

        h.monitor.pollUsbOnce()
        h.monitor.pollUsbOnce()

        assertEquals(listOf("COM5"), h.probeCalls)
    }

    @Test
    fun `vanished port marks device offline`() = runTest {
        val h = Harness()
        h.ports = listOf(UsbPortRef("COM5", "USB Serial (COM5)"))
        h.probeResults["COM5"] = fakeInfo("taby-1")
        h.monitor.pollUsbOnce()

        h.ports = emptyList()
        h.monitor.pollUsbOnce()

        val device = h.device("usb:COM5")!!
        assertFalse(device.online)
    }

    @Test
    fun `reappeared port is re-probed and comes back online`() = runTest {
        val h = Harness()
        h.ports = listOf(UsbPortRef("COM5", "USB Serial (COM5)"))
        h.probeResults["COM5"] = fakeInfo("taby-1")
        h.monitor.pollUsbOnce()
        h.ports = emptyList()
        h.monitor.pollUsbOnce()

        h.ports = listOf(UsbPortRef("COM5", "USB Serial (COM5)"))
        h.monitor.pollUsbOnce()

        val device = h.device("usb:COM5")!!
        assertTrue(device.online)
        assertEquals(listOf("COM5", "COM5"), h.probeCalls)
    }

    @Test
    fun `vanished non-taby port is re-probed when it returns`() = runTest {
        val h = Harness()
        h.ports = listOf(UsbPortRef("COM3", "Some Modem (COM3)"))
        h.monitor.pollUsbOnce()           // probe fails → cached as non-Taby
        h.ports = emptyList()
        h.monitor.pollUsbOnce()           // port gone → cache cleared
        h.ports = listOf(UsbPortRef("COM3", "Some Modem (COM3)"))
        h.probeResults["COM3"] = fakeInfo("taby-2")   // a Taby was plugged into COM3

        h.monitor.pollUsbOnce()

        assertTrue(h.device("usb:COM3")!!.online)
        assertEquals(listOf("COM3", "COM3"), h.probeCalls)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail to compile**

Run: `.\gradlew.bat :api:test --tests "cc.dvitski.tabyproto.TabyDeviceMonitorTest"`
Expected: FAIL — `unresolved reference: TabyDeviceMonitor`

- [ ] **Step 3: Implement the monitor core**

Create `api/src/main/kotlin/cc/dvitski/tabyproto/TabyDeviceMonitor.kt`:

```kotlin
package cc.dvitski.tabyproto

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.time.Duration

/**
 * Discovers Taby devices on USB and WiFi, tracks their liveness, and owns
 * one [TabySession] per device.
 *
 * - USB: polls the serial port list every [usbPollInterval]. A new port is
 *   probed once with `INFO`; afterwards presence in the list is the liveness
 *   signal (an open session holds the port, so it must never be re-probed).
 * - WiFi: every [wifiPollInterval], each known host (manual + mDNS-discovered)
 *   is health-checked via `GET /v1/health`.
 * - Sessions: [session] returns a cached session per device, created lazily
 *   and closed automatically when the device goes offline or on [close].
 *   Callers must never close a session obtained here.
 *
 * Construct via the companion `invoke` (real hardware/network), then [start].
 */
class TabyDeviceMonitor internal constructor(
    initialManualHosts: List<String>,
    private val usbPollInterval: Duration,
    private val wifiPollInterval: Duration,
    private val portLister: () -> List<UsbPortRef>,
    private val usbProber: suspend (portName: String) -> DeviceInfo,
    private val healthChecker: suspend (host: String) -> DeviceInfo,
    private val usbSessionFactory: suspend (portName: String, info: DeviceInfo) -> TabySession,
    private val wifiSessionFactory: suspend (host: String, info: DeviceInfo) -> TabySession,
    private val mdnsBrowserFactory: (onCandidate: (String) -> Unit) -> Closeable?,
) : Closeable {

    private val logger = LoggerFactory.getLogger(TabyDeviceMonitor::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _devices = MutableStateFlow<List<TabyDevice>>(emptyList())
    val devices: StateFlow<List<TabyDevice>> = _devices.asStateFlow()

    private val _manualHosts = MutableStateFlow(initialManualHosts.distinct())
    val manualHosts: StateFlow<List<String>> = _manualHosts.asStateFlow()

    private val mdnsHosts = CopyOnWriteArraySet<String>()
    private val nonTabyPorts = mutableSetOf<String>()

    private val sessions = mutableMapOf<String, TabySession>()
    private val sessionMutex = Mutex()

    private var started = false
    private var mdnsBrowser: Closeable? = null

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /** Starts the discovery loops. Idempotent. */
    fun start() {
        if (started) return
        started = true
        mdnsBrowser = try {
            mdnsBrowserFactory(::onMdnsCandidate)
        } catch (e: Exception) {
            logger.warn("mDNS browse unavailable: ${e.message}")
            null
        }
        scope.launch {
            while (true) {
                try {
                    pollUsbOnce()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("USB poll failed", e)
                }
                delay(usbPollInterval)
            }
        }
        scope.launch {
            while (true) {
                try {
                    pollWifiOnce()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("WiFi poll failed", e)
                }
                delay(wifiPollInterval)
            }
        }
    }

    override fun close() {
        scope.cancel()
        runCatching { mdnsBrowser?.close() }
        sessions.values.forEach { runCatching { it.close() } }
        sessions.clear()
    }

    // ── Manual hosts ───────────────────────────────────────────────────────────

    fun addManualHost(host: String) {
        val cleaned = host.trim().removePrefix("http://").removeSuffix("/")
        if (cleaned.isEmpty()) return
        _manualHosts.update { if (cleaned in it) it else it + cleaned }
    }

    /** The device entry disappears on the next WiFi poll (≤ [wifiPollInterval]). */
    fun removeManualHost(host: String) {
        _manualHosts.update { it - host }
    }

    internal fun onMdnsCandidate(host: String) {
        mdnsHosts.add(host)
    }

    // ── USB polling ────────────────────────────────────────────────────────────

    internal suspend fun pollUsbOnce() {
        val present = portLister()
        val presentNames = present.map { it.portName }.toSet()
        // A vanished port may get a different device plugged in — forget its probe verdict.
        nonTabyPorts.retainAll(presentNames)

        _devices.value.forEach { device ->
            val src = device.source
            if (src is DeviceSource.Usb && src.portName !in presentNames && device.online) {
                evictSession(device.id)
                update(device.id) { it.copy(online = false) }
            }
        }

        for (ref in present) {
            if (ref.portName in nonTabyPorts) continue
            val id = "usb:${ref.portName}"
            if (_devices.value.firstOrNull { it.id == id }?.online == true) continue
            val info = try {
                usbProber(ref.portName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                nonTabyPorts.add(ref.portName)
                continue
            }
            upsert(
                TabyDevice(
                    id = id,
                    label = info.mdnsHost ?: ref.portName,
                    transport = TabyTransport.USB,
                    online = true,
                    info = info,
                    source = DeviceSource.Usb(ref.portName, ref.friendlyName),
                )
            )
        }
    }

    // ── WiFi polling ───────────────────────────────────────────────────────────

    internal suspend fun pollWifiOnce() {
        val hosts = LinkedHashMap<String, Boolean>() // host -> isManual
        _manualHosts.value.forEach { hosts[it] = true }
        mdnsHosts.forEach { hosts.putIfAbsent(it, false) }

        // Entries whose manual host was removed (and isn't mDNS-known) get dropped.
        _devices.value.forEach { device ->
            val src = device.source
            if (src is DeviceSource.Wifi && src.host !in hosts) {
                evictSession(device.id)
                remove(device.id)
            }
        }

        coroutineScope {
            hosts.forEach { (host, manual) -> launch { checkWifiHost(host, manual) } }
        }
    }

    private suspend fun checkWifiHost(host: String, manual: Boolean) {
        val id = "wifi:$host"
        val info = try {
            healthChecker(host)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (info != null) {
            upsert(
                TabyDevice(
                    id = id,
                    label = info.mdnsHost ?: host,
                    transport = TabyTransport.WIFI,
                    online = true,
                    info = info,
                    source = DeviceSource.Wifi(host, manual),
                )
            )
        } else {
            val existing = _devices.value.firstOrNull { it.id == id }
            if (existing == null) {
                upsert(
                    TabyDevice(
                        id = id,
                        label = host,
                        transport = TabyTransport.WIFI,
                        online = false,
                        info = null,
                        source = DeviceSource.Wifi(host, manual),
                    )
                )
            } else if (existing.online) {
                evictSession(id)
                update(id) { it.copy(online = false) }
            }
        }
    }

    // ── Sessions ───────────────────────────────────────────────────────────────

    /**
     * Returns the cached session for [device], creating it if needed.
     * Throws [IllegalStateException] if the device is currently offline.
     * Sessions are owned by the monitor — do not close them.
     */
    suspend fun session(device: TabyDevice): TabySession {
        val current = devices.value.firstOrNull { it.id == device.id }
        check(current != null && current.online) { "${device.label} is offline" }
        val info = checkNotNull(current.info) { "No device info for ${device.label}" }
        return sessionMutex.withLock {
            sessions[device.id] ?: run {
                val session = when (val src = current.source) {
                    is DeviceSource.Usb -> usbSessionFactory(src.portName, info)
                    is DeviceSource.Wifi -> wifiSessionFactory(src.host, info)
                }
                sessions[device.id] = session
                session
            }
        }
    }

    private suspend fun evictSession(id: String) {
        sessionMutex.withLock {
            sessions.remove(id)?.let { runCatching { it.close() } }
        }
    }

    // ── Registry helpers ───────────────────────────────────────────────────────

    private fun upsert(device: TabyDevice) {
        _devices.update { list ->
            (list.filterNot { it.id == device.id } + device).sortedWith(
                compareBy({ it.transport != TabyTransport.USB }, { it.label })
            )
        }
    }

    private fun update(id: String, transform: (TabyDevice) -> TabyDevice) {
        _devices.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun remove(id: String) {
        _devices.update { list -> list.filterNot { it.id == id } }
    }
}
```

Note: the companion-object production constructor and the JmDNS browser arrive in Task 6 — at this point the class is only constructible from tests (and module-internal code), which is fine.

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :api:test --tests "cc.dvitski.tabyproto.TabyDeviceMonitorTest"`
Expected: PASS — 7 tests

- [ ] **Step 5: Commit**

```powershell
git add api/src/main/kotlin/cc/dvitski/tabyproto/TabyDeviceMonitor.kt api/src/test/kotlin/cc/dvitski/tabyproto/TabyDeviceMonitorTest.kt
git commit -m "feat: add TabyDeviceMonitor with USB discovery and liveness tracking"
```

---

### Task 4: WiFi polling + manual hosts (TDD)

**Files:**
- Modify: `api/src/test/kotlin/cc/dvitski/tabyproto/TabyDeviceMonitorTest.kt` (add tests; implementation already exists from Task 3)

The WiFi logic was implemented in Task 3 (it shares the registry helpers); this task locks it in with tests. If any test fails, fix `TabyDeviceMonitor.kt` — the tests are the contract.

- [ ] **Step 1: Add WiFi tests**

Append inside `class TabyDeviceMonitorTest`:

```kotlin
    // ── WiFi polling & manual hosts ────────────────────────────────────────────

    @Test
    fun `reachable manual host is registered online`() = runTest {
        val h = Harness(manualHosts = listOf("192.168.1.50"))
        h.healthResults["192.168.1.50"] = fakeInfo("taby-w", mdnsHost = "taby.local")

        h.monitor.pollWifiOnce()

        val device = h.device("wifi:192.168.1.50")!!
        assertTrue(device.online)
        assertEquals(TabyTransport.WIFI, device.transport)
        assertEquals("taby.local", device.label)
        assertEquals(DeviceSource.Wifi("192.168.1.50", manual = true), device.source)
    }

    @Test
    fun `unreachable manual host appears as offline entry and is retried`() = runTest {
        val h = Harness(manualHosts = listOf("192.168.1.50"))

        h.monitor.pollWifiOnce()
        assertFalse(h.device("wifi:192.168.1.50")!!.online)

        // Host comes up later → next poll flips it online.
        h.healthResults["192.168.1.50"] = fakeInfo("taby-w")
        h.monitor.pollWifiOnce()
        assertTrue(h.device("wifi:192.168.1.50")!!.online)
    }

    @Test
    fun `online wifi device flips offline when health check fails`() = runTest {
        val h = Harness(manualHosts = listOf("192.168.1.50"))
        h.healthResults["192.168.1.50"] = fakeInfo("taby-w")
        h.monitor.pollWifiOnce()

        h.healthResults.clear()
        h.monitor.pollWifiOnce()

        assertFalse(h.device("wifi:192.168.1.50")!!.online)
    }

    @Test
    fun `addManualHost normalizes and dedupes`() = runTest {
        val h = Harness()
        h.monitor.addManualHost(" http://taby.local/ ")
        h.monitor.addManualHost("taby.local")
        h.monitor.addManualHost("   ")

        assertEquals(listOf("taby.local"), h.monitor.manualHosts.value)
    }

    @Test
    fun `removed manual host disappears from devices on next poll`() = runTest {
        val h = Harness(manualHosts = listOf("192.168.1.50"))
        h.healthResults["192.168.1.50"] = fakeInfo("taby-w")
        h.monitor.pollWifiOnce()

        h.monitor.removeManualHost("192.168.1.50")
        h.monitor.pollWifiOnce()

        assertNull(h.device("wifi:192.168.1.50"))
    }

    @Test
    fun `mdns candidate host is polled and registered as non-manual`() = runTest {
        val h = Harness()
        h.monitor.onMdnsCandidate("192.168.1.77")
        h.healthResults["192.168.1.77"] = fakeInfo("taby-m")

        h.monitor.pollWifiOnce()

        val device = h.device("wifi:192.168.1.77")!!
        assertTrue(device.online)
        assertEquals(DeviceSource.Wifi("192.168.1.77", manual = false), device.source)
    }
```

- [ ] **Step 2: Run tests**

Run: `.\gradlew.bat :api:test --tests "cc.dvitski.tabyproto.TabyDeviceMonitorTest"`
Expected: PASS — 13 tests. If any WiFi test fails, fix the implementation in `TabyDeviceMonitor.kt`, not the test.

- [ ] **Step 3: Commit**

```powershell
git add api/src/test/kotlin/cc/dvitski/tabyproto/TabyDeviceMonitorTest.kt
git commit -m "test: cover WiFi polling, manual hosts, and mDNS candidates"
```

---

### Task 5: Session management (TDD)

**Files:**
- Modify: `api/src/test/kotlin/cc/dvitski/tabyproto/TabyDeviceMonitorTest.kt`

Like Task 4, `session()`/eviction were implemented in Task 3; these tests are the contract.

- [ ] **Step 1: Add session tests**

Append inside `class TabyDeviceMonitorTest`:

```kotlin
    // ── Session management ─────────────────────────────────────────────────────

    @Test
    fun `session is cached per device`() = runTest {
        val h = Harness()
        h.ports = listOf(UsbPortRef("COM5", "USB Serial (COM5)"))
        h.probeResults["COM5"] = fakeInfo("taby-1")
        h.monitor.pollUsbOnce()
        val device = h.device("usb:COM5")!!

        val s1 = h.monitor.session(device)
        val s2 = h.monitor.session(device)

        assertSame(s1, s2)
        assertEquals(1, h.sessions.size)
    }

    @Test
    fun `session for offline device throws`() = runTest {
        val h = Harness(manualHosts = listOf("192.168.1.50"))
        h.monitor.pollWifiOnce()  // unreachable → offline entry
        val device = h.device("wifi:192.168.1.50")!!

        assertFailsWith<IllegalStateException> { h.monitor.session(device) }
    }

    @Test
    fun `session is closed and evicted when device goes offline`() = runTest {
        val h = Harness()
        h.ports = listOf(UsbPortRef("COM5", "USB Serial (COM5)"))
        h.probeResults["COM5"] = fakeInfo("taby-1")
        h.monitor.pollUsbOnce()
        val device = h.device("usb:COM5")!!
        h.monitor.session(device)

        h.ports = emptyList()
        h.monitor.pollUsbOnce()

        assertTrue(h.sessions.single().closed)
    }

    @Test
    fun `new session is created after device comes back online`() = runTest {
        val h = Harness()
        h.ports = listOf(UsbPortRef("COM5", "USB Serial (COM5)"))
        h.probeResults["COM5"] = fakeInfo("taby-1")
        h.monitor.pollUsbOnce()
        val s1 = h.monitor.session(h.device("usb:COM5")!!)

        h.ports = emptyList()
        h.monitor.pollUsbOnce()
        h.ports = listOf(UsbPortRef("COM5", "USB Serial (COM5)"))
        h.monitor.pollUsbOnce()
        val s2 = h.monitor.session(h.device("usb:COM5")!!)

        assertTrue(s1 !== s2)
        assertEquals(2, h.sessions.size)
    }

    @Test
    fun `close closes all cached sessions`() = runTest {
        val h = Harness()
        h.ports = listOf(UsbPortRef("COM5", "USB Serial (COM5)"))
        h.probeResults["COM5"] = fakeInfo("taby-1")
        h.monitor.pollUsbOnce()
        h.monitor.session(h.device("usb:COM5")!!)

        h.monitor.close()

        assertTrue(h.sessions.single().closed)
    }
```

- [ ] **Step 2: Run tests**

Run: `.\gradlew.bat :api:test --tests "cc.dvitski.tabyproto.TabyDeviceMonitorTest"`
Expected: PASS — 18 tests. Fix the implementation if not.

- [ ] **Step 3: Commit**

```powershell
git add api/src/test/kotlin/cc/dvitski/tabyproto/TabyDeviceMonitorTest.kt
git commit -m "test: cover monitor session caching, eviction, and close"
```

---

### Task 6: mDNS browser + production wiring

**Files:**
- Create: `api/src/main/kotlin/cc/dvitski/tabyproto/TabyMdnsBrowser.kt`
- Create: `api/src/test/kotlin/cc/dvitski/tabyproto/TabyMdnsBrowserTest.kt`
- Modify: `api/src/main/kotlin/cc/dvitski/tabyproto/TabyDeviceMonitor.kt` (add companion object)

- [ ] **Step 1: Write the failing test for the candidate filter**

Create `api/src/test/kotlin/cc/dvitski/tabyproto/TabyMdnsBrowserTest.kt`:

```kotlin
package cc.dvitski.tabyproto

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabyMdnsBrowserTest {

    @Test
    fun `matches taby in service name case-insensitively`() {
        assertTrue(isTabyCandidate("Taby-AB12", null))
        assertTrue(isTabyCandidate("my-taby-device", null))
    }

    @Test
    fun `matches taby in server hostname`() {
        assertTrue(isTabyCandidate("some-service", "taby.local."))
    }

    @Test
    fun `rejects unrelated services`() {
        assertFalse(isTabyCandidate("printer", "printer.local."))
        assertFalse(isTabyCandidate(null, null))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :api:test --tests "cc.dvitski.tabyproto.TabyMdnsBrowserTest"`
Expected: FAIL — `unresolved reference: isTabyCandidate`

- [ ] **Step 3: Implement the browser**

Create `api/src/main/kotlin/cc/dvitski/tabyproto/TabyMdnsBrowser.kt`:

```kotlin
package cc.dvitski.tabyproto

import org.slf4j.LoggerFactory
import java.io.Closeable
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

private const val HTTP_SERVICE_TYPE = "_http._tcp.local."
private const val RESOLVE_TIMEOUT_MS = 1_000L

internal fun isTabyCandidate(serviceName: String?, server: String?): Boolean =
    serviceName?.contains("taby", ignoreCase = true) == true ||
        server?.contains("taby", ignoreCase = true) == true

/**
 * Browses the LAN for HTTP services that look like Taby devices and reports
 * candidate hosts via [onCandidate]. Candidates are unverified — the monitor
 * confirms each one with a `/v1/health` check before treating it as a device.
 */
internal class TabyMdnsBrowser(private val onCandidate: (String) -> Unit) : Closeable {

    private val logger = LoggerFactory.getLogger(TabyMdnsBrowser::class.java)
    private val jmdns: JmDNS = JmDNS.create()

    private val listener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            jmdns.requestServiceInfo(event.type, event.name, RESOLVE_TIMEOUT_MS)
        }

        override fun serviceRemoved(event: ServiceEvent) {
            // Ignored — the health poll is the source of truth for offline.
        }

        override fun serviceResolved(event: ServiceEvent) {
            val info = event.info ?: return
            if (!isTabyCandidate(event.name, info.server)) return
            val host = info.inet4Addresses.firstOrNull()?.hostAddress
                ?: info.server?.trimEnd('.')
                ?: return
            logger.debug("mDNS candidate: ${event.name} → $host")
            onCandidate(host)
        }
    }

    init {
        jmdns.addServiceListener(HTTP_SERVICE_TYPE, listener)
    }

    override fun close() {
        jmdns.close()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :api:test --tests "cc.dvitski.tabyproto.TabyMdnsBrowserTest"`
Expected: PASS — 3 tests

- [ ] **Step 5: Add the production constructor to the monitor**

In `TabyDeviceMonitor.kt`, add this companion object inside the class (after the `remove` helper), plus the two imports:

```kotlin
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds
```

```kotlin
    companion object {
        /** Creates a monitor wired to real USB serial ports, HTTP, and mDNS. */
        operator fun invoke(
            initialManualHosts: List<String> = emptyList(),
            usbPollInterval: Duration = 2.seconds,
            wifiPollInterval: Duration = 5.seconds,
        ): TabyDeviceMonitor {
            val usbClient = TabyUsbClient()
            val wifiClients = ConcurrentHashMap<String, TabyWifiClient>()
            fun clientFor(host: String): TabyWifiClient =
                wifiClients.computeIfAbsent(host) { TabyWifiClient(it) }
            return TabyDeviceMonitor(
                initialManualHosts = initialManualHosts,
                usbPollInterval = usbPollInterval,
                wifiPollInterval = wifiPollInterval,
                portLister = {
                    usbClient.listCandidatePorts()
                        .map { UsbPortRef(it.systemPortName, it.descriptivePortName) }
                },
                usbProber = { port -> usbClient.readInfo(port) },
                healthChecker = { host -> clientFor(host).readHealth() },
                usbSessionFactory = { port, info -> UsbTabySession(usbClient.openSession(port), info) },
                wifiSessionFactory = { host, info -> WifiTabySession(clientFor(host), info) },
                mdnsBrowserFactory = { onCandidate -> TabyMdnsBrowser(onCandidate) },
            )
        }
    }
```

- [ ] **Step 6: Run the full api test suite and build**

Run: `.\gradlew.bat :api:build`
Expected: `BUILD SUCCESSFUL`, all 21 tests pass

- [ ] **Step 7: Commit**

```powershell
git add api/src/main/kotlin/cc/dvitski/tabyproto/TabyMdnsBrowser.kt api/src/test/kotlin/cc/dvitski/tabyproto/TabyMdnsBrowserTest.kt api/src/main/kotlin/cc/dvitski/tabyproto/TabyDeviceMonitor.kt
git commit -m "feat: add mDNS browsing and production wiring for TabyDeviceMonitor"
```

---

### Task 7: Desktop — manual host persistence + AppState rewrite

**Files:**
- Create: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/ManualHostsStore.kt`
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt` (the `AppState` class and `ConnectionState`; the `App` composable changes in Task 9)

The desktop module has no test infrastructure (Compose UI); these classes are exercised by the manual verification in Task 10.

- [ ] **Step 1: Create `ManualHostsStore.kt`**

```kotlin
package cc.dvitski.tabyproto.desktop

import java.util.prefs.Preferences

/** Persists the user's manually added WiFi hosts across app restarts. */
class ManualHostsStore {
    private val prefs = Preferences.userNodeForPackage(ManualHostsStore::class.java)

    fun load(): List<String> =
        prefs.get(KEY, "").split(SEPARATOR).filter { it.isNotBlank() }

    fun save(hosts: List<String>) {
        prefs.put(KEY, hosts.joinToString(SEPARATOR))
    }

    private companion object {
        const val KEY = "manualHosts"
        const val SEPARATOR = ","
    }
}
```

- [ ] **Step 2: Rewrite `AppState` in `App.kt`**

Delete the `ConnectionState` sealed class entirely. Replace the `AppState` class with:

```kotlin
class AppState {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val hostsStore = ManualHostsStore()
    private val monitor = TabyDeviceMonitor(initialManualHosts = hostsStore.load())

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

    init {
        monitor.start()
        thumbnailCache.preloadAll(Animation.entries)
        AnimationResources.preloadAll(Animation.entries, scope)
        scope.launch { monitor.manualHosts.collect { hostsStore.save(it) } }
        scope.launch {
            monitor.devices.collect { list ->
                // Auto-select the first device to come online (USB preferred);
                // afterwards selection only changes by user action.
                if (_activeDeviceId.value == null) {
                    val candidate = list.filter { it.online }
                        .minByOrNull { if (it.transport == TabyTransport.USB) 0 else 1 }
                    if (candidate != null) _activeDeviceId.value = candidate.id
                }
            }
        }
    }

    fun selectDevice(id: String) {
        _activeDeviceId.value = id
    }

    fun addManualHost(host: String) = monitor.addManualHost(host)

    fun removeManualHost(host: String) = monitor.removeManualHost(host)

    fun setQuery(q: String) {
        _query.value = q
    }

    suspend fun sendAnimation(animation: Animation): Result<Unit> {
        val device = devices.value.firstOrNull { it.id == _activeDeviceId.value }
            ?: return Result.failure(IllegalStateException("No Taby connected"))
        if (!device.online) {
            return Result.failure(IllegalStateException("${device.label} is offline"))
        }
        _sendingAnimation.value = animation
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
```

Update the imports in `App.kt`: remove `cc.dvitski.tabyproto.Taby`, `cc.dvitski.tabyproto.TabySession`, and `kotlinx.coroutines.Job`; add `cc.dvitski.tabyproto.TabyDevice` and `cc.dvitski.tabyproto.TabyDeviceMonitor`. (`TabyTransport` stays.) The `App` composable still references `ConnectionState` at this point — it gets rewritten in Task 9, so **the module will not compile until Task 9 is done**. That's expected; Tasks 7–9 form one compile unit and are committed together in Task 9.

- [ ] **Step 3: Verify the api module still builds (desktop is mid-rewrite)**

Run: `.\gradlew.bat :api:build`
Expected: `BUILD SUCCESSFUL`

---

### Task 8: Desktop — device selector UI

**Files:**
- Create: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/DeviceSelector.kt`

- [ ] **Step 1: Create `DeviceSelector.kt`**

```kotlin
package cc.dvitski.tabyproto.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cc.dvitski.tabyproto.DeviceSource
import cc.dvitski.tabyproto.TabyDevice
import cc.dvitski.tabyproto.TabyTransport

private val OnlineGreen = Color(0xFF00C853)
private val OfflineGrey = Color(0xFF6C7086)
private val UnknownGrey = Color(0xFF9399B2)

/**
 * Top-bar device picker: shows the active device with a status dot; the
 * dropdown lists every known device, lets the user switch targets, remove
 * manual WiFi hosts, and add a new host by name/IP.
 */
@Composable
fun DeviceSelector(
    devices: List<TabyDevice>,
    activeDeviceId: String?,
    onSelect: (String) -> Unit,
    onAddHost: (String) -> Unit,
    onRemoveHost: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    val active = devices.firstOrNull { it.id == activeDeviceId }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            StatusDot(
                color = when {
                    active == null -> UnknownGrey
                    active.online -> OnlineGreen
                    else -> OfflineGrey
                },
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = active?.label ?: "No device",
                color = Color.White,
                fontSize = 13.sp,
            )
            if (active != null) {
                Spacer(Modifier.width(6.dp))
                TransportTag(active.transport)
            }
            Spacer(Modifier.width(4.dp))
            Text("▾", color = Color.White, fontSize = 11.sp)
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
                    StatusDot(color = if (device.online) OnlineGreen else OfflineGrey)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = device.label,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f).widthIn(min = 120.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    TransportTag(device.transport, dark = true)
                    val src = device.source
                    if (src is DeviceSource.Wifi && src.manual) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "✕",
                            fontSize = 12.sp,
                            color = OfflineGrey,
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
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun TransportTag(transport: TabyTransport, dark: Boolean = false) {
    Text(
        text = when (transport) {
            TabyTransport.USB -> "USB"
            TabyTransport.WIFI -> "WiFi"
            TabyTransport.BLUETOOTH -> "BT"
        },
        color = if (dark) OfflineGrey else UnknownGrey,
        fontSize = 10.sp,
    )
}

@Composable
private fun AddDeviceDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var host by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(8.dp)) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text("Add Taby by hostname or IP", fontSize = 14.sp)
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("e.g. taby.local or 192.168.1.50") },
                    singleLine = true,
                )
                Spacer(Modifier.size(12.dp))
                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onAdd(host) },
                        enabled = host.isNotBlank(),
                    ) { Text("Add") }
                }
            }
        }
    }
}
```

Note: if `androidx.compose.ui.window.Dialog` is unavailable in this Compose version, use `androidx.compose.ui.window.DialogWindow` with the same content (it takes `onCloseRequest` instead of `onDismissRequest`).

- [ ] **Step 2: Continue to Task 9** (module still doesn't compile until `App.kt`'s composable is updated — no build/commit yet)

---

### Task 9: Desktop — App composable integration

**Files:**
- Modify: `desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/App.kt`

- [ ] **Step 1: Rewrite the `App` composable and remove dead code**

In `App.kt`: delete the `StatusIndicator` composable entirely. Replace the `App` composable with:

```kotlin
@Composable
fun App(appState: AppState) {
    val devices by appState.devices.collectAsState()
    val activeDeviceId by appState.activeDeviceId.collectAsState()
    val query by appState.query.collectAsState()
    val lastSent by appState.lastSent.collectAsState()
    val sendingAnimation by appState.sendingAnimation.collectAsState()
    val loadedCount by appState.thumbnailCache.loadedCount.collectAsState()
    val total = appState.totalAnimations
    val thumbnailsReady = loadedCount >= total

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        backgroundColor = AppBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        if (!thumbnailsReady) {
            // Splash loading screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        text = "Loading animations… $loadedCount / $total",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                // Top bar: query field + device selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = appState::setQuery,
                        label = { Text("Filter animations") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(horizontalAlignment = Alignment.End) {
                        DeviceSelector(
                            devices = devices,
                            activeDeviceId = activeDeviceId,
                            onSelect = appState::selectDevice,
                            onAddHost = appState::addManualHost,
                            onRemoveHost = appState::removeManualHost,
                        )
                        if (lastSent != null) {
                            Text(
                                text = "Last sent: ${lastSent?.id}",
                                color = MutedText,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }

                // Animation grid — always visible; sends fail with a snackbar
                // when no device is connected.
                val filtered = Animation.entries.filter { animation ->
                    query.isBlank() || animation.id.contains(query, ignoreCase = true)
                }
                AnimationGrid(
                    animations = filtered,
                    thumbnailCache = appState.thumbnailCache,
                    sendingAnimation = sendingAnimation,
                    onSend = { animation ->
                        scope.launch {
                            val result = appState.sendAnimation(animation)
                            result.onFailure { e ->
                                snackbarHostState.showSnackbar(
                                    message = e.message ?: "Send failed",
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 8.dp),
                )
            }
        }
    }
}
```

Clean up now-unused imports in `App.kt` (`Button`, `CircleShape`, `clip`, `background`, `size` may no longer be referenced — let the compiler tell you).

- [ ] **Step 2: Build everything**

Run: `.\gradlew.bat build`
Expected: `BUILD SUCCESSFUL` — api tests pass, desktop compiles

- [ ] **Step 3: Commit Tasks 7–9 together**

```powershell
git add desktop/src/main/kotlin/cc/dvitski/tabyproto/desktop/
git commit -m "feat: live multi-device discovery in desktop app with target selector"
```

---

### Task 10: Manual verification

No code — run the app against real hardware and check each behavior. Requires the physical Taby.

- [ ] **Step 1: Launch**

Run: `.\gradlew.bat :desktop:run`

- [ ] **Step 2: Verify, with the Taby initially unplugged:**

1. App opens straight to the grid; selector shows "No device" with a grey dot. Clicking an animation shows a "No Taby connected" snackbar.
2. Plug in the Taby over USB → within a few seconds (probe includes a ~1–3 s device boot) it appears in the selector, auto-selected, green dot, "USB" tag.
3. Click an animation → plays on the device (first send takes a few seconds while the session opens and the device resets; subsequent sends are fast).
4. Unplug the cable → dot turns grey within ~2 s. Sends now fail with "<label> is offline".
5. Replug → device returns to green automatically; sends work again (new session, device resets once).
6. "Add device…" → enter the Taby's IP (or `taby.local` while it's on WiFi) → WiFi entry appears; switch the selector to it and send an animation over WiFi.
7. Restart the app → the manual WiFi host is still listed (Preferences persistence).
8. Remove the manual host with ✕ → entry disappears within ~5 s.

- [ ] **Step 3: Report results** — note any deviations; tune `usbPollInterval`/`wifiPollInterval` or the mDNS service type only if observations demand it.
