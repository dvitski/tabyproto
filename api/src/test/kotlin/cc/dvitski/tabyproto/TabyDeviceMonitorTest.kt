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
        h.monitor.addManualHost("https://taby.local/")

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
        assertEquals("192.168.1.77", device.label)
    }

    @Test
    fun `host added both manually and via mdns retains manual flag`() = runTest {
        val h = Harness(manualHosts = listOf("192.168.1.77"))
        h.monitor.onMdnsCandidate("192.168.1.77")
        h.healthResults["192.168.1.77"] = fakeInfo("taby-m")

        h.monitor.pollWifiOnce()

        assertEquals(DeviceSource.Wifi("192.168.1.77", manual = true), h.device("wifi:192.168.1.77")!!.source)
    }

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
}
