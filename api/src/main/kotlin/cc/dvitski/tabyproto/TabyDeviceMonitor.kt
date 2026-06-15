package cc.dvitski.tabyproto

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TabyDeviceMonitor internal constructor(
    initialManualHosts: List<String>,
    private val usbPollInterval: Duration,
    private val wifiPollInterval: Duration,
    private val eventPollInterval: Duration = 500.milliseconds,
    // After this many consecutive failed poll exchanges, treat the device as gone. Covers a
    // firmware wedge that keeps the USB port enumerated (so no OS disconnect event fires) —
    // without it the loop would poll a dead device forever.
    private val maxConsecutivePollFailures: Int = 3,
    private val portLister: () -> List<UsbPortRef>,
    private val usbProber: suspend (portName: String) -> DeviceInfo,
    private val healthChecker: suspend (host: String) -> DeviceInfo,
    private val usbSessionFactory: suspend (portName: String, info: DeviceInfo, onDisconnect: () -> Unit) -> TabySession,
    private val wifiSessionFactory: suspend (host: String, info: DeviceInfo) -> TabySession,
    private val mdnsBrowserFactory: (onCandidate: (String) -> Unit) -> Closeable?,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    // Injectable so tests can supply runTest's backgroundScope: background poll loops then run
    // on virtual time (deterministic, auto-cancelled) instead of racing on real Default threads.
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : Closeable {

    private val logger = LoggerFactory.getLogger(TabyDeviceMonitor::class.java)

    private val _devices = MutableStateFlow<List<TabyDevice>>(emptyList())
    val devices: StateFlow<List<TabyDevice>> = _devices.asStateFlow()

    private val _manualHosts = MutableStateFlow(
        initialManualHosts.map(::normalizeHost).filter { it.isNotEmpty() }.distinct()
    )
    val manualHosts: StateFlow<List<String>> = _manualHosts.asStateFlow()

    private val _events = MutableSharedFlow<TabyEvent>()
    val events: SharedFlow<TabyEvent> = _events.asSharedFlow()

    private val listeners = CopyOnWriteArraySet<TabyEventListener>()
    private val pollJobs = ConcurrentHashMap<String, Job>()

    private val mdnsHosts = CopyOnWriteArraySet<String>()

    // Ports that failed to probe as a Taby, each with an exponential backoff before the
    // next retry. Unlike a permanent blacklist, this lets a port that was only transiently
    // silent — a device still booting, or one recovered after a reset/replug — reconnect on
    // its own, while still not re-probing genuine non-Taby ports on every poll.
    // Accessed only from the single USB poll coroutine, so no synchronization is needed.
    private val probeBackoff = mutableMapOf<String, ProbeBackoff>()

    private val sessions = ConcurrentHashMap<String, TabySession>()
    private val sessionMutex = Mutex()

    @Volatile private var closed = false
    private var started = false
    private var mdnsBrowser: Closeable? = null

    // ── Listener API ───────────────────────────────────────────────────────────

    fun addListener(listener: TabyEventListener) { listeners.add(listener) }
    fun removeListener(listener: TabyEventListener) { listeners.remove(listener) }

    private fun emitEvent(event: TabyEvent) {
        scope.launch { _events.emit(event) }
        listeners.forEach { runCatching { it.onEvent(event) } }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

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
                try { pollUsbOnce() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { logger.warn("USB poll failed", e) }
                delay(usbPollInterval)
            }
        }
        scope.launch {
            while (true) {
                try { pollWifiOnce() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { logger.warn("WiFi poll failed", e) }
                delay(wifiPollInterval)
            }
        }
    }

    override fun close() {
        closed = true
        scope.cancel()
        runCatching { mdnsBrowser?.close() }
        sessions.values.forEach { runCatching { it.close() } }
        sessions.clear()
        pollJobs.clear()
    }

    // ── Manual hosts ───────────────────────────────────────────────────────────

    private fun normalizeHost(host: String): String =
        host.trim().removePrefix("http://").removePrefix("https://").removeSuffix("/")

    fun addManualHost(host: String) {
        val cleaned = normalizeHost(host)
        if (cleaned.isEmpty()) return
        _manualHosts.update { if (cleaned in it) it else it + cleaned }
    }

    fun removeManualHost(host: String) {
        val cleaned = normalizeHost(host)
        _manualHosts.update { it - cleaned }
    }

    internal fun onMdnsCandidate(host: String) {
        mdnsHosts.add(host)
    }

    // ── USB polling ────────────────────────────────────────────────────────────

    internal suspend fun pollUsbOnce() {
        val present = portLister()
        val presentNames = present.map { it.portName }.toSet()
        probeBackoff.keys.retainAll(presentNames)

        _devices.value.forEach { device ->
            val src = device.source
            if (src is DeviceSource.Usb && src.portName !in presentNames) {
                if (device.online) {
                    evictSession(device.id)
                    update(device.id) { it.copy(online = false) }
                } else if (device.info == null) {
                    // Tentative entry for a port that disappeared before a successful probe — remove it.
                    remove(device.id)
                }
            }
        }

        for (ref in present) {
            // Skip a previously-failed port until its backoff window elapses, then retry.
            val backoff = probeBackoff[ref.portName]
            if (backoff != null && nowMs() < backoff.nextAttemptAt) continue
            val id = "usb:${ref.portName}"
            if (_devices.value.firstOrNull { it.id == id }?.online == true) continue

            // Add a tentative offline entry immediately so the port is visible in the UI
            // while the probe is in progress (and if it fails).
            if (_devices.value.none { it.id == id }) {
                _devices.update { list ->
                    (list + TabyDevice(
                        id = id,
                        label = ref.friendlyName.ifBlank { ref.portName },
                        transport = TabyTransport.USB,
                        online = false,
                        info = null,
                        source = DeviceSource.Usb(ref.portName, ref.friendlyName),
                    )).sortedWith(compareBy({ it.transport != TabyTransport.USB }, { it.label }))
                }
            }

            val info = try {
                usbProber(ref.portName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Back off before retrying a port that didn't answer as a Taby. Known devices
                // (info != null) stay visible as offline so reconnect attempts remain visible;
                // never-confirmed ports are removed from the list until the next retry.
                val b = probeBackoff.getOrPut(ref.portName) { ProbeBackoff() }
                b.failures++
                b.nextAttemptAt = nowMs() + backoffDelayMs(b.failures)
                if (_devices.value.firstOrNull { it.id == id }?.info == null) remove(id)
                continue
            }
            probeBackoff.remove(ref.portName)
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

    /** Per-port retry state: how many times probing failed and when to try again. */
    private class ProbeBackoff(var failures: Int = 0, var nextAttemptAt: Long = 0L)

    /** Exponential backoff (3s, 6s, 12s, 24s, …) capped so a recovered device reconnects within ~30s. */
    private fun backoffDelayMs(failures: Int): Long {
        val shift = (failures - 1).coerceIn(0, 10)
        return (PROBE_BACKOFF_BASE_MS shl shift).coerceAtMost(PROBE_BACKOFF_MAX_MS)
    }

    // ── WiFi polling ───────────────────────────────────────────────────────────

    internal suspend fun pollWifiOnce() {
        val hosts = LinkedHashMap<String, Boolean>()
        _manualHosts.value.forEach { hosts[it] = true }
        mdnsHosts.forEach { hosts.putIfAbsent(it, false) }

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
        val info = try { healthChecker(host) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }

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

    suspend fun session(device: TabyDevice): TabySession =
        sessionMutex.withLock {
            check(!closed) { "monitor is closed" }
            val current = devices.value.firstOrNull { it.id == device.id }
            check(current != null && current.online) { "${device.label} is offline" }
            val info = checkNotNull(current.info) { "No device info for ${device.label}" }
            sessions[device.id] ?: run {
                val session = when (val src = current.source) {
                    is DeviceSource.Usb -> usbSessionFactory(src.portName, info) { scope.launch { onUsbSessionDisconnected(device.id) } }
                    is DeviceSource.Wifi -> wifiSessionFactory(src.host, info)
                }
                sessions[device.id] = session
                session
            }
        }

    private suspend fun evictSession(id: String) {
        stopPolling(id)
        sessionMutex.withLock {
            sessions.remove(id)?.let { runCatching { it.close() } }
        }
    }

    internal suspend fun onUsbSessionDisconnected(deviceId: String) {
        evictSession(deviceId)
        update(deviceId) { it.copy(online = false) }
    }

    // ── Event polling ──────────────────────────────────────────────────────────

    private fun startPolling(device: TabyDevice) {
        pollJobs[device.id] = scope.launch {
            var lastInfo: DeviceInfo? = null
            var lastTouch: Int? = null
            var lastChoice: ChoiceSignal? = null
            var tick = 0
            var consecutiveFailures = 0
            while (true) {
                try {
                    val current = _devices.value.firstOrNull { it.id == device.id }
                        ?.takeIf { it.online } ?: break
                    val s = session(current)

                    val touch = s.readTouchSignal()
                    if (touch != lastTouch) {
                        emitEvent(TabyEvent.TouchSignal(current, touch))
                        lastTouch = touch
                    }

                    val choice = s.readChoiceSignal()
                    if (choice != lastChoice) {
                        emitEvent(TabyEvent.ChoiceSelected(current, choice.signal, choice.selection))
                        lastChoice = choice
                    }

                    if (tick % 10 == 0) {
                        val info = s.readInfo()
                        diffInfo(current, lastInfo, info)
                        lastInfo = info
                    }
                    tick++
                    consecutiveFailures = 0
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A brief hiccup is fine — retry. But if the device stops answering entirely
                    // (a firmware wedge with the USB port still enumerated, so no disconnect event
                    // ever fires), drop it offline so discovery can re-probe and reconnect instead
                    // of polling a dead device forever. We close the session and mark it offline
                    // inline rather than via evictSession(), whose stopPolling() would cancel this
                    // very coroutine mid-cleanup; breaking out lets the loop end naturally.
                    if (++consecutiveFailures >= maxConsecutivePollFailures) {
                        sessionMutex.withLock { sessions.remove(device.id)?.let { runCatching { it.close() } } }
                        update(device.id) { it.copy(online = false) }
                        break
                    }
                }
                delay(eventPollInterval)
            }
        }
    }

    private fun stopPolling(id: String) {
        pollJobs.remove(id)?.cancel()
    }

    private fun diffInfo(device: TabyDevice, prev: DeviceInfo?, curr: DeviceInfo) {
        if (prev != null && curr.state != prev.state)
            emitEvent(TabyEvent.StateChanged(device, curr.state))
        val bat = curr.batteryPercent
        if (bat != null && prev != null && (bat != prev.batteryPercent || curr.externalPower != prev.externalPower))
            emitEvent(TabyEvent.BatteryChanged(device, bat, curr.externalPower))
        if (prev != null && (curr.wifiConnected != prev.wifiConnected || curr.ip != prev.ip))
            emitEvent(TabyEvent.WifiChanged(device, curr.wifiConnected, curr.ip))
        val bt = curr.bluetoothConnected
        if (bt != null && prev != null && bt != prev.bluetoothConnected)
            emitEvent(TabyEvent.BluetoothChanged(device, bt))
    }

    // ── Registry helpers ───────────────────────────────────────────────────────

    private fun upsert(device: TabyDevice) {
        val wasOnline = _devices.value.firstOrNull { it.id == device.id }?.online
        _devices.update { list ->
            (list.filterNot { it.id == device.id } + device).sortedWith(
                compareBy({ it.transport != TabyTransport.USB }, { it.label })
            )
        }
        if (device.online && wasOnline != true) {
            emitEvent(TabyEvent.DeviceOnline(device))
            startPolling(device)
        }
    }

    private fun update(id: String, transform: (TabyDevice) -> TabyDevice) {
        val wasOnline = _devices.value.firstOrNull { it.id == id }?.online
        _devices.update { list -> list.map { if (it.id == id) transform(it) else it } }
        if (wasOnline == true) {
            val current = _devices.value.firstOrNull { it.id == id }
            if (current?.online == false) emitEvent(TabyEvent.DeviceOffline(current))
        }
    }

    private fun remove(id: String) {
        val device = _devices.value.firstOrNull { it.id == id }
        _devices.update { list -> list.filterNot { it.id == id } }
        if (device?.online == true) emitEvent(TabyEvent.DeviceOffline(device))
    }

    companion object {
        private const val PROBE_BACKOFF_BASE_MS = 3_000L
        private const val PROBE_BACKOFF_MAX_MS = 30_000L

        operator fun invoke(
            initialManualHosts: List<String> = emptyList(),
            usbPollInterval: Duration = 2.seconds,
            wifiPollInterval: Duration = 5.seconds,
            eventPollInterval: Duration = 500.milliseconds,
        ): TabyDeviceMonitor {
            val usbClient = TabyUsbClient()
            val wifiClients = ConcurrentHashMap<String, TabyWifiClient>()
            fun clientFor(host: String): TabyWifiClient =
                wifiClients.computeIfAbsent(host) { TabyWifiClient(it) }
            return TabyDeviceMonitor(
                initialManualHosts = initialManualHosts,
                usbPollInterval = usbPollInterval,
                wifiPollInterval = wifiPollInterval,
                eventPollInterval = eventPollInterval,
                portLister = {
                    usbClient.listCandidatePorts()
                        .map { UsbPortRef(it.systemPortName, it.descriptivePortName) }
                },
                usbProber = { port -> usbClient.readInfo(port) },
                healthChecker = { host -> clientFor(host).readHealth() },
                usbSessionFactory = { port, info, onDisconnect ->
                    val usbSession = usbClient.openSession(port)
                    usbSession.onDisconnect(onDisconnect)
                    UsbTabySession(usbSession, info)
                },
                wifiSessionFactory = { host, info -> WifiTabySession(clientFor(host), info) },
                mdnsBrowserFactory = { onCandidate -> TabyMdnsBrowser(onCandidate) },
            )
        }
    }
}
