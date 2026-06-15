package cc.dvitski.tabyproto

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
    // When true, every read times out — simulates a device that has wedged but kept its USB
    // port enumerated (no OS disconnect event), exactly the LVGL-lock hang seen in the field.
    var failReads = false
    override suspend fun play(animation: Animation) = sendRaw(animation.id)
    override suspend fun play(raw: RawAnimation) = sendRaw(raw.id)
    override suspend fun play(command: AnimationCommand) = sendRaw(command.toWireString())
    override suspend fun setBrightness(percent: Int) = sendRaw("BRIGHTNESS $percent")
    override suspend fun sendRaw(command: String) =
        CommandResult(true, transport, command, "TABY:OK", "fake")
    override suspend fun readInfo(): DeviceInfo = if (failReads) throw RuntimeException("wedged") else device
    override suspend fun readTouchSignal(): Int = if (failReads) throw RuntimeException("wedged") else 0
    override suspend fun readChoiceSignal(): ChoiceSignal =
        if (failReads) throw RuntimeException("wedged") else ChoiceSignal(0, ChoiceSelection.NONE)
    override fun close() { closed = true }
}

private class Harness(manualHosts: List<String> = emptyList(), scope: CoroutineScope) {
    var ports: List<UsbPortRef> = emptyList()
    val probeResults = mutableMapOf<String, DeviceInfo>()
    val healthResults = mutableMapOf<String, DeviceInfo>()
    val probeCalls = mutableListOf<String>()
    val sessions = mutableListOf<FakeSession>()
    var now = 0L  // controllable clock for backoff tests

