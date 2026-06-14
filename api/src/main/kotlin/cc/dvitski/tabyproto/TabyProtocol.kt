package cc.dvitski.tabyproto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── Wire constants ─────────────────────────────────────────────────────────────

internal const val USB_BAUD_RATE = 115200
const val WIFI_DEFAULT_HOST = "taby.local"
internal const val PROTOCOL_PREFIX = "TABY:"

internal val USB_DIRECT_PREFIXES = listOf(
    "PING", "STATE", "INFO", "DIAG", "USB_DEBUG", "TABY:USB_DEBUG",
    "LOGS", "BRIGHTNESS", "SETUP_INFO", "SETUP_START",
    "WIFI_", "PROVISION ", "TRANSPORT_", "ONBOARDING_RESET",
    "FACTORY_RESET", "CHOICE_SIGNAL", "TOUCH_SIGNAL", "CMD "
)

// ── Transport types ────────────────────────────────────────────────────────────

enum class TabyTransport { USB, WIFI, BLUETOOTH }

// ── Command result ─────────────────────────────────────────────────────────────

data class CommandResult(
    val ok: Boolean,
    val transport: TabyTransport,
    val command: String,
    val rawResponse: String,
    val message: String,
)

// ── Device state ───────────────────────────────────────────────────────────────

