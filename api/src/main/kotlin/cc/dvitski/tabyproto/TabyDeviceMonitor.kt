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