    val monitor = TabyDeviceMonitor(
        initialManualHosts = manualHosts,
        usbPollInterval = 2.seconds,
        wifiPollInterval = 5.seconds,
        scope = scope,
        portLister = { ports },
        nowMs = { now },
        usbProber = { port ->
            probeCalls += port
            probeResults[port] ?: error("no taby on $port")
        },
        healthChecker = { host -> healthResults[host] ?: error("unreachable $host") },
        usbSessionFactory = { _, info, _ ->
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

    // Build a Harness whose monitor runs background coroutines on runTest's backgroundScope —
    // deterministic virtual time, cancelled automatically when the test ends.
    private fun TestScope.harness(manualHosts: List<String> = emptyList()) =
        Harness(manualHosts, backgroundScope)

    // ── USB discovery & liveness ───────────────────────────────────────────────

    @Test
    fun `new taby port is probed and registered online`() = runTest {
        val h = harness()
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
        val h = harness()
        h.ports = listOf(UsbPortRef("COM5", "USB Serial (COM5)"))
        h.probeResults["COM5"] = fakeInfo("taby-1", mdnsHost = "taby.local")

        h.monitor.pollUsbOnce()

        assertEquals("taby.local", h.device("usb:COM5")!!.label)
    }

    @Test
    fun `non-taby port is probed once and then skipped while present`() = runTest {
        val h = harness()
        h.ports = listOf(UsbPortRef("COM3", "Some Modem (COM3)"))
        // no probeResults entry → prober throws

        h.monitor.pollUsbOnce()
        h.monitor.pollUsbOnce()

        assertNull(h.device("usb:COM3"))
        assertEquals(listOf("COM3"), h.probeCalls)
    }

    @Test
    fun `online device is not re-probed while its port stays present`() = runTest {
        val h = harness()
        h.ports = listOf(UsbPortRef("COM5", "USB Serial (COM5)"))
        h.probeResults["COM5"] = fakeInfo("taby-1")

        h.monitor.pollUsbOnce()
        h.monitor.pollUsbOnce()

        assertEquals(listOf("COM5"), h.probeCalls)
    }

    @Test
    fun `vanished port marks device offline`() = runTest {
        val h = harness()
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
        val h = harness()
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
        val h = harness()
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

    @Test
    fun `silent port is retried after backoff and reconnects when it recovers`() = runTest {
        val h = harness()
        h.ports = listOf(UsbPortRef("COM9", "USB Serial (COM9)"))
        // First poll: device is silent (no probeResults) → probe fails, backoff scheduled.
        h.monitor.pollUsbOnce()
        assertNull(h.device("usb:COM9"))
        assertEquals(listOf("COM9"), h.probeCalls)

        // Still within the backoff window → not re-probed.
        h.now += 1_000
        h.monitor.pollUsbOnce()
        assertEquals(listOf("COM9"), h.probeCalls)

        // Backoff elapsed and the device has recovered (e.g. after a reset) → re-probed, online.
        h.now += 5_000
        h.probeResults["COM9"] = fakeInfo("taby-9")
        h.monitor.pollUsbOnce()

        assertTrue(h.device("usb:COM9")!!.online)
        assertEquals(listOf("COM9", "COM9"), h.probeCalls)
    }

    @Test
    fun `repeated failures back off exponentially`() = runTest {
        val h = harness()
        h.ports = listOf(UsbPortRef("COM3", "Some Modem (COM3)"))  // never a Taby

        h.monitor.pollUsbOnce()                       // probe #1, backoff 3s
        h.now += 3_000; h.monitor.pollUsbOnce()       // probe #2, backoff 6s
        h.now += 3_000; h.monitor.pollUsbOnce()       // still within 6s → skipped
        h.now += 3_000; h.monitor.pollUsbOnce()       // probe #3 (6s elapsed)

        assertEquals(listOf("COM3", "COM3", "COM3"), h.probeCalls)
    }

    // ── Initial manual hosts normalization ─────────────────────────────────────

    @Test
    fun `initial manual hosts are normalized and deduped on construction`() = runTest {
        val h = harness(manualHosts = listOf("http://taby.local/", "https://taby.local", " taby.local "))

        assertEquals(listOf("taby.local"), h.monitor.manualHosts.value)
    }

    // ── WiFi polling & manual hosts ────────────────────────────────────────────

    @Test
    fun `reachable manual host is registered online`() = runTest {
        val h = harness(manualHosts = listOf("192.168.1.50"))
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
        val h = harness(manualHosts = listOf("192.168.1.50"))

        h.monitor.pollWifiOnce()
        assertFalse(h.device("wifi:192.168.1.50")!!.online)

        // Host comes up later → next poll flips it online.
        h.healthResults["192.168.1.50"] = fakeInfo("taby-w")
        h.monitor.pollWifiOnce()
        assertTrue(h.device("wifi:192.168.1.50")!!.online)
    }

    @Test
    fun `online wifi device flips offline when health check fails`() = runTest {
        val h = harness(manualHosts = listOf("192.168.1.50"))
        h.healthResults["192.168.1.50"] = fakeInfo("taby-w")
        h.monitor.pollWifiOnce()

        h.healthResults.clear()
        h.monitor.pollWifiOnce()

        assertFalse(h.device("wifi:192.168.1.50")!!.online)
    }

    @Test
    fun `addManualHost normalizes and dedupes`() = runTest {
        val h = harness()
        h.monitor.addManualHost(" http://taby.local/ ")
        h.monitor.addManualHost("taby.local")
        h.monitor.addManualHost("   ")
        h.monitor.addManualHost("https://taby.local/")

        assertEquals(listOf("taby.local"), h.monitor.manualHosts.value)
    }

    @Test
    fun `removed manual host disappears from devices on next poll`() = runTest {
        val h = harness(manualHosts = listOf("192.168.1.50"))
        h.healthResults["192.168.1.50"] = fakeInfo("taby-w")
        h.monitor.pollWifiOnce()

        h.monitor.removeManualHost("192.168.1.50")
        h.monitor.pollWifiOnce()

        assertNull(h.device("wifi:192.168.1.50"))
    }

    @Test
    fun `mdns candidate host is polled and registered as non-manual`() = runTest {
        val h = harness()
        h.monitor.onMdnsCandidate("192.168.1.77")
        h.healthResults["192.168.1.77"] = fakeInfo("taby-m")

        h.monitor.pollWifiOnce()

        val device = h.device("wifi:192.168.1.77")!!
        assertTrue(device.online)
        assertEquals(DeviceSource.Wifi("192.168.1.77", manual = false), device.source)
        assertEquals("192.168.1.77", device.label)
    }

    @Test
    fun `host added both manually and via mdns retains manual flag`() = runTest {
        val h = harness(manualHosts = listOf("192.168.1.77"))
        h.monitor.onMdnsCandidate("192.168.1.77")
        h.healthResults["192.168.1.77"] = fakeInfo("taby-m")

        h.monitor.pollWifiOnce()

        assertEquals(DeviceSource.Wifi("192.168.1.77", manual = true), h.device("wifi:192.168.1.77")!!.source)
    }

    // ── Session management ─────────────────────────────────────────────────────

    @Test
    fun `session is cached per device`() = runTest {
        val h = harness()
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
        val h = harness(manualHosts = listOf("192.168.1.50"))
        h.monitor.pollWifiOnce()  // unreachable → offline entry
        val device = h.device("wifi:192.168.1.50")!!

        assertFailsWith<IllegalStateException> { h.monitor.session(device) }
    }

    @Test
    fun `session is closed and evicted when device goes offline`() = runTest {
        val h = harness()
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
        val h = harness()
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
        val h = harness()
        h.ports = listOf(UsbPortRef("COM5", "USB Serial (COM5)"))
        h.probeResults["COM5"] = fakeInfo("taby-1")
        h.monitor.pollUsbOnce()
        h.monitor.session(h.device("usb:COM5")!!)

        h.monitor.close()

        assertTrue(h.sessions.single().closed)
    }

    // ── USB disconnect event ───────────────────────────────────────────────────

    @Test
    fun `usb disconnect event marks device offline and closes session`() = runTest {
        val h = harness()
        h.ports = listOf(UsbPortRef("COM5", "USB Serial (COM5)"))
        h.probeResults["COM5"] = fakeInfo("taby-1")
        h.monitor.pollUsbOnce()
        val device = h.device("usb:COM5")!!
        h.monitor.session(device)

        h.monitor.onUsbSessionDisconnected(device.id)

        assertFalse(h.device("usb:COM5")!!.online)
        assertTrue(h.sessions.single().closed)
    }

    @Test
    fun `wedged device whose commands stop responding is marked offline`() = runTest {
        val h = harness()
        h.ports = listOf(UsbPortRef("COM5", "USB Serial (COM5)"))
        h.probeResults["COM5"] = fakeInfo("taby-1")
        h.monitor.pollUsbOnce()
        val device = h.device("usb:COM5")!!
        val session = h.monitor.session(device) as FakeSession
        assertTrue(h.device("usb:COM5")!!.online)

        // The device wedges with its USB port still present: every poll command now fails.
        session.failReads = true
        advanceTimeBy(60_000)

        assertFalse(h.device("usb:COM5")!!.online)
        assertTrue(session.closed)
    }

    @Test
    fun `transient poll failures that recover do not mark the device offline`() = runTest {
        val h = harness()
        h.ports = listOf(UsbPortRef("COM5", "USB Serial (COM5)"))
        h.probeResults["COM5"] = fakeInfo("taby-1")
        h.monitor.pollUsbOnce()
        val device = h.device("usb:COM5")!!
        val session = h.monitor.session(device) as FakeSession

        // A short blip (fewer failures than the threshold) followed by recovery must not evict it.
        session.failReads = true
        advanceTimeBy(600)        // at the 500ms cadence this is at most 2 failed polls (< 3)
        session.failReads = false
        advanceTimeBy(10_000)     // keep polling successfully

        assertTrue(h.device("usb:COM5")!!.online)
        assertFalse(session.closed)
    }

    @Test
    fun `usb disconnect event is idempotent`() = runTest {
        val h = harness()
        h.ports = listOf(UsbPortRef("COM5", "USB Serial (COM5)"))
        h.probeResults["COM5"] = fakeInfo("taby-1")
        h.monitor.pollUsbOnce()
        val device = h.device("usb:COM5")!!
        h.monitor.session(device)

        h.monitor.onUsbSessionDisconnected(device.id)
        h.monitor.onUsbSessionDisconnected(device.id)

        assertFalse(h.device("usb:COM5")!!.online)
        assertTrue(h.sessions.single().closed)
    }

    @Test
    fun `session created after disconnect reconnect works`() = runTest {
        val h = harness()
        h.ports = listOf(UsbPortRef("COM5", "USB Serial (COM5)"))
        h.probeResults["COM5"] = fakeInfo("taby-1")
        h.monitor.pollUsbOnce()
        val device = h.device("usb:COM5")!!
        val s1 = h.monitor.session(device)

        // Simulate OS disconnect event while port stays enumerated (Windows pinned-handle behavior)
        h.monitor.onUsbSessionDisconnected(device.id)
        assertFalse(h.device("usb:COM5")!!.online)

        // Next poll re-probes the (still-present) port and brings it back online
        h.monitor.pollUsbOnce()

        val s2 = h.monitor.session(h.device("usb:COM5")!!)
        assertTrue(h.device("usb:COM5")!!.online)
        assertTrue(s1 !== s2)
        assertEquals(2, h.sessions.size)
    }
}