enum class DeviceState {
    IDLE, UNKNOWN;
    companion object {
        fun from(raw: String?) =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

// ── WiFi mode ──────────────────────────────────────────────────────────────────

enum class WifiMode {
    NONE, STATION, AP, UNKNOWN;
    companion object {
        fun from(raw: String?) =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

// ── INFO response ──────────────────────────────────────────────────────────────

@Serializable
internal data class DeviceInfoRaw(
    @SerialName("firmware_version") val firmwareVersion: String? = null,
    @SerialName("assets_version") val assetsVersion: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("state") val state: String? = null,
    @SerialName("wifi_mode") val wifiMode: String? = null,
    @SerialName("ip") val ip: String? = null,
    @SerialName("claimed") val claimed: Boolean? = null,
    @SerialName("claimed_by") val claimedBy: String? = null,
    @SerialName("usb_mode") val usbMode: String? = null,
    @SerialName("usb_bridge_ready") val usbBridgeReady: Boolean? = null,
    @SerialName("reset_reason") val resetReason: String? = null,
    @SerialName("boot_count") val bootCount: Int? = null,
    @SerialName("last_command") val lastCommand: String? = null,
    @SerialName("last_reject_reason") val lastRejectReason: String? = null,
    @SerialName("usb_command_count") val usbCommandCount: Int? = null,
    @SerialName("usb_rejected_command_count") val usbRejectedCommandCount: Int? = null,
    @SerialName("brightness_percent") val brightnessPercent: Int? = null,
    @SerialName("brightness_raw") val brightnessRaw: Int? = null,
    @SerialName("free_heap_bytes") val freeHeapBytes: Int? = null,
    @SerialName("minimum_free_heap_bytes") val minimumFreeHeapBytes: Int? = null,
    @SerialName("preferred_transport") val preferredTransport: String? = null,
    @SerialName("transport_onboarding_complete") val transportOnboardingComplete: Boolean? = null,
    @SerialName("mdns_host") val mdnsHost: String? = null,
    @SerialName("station_ssid") val stationSsid: String? = null,
    @SerialName("wifi_error") val wifiError: String? = null,
    @SerialName("setup_ap_ssid") val setupApSsid: String? = null,
    @SerialName("bluetooth_ready") val bluetoothReady: Boolean? = null,
    @SerialName("bluetooth_advertising") val bluetoothAdvertising: Boolean? = null,
    @SerialName("bluetooth_connected") val bluetoothConnected: Boolean? = null,
    @SerialName("bluetooth_name") val bluetoothName: String? = null,
    @SerialName("bluetooth_error") val bluetoothError: String? = null,
    @SerialName("power_voltage_mv") val powerVoltageMv: Int? = null,
    @SerialName("battery_percent") val batteryPercent: Int? = null,
    @SerialName("external_power") val externalPower: Boolean? = null,
)

data class DeviceInfo(
    val firmwareVersion: String,
    val assetsVersion: String?,
    val deviceId: String?,
    val state: DeviceState,
    val wifiMode: WifiMode,
    val ip: String?,
    val claimed: Boolean?,
    val claimedBy: String?,
    val usbMode: String?,
    val bootCount: Int?,
    val brightnessPercent: Int?,
    val freeHeapBytes: Int?,
    val preferredTransport: TabyTransport?,
    val transportOnboardingComplete: Boolean?,
    val ip6: String? = null,
    val mdnsHost: String?,
    val stationSsid: String?,
    val wifiError: String?,
    val bluetoothReady: Boolean?,
    val bluetoothConnected: Boolean?,
    val bluetoothName: String?,
    val powerVoltageMv: Int?,
    val batteryPercent: Int?,
    val externalPower: Boolean?,
) {
    val wifiConnected: Boolean
        get() = wifiMode == WifiMode.STATION && !ip.isNullOrEmpty()
}

internal fun DeviceInfoRaw.toDomain() = DeviceInfo(
    firmwareVersion = firmwareVersion ?: "",
    assetsVersion = assetsVersion,
    deviceId = deviceId,
    state = DeviceState.from(state),
    wifiMode = WifiMode.from(wifiMode),
    ip = ip,
    claimed = claimed,
    claimedBy = claimedBy,
    usbMode = usbMode,
    bootCount = bootCount,
    brightnessPercent = brightnessPercent,
    freeHeapBytes = freeHeapBytes,
    preferredTransport = preferredTransport?.let { raw ->
        TabyTransport.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    },
    transportOnboardingComplete = transportOnboardingComplete,
    mdnsHost = mdnsHost,
    stationSsid = stationSsid,
    wifiError = wifiError,
    bluetoothReady = bluetoothReady,
    bluetoothConnected = bluetoothConnected,
    bluetoothName = bluetoothName,
    powerVoltageMv = powerVoltageMv,
    batteryPercent = batteryPercent,
    externalPower = externalPower,
)

// ── Choice signal ──────────────────────────────────────────────────────────────

enum class ChoiceSelection { CHOICE_1, CHOICE_2, ACTION, NONE }

@Serializable
internal data class ChoiceSignalRaw(
    @SerialName("signal") val signal: Int = 0,
    @SerialName("selection") val selection: String = "none",
)

data class ChoiceSignal(
    val signal: Int,
    val selection: ChoiceSelection,
)

internal fun ChoiceSignalRaw.toDomain() = ChoiceSignal(
    signal = signal,
    selection = when (selection.lowercase()) {
        "choice_1" -> ChoiceSelection.CHOICE_1
        "choice_2" -> ChoiceSelection.CHOICE_2
        "action" -> ChoiceSelection.ACTION
        else -> ChoiceSelection.NONE
    },
)

// ── WiFi network types ─────────────────────────────────────────────────────────

@Serializable
internal data class SavedWifiNetworkRaw(
    @SerialName("ssid") val ssid: String,
    @SerialName("label") val label: String = "",
    @SerialName("priority") val priority: Int = 0,
    @SerialName("auto_join") val autoJoin: Boolean = false,
    @SerialName("preferred") val preferred: Boolean = false,
    @SerialName("last_success") val lastSuccess: Boolean = false,
)

@Serializable
internal data class NearbyWifiNetworkRaw(
    @SerialName("ssid") val ssid: String,
    @SerialName("rssi") val rssi: Int,
    @SerialName("secure") val secure: Boolean,
)

// ── Provisioning info ──────────────────────────────────────────────────────────

@Serializable
internal data class ProvisioningInfoRaw(
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("wifi_mode") val wifiMode: String = "",
    @SerialName("provisioned") val provisioned: Boolean = false,
    @SerialName("wifi_error") val wifiError: String? = null,
    @SerialName("transport_onboarding_complete") val transportOnboardingComplete: Boolean = false,
    @SerialName("preferred_transport") val preferredTransport: String? = null,
)

internal data class ProvisioningInfo(
    val deviceId: String?,
    val wifiMode: String,
    val provisioned: Boolean,
    val wifiError: String?,
    val transportOnboardingComplete: Boolean,
    val preferredTransport: String?,
)

internal fun ProvisioningInfoRaw.toDomain() = ProvisioningInfo(
    deviceId = deviceId,
    wifiMode = wifiMode,
    provisioned = provisioned,
    wifiError = wifiError,
    transportOnboardingComplete = transportOnboardingComplete,
    preferredTransport = preferredTransport,
)

// ── USB-side response parsing ──────────────────────────────────────────────────

internal val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal fun normalizeUsbRequest(command: String): String {
    val trimmed = command.trim()
    require(trimmed.isNotEmpty()) { "Command cannot be empty" }
    val request = if (USB_DIRECT_PREFIXES.any { trimmed.startsWith(it) }) trimmed else "CMD $trimmed"
    return if (request.endsWith("\n")) request else "$request\n"
}

internal fun parseInfoResponse(line: String): DeviceInfo {
    val payload = extractPrefixedPayload(line, "TABY:INFO ")
    return json.decodeFromString(DeviceInfoRaw.serializer(), payload).toDomain()
}

internal fun parseChoiceSignalResponse(line: String): ChoiceSignal {
    val payload = extractPrefixedPayload(line, "TABY:CHOICE_SIGNAL ")
    return json.decodeFromString(ChoiceSignalRaw.serializer(), payload).toDomain()
}

private fun extractPrefixedPayload(line: String, prefix: String): String {
    check(line.startsWith(prefix)) { "Unexpected protocol response: $line" }
    return line.removePrefix(prefix)
}
