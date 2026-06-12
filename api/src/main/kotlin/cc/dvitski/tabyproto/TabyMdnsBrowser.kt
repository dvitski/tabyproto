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
            // getServer() returns non-null String (empty if unset); treat empty as null for candidate check
            val serverName = info.server.takeIf { it.isNotEmpty() }
            if (!isTabyCandidate(event.name, serverName)) return
            val host = info.inet4Addresses.firstOrNull()?.hostAddress
                ?: serverName?.trimEnd('.')
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
