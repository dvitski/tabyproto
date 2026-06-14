package cc.dvitski.tabyproto

sealed class TabyEvent {
    abstract val device: TabyDevice

    data class DeviceOnline(override val device: TabyDevice)  : TabyEvent()
    data class DeviceOffline(override val device: TabyDevice) : TabyEvent()
    data class TouchSignal(override val device: TabyDevice, val signal: Int) : TabyEvent()
    data class ChoiceSelected(override val device: TabyDevice, val signal: Int, val selection: ChoiceSelection) : TabyEvent()
    data class StateChanged(override val device: TabyDevice, val state: DeviceState) : TabyEvent()
    data class BatteryChanged(override val device: TabyDevice, val percent: Int, val externalPower: Boolean?) : TabyEvent()
    data class WifiChanged(override val device: TabyDevice, val connected: Boolean, val ip: String?) : TabyEvent()
    data class BluetoothChanged(override val device: TabyDevice, val connected: Boolean) : TabyEvent()
}

fun interface TabyEventListener {
    fun onEvent(event: TabyEvent)
}
