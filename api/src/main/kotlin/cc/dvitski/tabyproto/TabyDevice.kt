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
