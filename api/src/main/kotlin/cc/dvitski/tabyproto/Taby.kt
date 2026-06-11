package cc.dvitski.tabyproto

object Taby {
    /** Auto-detect: USB first, WiFi fallback. */
    suspend fun connect(): TabySession =
        try {
            connectUsb()
        } catch (_: Exception) {
            connectWifi()
        }

    suspend fun connectUsb(): TabySession {
        val client = TabyUsbClient()
        val port = client.findFirstTabyPort() ?: error("No Taby device found on USB")
        val session = client.openSession(port.portName)
        return UsbTabySession(session, port.info)
    }

    suspend fun connectWifi(host: String = WIFI_DEFAULT_HOST): TabySession {
        val client = TabyWifiClient(host)
        val info = client.readHealth()
        return WifiTabySession(client, info)
    }
}
